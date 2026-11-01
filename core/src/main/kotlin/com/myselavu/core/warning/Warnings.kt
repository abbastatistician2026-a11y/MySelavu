package com.myselavu.core.warning

import com.myselavu.core.money.Money

/** Shown with an icon AND a sentence, never by colour alone. */
enum class WarningLevel { GREEN, YELLOW, RED }

/** Why a level was chosen, so the UI can pick a friendly sentence. */
enum class WarningReason {
    ON_TRACK,
    SPENDING_A_BIT_FAST,
    PROJECTED_OVER,
    MOSTLY_USED_EARLY,
    OVERSPENT,
}

data class WarningResult(
    val level: WarningLevel,
    val reason: WarningReason,
    val usedPct: Double,
    val timePct: Double,
    val projected: Money,
    /** Allocation − spent (negative when overspent). */
    val remaining: Money,
    /** Days left including today. */
    val daysLeft: Int,
    /** remaining ÷ daysLeft, never below zero: "₹17/day left for 20 days". */
    val perDayLeft: Money,
)

/**
 * The warning colour logic, as a pure function.
 *
 * usedPct  = spent / allocated
 * timePct  = daysElapsed / daysInPeriod (today counts as elapsed), or the learned curve
 * projected = spent / timePct   (= spent / daysElapsed × daysInPeriod for the linear case)
 *
 * RED:    projected > allocated × 1.10, OR usedPct ≥ 0.90 with more than 3 days left, OR spent > allocated
 * GREEN:  usedPct ≤ timePct + 0.05 AND projected ≤ allocated
 * YELLOW: anything else (projected ≤ allocated × 1.10)
 */
object Warnings {
    const val GREEN_SLACK = 0.05
    const val RED_PROJECTION_FACTOR = 1.10
    const val RED_USED_PCT = 0.90
    const val RED_DAYS_LEFT_THRESHOLD = 3

    /**
     * @param expectedFraction optional learned "how much of the month's money is usually spent by now"
     *        (0..1). When null, the linear daysElapsed / daysInPeriod is used.
     */
    fun evaluate(
        spent: Money,
        allocated: Money,
        daysElapsed: Int,
        daysInPeriod: Int,
        expectedFraction: Double? = null,
    ): WarningResult {
        require(daysInPeriod > 0) { "daysInPeriod must be positive" }
        val elapsed = daysElapsed.coerceIn(1, daysInPeriod)
        val daysLeftAfterToday = daysInPeriod - elapsed
        val daysLeftInclToday = daysLeftAfterToday + 1
        val linear = elapsed.toDouble() / daysInPeriod
        val timePct = (expectedFraction ?: linear).coerceIn(0.0, 1.0).let { if (it <= 0.0) linear else it }

        val remaining = allocated - spent
        val perDay = remaining.coerceAtLeastZero().divFloor(daysLeftInclToday.toLong())

        // Projection: spent ÷ timePct, rounded to paise.
        val projected = Money(Math.round(spent.paise / timePct))

        if (allocated.paise <= 0) {
            val level = if (spent.isPositive) WarningLevel.RED else WarningLevel.GREEN
            val reason = if (spent.isPositive) WarningReason.OVERSPENT else WarningReason.ON_TRACK
            return WarningResult(level, reason, if (spent.isPositive) Double.POSITIVE_INFINITY else 0.0,
                timePct, projected, remaining, daysLeftInclToday, perDay)
        }

        val usedPct = spent.paise.toDouble() / allocated.paise
        val redProjectionLimit = allocated.paise * RED_PROJECTION_FACTOR

        val (level, reason) = when {
            spent > allocated -> WarningLevel.RED to WarningReason.OVERSPENT
            projected.paise > redProjectionLimit -> WarningLevel.RED to WarningReason.PROJECTED_OVER
            usedPct >= RED_USED_PCT && daysLeftAfterToday > RED_DAYS_LEFT_THRESHOLD ->
                WarningLevel.RED to WarningReason.MOSTLY_USED_EARLY
            usedPct <= timePct + GREEN_SLACK + 1e-9 && projected <= allocated ->
                WarningLevel.GREEN to WarningReason.ON_TRACK
            else -> WarningLevel.YELLOW to WarningReason.SPENDING_A_BIT_FAST
        }
        return WarningResult(level, reason, usedPct, timePct, projected, remaining, daysLeftInclToday, perDay)
    }

    /** The worst of several levels (for the month status chip). */
    fun worst(levels: Iterable<WarningLevel>): WarningLevel = levels.maxByOrNull { it.ordinal } ?: WarningLevel.GREEN
}
