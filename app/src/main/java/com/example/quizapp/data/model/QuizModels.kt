package com.example.quizapp.data.model

enum class AppLanguage(val displayName: String, val code: String, val flagEmoji: String) {
    RU("Русский", "ru", "🇷🇺"),
    EN("English", "en", "🇬🇧"),
    ES("Español", "es", "🇪🇸")
}

enum class AppThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark")
}

enum class AppColorTheme(
    val key: String,
    val titleRu: String,
    val titleEn: String,
    val titleEs: String,
    val previewHex: Long
) {
    SYSTEM("system", "Динамический (Система)", "Dynamic (System)", "Dinámico (Sistema)", 0xFF4F46E5),
    INDIGO("indigo", "Индиго", "Indigo", "Índigo", 0xFF4355B9),
    OCEAN("ocean", "Океан", "Ocean", "Océano", 0xFF006493),
    EMERALD("emerald", "Изумруд", "Emerald", "Esmeralda", 0xFF006C4C),
    VIOLET("violet", "Аметист", "Violet", "Violeta", 0xFF6750A4),
    ROSE("rose", "Рубин", "Rose", "Rubí", 0xFF9C4146);

    val seedColorHex: Long
        get() = previewHex

    fun label(lang: AppLanguage): String = when (lang) {
        AppLanguage.RU -> titleRu
        AppLanguage.EN -> titleEn
        AppLanguage.ES -> titleEs
    }
}

enum class CardMasteryStatus {
    NOT_STUDIED, // Не изучено / Требует повторения
    LEARNING,    // Изучено (на этапе закрепления, 1 правильный сеанс)
    MASTERED     // Усвоено (2+ успешных сеанса)
}

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>
) {
    GOOGLE_GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-3.8-flash",
        availableModels = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-pro",
            "gemini-1.5-flash"
        )
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-mini",
            "gpt-4.1",
            "o3-mini",
            "gpt-3.5-turbo"
        )
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-5-sonnet-20241022",
        availableModels = listOf(
            "claude-3-5-sonnet-20241022",
            "claude-3-7-sonnet-20250219",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229"
        )
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultModel = "deepseek-chat",
        availableModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultModel = "google/gemini-3.8-flash",
        availableModels = listOf(
            "google/gemini-3.8-flash",
            "google/gemini-3.5-flash-lite",
            "google/gemini-2.5-flash",
            "openai/gpt-4o-mini",
            "anthropic/claude-3.5-sonnet",
            "deepseek/deepseek-chat",
            "meta-llama/llama-3.3-70b-instruct"
        )
    )
}

data class AiProfile(
    val id: String,
    val orderNumber: Int,
    val name: String,
    val provider: AiProvider,
    val apiKey: String = "",
    val modelId: String = provider.defaultModel
)

enum class LearnStudyGoal {
    EXAM_PREP,    // Хорошо подготовиться к экзамену (интенсивное адаптивное повторение + тест и письмо)
    MEMORIZE_ALL  // Запомнить весь материал (глубокое интервальное усвоение по алгоритму Anki SM-2)
}

enum class AnkiGrade(val quality: Int) {
    AGAIN(0), // Снова (< 1 мин, повтор в текущем и следующем блоке)
    HARD(3),  // Трудно
    GOOD(4),  // Хорошо
    EASY(5)   // Легко
}

data class Flashcard(
    val id: String,
    val moduleId: String,
    val term: String,
    val definition: String,
    val diagramType: String? = null, // "cone", "heart", "dna", "wind"
    val exampleSentence: String? = null,
    val isMastered: Boolean = false,
    val masteryScore: Int = if (isMastered) 2 else 0, // 0 = Не изучено, 1 = Изучено, 2+ = Усвоено
    val reviewCount: Int = 0,
    val needsReview: Boolean = false,
    val isStarred: Boolean = false,
    val lastStudiedTimestamp: Long = 0L,
    // Anki SM-2 Spaced Repetition parameters
    val easeFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val nextReviewTimestamp: Long = 0L
) {
    val status: CardMasteryStatus
        get() = when {
            masteryScore >= 2 || isMastered -> CardMasteryStatus.MASTERED
            masteryScore == 1 -> CardMasteryStatus.LEARNING
            else -> CardMasteryStatus.NOT_STUDIED
        }
}

data class StudyModule(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val author: String = "QuizApp",
    val isVerified: Boolean = true,
    val colorHex: Long = 0xFF4355B9,
    val cards: List<Flashcard>,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastStudiedTimestamp: Long = 0L,
    val isBookmarked: Boolean = false
) {
    val notStudiedCount: Int = cards.count { it.status == CardMasteryStatus.NOT_STUDIED }
    val learningCount: Int = cards.count { it.status == CardMasteryStatus.LEARNING }
    val masteredCount: Int = cards.count { it.status == CardMasteryStatus.MASTERED }
    val needsReviewCount: Int = cards.count { it.needsReview || it.status == CardMasteryStatus.LEARNING }
    val completionPercent: Int = if (cards.isEmpty()) 0 else {
        val totalPoints = cards.size * 2
        val earnedPoints = cards.sumOf {
            when (it.status) {
                CardMasteryStatus.MASTERED -> 2
                CardMasteryStatus.LEARNING -> 1
                CardMasteryStatus.NOT_STUDIED -> 0
            }
        }
        ((earnedPoints * 100) / totalPoints).coerceIn(0, 100)
    }
}

data class UserStudyStats(
    val streakDays: Int = 1,
    val dailyGoal: Int = 20,
    val todayStudied: Int = 0,
    val totalCardsLearned: Int = 0
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isGenerating: Boolean = false,
    val suggestedCards: List<Pair<String, String>> = emptyList(),
    val createdModuleId: String? = null,
    val createdModuleTitle: String? = null
)

data class MagicNotesResult(
    val title: String,
    val summary: String,
    val bullets: List<String>,
    val cards: List<Flashcard>
)
