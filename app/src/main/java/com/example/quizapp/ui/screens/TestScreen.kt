package com.example.quizapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.quizapp.data.locale.LocalStrings
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.StudyModule
import com.example.ui.theme.StatusMasteredDark

data class TestQuestion(
    val card: Flashcard,
    val isTrueFalse: Boolean,
    val displayedDefinition: String,
    val correctBool: Boolean,
    val mcOptions: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    module: StudyModule,
    onBack: () -> Unit,
    onUpdateMastery: (String, Boolean) -> Unit = { _, _ -> }
) {
    val s = LocalStrings.current
    var isSubmitted by remember { mutableStateOf(false) }
    val userAnswers = remember { mutableStateMapOf<Int, String>() }

    val questions = remember(module.id, module.cards.size) {
        val allCards = module.cards
        val testSubset = if (allCards.size > 50) allCards.take(50) else allCards
        testSubset.mapIndexed { idx, card ->
            val isTf = idx % 2 == 0 && allCards.size > 1
            val showCorrectDef = idx % 3 != 0
            val otherIdx = (idx + 1) % allCards.size
            val otherDef = if (allCards.size > 1 && allCards[otherIdx].id != card.id) {
                allCards[otherIdx].definition
            } else {
                allCards.firstOrNull { it.id != card.id }?.definition ?: card.definition
            }
            val displayedDef = if (showCorrectDef) card.definition else otherDef

            val wrongDefs = mutableSetOf<String>()
            if (allCards.size <= 4) {
                allCards.filter { it.id != card.id }.forEach { wrongDefs.add(it.definition) }
            } else {
                var attempts = 0
                while (wrongDefs.size < 3 && attempts < 20) {
                    val candidate = allCards[kotlin.random.Random.nextInt(allCards.size)]
                    if (candidate.id != card.id && candidate.definition != card.definition) {
                        wrongDefs.add(candidate.definition)
                    }
                    attempts++
                }
            }
            val mcOpts = (wrongDefs.toList() + card.definition).shuffled()

            TestQuestion(
                card = card,
                isTrueFalse = isTf,
                displayedDefinition = displayedDef,
                correctBool = showCorrectDef,
                mcOptions = mcOpts
            )
        }
    }

    val scoreCount = if (isSubmitted) {
        questions.indices.count { i ->
            val q = questions[i]
            val ans = userAnswers[i]
            if (q.isTrueFalse) {
                (ans == "TRUE" && q.correctBool) || (ans == "FALSE" && !q.correctBool)
            } else {
                ans == q.card.definition
            }
        }
    } else 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${s.testMode}: ${module.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("close_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isSubmitted) {
                item {
                    val pct = if (questions.isEmpty()) 100 else (scoreCount * 100) / questions.size
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = s.testResultsTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${s.scoreLabel}: $scoreCount / ${questions.size} ($pct%)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            }

            itemsIndexed(questions) { idx, q ->
                val currentAns = userAnswers[idx]
                val isCorrect = if (q.isTrueFalse) {
                    (currentAns == "TRUE" && q.correctBool) || (currentAns == "FALSE" && !q.correctBool)
                } else {
                    currentAns == q.card.definition
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSubmitted) {
                            if (isCorrect) StatusMasteredDark else MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${idx + 1} / ${questions.size} • ${if (q.isTrueFalse) s.trueOrFalse else "Выбор ответа"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            if (isSubmitted) {
                                Icon(
                                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (isCorrect) StatusMasteredDark else MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Text(
                            text = q.card.term,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (q.isTrueFalse) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "→ ${q.displayedDefinition}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listOf("TRUE" to s.trueLabel, "FALSE" to s.falseLabel).forEach { (key, label) ->
                                    val selected = currentAns == key
                                    OutlinedButton(
                                        onClick = { if (!isSubmitted) userAnswers[idx] = key },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("test_tf_${idx}_$key"),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    ) {
                                        Text(
                                            text = label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            q.mcOptions.forEachIndexed { optIdx, opt ->
                                val selected = currentAns == opt
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isSubmitted) { userAnswers[idx] = opt }
                                        .testTag("test_mc_${idx}_$optIdx")
                                ) {
                                    Text(
                                        text = opt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                if (!isSubmitted) {
                    Button(
                        onClick = {
                            isSubmitted = true
                            questions.forEachIndexed { idx, q ->
                                val ans = userAnswers[idx]
                                val correct = if (q.isTrueFalse) {
                                    (ans == "TRUE" && q.correctBool) || (ans == "FALSE" && !q.correctBool)
                                } else {
                                    ans == q.card.definition
                                }
                                onUpdateMastery(q.card.id, correct)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("submit_test_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = s.submitTest,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            userAnswers.clear()
                            isSubmitted = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = s.restartBtn,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
