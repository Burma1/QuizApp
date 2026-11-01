package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.quizapp.data.ai.AiClient
import com.example.quizapp.data.algorithm.LearnQuestionType
import com.example.quizapp.data.algorithm.SpacedRepetitionEngine
import com.example.quizapp.data.model.AiProvider
import com.example.quizapp.data.model.AnkiGrade
import com.example.quizapp.data.model.CardMasteryStatus
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.LearnStudyGoal
import com.example.quizapp.data.model.StudyModule
import com.example.quizapp.data.repository.QuizRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("quizapp_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `AndroidManifest declares INTERNET and ACCESS_NETWORK_STATE permissions to prevent socket EPERM`() {
        val pm = context.packageManager
        val pkgInfo = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val requested = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertTrue("Manifest must include android.permission.INTERNET", requested.contains(android.Manifest.permission.INTERNET))
        assertTrue("Manifest must include android.permission.ACCESS_NETWORK_STATE", requested.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
    }

    @Test
    fun `SmartTtsHelper detects English, Russian, and Spanish locales and splits mixed definitions`() {
        val enLocale = com.example.quizapp.ui.components.SmartTtsHelper.detectLocaleForText("Break the ice")
        assertEquals("en", enLocale.language)

        val ruLocale = com.example.quizapp.ui.components.SmartTtsHelper.detectLocaleForText("Разрядить обстановку")
        assertEquals("ru", ruLocale.language)

        val esLocale = com.example.quizapp.ui.components.SmartTtsHelper.detectLocaleForText("¡Buenos días! ¿Qué tal?")
        assertEquals("es", esLocale.language)

        val segments = com.example.quizapp.ui.components.SmartTtsHelper.splitIntoLanguageSegments(
            "Разрядить обстановку (To make people feel more comfortable)."
        )
        assertEquals(2, segments.size)
        assertEquals("ru", segments[0].locale.language)
        assertEquals("en", segments[1].locale.language)
    }

    @Test
    fun `SpacedRepetitionEngine divides cards into blocks of 8 and repeats missed cards in next block`() {
        val sampleCards = (1..20).map { idx ->
            Flashcard(
                id = "card_$idx",
                moduleId = "mod_1",
                term = "Term $idx",
                definition = "Definition $idx"
            )
        }

        // 1st block should have exactly 8 cards
        val (firstBlock, cursorAfterFirst) = SpacedRepetitionEngine.buildNextBlockOf8(
            allOrderedCards = sampleCards,
            startIndex = 0,
            missedQueue = emptyList()
        )
        assertEquals(8, firstBlock.size)
        assertEquals(8, cursorAfterFirst)
        assertEquals("card_1", firstBlock.first().id)
        assertEquals("card_8", firstBlock.last().id)

        // Suppose user missed 2 cards in the first block ("card_2" and "card_5")
        val missedFromFirst = listOf(sampleCards[1], sampleCards[4])

        // 2nd block should have 8 cards total: the 2 missed cards from block 1 + 6 new cards ("card_9".."card_14")
        val (secondBlock, cursorAfterSecond) = SpacedRepetitionEngine.buildNextBlockOf8(
            allOrderedCards = sampleCards,
            startIndex = cursorAfterFirst,
            missedQueue = missedFromFirst
        )
        assertEquals(8, secondBlock.size)
        assertTrue("Second block must repeat missed card_2", secondBlock.any { it.id == "card_2" })
        assertTrue("Second block must repeat missed card_5", secondBlock.any { it.id == "card_5" })
        assertEquals(14, cursorAfterSecond)
    }

    @Test
    fun `SpacedRepetitionEngine builds Learn steps with Multiple Choice first and Written input after for tested words`() {
        val blockCards = (1..8).map { idx ->
            Flashcard(
                id = "c_$idx",
                moduleId = "mod_learn",
                term = "Word$idx",
                definition = "Meaning $idx"
            )
        }

        val examSteps = SpacedRepetitionEngine.buildLearnStepsForBlock(blockCards, LearnStudyGoal.EXAM_PREP)
        // First 8 steps are MULTIPLE_CHOICE, followed by WRITTEN steps for a subset of those 8 cards
        assertTrue(examSteps.size > 8)
        val mcSteps = examSteps.take(8)
        val writtenSteps = examSteps.drop(8)
        assertTrue(mcSteps.all { it.questionType == LearnQuestionType.MULTIPLE_CHOICE })
        assertTrue(writtenSteps.isNotEmpty())
        assertTrue(writtenSteps.all { it.questionType == LearnQuestionType.WRITTEN })
        // Every written step must be one of the cards from the MC test block
        val blockIds = blockCards.map { it.id }.toSet()
        assertTrue(writtenSteps.all { it.card.id in blockIds })

        val memorizeAllSteps = SpacedRepetitionEngine.buildLearnStepsForBlock(blockCards, LearnStudyGoal.MEMORIZE_ALL)
        val memorizeWritten = memorizeAllSteps.drop(8)
        assertTrue("MEMORIZE_ALL should include written practice for words from the block", memorizeWritten.size >= writtenSteps.size)
    }

    @Test
    fun `Anki SM-2 algorithm updates easeFactor, intervalDays, repetitions and lapses`() {
        val card = Flashcard(
            id = "anki_1",
            moduleId = "m1",
            term = "Ubiquitous",
            definition = "Present everywhere"
        )

        // 1st review GOOD -> repetitions = 1, intervalDays = 1, status = LEARNING
        val afterFirstGood = SpacedRepetitionEngine.applyAnkiReview(card, AnkiGrade.GOOD, nowMs = 1000L)
        assertEquals(1, afterFirstGood.repetitions)
        assertEquals(1, afterFirstGood.intervalDays)
        assertEquals(CardMasteryStatus.LEARNING, afterFirstGood.status)
        assertFalse(afterFirstGood.needsReview)

        // 2nd review GOOD -> repetitions = 2, intervalDays = 4, status = MASTERED
        val afterSecondGood = SpacedRepetitionEngine.applyAnkiReview(afterFirstGood, AnkiGrade.GOOD, nowMs = 2000L)
        assertEquals(2, afterSecondGood.repetitions)
        assertEquals(4, afterSecondGood.intervalDays)
        assertEquals(CardMasteryStatus.MASTERED, afterSecondGood.status)
        assertTrue(afterSecondGood.isMastered)

        // Lapse (AGAIN) -> resets repetitions to 0, increments lapses, sets needsReview = true
        val afterAgain = SpacedRepetitionEngine.applyAnkiReview(afterSecondGood, AnkiGrade.AGAIN, nowMs = 3000L)
        assertEquals(0, afterAgain.repetitions)
        assertEquals(0, afterAgain.intervalDays)
        assertEquals(1, afterAgain.lapses)
        assertTrue(afterAgain.needsReview)
        assertEquals(CardMasteryStatus.NOT_STUDIED, afterAgain.status)
    }

    @Test
    fun `AiClient generates around 12 flashcards by default and exact count when specified`() = runTest {
        val aiClient = AiClient()

        val defaultCards = aiClient.generateFlashcards(
            provider = AiProvider.GOOGLE_GEMINI,
            apiKey = "",
            model = "gemini-3.8-flash",
            topic = "English Phrasal Verbs",
            moduleId = "test_mod_default"
        )
        assertTrue("Default cards should be around 12 (10..15)", defaultCards.size in 10..15)

        val fiveCards = aiClient.generateFlashcards(
            provider = AiProvider.GOOGLE_GEMINI,
            apiKey = "",
            model = "gemini-3.5-flash-lite",
            topic = "5 карточек по биологии клетки",
            moduleId = "test_mod_5"
        )
        assertEquals(5, fiveCards.size)

        // 1000 cards generation & extraction
        assertEquals(1000, aiClient.extractRequestedCardCount("создай 1000 карточек по английскому"))
        val thousandCards = aiClient.generateFlashcards(
            provider = AiProvider.GOOGLE_GEMINI,
            apiKey = "",
            model = "gemini-3.8-flash",
            topic = "1000 карточек по английскому",
            moduleId = "test_mod_1000"
        )
        assertEquals(1000, thousandCards.size)

        // 5000 cards generation & extraction + SpacedRepetitionEngine block-of-8 handling
        assertEquals(5000, aiClient.extractRequestedCardCount("сгенерируй по 5000 карточек IT терминов"))
        val fiveThousandCards = aiClient.generateFlashcards(
            provider = AiProvider.GOOGLE_GEMINI,
            apiKey = "",
            model = "gemini-3.8-flash",
            topic = "5000 карточек IT терминов",
            moduleId = "test_mod_5000"
        )
        assertEquals(5000, fiveThousandCards.size)

        val ordered5000 = SpacedRepetitionEngine.orderCardsForGoal(fiveThousandCards, LearnStudyGoal.EXAM_PREP)
        assertEquals(5000, ordered5000.size)
        val (block1, nextIdx) = SpacedRepetitionEngine.buildNextBlockOf8(ordered5000, 0, emptyList())
        assertEquals(8, block1.size)
        assertEquals(8, nextIdx)
    }

    @Test
    fun `Word mastery tracking transitions between NOT_STUDIED, LEARNING, MASTERED and needsReview`() {
        val quizRepo = QuizRepository(context)
        val mod = quizRepo.modules.value.first()
        val firstCardId = mod.cards.first().id

        assertEquals(
            CardMasteryStatus.NOT_STUDIED,
            quizRepo.modules.value.first().cards.first().status
        )

        quizRepo.updateCardMastery(mod.id, firstCardId, isKnown = true)
        val afterFirst = quizRepo.modules.value.first().cards.first { it.id == firstCardId }
        assertEquals(1, afterFirst.masteryScore)
        assertEquals(CardMasteryStatus.LEARNING, afterFirst.status)
        assertFalse(afterFirst.needsReview)

        quizRepo.updateCardMastery(mod.id, firstCardId, isKnown = true)
        val afterSecond = quizRepo.modules.value.first().cards.first { it.id == firstCardId }
        assertEquals(2, afterSecond.masteryScore)
        assertEquals(CardMasteryStatus.MASTERED, afterSecond.status)
        assertTrue(afterSecond.isMastered)

        quizRepo.updateCardMastery(mod.id, firstCardId, isKnown = false)
        val afterMissed = quizRepo.modules.value.first().cards.first { it.id == firstCardId }
        assertEquals(0, afterMissed.masteryScore)
        assertEquals(CardMasteryStatus.NOT_STUDIED, afterMissed.status)
        assertTrue(afterMissed.needsReview)
    }

    @Test
    fun `AI Chat does not create modules on general or library questions and only creates on explicit commands`() = runTest {
        val quizRepo = QuizRepository(context)
        val initialModuleCount = quizRepo.modules.value.size

        // 1. Asking what modules exist must NOT create a new module
        quizRepo.askAiTutor("какие модули у меня есть созданные тобой")
        assertEquals(initialModuleCount, quizRepo.modules.value.size)
        val lastMsg1 = quizRepo.chatMessages.value.last()
        assertFalse(lastMsg1.text.contains("Создан новый модуль"))
        assertTrue(lastMsg1.createdModuleId == null)

        // 2. Asking a general question containing the word "карточки" or "модули" must NOT create a module
        quizRepo.askAiTutor("Как лучше учить карточки и модули перед экзаменом?")
        assertEquals(initialModuleCount, quizRepo.modules.value.size)
        val lastMsg2 = quizRepo.chatMessages.value.last()
        assertTrue(lastMsg2.createdModuleId == null)

        // 3. Explicit creation command MUST create a new module
        quizRepo.askAiTutor("Создай карточки: Фрукты (Apple - Яблоко, Banana - Банан)")
        assertEquals(initialModuleCount + 1, quizRepo.modules.value.size)
        val createdMod = quizRepo.modules.value.first { !it.id.startsWith("seed_") }
        assertEquals("Фрукты", createdMod.title)

        // 4. Asking what modules were created by AI now lists "Фрукты" and does NOT create another module
        quizRepo.askAiTutor("какие модули у меня есть созданные тобой")
        assertEquals(initialModuleCount + 1, quizRepo.modules.value.size)
        val lastMsg4 = quizRepo.chatMessages.value.last()
        assertTrue(lastMsg4.text.contains("Фрукты"))
        assertTrue(lastMsg4.createdModuleId == null)

        // 5. Deleting the module removes it from the repository
        quizRepo.deleteModule(createdMod.id)
        assertEquals(initialModuleCount, quizRepo.modules.value.size)
    }
}

