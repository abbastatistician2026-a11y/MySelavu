package com.myselavu.core.budget

import com.myselavu.core.money.Money
import com.myselavu.core.period.BudgetPeriod
import com.myselavu.core.rules.Effective

/** SPENDING categories get overspending warnings; SAVINGS categories don't. */
enum class CategoryType { SPENDING, SAVINGS }

/** What happens to unspent money when a budget month ends. Default: MOVE_TO_SAVINGS. */
enum class UnspentMode { RESET, CARRY_FORWARD, MOVE_TO_SAVINGS }

enum class PaymentMethod { UPI, CASH, DEBIT_CARD, CREDIT_CARD, OTHER;
    companion object { val DEFAULT = UPI }
}

/** One version of a fixed monthly expense (e.g. Rent). [active] = false means "deleted from here on". */
data class FixedExpenseRule(
    val name: String,
    val amount: Money,
    val dueDay: Int? = null,
    val active: Boolean = true,
)

/** One version of a category's monthly allocation. [active] = false means archived from here on. */
data class CategoryRule(
    val allocation: Money,
    val active: Boolean = true,
)

/** All "effective from" rules the budget engine needs to build a new month. */
data class BudgetRules(
    val salary: List<Effective<Money>>,
    val fixedExpenses: Map<Long, List<Effective<FixedExpenseRule>>> = emptyMap(),
    val categoryRules: Map<Long, List<Effective<CategoryRule>>> = emptyMap(),
    val categoryTypes: Map<Long, CategoryType> = emptyMap(),
)

data class FixedSnapshot(
    val fixedExpenseId: Long,
    val name: String,
    val amount: Money,
    val dueDay: Int?,
)

data class CategorySnapshot(
    val categoryId: Long,
    val type: CategoryType,
    /** This month's planned allocation (from the rules). */
    val allocation: Money,
    /** Money carried in from last month (only in CARRY_FORWARD mode). */
    val carryIn: Money = Money.ZERO,
)

/**
 * A frozen copy of salary, fixed expenses and allocations taken when a period starts.
 * Closed months are read from snapshots, so later rule edits never change them.
 */
data class PeriodSnapshot(
    val period: BudgetPeriod,
    val salary: Money,
    val fixedExpenses: List<FixedSnapshot>,
    val categories: List<CategorySnapshot>,
) {
    val totalFixed: Money get() = Money(fixedExpenses.sumOf { it.amount.paise })
    val totalAllocated: Money get() = Money(categories.sumOf { it.allocation.paise })
    val totalCarryIn: Money get() = Money(categories.sumOf { it.carryIn.paise })
    fun category(id: Long): CategorySnapshot? = categories.firstOrNull { it.categoryId == id }
}

/** "Move ₹500 from Personal to Food" within one month. */
data class CategoryTransfer(val fromCategoryId: Long, val toCategoryId: Long, val amount: Money) {
    init { require(amount.isPositive) { "Transfer amount must be positive" } }
}

/** What actually happened in a period: spending, extra income and moves between categories. */
data class PeriodActivity(
    val spentByCategory: Map<Long, Money> = emptyMap(),
    val extraIncome: Money = Money.ZERO,
    val transfers: List<CategoryTransfer> = emptyList(),
) {
    val totalSpent: Money get() = Money(spentByCategory.values.sumOf { it.paise })
    fun spent(categoryId: Long): Money = spentByCategory[categoryId] ?: Money.ZERO

    companion object { val EMPTY = PeriodActivity() }
}
