package com.example.quizapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizapp.data.algorithm.LearnQuestionType
import com.example.quizapp.data.algorithm.LearnStepItem
import com.example.quizapp.data.algorithm.SpacedRepetitionEngine
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.model.CardMasteryStatus
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.LearnStudyGoal
import com.example.quizapp.data.model.StudyModule
import com.example.quizapp.ui.components.DiagramIllustration
import com.example.ui.theme.StatusLearningDark
import com.example.ui.theme.StatusMasteredDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    module: StudyModule,
    customCards: List<Flashcard>? = null,
    onBack: () -> Unit,
    onUpdateMastery: (String, Boolean) -> Unit
) {
    val s = LocalStrings.current

    val baseDeck = remember(module.id, customCards) {
        if (!customCards.isNullOrEmpty()) customCards else module.cards
    }

    if (baseDeck.isEmpty()) {
        onBack()
        return
    }

    // Two study paths ("Выберите цель этого учебного сеанса"):
    // 1. Хорошо подготовиться к экзамену (EXAM_PREP)
    // 2. Запомнить весь материал (MEMORIZE_ALL)
    var selectedGoal by remember { mutableStateOf(LearnStudyGoal.EXAM_PREP) }
    var showGoalBottomSheet by remember { mutableStateOf(true) }

    // Ordered cards according to Anki SM-2 & selected goal
    var orderedDeck by remember(baseDeck, selectedGoal) {
        mutableStateOf(SpacedRepetitionEngine.orderCardsForGoal(baseDeck, selectedGoal))
    }

    // Queue of missed cards that must be repeated in the next 8-card block(s)
    val missedQueue = remember { mutableStateListOf<Flashcard>() }

    // Initial block of up to 8 cards
    val initialBlockPair = remember(orderedDeck) {
        SpacedRepetitionEngine.buildNextBlockOf8(orderedDeck, 0, emptyList())
    }

    var currentBlockCards by remember(orderedDeck) { mutableStateOf(initialBlockPair.first) }
    var nextUnseenCursor by remember(orderedDeck) { mutableIntStateOf(initialBlockPair.second) }
    var currentBlockNumber by remember(orderedDeck) { mutableIntStateOf(1) }

    // Steps inside the current 8-card block:
    // First: Multiple Choice (Тест-выбор) for the cards in the block
    // Second: Written recall (Письменно на клавиатуре) for a subset of the tested cards
    // Plus any re-queued mistakes within the block
    val blockSteps = remember(currentBlockCards, selectedGoal) {
        mutableStateListOf<LearnStepItem>().apply {
            addAll(SpacedRepetitionEngine.buildLearnStepsForBlock(currentBlockCards, selectedGoal))
        }
    }

    var currentStepIndex by remember(currentBlockCards, selectedGoal) { mutableIntStateOf(0) }
    var manualModeOverride by remember { mutableStateOf<LearnQuestionType?>(null) }

    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var writtenInput by remember { mutableStateOf("") }
    var isAnswerSubmitted by remember { mutableStateOf(false) }
    var lastSubmittedCorrect by remember { mutableStateOf(false) }

    var correctCount by remember { mutableIntStateOf(0) }
    var totalAttempted by remember { mutableIntStateOf(0) }
    var showCompletion by remember { mutableStateOf(false) }

    val wrongCardsInSession = remember { mutableStateListOf<Flashcard>() }

    // Helper to reset and start session with a chosen study path
    val applyStudyGoalAndReset: (LearnStudyGoal) -> Unit = { newGoal ->
        selectedGoal = newGoal
        val reordered = SpacedRepetitionEngine.orderCardsForGoal(baseDeck, newGoal)
        orderedDeck = reordered
        missedQueue.clear()
        val (firstBlock, cursor) = SpacedRepetitionEngine.buildNextBlockOf8(reordered, 0, emptyList())
        currentBlockCards = firstBlock
        nextUnseenCursor = cursor
        currentBlockNumber = 1
        blockSteps.clear()
        blockSteps.addAll(SpacedRepetitionEngine.buildLearnStepsForBlock(firstBlock, newGoal))
        currentStepIndex = 0
        manualModeOverride = null
        selectedAnswer = null
        writtenInput = ""
        isAnswerSubmitted = false
    }

    // Advance to the next step inside the current 8-card block, or transition to the next 8-card block
    val advanceStepOrBlock: () -> Unit = {
        isAnswerSubmitted = false
        selectedAnswer = null
        writtenInput = ""
        manualModeOverride = null

        if (currentStepIndex < blockSteps.size - 1) {
            currentStepIndex++
        } else {
            // Current 8-card block (both MC + Written stages) is finished!
            // Load the next block of 8 cards (including any missed cards so user repeats them)
            if (nextUnseenCursor < orderedDeck.size || missedQueue.isNotEmpty()) {
                val (nextBlock, newCursor) = SpacedRepetitionEngine.buildNextBlockOf8(
                    allOrderedCards = orderedDeck,
                    startIndex = nextUnseenCursor,
                    missedQueue = missedQueue.toList()
                )
                val pulledIds = nextBlock.map { it.id }.toSet()
                missedQueue.removeAll { it.id in pulledIds }

                if (nextBlock.isNotEmpty()) {
                    currentBlockCards = nextBlock
                    nextUnseenCursor = newCursor
                    currentBlockNumber++
                    blockSteps.clear()
                    blockSteps.addAll(SpacedRepetitionEngine.buildLearnStepsForBlock(nextBlock, selectedGoal))
                    currentStepIndex = 0
                } else {
                    showCompletion = true
                }
            } else {
                showCompletion = true
            }
        }
    }

    val currentStepItem = blockSteps.getOrNull(currentStepIndex.coerceIn(0, (blockSteps.size - 1).coerceAtLeast(0)))
        ?: LearnStepItem(currentBlockCards.first(), LearnQuestionType.MULTIPLE_CHOICE)
    val currentCard = currentStepItem.card
    val effectiveQuestionType = manualModeOverride ?: currentStepItem.questionType
    val isWrittenMode = effectiveQuestionType == LearnQuestionType.WRITTEN

    val options = remember(currentStepIndex, currentBlockNumber, currentCard.id, module.cards.size) {
        val allCards = module.cards
        val wrongAnswers = mutableSetOf<String>()
        if (allCards.size <= 4) {
            allCards.filter { it.id != currentCard.id }.forEach { wrongAnswers.add(it.term) }
        } else {
            var attempts = 0
            while (wrongAnswers.size < 3 && attempts < 25) {
                val candidate = allCards[kotlin.random.Random.nextInt(allCards.size)]
                if (candidate.id != currentCard.id && candidate.term != currentCard.term) {
                    wrongAnswers.add(candidate.term)
                }
                attempts++
            }
            if (wrongAnswers.size < 3) {
                for (c in allCards) {
                    if (wrongAnswers.size >= 3) break
                    if (c.id != currentCard.id && c.term != currentCard.term) {
                        wrongAnswers.add(c.term)
                    }
                }
            }
        }
        (wrongAnswers.toList() + currentCard.term).shuffled()
    }

    val recordAnswer: (Boolean) -> Unit = { isCorrect ->
        lastSubmittedCorrect = isCorrect
        totalAttempted++
        if (isCorrect) {
            correctCount++
            // If user answered correctly on a written question or non-retry, remove from session wrong list
            if (isWrittenMode || !currentStepItem.isRetryInBlock) {
                wrongCardsInSession.removeAll { it.id == currentCard.id }
            }
        } else {
            if (wrongCardsInSession.none { it.id == currentCard.id }) {
                wrongCardsInSession.add(currentCard)
            }
            // Add to missedQueue so it is guaranteed to appear again in the next 8-card block
            if (missedQueue.none { it.id == currentCard.id }) {
                missedQueue.add(currentCard)
            }
            // Also re-queue once at the end of the current block if not already retried in this block
            if (!currentStepItem.isRetryInBlock && blockSteps.count { it.card.id == currentCard.id && it.isRetryInBlock } == 0) {
                blockSteps.add(
                    LearnStepItem(
                        card = currentCard,
                        questionType = LearnQuestionType.MULTIPLE_CHOICE,
                        isRetryInBlock = true
                    )
                )
            }
        }
        onUpdateMastery(currentCard.id, isCorrect)
    }

    val overrideWrittenAsCorrect: () -> Unit = {
        if (isAnswerSubmitted && !lastSubmittedCorrect) {
            lastSubmittedCorrect = true
            correctCount++
            wrongCardsInSession.removeAll { it.id == currentCard.id }
            missedQueue.removeAll { it.id == currentCard.id }
            val retryIndex = blockSteps.indexOfLast {
                it.card.id == currentCard.id && it.isRetryInBlock
            }
            if (retryIndex > currentStepIndex) {
                blockSteps.removeAt(retryIndex)
            }
            // Restore and increment mastery after previous false record
            onUpdateMastery(currentCard.id, true)
            onUpdateMastery(currentCard.id, true)
        }
    }

    val remainingDeckCount = (orderedDeck.size - nextUnseenCursor).coerceAtLeast(0) + missedQueue.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "${s.learnMode} • Блок $currentBlockNumber",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedGoal == LearnStudyGoal.EXAM_PREP) {
                                    "Подготовка к экзамену"
                                } else {
                                    "Запомнить весь материал (Anki)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .clickable {
                                    manualModeOverride = if (isWrittenMode) {
                                        LearnQuestionType.MULTIPLE_CHOICE
                                    } else {
                                        LearnQuestionType.WRITTEN
                                    }
                                    selectedAnswer = null
                                    writtenInput = ""
                                    isAnswerSubmitted = false
                                }
                                .testTag("toggle_learn_mode_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWrittenMode) Icons.Default.Keyboard else Icons.Default.Quiz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isWrittenMode) "Письменно" else "Тест-выбор",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("close_learn_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Text(
                        text = "${currentStepIndex + 1} / ${blockSteps.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { showGoalBottomSheet = true },
                        modifier = Modifier.testTag("learn_goal_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Цель учебного сеанса",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Segmented progress bar matching screenshot (Left circle counter, segmented pills, Right total counter)
            LearnSegmentedHeaderBar(
                completedInBlock = currentStepIndex,
                totalStepsInBlock = blockSteps.size.coerceAtLeast(1),
                remainingTotalCards = (blockSteps.size - currentStepIndex).coerceAtLeast(0) + remainingDeckCount
            )

            // Block & Stage info pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "Блок $currentBlockNumber (${currentBlockCards.size} слов) • " +
                            if (isWrittenMode) "Этап 2: Письменное закрепление" else "Этап 1: Тест-узнавание",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                if (currentStepItem.isRetryInBlock) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusLearningDark.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "Повтор ошибки",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusLearningDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Prompt Card (MD3 Surface Container)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.definitionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val liveCard = module.cards.find { it.id == currentCard.id } ?: currentCard
                        val (statusLabel, statusColor) = when (liveCard.status) {
                            CardMasteryStatus.MASTERED -> s.masteredLabel to StatusMasteredDark
                            CardMasteryStatus.LEARNING -> s.learningLabel to StatusLearningDark
                            CardMasteryStatus.NOT_STUDIED -> if (liveCard.needsReview) {
                                s.needsReviewLabel to MaterialTheme.colorScheme.error
                            } else {
                                s.notStudiedLabel to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = statusColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (currentCard.diagramType != null) {
                        DiagramIllustration(diagramType = currentCard.diagramType)
                    }

                    Text(
                        text = currentCard.definition,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!isWrittenMode) {
                Text(
                    text = s.chooseCorrectAnswer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    options.forEachIndexed { index, optionText ->
                        val isSelected = selectedAnswer == optionText
                        val isCorrectOption = optionText == currentCard.term

                        val borderColor = when {
                            !isAnswerSubmitted && isSelected -> MaterialTheme.colorScheme.primary
                            isAnswerSubmitted && isCorrectOption -> StatusMasteredDark
                            isAnswerSubmitted && isSelected && !isCorrectOption -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }

                        val bgColor = when {
                            isAnswerSubmitted && isCorrectOption -> StatusMasteredDark.copy(alpha = 0.14f)
                            isAnswerSubmitted && isSelected && !isCorrectOption -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isAnswerSubmitted) {
                                    selectedAnswer = optionText
                                    isAnswerSubmitted = true
                                    val correct = optionText == currentCard.term
                                    recordAnswer(correct)
                                }
                                .testTag("learn_option_$index"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = bgColor),
                            border = BorderStroke(
                                width = if (isSelected || (isAnswerSubmitted && isCorrectOption)) 2.dp else 1.dp,
                                color = borderColor
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = optionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isAnswerSubmitted && isCorrectOption) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Correct",
                                        tint = StatusMasteredDark
                                    )
                                } else if (isAnswerSubmitted && isSelected && !isCorrectOption) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Incorrect",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Written Answer Mode (Письменный ввод на клавиатуре для закрепления слов из теста)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Введите термин на клавиатуре (закрепление слов из блока):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = writtenInput,
                        onValueChange = { raw ->
                            if (!isAnswerSubmitted) {
                                writtenInput = raw.replaceFirstChar { ch ->
                                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("written_answer_input"),
                        label = { Text(s.typeAnswerHint) },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isAnswerSubmitted && writtenInput.isNotBlank()) {
                                    isAnswerSubmitted = true
                                    val isCorrect = writtenInput.trim().equals(currentCard.term.trim(), ignoreCase = true)
                                    recordAnswer(isCorrect)
                                }
                            }
                        )
                    )

                    if (!isAnswerSubmitted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isAnswerSubmitted = true
                                    recordAnswer(false)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(s.dontKnow)
                            }
                            Button(
                                onClick = {
                                    isAnswerSubmitted = true
                                    val isCorrect = writtenInput.trim().equals(currentCard.term.trim(), ignoreCase = true)
                                    recordAnswer(isCorrect)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("check_written_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(s.checkAnswer)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (lastSubmittedCorrect) {
                                StatusMasteredDark.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (lastSubmittedCorrect) s.correctFeedback else s.incorrectFeedback,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lastSubmittedCorrect) StatusMasteredDark else MaterialTheme.colorScheme.onErrorContainer
                                )
                                if (!lastSubmittedCorrect) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${s.correctAnswerIs}: ${currentCard.term}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = overrideWrittenAsCorrect,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("override_written_correct_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, StatusMasteredDark),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = StatusMasteredDark
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Ответ верный",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isAnswerSubmitted) {
                val isLastStepOfBlock = currentStepIndex >= blockSteps.size - 1
                val hasMoreBlocks = nextUnseenCursor < orderedDeck.size || missedQueue.isNotEmpty()
                val buttonLabel = if (isLastStepOfBlock && hasMoreBlocks) {
                    "Следующий блок из 8 карточек"
                } else {
                    s.nextQuestion
                }

                Button(
                    onClick = { advanceStepOrBlock() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(54.dp)
                        .testTag("next_question_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = buttonLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Bottom Sheet: "Выберите цель этого учебного сеанса" (matching attached screenshot in our app's dark green MD3 theme)
    if (showGoalBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var pendingGoal by remember(selectedGoal) { mutableStateOf(selectedGoal) }

        ModalBottomSheet(
            onDismissRequest = { showGoalBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Выберите цель этого\nучебного сеанса",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Option 1: Хорошо подготовиться к экзамену
                val isExamSelected = pendingGoal == LearnStudyGoal.EXAM_PREP
                Surface(
                    onClick = { pendingGoal = LearnStudyGoal.EXAM_PREP },
                    shape = RoundedCornerShape(26.dp),
                    color = if (isExamSelected) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    border = BorderStroke(
                        width = if (isExamSelected) 2.dp else 1.dp,
                        color = if (isExamSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_option_exam_prep")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Хорошо подготовиться к экзамену",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Блоки по 8 слов • Тест + письменный ввод • Быстрый повтор ошибок",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Lightning + Clock badge icon matching screenshot
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = StatusLearningDark,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.CenterStart)
                            )
                        }
                    }
                }

                // Option 2: Запомнить весь материал
                val isMemorizeSelected = pendingGoal == LearnStudyGoal.MEMORIZE_ALL
                Surface(
                    onClick = { pendingGoal = LearnStudyGoal.MEMORIZE_ALL },
                    shape = RoundedCornerShape(26.dp),
                    color = if (isMemorizeSelected) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    border = BorderStroke(
                        width = if (isMemorizeSelected) 2.dp else 1.dp,
                        color = if (isMemorizeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goal_option_memorize_all")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Запомнить весь материал",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Алгоритм Anki SM-2 • Глубокое усвоение каждого блока по 8 слов с письмом",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Document + Star badge icon matching screenshot
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.FactCheck,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = StatusLearningDark,
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom CTA Button: "Перейти в режим заучивания"
                Button(
                    onClick = {
                        applyStudyGoalAndReset(pendingGoal)
                        showGoalBottomSheet = false
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_learn_session_button")
                ) {
                    Text(
                        text = "Перейти в режим заучивания",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showCompletion) {
        AlertDialog(
            onDismissRequest = { showCompletion = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = s.greatJob,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                val denom = totalAttempted.coerceAtLeast(1)
                val pct = (correctCount * 100) / denom
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${s.scoreLabel}: $correctCount / $denom ($pct%)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Пройдено блоков по 8 карточек: $currentBlockNumber",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (wrongCardsInSession.isNotEmpty()) {
                        Text(
                            text = "${s.needsReviewTitle}: ${wrongCardsInSession.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            },
            confirmButton = {
                if (wrongCardsInSession.isNotEmpty()) {
                    Button(
                        onClick = {
                            val retryList = wrongCardsInSession.toList()
                            wrongCardsInSession.clear()
                            orderedDeck = retryList
                            missedQueue.clear()
                            val (firstBlock, cursor) = SpacedRepetitionEngine.buildNextBlockOf8(retryList, 0, emptyList())
                            currentBlockCards = firstBlock
                            nextUnseenCursor = cursor
                            currentBlockNumber = 1
                            blockSteps.clear()
                            blockSteps.addAll(SpacedRepetitionEngine.buildLearnStepsForBlock(firstBlock, selectedGoal))
                            currentStepIndex = 0
                            correctCount = 0
                            totalAttempted = 0
                            selectedAnswer = null
                            writtenInput = ""
                            isAnswerSubmitted = false
                            showCompletion = false
                        },
                        modifier = Modifier.testTag("repeat_unlearned_learn_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${s.studyUnlearnedBtn} (${wrongCardsInSession.size})")
                    }
                } else {
                    Button(
                        onClick = {
                            applyStudyGoalAndReset(selectedGoal)
                            correctCount = 0
                            totalAttempted = 0
                            showCompletion = false
                        }
                    ) {
                        Text(s.restartBtn)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("К модулю")
                }
            }
        )
    }
}

@Composable
private fun LearnSegmentedHeaderBar(
    completedInBlock: Int,
    totalStepsInBlock: Int,
    remainingTotalCards: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left green circle with completed count in current block
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$completedInBlock",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Segmented pills (like in screenshot)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val segmentsCount = 4
            for (seg in 0 until segmentsCount) {
                val segProgress = ((completedInBlock.toFloat() / totalStepsInBlock.coerceAtLeast(1)) * segmentsCount)
                val isFilled = seg < segProgress.toInt()
                val isCurrent = seg == segProgress.toInt()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                isFilled -> StatusMasteredDark
                                isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                )
            }
        }

        // Right circle/pill with remaining total count
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.height(36.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "$remainingTotalCards",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
