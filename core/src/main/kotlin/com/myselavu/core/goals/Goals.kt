package com.myselavu.core.goals

import com.myselavu.core.money.Money
import java.time.LocalDate
import java.time.YearMonth

/** A savings goal such as "Emergency fund ₹50,000" or "New phone ₹20,000 by March". */
data class SavingsGoal(
    val target: Money,
    val saved: Money,
    /** Optional: the goal should be reached by the end of this date's month. */
    val deadline: LocalDate? = null,
) {
    val remaining: Money get() = (target - saved).coerceAtLeastZero()
    val isReached: Boolean get() = target.isPositive && saved >= target

    /** 0.0..1.0 for the progress ring. */
    val progress: Double
        get() = if (target.paise <= 0) 1.0 else (saved.paise.toDouble() / target.paise).coerceIn(0.0, 1.0)
}

object GoalMath {
    /**
     * Months left to save, counting the current month and the deadline month.
     * Today in November with a March deadline = Nov, Dec, Jan, Feb, Mar = 5. Never less than 1.
     */
    fun monthsLeft(today: LocalDate, deadline: LocalDate): Int {
        val from = YearMonth.from(today)
        val to = YearMonth.from(deadline)
        val months = (to.year - from.year) * 12 + (to.monthValue - from.monthValue) + 1
        return maxOf(1, months)
    }

    /**
     * "₹X per month needed to reach it on time", rounded UP to a whole rupee.
     * Null when there is no deadline. Zero when the goal is already reached.
     */
    fun perMonthNeeded(goal: SavingsGoal, today: LocalDate): Money? {
        val deadline = goal.deadline ?: return null
        val remaining = goal.remaining
        if (!remaining.isPositive) return Money.ZERO
        val months = monthsLeft(today, deadline).toLong()
        val perMonthPaise = remaining.divCeil(months).paise
        // round up to a whole rupee
        return Money(-Math.floorDiv(-perMonthPaise, 100L) * 100L)
    }
}

/** Running total of the Savings pot from its history entries (deposits +, withdrawals −). */
object SavingsPot {
    fun total(entries: List<Money>): Money = Money(entries.sumOf { it.paise })
}
