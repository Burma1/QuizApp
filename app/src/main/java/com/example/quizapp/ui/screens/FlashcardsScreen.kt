package com.example.quizapp.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.quizapp.data.algorithm.SpacedRepetitionEngine
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.LearnStudyGoal
import com.example.quizapp.data.model.StudyModule
import com.example.quizapp.ui.components.FlashcardView
import com.example.quizapp.ui.components.HeaderCounters
import com.example.ui.theme.StatusLearningDark
import com.example.ui.theme.StatusMasteredDark
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsScreen(
    module: StudyModule,
    customCards: List<Flashcard>? = null,
    onBack: () -> Unit,
    onUpdateMastery: (String, Boolean) -> Unit,
    onToggleStar: (String) -> Unit = {},
    recentTtsLanguages: List<String> = emptyList(),
    onAddRecentTtsLanguage: (String) -> Unit = {},
    onClearRecentTtsLanguages: () -> Unit = {}
) {
    val s = LocalStrings.current
    val context = LocalContext.current

    // Full ordered deck for this session
    var allOrderedCards by remember(module.id, customCards) {
        val base = if (!customCards.isNullOrEmpty()) customCards else module.cards
        mutableStateOf(SpacedRepetitionEngine.orderCardsForGoal(base, LearnStudyGoal.MEMORIZE_ALL))
    }

    // Queue of missed cards to re-appear in subsequent blocks of 8
    val missedQueue = remember { mutableStateListOf<Flashcard>() }

    // Initial block of up to 8 cards
    val initialBlockPair = remember(allOrderedCards) {
        SpacedRepetitionEngine.buildNextBlockOf8(allOrderedCards, 0, emptyList())
    }

    var currentBlockCards by remember(allOrderedCards) { mutableStateOf(initialBlockPair.first) }
    var nextUnseenCursor by remember(allOrderedCards) { mutableIntStateOf(initialBlockPair.second) }
    var currentBlockNumber by remember(allOrderedCards) { mutableIntStateOf(1) }

    var currentIndex by remember { mutableIntStateOf(0) }
    val stepFlipMap = remember { mutableStateMapOf<String, Boolean>() }
    var lastTransitionDir by remember { mutableIntStateOf(1) }
    val activeCardId = currentBlockCards.getOrNull(currentIndex)?.id
    val currentStepKey = "${currentBlockNumber}_${currentIndex}_${activeCardId.orEmpty()}"
    val isFlipped = stepFlipMap[currentStepKey] ?: false
    var hasBeenOpened by remember(currentStepKey) { mutableStateOf(false) }
    var stillLearningCount by remember { mutableIntStateOf(0) }
    var knowCount by remember { mutableIntStateOf(0) }
    var isAutoPlaying by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }

    // Track cards marked "Still learning" across the whole session
    val missedCardsInSession = remember { mutableStateListOf<Flashcard>() }
    // History stack of (card, wasKnown) inside the current block for undo
    val historyStack = remember { mutableStateListOf<Pair<Flashcard, Boolean>>() }

    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsPickerTargetText by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    val speakCardText: (String) -> Unit = { text ->
        com.example.quizapp.ui.components.SmartTtsHelper.speakSmart(ttsEngine, text)
    }

    // Advances to the next block of 8 cards (including missed cards from previous blocks)
    val advanceToNextBlockOrFinish: () -> Unit = {
        if (nextUnseenCursor < allOrderedCards.size || missedQueue.isNotEmpty()) {
            val (nextBlock, newCursor) = SpacedRepetitionEngine.buildNextBlockOf8(
                allOrderedCards = allOrderedCards,
                startIndex = nextUnseenCursor,
                missedQueue = missedQueue.toList()
            )
            // Remove the cards we just pulled into the new block from missedQueue
            val pulledIds = nextBlock.map { it.id }.toSet()
            missedQueue.removeAll { it.id in pulledIds }

            if (nextBlock.isNotEmpty()) {
                currentBlockCards = nextBlock
                nextUnseenCursor = newCursor
                currentBlockNumber++
                currentIndex = 0
                lastTransitionDir = 1
                hasBeenOpened = false
                historyStack.clear()
            } else {
                isAutoPlaying = false
                showCompletionDialog = true
            }
        } else {
            isAutoPlaying = false
            showCompletionDialog = true
        }
    }

    LaunchedEffect(isAutoPlaying, currentStepKey, isFlipped) {
        if (isAutoPlaying && currentBlockCards.isNotEmpty() && !showCompletionDialog) {
            delay(2200)
            val activeCard = currentBlockCards[currentIndex]
            if (!isFlipped) {
                stepFlipMap[currentStepKey] = true
                hasBeenOpened = true
                speakCardText(activeCard.definition)
            } else {
                lastTransitionDir = 1
                hasBeenOpened = false
                if (currentIndex < currentBlockCards.size - 1) {
                    currentIndex++
                    val nextCard = currentBlockCards[currentIndex]
                    speakCardText(nextCard.term)
                } else {
                    advanceToNextBlockOrFinish()
                }
            }
        }
    }

    val handleCardDecision: (Boolean) -> Unit = { known ->
        if (currentBlockCards.isNotEmpty()) {
            val currentCard = currentBlockCards[currentIndex]
            historyStack.add(currentCard to known)
            if (known) {
                knowCount++
                missedCardsInSession.removeAll { it.id == currentCard.id }
                missedQueue.removeAll { it.id == currentCard.id }
            } else {
                stillLearningCount++
                if (missedCardsInSession.none { it.id == currentCard.id }) {
                    missedCardsInSession.add(currentCard)
                }
                if (missedQueue.none { it.id == currentCard.id }) {
                    missedQueue.add(currentCard)
                }
            }
            onUpdateMastery(currentCard.id, known)
            lastTransitionDir = if (known) 1 else -1
            hasBeenOpened = false
            if (currentIndex < currentBlockCards.size - 1) {
                currentIndex++
            } else {
                advanceToNextBlockOrFinish()
            }
        }
    }

    val totalBlocksEstimate = ((allOrderedCards.size + SpacedRepetitionEngine.BLOCK_SIZE - 1) / SpacedRepetitionEngine.BLOCK_SIZE).coerceAtLeast(1)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = module.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Блок $currentBlockNumber • по ${SpacedRepetitionEngine.BLOCK_SIZE} карточек (Anki SM-2)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("close_flashcards_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            allOrderedCards = allOrderedCards.shuffled()
                            missedQueue.clear()
                            val (firstBlock, cursor) = SpacedRepetitionEngine.buildNextBlockOf8(allOrderedCards, 0, emptyList())
                            currentBlockCards = firstBlock
                            nextUnseenCursor = cursor
                            currentBlockNumber = 1
                            currentIndex = 0
                            stepFlipMap.clear()
                            lastTransitionDir = 1
                            hasBeenOpened = false
                            historyStack.clear()
                        },
                        modifier = Modifier.testTag("shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = s.shuffleCards,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { isAutoPlaying = !isAutoPlaying },
                        modifier = Modifier.testTag("autoplay_button")
                    ) {
                        Icon(
                            imageVector = if (isAutoPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = s.autoPlay,
                            tint = if (isAutoPlaying) StatusLearningDark else MaterialTheme.colorScheme.primary
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
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Segmented 8-card block progress bar
            SegmentedBlockProgressBar(
                currentStepInBlock = currentIndex,
                totalStepsInBlock = currentBlockCards.size.coerceAtLeast(1),
                blockNumber = currentBlockNumber,
                totalBlocksEstimate = totalBlocksEstimate,
                remainingInDeck = (allOrderedCards.size - nextUnseenCursor).coerceAtLeast(0) + missedQueue.size
            )

            HeaderCounters(
                stillLearningCount = stillLearningCount,
                knowCount = knowCount,
                currentIndex = currentIndex,
                totalCount = currentBlockCards.size.coerceAtLeast(1)
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (currentBlockCards.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentStepKey,
                        transitionSpec = {
                            val dir = if (lastTransitionDir >= 0) 1 else -1
                            (slideInHorizontally(animationSpec = tween(220)) { width -> width / 3 * dir } +
                                fadeIn(animationSpec = tween(200)))
                                .togetherWith(
                                    slideOutHorizontally(animationSpec = tween(180)) { width -> -width / 4 * dir } +
                                        fadeOut(animationSpec = tween(160))
                                )
                        },
                        label = "flashcard_page_transition"
                    ) { stepKey ->
                        val parts = stepKey.split("_", limit = 3)
                        val cardIdInStep = parts.getOrNull(2).orEmpty()
                        val cardInStep = module.cards.find { it.id == cardIdInStep }
                            ?: currentBlockCards.find { it.id == cardIdInStep }
                            ?: currentBlockCards[currentIndex]
                        val stepIsFlipped = stepFlipMap[stepKey] ?: false
                        FlashcardView(
                            card = cardInStep,
                            isFlipped = stepIsFlipped,
                            onFlip = {
                                val nextState = !(stepFlipMap[stepKey] ?: false)
                                stepFlipMap[stepKey] = nextState
                                if (nextState) {
                                    hasBeenOpened = true
                                }
                            },
                            onSwipeLeft = { handleCardDecision(false) },
                            onSwipeRight = { handleCardDecision(true) },
                            onSpeak = { text -> speakCardText(text) },
                            onLongPressSpeak = { text -> ttsPickerTargetText = text },
                            onToggleStar = { onToggleStar(cardInStep.id) }
                        )
                    }
                }
            }

            // Bottom Action Bar: Still Learning (Left), Undo (Center), Know (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { handleCardDecision(false) },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("still_learning_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = s.stillLearning,
                        tint = StatusLearningDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = s.stillLearning,
                        color = StatusLearningDark,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        if (historyStack.isNotEmpty() && currentIndex > 0) {
                            val (prevCard, lastKnown) = historyStack.removeAt(historyStack.lastIndex)
                            lastTransitionDir = -1
                            currentIndex--
                            val restoredKey = "${currentBlockNumber}_${currentIndex}_${prevCard.id}"
                            stepFlipMap[restoredKey] = false
                            hasBeenOpened = false
                            if (lastKnown && knowCount > 0) knowCount--
                            if (!lastKnown && stillLearningCount > 0) {
                                stillLearningCount--
                                missedQueue.removeAll { it.id == prevCard.id }
                            }
                        }
                    },
                    enabled = historyStack.isNotEmpty() && currentIndex > 0,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("undo_card_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo"
                    )
                }

                Button(
                    onClick = { handleCardDecision(true) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusMasteredDark
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("know_card_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = s.knowCard,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = s.knowCard,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showCompletionDialog) {
        AlertDialog(
            onDismissRequest = { showCompletionDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = s.greatJob,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${s.knowCard}: $knowCount  •  ${s.stillLearning}: $stillLearningCount",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val totalReviewed = (knowCount + stillLearningCount).coerceAtLeast(1)
                    val percentage = (knowCount * 100) / totalReviewed
                    LinearProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = StatusMasteredDark,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(
                        text = "Пройдено блоков по 8 карточек: $currentBlockNumber • ${s.scoreLabel}: $percentage%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                if (missedCardsInSession.isNotEmpty()) {
                    Button(
                        onClick = {
                            allOrderedCards = missedCardsInSession.toList()
                            missedCardsInSession.clear()
                            missedQueue.clear()
                            historyStack.clear()
                            val (firstBlock, cursor) = SpacedRepetitionEngine.buildNextBlockOf8(allOrderedCards, 0, emptyList())
                            currentBlockCards = firstBlock
                            nextUnseenCursor = cursor
                            currentBlockNumber = 1
                            currentIndex = 0
                            knowCount = 0
                            stillLearningCount = 0
                            stepFlipMap.clear()
                            lastTransitionDir = 1
                            showCompletionDialog = false
                        },
                        modifier = Modifier.testTag("repeat_unlearned_flashcards_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${s.studyUnlearnedBtn} (${missedCardsInSession.size})")
                    }
                } else {
                    Button(
                        onClick = {
                            allOrderedCards = SpacedRepetitionEngine.orderCardsForGoal(module.cards, LearnStudyGoal.MEMORIZE_ALL)
                            missedQueue.clear()
                            historyStack.clear()
                            val (firstBlock, cursor) = SpacedRepetitionEngine.buildNextBlockOf8(allOrderedCards, 0, emptyList())
                            currentBlockCards = firstBlock
                            nextUnseenCursor = cursor
                            currentBlockNumber = 1
                            currentIndex = 0
                            knowCount = 0
                            stillLearningCount = 0
                            stepFlipMap.clear()
                            lastTransitionDir = 1
                            showCompletionDialog = false
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
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

    val pickerText = ttsPickerTargetText
    if (pickerText != null) {
        com.example.quizapp.ui.components.TtsLanguagePickerSheet(
            textToSpeak = pickerText,
            ttsEngine = ttsEngine,
            recentLanguageTags = recentTtsLanguages,
            onSelectLanguage = { option ->
                com.example.quizapp.ui.components.SmartTtsHelper.speakWithLocale(
                    tts = ttsEngine,
                    text = pickerText,
                    targetLocale = option.locale
                )
                onAddRecentTtsLanguage(option.languageTag)
                ttsPickerTargetText = null
            },
            onClearRecent = onClearRecentTtsLanguages,
            onDismiss = { ttsPickerTargetText = null }
        )
    }
}

@Composable
fun SegmentedBlockProgressBar(
    currentStepInBlock: Int,
    totalStepsInBlock: Int,
    blockNumber: Int,
    totalBlocksEstimate: Int,
    remainingInDeck: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left circle badge: current step in block
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${currentStepInBlock + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Segmented pills for the block (up to 8 segments)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val segments = totalStepsInBlock.coerceIn(1, 16)
                for (i in 0 until segments) {
                    val isDone = i < currentStepInBlock
                    val isCurrent = i == currentStepInBlock
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    isDone -> StatusMasteredDark
                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                    )
                }
            }

            // Right circle badge: remaining cards after this block + in current block
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.height(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    val totalLeft = (totalStepsInBlock - currentStepInBlock) + remainingInDeck
                    Text(
                        text = "$totalLeft",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
