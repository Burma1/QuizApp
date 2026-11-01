package com.example.quizapp.ui.components

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SpeechSegment(
    val text: String,
    val locale: Locale
)

/**
 * Smart multilingual TextToSpeech helper that automatically detects whether a word/phrase
 * is in English, Russian, or Spanish and switches the TTS engine's Locale and native voice
 * before speaking each segment so English words are pronounced with a natural English accent
 * and Russian words with a natural Russian accent.
 */
object SmartTtsHelper {

    private val russianLocale = Locale.forLanguageTag("ru-RU")
    private val englishLocale = Locale.US
    private val spanishLocale = Locale.forLanguageTag("es-ES")

    private val spanishDiacritics = setOf('á', 'é', 'í', 'ó', 'ú', 'ñ', 'ü', '¡', '¿', 'Á', 'É', 'Í', 'Ó', 'Ú', 'Ñ', 'Ü')

    /**
     * Detects the dominant language Locale for a piece of text:
     * - If Cyrillic characters dominate -> Russian (ru-RU)
     * - If Spanish diacritics/punctuation or common Spanish words are present -> Spanish (es-ES)
     * - If Latin characters dominate -> English (en-US)
     */
    fun detectLocaleForText(text: String): Locale {
        var cyrillicCount = 0
        var latinCount = 0
        var hasSpanishChars = false

        for (ch in text) {
            when {
                ch in '\u0400'..'\u04FF' -> cyrillicCount++
                ch in spanishDiacritics -> {
                    hasSpanishChars = true
                    latinCount++
                }
                ch in 'a'..'z' || ch in 'A'..'Z' -> latinCount++
            }
        }

        if (cyrillicCount > 0 && cyrillicCount >= latinCount) {
            return russianLocale
        }
        if (hasSpanishChars) {
            return spanishLocale
        }
        if (latinCount > 0) {
            return englishLocale
        }
        return russianLocale
    }

    /**
     * Splits mixed text (for example: "Разрядить обстановку (To make people feel more comfortable)"
     * or "Водород (H, 1)") into language-consistent segments so that Russian parts are spoken
     * with the Russian voice and English parts (if any) or standalone terms are spoken with the
     * proper native voice.
     */
    fun splitIntoLanguageSegments(rawText: String): List<SpeechSegment> {
        val cleaned = rawText
            .replace("💡", "")
            .replace("Пример:", "")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        // If the text is purely one script (or has no parenthetical secondary language), return a single segment
        val hasCyrillic = cleaned.any { it in '\u0400'..'\u04FF' }
        val hasLatin = cleaned.any { it in 'a'..'z' || it in 'A'..'Z' }

        if (!hasCyrillic || !hasLatin) {
            return listOf(SpeechSegment(cleaned, detectLocaleForText(cleaned)))
        }

        // Mixed Cyrillic + Latin text (e.g. "Доброе утро! (Good morning!)" or "Apple — Яблоко"):
        // Split by parentheses or em-dash / sentence boundaries so each part gets its own native accent!
        val rawParts = cleaned
            .split(Regex("""(?=[(\[])|(?<=[)\]])|\s+—\s+|\s+-\s+|\.\s+"""))
            .map { it.trim().trim('(', ')', '[', ']', '.', ',', ';') }
            .filter { it.isNotBlank() && it.any { ch -> ch.isLetterOrDigit() } }

        if (rawParts.isEmpty()) {
            return listOf(SpeechSegment(cleaned, detectLocaleForText(cleaned)))
        }

        val segments = mutableListOf<SpeechSegment>()
        for (part in rawParts) {
            val locale = detectLocaleForText(part)
            // Merge adjacent segments of the same language
            if (segments.isNotEmpty() && segments.last().locale == locale) {
                val prev = segments.removeAt(segments.lastIndex)
                segments.add(SpeechSegment("${prev.text}. $part", locale))
            } else {
                segments.add(SpeechSegment(part, locale))
            }
        }
        return segments
    }

    private val pendingSegmentsQueue = ConcurrentHashMap<Int, ArrayDeque<SpeechSegment>>()

    /**
     * Speaks the given text with automatic language and accent switching.
     */
    fun speakSmart(tts: TextToSpeech?, text: String) {
        if (tts == null || text.isBlank()) return
        val segments = splitIntoLanguageSegments(text)
        if (segments.isEmpty()) return

        val engineKey = System.identityHashCode(tts)
        val queue = ArrayDeque(segments.drop(1))
        pendingSegmentsQueue[engineKey] = queue

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                val next = pendingSegmentsQueue[engineKey]?.removeFirstOrNull()
                if (next != null) {
                    applyLocaleAndVoice(tts, next.locale)
                    tts.speak(
                        next.text,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "seg_${UUID.randomUUID()}"
                    )
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })

        val first = segments.first()
        applyLocaleAndVoice(tts, first.locale)
        tts.speak(
            first.text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "seg_${UUID.randomUUID()}"
        )
    }

    private fun applyLocaleAndVoice(tts: TextToSpeech, targetLocale: Locale) {
        try {
            val result = tts.setLanguage(targetLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to generic language code (e.g. Locale("en") or Locale("ru"))
                tts.setLanguage(Locale(targetLocale.language))
            }
            // Select the highest-quality non-network-dependent voice matching the language if available
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                val matchingVoice = voices
                    .filter { it.locale.language.equals(targetLocale.language, ignoreCase = true) }
                    .sortedWith(
                        compareByDescending<android.speech.tts.Voice> {
                            it.locale.country.equals(targetLocale.country, ignoreCase = true)
                        }
                            .thenBy { it.isNetworkConnectionRequired }
                            .thenByDescending { it.quality }
                    )
                    .firstOrNull()
                if (matchingVoice != null) {
                    tts.voice = matchingVoice
                }
            }
        } catch (_: Exception) {
            // Ignore voice selection errors on minimal/stubbed TTS engines
        }
    }
}
