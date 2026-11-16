package com.example.quizapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.model.CardMasteryStatus
import com.example.quizapp.data.model.Flashcard
import com.example.ui.theme.StatusLearningDark
import com.example.ui.theme.StatusMasteredDark
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FlashcardView(
    card: Flashcard,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSpeak: (String) -> Unit,
    onLongPressSpeak: ((String) -> Unit)? = null,
    onToggleStar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    var showHint by remember(card.id) { mutableStateOf(false) }
    val dragOffsetX = remember { Animatable(0f) }
    var isSwipingOut by remember { mutableStateOf(false) }

    LaunchedEffect(card.id) {
        isSwipingOut = false
        dragOffsetX.snapTo(0f)
    }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "flipRotation"
    )

    val currentOffset = dragOffsetX.value
    val swipeRotationZ = (currentOffset / 28f).coerceIn(-12f, 12f)
    val swipeAlpha = (1f - (abs(currentOffset) / 900f)).coerceIn(0.15f, 1f)

    val borderStroke = when {
        currentOffset > 120f -> BorderStroke(2.5.dp, StatusMasteredDark)
        currentOffset < -120f -> BorderStroke(2.5.dp, StatusLearningDark)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    // Outer container handles drag/swipe physics (no rotationY here so front & back animate identically)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .pointerInput(card.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (isSwipingOut) return@detectHorizontalDragGestures
                        coroutineScope.launch {
                            val offsetNow = dragOffsetX.value
                            when {
                                offsetNow > 140f -> {
                                    isSwipingOut = true
                                    dragOffsetX.animateTo(
                                        targetValue = 900f,
                                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                                    )
                                    onSwipeRight()
                                }
                                offsetNow < -140f -> {
                                    isSwipingOut = true
                                    dragOffsetX.animateTo(
                                        targetValue = -900f,
                                        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                                    )
                                    onSwipeLeft()
                                }
                                else -> {
                                    dragOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        if (!isSwipingOut) {
                            coroutineScope.launch {
                                dragOffsetX.animateTo(0f, spring())
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!isSwipingOut) {
                            change.consume()
                            coroutineScope.launch {
                                dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                            }
                        }
                    }
                )
            }
            .offset { IntOffset(currentOffset.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = swipeRotationZ
                alpha = swipeAlpha
            }
    ) {
        // Inner Card handles only the 3D Y-axis flip
        Card(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 14f * density
                }
                .clickable(enabled = !isSwipingOut) { onFlip() }
                .testTag("flashcard_container"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            border = borderStroke
        ) {
            if (rotation <= 90f) {
                // FRONT SIDE (TERM)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Top Row: Term Badge + Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = s.termLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            val (statusText, statusColor) = when (card.status) {
                                CardMasteryStatus.MASTERED -> s.masteredLabel to StatusMasteredDark
                                CardMasteryStatus.LEARNING -> s.learningLabel to StatusLearningDark
                                CardMasteryStatus.NOT_STUDIED -> if (card.needsReview) {
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
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!card.exampleSentence.isNullOrBlank()) {
                                IconButton(
                                    onClick = { showHint = !showHint },
                                    modifier = Modifier.testTag("hint_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = "Hint",
                                        tint = if (showHint) StatusLearningDark else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (onToggleStar != null) {
                                IconButton(
                                    onClick = onToggleStar,
                                    modifier = Modifier.testTag("flashcard_star_button")
                                ) {
                                    Icon(
                                        imageVector = if (card.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = "Star",
                                        tint = if (card.isStarred) StatusLearningDark else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TtsSpeakButton(
                                onClick = { onSpeak(card.term) },
                                onLongClick = { onLongPressSpeak?.invoke(card.term) },
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescriptionText = "Speak Term",
                                modifier = Modifier.testTag("tts_front_button")
                            )
                        }
                    }

                    // Center Content: Diagram + Term
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (card.diagramType != null) {
                            DiagramIllustration(
                                diagramType = card.diagramType,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                        }

                        Text(
                            text = card.term,
                            style = if (card.diagramType != null) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.headlineMedium
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        if (showHint && !card.exampleSentence.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "💡 ${card.exampleSentence}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // Bottom Flip Hint
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = s.tapToFlip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // BACK SIDE (DEFINITION) - Mirrored horizontally to compensate for 180deg Y rotation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = s.definitionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onToggleStar != null) {
                                IconButton(
                                    onClick = onToggleStar,
                                    modifier = Modifier.testTag("flashcard_back_star_button")
                                ) {
                                    Icon(
                                        imageVector = if (card.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = "Star",
                                        tint = if (card.isStarred) StatusLearningDark else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TtsSpeakButton(
                                onClick = { onSpeak(card.definition) },
                                onLongClick = { onLongPressSpeak?.invoke(card.definition) },
                                tint = MaterialTheme.colorScheme.tertiary,
                                contentDescriptionText = "Speak Definition",
                                modifier = Modifier.testTag("tts_back_button")
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = card.definition,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        if (!card.exampleSentence.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "Пример: ${card.exampleSentence}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = s.tapToFlip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
