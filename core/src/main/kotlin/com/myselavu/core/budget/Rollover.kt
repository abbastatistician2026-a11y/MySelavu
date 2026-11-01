package com.myselavu.core.budget

import com.myselavu.core.money.Money
import com.myselavu.core.period.BudgetPeriod
import com.myselavu.core.period.PeriodCalendar
import java.time.LocalDate

/** The result of closing one budget month. */
data class CloseResult(
    val period: BudgetPeriod,
    val mode: UnspentMode,
    /** Money left at month end (salary + extra + carried in − fixed − spent). Can be negative. */
    val moneyLeft: Money,
    /** Per-category money carried into the same category next month (CARRY_FORWARD only). */
    val carryToNext: Map<Long, Money>,
    /** Amount added to the Savings pot ("Moved to savings"). Never negative. */
    val savingsDeposit: Money,
)

/** What has to be written to the database to bring the budget up to date. */
data class CatchUpPlan(
    val closed: List<CloseResult>,
    val created: List<PeriodSnapshot>,
) {
    val isEmpty: Boolean get() = closed.isEmpty() && created.isEmpty()
}

object Rollover {

    /**
     * Closes a month and works out what happens to the unspent money.
     *
     * Rules (never moves more money than was really left over):
     * - Unspent SAVINGS-category money always goes to the Savings pot.
     * - MOVE_TO_SAVINGS: all money left over goes to the Savings pot.
     * - CARRY_FORWARD: each SPENDING category's unspent money is added to the same category next month.
     * - RESET: SPENDING-category leftovers are not carried over.
     */
    fun close(snapshot: PeriodSnapshot, activity: PeriodActivity, mode: UnspentMode): CloseResult {
        val summary = PeriodSummary.compute(snapshot, activity, snapshot.period.end)
        val moneyLeft = summary.moneyLeft
        val cash = moneyLeft.coerceAtLeastZero()

        val savingsUnspent = Money(
            summary.categories
                .filter { it.type == CategoryType.SAVINGS }
                .sumOf { it.remaining.coerceAtLeastZero().paise }
        )

        return when (mode) {
            UnspentMode.MOVE_TO_SAVINGS -> CloseResult(snapshot.period, mode, moneyLeft, emptyMap(), cash)
            UnspentMode.RESET -> {
                val deposit = com.myselavu.core.money.minOf(savingsUnspent, cash)
                CloseResult(snapshot.period, mode, moneyLeft, emptyMap(), deposit)
            }
            UnspentMode.CARRY_FORWARD -> {
                val deposit = com.myselavu.core.money.minOf(savingsUnspent, cash)
                val cashForCarry = cash - deposit
                val wanted = summary.categories
                    .filter { it.type == CategoryType.SPENDING && it.remaining.isPositive }
                    .associate { it.categoryId to it.remaining }
                CloseResult(snapshot.period, mode, moneyLeft, scaleDown(wanted, cashForCarry), deposit)
            }
        }
    }

    /**
     * If categories want to carry more than the money actually left, shrink every carry
     * proportionally (rounding down to whole paise) so the total never exceeds [limit].
     */
    internal fun scaleDown(wanted: Map<Long, Money>, limit: Money): Map<Long, Money> {
        val total = wanted.values.sumOf { it.paise }
        if (total <= limit.paise) return wanted
        if (limit.paise <= 0) return emptyMap()
        val out = LinkedHashMap<Long, Money>()
        for ((id, amount) in wanted.entries.sortedBy { it.key }) {
            // amount * limit / total, computed without overflow for realistic values
            val scaled = java.math.BigInteger.valueOf(amount.paise)
                .multiply(java.math.BigInteger.valueOf(limit.paise))
                .divide(java.math.BigInteger.valueOf(total))
                .toLong()
            if (scaled > 0) out[id] = Money(scaled)
        }
        return out
    }

    /**
     * Brings the budget up to date, even if the app wasn't opened for months.
     * Closes the open period(s) that ended before [today] in order, applying [mode] to each,
     * and creates every missing period up to the one containing [today].
     *
     * @param latest the newest existing period snapshot, or null on first use.
     * @param activityFor actual spending/income for a period (empty for periods that never existed).
     */
    fun catchUp(
        latest: PeriodSnapshot?,
        today: LocalDate,
        startDay: Int,
        rules: BudgetRules,
        mode: UnspentMode,
        activityFor: (BudgetPeriod) -> PeriodActivity,
        maxPeriods: Int = 240,
    ): CatchUpPlan {
        val closed = ArrayList<CloseResult>()
        val created = ArrayList<PeriodSnapshot>()

        var current = latest ?: run {
            val first = Snapshotter.snapshot(PeriodCalendar.periodContaining(today, startDay), rules)
            created.add(first)
            first
        }

        var guard = 0
        while (today.isAfter(current.period.end)) {
            check(guard++ < maxPeriods) { "Too many missing periods" }
            // Newly created periods have no activity yet, except what activityFor knows
            // (e.g. backdated expenses), so always ask.
            val result = close(current, activityFor(current.period), mode)
            closed.add(result)
            val nextPeriod = PeriodCalendar.nextPeriod(current.period, startDay)
            val next = Snapshotter.snapshot(nextPeriod, rules, result.carryToNext)
            created.add(next)
            current = next
        }
        return CatchUpPlan(closed, created)
    }
}
