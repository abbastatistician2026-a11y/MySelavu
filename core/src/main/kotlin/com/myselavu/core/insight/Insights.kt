package com.myselavu.core.insight

import com.myselavu.core.money.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.min

object UnusualExpense {
    const val FACTOR = 3L
    /** Need at least this many earlier expenses in the category before judging. */
    const val MIN_SAMPLES = 5

    /** Median in paise (average of the two middle values for an even count, rounded down). */
    fun median(amounts: List<Money>): Money? {
        if (amounts.isEmpty()) return null
        val sorted = amounts.map { it.paise }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) Money(sorted[mid]) else Money((sorted[mid - 1] + sorted[mid]) / 2)
    }

    /** True when [amount] is more than 3× the median of the category's earlier expenses. */
    fun isUnusual(amount: Money, previousInCategory: List<Money>): Boolean {
        if (previousInCategory.size < MIN_SAMPLES) return false
        val median = median(previousInCategory) ?: return false
        if (median.paise <= 0) return false
        return amount.paise > median.paise * FACTOR
    }
}

object NoSpendStreak {
    /**
     * Consecutive days, counting back from [today], with no SPENDING-category expense.
     * If today already has spending the streak is 0. Days before [trackingStarted] don't count.
     */
    fun current(spendingDays: Set<LocalDate>, today: LocalDate, trackingStarted: LocalDate): Int {
        var count = 0
        var d = today
        while (!d.isBefore(trackingStarted)) {
            if (d in spendingDays) break
            count++
            d = d.minusDays(1)
        }
        return count
    }

    /** Number of days in [from]..[to] (inclusive) with no spending. */
    fun countNoSpendDays(spendingDays: Set<LocalDate>, from: LocalDate, to: LocalDate): Int {
        var count = 0
        var d = from
        while (!d.isAfter(to)) {
            if (d !in spendingDays) count++
            d = d.plusDays(1)
        }
        return count
    }
}

/**
 * Smart category suggestions for the add-expense sheet: most-used first, boosted by
 * what you usually log at this time of day and by recent use.
 */
object CategorySuggestions {
    data class Use(val categoryId: Long, val at: LocalDateTime)

    const val TIME_WINDOW_MINUTES = 90
    const val RECENT_DAYS = 14

    fun rank(history: List<Use>, now: LocalDateTime, allCategoryIds: List<Long>): List<Long> {
        val scores = HashMap<Long, Double>()
        val nowMinutes = now.hour * 60 + now.minute
        for (use in history) {
            var score = 1.0
            val m = use.at.hour * 60 + use.at.minute
            val diff = abs(nowMinutes - m).let { min(it, 24 * 60 - it) }
            if (diff <= TIME_WINDOW_MINUTES) score += 2.0
            val ageDays = ChronoUnit.DAYS.between(use.at.toLocalDate(), now.toLocalDate())
            if (ageDays in 0..RECENT_DAYS) score += 1.0
            scores[use.categoryId] = (scores[use.categoryId] ?: 0.0) + score
        }
        val order = allCategoryIds.withIndex().associate { it.value to it.index }
        return allCategoryIds.sortedWith(
            compareByDescending<Long> { scores[it] ?: 0.0 }.thenBy { order[it] ?: Int.MAX_VALUE }
        )
    }
}

/** Month-over-month change in plain numbers. */
object MonthChange {
    /** Percentage change from [previous] to [current], rounded to a whole number; null if previous is 0. */
    fun percent(previous: Money, current: Money): Int? {
        if (previous.paise == 0L) return null
        val change = (current.paise - previous.paise).toDouble() / previous.paise * 100.0
        return Math.round(change).toInt()
    }
}
