package com.myselavu.app

import com.myselavu.app.data.CategoryMonth
import com.myselavu.app.data.MonthSummary
import com.myselavu.app.data.db.BudgetPeriodEntity
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.CategorySum
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.PeriodCategoryEntity
import com.myselavu.app.data.db.PeriodFixedExpenseEntity
import com.myselavu.app.data.db.QuickTemplateEntity
import com.myselavu.app.data.settings.AppSettings
import com.myselavu.app.ui.history.HistoryFilter
import com.myselavu.app.ui.history.RangePreset
import com.myselavu.app.ui.history.buildHistory
import com.myselavu.app.ui.home.DashboardState
import com.myselavu.app.ui.home.buildDashboard
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.money.Money
import com.myselavu.core.period.BudgetPeriod
import java.time.LocalDate

/** Fixed sample data for screenshots (the same engine code as the real app). */
object SampleScreens {
    val today: LocalDate = LocalDate.of(2026, 11, 18)
    private val start = LocalDate.of(2026, 11, 1)
    private val end = LocalDate.of(2026, 11, 30)
    private fun r(x: Long) = x * 100

    val categories = listOf(
        CategoryEntity(1, "Food", "🍛", 0xFFE07A2E, CategoryType.SPENDING, 0, false, 0),
        CategoryEntity(2, "Travel", "🛺", 0xFF2E86AB, CategoryType.SPENDING, 1, false, 0),
        CategoryEntity(3, "Personal", "🛍️", 0xFF8E5BB5, CategoryType.SPENDING, 2, false, 0),
        CategoryEntity(4, "Savings", "🏦", 0xFF2E7D32, CategoryType.SAVINGS, 3, false, 0),
    )
    val byId = categories.associateBy { it.id }

    val templates = listOf(
        QuickTemplateEntity(1, "Tea", r(15), 1, method = PaymentMethod.CASH),
        QuickTemplateEntity(2, "Bus", r(30), 2, method = PaymentMethod.CASH),
    )

    val expenses = listOf(
        ExpenseEntity(1, r(120), 1, null, PaymentMethod.UPI, "Lunch", today, 5),
        ExpenseEntity(2, r(15), 1, null, PaymentMethod.CASH, "Tea", today, 4),
        ExpenseEntity(3, r(60), 2, null, PaymentMethod.UPI, "Auto", today.minusDays(1), 3),
        ExpenseEntity(4, r(1450), 1, null, PaymentMethod.DEBIT_CARD, "Groceries", today.minusDays(2), 2),
        ExpenseEntity(5, r(799), 3, null, PaymentMethod.UPI, "Shirt", today.minusDays(4), 1),
    )

    fun dashboard(hide: Boolean = false): DashboardState = buildDashboard(
        period = BudgetPeriodEntity(start, end, r(55000)),
        categoryRows = listOf(
            PeriodCategoryEntity(start, 1, CategoryType.SPENDING, r(8000)),
            PeriodCategoryEntity(start, 2, CategoryType.SPENDING, r(3000)),
            PeriodCategoryEntity(start, 3, CategoryType.SPENDING, r(4000)),
            PeriodCategoryEntity(start, 4, CategoryType.SAVINGS, r(12000)),
        ),
        fixedRows = listOf(
            PeriodFixedExpenseEntity(start, 1, "Rent", r(14000), 5, paid = true),
            PeriodFixedExpenseEntity(start, 2, "Family fund", r(8000), 1, paid = true),
            PeriodFixedExpenseEntity(start, 3, "EB bill", r(1500), 22),
        ),
        transfers = emptyList(),
        sums = listOf(CategorySum(1, r(4560)), CategorySum(2, r(2700)), CategorySum(3, r(1200))),
        extraIncome = r(2000),
        spendingDays = emptyList(),
        categories = categories,
        recent = expenses.take(4),
        history = emptyMap(),
        firstStart = start.minusMonths(2),
        settings = AppSettings(setupDone = true, hideAmounts = hide),
        today = today,
    ).let { it.copy(streak = 0) }

    fun history() = buildHistory(expenses, HistoryFilter(start, end, RangePreset.THIS_MONTH), byId)

    val months: List<MonthSummary> = listOf(
        MonthSummary(BudgetPeriod(start, end), false, Money(r(55000)), Money(r(2000)), Money(r(23500)), Money(r(27000)), Money.ZERO,
            Money(r(8460)), Money.ZERO, listOf(CategoryMonth(1, Money(r(8000)), Money(r(4560))), CategoryMonth(2, Money(r(3000)), Money(r(2700))),
                CategoryMonth(3, Money(r(4000)), Money(r(1200))))),
        MonthSummary(BudgetPeriod(start.minusMonths(1), start.minusDays(1)), true, Money(r(55000)), Money(r(5000)), Money(r(23500)),
            Money(r(27000)), Money.ZERO, Money(r(16950)), Money(r(19550)),
            listOf(CategoryMonth(1, Money(r(8000)), Money(r(8900))), CategoryMonth(2, Money(r(3000)), Money(r(2850))),
                CategoryMonth(3, Money(r(4000)), Money(r(2200))), CategoryMonth(4, Money(r(12000)), Money(r(3000))))),
        MonthSummary(BudgetPeriod(start.minusMonths(2), start.minusMonths(1).minusDays(1)), true, Money(r(52000)), Money.ZERO, Money(r(23500)),
            Money(r(27000)), Money.ZERO, Money(r(14100)), Money(r(14400)),
            listOf(CategoryMonth(1, Money(r(8000)), Money(r(7300))), CategoryMonth(2, Money(r(3000)), Money(r(3400))),
                CategoryMonth(3, Money(r(4000)), Money(r(3400))))),
    )
}
