package com.example.quizapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.quizapp.data.ai.AiClient
import com.example.quizapp.data.model.AiProfile
import com.example.quizapp.data.model.AiProvider
import com.example.quizapp.data.model.AppColorTheme
import com.example.quizapp.data.model.AppLanguage
import com.example.quizapp.data.model.AppThemeMode
import com.example.quizapp.data.model.ChatMessage
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.MagicNotesResult
import com.example.quizapp.data.model.StudyModule
import com.example.quizapp.data.model.UserStudyStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class QuizRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("quizapp_prefs", Context.MODE_PRIVATE)
    private val aiClient = AiClient()

    private val _modules = MutableStateFlow<List<StudyModule>>(emptyList())
    val modules: StateFlow<List<StudyModule>> = _modules.asStateFlow()

    private val _activeLanguage = MutableStateFlow(AppLanguage.RU)
    val activeLanguage: StateFlow<AppLanguage> = _activeLanguage.asStateFlow()

    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(AppColorTheme.SYSTEM)
    val colorTheme: StateFlow<AppColorTheme> = _colorTheme.asStateFlow()

    private val _userStats = MutableStateFlow(loadUserStats())
    val userStats: StateFlow<UserStudyStats> = _userStats.asStateFlow()

    private val defaultInitialProfile = AiProfile(
        id = "profile_default_1",
        orderNumber = 1,
        name = "Основной профиль",
        provider = AiProvider.GOOGLE_GEMINI,
        apiKey = "",
        modelId = AiProvider.GOOGLE_GEMINI.defaultModel
    )

    private val _aiProfiles = MutableStateFlow<List<AiProfile>>(listOf(defaultInitialProfile))
    val aiProfiles: StateFlow<List<AiProfile>> = _aiProfiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(defaultInitialProfile)
    val activeProfile: StateFlow<AiProfile> = _activeProfile.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        loadSettings()
        initDefaultModules()
        initInitialChat()
    }

    private fun loadUserStats(): UserStudyStats {
        val streak = prefs.getInt("stats_streak_days", 1)
        val goal = prefs.getInt("stats_daily_goal", 20)
        val today = prefs.getInt("stats_today_studied", 0)
        val total = prefs.getInt("stats_total_learned", 0)
        return UserStudyStats(
            streakDays = streak,
            dailyGoal = goal,
            todayStudied = today,
            totalCardsLearned = total
        )
    }

    private fun persistUserStats(stats: UserStudyStats) {
        prefs.edit()
            .putInt("stats_streak_days", stats.streakDays)
            .putInt("stats_daily_goal", stats.dailyGoal)
            .putInt("stats_today_studied", stats.todayStudied)
            .putInt("stats_total_learned", stats.totalCardsLearned)
            .apply()
    }

    private fun loadSettings() {
        val langCode = prefs.getString("app_lang", AppLanguage.RU.code) ?: AppLanguage.RU.code
        _activeLanguage.value = AppLanguage.entries.find { it.code == langCode } ?: AppLanguage.RU

        val themeKey = prefs.getString("app_theme_mode", AppThemeMode.SYSTEM.key) ?: AppThemeMode.SYSTEM.key
        _themeMode.value = AppThemeMode.entries.find { it.key == themeKey } ?: AppThemeMode.SYSTEM

        val colorKey = prefs.getString("app_color_theme", AppColorTheme.SYSTEM.key) ?: AppColorTheme.SYSTEM.key
        _colorTheme.value = AppColorTheme.entries.find { it.key == colorKey } ?: AppColorTheme.SYSTEM

        val profilesJson = prefs.getString("ai_profiles_json", null)
        val loadedProfiles = if (!profilesJson.isNullOrBlank()) {
            parseAiProfilesJson(profilesJson)
        } else {
            emptyList()
        }

        val finalProfiles = if (loadedProfiles.isNotEmpty()) {
            loadedProfiles.sortedBy { it.orderNumber }
        } else {
            listOf(defaultInitialProfile)
        }
        _aiProfiles.value = finalProfiles

        val activeId = prefs.getString("active_ai_profile_id", null)
        val chosen = finalProfiles.find { it.id == activeId } ?: finalProfiles.first()
        _activeProfile.value = chosen

        if (loadedProfiles.isEmpty()) {
            persistAiProfiles(finalProfiles, chosen.id)
        }
    }

    fun setLanguage(language: AppLanguage) {
        _activeLanguage.value = language
        prefs.edit().putString("app_lang", language.code).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("app_theme_mode", mode.key).apply()
    }

    fun setColorTheme(theme: AppColorTheme) {
        _colorTheme.value = theme
        prefs.edit().putString("app_color_theme", theme.key).apply()
    }

    fun addAiProfile(
        name: String,
        provider: AiProvider,
        apiKey: String,
        modelId: String
    ): AiProfile {
        val current = _aiProfiles.value
        val nextOrderNumber = (current.maxOfOrNull { it.orderNumber } ?: 0) + 1
        val cleanName = name.trim().ifBlank { "Профиль #${nextOrderNumber}" }
        val cleanModel = modelId.trim().ifBlank { provider.defaultModel }

        val newProfile = AiProfile(
            id = "profile_" + UUID.randomUUID().toString(),
            orderNumber = nextOrderNumber,
            name = cleanName,
            provider = provider,
            apiKey = apiKey.trim(),
            modelId = cleanModel
        )
        val updatedList = (current + newProfile).sortedBy { it.orderNumber }
        _aiProfiles.value = updatedList
        _activeProfile.value = newProfile
        persistAiProfiles(updatedList, newProfile.id)
        return newProfile
    }

    fun updateAiProfile(updated: AiProfile) {
        val current = _aiProfiles.value.toMutableList()
        val idx = current.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            val sanitized = updated.copy(
                name = updated.name.trim().ifBlank { "Профиль #${updated.orderNumber}" },
                apiKey = updated.apiKey.trim(),
                modelId = updated.modelId.trim().ifBlank { updated.provider.defaultModel }
            )
            current[idx] = sanitized
            val sorted = current.sortedBy { it.orderNumber }
            _aiProfiles.value = sorted
            if (_activeProfile.value.id == sanitized.id) {
                _activeProfile.value = sanitized
            }
            persistAiProfiles(sorted, _activeProfile.value.id)
        }
    }

    fun deleteAiProfile(profileId: String) {
        val remaining = _aiProfiles.value.filter { it.id != profileId }
        val finalProfiles = if (remaining.isNotEmpty()) {
            remaining.sortedBy { it.orderNumber }
        } else {
            listOf(defaultInitialProfile)
        }
        _aiProfiles.value = finalProfiles
        val newActive = if (_activeProfile.value.id == profileId) {
            finalProfiles.first()
        } else {
            finalProfiles.find { it.id == _activeProfile.value.id } ?: finalProfiles.first()
        }
        _activeProfile.value = newActive
        persistAiProfiles(finalProfiles, newActive.id)
    }

    fun selectAiProfile(profileId: String) {
        val target = _aiProfiles.value.find { it.id == profileId } ?: return
        _activeProfile.value = target
        prefs.edit().putString("active_ai_profile_id", target.id).apply()
    }

    suspend fun fetchModelsForProvider(provider: AiProvider, apiKey: String = ""): List<String> {
        return aiClient.fetchAvailableModels(provider, apiKey)
    }

    suspend fun fetchModelsForProviderResult(provider: AiProvider, apiKey: String = ""): com.example.quizapp.data.ai.FetchModelsResult {
        return aiClient.fetchAvailableModelsWithResult(provider, apiKey)
    }

    private fun persistAiProfiles(profiles: List<AiProfile>, activeProfileId: String) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("orderNumber", p.orderNumber)
                    put("name", p.name)
                    put("provider", p.provider.name)
                    put("apiKey", p.apiKey)
                    put("modelId", p.modelId)
                }
            )
        }
        prefs.edit()
            .putString("ai_profiles_json", arr.toString())
            .putString("active_ai_profile_id", activeProfileId)
            .apply()
    }

    private fun parseAiProfilesJson(jsonStr: String): List<AiProfile> {
        val list = mutableListOf<AiProfile>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val providerName = obj.optString("provider", AiProvider.GOOGLE_GEMINI.name)
                val provider = try {
                    AiProvider.valueOf(providerName)
                } catch (_: Exception) {
                    AiProvider.GOOGLE_GEMINI
                }
                val orderNumber = obj.optInt("orderNumber", i + 1).coerceAtLeast(1)
                val id = obj.optString("id").ifBlank { "profile_${orderNumber}_${UUID.randomUUID()}" }
                val name = obj.optString("name").ifBlank { "Профиль #$orderNumber" }
                val apiKey = obj.optString("apiKey", "")
                val modelId = obj.optString("modelId", provider.defaultModel).ifBlank { provider.defaultModel }

                list.add(
                    AiProfile(
                        id = id,
                        orderNumber = orderNumber,
                        name = name,
                        provider = provider,
                        apiKey = apiKey,
                        modelId = modelId
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun initDefaultModules() {
        val deletedIds = prefs.getString("deleted_module_ids", "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val savedCustomJson = prefs.getString("custom_modules_json", null)
        val customList = if (!savedCustomJson.isNullOrBlank()) {
            parseModulesJson(savedCustomJson).filter { mod ->
                mod.id !in deletedIds &&
                    !mod.title.lowercase().startsWith("какие модули у меня") &&
                    !mod.title.lowercase().startsWith("какие карточки у меня")
            }
        } else {
            emptyList()
        }
        val seedList = createSeedModules().filter { it.id !in deletedIds }
        val combined = applySavedCardProgress(seedList + customList)
        _modules.value = combined
    }

    private fun applySavedCardProgress(modulesList: List<StudyModule>): List<StudyModule> {
        val progressJsonStr = prefs.getString("card_progress_json", null) ?: return modulesList
        return try {
            val root = JSONObject(progressJsonStr)
            modulesList.map { mod ->
                val updatedCards = mod.cards.map { card ->
                    val key = "${mod.id}:${card.id}"
                    val cObj = root.optJSONObject(key)
                    if (cObj != null) {
                        val score = cObj.optInt("masteryScore", card.masteryScore).coerceIn(0, 2)
                        card.copy(
                            masteryScore = score,
                            isMastered = score >= 2 || cObj.optBoolean("isMastered", card.isMastered),
                            needsReview = cObj.optBoolean("needsReview", card.needsReview),
                            isStarred = cObj.optBoolean("isStarred", card.isStarred),
                            reviewCount = cObj.optInt("reviewCount", card.reviewCount),
                            lastStudiedTimestamp = cObj.optLong("lastStudiedTimestamp", card.lastStudiedTimestamp),
                            easeFactor = cObj.optDouble("easeFactor", card.easeFactor.toDouble()).toFloat(),
                            intervalDays = cObj.optInt("intervalDays", card.intervalDays),
                            repetitions = cObj.optInt("repetitions", card.repetitions),
                            lapses = cObj.optInt("lapses", card.lapses),
                            nextReviewTimestamp = cObj.optLong("nextReviewTimestamp", card.nextReviewTimestamp)
                        )
                    } else {
                        card
                    }
                }
                mod.copy(cards = updatedCards)
            }
        } catch (_: Exception) {
            modulesList
        }
    }

    private fun persistCardProgress(modulesList: List<StudyModule>) {
        val root = JSONObject()
        modulesList.forEach { mod ->
            mod.cards.forEach { c ->
                if (c.masteryScore > 0 || c.isMastered || c.needsReview || c.isStarred || c.reviewCount > 0 || c.lastStudiedTimestamp > 0L) {
                    val key = "${mod.id}:${c.id}"
                    root.put(
                        key,
                        JSONObject().apply {
                            put("masteryScore", c.masteryScore)
                            put("isMastered", c.isMastered)
                            put("needsReview", c.needsReview)
                            put("isStarred", c.isStarred)
                            put("reviewCount", c.reviewCount)
                            put("lastStudiedTimestamp", c.lastStudiedTimestamp)
                            put("easeFactor", c.easeFactor.toDouble())
                            put("intervalDays", c.intervalDays)
                            put("repetitions", c.repetitions)
                            put("lapses", c.lapses)
                            put("nextReviewTimestamp", c.nextReviewTimestamp)
                        }
                    )
                }
            }
        }
        prefs.edit().putString("card_progress_json", root.toString()).apply()
    }

    private fun initInitialChat() {
        val initialGreeting = ChatMessage(
            id = "msg_0",
            text = "### Привет! Я твой ИИ-репетитор в **QuizApp**\n\n" +
                "Спрашивай меня обо всём: грамматика языков, формулы, исторические факты или медицина.\n\n" +
                "- Объясню сложную тему простыми словами\n" +
                "- Создам карточки прямо из нашего диалога",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        _chatMessages.value = listOf(initialGreeting)
    }

    fun addChatMessage(text: String, isUser: Boolean, suggestedCards: List<Pair<String, String>> = emptyList()): ChatMessage {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isUser = isUser,
            timestamp = System.currentTimeMillis(),
            suggestedCards = suggestedCards
        )
        _chatMessages.value = _chatMessages.value + msg
        return msg
    }

    private fun extractRemainderAfterKeyword(text: String, vararg keywords: String): String {
        val lower = text.lowercase()
        for (kw in keywords) {
            val idx = lower.indexOf(kw.lowercase())
            if (idx >= 0) {
                return text.substring(idx + kw.length).trim()
            }
        }
        return text.trim()
    }

    private fun parseExplicitCardsFromText(rawText: String, moduleId: String): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()
        val contentToParse = if (rawText.contains("(") && rawText.contains(")")) {
            rawText.substringAfter("(").substringBeforeLast(")")
        } else {
            rawText
        }
        val segments = contentToParse.split(Regex("[\n,;]")).map { it.trim() }.filter { it.isNotBlank() }
        for (seg in segments) {
            val parts = if (seg.contains(" - ")) {
                seg.split(" - ", limit = 2)
            } else if (seg.contains(" — ")) {
                seg.split(" — ", limit = 2)
            } else if (seg.contains(":")) {
                seg.split(":", limit = 2)
            } else if (seg.contains("–")) {
                seg.split("–", limit = 2)
            } else {
                null
            }
            if (parts != null && parts.size == 2 && parts[0].trim().isNotBlank() && parts[1].trim().isNotBlank()) {
                cards.add(
                    Flashcard(
                        id = UUID.randomUUID().toString(),
                        moduleId = moduleId,
                        term = parts[0].trim().trim('•', '-', '*', '(', ')'),
                        definition = parts[1].trim().trim('(', ')')
                    )
                )
            }
        }
        return cards
    }

    fun clearChatHistory() {
        val initialGreeting = ChatMessage(
            id = "msg_" + UUID.randomUUID().toString(),
            text = "### Диалог очищен 🔄\n\nЯ готов ответить на новые вопросы или создать новые учебные карточки по любой теме!",
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
        _chatMessages.value = listOf(initialGreeting)
    }

    private fun extractTopicAndTitle(text: String): Pair<String, String> {
        val clean = text.trim()
        val afterKeywords = extractRemainderAfterKeyword(
            clean,
            "создай карточки по теме", "создать карточки по теме", "сделай карточки по теме",
            "создай карточки на тему", "создать карточки на тему", "сделай карточки на тему",
            "создай карточки по", "создать карточки по", "сделай карточки по",
            "создай карточки для", "создать карточки для", "сделай карточки для",
            "дай карточки по", "дай мне карточки", "дай карточки", "дай",
            "покажи карточки", "составь карточки", "составить карточки", "сформируй карточки",
            "создай карточки", "создать карточки", "сделай карточки", "сделать карточки",
            "сгенерируй карточки", "сгенерировать карточки",
            "создай модуль", "создать модуль", "сделай модуль", "сделать модуль",
            "create cards for", "create cards about", "create cards", "create module"
        ).trim(':', ' ', '"', '«', '»')

        val rawTitle = afterKeywords
            .substringBefore(":")
            .substringBefore("(")
            .substringBefore("\n")
            .trim()
            .trim('"', '«', '»')
        val cleanTitleCandidate = rawTitle
            .removePrefix("абсолютно все ")
            .removePrefix("абсолютно всё ")
            .removePrefix("все ")
            .removePrefix("всё ")
            .removePrefix("много ")
            .trim()

        val finalTitle = if (cleanTitleCandidate.isNotBlank() && cleanTitleCandidate.length <= 40) {
            cleanTitleCandidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else if (cleanTitleCandidate.length > 40) {
            cleanTitleCandidate.take(38).trim() + "…"
        } else {
            "Новые карточки"
        }
        val topic = afterKeywords.substringAfter(":", "").trim().ifBlank { afterKeywords }
        return finalTitle to topic
    }

    suspend fun askAiTutor(userText: String) {
        val previousHistory = _chatMessages.value
            .filter { !it.isGenerating && it.text.isNotBlank() && !it.text.startsWith("⚠️") }
            .takeLast(10)
            .map { it.text to it.isUser }

        addChatMessage(userText, isUser = true)
        val generatingMsg = ChatMessage(
            id = "gen_" + UUID.randomUUID(),
            text = "...",
            isUser = false,
            isGenerating = true
        )
        _chatMessages.value = _chatMessages.value + generatingMsg

        val profile = _activeProfile.value
        val trimmed = userText.trim()
        val lower = trimmed.lowercase()

        // 1. Chatbot Command: Delete Module
        if (lower.startsWith("удали модуль") || lower.startsWith("удалить модуль") || lower.startsWith("delete module")) {
            val candidate = extractRemainderAfterKeyword(trimmed, "модуль", "module").trim('"', '«', '»', '\'')
            val found = _modules.value.find {
                it.title.contains(candidate, ignoreCase = true) || it.id.equals(candidate, ignoreCase = true)
            }
            if (found != null) {
                deleteModule(found.id)
                _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "🗑️ Модуль **«${found.title}»** успешно удалён.",
                    isUser = false
                )
                return
            } else {
                _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "Не удалось найти модуль с названием «$candidate». Проверьте список модулей в Библиотеке.",
                    isUser = false
                )
                return
            }
        }

        // 2. Chatbot Command: Add cards to existing module
        if (lower.contains("добавь в модуль") || lower.contains("добавить в модуль") || lower.contains("add to module")) {
            val afterMod = extractRemainderAfterKeyword(trimmed, "модуль", "module")
            val modNameCandidate = afterMod.substringBefore(":").substringBefore("\n").trim().trim('"', '«', '»')
            val found = _modules.value.find { it.title.contains(modNameCandidate, ignoreCase = true) }
            if (found != null) {
                val promptForCards = afterMod.substringAfter(":", "").trim().ifBlank { afterMod }
                val explicit = parseExplicitCardsFromText(promptForCards, found.id)
                val newCards = if (explicit.isNotEmpty()) {
                    explicit
                } else {
                    aiClient.generateFlashcards(
                        provider = profile.provider,
                        apiKey = profile.apiKey,
                        model = profile.modelId,
                        topic = promptForCards,
                        moduleId = found.id,
                        defaultCount = AiClient.DEFAULT_FLASHCARD_COUNT
                    )
                }
                if (newCards.isNotEmpty()) {
                    val updatedModule = found.copy(cards = found.cards + newCards)
                    saveModule(updatedModule)
                    _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "✏️ В модуль **«${found.title}»** успешно добавлено карточек (${newCards.size}):\n" +
                            newCards.joinToString("\n") { "• **${it.term}** — ${it.definition}" },
                        isUser = false,
                        suggestedCards = newCards.map { it.term to it.definition },
                        createdModuleId = found.id,
                        createdModuleTitle = found.title
                    )
                    return
                }
            }
        }

        // 3. Chatbot Query: Ask about existing modules / AI-created modules
        val isModulesQuery = lower.contains("какие модул") ||
            lower.contains("какие у меня модул") ||
            lower.contains("мои модул") ||
            lower.contains("список модул") ||
            lower.contains("созданные тобой") ||
            lower.contains("ты создал") ||
            lower.contains("создал ты") ||
            (lower.contains("какие карточк") && (lower.contains("у меня") || lower.contains("есть") || lower.contains("создан") || lower.contains("тобой"))) ||
            lower.contains("сколько модул") ||
            lower.contains("what modules") ||
            lower.contains("my modules")

        if (isModulesQuery) {
            val allModules = _modules.value
            val createdByAiOrUser = allModules.filter { !it.id.startsWith("seed_") }
            val builtInModules = allModules.filter { it.id.startsWith("seed_") }
            val askingSpecificallyAiCreated = lower.contains("тобой") || lower.contains("ты создал") || lower.contains("создан")

            val replyText = buildString {
                if (createdByAiOrUser.isNotEmpty()) {
                    append("### 📚 Модули, созданные вами и ИИ (${createdByAiOrUser.size})\n\n")
                    createdByAiOrUser.forEachIndexed { idx, mod ->
                        val mastered = mod.cards.count { it.status == com.example.quizapp.data.model.CardMasteryStatus.MASTERED }
                        append("${idx + 1}. **«${mod.title}»** — ${mod.cards.size} карточек (усвоено: $mastered)\n")
                    }
                } else {
                    append("### 📚 Созданные с ИИ модули\n\n")
                    append("У вас пока нет модулей, созданных мной в диалоге.\n")
                    append("Чтобы я создал новый модуль, напишите, например:\n")
                    append("• *«Создай карточки по теме Неправильные глаголы»*\n")
                    append("• *«Создай 50 карточек по теме Анатомия»*\n")
                }

                if (!askingSpecificallyAiCreated && builtInModules.isNotEmpty()) {
                    append("\n### 📦 Базовые модули в вашей библиотеке (${builtInModules.size})\n\n")
                    builtInModules.forEachIndexed { idx, mod ->
                        append("${idx + 1}. **«${mod.title}»** (${mod.category}) — ${mod.cards.size} карточек\n")
                    }
                }
            }.trim()

            _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                id = UUID.randomUUID().toString(),
                text = replyText,
                isUser = false
            )
            return
        }

        // 4. Chatbot Command: Explicit Create Cards / Module
        val isQuestion = lower.endsWith("?") ||
            lower.startsWith("какие") || lower.startsWith("какой") || lower.startsWith("какая") || lower.startsWith("какое") ||
            lower.startsWith("сколько") || lower.startsWith("что ") || lower.startsWith("как ") ||
            lower.startsWith("почему") || lower.startsWith("зачем") || lower.startsWith("где ") ||
            lower.startsWith("кто ") || lower.startsWith("когда") || lower.startsWith("есть ли") ||
            lower.startsWith("а какие") || lower.startsWith("подскажи") || lower.startsWith("расскажи") ||
            lower.startsWith("объясни") || lower.startsWith("помоги понять") || lower.startsWith("в чем ") ||
            lower.startsWith("в чём ") || lower.startsWith("what ") || lower.startsWith("how ") ||
            lower.startsWith("why ") || lower.startsWith("which ")

        val hasExplicitCreateCommand = lower.startsWith("создай карточк") || lower.startsWith("создать карточк") ||
            lower.startsWith("сделай карточк") || lower.startsWith("сделать карточк") ||
            lower.startsWith("сгенерируй карточк") || lower.startsWith("сгенерировать карточк") ||
            lower.startsWith("составь карточк") || lower.startsWith("составить карточк") ||
            lower.startsWith("сформируй карточк") || lower.startsWith("напиши карточк") ||
            lower.startsWith("создай модул") || lower.startsWith("создать модул") ||
            lower.startsWith("сделай модул") || lower.startsWith("сделать модул") ||
            lower.startsWith("сгенерируй модул") || lower.startsWith("составь модул") ||
            lower.startsWith("дай карточк") || lower.startsWith("дай мне карточк") ||
            lower.startsWith("карточки:") || lower.startsWith("карточки по теме") || lower.startsWith("карточки на тему") ||
            lower.startsWith("create cards") || lower.startsWith("create flashcards") || lower.startsWith("create module") ||
            lower.startsWith("generate cards") || lower.startsWith("generate flashcards") ||
            Regex("^(создай|создать|сделай|сделать|сгенерируй|составь|create|generate)\\s+\\d+\\s*(карточ|слов|термин|cards|words)").containsMatchIn(lower) ||
            Regex("^(создай|создать|сделай|сделать|сгенерируй|составь)\\s+(мне\\s+)?(набор|колоду|модуль|карточки)\\b").containsMatchIn(lower)

        val isCardsCreationIntent = !isQuestion && hasExplicitCreateCommand

        if (isCardsCreationIntent) {
            val (extractedTitle, topic) = extractTopicAndTitle(trimmed)
            val newModuleId = "user_" + UUID.randomUUID().toString()
            val explicit = parseExplicitCardsFromText(topic, newModuleId)
            val promptForGeneration = if (topic.isNotBlank()) topic else trimmed
            val generatedCards = if (explicit.isNotEmpty()) {
                explicit
            } else {
                aiClient.generateFlashcards(
                    provider = profile.provider,
                    apiKey = profile.apiKey,
                    model = profile.modelId,
                    topic = promptForGeneration,
                    moduleId = newModuleId,
                    defaultCount = AiClient.DEFAULT_FLASHCARD_COUNT
                )
            }

            if (generatedCards.isNotEmpty()) {
                val finalTitle = if (extractedTitle != "Новые карточки") extractedTitle else topic.take(30).trim().ifBlank { "Новый модуль" }
                val newModule = StudyModule(
                    id = newModuleId,
                    title = finalTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    description = "Создано по запросу в диалоге с Репетитором",
                    category = "Пользовательские",
                    author = "Вы & Репетитор",
                    isVerified = false,
                    colorHex = 0xFF16A34A,
                    cards = generatedCards
                )
                saveModule(newModule)
                _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                    id = UUID.randomUUID().toString(),
                    text = "✅ **Создан новый модуль «${newModule.title}»** (${newModule.cards.size} карточек)!\n" +
                        "Он уже сохранён в вашей библиотеке и доступен в разделе «Ваши карточки». Вы можете сразу открыть его для изучения.",
                    isUser = false,
                    suggestedCards = generatedCards.map { it.term to it.definition },
                    createdModuleId = newModule.id,
                    createdModuleTitle = newModule.title
                )
                return
            }
        }

        // 5. Standard Q&A or explanation with full conversation memory and library awareness
        val customMods = _modules.value.filter { !it.id.startsWith("seed_") }
        val seedMods = _modules.value.filter { it.id.startsWith("seed_") }
        val systemInstructionWithContext = buildString {
            append("Ты — дружелюбный, умный ИИ-репетитор и собеседник в приложении QuizApp. ")
            append("Веди естественный, полезный диалог на языке пользователя. Помни контекст предыдущих сообщений. ")
            append("Отвечай по существу на любые вопросы пользователя, объясняй правила, переводи слова, помогай готовиться к экзаменам. ")
            append("НЕ создавай карточки без явной просьбы пользователя.\n")
            append("Справка о текущих модулях пользователя в библиотеке QuizApp:\n")
            if (customMods.isNotEmpty()) {
                append("- Созданные пользователем/ИИ модули (${customMods.size}): ")
                append(customMods.joinToString(", ") { "«${it.title}» (${it.cards.size} карт.)" })
                append(".\n")
            } else {
                append("- Созданных пользователем/ИИ модулей пока нет.\n")
            }
            if (seedMods.isNotEmpty()) {
                append("- Базовые модули (${seedMods.size}): ")
                append(seedMods.joinToString(", ") { "«${it.title}» (${it.cards.size} карт.)" })
                append(".")
            }
        }

        val responseText = aiClient.generateResponse(
            provider = profile.provider,
            apiKey = profile.apiKey,
            model = profile.modelId,
            prompt = userText,
            history = previousHistory,
            systemInstruction = systemInstructionWithContext
        )

        val wantsVocabularySuggestions = lower.contains("подбери слова") ||
            lower.contains("список слов") ||
            lower.contains("выпиши слова") ||
            lower.contains("слова по теме")

        val suggestedPairs = if (!wantsVocabularySuggestions) {
            emptyList()
        } else {
            val cards = aiClient.parseFlashcardsFromJsonOrText(responseText, "temp")
            cards.filter { it.term.length in 2..50 && it.definition.length in 4..150 }
                .take(12)
                .map { it.term to it.definition }
        }

        _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
            id = UUID.randomUUID().toString(),
            text = responseText,
            isUser = false,
            suggestedCards = suggestedPairs
        )
    }

    suspend fun generateFlashcardsForTopic(topic: String, defaultCount: Int = AiClient.DEFAULT_FLASHCARD_COUNT): List<Flashcard> {
        val profile = _activeProfile.value
        return aiClient.generateFlashcards(
            provider = profile.provider,
            apiKey = profile.apiKey,
            model = profile.modelId,
            topic = topic,
            moduleId = UUID.randomUUID().toString(),
            defaultCount = defaultCount
        )
    }

    suspend fun generateCardsFromImage(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        moduleId: String = UUID.randomUUID().toString()
    ): List<Flashcard> {
        val profile = _activeProfile.value
        return aiClient.generateFlashcardsFromImage(
            provider = profile.provider,
            apiKey = profile.apiKey,
            model = profile.modelId,
            imageBase64 = imageBase64,
            mimeType = mimeType,
            moduleId = moduleId
        )
    }

    fun saveModule(module: StudyModule) {
        val current = _modules.value.toMutableList()
        val index = current.indexOfFirst { it.id == module.id }
        if (index >= 0) {
            current[index] = module
        } else {
            current.add(0, module)
        }
        _modules.value = current
        persistCustomModules(current.filter { !it.id.startsWith("seed_") })
    }

    fun updateCardMastery(
        moduleId: String,
        cardId: String,
        isKnown: Boolean,
        grade: com.example.quizapp.data.model.AnkiGrade = if (isKnown) com.example.quizapp.data.model.AnkiGrade.GOOD else com.example.quizapp.data.model.AnkiGrade.AGAIN
    ) {
        val current = _modules.value.toMutableList()
        val mIdx = current.indexOfFirst { it.id == moduleId }
        if (mIdx >= 0) {
            val mod = current[mIdx]
            val now = System.currentTimeMillis()
            val updatedCards = mod.cards.map { card ->
                if (card.id == cardId) {
                    com.example.quizapp.data.algorithm.SpacedRepetitionEngine.applyAnkiReview(card, grade, now)
                } else {
                    card
                }
            }
            current[mIdx] = mod.copy(cards = updatedCards)
            _modules.value = current
            persistCardProgress(current)
            persistCustomModules(current.filter { !it.id.startsWith("seed_") })

            // Recalculate total mastered cards across all modules and increment todayStudied
            val totalMasteredAcrossAll = current.sumOf { m -> m.cards.count { it.status == com.example.quizapp.data.model.CardMasteryStatus.MASTERED } }
            val currentStats = _userStats.value
            val updatedStats = currentStats.copy(
                todayStudied = currentStats.todayStudied + 1,
                totalCardsLearned = totalMasteredAcrossAll
            )
            _userStats.value = updatedStats
            persistUserStats(updatedStats)
        }
    }

    fun toggleCardStarred(moduleId: String, cardId: String) {
        val current = _modules.value.toMutableList()
        val mIdx = current.indexOfFirst { it.id == moduleId }
        if (mIdx >= 0) {
            val mod = current[mIdx]
            val updatedCards = mod.cards.map { card ->
                if (card.id == cardId) card.copy(isStarred = !card.isStarred) else card
            }
            current[mIdx] = mod.copy(cards = updatedCards)
            _modules.value = current
            persistCardProgress(current)
            persistCustomModules(current.filter { !it.id.startsWith("seed_") })
        }
    }

    fun deleteModule(moduleId: String) {
        val current = _modules.value.filter { it.id != moduleId }
        _modules.value = current
        val existingDeleted = prefs.getString("deleted_module_ids", "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        existingDeleted.add(moduleId)
        prefs.edit().putString("deleted_module_ids", existingDeleted.joinToString(",")).apply()
        persistCardProgress(current)
        persistCustomModules(current.filter { !it.id.startsWith("seed_") })
    }

    // --- Export & Import Functions ---

    fun exportModuleToJson(module: StudyModule): String {
        val root = JSONObject().apply {
            put("id", module.id)
            put("title", module.title)
            put("description", module.description)
            put("category", module.category)
            put("author", module.author)
            put("colorHex", module.colorHex)
            val cardsArray = JSONArray()
            module.cards.forEach { c ->
                cardsArray.put(JSONObject().apply {
                    put("id", c.id)
                    put("term", c.term)
                    put("definition", c.definition)
                    put("diagramType", c.diagramType ?: "")
                    put("exampleSentence", c.exampleSentence ?: "")
                    put("isMastered", c.isMastered)
                })
            }
            put("cards", cardsArray)
        }
        return root.toString(2)
    }

    fun exportModuleToCsv(module: StudyModule): String {
        val sb = StringBuilder()
        sb.append("# ${module.title}\n")
        module.cards.forEach { card ->
            val termClean = card.term.replace("\t", " ").replace("\n", " ")
            val defClean = card.definition.replace("\t", " ").replace("\n", " ")
            sb.append("$termClean\t$defClean\n")
        }
        return sb.toString()
    }

    fun importModuleFromJson(jsonStr: String): StudyModule {
        val obj = JSONObject(jsonStr)
        val modId = "import_" + UUID.randomUUID().toString()
        val cardsArr = obj.optJSONArray("cards") ?: JSONArray()
        val cards = mutableListOf<Flashcard>()
        for (i in 0 until cardsArr.length()) {
            val cObj = cardsArr.getJSONObject(i)
            cards.add(
                Flashcard(
                    id = UUID.randomUUID().toString(),
                    moduleId = modId,
                    term = cObj.optString("term").trim(),
                    definition = cObj.optString("definition").trim(),
                    diagramType = cObj.optString("diagramType").ifBlank { null },
                    exampleSentence = cObj.optString("exampleSentence").ifBlank { null }
                )
            )
        }
        return StudyModule(
            id = modId,
            title = obj.optString("title", "Импортированный модуль"),
            description = obj.optString("description", "Импортировано из файла JSON"),
            category = obj.optString("category", "Импорт"),
            author = "QuizApp Import",
            isVerified = false,
            colorHex = 0xFF16A34A,
            cards = cards,
            createdTimestamp = System.currentTimeMillis()
        )
    }

    fun importModuleFromTextOrCsv(rawText: String, defaultTitle: String = "Импортированный модуль"): StudyModule {
        val modId = "import_" + UUID.randomUUID().toString()
        val cards = mutableListOf<Flashcard>()
        val lines = rawText.lines()
        var extractedTitle = defaultTitle

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                extractedTitle = trimmed.removePrefix("# ").trim()
                continue
            }
            if (trimmed.isBlank() || trimmed.startsWith("//")) continue

            val separator = when {
                trimmed.contains("\t") -> "\t"
                trimmed.contains(" - ") -> " - "
                trimmed.contains(" — ") -> " — "
                trimmed.contains(";") -> ";"
                trimmed.contains(",") -> ","
                else -> null
            }

            if (separator != null) {
                val parts = trimmed.split(separator, limit = 2)
                val t = parts[0].trim()
                val d = parts[1].trim()
                if (t.isNotBlank() && d.isNotBlank()) {
                    cards.add(
                        Flashcard(
                            id = UUID.randomUUID().toString(),
                            moduleId = modId,
                            term = t,
                            definition = d
                        )
                    )
                }
            }
        }

        return StudyModule(
            id = modId,
            title = extractedTitle,
            description = "Импортировано ${cards.size} карточек",
            category = "Импорт",
            author = "QuizApp Import",
            isVerified = false,
            colorHex = 0xFF0284C7,
            cards = cards,
            createdTimestamp = System.currentTimeMillis()
        )
    }

    private fun persistCustomModules(customModules: List<StudyModule>) {
        val jsonArr = JSONArray()
        customModules.forEach { mod ->
            val obj = JSONObject().apply {
                put("id", mod.id)
                put("title", mod.title)
                put("description", mod.description)
                put("category", mod.category)
                put("author", mod.author)
                put("isVerified", mod.isVerified)
                put("colorHex", mod.colorHex)
                put("createdTimestamp", mod.createdTimestamp)
                val cardsArr = JSONArray()
                mod.cards.forEach { c ->
                    cardsArr.put(JSONObject().apply {
                        put("id", c.id)
                        put("term", c.term)
                        put("definition", c.definition)
                        put("diagramType", c.diagramType ?: "")
                        put("exampleSentence", c.exampleSentence ?: "")
                        put("isMastered", c.isMastered)
                        put("masteryScore", c.masteryScore)
                        put("needsReview", c.needsReview)
                        put("isStarred", c.isStarred)
                        put("reviewCount", c.reviewCount)
                        put("lastStudiedTimestamp", c.lastStudiedTimestamp)
                        put("easeFactor", c.easeFactor.toDouble())
                        put("intervalDays", c.intervalDays)
                        put("repetitions", c.repetitions)
                        put("lapses", c.lapses)
                        put("nextReviewTimestamp", c.nextReviewTimestamp)
                    })
                }
                put("cards", cardsArr)
            }
            jsonArr.put(obj)
        }
        prefs.edit().putString("custom_modules_json", jsonArr.toString()).apply()
    }

    private fun parseModulesJson(jsonStr: String): List<StudyModule> {
        val list = mutableListOf<StudyModule>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cardsArr = obj.optJSONArray("cards") ?: JSONArray()
                val cards = mutableListOf<Flashcard>()
                val modId = obj.getString("id")
                for (j in 0 until cardsArr.length()) {
                    val cObj = cardsArr.getJSONObject(j)
                    val score = cObj.optInt("masteryScore", if (cObj.optBoolean("isMastered", false)) 2 else 0).coerceIn(0, 2)
                    cards.add(
                        Flashcard(
                            id = cObj.getString("id"),
                            moduleId = modId,
                            term = cObj.getString("term"),
                            definition = cObj.getString("definition"),
                            diagramType = cObj.optString("diagramType").ifBlank { null },
                            exampleSentence = cObj.optString("exampleSentence").ifBlank { null },
                            isMastered = score >= 2 || cObj.optBoolean("isMastered", false),
                            masteryScore = score,
                            needsReview = cObj.optBoolean("needsReview", false),
                            isStarred = cObj.optBoolean("isStarred", false),
                            reviewCount = cObj.optInt("reviewCount", 0),
                            lastStudiedTimestamp = cObj.optLong("lastStudiedTimestamp", 0L),
                            easeFactor = cObj.optDouble("easeFactor", 2.5).toFloat(),
                            intervalDays = cObj.optInt("intervalDays", 0),
                            repetitions = cObj.optInt("repetitions", 0),
                            lapses = cObj.optInt("lapses", 0),
                            nextReviewTimestamp = cObj.optLong("nextReviewTimestamp", 0L)
                        )
                    )
                }
                list.add(
                    StudyModule(
                        id = modId,
                        title = obj.getString("title"),
                        description = obj.optString("description"),
                        category = obj.optString("category"),
                        author = obj.optString("author", "QuizApp"),
                        isVerified = obj.optBoolean("isVerified", false),
                        colorHex = obj.optLong("colorHex", 0xFF16A34A),
                        cards = cards,
                        createdTimestamp = obj.optLong("createdTimestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    // --- Rich Seed Modules with 150+ cards total ---
    private fun createSeedModules(): List<StudyModule> {
        // 1. English Phrasal Verbs & Idioms (22 cards)
        val englishModule = StudyModule(
            id = "seed_english",
            title = "English: Top Phrasal Verbs & Advanced Idioms",
            description = "Ключевые фразовые глаголы и популярные идиомы для свободного разговорного английского.",
            category = "Языки",
            author = "QuizApp English",
            isVerified = true,
            colorHex = 0xFF16A34A,
            cards = listOf(
                Flashcard("e1", "seed_english", "Bring up", "Упомянуть тему или воспитывать детей (To mention a topic or raise children).", exampleSentence = "She brought up an interesting point during the meeting."),
                Flashcard("e2", "seed_english", "Look forward to", "С нетерпением ждать чего-либо (To feel excited about something that is going to happen).", exampleSentence = "I'm looking forward to our weekend trip."),
                Flashcard("e3", "seed_english", "Figure out", "Понять, разобраться, найти решение (To understand or find the solution to a problem).", exampleSentence = "We need to figure out how to solve this quiz."),
                Flashcard("e4", "seed_english", "Call off", "Отменить мероприятие или договорённость (To cancel an event or agreement).", exampleSentence = "They called off the concert due to heavy rain."),
                Flashcard("e5", "seed_english", "Piece of cake", "Очень просто, пара пустяков (Something that is very easy to do).", exampleSentence = "Don't worry about the test, it's a piece of cake!"),
                Flashcard("e6", "seed_english", "Under the weather", "Слегка приболевший, нездоровится (Feeling slightly sick or unwell).", exampleSentence = "I'm feeling a bit under the weather today."),
                Flashcard("e7", "seed_english", "Break the ice", "Разрядить обстановку, начать разговор (To make people feel more comfortable).", exampleSentence = "He told a joke to break the ice with the new team."),
                Flashcard("e8", "seed_english", "Hit the books", "Усердно засесть за учёбу (To begin studying hard).", exampleSentence = "Final exams start tomorrow, time to hit the books."),
                Flashcard("e9", "seed_english", "Come across", "Случайно наткнуться, обнаружить (To find or meet by chance).", exampleSentence = "I came across an old photo album in the attic."),
                Flashcard("e10", "seed_english", "Put off", "Откладывать на потом (To postpone or delay an action).", exampleSentence = "Never put off till tomorrow what you can do today."),
                Flashcard("e11", "seed_english", "Run out of", "Закончиться, исчерпать запас (To use up all of something).", exampleSentence = "We have run out of coffee, let's buy some."),
                Flashcard("e12", "seed_english", "Get along with", "Ладить, быть в хороших отношениях (To have a friendly relationship).", exampleSentence = "She gets along well with all her colleagues."),
                Flashcard("e13", "seed_english", "Turn down", "Отклонить предложение или убавить громкость (To refuse or reduce volume).", exampleSentence = "He turned down the job offer in London."),
                Flashcard("e14", "seed_english", "Keep up with", "Поспевать за кем-то, держаться на одном уровне (To stay at the same level).", exampleSentence = "It is hard to keep up with rapid technological changes."),
                Flashcard("e15", "seed_english", "Give up", "Сдаваться, бросать занятие (To cease trying or quit).", exampleSentence = "Never give up on your dreams."),
                Flashcard("e16", "seed_english", "Bite the bullet", "Стиснуть зубы и сделать трудное дело (To face a painful situation courageously).", exampleSentence = "I decided to bite the bullet and go to the dentist."),
                Flashcard("e17", "seed_english", "Cost an arm and a leg", "Стоить баснословных денег (To be extremely expensive).", exampleSentence = "This new laptop costs an arm and a leg."),
                Flashcard("e18", "seed_english", "Once in a blue moon", "Крайне редко, в кои-то веки (Very rarely happening).", exampleSentence = "He visits his hometown once in a blue moon."),
                Flashcard("e19", "seed_english", "See eye to eye", "Полностью соглашаться, сходиться во взглядах (To agree fully with someone).", exampleSentence = "My brother and I rarely see eye to eye on politics."),
                Flashcard("e20", "seed_english", "Spill the beans", "Проболтаться, выдать секрет (To disclose a secret unintentionally).", exampleSentence = "Don't spill the beans about the surprise party!"),
                Flashcard("e21", "seed_english", "Burn the midnight oil", "Работать допоздна (To study or work late into the night).", exampleSentence = "The team burned the midnight oil before the launch."),
                Flashcard("e22", "seed_english", "Cut corners", "Халтурить, экономить в ущерб качеству (To do something carelessly to save time or money).", exampleSentence = "They cut corners during construction and now the roof leaks.")
            )
        )

        // 2. IT & Программирование (20 cards)
        val itModule = StudyModule(
            id = "seed_it",
            title = "IT & Программирование: Computer Science & Kotlin",
            description = "Фундаментальные структуры данных, концепции ООП, алгоритмы и современные паттерны разработки.",
            category = "Точные науки",
            author = "QuizApp Dev",
            isVerified = true,
            colorHex = 0xFF0284C7,
            cards = listOf(
                Flashcard("it1", "seed_it", "Хеш-таблица (HashMap)", "Структура данных для хранения пар ключ-значение с поиском за среднее время O(1) благодаря хешированию."),
                Flashcard("it2", "seed_it", "Бинарный поиск (Binary Search)", "Эффективный алгоритм поиска элемента в отсортированном массиве с логарифмической сложностью O(log N)."),
                Flashcard("it3", "seed_it", "Инкапсуляция (Encapsulation)", "Принцип ООП, объединяющий данные и методы для работы с ними внутри класса и скрывающий внутреннюю реализацию."),
                Flashcard("it4", "seed_it", "Полиморфизм (Polymorphism)", "Способность объектов с одинаковым интерфейсом иметь различную реализацию методов."),
                Flashcard("it5", "seed_it", "Корутины (Coroutines)", "Механизм легковесной асинхронности в Kotlin, позволяющий писать неблокирующий код последовательно."),
                Flashcard("it6", "seed_it", "StateFlow", "Холодный или горячий реактивный поток состояния в Kotlin Coroutines, хранящий текущее значение для UI."),
                Flashcard("it7", "seed_it", "Паттерн MVVM", "Архитектурный шаблон Model-View-ViewModel, разделяющий бизнес-логику, состояние и графический интерфейс."),
                Flashcard("it8", "seed_it", "Рекурсия (Recursion)", "Метод решения задачи, при котором функция вызывает саму себя с уменьшающимся базовым случаем."),
                Flashcard("it9", "seed_it", "Стек (Stack / LIFO)", "Структура данных по принципу 'последним пришёл — первым ушёл' (push/pop)."),
                Flashcard("it10", "seed_it", "Очередь (Queue / FIFO)", "Структура данных по принципу 'первым пришёл — первым ушёл' (enqueue/dequeue)."),
                Flashcard("it11", "seed_it", "REST API", "Архитектурный стиль взаимодействия клиент-сервер по протоколу HTTP с методами GET, POST, PUT, DELETE."),
                Flashcard("it12", "seed_it", "JSON (JavaScript Object Notation)", "Текстовый формат обмена данными, основанный на парах ключ-значение и массивах."),
                Flashcard("it13", "seed_it", "Сложность O(N²)", "Квадратичная временная сложность, типичная для простых сортировок (пузырьковая, вставками) с вложенными циклами."),
                Flashcard("it14", "seed_it", "Быстрая сортировка (QuickSort)", "Алгоритм сортировки методом 'разделяй и властвуй' со средней сложностью O(N log N)."),
                Flashcard("it15", "seed_it", "Dependency Injection (DI)", "Паттерн внедрения зависимостей извне, повышающий тестируемость и слабую связность компонентов."),
                Flashcard("it16", "seed_it", "Иммутабельность (Immutability)", "Свойство объекта или переменной (val в Kotlin), запрещающее изменение состояния после создания."),
                Flashcard("it17", "seed_it", "Связный список (LinkedList)", "Последовательность узлов, где каждый элемент содержит данные и ссылку на следующий узел."),
                Flashcard("it18", "seed_it", "Дерево поиска (BST)", "Иерархическая древовидная структура, где левое поддерево меньше корня, а правое — больше."),
                Flashcard("it19", "seed_it", "Транзакция БД (ACID)", "Атомарная последовательность операций, гарантирующая целостность данных."),
                Flashcard("it20", "seed_it", "Jetpack Compose", "Современный декларативный инструментарий Android для создания интуитивного пользовательского интерфейса на Kotlin.")
            )
        )

        // 3. Анатомия и физиология человека (20 cards)
        val anatomyModule = StudyModule(
            id = "seed_anatomy",
            title = "Анатомия и физиология человека",
            description = "Строение внутренних органов, сердечно-сосудистая система, дыхание и клетки организма.",
            category = "Медицина",
            author = "QuizApp Medical",
            isVerified = true,
            colorHex = 0xFF00BFA5,
            cards = listOf(
                Flashcard("a1", "seed_anatomy", "Левый желудочек", "Камера сердца с самой толстой мышечной стенкой, нагнетающая насыщенную кислородом кровь в аорту.", diagramType = "heart"),
                Flashcard("a2", "seed_anatomy", "Аорта", "Самая крупная артерия организма человека, начинающаяся от левого желудочка сердца.", diagramType = "heart"),
                Flashcard("a3", "seed_anatomy", "Правое предсердие", "Камера сердца, принимающая венозную бедную кислородом кровь из верхней и нижней полых вен.", diagramType = "heart"),
                Flashcard("a4", "seed_anatomy", "Митральный клапан", "Двустворчатый сердечный клапан, разделяющий левое предсердие и левый желудочек.", diagramType = "heart"),
                Flashcard("a5", "seed_anatomy", "Малый круг кровообращения", "Легочный круг: правый желудочек -> легочные артерии -> капилляры легких -> легочные вены -> левое предсердие.", diagramType = "heart"),
                Flashcard("a6", "seed_anatomy", "Митохондрия", "Энергетическая станция клетки, в которой в процессе клеточного дыхания синтезируется АТФ.", diagramType = "dna"),
                Flashcard("a7", "seed_anatomy", "Рибосома", "Немембранный органоид клетки, отвечающий за биосинтез полипептидных цепей (белков).", diagramType = "dna"),
                Flashcard("a8", "seed_anatomy", "ДНК (Дезоксирибонуклеиновая кислота)", "Макромолекула двойной спирали, обеспечивающая хранение и передачу генетической информации.", diagramType = "dna"),
                Flashcard("a9", "seed_anatomy", "Хромосома", "Комплекс ДНК и белков-гистонов, несущий наследственную информацию клетки.", diagramType = "dna"),
                Flashcard("a10", "seed_anatomy", "Эритроциты", "Красные кровяные тельца, содержащие гемоглобин и переносящие кислород от легких к тканям."),
                Flashcard("a11", "seed_anatomy", "Лейкоциты", "Белые кровяные клетки иммунной системы, защищающие организм от инфекций и патогенов."),
                Flashcard("a12", "seed_anatomy", "Тромбоциты", "Безъядерные клетки крови, участвующие в свертывании крови и образовании тромбов при травмах."),
                Flashcard("a13", "seed_anatomy", "Нефрон", "Структурно-функциональная единица почки, осуществляющая фильтрацию плазмы и образование мочи."),
                Flashcard("a14", "seed_anatomy", "Печень", "Самая крупная железа человека, участвующая в метаболизме, детоксикации и синтезе желчи."),
                Flashcard("a15", "seed_anatomy", "Нейрон", "Специализированная клетка нервной системы, проводящая электрические нервные импульсы."),
                Flashcard("a16", "seed_anatomy", "Синапс", "Место контакта между двумя нейронами для передачи нервного сигнала с помощью медиаторов."),
                Flashcard("a17", "seed_anatomy", "Альвеолы", "Легочные пузырьки, оплетенные капиллярами, в которых происходит газообмен O₂ и CO₂."),
                Flashcard("a18", "seed_anatomy", "Щитовидная железа", "Эндокринный орган на передней поверхности шеи, вырабатывающий тироксин и регулирующий обмен веществ."),
                Flashcard("a19", "seed_anatomy", "Диафрагма", "Главная дыхательная куполообразная мышца, разделяющая грудную и брюшную полости."),
                Flashcard("a20", "seed_anatomy", "Гемоглобин", "Железосодержащий сложный белок в эритроцитах, связывающий кислород.")
            )
        )

        // 4. Мировая география: Страны, столицы и достопримечательности (22 cards)
        val geoModule = StudyModule(
            id = "seed_geo",
            title = "Мировая география: Страны и столицы",
            description = "Столицы государств всех континентов, географические рекорды и знаменитые мировые объекты.",
            category = "География",
            author = "QuizApp Geography",
            isVerified = true,
            colorHex = 0xFFF59E0B,
            cards = listOf(
                Flashcard("geo1", "seed_geo", "Канада — Столица", "Оттава (крупнейшие города по населению — Торонто и Монреаль)."),
                Flashcard("geo2", "seed_geo", "Австралия — Столица", "Канберра (компромисс между Сиднеем и Мельбурном)."),
                Flashcard("geo3", "seed_geo", "Бразилия — Столица", "Бразилиа (спроектирована Оскаром Нимейером в 1960 году)."),
                Flashcard("geo4", "seed_geo", "Япония — Столица", "Токио (крупнейшая городская агломерация планеты)."),
                Flashcard("geo5", "seed_geo", "Исландия — Столица", "Рейкьявик (самая северная столица независимого государства)."),
                Flashcard("geo6", "seed_geo", "Египет — Столица", "Каир (крупнейший мегаполис арабского мира на берегах Нила)."),
                Flashcard("geo7", "seed_geo", "Турция — Столица", "Анкара (Стамбул — крупнейший исторический и финансовый центр)."),
                Flashcard("geo8", "seed_geo", "ЮАР — Столицы", "Претория (административная), Кейптаун (законодательная), Блумфонтейн (судебная)."),
                Flashcard("geo9", "seed_geo", "Байкал", "Самое глубокое озеро на планете (1642 м) и крупнейший резервуар пресной воды."),
                Flashcard("geo10", "seed_geo", "Самая длинная река мира", "Нил (по традиционным измерениям, ~6650 км) или Амазонка (самая полноводная река)."),
                Flashcard("geo11", "seed_geo", "Самая высокая вершина Земли", "Эверест / Джомолунгма (8848.86 метров над уровнем моря в Гималаях)."),
                Flashcard("geo12", "seed_geo", "Самый крупный океан", "Тихий океан (занимает почти половину водной поверхности Земли)."),
                Flashcard("geo13", "seed_geo", "Самая сухая пустыня", "Атакама в Южной Америке (в некоторых районах осадки не выпадали веками)."),
                Flashcard("geo14", "seed_geo", "Марианская впадина", "Глубочайший океанический желоб Земли (~11 000 метров ниже уровня океана)."),
                Flashcard("geo15", "seed_geo", "Швейцария — Де-факто столица", "Берн (официально Федеральный город Швейцарской Конфедерации)."),
                Flashcard("geo16", "seed_geo", "Норвегия — Столица", "Осло (расположен в глубине Осло-фьорда)."),
                Flashcard("geo17", "seed_geo", "Индия — Столица", "Нью-Дели (район в составе национальной столичной территории Дели)."),
                Flashcard("geo18", "seed_geo", "Аргентина — Столица", "Буэнос-Айрес (расположен на западном берегу залива Ла-Плата)."),
                Flashcard("geo19", "seed_geo", "Самый большой остров планеты", "Гренландия (площадь свыше 2.1 миллиона квадратных километров)."),
                Flashcard("geo20", "seed_geo", "Самый высокий водопад", "Анхель в Венесуэле (высота непрерывного падения воды 979 метров)."),
                Flashcard("geo21", "seed_geo", "Испания — Столица", "Мадрид (географический и политический центр Пиренейского полуострова)."),
                Flashcard("geo22", "seed_geo", "Мадагаскар — Столица", "Антананариву (расположен в центральной горной части острова).")
            )
        )

        // 5. История и мировые цивилизации (20 cards)
        val historyModule = StudyModule(
            id = "seed_history",
            title = "История: Великие эпохи и цивилизации",
            description = "Ключевые события древнего мира, средневековья, нового времени и поворотные моменты человечества.",
            category = "История",
            author = "QuizApp History",
            isVerified = true,
            colorHex = 0xFF8B5CF6,
            cards = listOf(
                Flashcard("h1", "seed_history", "Падение Западной Римской империи", "476 год н.э. — низложение последнего римского императора Ромула Августула вождём Одоакром."),
                Flashcard("h2", "seed_history", "Великая хартия вольностей (Magna Carta)", "1215 год — документ, ограничивший власть английского короля и заложивший основы правового государства."),
                Flashcard("h3", "seed_history", "Падение Константинополя", "1453 год — взятие столицы Византии османскими войсками султана Мехмеда II, конец эпохи Средневековья."),
                Flashcard("h4", "seed_history", "Открытие Америки Колумбом", "1492 год — первая экспедиция Христофора Колумба достигла Багамских островов, начало эпохи Великих открытий."),
                Flashcard("h5", "seed_history", "Начало Реформации", "1517 год — Мартин Лютер прибил '95 тезисов' к дверям замковой церкви в Виттенберге."),
                Flashcard("h6", "seed_history", "Декларация независимости США", "4 июля 1776 года — принятие документа о независимости 13 североамериканских колоний от Великобритании."),
                Flashcard("h7", "seed_history", "Взятие Бастилии", "14 июля 1789 года — начало Великой французской революции, символическое свержение абсолютизма."),
                Flashcard("h8", "seed_history", "Битва при Ватерлоо", "18 июня 1815 года — окончательное поражение Наполеона Бонапарта в Бельгии от войск союзников."),
                Flashcard("h9", "seed_history", "Отмена крепостного права в России", "1861 год — манифест императора Александра II об освобождении крестьян от крепостной зависимости."),
                Flashcard("h10", "seed_history", "Первая мировая война", "1914–1918 годы — глобальный военный конфликт между Антантой и Центральными державами."),
                Flashcard("h11", "seed_history", "Создание Организации Объединенных Наций (ООН)", "24 октября 1945 года — вступление в силу Устава ООН для поддержания глобального мира и безопасности."),
                Flashcard("h12", "seed_history", "Первый полет человека в космос", "12 апреля 1961 года — Юрий Гагарин на космическом корабле 'Восток-1' совершил виток вокруг Земли."),
                Flashcard("h13", "seed_history", "Высадка человека на Луну", "20 июля 1969 года — астронавты Нил Армстронг и Базз Олдрин ступили на поверхность Луны (миссия Аполлон-11)."),
                Flashcard("h14", "seed_history", "Падение Берлинской стены", "9 ноября 1989 года — объединение разделенного Берлина и символ завершения 'холодной войны'."),
                Flashcard("h15", "seed_history", "Древний Египет: Розеттский камень", "Каменная плита с надписью на трёх языках, позволившая Жан-Франсуа Шампольону расшифровать иероглифы."),
                Flashcard("h16", "seed_history", "Эпоха Возрождения (Ренессанс)", "XIV–XVI века — эпоха расцвета гуманизма, науки и искусства в Европе (Леонардо да Винчи, Микеланджело)."),
                Flashcard("h17", "seed_history", "Изобретение книгопечатания", "Около 1440 года — Иоганн Гутенберг разработал станок с подвижными металлическими литерами."),
                Flashcard("h18", "seed_history", "Столетняя война", "1337–1453 годы — серия военных конфликтов между Англией и Францией за контроль над землями."),
                Flashcard("h19", "seed_history", "Куликовская битва", "8 сентября 1380 года — победа объединённых русских полков под командованием Дмитрия Донского."),
                Flashcard("h20", "seed_history", "Промышленная революция", "Переход во второй половине XVIII века от ручного ремесленного труда к машинному фабричному производству.")
            )
        )

        // 6. Español Práctico (20 cards)
        val spanishModule = StudyModule(
            id = "seed_spanish",
            title = "Español Práctico: Фразы для общения",
            description = "Самые важные испанские выражения для путешествий, приветствий, покупок и повседневных диалогов.",
            category = "Языки",
            author = "QuizApp Spanish",
            isVerified = true,
            colorHex = 0xFFFF6D00,
            cards = listOf(
                Flashcard("s1", "seed_spanish", "¿Cómo estás? / ¿Qué tal?", "Как дела? / Как поживаешь? (Разговорное приветствие).", exampleSentence = "¡Hola amigo! ¿Qué tal todo?"),
                Flashcard("s2", "seed_spanish", "Mucho gusto / Encantado", "Приятно познакомиться / Очень рад знакомству.", exampleSentence = "Me llamo Carlos, mucho gusto."),
                Flashcard("s3", "seed_spanish", "De nada", "Не за что / Пожалуйста (Ответ на благодарность 'Gracias').", exampleSentence = "— ¡Muchas gracias! — De nada."),
                Flashcard("s4", "seed_spanish", "Por favor", "Пожалуйста (при вежливой просьбе).", exampleSentence = "Una cuenta, por favor."),
                Flashcard("s5", "seed_spanish", "¿Cuánto cuesta esto?", "Сколько это стоит? (Узнать цену в магазине).", exampleSentence = "¿Cuánto cuesta esta camiseta, por favor?"),
                Flashcard("s6", "seed_spanish", "¿Dónde está...?", "Где находится...? (Спросить дорогу).", exampleSentence = "¿Dónde está la estación de metro más cercana?"),
                Flashcard("s7", "seed_spanish", "La cuenta, por favor", "Счёт, пожалуйста (В ресторане или кафе).", exampleSentence = "Camarero, la cuenta, por favor."),
                Flashcard("s8", "seed_spanish", "Disculpe / Perdón", "Извините / Прошу прощения (Для привлечения внимания или извинения).", exampleSentence = "Disculpe, ¿sabe qué hora es?"),
                Flashcard("s9", "seed_spanish", "No entiendo / No comprendo", "Я не понимаю.", exampleSentence = "Hablo poco español, no entiendo."),
                Flashcard("s10", "seed_spanish", "¿Habla inglés?", "Вы говорите по-английски?", exampleSentence = "Disculpe, ¿habla inglés?"),
                Flashcard("s11", "seed_spanish", "Buenos días", "Доброе утро! (Используется до обеда).", exampleSentence = "¡Buenos días a todos!"),
                Flashcard("s12", "seed_spanish", "Buenas tardes", "Добрый день / Добрый вечер (После обеда и до заката).", exampleSentence = "¡Buenas tardes, señor!"),
                Flashcard("s13", "seed_spanish", "Buenas noches", "Доброй ночи / Спокойной ночи.", exampleSentence = "Hasta mañana, buenas noches."),
                Flashcard("s14", "seed_spanish", "Hasta luego / Hasta pronto", "До скорого / До встречи.", exampleSentence = "Nos vemos mañana, ¡hasta luego!"),
                Flashcard("s15", "seed_spanish", "¿Me puede ayudar?", "Вы можете мне помочь?", exampleSentence = "Disculpe, ¿me puede ayudar con las maletas?"),
                Flashcard("s16", "seed_spanish", "Lo siento mucho", "Мне очень жаль / Простите.", exampleSentence = "Lo siento mucho por llegar tarde."),
                Flashcard("s17", "seed_spanish", "Tengo una reserva", "У меня бронь (В отеле или ресторане).", exampleSentence = "Hola, tengo una reserva a nombre de David."),
                Flashcard("s18", "seed_spanish", "Con permiso", "С вашего позволения / Разрешите пройти.", exampleSentence = "Con permiso, tengo que pasar."),
                Flashcard("s19", "seed_spanish", "¡Buen provecho!", "Приятного аппетита!", exampleSentence = "La cena está servida, ¡buen provecho!"),
                Flashcard("s20", "seed_spanish", "¿Qué hora es?", "Который час?", exampleSentence = "¿Qué hora es, por favor?")
            )
        )

        // 7. Стереометрия и формулы геометрии (16 cards)
        val geometryModule = StudyModule(
            id = "seed_geometry",
            title = "Стереометрия и формулы геометрии: 3D фигуры",
            description = "Формулы объема и площади поверхности конуса, цилиндра, сферы, пирамиды и призмы.",
            category = "Точные науки",
            author = "QuizApp Geometry",
            isVerified = true,
            colorHex = 0xFF10B981,
            cards = listOf(
                Flashcard("g1", "seed_geometry", "Объем конуса", "V = 1/3 · π · r² · h (Треть объема цилиндра с тем же основанием и высотой).", diagramType = "cone"),
                Flashcard("g2", "seed_geometry", "Объем цилиндра", "V = π · r² · h (Произведение площади круга в основании на высоту).", diagramType = "cone"),
                Flashcard("g3", "seed_geometry", "Объем сферы (шара)", "V = 4/3 · π · r³ (Четыре трети пи на куб радиуса).", diagramType = "cone"),
                Flashcard("g4", "seed_geometry", "Площадь сферы", "S = 4 · π · r² (В четыре раза больше площади круга того же радиуса).", diagramType = "cone"),
                Flashcard("g5", "seed_geometry", "Боковая площадь конуса", "S_бок = π · r · l (где l — образующая конуса: l = √(r² + h²)).", diagramType = "cone"),
                Flashcard("g6", "seed_geometry", "Объем пирамиды", "V = 1/3 · S_осн · h (Одна треть произведения площади основания на высоту)."),
                Flashcard("g7", "seed_geometry", "Теорема Пифагора", "a² + b² = c² (В прямоугольном треугольнике квадрат гипотенузы равен сумме квадратов катетов)."),
                Flashcard("g8", "seed_geometry", "Площадь круга", "S = π · r² (где r — радиус, π ≈ 3.14159)."),
                Flashcard("g9", "seed_geometry", "Длина окружности", "C = 2 · π · r = π · d (где d — диаметр круга)."),
                Flashcard("g10", "seed_geometry", "Площадь треугольника", "S = 1/2 · a · h_a (Половина произведения основания на высоту)."),
                Flashcard("g11", "seed_geometry", "Формула Герона", "S = √(p · (p - a) · (p - b) · (p - c)), где p = (a + b + c)/2 — полупериметр."),
                Flashcard("g12", "seed_geometry", "Площадь трапеции", "S = (a + b) / 2 · h (Произведение полусуммы оснований на высоту)."),
                Flashcard("g13", "seed_geometry", "Объем прямоугольного параллелепипеда", "V = a · b · c (Произведение длины, ширины и высоты)."),
                Flashcard("g14", "seed_geometry", "Диагональ куба", "d = a · √3 (где a — длина ребра куба)."),
                Flashcard("g15", "seed_geometry", "Площадь правильного шестиугольника", "S = (3√3 / 2) · a² (Состоит из шести правильных треугольников)."),
                Flashcard("g16", "seed_geometry", "Теорема косинусов", "c² = a² + b² - 2ab · cos(γ) (Обобщение теоремы Пифагора для любого треугольника).")
            )
        )

        // 8. Общая физика и законы природы (18 cards)
        val physicsModule = StudyModule(
            id = "seed_physics",
            title = "Общая физика и законы природы",
            description = "Основные формулы механики, термодинамики, электромагнетизма и оптики.",
            category = "Точные науки",
            author = "QuizApp Physics",
            isVerified = true,
            colorHex = 0xFFEF4444,
            cards = listOf(
                Flashcard("p1", "seed_physics", "Второй закон Ньютона", "F = m · a (Сила равна произведению массы тела на его ускорение)."),
                Flashcard("p2", "seed_physics", "Закон всемирного тяготения", "F = G · (m₁ · m₂) / r² (Сила гравитационного притяжения между двумя телами)."),
                Flashcard("p3", "seed_physics", "Кинетическая энергия", "E_к = (m · v²) / 2 (Энергия движения тела массой m со скоростью v)."),
                Flashcard("p4", "seed_physics", "Потенциальная энергия в поле тяжести", "E_п = m · g · h (где g ≈ 9.8 м/с² — ускорение свободного падения)."),
                Flashcard("p5", "seed_physics", "Закон сохранения энергии", "В изолированной системе полная механическая энергия остается постоянной во времени."),
                Flashcard("p6", "seed_physics", "Закон Ома для участка цепи", "I = U / R (Сила тока прямо пропорциональна напряжению и обратно пропорциональна сопротивлению)."),
                Flashcard("p7", "seed_physics", "Мощность электрического тока", "P = U · I = I² · R = U² / R (Скорость выделения или потребления энергии)."),
                Flashcard("p8", "seed_physics", "Закон Джоуля — Ленца", "Q = I² · R · t (Количество теплоты, выделяемое проводником с током)."),
                Flashcard("p9", "seed_physics", "Закон Архимеда", "F_А = ρ_ж · g · V_погр (Выталкивающая сила равна весу вытесненной телом жидкости)."),
                Flashcard("p10", "seed_physics", "Уравнение Менделеева — Клапейрона", "P · V = (m / M) · R · T = ν · R · T (Уравнение состояния идеального газа)."),
                Flashcard("p11", "seed_physics", "Первый закон термодинамики", "ΔU = Q + A (Изменение внутренней энергии равно сумме переданного тепла и совершенной работы)."),
                Flashcard("p12", "seed_physics", "Скорость света в вакууме", "c ≈ 3 · 10⁸ м/с (Фундаментальная физическая константа, предел скорости в космосе)."),
                Flashcard("p13", "seed_physics", "Формула Планка", "E = h · ν (Энергия кванта света пропорциональна его частоте, где h — постоянная Планка)."),
                Flashcard("p14", "seed_physics", "Эквивалентность массы и энергии", "E = m · c² (Знаменитая релятивистская формула Альберта Эйнштейна)."),
                Flashcard("p15", "seed_physics", "Период математического маятника", "T = 2π · √(L / g) (Формула Гюйгенса для периода малых колебаний)."),
                Flashcard("p16", "seed_physics", "Закон Кулона", "F = k · (|q₁| · |q₂|) / r² (Сила взаимодействия двух неподвижных точечных зарядов)."),
                Flashcard("p17", "seed_physics", "Импульс тела (Количество движения)", "p = m · v (Векторная величина, равная произведению массы на скорость)."),
                Flashcard("p18", "seed_physics", "Давление", "P = F / S (Отношение силы, действующей перпендикулярно поверхности, к площади этой поверхности).")
            )
        )

        return listOf(
            englishModule,
            SeedEnglishModules.createA1Module(),
            SeedEnglishModules.createA2Module(),
            SeedEnglishModules.createOxford3000Module(),
            itModule,
            anatomyModule,
            geoModule,
            historyModule,
            spanishModule,
            geometryModule,
            physicsModule
        )
    }

    private fun createSampleWindHydroCards(): List<Flashcard> {
        return listOf(
            Flashcard("w1", "seed_wind", "Кинетическая энергия ветра", "Энергия перемещения воздушных масс, преобразуемая вращением турбины.", diagramType = "wind"),
            Flashcard("w2", "seed_wind", "Гидроэлектростанция (ГЭС)", "Комплекс сооружений, использующий напор водного потока для генерации электричества.", diagramType = "wind"),
            Flashcard("w3", "seed_wind", "Генератор переменного тока", "Устройство, превращающее механическое вращение ротора в электроэнергию.", diagramType = "wind"),
            Flashcard("w4", "seed_wind", "Возобновляемый источник", "Природный ресурс, восполняемый естественным путем и не приводящий к выбросам парниковых газов.", diagramType = "wind")
        )
    }
}
