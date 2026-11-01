package com.myselavu.app.ui.home

import com.myselavu.app.data.db.BudgetPeriodEntity
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.CategorySum
import com.myselavu.app.data.db.CategoryTransferEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.PeriodCategoryEntity
import com.myselavu.app.data.db.PeriodFixedExpenseEntity
import com.myselavu.app.data.settings.AppSettings
import com.myselavu.core.budget.CategorySnapshot
import com.myselavu.core.budget.CategoryTransfer
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.FixedSnapshot
import com.myselavu.core.budget.PeriodActivity
import com.myselavu.core.budget.PeriodSnapshot
import com.myselavu.core.budget.PeriodSummary
import com.myselavu.core.insight.NoSpendStreak
import com.myselavu.core.money.Money
import com.myselavu.core.period.BudgetPeriod
import com.myselavu.core.recurring.FixedDueDate
import com.myselavu.core.warning.SpendingPattern
import com.myselavu.core.warning.WarningResult
import com.myselavu.core.warning.Warnings
import java.time.LocalDate

data class CategoryCard(
    val category: CategoryEntity,
    /** This month's planned allocation (before carry-in and moves). */
    val allocation: Money,
    val available: Money,
    val spent: Money,
    val remaining: Money,
    /** Null for SAVINGS categories (no overspending warnings). */
    val warning: WarningResult?,
    val usesLearnedPattern: Boolean,
)

data class BillRow(val row: PeriodFixedExpenseEntity, val dueDate: LocalDate?, val dueSoon: Boolean)

data class DashboardState(
    val period: BudgetPeriod,
    val today: LocalDate,
    val salary: Money,
    val extraIncome: Money,
    val totalFixed: Money,
    val moneyLeft: Money,
    val unplanned: Money,
    val safeToSpend: Money,
    val spendingSpent: Money,
    val spendingAvailable: Money,
    val totalSpent: Money,
    val daysLeft: Int,
    val overall: WarningResult,
    val cards: List<CategoryCard>,
    val bills: List<BillRow>,
    val recent: List<ExpenseEntity>,
    val categoriesById: Map<Long, CategoryEntity>,
    val streak: Int,
    val hideAmounts: Boolean,
)

/** Pure function: turns database rows into everything the dashboard shows. */
fun buildDashboard(
    period: BudgetPeriodEntity,
    categoryRows: List<PeriodCategoryEntity>,
    fixedRows: List<PeriodFixedExpenseEntity>,
    transfers: List<CategoryTransferEntity>,
    sums: List<CategorySum>,
    extraIncome: Long,
    spendingDays: List<Long>,
    categories: List<CategoryEntity>,
    recent: List<ExpenseEntity>,
    history: Map<Long, List<SpendingPattern.ClosedMonth>>,
    firstStart: LocalDate?,
    settings: AppSettings,
    today: LocalDate,
): DashboardState {
    val p = BudgetPeriod(period.startDate, period.endDate)
    val byId = categories.associateBy { it.id }
    val snapshot = PeriodSnapshot(
        period = p,
        salary = Money(period.salaryPaise),
        fixedExpenses = fixedRows.map { FixedSnapshot(it.fixedExpenseId, it.name, Money(it.amountPaise), it.dueDay) },
        categories = categoryRows.map { CategorySnapshot(it.categoryId, it.type, Money(it.allocationPaise), Money(it.carryInPaise)) },
    )
    val activity = PeriodActivity(
        spentByCategory = sums.associate { it.categoryId to Money(it.totalPaise) },
        extraIncome = Money(extraIncome),
        transfers = transfers.map { CategoryTransfer(it.fromCategoryId, it.toCategoryId, Money(it.amountPaise)) },
    )
    val summary = PeriodSummary.compute(snapshot, activity, today)
    val elapsed = p.daysElapsed(today).coerceAtLeast(1)
    val length = p.lengthInDays

    val cards = summary.categories
        .mapNotNull { status -> byId[status.categoryId]?.let { it to status } }
        .sortedWith(compareBy({ it.first.sortOrder }, { it.first.id }))
        .map { (cat, status) ->
            val learned = SpendingPattern.expectedFraction(history[cat.id].orEmpty(), elapsed, length)
            val warning = if (status.type == CategoryType.SAVINGS) null
            else Warnings.evaluate(status.spent, status.available, elapsed, length, learned)
            CategoryCard(cat, status.allocation, status.available, status.spent, status.remaining, warning, learned != null)
        }

    val overall = Warnings.evaluate(summary.spendingSpent, summary.spendingAvailable, elapsed, length)

    val bills = fixedRows.map { row ->
        val due = row.dueDay?.let { FixedDueDate.inPeriod(it, p.start, p.end) }
        val soon = !row.paid && due != null && !due.isBefore(today) && !due.isAfter(today.plusDays(7))
        BillRow(row, due, soon)
    }.sortedWith(compareBy<BillRow>({ it.row.paid }, { it.dueDate ?: LocalDate.MAX }))

    val streak = NoSpendStreak.current(
        spendingDays.map(LocalDate::ofEpochDay).toSet(), today, firstStart ?: p.start,
    )

    return DashboardState(
        period = p, today = today,
        salary = summary.salary, extraIncome = summary.extraIncome, totalFixed = summary.totalFixed,
        moneyLeft = summary.moneyLeft, unplanned = summary.unplannedMoney, safeToSpend = summary.safeToSpendToday,
        spendingSpent = summary.spendingSpent, spendingAvailable = summary.spendingAvailable,
        totalSpent = summary.totalSpent, daysLeft = summary.daysLeftIncludingToday,
        overall = overall, cards = cards, bills = bills, recent = recent, categoriesById = byId,
        streak = streak, hideAmounts = settings.hideAmounts,
    )
}
