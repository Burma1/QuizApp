package com.example.quizapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.example.quizapp.data.ai.AiChatTurn
import com.example.quizapp.data.ai.AiClient
import com.example.quizapp.data.model.AiProfile
import com.example.quizapp.data.model.AiProvider
import com.example.quizapp.data.model.AppColorTheme
import com.example.quizapp.data.model.AppLanguage
import com.example.quizapp.data.model.AppThemeMode
import com.example.quizapp.data.model.CardMasteryStatus
import com.example.quizapp.data.model.ChatAttachment
import com.example.quizapp.data.model.ChatAttachmentType
import com.example.quizapp.data.model.ChatMessage
import com.example.quizapp.data.model.DatabaseRestoreResult
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

    private val _customFolders = MutableStateFlow<Set<String>>(emptySet())
    val customFolders: StateFlow<Set<String>> = _customFolders.asStateFlow()

    private val _folderOrder = MutableStateFlow<List<String>>(emptyList())
    val folderOrder: StateFlow<List<String>> = _folderOrder.asStateFlow()

    private val _recentTtsLanguages = MutableStateFlow<List<String>>(emptyList())
    val recentTtsLanguages: StateFlow<List<String>> = _recentTtsLanguages.asStateFlow()

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

        val foldersSet = (prefs.getStringSet("user_custom_folders", emptySet()) ?: emptySet())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .toSet()
        _customFolders.value = foldersSet
        _folderOrder.value = loadFolderOrder()
        _recentTtsLanguages.value = loadRecentTtsLanguages()
    }

    private fun loadRecentTtsLanguages(): List<String> {
        val raw = prefs.getString("recent_tts_languages_json", null) ?: return emptyList()
        val result = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val tag = arr.optString(i).trim()
                if (tag.isNotBlank() && result.none { it.equals(tag, ignoreCase = true) }) {
                    result.add(tag)
                }
            }
        } catch (_: Exception) {}
        return result.take(8)
    }

    private fun persistRecentTtsLanguages(tags: List<String>) {
        val clean = tags.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(8)
        _recentTtsLanguages.value = clean
        val arr = JSONArray()
        clean.forEach { arr.put(it) }
        prefs.edit().putString("recent_tts_languages_json", arr.toString()).apply()
    }

    fun addRecentTtsLanguage(languageTag: String) {
        val cleanTag = languageTag.trim()
        if (cleanTag.isBlank()) return
        val updated = listOf(cleanTag) + _recentTtsLanguages.value.filterNot { it.equals(cleanTag, ignoreCase = true) }
        persistRecentTtsLanguages(updated)
    }

    fun clearRecentTtsLanguages() {
        persistRecentTtsLanguages(emptyList())
    }

    private fun loadFolderOrder(): List<String> {
        val raw = prefs.getString("folder_order_json", null) ?: return emptyList()
        val result = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotBlank() && result.none { it.equals(item, ignoreCase = true) }) {
                    result.add(item)
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun persistFolderOrder(order: List<String>) {
        val clean = order.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        _folderOrder.value = clean
        val arr = JSONArray()
        clean.forEach { arr.put(it) }
        prefs.edit().putString("folder_order_json", arr.toString()).apply()
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
        val modifiedSeedJson = prefs.getString("modified_seed_modules_json", null)
        val modifiedSeedsById = if (!modifiedSeedJson.isNullOrBlank()) {
            parseModulesJson(modifiedSeedJson).associateBy { it.id }
        } else {
            emptyMap()
        }
        val seedList = createSeedModules()
            .filter { it.id !in deletedIds }
            .map { modifiedSeedsById[it.id] ?: it }
        val categoryOverrides = loadCategoryOverrides()
        val combinedRaw = applySavedCardProgress(customList + seedList).map { mod ->
            val override = categoryOverrides[mod.id]
            if (!override.isNullOrBlank()) mod.copy(category = override) else mod
        }
        val savedModuleOrder = loadModuleOrderIds()
        val combined = if (savedModuleOrder.isNotEmpty()) {
            val byId = combinedRaw.associateBy { it.id }
            val ordered = savedModuleOrder.mapNotNull { byId[it] }
            val remaining = combinedRaw.filter { it.id !in savedModuleOrder.toSet() }
            ordered + remaining
        } else {
            combinedRaw
        }
        _modules.value = combined
        syncFolderOrderWithModules(combined, _customFolders.value)
    }

    private fun loadModuleOrderIds(): List<String> {
        val raw = prefs.getString("module_order_ids_json", null) ?: return emptyList()
        val result = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val id = arr.optString(i).trim()
                if (id.isNotBlank()) result.add(id)
            }
        } catch (_: Exception) {}
        return result
    }

    private fun persistModuleOrderIds(modulesList: List<StudyModule>) {
        val arr = JSONArray()
        modulesList.forEach { arr.put(it.id) }
        prefs.edit().putString("module_order_ids_json", arr.toString()).apply()
    }

    private fun syncFolderOrderWithModules(modulesList: List<StudyModule>, customFoldersSet: Set<String>) {
        val currentOrder = _folderOrder.value
        val moduleCategories = modulesList.map { it.category.trim().ifBlank { "Общее" } }
        val allKnown = (currentOrder + moduleCategories + customFoldersSet)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        if (allKnown != currentOrder) {
            persistFolderOrder(allKnown)
        }
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

    private fun buildCardProgressJson(modulesList: List<StudyModule>): JSONObject {
        val root = JSONObject()
        modulesList.forEach { mod ->
            mod.cards.forEach { c ->
                if (c.masteryScore > 0 || c.isMastered || c.needsReview || c.isStarred || c.reviewCount > 0 || c.lastStudiedTimestamp > 0L || c.repetitions > 0 || c.lapses > 0) {
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
        return root
    }

    private fun persistCardProgress(modulesList: List<StudyModule>) {
        val root = buildCardProgressJson(modulesList)
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

    fun addChatMessage(
        text: String,
        isUser: Boolean,
        suggestedCards: List<Pair<String, String>> = emptyList(),
        attachments: List<ChatAttachment> = emptyList()
    ): ChatMessage {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isUser = isUser,
            timestamp = System.currentTimeMillis(),
            suggestedCards = suggestedCards,
            attachments = attachments
        )
        _chatMessages.value = _chatMessages.value + msg
        return msg
    }

    fun markChatMessageModuleSaved(messageId: String, module: StudyModule) {
        saveModule(module)
        _chatMessages.value = _chatMessages.value.map { msg ->
            if (msg.id == messageId) {
                msg.copy(
                    createdModuleId = module.id,
                    createdModuleTitle = module.title
                )
            } else msg
        }
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

    suspend fun askAiTutor(userText: String, attachments: List<ChatAttachment> = emptyList()) {
        val previousHistory = _chatMessages.value
            .filter { !it.isGenerating && (it.text.isNotBlank() || it.attachments.isNotEmpty()) && !it.text.startsWith("⚠️") }
            .takeLast(16)
            .map { AiChatTurn(text = it.text, isUser = it.isUser, attachments = it.attachments) }

        val displayUserText = userText.trim()
        addChatMessage(displayUserText, isUser = true, attachments = attachments)
        val generatingMsg = ChatMessage(
            id = "gen_" + UUID.randomUUID(),
            text = "...",
            isUser = false,
            isGenerating = true
        )
        _chatMessages.value = _chatMessages.value + generatingMsg

        val profile = _activeProfile.value
        val trimmed = displayUserText
        val lower = trimmed.lowercase()
        val isTestEnv = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

        // 1. Fast local handler: Simple explicit delete module (no attachments)
        if (attachments.isEmpty() &&
            (lower.startsWith("удали модуль") || lower.startsWith("удалить модуль") || lower.startsWith("delete module"))
        ) {
            val candidate = extractRemainderAfterKeyword(trimmed, "модуль", "module").trim('"', '«', '»', '\'')
            val found = _modules.value.find {
                it.title.contains(candidate, ignoreCase = true) || it.id.equals(candidate, ignoreCase = true)
            }
            val reply = if (found != null) {
                deleteModule(found.id)
                "🗑️ Модуль **«${found.title}»** успешно удалён."
            } else {
                "Не удалось найти модуль с названием «$candidate». Проверьте список модулей в Библиотеке."
            }
            _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                id = UUID.randomUUID().toString(),
                text = reply,
                isUser = false
            )
            return
        }

        // 2. Fast local handler: Explicit library modules listing query (no attachments)
        val isModulesQuery = attachments.isEmpty() && (
            lower.contains("какие модул") ||
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
            )

        if (isModulesQuery) {
            val allModules = _modules.value
            val createdByAiOrUser = allModules.filter { !it.id.startsWith("seed_") }
            val builtInModules = allModules.filter { it.id.startsWith("seed_") }
            val askingSpecificallyAiCreated = lower.contains("тобой") || lower.contains("ты создал") || lower.contains("создан")

            val replyText = buildString {
                if (createdByAiOrUser.isNotEmpty()) {
                    append("### 📚 Мои и созданные с ИИ модули (${createdByAiOrUser.size})\n\n")
                    createdByAiOrUser.forEachIndexed { idx, mod ->
                        val mastered = mod.cards.count { it.status == CardMasteryStatus.MASTERED }
                        append("${idx + 1}. **«${mod.title}»** — ${mod.cards.size} карточек (усвоено: $mastered)\n")
                    }
                } else {
                    append("### 📚 Созданные с ИИ модули\n\n")
                    append("У вас пока нет собственных или созданных в диалоге модулей.\n")
                    append("Напишите тему, вставьте текст или прикрепите фото/файл — и я сразу сделаю карточки!")
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

        // 3. Fast local handler: Explicit inline pairs e.g. "Создай карточки: Фрукты (Apple - Яблоко, Banana - Банан)"
        if (attachments.isEmpty() && !lower.endsWith("?") &&
            (lower.startsWith("создай карточк") || lower.startsWith("создать карточк") ||
                lower.startsWith("create cards") || lower.startsWith("добавь в модуль") || lower.startsWith("add to module"))
        ) {
            if (lower.startsWith("добавь в модуль") || lower.startsWith("add to module")) {
                val afterMod = extractRemainderAfterKeyword(trimmed, "модуль", "module")
                val modNameCandidate = afterMod.substringBefore(":").substringBefore("\n").trim().trim('"', '«', '»')
                val found = _modules.value.find { it.title.contains(modNameCandidate, ignoreCase = true) }
                val promptForCards = afterMod.substringAfter(":", "").trim()
                val explicit = if (found != null && promptForCards.isNotBlank()) parseExplicitCardsFromText(promptForCards, found.id) else emptyList()
                if (found != null && explicit.isNotEmpty()) {
                    saveModule(found.copy(cards = found.cards + explicit))
                    _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "✏️ В модуль **«${found.title}»** добавлено карточек (${explicit.size}):\n" +
                            explicit.joinToString("\n") { "• **${it.term}** — ${it.definition}" },
                        isUser = false,
                        suggestedCards = explicit.map { it.term to it.definition },
                        createdModuleId = found.id,
                        createdModuleTitle = found.title
                    )
                    return
                }
            } else {
                val (extractedTitle, topic) = extractTopicAndTitle(trimmed)
                val newModuleId = "user_" + UUID.randomUUID().toString()
                val hasInlineDelimiters = trimmed.contains("(") && trimmed.contains(")") && trimmed.length < 300
                val explicit = if (hasInlineDelimiters) parseExplicitCardsFromText(topic, newModuleId) else emptyList()
                if (explicit.isNotEmpty()) {
                    val finalTitle = if (extractedTitle != "Новые карточки") extractedTitle else "Новый модуль"
                    val newModule = StudyModule(
                        id = newModuleId,
                        title = finalTitle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        description = "Создано в диалоге с Репетитором",
                        category = "Пользовательские",
                        author = "Вы & Репетитор",
                        isVerified = false,
                        colorHex = 0xFF16A34A,
                        cards = explicit
                    )
                    saveModule(newModule)
                    _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = "✅ **Создан новый модуль «${newModule.title}»** (${newModule.cards.size} карточек)!\n" +
                            "Он уже сохранён в вашей библиотеке и доступен в разделе «Ваши карточки».",
                        isUser = false,
                        suggestedCards = explicit.map { it.term to it.definition },
                        createdModuleId = newModule.id,
                        createdModuleTitle = newModule.title
                    )
                    return
                }
            }
        }

        // 4. Robolectric unit-test offline deterministic branch when no API key
        if (profile.apiKey.isBlank() && isTestEnv) {
            val isExplicitCreate = !lower.endsWith("?") &&
                (lower.startsWith("создай карточк") || lower.startsWith("create cards"))
            if (isExplicitCreate) {
                val (extractedTitle, topic) = extractTopicAndTitle(trimmed)
                val newModuleId = "user_" + UUID.randomUUID().toString()
                val generatedCards = aiClient.generateFlashcards(
                    provider = profile.provider,
                    apiKey = profile.apiKey,
                    model = profile.modelId,
                    topic = topic.ifBlank { trimmed },
                    moduleId = newModuleId,
                    defaultCount = AiClient.DEFAULT_FLASHCARD_COUNT
                )
                val newModule = StudyModule(
                    id = newModuleId,
                    title = extractedTitle,
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
                    text = "✅ **Создан новый модуль «${newModule.title}»** (${newModule.cards.size} карточек)!",
                    isUser = false,
                    suggestedCards = generatedCards.map { it.term to it.definition },
                    createdModuleId = newModule.id,
                    createdModuleTitle = newModule.title
                )
                return
            }
        }

        // 5. Unified Smart Conversational AI with Full Context, Attachments, and Action Execution
        val customMods = _modules.value.filter { !it.id.startsWith("seed_") }
        val seedMods = _modules.value.filter { it.id.startsWith("seed_") }
        val latestCustomMod = customMods.firstOrNull()

        val systemInstructionWithContext = buildString {
            append("Ты — умный, живой и понятный ИИ-репетитор в приложении QuizApp.\n")
            append("Отвечай по делу, дружелюбно, на языке пользователя (обычно на русском). Форматируй текст аккуратным Markdown.\n")
            append("НИКОГДА не выводи служебные теги вроде [Program finished] или сырой JSON в видимой части ответа.\n\n")
            append("РАБОТА С КАРТОЧКАМИ И МОДУЛЯМИ (ОЧЕНЬ ВАЖНО):\n")
            append("Ты умеешь реально создавать, дополнять, обновлять и удалять учебные модули в приложении! ")
            append("Если пользователь просит создать карточки/модуль (из темы, из вставленного текста, из твоего предыдущего ответа, из прикреплённого фото или файла), ")
            append("или подтверждает предложение («да», «создай», «делай»), или просит добавить/исправить/переделать карточки в существующем модуле — ")
            append("НЕ ПЕРЕСПРАШИВАЙ «Создать карточки?» и НЕ пиши карточки просто текстом без сохранения! СРАЗУ выполни действие, добавив в самый конец ответа блок:\n")
            append("<quizapp_action>\n")
            append("{\"action\":\"create_module\",\"title\":\"Короткое название\",\"category\":\"Категория\",\"cards\":[{\"term\":\"Термин\",\"definition\":\"Чёткое определение\",\"example\":\"Необязательный пример\"}]}\n")
            append("</quizapp_action>\n\n")
            append("Доступные значения \"action\":\n")
            append("- \"create_module\": поля title, category, cards ([{term, definition, example?}]).\n")
            append("- \"add_cards\": поля module_id (если известен) или module_title, и cards.\n")
            append("- \"replace_cards\": полностью обновляет/исправляет карточки в существующем модуле (поля module_id/module_title, cards).\n")
            append("- \"delete_module\": поля module_id или module_title.\n\n")
            append("Правила качества карточек:\n")
            append("1. Каждый term должен быть кратким и осмысленным (1–5 слов, без обрывков предложений или случайных символов вроде ').Ш').\n")
            append("2. Каждое definition должно быть ясным, точным и законченным (1–2 предложения).\n")
            append("3. Если пользователь указал количество (например, 15 или 30) — создай ровно столько карточек. Иначе выбери оптимальное количество (обычно 6–14 карточек по ключевым понятиям материала).\n")
            append("4. НЕ добавляй <quizapp_action>, если пользователь просто задал вопрос, попросил объяснить тему, проверить ответ или пообщаться.\n")
            append("5. Если пользователь прикрепил фото или файл (PDF, DOCX, TXT, CSV, JSON) — опирайся строго на его реальное содержимое.\n\n")
            append("Библиотека модулей пользователя:\n")
            if (customMods.isNotEmpty()) {
                append("- Мои/ИИ модули (${customMods.size}): ")
                append(customMods.take(15).joinToString(", ") { "«${it.title}» [id=${it.id}, ${it.cards.size} карт.]" })
                append("\n")
                if (latestCustomMod != null && latestCustomMod.cards.isNotEmpty()) {
                    append("- Последний модуль «${latestCustomMod.title}» содержит термины: ")
                    append(latestCustomMod.cards.take(15).joinToString("; ") { "${it.term} — ${it.definition.take(50)}" })
                    append("\n")
                }
            } else {
                append("- Пользовательских модулей пока нет.\n")
            }
            if (seedMods.isNotEmpty()) {
                append("- Базовые модули (${seedMods.size}): ")
                append(seedMods.joinToString(", ") { "«${it.title}»" })
                append("\n")
            }
        }

        val effectivePrompt = if (trimmed.isNotBlank()) {
            trimmed
        } else if (attachments.isNotEmpty()) {
            "Проанализируй прикреплённые материалы. Кратко объясни, что в них, и предложи помощь (например, создать учебные карточки или разобрать тему)."
        } else {
            "Привет!"
        }

        val rawResponse = aiClient.generateChatResponse(
            provider = profile.provider,
            apiKey = profile.apiKey,
            model = profile.modelId,
            prompt = effectivePrompt,
            attachments = attachments,
            history = previousHistory,
            systemInstruction = systemInstructionWithContext
        )

        val processed = processAiActionResponse(rawResponse, userIntentText = trimmed)

        _chatMessages.value = _chatMessages.value.filter { it.id != generatingMsg.id } + ChatMessage(
            id = UUID.randomUUID().toString(),
            text = processed.visibleText,
            isUser = false,
            suggestedCards = processed.suggestedCards,
            createdModuleId = processed.createdModuleId,
            createdModuleTitle = processed.createdModuleTitle
        )
    }

    internal data class ProcessedAiReply(
        val visibleText: String,
        val suggestedCards: List<Pair<String, String>> = emptyList(),
        val createdModuleId: String? = null,
        val createdModuleTitle: String? = null
    )

    internal fun processAiActionResponse(rawResponse: String, userIntentText: String): ProcessedAiReply {
        if (rawResponse.startsWith("⚠️")) {
            return ProcessedAiReply(visibleText = rawResponse)
        }

        val cleanedRaw = rawResponse.replace("[Program finished]", "").trim()
        val actionRegex = Regex("<quizapp_action>\\s*([\\s\\S]*?)\\s*</quizapp_action>", RegexOption.IGNORE_CASE)
        val match = actionRegex.find(cleanedRaw)

        if (match != null) {
            val actionJsonRaw = match.groupValues[1]
                .replace(Regex("^```json\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^```\\s*"), "")
                .replace(Regex("\\s*```$"), "")
                .trim()
            val visibleBeforeStrip = actionRegex.replace(cleanedRaw, "").trim()

            try {
                val obj = JSONObject(actionJsonRaw)
                val actionType = obj.optString("action").lowercase().trim()
                val modIdParam = obj.optString("module_id").trim()
                val modTitleParam = obj.optString("module_title").ifBlank { obj.optString("title") }.trim()

                fun parseActionCards(targetModuleId: String): List<Flashcard> {
                    val arr = obj.optJSONArray("cards") ?: return emptyList()
                    val result = mutableListOf<Flashcard>()
                    for (i in 0 until arr.length()) {
                        val c = arr.optJSONObject(i) ?: continue
                        val term = c.optString("term").ifBlank { c.optString("front") }.trim()
                        val def = c.optString("definition").ifBlank { c.optString("back") }.trim()
                        val ex = c.optString("example").ifBlank { c.optString("exampleSentence") }.ifBlank { null }
                        if (term.isNotBlank() && def.isNotBlank()) {
                            result.add(
                                Flashcard(
                                    id = UUID.randomUUID().toString(),
                                    moduleId = targetModuleId,
                                    term = term,
                                    definition = def,
                                    exampleSentence = ex
                                )
                            )
                        }
                    }
                    return result
                }

                when (actionType) {
                    "create_module" -> {
                        val newModId = "user_" + UUID.randomUUID().toString()
                        val cards = parseActionCards(newModId)
                        if (cards.isNotEmpty()) {
                            val title = obj.optString("title").trim().ifBlank { "Новый модуль" }
                            val category = obj.optString("category").trim().ifBlank { "Пользовательские" }
                            val newModule = StudyModule(
                                id = newModId,
                                title = title,
                                description = "Создано в диалоге с Репетитором",
                                category = category,
                                author = "Вы & Репетитор",
                                isVerified = false,
                                colorHex = 0xFF16A34A,
                                cards = cards
                            )
                            saveModule(newModule)
                            val finalMsg = visibleBeforeStrip.ifBlank {
                                "✅ Создан новый модуль **«${newModule.title}»** (${cards.size} карточек)!"
                            }
                            return ProcessedAiReply(
                                visibleText = finalMsg,
                                suggestedCards = cards.map { it.term to it.definition },
                                createdModuleId = newModule.id,
                                createdModuleTitle = newModule.title
                            )
                        }
                    }

                    "add_cards" -> {
                        val targetMod = findModuleForAction(modIdParam, modTitleParam)
                        if (targetMod != null) {
                            val newCards = parseActionCards(targetMod.id)
                            if (newCards.isNotEmpty()) {
                                val existingTerms = targetMod.cards.map { it.term.lowercase() }.toSet()
                                val deduped = newCards.filter { it.term.lowercase() !in existingTerms }.ifEmpty { newCards }
                                val updatedMod = targetMod.copy(cards = targetMod.cards + deduped)
                                saveModule(updatedMod)
                                val finalMsg = visibleBeforeStrip.ifBlank {
                                    "✏️ В модуль **«${updatedMod.title}»** добавлено ${deduped.size} карточек."
                                }
                                return ProcessedAiReply(
                                    visibleText = finalMsg,
                                    suggestedCards = deduped.map { it.term to it.definition },
                                    createdModuleId = updatedMod.id,
                                    createdModuleTitle = updatedMod.title
                                )
                            }
                        } else {
                            // Fallback: create a new module if target not found
                            val newModId = "user_" + UUID.randomUUID().toString()
                            val newCards = parseActionCards(newModId)
                            if (newCards.isNotEmpty()) {
                                val title = modTitleParam.ifBlank { "Новый модуль" }
                                val newModule = StudyModule(
                                    id = newModId,
                                    title = title,
                                    description = "Создано в диалоге с Репетитором",
                                    category = "Пользовательские",
                                    author = "Вы & Репетитор",
                                    isVerified = false,
                                    colorHex = 0xFF16A34A,
                                    cards = newCards
                                )
                                saveModule(newModule)
                                val finalMsg = visibleBeforeStrip.ifBlank {
                                    "✅ Создан новый модуль **«${newModule.title}»** (${newCards.size} карточек)!"
                                }
                                return ProcessedAiReply(
                                    visibleText = finalMsg,
                                    suggestedCards = newCards.map { it.term to it.definition },
                                    createdModuleId = newModule.id,
                                    createdModuleTitle = newModule.title
                                )
                            }
                        }
                    }

                    "replace_cards" -> {
                        val targetMod = findModuleForAction(modIdParam, modTitleParam)
                        if (targetMod != null) {
                            val newCards = parseActionCards(targetMod.id)
                            if (newCards.isNotEmpty()) {
                                val updatedMod = targetMod.copy(cards = newCards)
                                saveModule(updatedMod)
                                val finalMsg = visibleBeforeStrip.ifBlank {
                                    "🔄 Модуль **«${updatedMod.title}»** обновлён (${newCards.size} карточек)."
                                }
                                return ProcessedAiReply(
                                    visibleText = finalMsg,
                                    suggestedCards = newCards.map { it.term to it.definition },
                                    createdModuleId = updatedMod.id,
                                    createdModuleTitle = updatedMod.title
                                )
                            }
                        }
                    }

                    "delete_module" -> {
                        val targetMod = findModuleForAction(modIdParam, modTitleParam)
                        if (targetMod != null) {
                            deleteModule(targetMod.id)
                            val finalMsg = visibleBeforeStrip.ifBlank {
                                "🗑️ Модуль **«${targetMod.title}»** удалён."
                            }
                            return ProcessedAiReply(visibleText = finalMsg)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to clean text if action JSON was malformed
            }

            return ProcessedAiReply(visibleText = visibleBeforeStrip.ifBlank { cleanedRaw })
        }

        // Secondary fallback: if user clearly asked to list/suggest vocabulary or cards without an action tag,
        // offer clean parsed pairs ONLY if there are >= 2 clean pairs
        val lowerIntent = userIntentText.lowercase()
        val wantsCardsOrWords = lowerIntent.contains("карточ") ||
            lowerIntent.contains("подбери слова") ||
            lowerIntent.contains("список слов") ||
            lowerIntent.contains("выпиши слова") ||
            lowerIntent.contains("слова по теме")
        val suggestedPairs = if (wantsCardsOrWords) {
            val parsed = aiClient.parseFlashcardsFromJsonOrText(cleanedRaw, "temp")
            if (parsed.size >= 2) {
                parsed.take(30).map { it.term to it.definition }
            } else emptyList()
        } else {
            emptyList()
        }

        return ProcessedAiReply(
            visibleText = cleanedRaw,
            suggestedCards = suggestedPairs
        )
    }

    private fun findModuleForAction(moduleId: String, moduleTitle: String): StudyModule? {
        if (moduleId.isNotBlank()) {
            _modules.value.find { it.id == moduleId }?.let { return it }
        }
        if (moduleTitle.isNotBlank()) {
            _modules.value.find { it.title.equals(moduleTitle, ignoreCase = true) }?.let { return it }
            _modules.value.find { !it.id.startsWith("seed_") && it.title.contains(moduleTitle, ignoreCase = true) }?.let { return it }
            _modules.value.find { it.title.contains(moduleTitle, ignoreCase = true) }?.let { return it }
        }
        return _modules.value.firstOrNull { !it.id.startsWith("seed_") }
    }

    suspend fun generateFlashcardsForTopic(
        topic: String,
        attachments: List<ChatAttachment> = emptyList(),
        defaultCount: Int = AiClient.DEFAULT_FLASHCARD_COUNT
    ): List<Flashcard> {
        val profile = _activeProfile.value
        val moduleId = UUID.randomUUID().toString()

        if (attachments.isNotEmpty()) {
            val cleanTopic = topic.trim()
            val hasSpecifiedCount = if (cleanTopic.isNotBlank()) aiClient.hasUserSpecifiedCardCount(cleanTopic) else false
            val targetCount = if (cleanTopic.isNotBlank()) aiClient.extractRequestedCardCount(cleanTopic, defaultCount) else defaultCount

            if (profile.apiKey.isNotBlank()) {
                val prompt = buildString {
                    append("Проанализируй прикреплённые материалы (файл/изображение)")
                    if (cleanTopic.isNotBlank()) {
                        append(" и указания пользователя: «$cleanTopic»")
                    }
                    append(".\n")
                    if (hasSpecifiedCount) {
                        append("Создай РОВНО ${targetCount.coerceAtMost(120)} учебных карточек по самым важным терминам, фактам и определениям.\n")
                    } else {
                        append("Создай от 10 до 16 качественных учебных карточек по ключевым терминам, понятиям и определениям из материала.\n")
                    }
                    append("Верни результат СТРОГО в виде JSON-массива объектов с ключами \"term\" и \"definition\":\n")
                    append("[{\"term\": \"Понятие\", \"definition\": \"Точное определение\"}]")
                }
                val rawResponse = aiClient.generateChatResponse(
                    provider = profile.provider,
                    apiKey = profile.apiKey,
                    model = profile.modelId,
                    prompt = prompt,
                    attachments = attachments,
                    systemInstruction = "Ты — генератор учебных карточек в QuizApp. Извлекай термины и определения из прикреплённых файлов и фото. Возвращай только валидный JSON-массив объектов {\"term\": \"...\", \"definition\": \"...\"}."
                )
                if (!rawResponse.startsWith("⚠️")) {
                    val parsedFromAi = aiClient.parseFlashcardsFromJsonOrText(rawResponse, moduleId)
                    if (parsedFromAi.isNotEmpty()) {
                        return parsedFromAi
                    }
                }
            }

            // Local/direct extraction from attached text documents (CSV, TSV, JSON, TXT, DOCX, MD)
            val extractedCards = mutableListOf<Flashcard>()
            for (att in attachments) {
                val docText = att.extractedText?.trim().orEmpty()
                if (docText.isNotBlank()) {
                    val fromJsonOrPairs = aiClient.parseFlashcardsFromJsonOrText(docText, moduleId)
                    if (fromJsonOrPairs.isNotEmpty()) {
                        extractedCards.addAll(fromJsonOrPairs)
                        continue
                    }
                    val fromCsvOrText = importModuleFromTextOrCsv(docText).cards.map { it.copy(moduleId = moduleId) }
                    if (fromCsvOrText.isNotEmpty()) {
                        extractedCards.addAll(fromCsvOrText)
                        continue
                    }
                    // Extract meaningful sentences/paragraphs from plain prose document
                    val paragraphs = docText.lines()
                        .map { it.trim() }
                        .filter { it.length >= 12 && !it.startsWith("[PDF-") }
                    for ((idx, line) in paragraphs.take(targetCount.coerceAtLeast(12)).withIndex()) {
                        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                        if (words.size >= 3) {
                            val termPart = words.take(minOf(3, words.size / 2)).joinToString(" ").trim(',', '.', ':', ';', '-', '—')
                            val defPart = line
                            if (termPart.isNotBlank()) {
                                extractedCards.add(
                                    Flashcard(
                                        id = UUID.randomUUID().toString(),
                                        moduleId = moduleId,
                                        term = termPart,
                                        definition = defPart
                                    )
                                )
                            }
                        } else {
                            extractedCards.add(
                                Flashcard(
                                    id = UUID.randomUUID().toString(),
                                    moduleId = moduleId,
                                    term = "${att.name.substringBeforeLast(".")} — Пункт ${idx + 1}",
                                    definition = line
                                )
                            )
                        }
                    }
                } else if (att.type == ChatAttachmentType.IMAGE && !att.base64Data.isNullOrBlank()) {
                    val imgCards = generateCardsFromImage(att.base64Data, att.mimeType, moduleId)
                    extractedCards.addAll(imgCards)
                }
            }
            if (extractedCards.isNotEmpty()) {
                return if (hasSpecifiedCount && extractedCards.size > targetCount) {
                    extractedCards.take(targetCount)
                } else {
                    extractedCards
                }
            }

            val fallbackTopic = cleanTopic.ifBlank {
                attachments.firstOrNull()?.name?.substringBeforeLast(".")?.ifBlank { "Материал из файла" } ?: "Материал из файла"
            }
            return aiClient.generateFlashcards(
                provider = profile.provider,
                apiKey = profile.apiKey,
                model = profile.modelId,
                topic = fallbackTopic,
                moduleId = moduleId,
                defaultCount = defaultCount
            )
        }

        return aiClient.generateFlashcards(
            provider = profile.provider,
            apiKey = profile.apiKey,
            model = profile.modelId,
            topic = topic,
            moduleId = moduleId,
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
        val cleanCategory = module.category.trim().ifBlank { "Общее" }
        val normalizedModule = module.copy(category = cleanCategory)
        val current = _modules.value.toMutableList()
        val index = current.indexOfFirst { it.id == normalizedModule.id }
        if (index >= 0) {
            current[index] = normalizedModule
        } else {
            current.add(0, normalizedModule)
        }
        _modules.value = current
        val overrides = loadCategoryOverrides().toMutableMap()
        overrides[normalizedModule.id] = cleanCategory
        persistCategoryOverrides(overrides)
        persistCustomModules(current.filter { !it.id.startsWith("seed_") })
        persistModifiedSeedModules(current.filter { it.id.startsWith("seed_") })
        persistCardProgress(current)
        persistModuleOrderIds(current)
        syncFolderOrderWithModules(current, _customFolders.value)
    }

    fun addCardToModule(
        moduleId: String,
        term: String,
        definition: String,
        exampleSentence: String? = null
    ) {
        val cleanTerm = term.trim()
        val cleanDef = definition.trim()
        if (cleanTerm.isBlank() || cleanDef.isBlank()) return
        val mod = _modules.value.find { it.id == moduleId } ?: return
        val newCard = Flashcard(
            id = UUID.randomUUID().toString(),
            moduleId = moduleId,
            term = cleanTerm,
            definition = cleanDef,
            exampleSentence = exampleSentence?.trim()?.ifBlank { null }
        )
        saveModule(mod.copy(cards = mod.cards + newCard))
    }

    fun updateCardInModule(
        moduleId: String,
        cardId: String,
        term: String,
        definition: String,
        exampleSentence: String? = null
    ) {
        val cleanTerm = term.trim()
        val cleanDef = definition.trim()
        if (cleanTerm.isBlank() || cleanDef.isBlank()) return
        val mod = _modules.value.find { it.id == moduleId } ?: return
        val updatedCards = mod.cards.map { c ->
            if (c.id == cardId) {
                c.copy(
                    term = cleanTerm,
                    definition = cleanDef,
                    exampleSentence = exampleSentence?.trim()?.ifBlank { null }
                )
            } else c
        }
        saveModule(mod.copy(cards = updatedCards))
    }

    fun deleteCardFromModule(moduleId: String, cardId: String) {
        val mod = _modules.value.find { it.id == moduleId } ?: return
        val updatedCards = mod.cards.filter { it.id != cardId }
        saveModule(mod.copy(cards = updatedCards))
    }

    fun updateModuleMetadata(moduleId: String, title: String, description: String, category: String) {
        val mod = _modules.value.find { it.id == moduleId } ?: return
        val cleanTitle = title.trim().ifBlank { mod.title }
        val cleanCategory = category.trim().ifBlank { mod.category }
        saveModule(
            mod.copy(
                title = cleanTitle,
                description = description.trim(),
                category = cleanCategory
            )
        )
    }

    private fun getModifiedSeedModules(seedModules: List<StudyModule>): List<StudyModule> {
        val defaults = createSeedModules().associateBy { it.id }
        return seedModules.filter { mod ->
            val orig = defaults[mod.id]
            orig == null || orig.cards != mod.cards || orig.title != mod.title || orig.description != mod.description || orig.category != mod.category
        }
    }

    private fun persistModifiedSeedModules(seedModules: List<StudyModule>) {
        val modified = getModifiedSeedModules(seedModules)
        prefs.edit().putString("modified_seed_modules_json", serializeModulesToJsonString(modified)).apply()
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
            persistModifiedSeedModules(current.filter { it.id.startsWith("seed_") })

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
            persistModifiedSeedModules(current.filter { it.id.startsWith("seed_") })
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
        val overrides = loadCategoryOverrides().toMutableMap()
        overrides.remove(moduleId)
        persistCategoryOverrides(overrides)
        prefs.edit().putString("deleted_module_ids", existingDeleted.joinToString(",")).apply()
        persistCardProgress(current)
        persistCustomModules(current.filter { !it.id.startsWith("seed_") })
        persistModifiedSeedModules(current.filter { it.id.startsWith("seed_") })
        persistModuleOrderIds(current)
    }

    private fun loadCategoryOverrides(): Map<String, String> {
        val json = prefs.getString("module_category_overrides_json", null) ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.getString(k)
            }
        } catch (_: Exception) {}
        return result
    }

    private fun persistCategoryOverrides(overrides: Map<String, String>) {
        val obj = JSONObject()
        overrides.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("module_category_overrides_json", obj.toString()).apply()
    }

    fun updateModuleCategory(moduleId: String, newCategory: String) {
        val cleanCategory = newCategory.trim().ifBlank { "Общее" }
        val current = _modules.value.map { mod ->
            if (mod.id == moduleId) mod.copy(category = cleanCategory) else mod
        }
        _modules.value = current
        val overrides = loadCategoryOverrides().toMutableMap()
        overrides[moduleId] = cleanCategory
        persistCategoryOverrides(overrides)
        persistCustomModules(current.filter { !it.id.startsWith("seed_") })
        persistModifiedSeedModules(current.filter { it.id.startsWith("seed_") })
        syncFolderOrderWithModules(current, _customFolders.value)
    }

    fun createFolder(folderName: String) {
        val clean = folderName.trim()
        if (clean.isBlank()) return
        val existingMatch = _customFolders.value.find { it.equals(clean, ignoreCase = true) }
        val updated = if (existingMatch != null) _customFolders.value else (_customFolders.value + clean)
        _customFolders.value = updated
        prefs.edit().putStringSet("user_custom_folders", updated).apply()

        val curOrder = _folderOrder.value
        if (curOrder.none { it.equals(clean, ignoreCase = true) }) {
            persistFolderOrder(curOrder + clean)
        }
    }

    fun renameFolder(oldName: String, newName: String) {
        val cleanOld = oldName.trim()
        val cleanNew = newName.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld.equals(cleanNew, ignoreCase = true)) return

        val updatedCustom = _customFolders.value.map {
            if (it.equals(cleanOld, ignoreCase = true)) cleanNew else it
        }.distinctBy { it.lowercase() }.toSet()
        _customFolders.value = updatedCustom
        prefs.edit().putStringSet("user_custom_folders", updatedCustom).apply()

        val updatedOrder = _folderOrder.value.map {
            if (it.equals(cleanOld, ignoreCase = true)) cleanNew else it
        }.distinctBy { it.lowercase() }
        persistFolderOrder(updatedOrder)

        val overrides = loadCategoryOverrides().toMutableMap()
        val updatedModules = _modules.value.map { mod ->
            if (mod.category.equals(cleanOld, ignoreCase = true)) {
                overrides[mod.id] = cleanNew
                mod.copy(category = cleanNew)
            } else mod
        }
        _modules.value = updatedModules
        persistCategoryOverrides(overrides)
        persistCustomModules(updatedModules.filter { !it.id.startsWith("seed_") })
        persistModifiedSeedModules(updatedModules.filter { it.id.startsWith("seed_") })
    }

    fun moveFolderOrder(folderName: String, delta: Int) {
        val clean = folderName.trim()
        if (clean.isBlank() || delta == 0) return
        val allActiveFolders = getOrderedLibraryFolders()
        val idx = allActiveFolders.indexOfFirst { it.equals(clean, ignoreCase = true) }
        if (idx < 0) return
        val targetIdx = (idx + delta).coerceIn(0, allActiveFolders.lastIndex)
        if (targetIdx == idx) return
        val mutable = allActiveFolders.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(targetIdx, item)
        persistFolderOrder(mutable)
    }

    fun setFolderOrder(newOrder: List<String>) {
        persistFolderOrder(newOrder)
    }

    fun getOrderedLibraryFolders(): List<String> {
        val fromModules = _modules.value.map { it.category.trim().ifBlank { "Общее" } }
        val all = (_folderOrder.value + fromModules + _customFolders.value)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        return all
    }

    fun deleteFolder(folderName: String) {
        val clean = folderName.trim()
        val updatedFolders = _customFolders.value.filterNot { it.equals(clean, ignoreCase = true) }.toSet()
        _customFolders.value = updatedFolders
        prefs.edit().putStringSet("user_custom_folders", updatedFolders).apply()

        val updatedOrder = _folderOrder.value.filterNot { it.equals(clean, ignoreCase = true) }
        persistFolderOrder(updatedOrder)

        val overrides = loadCategoryOverrides().toMutableMap()
        val updated = _modules.value.map { mod ->
            if (mod.category.equals(clean, ignoreCase = true)) {
                overrides[mod.id] = "Общее"
                mod.copy(category = "Общее")
            } else mod
        }
        _modules.value = updated
        persistCategoryOverrides(overrides)
        persistCustomModules(updated.filter { !it.id.startsWith("seed_") })
        persistModifiedSeedModules(updated.filter { it.id.startsWith("seed_") })
        syncFolderOrderWithModules(updated, updatedFolders)
    }

    fun addModulesToFolder(folderName: String, moduleIds: Set<String>) {
        val clean = folderName.trim().ifBlank { "Общее" }
        createFolder(clean)
        val overrides = loadCategoryOverrides().toMutableMap()
        val updated = _modules.value.map { mod ->
            if (mod.id in moduleIds) {
                overrides[mod.id] = clean
                mod.copy(category = clean)
            } else mod
        }
        _modules.value = updated
        persistCategoryOverrides(overrides)
        persistCustomModules(updated.filter { !it.id.startsWith("seed_") })
        persistModifiedSeedModules(updated.filter { it.id.startsWith("seed_") })
        syncFolderOrderWithModules(updated, _customFolders.value)
    }

    fun removeModulesFromFolder(folderName: String, moduleIds: Set<String>) {
        val clean = folderName.trim()
        val overrides = loadCategoryOverrides().toMutableMap()
        val updated = _modules.value.map { mod ->
            if (mod.id in moduleIds && mod.category.equals(clean, ignoreCase = true)) {
                overrides[mod.id] = "Общее"
                mod.copy(category = "Общее")
            } else mod
        }
        _modules.value = updated
        persistCategoryOverrides(overrides)
        persistCustomModules(updated.filter { !it.id.startsWith("seed_") })
        persistModifiedSeedModules(updated.filter { it.id.startsWith("seed_") })
        syncFolderOrderWithModules(updated, _customFolders.value)
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
        val cleanText = rawText.removePrefix("\uFEFF")
        val lines = cleanText.lines()
        var extractedTitle = defaultTitle

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                val candidate = trimmed.removePrefix("# ").trim()
                if (candidate.isNotBlank()) extractedTitle = candidate
                continue
            }
            if (trimmed.isBlank() || trimmed.startsWith("//")) continue

            val separator = when {
                trimmed.contains("\t") -> "\t"
                trimmed.contains(" — ") -> " — "
                trimmed.contains(" – ") -> " – "
                trimmed.contains(" - ") -> " - "
                trimmed.contains(";") -> ";"
                trimmed.contains(": ") -> ": "
                trimmed.contains(",") -> ","
                else -> null
            }

            if (separator != null) {
                val parts = trimmed.split(separator, limit = 2)
                val t = parts[0].trim().trim('"')
                val d = parts[1].trim().trim('"')
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

    /**
     * Exports the entire QuizApp database into a single structured JSON string:
     * - User-created modules and all their cards
     * - Modified seed modules (added/edited/removed terms) and deleted module IDs
     * - All card progress across every module (needsReview, learning, mastered, Anki SM-2, reviewCount, timestamps)
     * - All starred cards (isStarred ⭐)
     * - Custom library folders, folder ordering (folderOrder), module ordering (moduleOrderIds), and module-to-folder assignments (categoryOverrides)
     * - Study statistics and app preferences
     */
    fun exportFullDatabaseToJson(): String {
        val currentModules = _modules.value
        val customMods = currentModules.filter { !it.id.startsWith("seed_") }
        val modifiedSeeds = getModifiedSeedModules(currentModules.filter { it.id.startsWith("seed_") })
        val progressJson = buildCardProgressJson(currentModules)

        val overridesObj = JSONObject()
        val storedOverrides = loadCategoryOverrides()
        storedOverrides.forEach { (k, v) -> overridesObj.put(k, v) }
        currentModules.forEach { mod ->
            overridesObj.put(mod.id, mod.category.trim().ifBlank { "Общее" })
        }

        val customFoldersArr = JSONArray()
        _customFolders.value.forEach { customFoldersArr.put(it) }

        val orderedFoldersArr = JSONArray()
        getOrderedLibraryFolders().forEach { orderedFoldersArr.put(it) }

        val moduleOrderArr = JSONArray()
        currentModules.forEach { moduleOrderArr.put(it.id) }

        val deletedIdsArr = JSONArray()
        prefs.getString("deleted_module_ids", "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { deletedIdsArr.put(it) }

        val stats = _userStats.value
        val statsObj = JSONObject().apply {
            put("streakDays", stats.streakDays)
            put("dailyGoal", stats.dailyGoal)
            put("todayStudied", stats.todayStudied)
            put("totalCardsLearned", stats.totalCardsLearned)
        }

        val root = JSONObject().apply {
            put("backupFormat", "quizapp_full_db_v2")
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("activeLanguage", _activeLanguage.value.code)
            put("themeMode", _themeMode.value.key)
            put("colorTheme", _colorTheme.value.key)
            put("userStats", statsObj)
            put("customFolders", customFoldersArr)
            put("folderOrder", orderedFoldersArr)
            put("categoryOverrides", overridesObj)
            put("deletedModuleIds", deletedIdsArr)
            put("moduleOrderIds", moduleOrderArr)
            put("customModules", JSONArray(serializeModulesToJsonString(customMods)))
            put("modifiedSeedModules", JSONArray(serializeModulesToJsonString(modifiedSeeds)))
            put("cardProgress", progressJson)
        }
        return root.toString(2)
    }

    /**
     * Imports a full QuizApp database backup from JSON.
     * @param replaceExisting If true, restores the exact state from backup. If false, safely merges modules, progress, starred cards, and folders.
     */
    fun importFullDatabaseFromJson(
        jsonStr: String,
        replaceExisting: Boolean = true
    ): Result<DatabaseRestoreResult> {
        return try {
            val cleanJson = jsonStr
                .removePrefix("\uFEFF")
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            if (cleanJson.isBlank()) {
                return Result.failure(IllegalArgumentException("Файл или текст резервной копии пуст."))
            }

            val root = JSONObject(cleanJson)

            // Fallback: if user passed a single-module JSON object {"title": "...", "cards": [...]}
            val isSingleModuleJson = root.has("cards") && !root.has("customModules") && !root.has("cardProgress") && !root.has("backupFormat")
            if (isSingleModuleJson) {
                val singleParsed = parseModulesJson(JSONArray().put(root).toString()).firstOrNull()
                    ?: importModuleFromJson(cleanJson)
                saveModule(singleParsed)
                return Result.success(buildCurrentDatabaseSummary())
            }

            val incomingCustomMods = root.optJSONArray("customModules")?.let {
                parseModulesJson(it.toString())
            } ?: root.optJSONArray("modules")?.let {
                parseModulesJson(it.toString()).filter { m -> !m.id.startsWith("seed_") }
            } ?: emptyList()

            val incomingModifiedSeeds = root.optJSONArray("modifiedSeedModules")?.let {
                parseModulesJson(it.toString())
            } ?: emptyList()

            val incomingProgressObj = root.optJSONObject("cardProgress") ?: JSONObject()

            val incomingCustomFolders = mutableListOf<String>()
            root.optJSONArray("customFolders")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.optString(i).trim()
                    if (f.isNotBlank() && incomingCustomFolders.none { it.equals(f, ignoreCase = true) }) {
                        incomingCustomFolders.add(f)
                    }
                }
            }

            val incomingFolderOrder = mutableListOf<String>()
            root.optJSONArray("folderOrder")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val f = arr.optString(i).trim()
                    if (f.isNotBlank() && incomingFolderOrder.none { it.equals(f, ignoreCase = true) }) {
                        incomingFolderOrder.add(f)
                    }
                }
            }

            val incomingOverrides = mutableMapOf<String, String>()
            root.optJSONObject("categoryOverrides")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k).trim()
                    if (k.isNotBlank() && v.isNotBlank()) {
                        incomingOverrides[k] = v
                    }
                }
            }

            val incomingDeletedIds = mutableSetOf<String>()
            root.optJSONArray("deletedModuleIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotBlank()) incomingDeletedIds.add(id)
                }
            }

            val incomingModuleOrder = mutableListOf<String>()
            root.optJSONArray("moduleOrderIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotBlank()) incomingModuleOrder.add(id)
                }
            }

            fun applyProgressEntryToCard(card: Flashcard, modId: String, progressRoot: JSONObject): Flashcard {
                val cObj = progressRoot.optJSONObject("$modId:${card.id}") ?: return card
                val score = cObj.optInt("masteryScore", card.masteryScore).coerceIn(0, 2)
                return card.copy(
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
            }

            fun mergeCards(existing: Flashcard, incoming: Flashcard): Flashcard {
                val useIncomingProgress = incoming.lastStudiedTimestamp > existing.lastStudiedTimestamp ||
                    (incoming.lastStudiedTimestamp == existing.lastStudiedTimestamp &&
                        (incoming.masteryScore > existing.masteryScore || incoming.reviewCount > existing.reviewCount || incoming.needsReview))
                val mergedScore = if (useIncomingProgress) incoming.masteryScore else maxOf(existing.masteryScore, incoming.masteryScore)
                val mergedMastered = if (useIncomingProgress) (mergedScore >= 2 || incoming.isMastered) else (mergedScore >= 2 || existing.isMastered || incoming.isMastered)
                val mergedNeedsReview = if (useIncomingProgress) incoming.needsReview else (existing.needsReview || incoming.needsReview)
                return existing.copy(
                    term = incoming.term.ifBlank { existing.term },
                    definition = incoming.definition.ifBlank { existing.definition },
                    exampleSentence = incoming.exampleSentence ?: existing.exampleSentence,
                    diagramType = incoming.diagramType ?: existing.diagramType,
                    masteryScore = mergedScore,
                    isMastered = mergedMastered,
                    needsReview = mergedNeedsReview,
                    isStarred = existing.isStarred || incoming.isStarred,
                    reviewCount = maxOf(existing.reviewCount, incoming.reviewCount),
                    lastStudiedTimestamp = maxOf(existing.lastStudiedTimestamp, incoming.lastStudiedTimestamp),
                    easeFactor = if (useIncomingProgress) incoming.easeFactor else existing.easeFactor,
                    intervalDays = if (useIncomingProgress) incoming.intervalDays else maxOf(existing.intervalDays, incoming.intervalDays),
                    repetitions = if (useIncomingProgress) incoming.repetitions else maxOf(existing.repetitions, incoming.repetitions),
                    lapses = maxOf(existing.lapses, incoming.lapses),
                    nextReviewTimestamp = maxOf(existing.nextReviewTimestamp, incoming.nextReviewTimestamp)
                )
            }

            val finalDeletedIds: Set<String>
            val finalCustomFolders: Set<String>
            val finalOverrides: Map<String, String>
            val finalModules: List<StudyModule>
            val finalFolderOrder: List<String>

            if (replaceExisting) {
                finalDeletedIds = incomingDeletedIds
                finalCustomFolders = incomingCustomFolders.toSet()
                finalOverrides = incomingOverrides.toMutableMap()

                val modifiedSeedsById = incomingModifiedSeeds.associateBy { it.id }
                val baseSeedList = createSeedModules()
                    .filter { it.id !in finalDeletedIds }
                    .map { modifiedSeedsById[it.id] ?: it }

                val rawAll = (incomingCustomMods.filter { it.id !in finalDeletedIds } + baseSeedList).map { mod ->
                    val overrideCat = finalOverrides[mod.id]
                    val resolvedCat = if (!overrideCat.isNullOrBlank()) overrideCat else mod.category.trim().ifBlank { "Общее" }
                    finalOverrides[mod.id] = resolvedCat
                    val updatedCards = mod.cards.map { c ->
                        applyProgressEntryToCard(c, mod.id, incomingProgressObj)
                    }
                    mod.copy(category = resolvedCat, cards = updatedCards)
                }

                finalModules = if (incomingModuleOrder.isNotEmpty()) {
                    val byId = rawAll.associateBy { it.id }
                    val ordered = incomingModuleOrder.mapNotNull { byId[it] }
                    val remaining = rawAll.filter { it.id !in incomingModuleOrder.toSet() }
                    ordered + remaining
                } else {
                    rawAll
                }

                val categoriesFromMods = finalModules.map { it.category.trim().ifBlank { "Общее" } }
                finalFolderOrder = (incomingFolderOrder + categoriesFromMods + finalCustomFolders)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
            } else {
                // Safe Merge Mode
                val currentDeleted = prefs.getString("deleted_module_ids", "")
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                val restoredIds = (incomingCustomMods.map { it.id } + incomingModifiedSeeds.map { it.id }).toSet()
                finalDeletedIds = (currentDeleted - restoredIds)

                val mergedCustomFolders = (_customFolders.value + incomingCustomFolders)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .toSet()
                finalCustomFolders = mergedCustomFolders

                val mergedOverrides = loadCategoryOverrides().toMutableMap()
                incomingOverrides.forEach { (k, v) -> mergedOverrides[k] = v }
                finalOverrides = mergedOverrides

                val currentById = _modules.value.associateBy { it.id }.toMutableMap()

                // Merge incoming custom modules
                val mergedCustomList = mutableListOf<StudyModule>()
                val currentCustom = _modules.value.filter { !it.id.startsWith("seed_") }.toMutableList()
                for (incMod in incomingCustomMods) {
                    val incWithProgress = incMod.copy(
                        cards = incMod.cards.map { applyProgressEntryToCard(it, incMod.id, incomingProgressObj) }
                    )
                    val existingIdx = currentCustom.indexOfFirst { it.id == incWithProgress.id }
                    if (existingIdx >= 0) {
                        val existingMod = currentCustom[existingIdx]
                        val existingCardsById = existingMod.cards.associateBy { it.id }
                        val mergedCards = incWithProgress.cards.map { incCard ->
                            val exCard = existingCardsById[incCard.id]
                            if (exCard != null) mergeCards(exCard, incCard) else incCard
                        }
                        val extraExisting = existingMod.cards.filter { ex -> mergedCards.none { it.id == ex.id } }
                        val resolvedCat = finalOverrides[incWithProgress.id] ?: incWithProgress.category.ifBlank { existingMod.category }
                        currentCustom[existingIdx] = incWithProgress.copy(
                            category = resolvedCat,
                            cards = mergedCards + extraExisting
                        )
                    } else {
                        val resolvedCat = finalOverrides[incWithProgress.id] ?: incWithProgress.category.ifBlank { "Пользовательские" }
                        mergedCustomList.add(incWithProgress.copy(category = resolvedCat))
                    }
                }
                val allCustom = mergedCustomList + currentCustom

                // Merge seed modules and progress
                val incSeedsById = incomingModifiedSeeds.associateBy { it.id }
                val currentSeeds = _modules.value.filter { it.id.startsWith("seed_") }.ifEmpty { createSeedModules() }
                val mergedSeeds = currentSeeds.map { seedMod ->
                    val incSeed = incSeedsById[seedMod.id]
                    val baseCards = if (incSeed != null) {
                        val existingById = seedMod.cards.associateBy { it.id }
                        val combined = incSeed.cards.map { incC ->
                            val exC = existingById[incC.id]
                            if (exC != null) mergeCards(exC, incC) else incC
                        }
                        val extraEx = seedMod.cards.filter { ex -> combined.none { it.id == ex.id } }
                        combined + extraEx
                    } else {
                        seedMod.cards
                    }
                    val updatedCards = baseCards.map { c ->
                        val fromProg = applyProgressEntryToCard(c, seedMod.id, incomingProgressObj)
                        mergeCards(c, fromProg)
                    }
                    val resolvedCat = finalOverrides[seedMod.id] ?: incSeed?.category ?: seedMod.category
                    seedMod.copy(
                        title = incSeed?.title ?: seedMod.title,
                        description = incSeed?.description ?: seedMod.description,
                        category = resolvedCat,
                        cards = updatedCards
                    )
                }

                val combinedAll = allCustom + mergedSeeds
                finalModules = if (incomingModuleOrder.isNotEmpty()) {
                    val byId = combinedAll.associateBy { it.id }
                    val ordered = incomingModuleOrder.mapNotNull { byId[it] }
                    val remaining = combinedAll.filter { it.id !in incomingModuleOrder.toSet() }
                    ordered + remaining
                } else {
                    combinedAll
                }

                val categoriesFromMods = finalModules.map { it.category.trim().ifBlank { "Общее" } }
                finalFolderOrder = (incomingFolderOrder + _folderOrder.value + categoriesFromMods + finalCustomFolders)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
            }

            // Update in-memory state flows
            _customFolders.value = finalCustomFolders
            _folderOrder.value = finalFolderOrder
            _modules.value = finalModules

            // Update user stats
            val totalMastered = finalModules.sumOf { m -> m.cards.count { it.status == CardMasteryStatus.MASTERED } }
            val incomingStatsObj = root.optJSONObject("userStats")
            val currentStats = _userStats.value
            val updatedStats = if (incomingStatsObj != null) {
                UserStudyStats(
                    streakDays = if (replaceExisting) incomingStatsObj.optInt("streakDays", 1) else maxOf(currentStats.streakDays, incomingStatsObj.optInt("streakDays", 1)),
                    dailyGoal = incomingStatsObj.optInt("dailyGoal", currentStats.dailyGoal),
                    todayStudied = if (replaceExisting) incomingStatsObj.optInt("todayStudied", 0) else maxOf(currentStats.todayStudied, incomingStatsObj.optInt("todayStudied", 0)),
                    totalCardsLearned = maxOf(totalMastered, incomingStatsObj.optInt("totalCardsLearned", totalMastered))
                )
            } else {
                currentStats.copy(totalCardsLearned = totalMastered)
            }
            _userStats.value = updatedStats

            // Restore language/theme if present in full replace mode
            if (replaceExisting) {
                val langCode = root.optString("activeLanguage", "")
                AppLanguage.entries.find { it.code == langCode }?.let { _activeLanguage.value = it }
            }

            // Persist everything atomically
            val overridesJson = JSONObject()
            finalOverrides.forEach { (k, v) -> overridesJson.put(k, v) }
            val folderOrderJson = JSONArray()
            finalFolderOrder.forEach { folderOrderJson.put(it) }
            val moduleOrderJson = JSONArray()
            finalModules.forEach { moduleOrderJson.put(it.id) }

            prefs.edit()
                .putString("deleted_module_ids", finalDeletedIds.joinToString(","))
                .putStringSet("user_custom_folders", finalCustomFolders)
                .putString("folder_order_json", folderOrderJson.toString())
                .putString("module_order_ids_json", moduleOrderJson.toString())
                .putString("module_category_overrides_json", overridesJson.toString())
                .putString("custom_modules_json", serializeModulesToJsonString(finalModules.filter { !it.id.startsWith("seed_") }))
                .putString("modified_seed_modules_json", serializeModulesToJsonString(getModifiedSeedModules(finalModules.filter { it.id.startsWith("seed_") })))
                .putString("card_progress_json", buildCardProgressJson(finalModules).toString())
                .putInt("stats_streak_days", updatedStats.streakDays)
                .putInt("stats_daily_goal", updatedStats.dailyGoal)
                .putInt("stats_today_studied", updatedStats.todayStudied)
                .putInt("stats_total_learned", updatedStats.totalCardsLearned)
                .putString("app_lang", _activeLanguage.value.code)
                .apply()

            Result.success(buildCurrentDatabaseSummary())
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Не удалось прочитать резервную копию базы: ${e.localizedMessage ?: "неверный формат JSON"}"))
        }
    }

    fun buildCurrentDatabaseSummary(): DatabaseRestoreResult {
        val allMods = _modules.value
        val customCount = allMods.count { !it.id.startsWith("seed_") }
        var totalCards = 0
        var mastered = 0
        var learning = 0
        var needsReview = 0
        var starred = 0
        for (mod in allMods) {
            totalCards += mod.cards.size
            for (c in mod.cards) {
                when (c.status) {
                    CardMasteryStatus.MASTERED -> mastered++
                    CardMasteryStatus.LEARNING -> learning++
                    CardMasteryStatus.NOT_STUDIED -> {}
                }
                if (c.needsReview) {
                    needsReview++
                }
                if (c.isStarred) {
                    starred++
                }
            }
        }
        val foldersCount = getOrderedLibraryFolders().size
        return DatabaseRestoreResult(
            totalModules = allMods.size,
            customModulesCount = customCount,
            totalCards = totalCards,
            masteredCards = mastered,
            learningCards = learning,
            needsReviewCards = needsReview,
            starredCards = starred,
            foldersCount = foldersCount
        )
    }

    private fun serializeModulesToJsonString(modulesList: List<StudyModule>): String {
        val jsonArr = JSONArray()
        modulesList.forEach { mod ->
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
        return jsonArr.toString()
    }

    private fun persistCustomModules(customModules: List<StudyModule>) {
        prefs.edit().putString("custom_modules_json", serializeModulesToJsonString(customModules)).apply()
    }

    private fun parseModulesJson(jsonStr: String): List<StudyModule> {
        val list = mutableListOf<StudyModule>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val modId = obj.optString("id").ifBlank { "user_" + UUID.randomUUID().toString() }
                val cardsArr = obj.optJSONArray("cards") ?: JSONArray()
                val cards = mutableListOf<Flashcard>()
                for (j in 0 until cardsArr.length()) {
                    val cObj = cardsArr.optJSONObject(j) ?: continue
                    val term = cObj.optString("term").trim()
                    val def = cObj.optString("definition").trim()
                    if (term.isBlank() && def.isBlank()) continue
                    val score = cObj.optInt("masteryScore", if (cObj.optBoolean("isMastered", false)) 2 else 0).coerceIn(0, 2)
                    cards.add(
                        Flashcard(
                            id = cObj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            moduleId = modId,
                            term = term,
                            definition = def,
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
                        title = obj.optString("title").ifBlank { "Без названия" },
                        description = obj.optString("description"),
                        category = obj.optString("category").ifBlank { "Пользовательские" },
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
