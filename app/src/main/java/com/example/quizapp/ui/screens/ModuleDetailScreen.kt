package com.example.quizapp.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.model.CardMasteryStatus
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.StudyModule
import com.example.ui.theme.StatusLearningDark
import com.example.ui.theme.StatusMasteredDark
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(
    module: StudyModule,
    onBack: () -> Unit,
    onOpenFlashcards: (List<Flashcard>?) -> Unit,
    onOpenLearn: (List<Flashcard>?) -> Unit,
    onOpenTest: (List<Flashcard>?) -> Unit,
    onOpenExport: () -> Unit = {},
    onToggleStar: (String) -> Unit = {},
    onDeleteModule: () -> Unit = {},
    onAddCard: (term: String, definition: String, example: String?) -> Unit = { _, _, _ -> },
    onUpdateCard: (cardId: String, term: String, definition: String, example: String?) -> Unit = { _, _, _, _ -> },
    onDeleteCard: (cardId: String) -> Unit = {},
    onUpdateModuleInfo: (title: String, description: String, category: String) -> Unit = { _, _, _ -> },
    recentTtsLanguages: List<String> = emptyList(),
    onAddRecentTtsLanguage: (String) -> Unit = {},
    onClearRecentTtsLanguages: () -> Unit = {}
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsPickerTargetText by remember { mutableStateOf<String?>(null) }

    // 0: All, 1: Not Studied, 2: Learning, 3: Mastered, 4: Starred
    var selectedFilter by remember { mutableIntStateOf(0) }
    var sortByStatus by remember { mutableStateOf(false) }

    // Dialog states
    var showAddTermDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var showEditModuleDialog by remember { mutableStateOf(false) }

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

    val previewCards = remember(module.cards) { module.cards.take(8) }
    val pagerState = rememberPagerState(pageCount = { previewCards.size.coerceAtLeast(1) })
    val starredCards = remember(module.cards) { module.cards.filter { it.isStarred } }

    val filteredCards = remember(module.cards, selectedFilter, sortByStatus) {
        val base = when (selectedFilter) {
            1 -> module.cards.filter { it.status == CardMasteryStatus.NOT_STUDIED }
            2 -> module.cards.filter { it.status == CardMasteryStatus.LEARNING }
            3 -> module.cards.filter { it.status == CardMasteryStatus.MASTERED }
            4 -> module.cards.filter { it.isStarred }
            else -> module.cards
        }
        if (sortByStatus) {
            base.sortedBy { it.masteryScore }
        } else {
            base
        }
    }

    val unmasteredCards = remember(module.cards) {
        module.cards.filter { it.status != CardMasteryStatus.MASTERED }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button")
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
                        onClick = { showAddTermDialog = true },
                        modifier = Modifier.testTag("topbar_add_term_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Добавить термин",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onOpenExport,
                        modifier = Modifier.testTag("export_module_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = s.exportModuleBtn,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Flashcard Preview Carousel (MD3 themed)
            if (previewCards.isNotEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            pageSpacing = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) { page ->
                            val card = previewCards[page]
                            var flipped by remember(card.id) { mutableStateOf(false) }
                            val flipRotation by animateFloatAsState(
                                targetValue = if (flipped) 180f else 0f,
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                                label = "previewCardFlip"
                            )

                            ElevatedCard(
                                onClick = { flipped = !flipped },
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationY = flipRotation
                                        cameraDistance = 12f * density
                                    }
                                    .testTag("preview_card_$page")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (flipRotation > 90f) rotationY = 180f
                                        }
                                        .padding(24.dp)
                                ) {
                                    val isShowingTerm = flipRotation <= 90f
                                    val displayedText = if (isShowingTerm) card.term else card.definition

                                    com.example.quizapp.ui.components.TtsSpeakButton(
                                        onClick = {
                                            com.example.quizapp.ui.components.SmartTtsHelper.speakSmart(
                                                ttsEngine,
                                                displayedText
                                            )
                                        },
                                        onLongClick = {
                                            ttsPickerTargetText = displayedText
                                        },
                                        tint = MaterialTheme.colorScheme.primary,
                                        contentDescriptionText = "Speak",
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .testTag("preview_card_tts_$page")
                                    )

                                    Text(
                                        text = displayedText,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.Center)
                                    )

                                    IconButton(
                                        onClick = { onOpenFlashcards(null) },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = "Fullscreen",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Pager Dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(previewCards.size) { index ->
                                val active = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .size(if (active) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // 2. Module Title & Author Info
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = module.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { showEditModuleDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("edit_module_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Редактировать модуль",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = onDeleteModule,
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("delete_module_detail_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Удалить карточки",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Удалить",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = module.author.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(
                            text = module.author,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (module.isVerified) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "|  ${module.cards.size} ${s.cardsCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Educational Study Modes ONLY
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudyModeMd3Row(
                        icon = Icons.Default.Style,
                        title = s.flashcardsMode,
                        onClick = { onOpenFlashcards(null) },
                        testTag = "mode_flashcards_button"
                    )
                    StudyModeMd3Row(
                        icon = Icons.Default.School,
                        title = s.learnMode,
                        onClick = { onOpenLearn(null) },
                        testTag = "mode_learn_button"
                    )
                    StudyModeMd3Row(
                        icon = Icons.Default.Assignment,
                        title = s.testMode,
                        onClick = { onOpenTest(null) },
                        testTag = "mode_test_button"
                    )

                    if (starredCards.isNotEmpty()) {
                        OutlinedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = StatusLearningDark.copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, StatusLearningDark.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("starred_practice_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = StatusLearningDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Учить отмеченные (${starredCards.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { onOpenFlashcards(starredCards) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("starred_flashcards_btn")
                                    ) {
                                        Text(
                                            text = s.flashcardsMode,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onOpenLearn(starredCards) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("starred_learn_btn")
                                    ) {
                                        Text(
                                            text = s.learnMode,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { onOpenTest(starredCards) },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("starred_test_btn")
                                    ) {
                                        Text(
                                            text = s.testMode,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. "Ваш прогресс" Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = s.yourProgressTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = s.yourProgressDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val totalCount = module.cards.size.coerceAtLeast(1)

                    MasteryProgressRowCard(
                        count = module.notStudiedCount,
                        total = totalCount,
                        label = s.notStudiedLabel,
                        arcColor = MaterialTheme.colorScheme.outline,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        onClick = {
                            val subset = module.cards.filter { it.status == CardMasteryStatus.NOT_STUDIED }
                            if (subset.isNotEmpty()) {
                                onOpenFlashcards(subset)
                            } else {
                                selectedFilter = 1
                            }
                        },
                        testTag = "progress_row_not_studied"
                    )

                    MasteryProgressRowCard(
                        count = module.learningCount,
                        total = totalCount,
                        label = s.learningLabel,
                        arcColor = StatusLearningDark,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        onClick = {
                            val subset = module.cards.filter { it.status == CardMasteryStatus.LEARNING }
                            if (subset.isNotEmpty()) {
                                onOpenLearn(subset)
                            } else {
                                selectedFilter = 2
                            }
                        },
                        testTag = "progress_row_learning"
                    )

                    MasteryProgressRowCard(
                        count = module.masteredCount,
                        total = totalCount,
                        label = s.masteredLabel,
                        arcColor = StatusMasteredDark,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        onClick = {
                            selectedFilter = 3
                        },
                        testTag = "progress_row_mastered"
                    )

                    if (unmasteredCards.isNotEmpty() && (module.learningCount > 0 || module.needsReviewCount > 0)) {
                        Button(
                            onClick = { onOpenLearn(unmasteredCards) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("study_unlearned_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${s.studyUnlearnedBtn} (${unmasteredCards.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 5. "Термины" Header + Prominent Add Term Button + Filter Chips + Sort
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.termsInModule,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        TextButton(
                            onClick = { sortByStatus = !sortByStatus },
                            modifier = Modifier.testTag("sort_terms_button")
                        ) {
                            Text(
                                text = if (sortByStatus) s.byStatusOrder else s.originalOrder,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Prominent button to add a new term directly in this module
                    FilledTonalButton(
                        onClick = { showAddTermDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_term_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Добавить термин в модуль",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chips = listOf(
                            0 to "${s.allTermsTab} (${module.cards.size})",
                            4 to "⭐ Избранные (${starredCards.size})",
                            1 to "${s.notStudiedLabel} (${module.notStudiedCount})",
                            2 to "${s.learningLabel} (${module.learningCount})",
                            3 to "${s.masteredLabel} (${module.masteredCount})"
                        )
                        items(chips, key = { it.first }) { (idx, label) ->
                            FilterChip(
                                selected = selectedFilter == idx,
                                onClick = { selectedFilter = idx },
                                label = {
                                    Text(
                                        text = label,
                                        fontWeight = if (selectedFilter == idx) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier.testTag("terms_filter_chip_$idx")
                            )
                        }
                    }
                }
            }

            // 6. Terms Cards List
            if (filteredCards.isEmpty()) {
                item {
                    OutlinedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (module.cards.isEmpty()) "В этом модуле пока нет терминов" else "Нет терминов с выбранным фильтром",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredCards, key = { it.id }) { card ->
                    val fullPhrase = "${card.term} — ${card.definition}"
                    TermItemMd3Card(
                        card = card,
                        onSpeak = {
                            com.example.quizapp.ui.components.SmartTtsHelper.speakSmart(
                                ttsEngine,
                                fullPhrase
                            )
                        },
                        onLongPressSpeak = {
                            ttsPickerTargetText = fullPhrase
                        },
                        onToggleStar = { onToggleStar(card.id) },
                        onEdit = { editingCard = card }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Add Term Dialog
    if (showAddTermDialog) {
        TermEditorDialog(
            title = "Новый термин",
            initialTerm = "",
            initialDefinition = "",
            initialExample = "",
            onDismiss = { showAddTermDialog = false },
            onSave = { term, def, ex ->
                onAddCard(term, def, ex)
                showAddTermDialog = false
            }
        )
    }

    // Edit Existing Term Dialog
    editingCard?.let { cardToEdit ->
        TermEditorDialog(
            title = "Редактировать термин",
            initialTerm = cardToEdit.term,
            initialDefinition = cardToEdit.definition,
            initialExample = cardToEdit.exampleSentence.orEmpty(),
            onDismiss = { editingCard = null },
            onSave = { term, def, ex ->
                onUpdateCard(cardToEdit.id, term, def, ex)
                editingCard = null
            },
            onDelete = {
                onDeleteCard(cardToEdit.id)
                editingCard = null
            }
        )
    }

    // Edit Module Info Dialog
    if (showEditModuleDialog) {
        ModuleInfoEditorDialog(
            initialTitle = module.title,
            initialDescription = module.description,
            initialCategory = module.category,
            onDismiss = { showEditModuleDialog = false },
            onSave = { newTitle, newDesc, newCat ->
                onUpdateModuleInfo(newTitle, newDesc, newCat)
                showEditModuleDialog = false
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
private fun TermEditorDialog(
    title: String,
    initialTerm: String,
    initialDefinition: String,
    initialExample: String,
    onDismiss: () -> Unit,
    onSave: (term: String, definition: String, example: String?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var termText by remember(initialTerm) { mutableStateOf(initialTerm) }
    var defText by remember(initialDefinition) { mutableStateOf(initialDefinition) }
    var exampleText by remember(initialExample) { mutableStateOf(initialExample) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = termText,
                    onValueChange = { termText = it },
                    label = { Text("Термин") },
                    placeholder = { Text("Например: Photosynthesis") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_term_input")
                )
                OutlinedTextField(
                    value = defText,
                    onValueChange = { defText = it },
                    label = { Text("Определение / перевод") },
                    placeholder = { Text("Например: Процесс образования органики на свету") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_definition_input")
                )
                OutlinedTextField(
                    value = exampleText,
                    onValueChange = { exampleText = it },
                    label = { Text("Пример (необязательно)") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_example_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(termText, defText, exampleText.ifBlank { null }) },
                enabled = termText.isNotBlank() && defText.isNotBlank(),
                modifier = Modifier.testTag("dialog_save_term_button")
            ) {
                Text("Сохранить", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("dialog_delete_term_button")
                    ) {
                        Text("Удалить")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}

@Composable
private fun ModuleInfoEditorDialog(
    initialTitle: String,
    initialDescription: String,
    initialCategory: String,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, category: String) -> Unit
) {
    var titleText by remember(initialTitle) { mutableStateOf(initialTitle) }
    var descText by remember(initialDescription) { mutableStateOf(initialDescription) }
    var catText by remember(initialCategory) { mutableStateOf(initialCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Редактировать модуль", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = catText,
                    onValueChange = { catText = it },
                    label = { Text("Категория / Папка") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("Описание") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(titleText, descText, catText) },
                enabled = titleText.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun StudyModeMd3Row(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MasteryProgressRowCard(
    count: Int,
    total: Int,
    label: String,
    arcColor: Color,
    trackColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val sweepFraction = (count.toFloat() / total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp)
                ) {
                    Canvas(modifier = Modifier.size(42.dp)) {
                        val strokeWidth = 4.dp.toPx()
                        drawArc(
                            color = trackColor,
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        if (sweepFraction > 0f) {
                            drawArc(
                                color = arcColor,
                                startAngle = 135f,
                                sweepAngle = 270f * sweepFraction,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TermItemMd3Card(
    card: Flashcard,
    onSpeak: () -> Unit,
    onLongPressSpeak: () -> Unit,
    onToggleStar: () -> Unit,
    onEdit: () -> Unit
) {
    val s = LocalStrings.current
    val (statusLabel, statusColor) = when (card.status) {
        CardMasteryStatus.MASTERED -> s.masteredLabel to StatusMasteredDark
        CardMasteryStatus.LEARNING -> s.learningLabel to StatusLearningDark
        CardMasteryStatus.NOT_STUDIED -> if (card.needsReview) {
            s.needsReviewLabel to MaterialTheme.colorScheme.error
        } else {
            s.notStudiedLabel to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    OutlinedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("term_card_${card.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = card.term,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = card.definition,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("edit_term_button_${card.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Редактировать термин",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    com.example.quizapp.ui.components.TtsSpeakButton(
                        onClick = onSpeak,
                        onLongClick = onLongPressSpeak,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescriptionText = "Speak",
                        iconSize = 20.dp,
                        modifier = Modifier.testTag("term_tts_button_${card.id}")
                    )
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("star_button_${card.id}")
                    ) {
                        Icon(
                            imageVector = if (card.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Star",
                            tint = if (card.isStarred) StatusLearningDark else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!card.exampleSentence.isNullOrBlank()) {
                Text(
                    text = "💡 ${card.exampleSentence}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
