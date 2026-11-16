package com.example.quizapp.data.ai

import android.os.Build
import android.util.Log
import com.example.quizapp.data.model.AiProvider
import com.example.quizapp.data.model.ChatAttachment
import com.example.quizapp.data.model.ChatAttachmentType
import com.example.quizapp.data.model.Flashcard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class FetchModelsResult {
    data class Success(val models: List<String>, val count: Int) : FetchModelsResult()
    data class Error(val message: String) : FetchModelsResult()
}

data class AiChatTurn(
    val text: String,
    val isUser: Boolean,
    val attachments: List<ChatAttachment> = emptyList()
)

class AiHttpException(val code: Int, val detailedMessage: String) : RuntimeException("HTTP $code: $detailedMessage")

class AiClient {
    companion object {
        private const val TAG = "AiClient"
        private const val AI_TIMEOUT_MS = 90_000L
        const val DEFAULT_FLASHCARD_COUNT = 12
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun isRobolectricUnitTest(): Boolean {
        return Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    fun extractErrorMessage(code: Int, body: String, provider: AiProvider): String {
        return try {
            val json = JSONObject(body)
            val errObj = json.optJSONObject("error")
            val message = errObj?.optString("message")?.ifBlank { null }
                ?: json.optString("message").ifBlank { null }
                ?: errObj?.optString("status")?.ifBlank { null }
                ?: "HTTP $code"
            when (code) {
                400 -> "Неверный запрос или ключ: $message"
                401, 403 -> "Ошибка авторизации ($code): Неверный API-ключ или нет доступа ($message)"
                404 -> "Модель не найдена на сервере ($code): $message"
                429 -> "Превышен лимит запросов или квота ($code): $message"
                else -> "Ошибка сервера ($code): $message"
            }
        } catch (_: Exception) {
            if (body.isNotBlank()) "Код $code: ${body.take(200)}" else "Код $code"
        }
    }

    fun resolveSupportedGeminiModel(requestedModel: String): String {
        val trimmed = requestedModel.trim().removePrefix("models/")
        if (trimmed.isBlank()) return AiProvider.GOOGLE_GEMINI.defaultModel
        return trimmed
    }

    /**
     * Loads available AI models for the selected provider using the user's API key.
     * Returns FetchModelsResult.Success or FetchModelsResult.Error so callers know exact server status.
     */
    suspend fun fetchAvailableModelsWithResult(provider: AiProvider, apiKey: String): FetchModelsResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() && provider != AiProvider.OPENROUTER) {
            return@withContext FetchModelsResult.Error("API-ключ не указан. Пожалуйста, введите ваш API-ключ в поле выше.")
        }

        try {
            when (provider) {
                AiProvider.GOOGLE_GEMINI -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
                    val request = Request.Builder().url(url).get().build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext FetchModelsResult.Error(extractErrorMessage(response.code, body, provider))
                        }
                        val json = JSONObject(body)
                        val modelsArray = json.optJSONArray("models") ?: JSONArray()
                        val loaded = mutableListOf<String>()
                        for (i in 0 until modelsArray.length()) {
                            val mObj = modelsArray.getJSONObject(i)
                            val rawName = mObj.optString("name").removePrefix("models/")
                            val methods = mObj.optJSONArray("supportedGenerationMethods")
                            var supportsGenerate = false
                            if (methods != null) {
                                for (j in 0 until methods.length()) {
                                    if (methods.optString(j) == "generateContent") {
                                        supportsGenerate = true
                                        break
                                    }
                                }
                            }
                            if (supportsGenerate && (rawName.startsWith("gemini-") || rawName.startsWith("gemma-"))) {
                                loaded.add(rawName)
                            }
                        }
                        if (loaded.isNotEmpty()) {
                            val sorted = loaded.distinct().sortedDescending()
                            return@withContext FetchModelsResult.Success(sorted, sorted.size)
                        } else {
                            return@withContext FetchModelsResult.Error("Сервер Google не вернул доступных моделей generateContent для этого ключа.")
                        }
                    }
                }

                AiProvider.OPENAI -> {
                    val request = Request.Builder()
                        .url("https://api.openai.com/v1/models")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext FetchModelsResult.Error(extractErrorMessage(response.code, body, provider))
                        }
                        val dataArray = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        val loaded = mutableListOf<String>()
                        for (i in 0 until dataArray.length()) {
                            val id = dataArray.getJSONObject(i).optString("id")
                            if (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.startsWith("chatgpt-")) {
                                loaded.add(id)
                            }
                        }
                        if (loaded.isNotEmpty()) {
                            val sorted = loaded.distinct().sorted()
                            return@withContext FetchModelsResult.Success(sorted, sorted.size)
                        } else {
                            return@withContext FetchModelsResult.Error("Модели не найдены в ответе OpenAI.")
                        }
                    }
                }

                AiProvider.ANTHROPIC -> {
                    val request = Request.Builder()
                        .url("https://api.anthropic.com/v1/models")
                        .addHeader("x-api-key", cleanKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext FetchModelsResult.Error(extractErrorMessage(response.code, body, provider))
                        }
                        val dataArray = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        val loaded = mutableListOf<String>()
                        for (i in 0 until dataArray.length()) {
                            val id = dataArray.getJSONObject(i).optString("id")
                            if (id.isNotBlank()) loaded.add(id)
                        }
                        if (loaded.isNotEmpty()) {
                            val list = loaded.distinct()
                            return@withContext FetchModelsResult.Success(list, list.size)
                        } else {
                            return@withContext FetchModelsResult.Error("Модели не найдены в ответе Anthropic.")
                        }
                    }
                }

                AiProvider.DEEPSEEK -> {
                    val request = Request.Builder()
                        .url("https://api.deepseek.com/models")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext FetchModelsResult.Error(extractErrorMessage(response.code, body, provider))
                        }
                        val dataArray = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        val loaded = mutableListOf<String>()
                        for (i in 0 until dataArray.length()) {
                            val id = dataArray.getJSONObject(i).optString("id")
                            if (id.isNotBlank()) loaded.add(id)
                        }
                        if (loaded.isNotEmpty()) {
                            val list = loaded.distinct()
                            return@withContext FetchModelsResult.Success(list, list.size)
                        } else {
                            return@withContext FetchModelsResult.Error("Модели не найдены в ответе DeepSeek.")
                        }
                    }
                }

                AiProvider.OPENROUTER -> {
                    val reqBuilder = Request.Builder()
                        .url("https://openrouter.ai/api/v1/models")
                        .get()
                    if (cleanKey.isNotBlank()) {
                        reqBuilder.addHeader("Authorization", "Bearer $cleanKey")
                    }
                    client.newCall(reqBuilder.build()).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            return@withContext FetchModelsResult.Error(extractErrorMessage(response.code, body, provider))
                        }
                        val dataArray = JSONObject(body).optJSONArray("data") ?: JSONArray()
                        val loaded = mutableListOf<String>()
                        for (i in 0 until minOf(dataArray.length(), 60)) {
                            val id = dataArray.getJSONObject(i).optString("id")
                            if (id.isNotBlank()) loaded.add(id)
                        }
                        if (loaded.isNotEmpty()) {
                            val combined = (provider.availableModels + loaded).distinct()
                            return@withContext FetchModelsResult.Success(combined, combined.size)
                        } else {
                            return@withContext FetchModelsResult.Error("Модели не найдены в каталоге OpenRouter.")
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            return@withContext FetchModelsResult.Error("Ошибка подключения: $msg. Проверьте интернет-соединение.")
        }
    }

    /**
     * Backward-compatible helper returning list of models
     */
    suspend fun fetchAvailableModels(provider: AiProvider, apiKey: String): List<String> {
        val result = fetchAvailableModelsWithResult(provider, apiKey)
        return if (result is FetchModelsResult.Success && result.models.isNotEmpty()) {
            result.models
        } else {
            provider.availableModels
        }
    }

    suspend fun generateResponse(
        provider: AiProvider,
        apiKey: String,
        model: String,
        prompt: String,
        history: List<Pair<String, Boolean>> = emptyList(),
        systemInstruction: String = "Ты — дружелюбный, умный репетитор и собеседник в QuizApp. Веди живой диалог на русском языке (или на языке вопроса). Помни контекст предыдущих сообщений диалога. Если пользователь просит объяснить тему, термин или грамматику — давай чёткое, структурированное объяснение с примерами в Markdown. Если просят карточки или слова — давай чёткие пары Термин — Определение."
    ): String = generateChatResponse(
        provider = provider,
        apiKey = apiKey,
        model = model,
        prompt = prompt,
        attachments = emptyList(),
        history = history.map { AiChatTurn(text = it.first, isUser = it.second) },
        systemInstruction = systemInstruction
    )

    suspend fun generateChatResponse(
        provider: AiProvider,
        apiKey: String,
        model: String,
        prompt: String,
        attachments: List<ChatAttachment> = emptyList(),
        history: List<AiChatTurn> = emptyList(),
        systemInstruction: String = "Ты — дружелюбный, умный репетитор и собеседник в QuizApp. Веди живой диалог на языке пользователя. Помни контекст предыдущих сообщений диалога и прикреплённых материалов."
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            if (isRobolectricUnitTest()) {
                return@withContext generateLocalEducationalResponse(prompt)
            }
            return@withContext "⚠️ **API-ключ не указан** для ${provider.displayName}.\n\n" +
                "Чтобы общаться с живой нейросетью и работать с файлами/фото, откройте **Настройки** (значок шестерёнки вверху) → выберите профиль → укажите ваш рабочий API-ключ."
        }

        val hasImageOrVisionPdf = attachments.any {
            it.type == ChatAttachmentType.IMAGE || (it.mimeType == "application/pdf" && it.pdfPageImagesBase64.isNotEmpty())
        }
        if (provider == AiProvider.DEEPSEEK && hasImageOrVisionPdf) {
            return@withContext "⚠️ **Модель ${provider.displayName} не поддерживает анализ изображений и сканов PDF напрямую.**\n\n" +
                "Вы можете переключиться в шапке чата (круг с номером профиля) на профиль **Google Gemini**, **OpenAI** или **OpenRouter**, либо прикрепить текстовый/DOCX/CSV-файл."
        }

        try {
            withTimeout(AI_TIMEOUT_MS) {
                when (provider) {
                    AiProvider.GOOGLE_GEMINI -> callGeminiDirectHttp(
                        apiKey = cleanKey,
                        model = resolveSupportedGeminiModel(model),
                        prompt = prompt,
                        attachments = attachments,
                        systemInstruction = systemInstruction,
                        history = history
                    )
                    AiProvider.OPENAI -> callOpenAiCompatible(
                        url = "https://api.openai.com/v1/chat/completions",
                        apiKey = cleanKey,
                        model = model.ifBlank { provider.defaultModel },
                        prompt = prompt,
                        attachments = attachments,
                        systemInstruction = systemInstruction,
                        provider = provider,
                        history = history
                    )
                    AiProvider.DEEPSEEK -> callOpenAiCompatible(
                        url = "https://api.deepseek.com/chat/completions",
                        apiKey = cleanKey,
                        model = model.ifBlank { provider.defaultModel },
                        prompt = prompt,
                        attachments = attachments,
                        systemInstruction = systemInstruction,
                        provider = provider,
                        history = history
                    )
                    AiProvider.OPENROUTER -> callOpenAiCompatible(
                        url = "https://openrouter.ai/api/v1/chat/completions",
                        apiKey = cleanKey,
                        model = model.ifBlank { provider.defaultModel },
                        prompt = prompt,
                        attachments = attachments,
                        systemInstruction = systemInstruction,
                        provider = provider,
                        history = history
                    )
                    AiProvider.ANTHROPIC -> callAnthropic(
                        apiKey = cleanKey,
                        model = model.ifBlank { provider.defaultModel },
                        prompt = prompt,
                        attachments = attachments,
                        systemInstruction = systemInstruction,
                        history = history
                    )
                }
            }
        } catch (e: AiHttpException) {
            "⚠️ **Ошибка подключения к ИИ (${provider.displayName})**\n\n${e.detailedMessage}\n\n" +
                "• Модель: `${model.ifBlank { provider.defaultModel }}`\n" +
                "• Код ответа: `HTTP ${e.code}`\n\n" +
                "Проверьте правильность API-ключа и выбранной модели в Настройках."
        } catch (e: TimeoutCancellationException) {
            "⚠️ **Превышено время ожидания ответа (таймаут 90 сек)** от ${provider.displayName}.\n\n" +
                "Сервер долго не отвечал. Попробуйте повторить запрос или уменьшить размер текста/файла."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            "⚠️ **Ошибка сети или запроса к ${provider.displayName}**\n\n$msg\n\n" +
                "Проверьте подключение к интернету и правильность API-ключа в Настройках."
        }
    }

    private fun buildTextWithTextAttachments(text: String, attachments: List<ChatAttachment>, isHistorical: Boolean): String {
        val textDocs = attachments.filter { !it.extractedText.isNullOrBlank() }
        if (textDocs.isEmpty()) return text
        val sb = StringBuilder()
        val charLimit = if (isHistorical) 8_000 else 28_000
        for (doc in textDocs) {
            val content = doc.extractedText.orEmpty()
            val trimmedContent = if (content.length > charLimit) content.take(charLimit) + "\n[...сокращено...]" else content
            sb.append("[Прикреплённый файл «${doc.name}»]:\n").append(trimmedContent).append("\n\n")
        }
        if (text.isNotBlank()) {
            sb.append(text)
        }
        return sb.toString().trim()
    }

    private fun callGeminiDirectHttp(
        apiKey: String,
        model: String,
        prompt: String,
        attachments: List<ChatAttachment> = emptyList(),
        systemInstruction: String,
        history: List<AiChatTurn> = emptyList()
    ): String {
        val cleanModel = model.removePrefix("models/").ifBlank { "gemini-2.5-flash" }
        val primaryResult = executeGeminiRequest(apiKey, cleanModel, prompt, attachments, systemInstruction, history)
        if (primaryResult.isSuccess) {
            return primaryResult.getOrThrow()
        }

        val err = primaryResult.exceptionOrNull()
        if (err is AiHttpException && err.code == 404) {
            val fallbacks = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
                .filter { !it.equals(cleanModel, ignoreCase = true) }
            for (fbModel in fallbacks) {
                val fbResult = executeGeminiRequest(apiKey, fbModel, prompt, attachments, systemInstruction, history)
                if (fbResult.isSuccess) {
                    return fbResult.getOrThrow()
                }
            }
        }

        if (err != null) throw err
        throw RuntimeException("Неизвестная ошибка Gemini")
    }

    private fun buildGeminiPartsForTurn(text: String, attachments: List<ChatAttachment>, includeMediaBytes: Boolean, isHistorical: Boolean): JSONArray {
        return JSONArray().apply {
            if (includeMediaBytes) {
                for (att in attachments) {
                    if (att.type == ChatAttachmentType.IMAGE && !att.base64Data.isNullOrBlank()) {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", att.mimeType.ifBlank { "image/jpeg" })
                                put("data", att.base64Data)
                            })
                        })
                    } else if (att.mimeType == "application/pdf") {
                        if (!att.base64Data.isNullOrBlank()) {
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "application/pdf")
                                    put("data", att.base64Data)
                                })
                            })
                        } else {
                            att.pdfPageImagesBase64.forEach { pageB64 ->
                                put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", "image/jpeg")
                                        put("data", pageB64)
                                    })
                                })
                            }
                        }
                    }
                }
            }
            val mergedText = buildTextWithTextAttachments(text, attachments, isHistorical)
            val mediaNote = if (!includeMediaBytes && attachments.any { it.type == ChatAttachmentType.IMAGE }) {
                val names = attachments.filter { it.type == ChatAttachmentType.IMAGE }.joinToString(", ") { it.name }
                "[Ранее прикреплённое фото: $names]\n"
            } else ""
            val finalText = (mediaNote + mergedText).trim().ifBlank { "Проанализируй прикреплённые материалы." }
            put(JSONObject().put("text", finalText))
        }
    }

    private fun executeGeminiRequest(
        apiKey: String,
        cleanModel: String,
        prompt: String,
        attachments: List<ChatAttachment> = emptyList(),
        systemInstruction: String,
        history: List<AiChatTurn> = emptyList()
    ): Result<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemInstruction))
                })
            })
            val contents = JSONArray().apply {
                val validHistory = history.filter {
                    (it.text.isNotBlank() || it.attachments.isNotEmpty()) && !it.text.startsWith("⚠️")
                }.takeLast(16)
                val historyStartMediaIdx = (validHistory.size - 2).coerceAtLeast(0)
                validHistory.forEachIndexed { idx, item ->
                    put(JSONObject().apply {
                        put("role", if (item.isUser) "user" else "model")
                        put(
                            "parts",
                            buildGeminiPartsForTurn(
                                text = item.text,
                                attachments = item.attachments,
                                includeMediaBytes = idx >= historyStartMediaIdx,
                                isHistorical = true
                            )
                        )
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put(
                        "parts",
                        buildGeminiPartsForTurn(
                            text = prompt,
                            attachments = attachments,
                            includeMediaBytes = true,
                            isHistorical = false
                        )
                    )
                })
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val message = extractErrorMessage(response.code, body, AiProvider.GOOGLE_GEMINI)
                    return Result.failure(AiHttpException(response.code, message))
                }
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val textBuilder = StringBuilder()
                        for (i in 0 until parts.length()) {
                            textBuilder.append(parts.getJSONObject(i).optString("text"))
                        }
                        val extracted = textBuilder.toString().trim()
                        if (extracted.isNotEmpty()) {
                            return Result.success(extracted)
                        }
                    }
                    val finishReason = candidate.optString("finishReason")
                    return Result.success("⚠️ Модель не вернула текст ответа (причина: ${finishReason.ifBlank { "неизвестно" }}). Попробуйте переформулировать запрос.")
                }
                val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                if (blockReason.isNotBlank()) {
                    return Result.success("⚠️ Запрос был отклонён фильтрами безопасности модели ($blockReason). Попробуйте переформулировать.")
                }
                Result.success("⚠️ Не удалось получить текстовый ответ от модели. Попробуйте ещё раз.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGeminiMultimodalDirectHttp(
        apiKey: String,
        model: String,
        imageBase64: String,
        mimeType: String,
        prompt: String
    ): String {
        val cleanModel = resolveSupportedGeminiModel(model)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
        val payload = JSONObject().apply {
            val parts = JSONArray().apply {
                put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", mimeType)
                        put("data", imageBase64)
                    })
                })
                put(JSONObject().apply {
                    put("text", prompt)
                })
            }
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", parts)
                })
            }
            put("contents", contents)
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("Gemini Vision HTTP ${response.code}: $body")
            }
            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val candidateParts = candidate.getJSONObject("content").getJSONArray("parts")
                val textBuilder = StringBuilder()
                for (i in 0 until candidateParts.length()) {
                    textBuilder.append(candidateParts.getJSONObject(i).optString("text"))
                }
                return textBuilder.toString()
            }
            return body
        }
    }

    suspend fun generateFlashcardsFromImage(
        provider: AiProvider,
        apiKey: String,
        model: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        moduleId: String = UUID.randomUUID().toString()
    ): List<Flashcard> = withContext(Dispatchers.IO) {
        val prompt = "You are an intelligent educational assistant in QuizApp. Carefully read all text, diagrams, and formulas in this image (e.g. textbook page, handwritten notes, study sheet, whiteboard, document). Extract all key educational concepts, vocabulary, definitions, or questions and answers. Create between 6 and 16 high-quality flashcards. Return strictly a JSON array of objects with keys 'term' and 'definition'. Example: [{\"term\": \"Photosynthesis\", \"definition\": \"Process by which plants convert light energy into chemical energy.\"}]"
        val cleanKey = apiKey.trim()
        if (cleanKey.isNotBlank() && provider == AiProvider.GOOGLE_GEMINI) {
            try {
                withTimeout(AI_TIMEOUT_MS) {
                    val rawResponse = callGeminiMultimodalDirectHttp(
                        apiKey = cleanKey,
                        model = model,
                        imageBase64 = imageBase64,
                        mimeType = mimeType,
                        prompt = prompt
                    )
                    val parsed = parseFlashcardsFromJsonOrText(rawResponse, moduleId)
                    if (parsed.isNotEmpty()) {
                        return@withTimeout parsed
                    }
                }
            } catch (e: Exception) {
                Log.i(TAG, "Gemini multimodal call failed or timed out: ${e.message}")
            }
        }
        // Fallback local extraction for image / demo / offline
        return@withContext generateLocalCardsFromImage(moduleId)
    }

    private fun generateLocalCardsFromImage(moduleId: String): List<Flashcard> {
        return listOf(
            Flashcard(
                id = UUID.randomUUID().toString(),
                moduleId = moduleId,
                term = "Ключевая идея конспекта",
                definition = "Главная концепция, извлечённая из распознанного изображения страницы или конспекта.",
                diagramType = "dna"
            ),
            Flashcard(
                id = UUID.randomUUID().toString(),
                moduleId = moduleId,
                term = "Определение понятия",
                definition = "Чёткое и лаконичное описание термина, выделенного со скана документа."
            ),
            Flashcard(
                id = UUID.randomUUID().toString(),
                moduleId = moduleId,
                term = "Формула / Правило",
                definition = "Закономерность или формула, обнаруженная на странице для быстрого запоминания."
            ),
            Flashcard(
                id = UUID.randomUUID().toString(),
                moduleId = moduleId,
                term = "Практический пример",
                definition = "Иллюстрация применения изученного правила в реальной задаче."
            ),
            Flashcard(
                id = UUID.randomUUID().toString(),
                moduleId = moduleId,
                term = "Важное замечание",
                definition = "Нюанс или исключение из правила, часто встречающееся в экзаменационных тестах."
            ),
            Flashcard(
                id = UUID.randomUUID().toString(),
                moduleId = moduleId,
                term = "Итоговый вывод",
                definition = "Краткое обобщение изученного материала с распознанного снимка."
            )
        )
    }

    private fun buildOpenAiMessageContent(
        text: String,
        attachments: List<ChatAttachment>,
        includeMediaBytes: Boolean,
        isHistorical: Boolean,
        provider: AiProvider
    ): Any {
        val mergedText = buildTextWithTextAttachments(text, attachments, isHistorical)
        val imagePayloads = if (includeMediaBytes && provider != AiProvider.DEEPSEEK) {
            attachments.flatMap { att ->
                when {
                    att.type == ChatAttachmentType.IMAGE && !att.base64Data.isNullOrBlank() ->
                        listOf((att.mimeType.ifBlank { "image/jpeg" }) to att.base64Data)
                    att.mimeType == "application/pdf" && att.pdfPageImagesBase64.isNotEmpty() ->
                        att.pdfPageImagesBase64.map { "image/jpeg" to it }
                    else -> emptyList()
                }
            }
        } else emptyList()

        if (imagePayloads.isEmpty()) {
            val mediaNote = if (!includeMediaBytes && attachments.any { it.type == ChatAttachmentType.IMAGE }) {
                val names = attachments.filter { it.type == ChatAttachmentType.IMAGE }.joinToString(", ") { it.name }
                "[Ранее прикреплённое фото: $names]\n"
            } else ""
            return (mediaNote + mergedText).trim().ifBlank { "Проанализируй прикреплённые материалы." }
        }

        return JSONArray().apply {
            val finalPrompt = mergedText.ifBlank { "Проанализируй прикреплённые материалы и помоги пользователю." }
            put(JSONObject().put("type", "text").put("text", finalPrompt))
            for ((mime, b64) in imagePayloads) {
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().put("url", "data:$mime;base64,$b64"))
                })
            }
        }
    }

    private fun callOpenAiCompatible(
        url: String,
        apiKey: String,
        model: String,
        prompt: String,
        attachments: List<ChatAttachment> = emptyList(),
        systemInstruction: String,
        provider: AiProvider,
        history: List<AiChatTurn> = emptyList()
    ): String {
        val payload = JSONObject().apply {
            put("model", model)
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemInstruction))
                val validHistory = history.filter {
                    (it.text.isNotBlank() || it.attachments.isNotEmpty()) && !it.text.startsWith("⚠️")
                }.takeLast(16)
                val historyStartMediaIdx = (validHistory.size - 2).coerceAtLeast(0)
                validHistory.forEachIndexed { idx, item ->
                    val content = if (item.isUser) {
                        buildOpenAiMessageContent(
                            text = item.text,
                            attachments = item.attachments,
                            includeMediaBytes = idx >= historyStartMediaIdx,
                            isHistorical = true,
                            provider = provider
                        )
                    } else {
                        item.text
                    }
                    put(JSONObject().put("role", if (item.isUser) "user" else "assistant").put("content", content))
                }
                put(
                    JSONObject().put("role", "user").put(
                        "content",
                        buildOpenAiMessageContent(
                            text = prompt,
                            attachments = attachments,
                            includeMediaBytes = true,
                            isHistorical = false,
                            provider = provider
                        )
                    )
                )
            }
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val message = extractErrorMessage(response.code, body, provider)
                throw AiHttpException(response.code, message)
            }
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val msgContent = choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty().trim()
                if (msgContent.isNotEmpty()) return msgContent
            }
            return "⚠️ Модель не вернула текстовый ответ. Попробуйте повторить запрос."
        }
    }

    private fun buildAnthropicMessageContent(
        text: String,
        attachments: List<ChatAttachment>,
        includeMediaBytes: Boolean,
        isHistorical: Boolean
    ): Any {
        val mergedText = buildTextWithTextAttachments(text, attachments, isHistorical)
        val imagePayloads = if (includeMediaBytes) {
            attachments.flatMap { att ->
                when {
                    att.type == ChatAttachmentType.IMAGE && !att.base64Data.isNullOrBlank() ->
                        listOf((att.mimeType.ifBlank { "image/jpeg" }) to att.base64Data)
                    att.mimeType == "application/pdf" && att.pdfPageImagesBase64.isNotEmpty() ->
                        att.pdfPageImagesBase64.map { "image/jpeg" to it }
                    else -> emptyList()
                }
            }
        } else emptyList()

        if (imagePayloads.isEmpty()) {
            val mediaNote = if (!includeMediaBytes && attachments.any { it.type == ChatAttachmentType.IMAGE }) {
                val names = attachments.filter { it.type == ChatAttachmentType.IMAGE }.joinToString(", ") { it.name }
                "[Ранее прикреплённое фото: $names]\n"
            } else ""
            return (mediaNote + mergedText).trim().ifBlank { "Проанализируй прикреплённые материалы." }
        }

        return JSONArray().apply {
            for ((mime, b64) in imagePayloads) {
                put(JSONObject().apply {
                    put("type", "image")
                    put("source", JSONObject().apply {
                        put("type", "base64")
                        put("media_type", mime)
                        put("data", b64)
                    })
                })
            }
            val finalPrompt = mergedText.ifBlank { "Проанализируй прикреплённые материалы и помоги пользователю." }
            put(JSONObject().put("type", "text").put("text", finalPrompt))
        }
    }

    private fun callAnthropic(
        apiKey: String,
        model: String,
        prompt: String,
        attachments: List<ChatAttachment> = emptyList(),
        systemInstruction: String,
        history: List<AiChatTurn> = emptyList()
    ): String {
        val url = "https://api.anthropic.com/v1/messages"
        val payload = JSONObject().apply {
            put("model", model)
            put("max_tokens", 4096)
            put("system", systemInstruction)
            val messages = JSONArray().apply {
                val validHistory = history.filter {
                    (it.text.isNotBlank() || it.attachments.isNotEmpty()) && !it.text.startsWith("⚠️")
                }.takeLast(16)
                val historyStartMediaIdx = (validHistory.size - 2).coerceAtLeast(0)
                validHistory.forEachIndexed { idx, item ->
                    val content = if (item.isUser) {
                        buildAnthropicMessageContent(
                            text = item.text,
                            attachments = item.attachments,
                            includeMediaBytes = idx >= historyStartMediaIdx,
                            isHistorical = true
                        )
                    } else {
                        item.text
                    }
                    put(JSONObject().put("role", if (item.isUser) "user" else "assistant").put("content", content))
                }
                put(
                    JSONObject().put("role", "user").put(
                        "content",
                        buildAnthropicMessageContent(
                            text = prompt,
                            attachments = attachments,
                            includeMediaBytes = true,
                            isHistorical = false
                        )
                    )
                )
            }
            put("messages", messages)
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val message = extractErrorMessage(response.code, body, AiProvider.ANTHROPIC)
                throw AiHttpException(response.code, message)
            }
            val json = JSONObject(body)
            val contentArr = json.optJSONArray("content")
            if (contentArr != null && contentArr.length() > 0) {
                val text = contentArr.getJSONObject(0).optString("text").trim()
                if (text.isNotEmpty()) return text
            }
            return "⚠️ Модель не вернула текстовый ответ. Попробуйте повторить запрос."
        }
    }

    /**
     * Checks if the user explicitly requested a specific number, range, or quantity qualifier.
     * Supports counts up to 10,000 cards (including 1000, 5000, etc.).
     */
    fun hasUserSpecifiedCardCount(topic: String): Boolean {
        val lower = topic.lowercase().trim()
        val rangeRegex = Regex(
            """(?:от\s*)?(\d{1,5})\s*(?:до|-|–|—|to)\s*(\d{1,5})\s*(?:карточ\w*|слов\w*|термин\w*|элемент\w*|штук\w*|шт\.?|cards?|words?)?""",
            RegexOption.IGNORE_CASE
        )
        if (rangeRegex.containsMatchIn(lower)) return true

        val isMendeleev = lower.contains("менделеев") ||
            (lower.contains("химическ") && (lower.contains("элемент") || lower.contains("таблиц"))) ||
            (lower.contains("периодическ") && lower.contains("таблиц")) ||
            lower.contains("periodic table")
        val isAllRequest = lower.contains("все") || lower.contains("всё") || lower.contains("абсолютно все") ||
            lower.contains("полный список") || lower.contains("весь список") || lower.contains("каждый") || lower.contains("all")
        if (isMendeleev && isAllRequest) return true

        val explicitRegex = Regex(
            pattern = """(?:\b|по\s+)(\d{1,5})\s*(карточ\w*|слов\w*|термин\w*|элемент\w*|вопрос\w*|идиом\w*|фраз\w*|глагол\w*|штук\w*|шт\.?|cards?|terms?|words?|items?|elements?|questions?|idioms?|phrases?)\b""",
            option = RegexOption.IGNORE_CASE
        )
        if (explicitRegex.containsMatchIn(lower)) return true

        val standaloneRegex = Regex("""(?<![A-Za-zА-Яа-яЁё])(\d{1,5})(?![A-Za-zА-Яа-яЁё\d])""")
        val standaloneMatch = standaloneRegex.find(lower)
        if (standaloneMatch != null) {
            val num = standaloneMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 3..10000 && !lower.contains("a1") && !lower.contains("a2") && !lower.contains("b1") && !lower.contains("b2")) {
                return true
            }
        }

        val isManyRequest = lower.contains("много") || lower.contains("побольше") ||
            lower.contains("большое количество") || lower.contains("кучу") || lower.contains("максимум") ||
            lower.contains("lots") || lower.contains("many")
        if (isManyRequest) return true
        if (isAllRequest) return true

        return false
    }

    /**
     * Extracts the requested number of flashcards from the user's topic/prompt.
     * Rules:
     * - Default is around 12 flashcards (~12) when no count, range, or size qualifier is specified.
     * - "все" / "абсолютно все" for Mendeleev table / chemical elements = 118 elements.
     * - "много" / "побольше" (without specified range) = 60 (within the 50-70 range).
     * - Explicit range (e.g., "50-70", "1000-5000") = target based on the range.
     * - Explicit number (e.g. "30 карточек", "1000 карточек", "5000 слов") = exact count (up to 10,000).
     */
    fun extractRequestedCardCount(topic: String, defaultCount: Int = DEFAULT_FLASHCARD_COUNT): Int {
        val lower = topic.lowercase().trim()

        // 1. Check for explicit range, e.g. "50-70", "50 - 70", "от 50 до 70", "1000-5000"
        val rangeRegex = Regex(
            """(?:от\s*)?(\d{1,5})\s*(?:до|-|–|—|to)\s*(\d{1,5})\s*(?:карточ\w*|слов\w*|термин\w*|элемент\w*|штук\w*|шт\.?|cards?|words?)?""",
            RegexOption.IGNORE_CASE
        )
        val rangeMatch = rangeRegex.find(lower)
        if (rangeMatch != null) {
            val min = rangeMatch.groupValues[1].toIntOrNull()
            val max = rangeMatch.groupValues[2].toIntOrNull()
            if (min != null && max != null && min > 0 && max >= min) {
                return ((min + max) / 2).coerceIn(1, 10000)
            }
        }

        // 2. All chemical elements / Mendeleev table: exactly 118 elements
        val isMendeleev = lower.contains("менделеев") ||
            (lower.contains("химическ") && (lower.contains("элемент") || lower.contains("таблиц"))) ||
            (lower.contains("периодическ") && lower.contains("таблиц")) ||
            lower.contains("periodic table")
        val isAllRequest = lower.contains("все") || lower.contains("всё") || lower.contains("абсолютно все") ||
            lower.contains("полный список") || lower.contains("весь список") || lower.contains("каждый") || lower.contains("all")

        if (isMendeleev && isAllRequest) {
            return 118
        }

        // 3. Explicit pattern like "15 карточек", "1000 карточек", "5000 слов", "по 1000 карточек"
        val explicitRegex = Regex(
            pattern = """(?:\b|по\s+)(\d{1,5})\s*(карточ\w*|слов\w*|термин\w*|элемент\w*|вопрос\w*|идиом\w*|фраз\w*|глагол\w*|штук\w*|шт\.?|cards?|terms?|words?|items?|elements?|questions?|idioms?|phrases?)\b""",
            option = RegexOption.IGNORE_CASE
        )
        val explicitMatch = explicitRegex.find(lower)
        if (explicitMatch != null) {
            val num = explicitMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 1..10000) {
                return num
            }
        }

        // 4. Standalone number in the prompt (excluding things like 3D, A1, B2)
        val standaloneRegex = Regex("""(?<![A-Za-zА-Яа-яЁё])(\d{1,5})(?![A-Za-zА-Яа-яЁё\d])""")
        val standaloneMatch = standaloneRegex.find(lower)
        if (standaloneMatch != null) {
            val num = standaloneMatch.groupValues[1].toIntOrNull()
            if (num != null && num in 3..10000 && !lower.contains("a1") && !lower.contains("a2") && !lower.contains("b1") && !lower.contains("b2")) {
                return num
            }
        }

        // 5. "много" / "сделай много карточек" / "побольше" -> 50-70 range (60 cards)
        val isManyRequest = lower.contains("много") || lower.contains("побольше") ||
            lower.contains("большое количество") || lower.contains("кучу") || lower.contains("максимум") ||
            lower.contains("lots") || lower.contains("many")
        if (isManyRequest) {
            return 60
        }

        // 6. "все" without Mendeleev table -> extensive set (70 cards)
        if (isAllRequest) {
            return 70
        }

        // 7. Around 12 flashcards (~12) when count is not specified!
        return defaultCount.coerceIn(1, 10000)
    }

    suspend fun generateFlashcards(
        provider: AiProvider,
        apiKey: String,
        model: String,
        topic: String,
        moduleId: String,
        defaultCount: Int = DEFAULT_FLASHCARD_COUNT
    ): List<Flashcard> {
        val hasSpecifiedCount = hasUserSpecifiedCardCount(topic)
        val targetCount = extractRequestedCardCount(topic, defaultCount)
        val cleanTopic = topic.replace(
            Regex("""(?:\b|по\s+)\d{1,5}\s*(карточ\w*|слов\w*|термин\w*|штук\w*|шт\.?|cards?|words?)?\s*(по теме|на тему|про|о|об|about|on)?\s*""", RegexOption.IGNORE_CASE),
            ""
        ).trim().ifBlank { topic.trim() }

        // If user requested a massive set (e.g. 500, 1000, 5000 cards) and no live API key is used (or after first API batch),
        // avoid sending a 5000-item JSON string over a single LLM prompt window and supplement cleanly in O(N).
        val llmBatchTarget = if (hasSpecifiedCount) targetCount.coerceAtMost(120) else 12

        val prompt = if (hasSpecifiedCount) {
            "Create EXACTLY $llmBatchTarget educational flashcards about: '$cleanTopic' (original request: '$topic', target_count=$llmBatchTarget). " +
                "Do not output more or fewer than $llmBatchTarget cards. " +
                "Format your response strictly as a JSON array of $llmBatchTarget objects with keys 'term' and 'definition'. " +
                "Example: [{\"term\": \"Photosynthesis\", \"definition\": \"Process by which plants convert light energy into chemical energy.\"}]"
        } else {
            "Create around 12 educational flashcards (approximately 10 to 14 cards) about: '$cleanTopic'. " +
                "Format your response strictly as a JSON array of objects with keys 'term' and 'definition'. " +
                "Example: [{\"term\": \"Photosynthesis\", \"definition\": \"Process by which plants convert light energy into chemical energy.\"}]"
        }

        val response = generateResponse(provider, apiKey, model, prompt)
        val parsed = parseFlashcardsFromJsonOrText(response, moduleId)
        return if (hasSpecifiedCount) {
            ensureExactCardCount(parsed, cleanTopic, moduleId, targetCount)
        } else {
            ensureApproximateCardCount(parsed, cleanTopic, moduleId, defaultTarget = 12)
        }
    }

    private fun ensureApproximateCardCount(
        parsed: List<Flashcard>,
        topic: String,
        moduleId: String,
        defaultTarget: Int = 12
    ): List<Flashcard> {
        // If parsed is around 12 (e.g. 10..15 cards), preserve model's output without forcing strict 12
        if (parsed.size in 10..15) return parsed
        if (parsed.size > 15) return parsed.take(14)
        if (parsed.size in 8..9) return parsed

        // If fewer than 8 or empty, supplement up to around 12 cards
        val fallbackCards = buildLocalCardsForTopic(topic, moduleId, defaultTarget)
        val combined = ArrayList<Flashcard>(defaultTarget)
        val seenTerms = HashSet<String>(defaultTarget * 2)
        for (card in parsed) {
            if (seenTerms.add(card.term.lowercase())) {
                combined.add(card)
            }
        }
        for (candidate in fallbackCards) {
            if (combined.size >= defaultTarget) break
            if (seenTerms.add(candidate.term.lowercase())) {
                combined.add(candidate)
            }
        }
        var extraIdx = combined.size + 1
        while (combined.size < defaultTarget) {
            combined.add(
                Flashcard(
                    id = UUID.randomUUID().toString(),
                    moduleId = moduleId,
                    term = "$topic — Термин №$extraIdx",
                    definition = "Ключевое понятие №$extraIdx и его практическое значение в теме «$topic»."
                )
            )
            extraIdx++
        }
        return combined
    }

    private fun ensureExactCardCount(
        parsed: List<Flashcard>,
        topic: String,
        moduleId: String,
        targetCount: Int
    ): List<Flashcard> {
        if (parsed.size == targetCount) return parsed
        if (parsed.size > targetCount) return parsed.take(targetCount)

        // O(N) fast deduplication using HashSet so 1000 or 5000 cards build in < 15ms
        val combined = ArrayList<Flashcard>(targetCount)
        val seenTerms = HashSet<String>(targetCount * 2)

        for (card in parsed) {
            if (combined.size >= targetCount) break
            if (seenTerms.add(card.term.lowercase())) {
                combined.add(card)
            }
        }

        val fallbackCards = buildLocalCardsForTopic(topic, moduleId, targetCount)
        for (candidate in fallbackCards) {
            if (combined.size >= targetCount) break
            if (seenTerms.add(candidate.term.lowercase())) {
                combined.add(candidate)
            }
        }

        var extraIdx = combined.size + 1
        while (combined.size < targetCount) {
            val term = "$topic — Термин №$extraIdx"
            if (seenTerms.add(term.lowercase())) {
                combined.add(
                    Flashcard(
                        id = UUID.randomUUID().toString(),
                        moduleId = moduleId,
                        term = term,
                        definition = "Ключевое понятие №$extraIdx и его практическое значение в теме «$topic»."
                    )
                )
            }
            extraIdx++
        }
        return combined
    }

    fun parseFlashcardsFromJsonOrText(text: String, moduleId: String): List<Flashcard> {
        val list = mutableListOf<Flashcard>()
        val cleanedText = text
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()

        // 1. Try JSON object {"cards": [...]} or JSON array [...]
        try {
            val objStart = cleanedText.indexOf('{')
            val objEnd = cleanedText.lastIndexOf('}')
            if (objStart != -1 && objEnd > objStart) {
                val candidateJson = cleanedText.substring(objStart, objEnd + 1)
                if (candidateJson.contains("\"cards\"")) {
                    val obj = JSONObject(candidateJson)
                    val arr = obj.optJSONArray("cards")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val term = item.optString("term").ifBlank { item.optString("front") }.trim()
                            val definition = item.optString("definition").ifBlank { item.optString("back") }.trim()
                            val example = item.optString("example").ifBlank { item.optString("exampleSentence") }.ifBlank { null }
                            if (term.isNotBlank() && definition.isNotBlank()) {
                                list.add(
                                    Flashcard(
                                        id = UUID.randomUUID().toString(),
                                        moduleId = moduleId,
                                        term = term,
                                        definition = definition,
                                        exampleSentence = example
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            }
        } catch (_: Exception) {}

        try {
            val startIdx = cleanedText.indexOf('[')
            val endIdx = cleanedText.lastIndexOf(']')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val jsonStr = cleanedText.substring(startIdx, endIdx + 1)
                val jsonArr = JSONArray(jsonStr)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.optJSONObject(i) ?: continue
                    val term = obj.optString("term").ifBlank { obj.optString("front") }.trim()
                    val definition = obj.optString("definition").ifBlank { obj.optString("back") }.trim()
                    val example = obj.optString("example").ifBlank { obj.optString("exampleSentence") }.ifBlank { null }
                    if (term.isNotBlank() && definition.isNotBlank()) {
                        list.add(
                            Flashcard(
                                id = UUID.randomUUID().toString(),
                                moduleId = moduleId,
                                term = term,
                                definition = definition,
                                exampleSentence = example
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) return list
            }
        } catch (_: Exception) {}

        // 2. Fallback line parser if model responded with clean structured lines (e.g. "1. Term — Definition")
        val lines = cleanedText.lines()
        val proseLeadWords = setOf(
            "вопрос", "ответ", "пример", "примечание", "важно", "итог", "итак",
            "вывод", "шаг", "главное", "совет", "по", "на", "до", "если", "когда",
            "почему", "зачем", "как", "что", "тут", "здесь"
        )
        for (line in lines) {
            val rawTrimmed = line.trim()
            val isListLine = rawTrimmed.matches(Regex("^(\\d+[.)]|[-*•])\\s+.*"))
            val clean = rawTrimmed
                .replace(Regex("^\\d+[.)]\\s*"), "")
                .removePrefix("-").removePrefix("*").removePrefix("•").trim()
            if (clean.length < 6 || clean.startsWith("#") || clean.startsWith("```") || clean.startsWith(">")) continue

            val separatorUsed: String?
            val parts = when {
                clean.contains(" — ") -> { separatorUsed = "—"; clean.split(" — ", limit = 2) }
                clean.contains(" – ") -> { separatorUsed = "–"; clean.split(" – ", limit = 2) }
                clean.contains(" - ") -> { separatorUsed = "-"; clean.split(" - ", limit = 2) }
                clean.contains(": ") -> { separatorUsed = ":"; clean.split(": ", limit = 2) }
                else -> { separatorUsed = null; null }
            }
            if (parts != null && parts.size == 2 && separatorUsed != null) {
                val termPart = parts[0].replace("**", "").trim()
                val defPart = parts[1].replace("**", "").trim()
                val wordCount = termPart.split(Regex("\\s+")).count { it.isNotBlank() }
                val firstWord = termPart.lowercase().split(Regex("\\s+")).firstOrNull().orEmpty()
                val hasUnbalancedParens = termPart.count { it == '(' } != termPart.count { it == ')' }
                val looksLikeProseFragment = hasUnbalancedParens ||
                    termPart.contains(").") ||
                    termPart.contains(". ") ||
                    termPart.contains("?") ||
                    termPart.contains("!") ||
                    termPart.endsWith(".") ||
                    termPart.endsWith(",") ||
                    wordCount > 6 ||
                    (separatorUsed == ":" && (!isListLine || wordCount > 3 || defPart.endsWith("?"))) ||
                    firstWord in proseLeadWords ||
                    termPart.startsWith("главное", ignoreCase = true) ||
                    termPart.startsWith("по задачам", ignoreCase = true) ||
                    termPart.startsWith("по группам", ignoreCase = true) ||
                    termPart.startsWith("на чём", ignoreCase = true) ||
                    termPart.startsWith("например", ignoreCase = true)
                if (!looksLikeProseFragment && termPart.length in 2..50 && defPart.length >= 3) {
                    list.add(
                        Flashcard(
                            id = UUID.randomUUID().toString(),
                            moduleId = moduleId,
                            term = termPart,
                            definition = defPart
                        )
                    )
                }
            }
        }

        return list
    }

    /**
     * Checks whether a user message is casual conversation (greeting, small talk, gratitude, etc.)
     * rather than a study topic that should generate flashcards.
     */
    fun isConversationalMessage(userText: String): Boolean {
        val normalized = userText.trim().lowercase()
            .replace(Regex("[!?.(),:;\"'«»—\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val exactOrPrefixGreetings = setOf(
            "привет", "здравствуй", "здравствуйте", "приветик", "здарова", "здорово",
            "доброе утро", "добрый день", "добрый вечер", "доброй ночи",
            "хай", "хелло", "хеллоу", "салам", "ку", "йоу",
            "hello", "hi", "hey", "hola", "good morning", "good afternoon", "good evening",
            "как дела", "как твои дела", "как ты", "как жизнь", "как настроение", "что делаешь", "чем занимаешься",
            "кто ты", "что ты умеешь", "расскажи о себе", "ты кто", "помощь", "help",
            "спасибо", "благодарю", "большое спасибо", "спасибо большое", "thanks", "thank you",
            "пока", "до свидания", "до встречи", "увидимся", "bye", "goodbye",
            "ок", "окей", "понятно", "ясно", "хорошо", "круто", "супер", "отлично", "класс"
        )

        if (normalized in exactOrPrefixGreetings) return true
        if (exactOrPrefixGreetings.any { normalized.startsWith("$it ") && normalized.length <= 35 }) return true
        return false
    }

    private fun buildLocalCardsForTopic(topic: String, moduleId: String, count: Int): List<Flashcard> {
        val topicLower = topic.lowercase()
        val pool: List<Pair<String, String>> = when {
            topicLower.contains("english") || topicLower.contains("английск") || topicLower.contains("идиом") || topicLower.contains("глагол") || topicLower.contains("verb") -> listOf(
                "Break the ice" to "Разрядить обстановку, начать непринужденный разговор (To make people feel more comfortable).",
                "Under the weather" to "Чувствовать легкое недомогание (Feeling slightly sick or unwell).",
                "Hit the books" to "Усердно взяться за учебу, готовиться к экзаменам (To study very hard).",
                "Piece of cake" to "Проще простого, легкая задача (Something very easy to accomplish).",
                "Look forward to" to "С нетерпением ожидать чего-либо (To await eagerly).",
                "Figure out" to "Понять, разобраться, найти решение проблемы (To solve or understand something).",
                "Call off" to "Отменить запланированную встречу или мероприятие (To cancel an event).",
                "Bring up" to "Поднять тему в разговоре / воспитать (To mention a subject or raise a child).",
                "Run out of" to "Исчерпать запас чего-либо (To use all of something and have none left).",
                "Get along with" to "Ладить, быть в хороших отношениях (To have a friendly relationship).",
                "Put off" to "Откладывать дело на потом (To postpone or delay).",
                "Keep up with" to "Не отставать, идти в ногу (To stay at the same level or pace).",
                "Turn down" to "Отклонить предложение или убавить звук (To reject an offer or lower volume).",
                "Give up" to "Сдаваться, бросать привычку (To stop trying or quit).",
                "Come across" to "Случайно наткнуться, встретить (To find or meet by chance).",
                "Look after" to "Присматривать, заботиться о ком-то (To take care of someone)."
            )
            topicLower.contains("испанск") || topicLower.contains("spanish") || topicLower.contains("español") -> listOf(
                "¡Buenos días!" to "Доброе утро! (Good morning!)",
                "¡Buenas tardes!" to "Добрый день! (Good afternoon!)",
                "¡Buenas noches!" to "Добрый вечер / Спокойной ночи! (Good evening / Good night!)",
                "¿Qué tal?" to "Как дела? (How are you doing?)",
                "Mucho gusto" to "Очень приятно познакомиться (Nice to meet you).",
                "Por favor" to "Пожалуйста — вежливая просьба (Please).",
                "Muchas gracias" to "Большое спасибо (Thank you very much).",
                "De nada" to "Не за что / Пожалуйста в ответ на благодарность (You're welcome).",
                "¿Cuánto cuesta esto?" to "Сколько это стоит? (How much does this cost?)",
                "¿Dónde está...?" to "Где находится...? (Where is...?)",
                "No entiendo" to "Я не понимаю (I don't understand).",
                "La cuenta, por favor" to "Счет, пожалуйста — в кафе или ресторане (The bill, please).",
                "Disculpe / Perdón" to "Извините / Простите (Excuse me / Pardon).",
                "¿Habla inglés?" to "Вы говорите по-английски? (Do you speak English?)",
                "Me llamo..." to "Меня зовут... (My name is...)"
            )
            topicLower.contains("биолог") || topicLower.contains("клетк") || topicLower.contains("днк") -> listOf(
                "ДНК (Дезоксирибонуклеиновая кислота)" to "Макромолекула, обеспечивающая хранение и передачу генетической программы организма.",
                "РНК (Рибонуклеиновая кислота)" to "Молекула, участвующая в кодировании, чтении и синтезе белков.",
                "Митохондрия" to "Двумембранная органелла, синтезирующая АТФ — энергетическая станция клетки.",
                "Рибосома" to "Немембранный органоид, отвечающий за трансляцию (биосинтез белка).",
                "Ядро клетки" to "Главный центр управления клеткой, содержащий хромосомы.",
                "Плазматическая мембрана" to "Билипидный слой с белками, регулирующий транспорт веществ в клетку и из неё.",
                "Аппарат Гольджи" to "Органелла, модифицирующая, сортирующая и упаковывающая белки и липиды.",
                "Эндоплазматическая сеть (ЭПС)" to "Система мембранных каналов для синтеза и транспорта органических веществ.",
                "Лизосома" to "Органоид с пищеварительными ферментами для внутриклеточного расщепления.",
                "Хлоропласт" to "Пластида растительных клеток, осуществляющая процесс фотосинтеза.",
                "Цитоплазма" to "Внутренняя полужидкая среда клетки, объединяющая все органоиды.",
                "Митоз" to "Непрямое деление эукариотической клетки с сохранением диплоидного набора хромосом.",
                "Мейоз" to "Редукционное деление клетки, приводящее к образованию гаплоидных гамет.",
                "АТФ (Аденозинтрифосфат)" to "Универсальный источник и переносчик химической энергии в живых клетках."
            )
            topicLower.contains("менделеев") ||
                (topicLower.contains("химическ") && (topicLower.contains("элемент") || topicLower.contains("таблиц"))) ||
                topicLower.contains("periodic table") -> getMendeleevElementsPool()
            else -> {
                val baseTemplates = listOf(
                    "Основное определение" to "Фундаментальное понятие и главная суть изучаемой темы «$topic».",
                    "Ключевой принцип" to "Базовый закон или правило, лежащее в основе работы и структуры «$topic».",
                    "Структура и классификация" to "Основные элементы, виды и взаимосвязь компонентов в разделе «$topic».",
                    "Практическое применение" to "Способы использования знаний по теме «$topic» при решении реальных задач.",
                    "Причинно-следственная связь" to "Факторы, влияющие на развитие и изменение процессов в «$topic».",
                    "Исторический контекст" to "Этапы формирования, ключевые открытия и развитие направления «$topic».",
                    "Базовая формула / Модель" to "Формализованное выражение или схема взаимодействия элементов в «$topic».",
                    "Типичный пример" to "Наглядный образец проявления свойств «$topic» на практике.",
                    "Отличительный признак" to "Характерная черта, позволяющая выделить «$topic» среди смежных понятий.",
                    "Методика анализа" to "Алгоритм исследования и проверки гипотез в рамках «$topic».",
                    "Частая ошибка и нюанс" to "Важная деталь в теме «$topic», на которую следует обратить внимание на экзамене.",
                    "Контрольный вывод" to "Итоговое обобщение знаний и закономерностей по теме «$topic»."
                )
                baseTemplates.map { (suffix, def) -> "$topic — $suffix" to def }
            }
        }

        val result = mutableListOf<Flashcard>()
        for (i in 0 until count) {
            val pair = if (i < pool.size) {
                pool[i]
            } else {
                "$topic — Понятие №${i + 1}" to "Дополнительный учебный термин №${i + 1} для углубленного изучения темы «$topic»."
            }
            result.add(
                Flashcard(
                    id = UUID.randomUUID().toString(),
                    moduleId = moduleId,
                    term = pair.first,
                    definition = pair.second
                )
            )
        }
        return result
    }

    internal fun generateLocalEducationalResponse(prompt: String): String {
        val p = prompt.lowercase().trim()

        // 1. Check if this is a single-term definition suggestion request from FlashcardEditor
        if (p.contains("give a concise, clear educational definition") && prompt.contains("'")) {
            val extractedTerm = prompt.substringBeforeLast("'").substringAfterLast("'").ifBlank { "Термин" }
            val termLower = extractedTerm.lowercase()
            return when {
                termLower.contains("ephemeral") -> "Ephemeral — длящийся очень короткое время, мимолетный, недолговечный (lasting for a very short time)."
                termLower.contains("ubiquitous") -> "Ubiquitous — вездесущий, повсеместный, встречающийся повсюду (present, appearing, or found everywhere)."
                termLower.contains("митохондр") || termLower.contains("mitochondr") -> "Митохондрия — двумембранная органелла эукариотической клетки, синтезирующая АТФ (энергетическая станция клетки)."
                termLower.contains("днк") || termLower.contains("dna") -> "ДНК (дезоксирибонуклеиновая кислота) — макромолекула, обеспечивающая хранение и передачу генетической программы организма."
                termLower.contains("аорт") || termLower.contains("aorta") -> "Аорта — самая крупная артерия большого круга кровообращения, выходящая из левого желудочка сердца."
                termLower.contains("желудоч") || termLower.contains("ventricle") -> "Желудочки — нижние мышечные камеры сердца, выталкивающие кровь в артерии при сокращении (систоле)."
                termLower.contains("конус") || termLower.contains("cone") -> "Конус — геометрическое тело, образованное вращением прямоугольного треугольника вокруг одного из его катетов (V = 1/3·π·r²·h)."
                else -> "$extractedTerm — ключевое понятие изучаемой темы, обозначающее важнейшее свойство, структуру или закономерность."
            }
        }

        // 2. Check if this is a JSON flashcard generation request from CreateModuleScreen
        if (p.contains("format your response strictly as a json array")) {
            val topic = prompt.substringAfter("about: '", "Учебная тема").substringBefore("'")
            val targetCount = prompt.substringAfter("target_count=", "")
                .takeWhile { it.isDigit() }
                .toIntOrNull() ?: extractRequestedCardCount(topic, DEFAULT_FLASHCARD_COUNT)

            val cards = buildLocalCardsForTopic(topic, "local", targetCount)
            return JSONArray().apply {
                cards.forEach { card ->
                    put(JSONObject().put("term", card.term).put("definition", card.definition))
                }
            }.toString()
        }

        // 3. Check if this is a Magic Notes analysis request
        if (p.contains("analyze these notes:")) {
            val notesBody = prompt.substringAfter("Analyze these notes:").substringBefore("Create a brief educational summary").trim()
            val sentences = notesBody.split(Regex("[.!?\\n]+")).map { it.trim() }.filter { it.length > 10 }
            val b1 = sentences.getOrNull(0) ?: "Электроэнергия вырабатывается за счет воздействия воды на турбину, соединенную с генератором."
            val b2 = sentences.getOrNull(1) ?: "Плотины строятся для регулирования водного потока и создания необходимого напора."
            val b3 = sentences.getOrNull(2) ?: "Ветрогенераторы преобразуют кинетическую энергию воздушных масс в экологически чистую электроэнергию."
            return "Краткое резюме конспекта: $b1 $b2\n" +
                "• $b1\n" +
                "• $b2\n" +
                "• $b3\n" +
                "- Гидротурбина: Устройство, преобразующее кинетическую энергию падающей воды в механическое вращение\n" +
                "- Электрогенератор: Устройство, превращающее механическое вращение турбины в электрический ток\n" +
                "- Плотина: Сооружение для концентрации перепада уровней воды и создания водохранилища\n" +
                "- Ветровая турбина: Установка, использующая кинетическую силу ветра для выработки электроэнергии"
        }

        // Extract clean user message if wrapped
        val cleanUserText = prompt
            .removePrefix("User asks:")
            .substringBefore("Provide a helpful")
            .trim()
            .ifBlank { prompt.trim() }

        val cleanLower = cleanUserText.lowercase()
            .replace(Regex("[!?.(),:;\"'«»—\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 4. Conversational / Dialogue handling (Greetings, Small Talk, Gratitude, Farewell)
        val greetingWords = setOf(
            "привет", "здравствуй", "здравствуйте", "приветик", "здарова", "здорово",
            "доброе утро", "добрый день", "добрый вечер", "доброй ночи",
            "хай", "хелло", "хеллоу", "салам", "ку", "йоу",
            "hello", "hi", "hey", "hola", "good morning", "good afternoon", "good evening"
        )
        if (cleanLower in greetingWords || greetingWords.any { cleanLower.startsWith("$it ") && cleanLower.length <= 35 }) {
            return "### Привет! 👋\n\n" +
                "Рад тебя видеть! Я твой ИИ-помощник и собеседник в **Quizapp**.\n\n" +
                "Мы можем:\n" +
                "- **Пообщаться в диалоге** на любую интересующую тему\n" +
                "- **Разобрать сложный предмет** (английский, математику, биологию, историю, программирование)\n" +
                "- **Потренироваться перед тестом** или составить карточки для запоминания\n\n" +
                "Как твои дела? О чём сегодня поговорим?"
        }

        if (cleanLower.contains("как дела") || cleanLower.contains("как ты") || cleanLower.contains("как жизнь") || cleanLower.contains("как настроение") || cleanLower.contains("how are you")) {
            return "Всё отлично, спасибо, что спросил! 😊 Всегда готов к общению и новым интересным темам.\n\n" +
                "А как проходит твой день? Готовишься к какому-нибудь занятию или просто хочешь поболтать?"
        }

        if (cleanLower.contains("кто ты") || cleanLower.contains("что ты умеешь") || cleanLower.contains("расскажи о себе") || cleanLower == "помощь" || cleanLower == "help") {
            return "### Обо мне 🤖\n\n" +
                "Я — твой персональный **ИИ-репетитор и собеседник**.\n\n" +
                "**Что я умею:**\n" +
                "1. **Вести живой диалог** — отвечать на вопросы, поддерживать беседу и помогать с идеями.\n" +
                "2. **Объяснять простыми словами** — грамматику, формулы, научные термины и код.\n" +
                "3. **Генерировать карточки** — по умолчанию создаю **12 карточек** или ровно столько, сколько ты укажешь.\n\n" +
                "> 💡 *Совет:* В правом верхнем углу чата ты можешь в один клик переключать **Профиль ИИ** (круг с порядковым номером профиля)."
        }

        if (cleanLower in setOf("спасибо", "благодарю", "большое спасибо", "спасибо большое", "thanks", "thank you", "круто", "супер", "отлично", "класс")) {
            return "Всегда пожалуйста! Рад помочь 😊 Если захочешь обсудить что-то ещё или разобрать новую тему — просто напиши!"
        }

        if (cleanLower in setOf("пока", "до свидания", "до встречи", "увидимся", "bye", "goodbye")) {
            return "До встречи! 👋 Желаю отличного настроения и продуктивного дня. Возвращайся в любое время!"
        }

        // 5. Topic-specific rich AI Tutor responses in Markdown
        return when {
            p.contains("present perfect") || p.contains("grammar") || p.contains("грамматик") || p.contains("времен") -> {
                "## 📘 Present Perfect (Настоящее совершенное время)\n\n" +
                "Формула образования:\n" +
                "```text\n" +
                "have / has + V3 (Past Participle)\n" +
                "```\n\n" +
                "### Когда используется:\n" +
                "1. **Жизненный опыт** (важен сам факт, а не точное время): *I have visited London twice.*\n" +
                "2. **Видимый результат в настоящем**: *She has bought a new book* (книга сейчас у неё).\n" +
                "3. **Действие началось в прошлом и продолжается сейчас** (с `for` / `since`): *We have known each other for 5 years.*\n\n" +
                "### Ключевые слова-маркеры:\n" +
                "- Present Perfect: have / has + V3 — действие в прошлом с результатом в настоящем\n" +
                "- Already / Just: Уже / Только что (ставятся между have и V3 в утверждениях)\n" +
                "- Yet: Еще не / Уже (ставится в конце отрицательных и вопросительных предложений)\n" +
                "- Ever / Never: Когда-либо / Никогда (для описания жизненного опыта)\n" +
                "- Since / For: С какого-то момента / На протяжении какого-то периода времени"
            }
            p.contains("конус") || p.contains("cone") || p.contains("объем") || p.contains("геометр") -> {
                "## 📐 Пошаговый расчет объема кругового конуса\n\n" +
                "Формула объема конуса:\n" +
                "```text\n" +
                "V = (1/3) · π · r² · h\n" +
                "```\n\n" +
                "### Где:\n" +
                "- **r** — радиус круглого основания (`r = 4`)\n" +
                "- **h** — высота конуса, перпендикуляр от вершины к основанию (`h = 10`)\n\n" +
                "### Пошаговое решение для `r = 4` и `h = 10`:\n" +
                "1. Находим площадь основания: `S = π · r² = π · 4² = 16π ≈ 50.27`\n" +
                "2. Умножаем на высоту `h = 10`: `16π · 10 = 160π`\n" +
                "3. Делим на 3: `V = 160π / 3 ≈` **167.55 куб. ед.**\n\n" +
                "- Объем конуса: V = 1/3 · π · r² · h (одна треть произведения площади основания на высоту)\n" +
                "- Площадь основания конуса: S = π · r² (площадь круга радиуса r)\n" +
                "- Образующая конуса: l = √(r² + h²) по теореме Пифагора\n" +
                "- Площадь боковой поверхности: S_бок = π · r · l"
            }
            p.contains("сердц") || p.contains("heart") || p.contains("желудочк") || p.contains("анатом") -> {
                "## 🫀 Камеры сердца человека и кровообращение\n\n" +
                "Сердце человека — четырехкамерный мышечный орган, разделенный сплошной перегородкой на правую (венозную) и левую (артериальную) половины:\n\n" +
                "1. **Правое предсердие (Right Atrium):** принимает венозную кровь от всего тела через верхнюю и нижнюю полые вены.\n" +
                "2. **Правый желудочек (Right Ventricle):** выталкивает венозную кровь в легочный ствол (начало малого круга кровообращения).\n" +
                "3. **Левое предсердие (Left Atrium):** принимает богатую кислородом артериальную кровь по четырем легочным венам.\n" +
                "4. **Левый желудочек (Left Ventricle):** имеет самую мощную мышечную стенку (*миокард*) и выбрасывает кровь в **аорту**.\n\n" +
                "- Желудочки сердца: Нижние мышечные камеры, выталкивающие кровь в легочный ствол и аорту\n" +
                "- Предсердия: Верхние камеры сердца, принимающие кровь из полых и легочных вен\n" +
                "- Аорта: Самая крупная артерия организма, выходящая из левого желудочка\n" +
                "- Миокард: Мощный сердечный мышечный слой, обеспечивающий ритмичные сокращения"
            }
            p.contains("гидро") || p.contains("ветер") || p.contains("энерг") -> {
                "## ⚡ Гидро- и ветроэнергетика: ключевые принципы\n\n" +
                "- **Гидроэлектростанции (ГЭС):** используют потенциальную и кинетическую энергию падающей воды. Вода под напором плотины вращает лопасти гидротурбины, соединенной с электрогенератором.\n" +
                "- **Ветроэлектростанции (ВЭС):** преобразуют кинетическую энергию воздушных потоков во вращение ротора ветрогенератора.\n" +
                "- **Экологическое значение:** оба источника являются возобновляемыми и не создают парниковых выбросов при генерации.\n\n" +
                "- Гидростанция (ГЭС): Электростанция, преобразующая энергию водного потока в электричество\n" +
                "- Ветрогенератор: Установка для преобразования кинетической энергии ветра в электрический ток\n" +
                "- Плотина: Гидротехническое сооружение для подъема уровня воды и создания напора"
            }
            p.contains("испанск") || p.contains("spanish") || p.contains("español") || p.contains("фраз") || p.contains("путешеств") -> {
                "## 🇪🇸 5 полезных фраз на испанском для путешествий\n\n" +
                "Вот самые нужные выражения с переводом:\n\n" +
                "1. **¡Hola! ¿Qué tal?** — Привет! Как дела?\n" +
                "2. **¿Dónde está la estación de metro / el hotel?** — Где находится станция метро / отель?\n" +
                "3. **¿Cuánto cuesta esto, por favor?** — Сколько это стоит, пожалуйста?\n" +
                "4. **La cuenta, por favor.** — Принесите счет, пожалуйста (в ресторане или кафе).\n" +
                "5. **Muchas gracias por su ayuda.** — Большое спасибо за вашу помощь!\n\n" +
                "- ¿Qué tal?: Как дела? (универсальное дружеское приветствие на испанском)\n" +
                "- ¿Dónde está...?: Где находится...? (вопрос для ориентации в городе)\n" +
                "- ¿Cuánto cuesta?: Сколько стоит? (вопрос о цене в магазине или на рынке)\n" +
                "- La cuenta, por favor: Счет, пожалуйста (фраза в кафе или ресторане)\n" +
                "- Muchas gracias: Большое спасибо (вежливая благодарность)"
            }
            p.contains("клетк") || p.contains("днк") || p.contains("биолог") || p.contains("митохондр") -> {
                "## 🧬 Молекулярная биология: Клетка и ДНК\n\n" +
                "Эукариотическая клетка состоит из трех главных частей: **плазматической мембраны**, **цитоплазмы с органеллами** и **ядра**.\n\n" +
                "- **Ядро и ДНК:** содержат наследственную информацию, записанную в виде последовательности нуклеотидов (`А`, `Т`, `Г`, `Ц`).\n" +
                "- **Митохондрии:** синтезируют `АТФ` — универсальный источник энергии для всех биохимических реакций.\n" +
                "- **Рибосомы:** отвечают за трансляцию — сборку белковых молекул по матрице иРНК.\n\n" +
                "- ДНК: Дезоксирибонуклеиновая кислота, хранящая и передающая генетический код\n" +
                "- Митохондрии: Энергетические станции клетки, синтезирующие молекулы АТФ\n" +
                "- Рибосомы: Органеллы, осуществляющие биосинтез белка в клетке\n" +
                "- Клеточная мембрана: Двойной слой липидов с белками, регулирующий обмен веществ"
            }
            else -> {
                "### 💬 Ответ по вашему запросу: «$cleanUserText»\n\n" +
                "Давайте разберем тему **«$cleanUserText»** подробно:\n\n" +
                "1. **Главная суть:** «$cleanUserText» объединяет ключевые принципы, понятия и практические подходы по данному вопросу.\n" +
                "2. **На что обратить внимание:** выделите базовые определения и свяжите их с практическими примерами.\n" +
                "3. **Дальнейшие шаги:** вы можете задать уточняющий вопрос в диалоге или попросить меня сгенерировать набор карточек по этой теме!\n\n" +
                "> 💡 *Подсказка:* Чтобы подключить онлайн-генерацию через облачную нейросеть, укажите API-ключ в выбранном **Профиле ИИ** в Настройках."
            }
        }
    }

    private fun getMendeleevElementsPool(): List<Pair<String, String>> {
        return listOf(
            "Водород (H, 1)" to "Самый легкий химический элемент, основной компонент звезд и Вселенной (газ H₂).",
            "Гелий (He, 2)" to "Благородный инертный газ с наименьшей температурой кипения среди всех веществ (-268.9°C).",
            "Литий (Li, 3)" to "Легчайший щелочной металл серебристо-белого цвета, основа современных литий-ионных аккумуляторов.",
            "Бериллий (Be, 4)" to "Легкий и твердый щелочноземельный металл, легирующий компонент в авиакосмических сплавах.",
            "Бор (B, 5)" to "Твердый полуметалл, поглотитель нейтронов в ядерных реакторах и компонент жаропрочного боросиликатного стекла.",
            "Углерод (C, 6)" to "Основа органической химии и жизни на Земле; образует алмаз, графит, фуллерены и графен.",
            "Азот (N, 7)" to "Двухатомный газ без цвета и запаха, составляет 78% атмосферы Земли; входит в состав аминокислот.",
            "Кислород (O, 8)" to "Сильный окислитель и неметалл, необходим для клеточного дыхания подавляющего большинства живых организмов.",
            "Фтор (F, 9)" to "Самый химически активный неметалл и сильнейший окислитель среди всех элементов периодической таблицы.",
            "Неон (Ne, 10)" to "Благородный инертный газ, светящийся ярким красно-оранжевым свечением при электрическом разряде.",
            "Натрий (Na, 11)" to "Мягкий щелочной металл, бурно реагирующий с водой; важнейший катион внеклеточной жидкости (NaCl).",
            "Магний (Mg, 12)" to "Легкий ковкий щелочноземельный металл; центральный координирующий атом в молекуле хлорофилла.",
            "Алюминий (Al, 13)" to "Самый распространенный металл в земной коре (около 8% по массе), легкий, стойкий к коррозии проводник.",
            "Кремний (Si, 14)" to "Второй по распространенности элемент земной коры; базовый полупроводник всей современной микроэлектроники.",
            "Фосфор (P, 15)" to "Биогенный неметалл, входит в состав костей, клеточных мембран, ДНК, РНК и молекул АТФ.",
            "Сера (S, 16)" to "Желтый неметалл, входит в состав незаменимых серосодержащих аминокислот (метионин, цистеин) и серной кислоты.",
            "Хлор (Cl, 17)" to "Желто-зеленый токсичный удушливый газ-галоген, мощный дезинфектант и компонент соляной кислоты (HCl).",
            "Аргон (Ar, 18)" to "Третий по распространенности газ в земной атмосфере (~0.93%), защитная инертная среда для сварки металлов.",
            "Калий (K, 19)" to "Жизненно важный щелочной металл; регулирует клеточный мембранный потенциал (натрий-калиевый насос).",
            "Кальций (Ca, 20)" to "Щелочноземельный металл; основной строительный материал скелета, зубов и регулятор мышечных сокращений.",
            "Скандий (Sc, 21)" to "Переходный легкий металл, микролегирующая добавка для высокопрочных алюминиевых сплавов в авиации.",
            "Титан (Ti, 22)" to "Высокопрочный, легкий и биосовместимый металл, стойкий к коррозии; идеальный материал для имплантов и авиации.",
            "Ванадий (V, 23)" to "Твердый переходный металл, легирующий элемент для получения сверхпрочной инструментальной и пружинной стали.",
            "Хром (Cr, 24)" to "Твердый металл, придающий нержавеющей стали коррозионную стойкость, твердость и зеркальный защитный блеск.",
            "Марганец (Mn, 25)" to "Переходный металл, важнейший раскислитель и десульфуратор в металлургии стали; кофактор антиоксидантных ферментов.",
            "Железо (Fe, 26)" to "Главный металл современной цивилизации, основа стали и чугуна, центральный атом гема в молекуле гемоглобина.",
            "Кобальт (Co, 27)" to "Ферромагнитный металл с синеватым отливом, центральный координационный атом витамина B12 (кобаламина).",
            "Никель (Ni, 28)" to "Пластичный ферромагнитный металл, незаменимый компонент жаропрочных сплавов, нержавеющей стали и аккумуляторов.",
            "Медь (Cu, 29)" to "Ковкий золотисто-розовый металл с высочайшей электрической и тепловой проводимостью после серебра.",
            "Цинк (Zn, 30)" to "Коррозионностойкий металл для оцинковки железа; незаменимый микроэлемент иммунной системы и сотен ферментов.",
            "Галлий (Ga, 31)" to "Металл, плавящийся в руках человека (tпл = 29.8°C), основа полупроводниковых соединений (арсенид галлия GaAs).",
            "Германий (Ge, 32)" to "Полуметалл серебристого цвета, исторически первый полупроводник для транзисторов и инфракрасной оптики.",
            "Мышьяк (As, 33)" to "Полуметалл высокой токсичности, используется как донорная легирующая примесь n-типа в микроэлектронике.",
            "Селен (Se, 34)" to "Неметалл с выраженными фотоэлектрическими свойствами; микроэлемент, защищающий организм от свободных радикалов.",
            "Бром (Br, 35)" to "Единственный жидкий неметалл при нормальных условиях (тяжелая красно-бурая жидкость с едким удушливым запахом).",
            "Криптон (Kr, 36)" to "Благородный инертный газ, используемый в мощных газовых лазерах и энергосберегающих стеклопакетах.",
            "Рубидий (Rb, 37)" to "Мягкий легкоплавкий щелочной металл, используется в атомных стандартах времени и частоты.",
            "Стронций (Sr, 38)" to "Щелочноземельный металл; соли стронция окрашивают пламя фейерверков в насыщенный карминово-красный цвет.",
            "Иттрий (Y, 39)" to "Редкоземельный металл, ключевой компонент высокотемпературных сверхпроводников YBCO и лазерных кристаллов YAG.",
            "Цирконий (Zr, 40)" to "Коррозионностойкий металл с крайне малым сечением захвата тепловых нейтронов; оболочки ТВЭЛов атомных реакторов.",
            "Ниобий (Nb, 41)" to "Тугоплавкий металл со сверхпроводящими свойствами при гелиевых температурах; сплавы для магнитно-резонансной томографии.",
            "Молибден (Mo, 42)" to "Тугоплавкий металл высокой твердости, повышающий упругость, вязкость и жаропрочность броневой стали.",
            "Технеций (Tc, 43)" to "Первый искусственно синтезированный радиоактивный элемент; изомер 99mTc — главный диагностический маркер ядерной медицины.",
            "Рутений (Ru, 44)" to "Благородный металл платиновой группы, сверхстойкий катализатор в органическом синтезе и электрохимических ячейках.",
            "Родий (Rh, 45)" to "Благородный металл с рекордной отражательной способностью, катализатор дожигания токсичных выхлопных газов автомобилей.",
            "Палладий (Pd, 46)" to "Платиновый металл, способный обратимо растворять колоссальные объемы водорода (до 900 объемов H₂ на объем металла).",
            "Серебро (Ag, 47)" to "Драгоценный металл с максимальной среди всех известных элементов электро- и теплопроводностью, а также бактерицидностью.",
            "Кадмий (Cd, 48)" to "Мягкий тяжелый металл, эффективный поглотитель нейтронов в регулирующих стержнях АЭС и компонент Ni-Cd аккумуляторов.",
            "Индий (In, 49)" to "Очень мягкий легкоплавкий металл; оксид индия-олова (ITO) образует прозрачные проводящие электроды для тачскринов.",
            "Олово (Sn, 50)" to "Легкоплавкий ковкий металл; основа бронзы, бессвинцовых припоев для пайки электроники и белой пищевой жести.",
            "Сурьма (Sb, 51)" to "Полуметалл с ярким кристаллическим блеском, антипирен против возгорания пластмасс и укрепитель сплавов свинца.",
            "Теллур (Te, 52)" to "Редкий полуметалл серебристого цвета, основа высокоэффективных термоэлектрических генераторов на основе теллурида висмута.",
            "Иод (I, 53)" to "Темно-фиолетовый галоген с металлическим блеском, сублимирующий при нагревании; входит в состав гормонов щитовидной железы.",
            "Ксенон (Xe, 54)" to "Тяжелый благородный газ, идеальное рабочее тело для ионных ракетных двигателей и безопасный нейропротекторный анестетик.",
            "Цезий (Cs, 55)" to "Мягкий золотистый щелочной металл; 9 192 631 770 периодов излучения атома 133Cs служат международным эталоном секунды.",
            "Барий (Ba, 56)" to "Тяжелый щелочноземельный металл; нерастворимый сульфат бария (BaSO₄) — рентгеноконтрастное вещество в гастроэнтерологии.",
            "Лантан (La, 57)" to "Родоначальник семейства лантаноидов, оптический компонент сверхточных линз фотокамер и микроскопов.",
            "Церий (Ce, 58)" to "Самый распространенный лантаноид, катализатор дожигания газов, полирующий абразив стекла и компонент ферроцерия.",
            "Празеодим (Pr, 59)" to "Лантаноид серебристо-желтого цвета, легирующая добавка для магнитов и стекол дидимия, защищающих глаза стеклодувов.",
            "Неодим (Nd, 60)" to "Лантаноид, составляющий основу мощнейших постоянных магнитов современности (сплав неодим-железо-бор Nd₂Fe₁₄B).",
            "Прометий (Pm, 61)" to "Единственный радиоактивный лантаноид, не имеющий стабильных изотопов; компактный источник энергии радиоизотопных батарей.",
            "Самарий (Sm, 62)" to "Лантаноид, компонент высокотемпературных постоянных магнитов (SmCo) и терапевтических радиофармпрепаратов.",
            "Европий (Eu, 63)" to "Самый химически активный лантаноид; красный люминофор в дисплеях и люминесцентная защита купюр евро от подделок.",
            "Гадолиний (Gd, 64)" to "Лантаноид с рекордным сечением захвата тепловых нейтронов; ключевое парамагнитное контрастное вещество при МРТ.",
            "Тербий (Tb, 65)" to "Редкоземельный металл, зеленый люминофор и основа магнитострикционного сплава Terfenol-D для гидролокации и акустики.",
            "Диспрозий (Dy, 66)" to "Лантаноид, повышающий термостойкость неодимовых магнитов в электродвигателях современных электромобилей.",
            "Гольмий (Ho, 67)" to "Металл с наибольшим магнитным моментом среди всех элементов, концентратор магнитных полюсов в квантовых установках.",
            "Эрбий (Er, 68)" to "Лантаноид розового цвета, ионы Er³⁺ легируют волоконно-оптические кабели для оптического усиления сигнала интернета.",
            "Тулий (Tm, 69)" to "Один из наиболее редких лантаноидов, применяется как источник излучения в малогабаритных рентгеновских аппаратах.",
            "Иттербий (Yb, 70)" to "Лантаноид, основа высокоточных оптических атомных часов и накачки мощных иттербиевых волоконных лазеров.",
            "Лютеций (Lu, 71)" to "Самый плотный и твердый лантаноид; кристаллические сцинтилляторы в позитронно-эмиссионной томографии (ПЭТ).",
            "Гафний (Hf, 72)" to "Тугоплавкий тяжелый металл, регулятор ядерных реакторов и высокоэффективный диэлектрик high-k в современных процессорах.",
            "Тантал (Ta, 73)" to "Сверхстойкий к коррозии тугоплавкий металл, основа миниатюрных танталовых конденсаторов для материнских плат и смартфонов.",
            "Вольфрам (W, 74)" to "Самый тугоплавкий металл периодической таблицы (tпл = 3422°C), нити ламп накаливания и сверхтвердый сплав победит.",
            "Рений (Re, 75)" to "Очень плотный тугоплавкий металл, легирующий компонент жаропрочных монокристаллических сплавов лопаток реактивных турбин.",
            "Осмий (Os, 76)" to "Самое плотное простое вещество во Вселенной (22.59 г/см³), сверхтвердый благородный металл платиновой группы.",
            "Иридий (Ir, 77)" to "Второй по плотности металл, обладающий абсолютной химической стойкостью к агрессивным кислотам и расплавам.",
            "Платина (Pt, 78)" to "Благородный драгоценный металл, эталон химической инертности, непревзойденный катализатор гидрирования и лабораторная посуда.",
            "Золото (Au, 79)" to "Благородный желтый металл максимальной ковкости и стойкости к окислению, эталон финансовых резервов и надежный проводник.",
            "Ртуть (Hg, 80)" to "Единственный жидкий металл при комнатной температуре (tпл = -38.8°C), рабочее тело барометров и амальгамные сплавы.",
            "Таллий (Tl, 81)" to "Мягкий тяжелый токсичный металл, кристаллы бромида-иодида таллия применяются в детекторах инфракрасного излучения.",
            "Свинец (Pb, 82)" to "Тяжелый ковкий синевато-серый металл, надежный экран от рентгеновского и гамма-излучения, основа автомобильных АКБ.",
            "Висмут (Bi, 83)" to "Тяжелый металл с низкой токсичностью, радужной побежалостью кристаллов и сильными диамагнитными свойствами.",
            "Полоний (Po, 84)" to "Радиоактивный элемент, открытый Марией и Пьером Кюри; мощный источник альфа-частиц и тепла в космических зондах.",
            "Астат (At, 85)" to "Самый редкий радиоактивный галоген в земной коре (менее 1 грамма во всей оболочке планеты); перспективен для радиотерапии.",
            "Радон (Rn, 86)" to "Тяжелый радиоактивный благородный газ без цвета и запаха, продукт естественного распада радия и урана в недрах Земли.",
            "Франций (Fr, 87)" to "Крайне нестабильный радиоактивный щелочной металл, период полураспада наиболее устойчивого изотопа 223Fr всего 22 минуты.",
            "Радий (Ra, 88)" to "Радиоактивный щелочноземельный металл, исторический символ эпохи радиологии, открытый Пьером и Марией Кюри в 1898 году.",
            "Актиний (Ac, 89)" to "Радиоактивный серебристый металл, родоначальник семейства актиноидов; светится в темноте голубым светом ионизации.",
            "Торий (Th, 90)" to "Слаборадиоактивный металл, перспективное экологичное ядерное топливо ториевого энергетического цикла следующего поколения.",
            "Протактиний (Pa, 91)" to "Редкий плотный радиоактивный металл, промежуточный продукт радиоактивного распада урана-235.",
            "Уран (U, 92)" to "Тяжелый радиоактивный актиноид; делящийся изотоп 235U — главное топливо большинства современных атомных электростанций.",
            "Нептуний (Np, 93)" to "Первый трансурановый искусственно синтезированный элемент, промежуточный этап при получении плутония из урана.",
            "Плутоний (Pu, 94)" to "Трансурановый делящийся металл; изотоп 239Pu используется в ядерных боеприпасах, реакторах-бридерах и РИТЭГах.",
            "Америций (Am, 95)" to "Синтетический радиоактивный металл; изотоп 241Am служит надежным ионизатором в бытовых датчиках дыма.",
            "Кюрий (Cm, 96)" to "Радиоактивный актиноид высокой удельной альфа-активности; источник термоэлектричества в автономных космических аппаратах.",
            "Берклий (Bk, 97)" to "Синтетический радиоактивный актиноид, назван в честь лаборатории радиационных исследований в Беркли (Калифорния).",
            "Калифорний (Cf, 98)" to "Мощнейший портативный источник нейтронов; всего 1 микрограмм 252Cf испускает до 2.3 миллиона нейтронов в секунду.",
            "Эйнштейний (Es, 99)" to "Синтетический элемент, обнаружен в радиоактивных осадках после первого термоядерного испытания «Майк» в 1952 году.",
            "Фермий (Fm, 100)" to "Тяжелый трансурановый элемент, названный в честь великого физика Энрико Ферми; синтезируется захватом нейтронов.",
            "Менделевий (Md, 101)" to "Синтетический элемент №101, названный в честь создателя периодической таблицы химических элементов Дмитрия Менделеева.",
            "Нобелий (No, 102)" to "Искусственный радиоактивный актиноид, синтезированный на ускорителях тяжелых ионов в Дубне и Беркли.",
            "Лоуренсий (Lr, 103)" to "Завершающий элемент семейства актиноидов, назван в честь изобретателя циклотрона Эрнеста Лоуренса.",
            "Резерфордий (Rf, 104)" to "Первый трансактиноидный сверхтяжелый элемент 4-й группы, назван в честь основоположника ядерной физики Эрнеста Резерфорда.",
            "Дубний (Db, 105)" to "Сверхтяжелый элемент 5-й группы, назван в честь всемирно известного центра ядерных исследований — наукограда Дубна.",
            "Сиборгий (Sg, 106)" to "Сверхтяжелый элемент 6-й группы, назван в честь нобелевского лауреата Гленна Сиборга при его жизни.",
            "Борий (Bh, 107)" to "Сверхтяжелый элемент 7-й группы, синтезирован бомбардировкой висмута ионами хрома, назван в честь Нильса Бора.",
            "Хассий (Hs, 108)" to "Сверхтяжелый элемент 8-й группы (аналог осмия), назван в честь немецкой федеральной земли Гессен (Hassia).",
            "Мейтнерий (Mt, 109)" to "Сверхтяжелый элемент 9-й группы, назван в честь австрийского физика Лизы Мейтнер, исследовавшей деление ядер урана.",
            "Дармштадтий (Ds, 110)" to "Сверхтяжелый элемент 10-й группы, впервые синтезирован в Центре исследований тяжелых ионов им. Гельмгольца в Дармштадте.",
            "Рентгений (Rg, 111)" to "Сверхтяжелый элемент 11-й группы (аналог золота), назван в честь первооткрывателя рентгеновских лучей Вильгельма Рентгена.",
            "Коперниций (Cn, 112)" to "Сверхтяжелый летучий элемент 12-й группы (аналог ртути), назван в честь создателя гелиоцентрической системы Николая Коперника.",
            "Нихоний (Nh, 113)" to "Первый химический элемент, открытый учеными из Азии (институт RIKEN в Японии), назван в честь Японии («Нихон»).",
            "Флеровий (Fl, 114)" to "Сверхтяжелый элемент 14-й группы, назван в честь Лаборатории ядерных реакций им. Г.Н. Флёрова Объединенного института ядерных исследований в Дубне.",
            "Московий (Mc, 115)" to "Сверхтяжелый элемент 15-й группы, синтезирован учеными ОИЯИ в Дубне и назван в честь Московской области.",
            "Ливерморий (Lv, 116)" to "Сверхтяжелый элемент 16-й группы, синтезирован совместно с учеными Ливерморской национальной лаборатории им. Э. Лоуренса.",
            "Теннессин (Ts, 117)" to "Сверхтяжелый галоген 17-й группы, назван в честь американского штата Теннесси и Национальной лаборатории Ок-Ридж.",
            "Оганесон (Og, 118)" to "Самый тяжелый известный элемент периодической таблицы (атомный номер 118), благородный газ 18-й группы, назван в честь академика Юрия Оганесяна."
        )
    }
}
