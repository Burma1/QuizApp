package com.example.quizapp.data.algorithm

import com.example.quizapp.data.model.AnkiGrade
import com.example.quizapp.data.model.CardMasteryStatus
import com.example.quizapp.data.model.Flashcard
import com.example.quizapp.data.model.LearnStudyGoal
import kotlin.math.max
import kotlin.math.roundToInt

enum class LearnQuestionType {
    MULTIPLE_CHOICE,
    WRITTEN
}

data class LearnStepItem(
    val card: Flashcard,
    val questionType: LearnQuestionType,
    val isRetryInBlock: Boolean = false
)

/**
 * Anki SM-2 & Block-of-8 Adaptive Study Engine.
 *
 * Implements:
 * 1. Anki SM-2 calculation (Ease Factor, Interval Days, Repetitions, Lapses, Next Review Timestamp).
 * 2. Block-by-8 chunking for both Flashcards and Learn modes.
 * 3. Missed-card re-queueing across subsequent blocks so mistaken cards are repeated until mastered.
 * 4. Two-stage Learn block generation:
 *    - Stage 1: Multiple-choice recognition (тест-выбор) for the cards in the 8-card block.
 *    - Stage 2: Written recall (ввод на клавиатуре) for a subset of those tested cards.
 */
object SpacedRepetitionEngine {

    const val BLOCK_SIZE = 8
    private const val MIN_EASE_FACTOR = 1.3f
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val MINUTE_MS = 60 * 1000L

    /**
     * Updates a card according to the Anki SM-2 spaced repetition algorithm.
     */
    fun applyAnkiReview(
        card: Flashcard,
        grade: AnkiGrade,
        nowMs: Long = System.currentTimeMillis()
    ): Flashcard {
        val q = grade.quality
        return if (q < 3) {
            // Card forgotten / Again -> reset repetitions, reduce easeFactor, schedule immediate re-learning
            val newEase = max(MIN_EASE_FACTOR, card.easeFactor - 0.20f)
            card.copy(
                masteryScore = 0,
                isMastered = false,
                needsReview = true,
                reviewCount = card.reviewCount + 1,
                lastStudiedTimestamp = nowMs,
                easeFactor = newEase,
                intervalDays = 0,
                repetitions = 0,
                lapses = card.lapses + 1,
                nextReviewTimestamp = nowMs + 10 * MINUTE_MS
            )
        } else {
            // Successful recall (HARD = 3, GOOD = 4, EASY = 5)
            val newReps = card.repetitions + 1
            val newInterval = when (newReps) {
                1 -> if (grade == AnkiGrade.EASY) 3 else 1
                2 -> if (grade == AnkiGrade.EASY) 6 else 4
                else -> {
                    val multiplier = when (grade) {
                        AnkiGrade.HARD -> 1.2f
                        AnkiGrade.GOOD -> card.easeFactor
                        AnkiGrade.EASY -> card.easeFactor * 1.3f
                        AnkiGrade.AGAIN -> 1.0f
                    }
                    max(1, (card.intervalDays.coerceAtLeast(1) * multiplier).roundToInt())
                }
            }
            val efDelta = (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
            val newEase = max(MIN_EASE_FACTOR, card.easeFactor + efDelta)
            val scoreIncrement = if (grade == AnkiGrade.EASY) 2 else 1
            val newScore = (card.masteryScore + scoreIncrement).coerceAtMost(2)

            card.copy(
                masteryScore = newScore,
                isMastered = newScore >= 2,
                needsReview = false,
                reviewCount = card.reviewCount + 1,
                lastStudiedTimestamp = nowMs,
                easeFactor = newEase,
                intervalDays = newInterval,
                repetitions = newReps,
                nextReviewTimestamp = nowMs + newInterval * DAY_MS
            )
        }
    }

    /**
     * Prioritizes cards for study according to the selected LearnStudyGoal and Anki SM-2 state:
     * - EXAM_PREP: Prioritizes cards with lapses/needsReview, then NOT_STUDIED, then LEARNING, then lowest easeFactor.
     * - MEMORIZE_ALL: Orders all cards systematically starting with NOT_STUDIED -> LEARNING -> due reviews -> MASTERED.
     */
    fun orderCardsForGoal(cards: List<Flashcard>, goal: LearnStudyGoal): List<Flashcard> {
        if (cards.isEmpty()) return emptyList()
        return when (goal) {
            LearnStudyGoal.EXAM_PREP -> {
                cards.sortedWith(
                    compareByDescending<Flashcard> { it.needsReview }
                        .thenByDescending { it.lapses }
                        .thenBy { it.masteryScore }
                        .thenBy { it.easeFactor }
                )
            }
            LearnStudyGoal.MEMORIZE_ALL -> {
                cards.sortedWith(
                    compareBy<Flashcard> {
                        when (it.status) {
                            CardMasteryStatus.NOT_STUDIED -> 0
                            CardMasteryStatus.LEARNING -> 1
                            CardMasteryStatus.MASTERED -> 2
                        }
                    }.thenByDescending { it.needsReview }
                        .thenBy { it.nextReviewTimestamp }
                )
            }
        }
    }

    /**
     * Builds the next block of up to 8 cards, combining:
     * 1. Cards missed in previous blocks (so mistaken cards are guaranteed to be shown again),
     * 2. Remaining unseen cards from the deck.
     *
     * Returns Pair(blockCards, newCursorIndex).
     */
    fun buildNextBlockOf8(
        allOrderedCards: List<Flashcard>,
        startIndex: Int,
        missedQueue: List<Flashcard>,
        blockSize: Int = BLOCK_SIZE
    ): Pair<List<Flashcard>, Int> {
        val block = mutableListOf<Flashcard>()

        // 1. Pull missed cards from previous block(s) first (up to half of block or all if no unseen cards remain)
        val unseenRemaining = (allOrderedCards.size - startIndex).coerceAtLeast(0)
        val maxMissedToTake = if (unseenRemaining > 0) {
            minOf(missedQueue.size, blockSize / 2).coerceAtLeast(minOf(missedQueue.size, 3))
        } else {
            minOf(missedQueue.size, blockSize)
        }

        val takenMissed = missedQueue.take(maxMissedToTake)
        block.addAll(takenMissed)

        // 2. Fill the rest of the 8-card block with the next unseen cards
        var cursor = startIndex
        while (block.size < blockSize && cursor < allOrderedCards.size) {
            val candidate = allOrderedCards[cursor]
            if (block.none { it.id == candidate.id }) {
                block.add(candidate)
            }
            cursor++
        }

        // 3. If still under blockSize and there are more missed cards, add them
        if (block.size < blockSize && missedQueue.size > takenMissed.size) {
            for (extra in missedQueue.drop(takenMissed.size)) {
                if (block.size >= blockSize) break
                if (block.none { it.id == extra.id }) {
                    block.add(extra)
                }
            }
        }

        return block to cursor
    }

    /**
     * Builds the initial sequence of LearnStepItems for an 8-card block:
     * Phase 1: All cards in the block are tested via Multiple Choice (тест-выбор).
     * Phase 2: A subset of cards from this block are then tested via Written keyboard input (письменный ввод)
     *          right after the multiple choice questions of the block!
     *
     * Goal customization:
     * - EXAM_PREP: ~Half of the block (3 to 4 cards) are selected for written reinforcement.
     * - MEMORIZE_ALL: Up to 5-6 cards (or all cards in small blocks) are selected for written reinforcement.
     */
    fun buildLearnStepsForBlock(
        blockCards: List<Flashcard>,
        goal: LearnStudyGoal
    ): List<LearnStepItem> {
        if (blockCards.isEmpty()) return emptyList()
        val steps = mutableListOf<LearnStepItem>()

        // 1. Multiple Choice phase for the block
        blockCards.forEach { card ->
            steps.add(
                LearnStepItem(
                    card = card,
                    questionType = LearnQuestionType.MULTIPLE_CHOICE
                )
            )
        }

        // 2. Written input phase for a subset of cards that were just in the test
        val writtenCount = when (goal) {
            LearnStudyGoal.EXAM_PREP -> (blockCards.size / 2).coerceIn(1, 4)
            LearnStudyGoal.MEMORIZE_ALL -> ((blockCards.size * 2) / 3).coerceAtLeast(1).coerceAtMost(blockCards.size)
        }

        // Prioritize cards that need more practice or shorter terms suitable for typing
        val writtenCandidates = blockCards
            .sortedWith(
                compareByDescending<Flashcard> { it.needsReview || it.lapses > 0 }
                    .thenBy { it.masteryScore }
                    .thenBy { it.term.length }
            )
            .take(writtenCount)

        writtenCandidates.forEach { card ->
            steps.add(
                LearnStepItem(
                    card = card,
                    questionType = LearnQuestionType.WRITTEN
                )
            )
        }

        return steps
    }
}
