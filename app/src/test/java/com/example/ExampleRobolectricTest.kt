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
import com.example.quizapp.ui.components.SmartTtsHelper
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
    fun `SmartTtsHelper detects alphabets deterministically and falls back basic Latin to English`() {
        val enLocale = SmartTtsHelper.detectLocaleForText("Break the ice")
        assertEquals("en", enLocale.language)

        val ruLocale = SmartTtsHelper.detectLocaleForText("Разрядить обстановку")
        assertEquals("ru", ruLocale.language)

        // Unique Spanish characters -> Spanish ("es")
        val esLocaleAccented = SmartTtsHelper.detectLocaleForText("¡Buenos días! ¿Qué tal?")
        assertEquals("es", esLocaleAccented.language)

        // Pure basic Latin (a-z) is ALWAYS English ("en"), never Spanish accent!
        assertEquals("en", SmartTtsHelper.detectLocaleForText("Bailando en la noche").language)
        assertEquals("en", SmartTtsHelper.detectLocaleForText("Color").language)
        assertEquals("en", SmartTtsHelper.detectLocaleForText("Despacito").language)
        assertEquals("en", SmartTtsHelper.detectLocaleForText("Mirada").language)

        val segments = SmartTtsHelper.splitIntoLanguageSegments(
            "Разрядить обстановку (To make people feel more comfortable)."
        )
        assertEquals(2, segments.size)
        assertEquals("ru", segments[0].locale.language)
        assertEquals("en", segments[1].locale.language)

        // Arabic ("ar")
        assertEquals("ar", SmartTtsHelper.detectLocaleForText("السلام عليكم ورحمة الله").language)
        assertEquals("ar", SmartTtsHelper.detectLocaleForText("مرحبا بك").language)

        // Portuguese ("pt") - unique ã / õ
        assertEquals("pt", SmartTtsHelper.detectLocaleForText("Não tenho coração").language)

        // French ("fr") - unique French letters/diacritics
        assertEquals("fr", SmartTtsHelper.detectLocaleForText("J'aime beaucoup cette chanson française").language)

        // German ("de") - unique umlauts/Eszett
        assertEquals("de", SmartTtsHelper.detectLocaleForText("Ich möchte Deutsch lernen").language)

        // Kazakh ("kk") - unique Kazakh Cyrillic letters
        assertEquals("kk", SmartTtsHelper.detectLocaleForText("Сәлеметсіз бе, қалайсың?").language)
        assertEquals("kk", SmartTtsHelper.detectLocaleForText("Қайырлы таң достар").language)
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

    @Test
    fun `Adding editing and deleting terms in custom and seed modules persists across repository reloads`() {
        val repo1 = QuizRepository(context)
        val seedId = repo1.modules.value.first { it.id.startsWith("seed_") }.id
        val initialSeedCount = repo1.modules.value.first { it.id == seedId }.cards.size

        // Add term to seed module
        repo1.addCardToModule(seedId, "Break a leg", "Ни пуха ни пера", "Break a leg on stage!")
        val addedCard = repo1.modules.value.first { it.id == seedId }.cards.last()
        assertEquals("Break a leg", addedCard.term)
        assertEquals(initialSeedCount + 1, repo1.modules.value.first { it.id == seedId }.cards.size)

        // Update term
        repo1.updateCardInModule(seedId, addedCard.id, "Break a leg!", "Удачи!", "Break a leg tonight!")
        val updatedCard = repo1.modules.value.first { it.id == seedId }.cards.first { it.id == addedCard.id }
        assertEquals("Break a leg!", updatedCard.term)
        assertEquals("Удачи!", updatedCard.definition)

        // Reload repository from SharedPreferences and verify persistence
        val repo2 = QuizRepository(context)
        val reloadedMod = repo2.modules.value.first { it.id == seedId }
        assertEquals(initialSeedCount + 1, reloadedMod.cards.size)
        assertEquals("Удачи!", reloadedMod.cards.first { it.id == addedCard.id }.definition)

        // Delete term
        repo2.deleteCardFromModule(seedId, addedCard.id)
        val repo3 = QuizRepository(context)
        assertEquals(initialSeedCount, repo3.modules.value.first { it.id == seedId }.cards.size)
    }

    @Test
    fun `Imported module appears before seed modules and exports cleanly to JSON and CSV`() {
        val repo = QuizRepository(context)
        val imported = repo.importModuleFromTextOrCsv(
            rawText = "\uFEFFЗоология — наука о животных\nЭтология – наука о поведении\nИхтиология - наука о рыбах",
            defaultTitle = "Зоология 7 класс"
        ).copy(category = "Биология")
        repo.saveModule(imported)

        // Imported/custom modules appear first so export selector always shows them cleanly
        val firstMod = repo.modules.value.first()
        assertEquals("Зоология 7 класс", firstMod.title)
        assertEquals("Биология", firstMod.category)
        assertEquals(3, firstMod.cards.size)

        val json = repo.exportModuleToJson(firstMod)
        assertTrue(json.contains("Зоология 7 класс"))
        assertTrue(json.contains("Ихтиология"))

        val csv = repo.exportModuleToCsv(firstMod)
        assertTrue(csv.contains("# Зоология 7 класс"))
        assertTrue(csv.contains("Этология\tнаука о поведении"))
    }

    @Test
    fun `AI action tag creates and updates modules cleanly and removes service tags`() {
        val repo = QuizRepository(context)
        val aiClient = AiClient()

        // 1. Ensure prose with colons doesn't produce garbage fragments like "скелеты).Ш..."
        val messyProse = """
            Бывает, классика жанра перед концом четверти.
            По оглавлению до 6-го параграфа у нас база: § 1. Зоология как наука.
            Вопрос на проверку: Если учёный наблюдает, как стая волков охотится?
            [Program finished]
        """.trimIndent()
        val parsedProse = aiClient.parseFlashcardsFromJsonOrText(messyProse, "tmp")
        assertTrue("Prose sentences must not produce broken flashcards", parsedProse.isEmpty())

        // 2. Execute <quizapp_action> create_module
        val aiResponseCreate = """
            Готово! Я разобрал конспект по зоологии и создал 3 карточки.
            [Program finished]
            <quizapp_action>
            {"action":"create_module","title":"Зоология §1","category":"Биология","cards":[
              {"term":"Зоология","definition":"Наука о животных: их строении, поведении и развитии."},
              {"term":"Этология","definition":"Раздел зоологии, изучающий поведение животных."},
              {"term":"Ихтиология","definition":"Раздел зоологии, изучающий рыб."}
            ]}
            </quizapp_action>
        """.trimIndent()

        val processedCreate = repo.processAiActionResponse(aiResponseCreate, "создай карточки по зоологии")
        assertFalse(processedCreate.visibleText.contains("quizapp_action"))
        assertFalse(processedCreate.visibleText.contains("[Program finished]"))
        assertNotNull(processedCreate.createdModuleId)
        assertEquals("Зоология §1", processedCreate.createdModuleTitle)
        assertEquals(3, processedCreate.suggestedCards.size)

        val createdId = processedCreate.createdModuleId!!
        val storedMod = repo.modules.value.first { it.id == createdId }
        assertEquals(3, storedMod.cards.size)

        // 3. Execute <quizapp_action> add_cards to the created module
        val aiResponseAdd = """
            Добавил ещё одну карточку в модуль «Зоология §1».
            <quizapp_action>
            {"action":"add_cards","module_id":"$createdId","cards":[
              {"term":"Бионика","definition":"Использование природных инженерных решений в технике."}
            ]}
            </quizapp_action>
        """.trimIndent()
        val processedAdd = repo.processAiActionResponse(aiResponseAdd, "добавь бионику")
        assertEquals(createdId, processedAdd.createdModuleId)
        assertEquals(4, repo.modules.value.first { it.id == createdId }.cards.size)
    }

    @Test
    fun `Full database export and import preserves custom cards progress needsReview learning mastered starred and folder order`() {
        val repo1 = QuizRepository(context)

        // 1. Create custom folders and custom module
        repo1.createFolder("Экзамен Биология")
        repo1.createFolder("IT Собеседование")
        val customMod = StudyModule(
            id = "custom_bio_1",
            title = "Клеточная биология",
            description = "Органеллы клетки",
            category = "Экзамен Биология",
            cards = listOf(
                Flashcard(id = "cb_1", moduleId = "custom_bio_1", term = "Митохондрия", definition = "Энергетическая станция клетки"),
                Flashcard(id = "cb_2", moduleId = "custom_bio_1", term = "Рибосома", definition = "Органелла синтеза белка"),
                Flashcard(id = "cb_3", moduleId = "custom_bio_1", term = "Лизосома", definition = "Внутриклеточное пищеварение")
            )
        )
        repo1.saveModule(customMod)

        // 2. Set different mastery statuses:
        // cb_1 -> MASTERED (2x known) + Starred
        repo1.updateCardMastery("custom_bio_1", "cb_1", isKnown = true)
        repo1.updateCardMastery("custom_bio_1", "cb_1", isKnown = true)
        repo1.toggleCardStarred("custom_bio_1", "cb_1")

        // cb_2 -> LEARNING (1x known)
        repo1.updateCardMastery("custom_bio_1", "cb_2", isKnown = true)

        // cb_3 -> needsReview (missed) + Starred
        repo1.updateCardMastery("custom_bio_1", "cb_3", isKnown = false)
        repo1.toggleCardStarred("custom_bio_1", "cb_3")

        // Also star and mark progress on a seed module card
        val seedMod = repo1.modules.value.first { it.id.startsWith("seed_") }
        val seedCardId = seedMod.cards.first().id
        repo1.updateCardMastery(seedMod.id, seedCardId, isKnown = true)
        repo1.toggleCardStarred(seedMod.id, seedCardId)

        // 3. Reorder folders and rename a folder
        repo1.renameFolder("IT Собеседование", "Архитектура ПО")
        repo1.setFolderOrder(listOf("Экзамен Биология", "Архитектура ПО", "Английский язык", "Общее"))

        // 4. Export full database JSON
        val exportedJson = repo1.exportFullDatabaseToJson()
        assertTrue(exportedJson.contains("quizapp_full_db_v2"))
        assertTrue(exportedJson.contains("Клеточная биология"))
        assertTrue(exportedJson.contains("Экзамен Биология"))

        // 5. Wipe SharedPreferences completely and restore into a fresh repository
        context.getSharedPreferences("quizapp_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val repoFresh = QuizRepository(context)
        assertFalse(repoFresh.modules.value.any { it.id == "custom_bio_1" })

        val restoreResult = repoFresh.importFullDatabaseFromJson(exportedJson, replaceExisting = true)
        assertTrue("Full DB restore must succeed", restoreResult.isSuccess)
        val summary = restoreResult.getOrThrow()
        assertEquals(1, summary.customModulesCount)
        assertEquals(1, summary.masteredCards)
        assertEquals(2, summary.learningCards)
        assertEquals(1, summary.needsReviewCards)
        assertEquals(3, summary.starredCards)

        // 6. Verify restored state across a brand-new repository reload
        val repoReloaded = QuizRepository(context)
        val restoredBio = repoReloaded.modules.value.first { it.id == "custom_bio_1" }
        assertEquals("Экзамен Биология", restoredBio.category)

        val c1 = restoredBio.cards.first { it.id == "cb_1" }
        val c2 = restoredBio.cards.first { it.id == "cb_2" }
        val c3 = restoredBio.cards.first { it.id == "cb_3" }

        assertEquals(CardMasteryStatus.MASTERED, c1.status)
        assertTrue(c1.isStarred)

        assertEquals(CardMasteryStatus.LEARNING, c2.status)
        assertFalse(c2.isStarred)

        assertTrue(c3.needsReview)
        assertTrue(c3.isStarred)

        // Verify folder order was preserved
        assertEquals("Экзамен Биология", repoReloaded.folderOrder.value.first())
        assertTrue(repoReloaded.customFolders.value.contains("Архитектура ПО"))
    }

    @Test
    fun `CreateModule AI generation extracts flashcards from attached file text`() = runTest {
        val repo = QuizRepository(context)
        val attachment = com.example.quizapp.data.model.ChatAttachment(
            id = "att_1",
            name = "biology_notes.txt",
            mimeType = "text/plain",
            type = com.example.quizapp.data.model.ChatAttachmentType.FILE,
            extractedText = "Фотосинтез — процесс образования органических веществ на свету\nХлорофилл — зелёный пигмент растений\nМитоз — непрямое деление клетки"
        )
        val generated = repo.generateFlashcardsForTopic(
            topic = "",
            attachments = listOf(attachment)
        )
        assertEquals(3, generated.size)
        assertEquals("Фотосинтез", generated[0].term)
        assertEquals("Хлорофилл", generated[1].term)
        assertEquals("Митоз", generated[2].term)
    }

    @Test
    fun `SmartTtsHelper uses strict letter-based detection and QuizRepository stores and clears recent TTS languages`() {
        // 1. Any basic Latin a-z text is strictly English (never Spanish accent on English words)
        assertEquals("en", SmartTtsHelper.detectLocaleForText("I'm coming").language)
        assertEquals("en", SmartTtsHelper.detectLocaleForText("camera").language)
        assertEquals("en", SmartTtsHelper.detectLocaleForText("solo").language)
        assertEquals("en", SmartTtsHelper.detectLocaleForText("hola").language)
        assertEquals(
            "en",
            SmartTtsHelper.detectLocaleForText("I'm coming", SmartTtsHelper.spanishLocale).language
        )

        // 2. Unique non-English letters identify their language accurately
        assertEquals("ru", SmartTtsHelper.detectLocaleForText("Первое").language)
        assertEquals("kk", SmartTtsHelper.detectLocaleForText("Сәлем, қалың қалай?").language)
        assertEquals("ar", SmartTtsHelper.detectLocaleForText("مرحبا كيف حالك").language)
        assertEquals("es", SmartTtsHelper.detectLocaleForText("España — ¿Qué tal?").language)
        assertEquals("pt", SmartTtsHelper.detectLocaleForText("coração não").language)
        assertEquals("fr", SmartTtsHelper.detectLocaleForText("Cœur et lumière").language)
        assertEquals("de", SmartTtsHelper.detectLocaleForText("Straße und Größe").language)

        // 3. Multi-alphabet splitting across Cyrillic / basic Latin / unique Spanish
        val segments = SmartTtsHelper.splitIntoLanguageSegments("Первое: I'm coming — ¿Qué tal?")
        assertEquals(3, segments.size)
        assertEquals("Первое", segments[0].text)
        assertEquals("ru", segments[0].locale.language)
        assertEquals("I'm coming", segments[1].text)
        assertEquals("en", segments[1].locale.language)
        assertEquals("¿Qué tal?", segments[2].text)
        assertEquals("es", segments[2].locale.language)

        // 4. Repository persistence for Recently Used TTS languages and Trash clear action
        val repo = QuizRepository(context)
        assertTrue(repo.recentTtsLanguages.value.isEmpty())

        repo.addRecentTtsLanguage("es-ES")
        repo.addRecentTtsLanguage("de-DE")
        repo.addRecentTtsLanguage("es-ES") // Moves to front without duplicates
        assertEquals(listOf("es-ES", "de-DE"), repo.recentTtsLanguages.value)

        // Survives repository reload
        val repoReloaded = QuizRepository(context)
        assertEquals(listOf("es-ES", "de-DE"), repoReloaded.recentTtsLanguages.value)

        // Clear removes all recent entries persistently
        repoReloaded.clearRecentTtsLanguages()
        assertTrue(repoReloaded.recentTtsLanguages.value.isEmpty())
        val repoAfterClear = QuizRepository(context)
        assertTrue(repoAfterClear.recentTtsLanguages.value.isEmpty())
    }
}




