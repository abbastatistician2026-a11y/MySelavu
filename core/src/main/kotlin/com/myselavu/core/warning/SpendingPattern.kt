package com.myselavu.core.warning

import com.myselavu.core.money.Money

/**
 * Pattern learning: how a category's spending usually builds up during a month.
 *
 * Once at least [MIN_CLOSED_MONTHS] closed months with spending exist, the warning logic uses the
 * category's average cumulative spending curve instead of a straight line. With less data it
 * falls back to linear (returns null).
 */
object SpendingPattern {
    const val MIN_CLOSED_MONTHS = 2

    /** The learned value is never allowed to drop below this share of the linear value. */
    const val FLOOR_OF_LINEAR = 0.5

    /** One closed month for a category: its length and the amount spent on each day (index 0 = day 1). */
    data class ClosedMonth(val dailySpend: List<Money>) {
        val daysInPeriod: Int get() = dailySpend.size
        val total: Long get() = dailySpend.sumOf { it.paise }

        /** Share of the month's total spent by the end of [day] (1-based). */
        fun cumulativeShareAtDay(day: Int): Double {
            val t = total
            if (t <= 0) return 0.0
            var sum = 0L
            for (i in 0 until day.coerceIn(0, dailySpend.size)) sum += dailySpend[i].paise
            return sum.toDouble() / t
        }

        /**
         * Share spent by the time a fraction [x] (0..1) of the month has passed,
         * interpolating between days so months of different length can be compared.
         */
        fun cumulativeShareAt(x: Double): Double {
            val n = daysInPeriod
            if (n == 0) return 0.0
            val position = x.coerceIn(0.0, 1.0) * n
            val whole = position.toInt().coerceAtMost(n)
            val frac = position - whole
            val a = cumulativeShareAtDay(whole)
            if (whole >= n || frac == 0.0) return a
            val b = cumulativeShareAtDay(whole + 1)
            return a + (b - a) * frac
        }
    }

    /**
     * The expected fraction of the month's allocation spent by the end of [daysElapsed]
     * (out of [daysInPeriod]), or null when there isn't enough history (use linear).
     */
    fun expectedFraction(history: List<ClosedMonth>, daysElapsed: Int, daysInPeriod: Int): Double? {
        val usable = history.filter { it.total > 0 && it.daysInPeriod > 0 }
        if (usable.size < MIN_CLOSED_MONTHS) return null
        require(daysInPeriod > 0)
        val x = daysElapsed.coerceIn(0, daysInPeriod).toDouble() / daysInPeriod
        val average = usable.map { it.cumulativeShareAt(x) }.average()
        val linear = x
        // Avoid "you'll spend 50× your budget" panics early in the month when history shows
        // little spending at the start: never go below half of the straight-line value.
        return maxOf(average, linear * FLOOR_OF_LINEAR).coerceIn(0.0, 1.0)
    }
}
