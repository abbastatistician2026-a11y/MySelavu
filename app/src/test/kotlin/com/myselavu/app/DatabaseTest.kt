package com.myselavu.app

import com.myselavu.app.data.db.AppDatabase
import com.myselavu.app.data.db.BudgetPeriodEntity
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.CategoryRuleEntity
import com.myselavu.app.data.db.DebtEntity
import com.myselavu.app.data.db.DebtRepaymentEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.PeriodCategoryEntity
import com.myselavu.app.data.db.SalaryRuleEntity
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.debts.DebtDirection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/** Checks the Room schema works end to end (tables, converters, queries). */
@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = AppDatabase.inMemory(RuntimeEnvironment.getApplication()) }
    @After fun tearDown() { db.close() }

    @Test fun salaryRulesAndPeriods() = runBlocking {
        val rules = db.rulesDao()
        rules.insertSalaryRule(SalaryRuleEntity(effectiveFrom = LocalDate.of(2026, 11, 1), amountPaise = 5_000_000, createdAt = 1))
        rules.insertSalaryRule(SalaryRuleEntity(effectiveFrom = LocalDate.of(2026, 12, 1), amountPaise = 5_500_000, createdAt = 2))
        assertEquals(listOf(5_000_000L, 5_500_000L), rules.salaryRules().map { it.amountPaise })

        val periods = db.periodDao()
        val start = LocalDate.of(2026, 11, 1)
        periods.insertPeriod(BudgetPeriodEntity(start, LocalDate.of(2026, 11, 30), 5_000_000))
        assertEquals(start, periods.periodContaining(LocalDate.of(2026, 11, 18))?.startDate)
        assertEquals(null, periods.periodContaining(LocalDate.of(2026, 12, 18)))
    }

    @Test fun expensesSumByCategoryAndMethod() = runBlocking {
        val rules = db.rulesDao()
        val food = rules.insertCategory(CategoryEntity(name = "Food", emoji = "🍛", color = 0xFFE07A2E, createdAt = 1))
        val travel = rules.insertCategory(CategoryEntity(name = "Travel", emoji = "🛺", color = 0xFF2E86AB, createdAt = 1))
        rules.insertCategoryRule(CategoryRuleEntity(categoryId = food, effectiveFrom = LocalDate.of(2026, 11, 1), allocationPaise = 800_000, createdAt = 1))
        val start = LocalDate.of(2026, 11, 1)
        db.periodDao().insertPeriod(BudgetPeriodEntity(start, LocalDate.of(2026, 11, 30), 5_000_000))
        db.periodDao().insertPeriodCategories(listOf(PeriodCategoryEntity(start, food, CategoryType.SPENDING, 800_000)))

        val e = db.expenseDao()
        e.insert(ExpenseEntity(amountPaise = 1500, categoryId = food, method = PaymentMethod.CASH, note = "Tea", date = LocalDate.of(2026, 11, 2), createdAt = 1))
        e.insert(ExpenseEntity(amountPaise = 4500, categoryId = food, note = "Lunch", date = LocalDate.of(2026, 11, 3), createdAt = 2))
        e.insert(ExpenseEntity(amountPaise = 12000, categoryId = travel, date = LocalDate.of(2026, 11, 3), createdAt = 3))
        e.insert(ExpenseEntity(amountPaise = 99900, categoryId = travel, date = LocalDate.of(2026, 12, 1), createdAt = 4))

        val sums = e.sumsByCategory(start, LocalDate.of(2026, 11, 30)).associate { it.categoryId to it.totalPaise }
        assertEquals(mapOf(food to 6000L, travel to 12000L), sums)
        val byMethod = e.sumsByMethod(start, LocalDate.of(2026, 11, 30)).associate { it.method to it.totalPaise }
        assertEquals(1500L, byMethod[PaymentMethod.CASH])
        assertEquals(16500L, byMethod[PaymentMethod.UPI])
        assertEquals(1, e.search("tea").first().size)
        assertEquals(2, e.search("Travel").first().size)
        assertEquals(1, e.search("120").first().size)
        assertNotNull(e.lastCreated())
    }

    @Test fun debtsWithRepayments() = runBlocking {
        val p = db.planningDao()
        val id = p.insertDebt(DebtEntity(direction = DebtDirection.LENT, person = "Arun", amountPaise = 50000, date = LocalDate.of(2026, 11, 1), createdAt = 1))
        p.insertRepayment(DebtRepaymentEntity(debtId = id, date = LocalDate.of(2026, 11, 5), amountPaise = 20000, createdAt = 2))
        assertEquals(1, p.observeRepayments().first().size)
        p.deleteDebt(p.observeDebts().first().first())
        // Repayments are removed with their debt (cascade)
        assertEquals(0, p.observeRepayments().first().size)
    }
}
