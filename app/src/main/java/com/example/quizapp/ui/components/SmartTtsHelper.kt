package com.example.quizapp.ui.components

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.StudyModule
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SpeechSegment(
    val text: String,
    val locale: Locale
)

data class TtsLanguageOption(
    val languageTag: String,
    val locale: Locale,
    val displayNameRu: String,
    val nativeName: String,
    val shortCode: String,
    val isAvailableOnDevice: Boolean = true
)

/**
 * Deterministic, alphabet/letter-based TextToSpeech helper:
 * - Detects language strictly from the letters/script present in each word/segment.
 * - Pure basic Latin (`a-z`, `A-Z`) is ALWAYS spoken in English (`en-US`).
 * - Unique non-English letters/scripts (Russian Cyrillic, Kazakh Cyrillic, Arabic, Spanish `ñ/¡/¿`,
 *   German `ß/ä/ö/ü`, French `œ/è/ê/î/ù`, Portuguese `ã/õ`, Turkish `ğ/ı/ş`, CJK/Kana/Hangul, etc.)
 *   map directly to their corresponding language.
 * - If the detected (or requested) language is not available in the Android TTS engine, falls back to English (`en-US`).
 * - Supports explicit language override via `speakWithLocale` for long-press language picker selection.
 */
object SmartTtsHelper {

    val russianLocale: Locale = Locale.forLanguageTag("ru-RU")
    val englishLocale: Locale = Locale.US
    val spanishLocale: Locale = Locale.forLanguageTag("es-ES")
    val arabicLocale: Locale = Locale.forLanguageTag("ar-SA")
    val portugueseLocale: Locale = Locale.forLanguageTag("pt-BR")
    val frenchLocale: Locale = Locale.forLanguageTag("fr-FR")
    val germanLocale: Locale = Locale.forLanguageTag("de-DE")
    val kazakhLocale: Locale = Locale.forLanguageTag("kk-KZ")
    val ukrainianLocale: Locale = Locale.forLanguageTag("uk-UA")
    val italianLocale: Locale = Locale.forLanguageTag("it-IT")
    val turkishLocale: Locale = Locale.forLanguageTag("tr-TR")
    val polishLocale: Locale = Locale.forLanguageTag("pl-PL")
    val chineseLocale: Locale = Locale.forLanguageTag("zh-CN")
    val japaneseLocale: Locale = Locale.forLanguageTag("ja-JP")
    val koreanLocale: Locale = Locale.forLanguageTag("ko-KR")
    val hebrewLocale: Locale = Locale.forLanguageTag("he-IL")
    val hindiLocale: Locale = Locale.forLanguageTag("hi-IN")

    val supportedTtsLanguages: List<TtsLanguageOption> = listOf(
        TtsLanguageOption("en-US", Locale.US, "Английский (США)", "English (US)", "EN"),
        TtsLanguageOption("en-GB", Locale.UK, "Английский (Великобритания)", "English (UK)", "EN"),
        TtsLanguageOption("ru-RU", russianLocale, "Русский", "Русский", "RU"),
        TtsLanguageOption("es-ES", spanishLocale, "Испанский (Испания)", "Español (España)", "ES"),
        TtsLanguageOption("es-MX", Locale.forLanguageTag("es-MX"), "Испанский (Мексика)", "Español (México)", "ES"),
        TtsLanguageOption("kk-KZ", kazakhLocale, "Казахский", "Қазақша", "KK"),
        TtsLanguageOption("ar-SA", arabicLocale, "Арабский", "العربية", "AR"),
        TtsLanguageOption("pt-BR", portugueseLocale, "Португальский (Бразилия)", "Português (Brasil)", "PT"),
        TtsLanguageOption("pt-PT", Locale.forLanguageTag("pt-PT"), "Португальский (Португалия)", "Português (Portugal)", "PT"),
        TtsLanguageOption("fr-FR", frenchLocale, "Французский", "Français", "FR"),
        TtsLanguageOption("de-DE", germanLocale, "Немецкий", "Deutsch", "DE"),
        TtsLanguageOption("it-IT", italianLocale, "Итальянский", "Italiano", "IT"),
        TtsLanguageOption("tr-TR", turkishLocale, "Турецкий", "Türkçe", "TR"),
        TtsLanguageOption("uk-UA", ukrainianLocale, "Украинский", "Українська", "UK"),
        TtsLanguageOption("pl-PL", polishLocale, "Польский", "Polski", "PL"),
        TtsLanguageOption("zh-CN", chineseLocale, "Китайский", "中文", "ZH"),
        TtsLanguageOption("ja-JP", japaneseLocale, "Японский", "日本語", "JA"),
        TtsLanguageOption("ko-KR", koreanLocale, "Корейский", "한국어", "KO"),
        TtsLanguageOption("hi-IN", hindiLocale, "Хинди", "हिन्दी", "HI"),
        TtsLanguageOption("he-IL", hebrewLocale, "Иврит", "עברית", "HE")
    )

    fun findLanguageOption(tag: String): TtsLanguageOption? {
        val normalized = tag.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null
        supportedTtsLanguages.firstOrNull { it.languageTag.lowercase(Locale.ROOT) == normalized }?.let { return it }
        val baseLang = normalized.substringBefore('-')
        supportedTtsLanguages.firstOrNull { it.locale.language.lowercase(Locale.ROOT) == baseLang }?.let { return it }

        val loc = Locale.forLanguageTag(tag)
        if (loc.language.isNullOrBlank()) return null
        val displayRu = loc.getDisplayName(Locale("ru")).replaceFirstChar { it.uppercaseChar() }
        val native = loc.getDisplayName(loc).replaceFirstChar { it.uppercaseChar() }
        return TtsLanguageOption(
            languageTag = loc.toLanguageTag(),
            locale = loc,
            displayNameRu = displayRu,
            nativeName = native,
            shortCode = loc.language.uppercase(Locale.ROOT).take(3)
        )
    }

    fun discoverAllTtsLanguages(tts: TextToSpeech?): List<TtsLanguageOption> {
        val seenTags = linkedSetOf<String>()
        val result = mutableListOf<TtsLanguageOption>()

        for (preset in supportedTtsLanguages) {
            val key = preset.languageTag.lowercase(Locale.ROOT)
            if (seenTags.add(key)) {
                val available = if (tts != null) isLocalePlayableOnEngine(tts, preset.locale) else true
                result.add(preset.copy(isAvailableOnDevice = available))
            }
        }

        val engineVoices = runCatching { tts?.voices }.getOrNull().orEmpty()
        for (voice in engineVoices) {
            val vLocale = voice.locale ?: continue
            val isNotInstalled = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) == true ||
                voice.features.orEmpty().any { it.contains("notInstalled", ignoreCase = true) }
            if (isNotInstalled) continue

            val tag = vLocale.toLanguageTag()
            val key = tag.lowercase(Locale.ROOT)
            val langOnlyMatch = result.any {
                it.languageTag.equals(tag, ignoreCase = true) ||
                    (it.locale.language.equals(vLocale.language, ignoreCase = true) &&
                        it.locale.country.equals(vLocale.country, ignoreCase = true))
            }
            if (!langOnlyMatch && seenTags.add(key)) {
                val ruName = vLocale.getDisplayName(Locale("ru")).replaceFirstChar { it.uppercaseChar() }
                val nativeName = vLocale.getDisplayName(vLocale).replaceFirstChar { it.uppercaseChar() }
                result.add(
                    TtsLanguageOption(
                        languageTag = tag,
                        locale = vLocale,
                        displayNameRu = ruName,
                        nativeName = nativeName,
                        shortCode = vLocale.language.uppercase(Locale.ROOT).take(3),
                        isAvailableOnDevice = true
                    )
                )
            }
        }

        return result
    }

    private val candidateLocalesByLang: Map<String, List<Locale>> = mapOf(
        "ru" to listOf(Locale.forLanguageTag("ru-RU"), Locale.forLanguageTag("ru")),
        "en" to listOf(Locale.US, Locale.UK, Locale.forLanguageTag("en-AU"), Locale.forLanguageTag("en")),
        "es" to listOf(
            Locale.forLanguageTag("es-ES"),
            Locale.forLanguageTag("es-MX"),
            Locale.forLanguageTag("es-US"),
            Locale.forLanguageTag("es-419"),
            Locale.forLanguageTag("es")
        ),
        "ar" to listOf(
            Locale.forLanguageTag("ar-SA"),
            Locale.forLanguageTag("ar-EG"),
            Locale.forLanguageTag("ar-AE"),
            Locale.forLanguageTag("ar-001"),
            Locale.forLanguageTag("ar")
        ),
        "pt" to listOf(Locale.forLanguageTag("pt-BR"), Locale.forLanguageTag("pt-PT"), Locale.forLanguageTag("pt")),
        "fr" to listOf(Locale.forLanguageTag("fr-FR"), Locale.forLanguageTag("fr-CA"), Locale.forLanguageTag("fr")),
        "de" to listOf(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("de-AT"), Locale.forLanguageTag("de")),
        "kk" to listOf(Locale.forLanguageTag("kk-KZ"), Locale.forLanguageTag("kk")),
        "uk" to listOf(Locale.forLanguageTag("uk-UA"), Locale.forLanguageTag("uk")),
        "it" to listOf(Locale.forLanguageTag("it-IT"), Locale.forLanguageTag("it")),
        "tr" to listOf(Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("tr")),
        "pl" to listOf(Locale.forLanguageTag("pl-PL"), Locale.forLanguageTag("pl")),
        "zh" to listOf(Locale.forLanguageTag("zh-CN"), Locale.forLanguageTag("zh-TW"), Locale.forLanguageTag("zh")),
        "ja" to listOf(Locale.forLanguageTag("ja-JP"), Locale.forLanguageTag("ja")),
        "ko" to listOf(Locale.forLanguageTag("ko-KR"), Locale.forLanguageTag("ko")),
        "he" to listOf(Locale.forLanguageTag("he-IL"), Locale.forLanguageTag("iw-IL"), Locale.forLanguageTag("he")),
        "hi" to listOf(Locale.forLanguageTag("hi-IN"), Locale.forLanguageTag("hi"))
    )

    // Unique letters outside basic ASCII Latin / Russian Cyrillic
    private val kazakhSpecificChars = setOf(
        'ә', 'Ә', 'і', 'І', 'ң', 'Ң', 'ғ', 'Ғ',
        'ү', 'Ү', 'ұ', 'Ұ', 'қ', 'Қ', 'ө', 'Ө', 'һ', 'Һ'
    )
    private val ukrainianSpecificChars = setOf('є', 'Є', 'ї', 'Ї', 'ґ', 'Ґ')

    private val spanishSpecificChars = setOf('ñ', 'Ñ', '¡', '¿')
    private val spanishAccentedVowels = setOf('á', 'é', 'í', 'ó', 'ú', 'Á', 'É', 'Í', 'Ó', 'Ú')
    private val portugueseSpecificChars = setOf('ã', 'õ', 'Ã', 'Õ')
    private val frenchSpecificChars = setOf(
        'œ', 'Œ', 'æ', 'Æ', 'è', 'È', 'ê', 'Ê', 'ë', 'Ë', 'î', 'Î',
        'ï', 'Ï', 'ù', 'Ù', 'û', 'Û', 'ÿ', 'Ÿ', 'à', 'À', 'â', 'Â'
    )
    private val germanSpecificChars = setOf('ß', 'ẞ', 'ä', 'Ä', 'ö', 'Ö', 'ü', 'Ü')
    private val turkishSpecificChars = setOf('ğ', 'Ğ', 'ı', 'İ', 'ş', 'Ş')
    private val polishSpecificChars = setOf('ą', 'ć', 'ę', 'ł', 'ń', 'ś', 'ź', 'ż', 'Ą', 'Ć', 'Ę', 'Ł', 'Ń', 'Ś', 'Ź', 'Ż')

    /**
     * Strictly detects language based ONLY on the letters/script present in [text].
     * Basic Latin (`a-z`, `A-Z`) without unique non-English letters ALWAYS resolves to English (`en-US`).
     * The [fallbackLatinLocale] parameter is retained only for binary compatibility and ignored for Latin `a-z`.
     */
    @Suppress("UNUSED_PARAMETER")
    fun detectLocaleForText(text: String, fallbackLatinLocale: Locale? = null): Locale {
        var arabicCount = 0
        var cyrillicCount = 0
        var kazakhCount = 0
        var ukrainianCount = 0
        var latinCount = 0
        var hebrewCount = 0
        var devanagariCount = 0
        var hangulCount = 0
        var kanaCount = 0
        var cjkCount = 0

        var spanishExclusiveCount = 0
        var spanishAccentCount = 0
        var portugueseExclusiveCount = 0
        var frenchExclusiveCount = 0
        var germanExclusiveCount = 0
        var turkishExclusiveCount = 0
        var polishExclusiveCount = 0
        var cCedillaCount = 0
        var oCircumflexCount = 0

        for (ch in text) {
            when {
                ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\u08A0'..'\u08FF' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF' -> arabicCount++
                ch in kazakhSpecificChars -> {
                    kazakhCount++
                    cyrillicCount++
                }
                ch in ukrainianSpecificChars -> {
                    ukrainianCount++
                    cyrillicCount++
                }
                ch in '\u0400'..'\u04FF' -> cyrillicCount++
                ch in '\u0590'..'\u05FF' -> hebrewCount++
                ch in '\u0900'..'\u097F' -> devanagariCount++
                ch in '\uAC00'..'\uD7AF' || ch in '\u1100'..'\u11FF' || ch in '\u3130'..'\u318F' -> hangulCount++
                ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF' -> kanaCount++
                ch in '\u4E00'..'\u9FFF' -> cjkCount++
                ch in spanishSpecificChars -> {
                    spanishExclusiveCount++
                    latinCount++
                }
                ch in portugueseSpecificChars -> {
                    portugueseExclusiveCount++
                    latinCount++
                }
                ch in turkishSpecificChars -> {
                    turkishExclusiveCount++
                    latinCount++
                }
                ch in polishSpecificChars -> {
                    polishExclusiveCount++
                    latinCount++
                }
                ch in frenchSpecificChars -> {
                    frenchExclusiveCount++
                    latinCount++
                }
                ch in germanSpecificChars -> {
                    germanExclusiveCount++
                    latinCount++
                }
                ch == 'ç' || ch == 'Ç' -> {
                    cCedillaCount++
                    latinCount++
                }
                ch == 'ô' || ch == 'Ô' -> {
                    oCircumflexCount++
                    latinCount++
                }
                ch in spanishAccentedVowels -> {
                    spanishAccentCount++
                    latinCount++
                }
                ch.isLetter() -> latinCount++
            }
        }

        // 1. Unique non-Latin, non-Cyrillic alphabets
        if (arabicCount > 0 && arabicCount >= cyrillicCount && arabicCount >= latinCount) return arabicLocale
        if (hebrewCount > 0 && hebrewCount >= latinCount && hebrewCount >= cyrillicCount) return hebrewLocale
        if (devanagariCount > 0 && devanagariCount >= latinCount && devanagariCount >= cyrillicCount) return hindiLocale
        if (hangulCount > 0) return koreanLocale
        if (kanaCount > 0) return japaneseLocale
        if (cjkCount > 0) return chineseLocale

        // 2. Cyrillic alphabet (Kazakh / Ukrainian only if their unique letters appear; otherwise Russian)
        if (cyrillicCount > 0 && cyrillicCount >= latinCount) {
            return when {
                kazakhCount > 0 -> kazakhLocale
                ukrainianCount > 0 -> ukrainianLocale
                else -> russianLocale
            }
        }

        // 3. Latin alphabet: only pick non-English when unique non-English letters appear
        if (latinCount > 0) {
            if (turkishExclusiveCount > 0) return turkishLocale
            if (polishExclusiveCount > 0) return polishLocale
            if (portugueseExclusiveCount > 0) return portugueseLocale
            if (spanishExclusiveCount > 0) return spanishLocale
            if (germanExclusiveCount > 0 && frenchExclusiveCount == 0 && spanishAccentCount == 0) return germanLocale
            if (frenchExclusiveCount > 0) return frenchLocale
            if (cCedillaCount > 0 || oCircumflexCount > 0) return frenchLocale
            if (spanishAccentCount > 0) return spanishLocale

            // Pure basic Latin (A-Z, a-z): ALWAYS English
            return englishLocale
        }

        return englishLocale
    }

    /**
     * Kept for call-site compatibility; letter-only detection does not override `a-z` with module metadata.
     */
    @Suppress("UNUSED_PARAMETER")
    fun inferFallbackLatinLocale(
        card: Flashcard? = null,
        isTermSide: Boolean = true,
        module: StudyModule? = null
    ): Locale? = null

    private enum class CharScript {
        CYRILLIC_RU, CYRILLIC_KK, CYRILLIC_UK, LATIN, ARABIC, HEBREW, DEVANAGARI, HANGUL, KANA, CJK, NEUTRAL
    }

    private fun scriptOfChar(ch: Char): CharScript = when {
        ch in kazakhSpecificChars -> CharScript.CYRILLIC_KK
        ch in ukrainianSpecificChars -> CharScript.CYRILLIC_UK
        ch in '\u0400'..'\u04FF' -> CharScript.CYRILLIC_RU
        ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\u08A0'..'\u08FF' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF' -> CharScript.ARABIC
        ch in '\u0590'..'\u05FF' -> CharScript.HEBREW
        ch in '\u0900'..'\u097F' -> CharScript.DEVANAGARI
        ch in '\uAC00'..'\uD7AF' || ch in '\u1100'..'\u11FF' || ch in '\u3130'..'\u318F' -> CharScript.HANGUL
        ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF' -> CharScript.KANA
        ch in '\u4E00'..'\u9FFF' -> CharScript.CJK
        ch.isLetter() || ch in spanishSpecificChars -> CharScript.LATIN
        else -> CharScript.NEUTRAL
    }

    private fun normalizeCyrillicGroup(script: CharScript): String = when (script) {
        CharScript.CYRILLIC_RU, CharScript.CYRILLIC_KK, CharScript.CYRILLIC_UK -> "CYR"
        else -> script.name
    }

    private fun dominantScriptGroupOfToken(token: String): String {
        for (ch in token) {
            val s = scriptOfChar(ch)
            if (s != CharScript.NEUTRAL) return normalizeCyrillicGroup(s)
        }
        return CharScript.NEUTRAL.name
    }

    private fun splitChunkByScriptTransitions(chunk: String): List<String> {
        val tokens = chunk.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.size <= 1) return listOf(chunk.trim()).filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        var currentBuilder = StringBuilder()
        var currentGroup = CharScript.NEUTRAL.name

        for (token in tokens) {
            val tokenGroup = dominantScriptGroupOfToken(token)
            if (currentBuilder.isEmpty()) {
                currentBuilder.append(token)
                if (tokenGroup != CharScript.NEUTRAL.name) {
                    currentGroup = tokenGroup
                }
            } else if (tokenGroup != CharScript.NEUTRAL.name &&
                currentGroup != CharScript.NEUTRAL.name &&
                tokenGroup != currentGroup
            ) {
                result.add(currentBuilder.toString().trim())
                currentBuilder = StringBuilder(token)
                currentGroup = tokenGroup
            } else {
                currentBuilder.append(' ').append(token)
                if (currentGroup == CharScript.NEUTRAL.name && tokenGroup != CharScript.NEUTRAL.name) {
                    currentGroup = tokenGroup
                }
            }
        }
        if (currentBuilder.isNotEmpty()) {
            result.add(currentBuilder.toString().trim())
        }
        return result
    }

    /**
     * Splits mixed text across explicit clause delimiters and alphabet transitions so each segment
     * is spoken in the language of its alphabet/letters.
     */
    @Suppress("UNUSED_PARAMETER")
    fun splitIntoLanguageSegments(
        rawText: String,
        fallbackLatinLocale: Locale? = null
    ): List<SpeechSegment> {
        val cleaned = rawText
            .replace("💡", "")
            .replace("Пример:", "")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        val delimiterParts = cleaned
            .split(Regex("""\r?\n|[:;|/]|(?=[(\[])|(?<=[)\]])|\s+[—–-]\s+|[.!?]+\s+|,\s+"""))
            .map { it.trim().trim('(', ')', '[', ']', '.', ',', ';', ':') }
            .filter { it.isNotBlank() && it.any { ch -> ch.isLetterOrDigit() } }

        val atomicParts = mutableListOf<String>()
        for (part in delimiterParts) {
            val scriptSplit = splitChunkByScriptTransitions(part)
            for (sub in scriptSplit) {
                val cleanedSub = sub.trim().trim('(', ')', '[', ']', '.', ',', ';', ':')
                if (cleanedSub.isNotBlank() && cleanedSub.any { ch -> ch.isLetterOrDigit() }) {
                    atomicParts.add(cleanedSub)
                }
            }
        }

        if (atomicParts.isEmpty()) {
            return listOf(SpeechSegment(cleaned, detectLocaleForText(cleaned)))
        }

        val mergedSegments = mutableListOf<SpeechSegment>()
        for (part in atomicParts) {
            val locale = detectLocaleForText(part)
            if (mergedSegments.isNotEmpty() && mergedSegments.last().locale.language == locale.language) {
                val prev = mergedSegments.removeAt(mergedSegments.lastIndex)
                mergedSegments.add(SpeechSegment("${prev.text}. $part", locale))
            } else {
                mergedSegments.add(SpeechSegment(part, locale))
            }
        }
        return mergedSegments
    }

    private data class QueuedSpeechItem(
        val utteranceId: String,
        val segment: SpeechSegment
    )

    private val activeBatchByEngine = ConcurrentHashMap<Int, String>()
    private val pendingSegmentsQueue = ConcurrentHashMap<Int, ArrayDeque<QueuedSpeechItem>>()

    /**
     * Speaks the given text with automatic letter/script-based language selection.
     * Any segment whose language is not available in the Android TTS engine falls back to English (`en-US`).
     */
    @Suppress("UNUSED_PARAMETER")
    fun speakSmart(
        tts: TextToSpeech?,
        text: String,
        fallbackLatinLocale: Locale? = null
    ) {
        if (tts == null || text.isBlank()) return
        val rawSegments = splitIntoLanguageSegments(text)
        if (rawSegments.isEmpty()) return

        val playableSegments = rawSegments.map { seg ->
            seg.copy(locale = resolvePlayableLocaleOrEnglish(tts, seg.locale))
        }
        enqueueAndSpeakSegments(tts, playableSegments)
    }

    /**
     * Speaks the entire [text] using the user-selected [targetLocale].
     * If [targetLocale] is unavailable in the Android TTS engine, falls back to English (`en-US`).
     */
    fun speakWithLocale(
        tts: TextToSpeech?,
        text: String,
        targetLocale: Locale
    ) {
        if (tts == null || text.isBlank()) return
        val cleaned = text
            .replace("💡", "")
            .replace("Пример:", "")
            .trim()
        if (cleaned.isBlank()) return

        val playableLocale = resolvePlayableLocaleOrEnglish(tts, targetLocale)
        enqueueAndSpeakSegments(tts, listOf(SpeechSegment(cleaned, playableLocale)))
    }

    private fun enqueueAndSpeakSegments(tts: TextToSpeech, segments: List<SpeechSegment>) {
        val engineKey = System.identityHashCode(tts)
        val batchId = "batch_${UUID.randomUUID()}"
        activeBatchByEngine[engineKey] = batchId

        val queuedItems = segments.mapIndexed { idx, seg ->
            QueuedSpeechItem(utteranceId = "${batchId}_$idx", segment = seg)
        }
        val queue = ArrayDeque(queuedItems.drop(1))
        pendingSegmentsQueue[engineKey] = queue

        fun playNextInBatch(completedUtteranceId: String?) {
            if (completedUtteranceId == null) return
            val currentActiveBatch = activeBatchByEngine[engineKey] ?: return
            if (!completedUtteranceId.startsWith(currentActiveBatch)) return

            val nextItem = pendingSegmentsQueue[engineKey]?.removeFirstOrNull() ?: return
            applyLocaleAndVoice(tts, nextItem.segment.locale)
            tts.speak(
                nextItem.segment.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                nextItem.utteranceId
            )
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                playNextInBatch(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                playNextInBatch(utteranceId)
            }
        })

        val firstItem = queuedItems.first()
        applyLocaleAndVoice(tts, firstItem.segment.locale)
        tts.speak(
            firstItem.segment.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            firstItem.utteranceId
        )
    }

    /**
     * Checks whether [targetLocale] has an installed/supported TTS voice on the engine.
     */
    internal fun isLocalePlayableOnEngine(tts: TextToSpeech, targetLocale: Locale): Boolean {
        val langKey = targetLocale.language.lowercase(Locale.ROOT)
        val allVoices = runCatching { tts.voices }.getOrNull()
        if (!allVoices.isNullOrEmpty()) {
            val hasInstalledVoice = allVoices.any { voice ->
                voice.locale != null &&
                    voice.locale.language.equals(langKey, ignoreCase = true) &&
                    voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
                    !voice.features.orEmpty().any { it.contains("notInstalled", ignoreCase = true) }
            }
            if (hasInstalledVoice) return true
        }

        val candidates = candidateLocalesByLang[langKey]
            ?: listOf(targetLocale, Locale.forLanguageTag(targetLocale.language))
        for (candidate in candidates) {
            val avail = runCatching { tts.isLanguageAvailable(candidate) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (avail >= TextToSpeech.LANG_AVAILABLE) {
                return true
            }
        }
        return false
    }

    /**
     * Returns [targetLocale] if playable on the TTS engine; otherwise falls back to [englishLocale].
     */
    internal fun resolvePlayableLocaleOrEnglish(tts: TextToSpeech, targetLocale: Locale): Locale {
        if (targetLocale.language.equals("en", ignoreCase = true)) return targetLocale
        return if (isLocalePlayableOnEngine(tts, targetLocale)) {
            targetLocale
        } else {
            englishLocale
        }
    }

    private fun applyLocaleAndVoice(tts: TextToSpeech, targetLocale: Locale) {
        try {
            val langKey = targetLocale.language.lowercase(Locale.ROOT)
            val candidates = candidateLocalesByLang[langKey]
                ?: listOf(targetLocale, Locale.forLanguageTag(targetLocale.language))

            // 1. Try installed voice matching targetLocale
            val allVoices = runCatching { tts.voices }.getOrNull()
            if (!allVoices.isNullOrEmpty()) {
                val installedMatchingVoice = allVoices
                    .filter { voice ->
                        voice.locale != null &&
                            voice.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                            voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
                            !voice.features.orEmpty().any { it.contains("notInstalled", ignoreCase = true) }
                    }
                    .sortedWith(
                        compareByDescending<Voice> { voice ->
                            voice.locale.country.equals(targetLocale.country, ignoreCase = true)
                        }
                            .thenBy { voice -> voice.isNetworkConnectionRequired }
                            .thenByDescending { voice -> voice.quality }
                    )
                    .firstOrNull()

                if (installedMatchingVoice != null) {
                    tts.setLanguage(installedMatchingVoice.locale)
                    tts.voice = installedMatchingVoice
                    return
                }
            }

            // 2. Otherwise check regional Locale availability
            for (candidate in candidates) {
                val avail = runCatching { tts.isLanguageAvailable(candidate) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (avail >= TextToSpeech.LANG_AVAILABLE) {
                    val setRes = tts.setLanguage(candidate)
                    if (setRes != TextToSpeech.LANG_MISSING_DATA && setRes != TextToSpeech.LANG_NOT_SUPPORTED) {
                        return
                    }
                }
            }

            // 3. Language unavailable in TTS options -> fall back to English (en-US)
            tts.setLanguage(englishLocale)
        } catch (_: Exception) {
            runCatching { tts.setLanguage(englishLocale) }
        }
    }
}
