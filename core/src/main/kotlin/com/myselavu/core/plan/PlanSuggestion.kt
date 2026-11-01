package com.myselavu.core.plan

import com.myselavu.core.budget.CategoryType
import com.myselavu.core.money.Money

/**
 * "Suggested plan": proposes next month's category allocations from past spending.
 *
 * - SPENDING categories with history: average of the last [MAX_MONTHS] finished months
 *   plus a 10% cushion, rounded up to ₹100.
 * - Categories without history keep their current allocation.
 * - SAVINGS categories keep their allocation, and any money that would otherwise stay
 *   unplanned is suggested for the first SAVINGS category.
 * - If the plan needs more than is available, spending suggestions are scaled down to fit.
 */
object PlanSuggestion {
    const val MAX_MONTHS = 3
    const val CUSHION_PERCENT = 10L
    private const val ROUND_TO_PAISE = 100_00L // ₹100

    data class Input(
        val categoryId: Long,
        val type: CategoryType,
        val currentAllocation: Money,
        /** Spending in finished months, newest first. */
        val pastSpends: List<Money>,
    )

    enum class Reason { NO_HISTORY, BASED_ON_AVERAGE, SCALED_TO_FIT, SAVINGS_KEPT, SAVINGS_EXTRA }

    data class Suggestion(
        val categoryId: Long,
        val current: Money,
        val suggested: Money,
        val averageSpent: Money?,
        val reason: Reason,
    )

    data class Plan(val suggestions: List<Suggestion>, val available: Money) {
        val totalSuggested: Money get() = Money(suggestions.sumOf { it.suggested.paise })
        val unplanned: Money get() = available - totalSuggested
        val hasHistory: Boolean get() = suggestions.any { it.averageSpent != null }
    }

    fun roundUpTo100(m: Money): Money {
        if (m.paise <= 0) return Money.ZERO
        return Money(-Math.floorDiv(-m.paise, ROUND_TO_PAISE) * ROUND_TO_PAISE)
    }

    private fun roundDownTo100(m: Money): Money = Money(Math.floorDiv(m.paise, ROUND_TO_PAISE) * ROUND_TO_PAISE)

    /** @param available money for categories = salary + usual extra income − fixed expenses. */
    fun suggest(inputs: List<Input>, available: Money): Plan {
        val out = ArrayList<Suggestion>()
        for (input in inputs) {
            if (input.type == CategoryType.SAVINGS) {
                out += Suggestion(input.categoryId, input.currentAllocation, input.currentAllocation, null, Reason.SAVINGS_KEPT)
                continue
            }
            val history = input.pastSpends.take(MAX_MONTHS)
            if (history.isEmpty()) {
                out += Suggestion(input.categoryId, input.currentAllocation, input.currentAllocation, null, Reason.NO_HISTORY)
            } else {
                val avg = Money(history.sumOf { it.paise } / history.size)
                val withCushion = Money(avg.paise + avg.paise * CUSHION_PERCENT / 100)
                out += Suggestion(input.categoryId, input.currentAllocation, roundUpTo100(withCushion), avg, Reason.BASED_ON_AVERAGE)
            }
        }

        val savingsTotal = out.filter { it.reason == Reason.SAVINGS_KEPT }.sumOf { it.suggested.paise }
        val spendingTotal = out.filter { it.reason != Reason.SAVINGS_KEPT }.sumOf { it.suggested.paise }
        val roomForSpending = available.paise - savingsTotal

        if (spendingTotal > roomForSpending && spendingTotal > 0) {
            // Scale spending suggestions down so the plan fits.
            val limit = maxOf(0L, roomForSpending)
            for (i in out.indices) {
                val s = out[i]
                if (s.reason == Reason.SAVINGS_KEPT) continue
                val scaled = java.math.BigInteger.valueOf(s.suggested.paise)
                    .multiply(java.math.BigInteger.valueOf(limit))
                    .divide(java.math.BigInteger.valueOf(spendingTotal)).toLong()
                out[i] = s.copy(suggested = roundDownTo100(Money(scaled)), reason = Reason.SCALED_TO_FIT)
            }
        } else {
            val leftover = available.paise - savingsTotal - spendingTotal
            val firstSavings = out.indexOfFirst { it.reason == Reason.SAVINGS_KEPT }
            if (leftover > 0 && firstSavings >= 0) {
                val s = out[firstSavings]
                val extra = roundDownTo100(Money(leftover))
                if (extra.isPositive) out[firstSavings] = s.copy(suggested = s.suggested + extra, reason = Reason.SAVINGS_EXTRA)
            }
        }
        return Plan(out, available)
    }
}
