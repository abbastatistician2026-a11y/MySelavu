package com.myselavu.core.rules

import com.myselavu.core.period.BudgetPeriod
import java.time.LocalDate

/**
 * A value that applies from a period onwards ("effective from").
 * Every edit to salary, a fixed expense or a category allocation INSERTS a new row;
 * old rows are never changed, so history is never lost.
 *
 * [effectiveFrom] is the start date of the first budget period the value applies to.
 * [sequence] breaks ties when two rows share the same effectiveFrom (the later edit wins).
 */
data class Effective<T>(
    val effectiveFrom: LocalDate,
    val value: T,
    val sequence: Long = 0,
)

/** When an edit should start applying. */
enum class ApplyFrom { THIS_PERIOD, NEXT_PERIOD }

object EffectiveFrom {

    /** The value for the period starting on [periodStart]: the latest row with effectiveFrom ≤ periodStart. */
    fun <T> valueFor(rows: Iterable<Effective<T>>, periodStart: LocalDate): T? {
        var best: Effective<T>? = null
        for (row in rows) {
            if (row.effectiveFrom.isAfter(periodStart)) continue
            val b = best
            if (b == null ||
                row.effectiveFrom.isAfter(b.effectiveFrom) ||
                (row.effectiveFrom == b.effectiveFrom && row.sequence >= b.sequence)
            ) {
                best = row
            }
        }
        return best?.value
    }

    fun <T> valueFor(rows: Iterable<Effective<T>>, period: BudgetPeriod): T? = valueFor(rows, period.start)

    /** The effectiveFrom date to store for an edit made during [current]. */
    fun effectiveDateFor(applyFrom: ApplyFrom, current: BudgetPeriod): LocalDate = when (applyFrom) {
        ApplyFrom.THIS_PERIOD -> current.start
        ApplyFrom.NEXT_PERIOD -> current.end.plusDays(1)
    }
}
