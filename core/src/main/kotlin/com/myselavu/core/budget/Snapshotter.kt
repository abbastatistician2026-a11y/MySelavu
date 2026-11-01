package com.myselavu.core.budget

import com.myselavu.core.money.Money
import com.myselavu.core.period.BudgetPeriod
import com.myselavu.core.rules.EffectiveFrom

/** Builds the frozen snapshot of a period from the current "effective from" rules. */
object Snapshotter {

    fun snapshot(
        period: BudgetPeriod,
        rules: BudgetRules,
        carryIns: Map<Long, Money> = emptyMap(),
    ): PeriodSnapshot {
        val salary = EffectiveFrom.valueFor(rules.salary, period) ?: Money.ZERO

        val fixed = rules.fixedExpenses.entries
            .sortedBy { it.key }
            .mapNotNull { (id, rows) ->
                val rule = EffectiveFrom.valueFor(rows, period) ?: return@mapNotNull null
                if (!rule.active) return@mapNotNull null
                FixedSnapshot(id, rule.name, rule.amount, rule.dueDay)
            }

        val categories = rules.categoryRules.entries
            .sortedBy { it.key }
            .mapNotNull { (id, rows) ->
                val rule = EffectiveFrom.valueFor(rows, period) ?: return@mapNotNull null
                if (!rule.active) return@mapNotNull null
                CategorySnapshot(
                    categoryId = id,
                    type = rules.categoryTypes[id] ?: CategoryType.SPENDING,
                    allocation = rule.allocation,
                    carryIn = carryIns[id] ?: Money.ZERO,
                )
            }

        return PeriodSnapshot(period, salary, fixed, categories)
    }
}
