package com.myselavu.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RulesDao {
    // Salary
    @Insert suspend fun insertSalaryRule(rule: SalaryRuleEntity): Long
    @Query("SELECT * FROM salary_rule ORDER BY effectiveFrom, id")
    suspend fun salaryRules(): List<SalaryRuleEntity>
    @Query("SELECT * FROM salary_rule ORDER BY effectiveFrom DESC, id DESC")
    fun observeSalaryRules(): Flow<List<SalaryRuleEntity>>

    // Extra income
    @Insert suspend fun insertExtraIncome(income: ExtraIncomeEntity): Long
    @Delete suspend fun deleteExtraIncome(income: ExtraIncomeEntity)
    @Query("SELECT * FROM extra_income WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeExtraIncome(from: LocalDate, to: LocalDate): Flow<List<ExtraIncomeEntity>>
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM extra_income WHERE date BETWEEN :from AND :to")
    suspend fun extraIncomeTotal(from: LocalDate, to: LocalDate): Long

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM extra_income WHERE date BETWEEN :from AND :to")
    fun observeExtraIncomeTotal(from: LocalDate, to: LocalDate): Flow<Long>
    @Query("SELECT * FROM extra_income ORDER BY date DESC, id DESC")
    fun observeAllExtraIncome(): Flow<List<ExtraIncomeEntity>>
    @Query("DELETE FROM extra_income WHERE date < :before")
    suspend fun deleteExtraIncomeBefore(before: LocalDate): Int
    @Query("SELECT * FROM extra_income ORDER BY date, id")
    suspend fun allExtraIncome(): List<ExtraIncomeEntity>
    @Query("SELECT * FROM salary_rule ORDER BY effectiveFrom, id")
    suspend fun allSalaryRules(): List<SalaryRuleEntity>

    // Fixed expenses
    @Insert suspend fun insertFixedExpense(fixed: FixedExpenseEntity): Long
    @Query("SELECT * FROM fixed_expense_rule ORDER BY fixedExpenseId, effectiveFrom, id")
    fun observeFixedExpenseRules(): Flow<List<FixedExpenseRuleEntity>>
    @Insert suspend fun insertFixedExpenseRule(rule: FixedExpenseRuleEntity): Long
    @Query("SELECT * FROM fixed_expense_rule ORDER BY fixedExpenseId, effectiveFrom, id")
    suspend fun fixedExpenseRules(): List<FixedExpenseRuleEntity>

    // Categories
    @Insert suspend fun insertCategory(category: CategoryEntity): Long
    @Update suspend fun updateCategory(category: CategoryEntity)
    @Insert suspend fun insertCategoryRule(rule: CategoryRuleEntity): Long
    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM category WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActiveCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category_rule ORDER BY categoryId, effectiveFrom, id")
    suspend fun categoryRules(): List<CategoryRuleEntity>
    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    fun observeAllCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun category(id: Long): CategoryEntity?

    // Sub-categories
    @Insert suspend fun insertSubCategory(sub: SubCategoryEntity): Long
    @Update suspend fun updateSubCategory(sub: SubCategoryEntity)
    @Query("SELECT * FROM sub_category WHERE archived = 0 ORDER BY categoryId, sortOrder, id")
    fun observeSubCategories(): Flow<List<SubCategoryEntity>>
}

@Dao
interface PeriodDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPeriod(period: BudgetPeriodEntity)
    @Update suspend fun updatePeriod(period: BudgetPeriodEntity)
    @Insert suspend fun insertPeriodFixed(rows: List<PeriodFixedExpenseEntity>)
    @Insert suspend fun insertPeriodCategories(rows: List<PeriodCategoryEntity>)
    @Update suspend fun updatePeriodFixed(row: PeriodFixedExpenseEntity)
    @Update suspend fun updatePeriodCategory(row: PeriodCategoryEntity)

    @Query("SELECT * FROM budget_period ORDER BY startDate DESC LIMIT 1")
    suspend fun latestPeriod(): BudgetPeriodEntity?
    @Query("SELECT * FROM budget_period ORDER BY startDate DESC LIMIT 1")
    fun observeLatestPeriod(): Flow<BudgetPeriodEntity?>
    @Query("SELECT * FROM budget_period ORDER BY startDate DESC")
    fun observePeriods(): Flow<List<BudgetPeriodEntity>>
    @Query("DELETE FROM budget_period WHERE startDate < :before")
    suspend fun deletePeriodsBefore(before: LocalDate): Int
    @Query("DELETE FROM category_transfer WHERE periodStart < :before")
    suspend fun deleteTransfersBefore(before: LocalDate): Int
    @Query("SELECT * FROM budget_period ORDER BY startDate")
    suspend fun allPeriods(): List<BudgetPeriodEntity>
    @Query("SELECT MIN(startDate) FROM budget_period")
    suspend fun firstPeriodStartEpochDay(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPeriodFixed(row: PeriodFixedExpenseEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPeriodCategory(row: PeriodCategoryEntity)
    @Query("DELETE FROM period_fixed_expense WHERE periodStart = :start AND fixedExpenseId = :fixedId")
    suspend fun deletePeriodFixed(start: LocalDate, fixedId: Long)
    @Query("DELETE FROM period_category WHERE periodStart = :start AND categoryId = :categoryId")
    suspend fun deletePeriodCategory(start: LocalDate, categoryId: Long)
    @Query("SELECT * FROM period_category ORDER BY periodStart, categoryId")
    suspend fun allPeriodCategories(): List<PeriodCategoryEntity>
    @Query("SELECT * FROM period_fixed_expense ORDER BY periodStart, fixedExpenseId")
    suspend fun allPeriodFixed(): List<PeriodFixedExpenseEntity>
    @Query("SELECT * FROM category_transfer ORDER BY periodStart, id")
    suspend fun allTransfers(): List<CategoryTransferEntity>
    @Query("SELECT * FROM budget_period WHERE startDate = :start")
    suspend fun period(start: LocalDate): BudgetPeriodEntity?
    @Query("SELECT * FROM budget_period WHERE :date BETWEEN startDate AND endDate LIMIT 1")
    suspend fun periodContaining(date: LocalDate): BudgetPeriodEntity?

    @Query("SELECT * FROM period_fixed_expense WHERE periodStart = :start ORDER BY fixedExpenseId")
    suspend fun periodFixed(start: LocalDate): List<PeriodFixedExpenseEntity>
    @Query("SELECT * FROM period_fixed_expense WHERE periodStart = :start ORDER BY fixedExpenseId")
    fun observePeriodFixed(start: LocalDate): Flow<List<PeriodFixedExpenseEntity>>
    @Query("SELECT * FROM period_category WHERE periodStart = :start ORDER BY categoryId")
    suspend fun periodCategories(start: LocalDate): List<PeriodCategoryEntity>
    @Query("SELECT * FROM period_category WHERE periodStart = :start ORDER BY categoryId")
    fun observePeriodCategories(start: LocalDate): Flow<List<PeriodCategoryEntity>>

    @Insert suspend fun insertTransfer(transfer: CategoryTransferEntity): Long
    @Delete suspend fun deleteTransfer(transfer: CategoryTransferEntity)
    @Query("SELECT * FROM category_transfer WHERE periodStart = :start ORDER BY id")
    suspend fun transfers(start: LocalDate): List<CategoryTransferEntity>
    @Query("SELECT * FROM category_transfer WHERE periodStart = :start ORDER BY id")
    fun observeTransfers(start: LocalDate): Flow<List<CategoryTransferEntity>>
}

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(expense: ExpenseEntity): Long
    @Update suspend fun update(expense: ExpenseEntity)
    @Delete suspend fun delete(expense: ExpenseEntity)
    @Query("SELECT * FROM expense WHERE id = :id") suspend fun byId(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expense WHERE date BETWEEN :from AND :to ORDER BY date DESC, createdAt DESC")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense ORDER BY date DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense WHERE date BETWEEN :from AND :to ORDER BY date, createdAt")
    suspend fun between(from: LocalDate, to: LocalDate): List<ExpenseEntity>

    @Query("SELECT * FROM expense ORDER BY date, createdAt")
    suspend fun all(): List<ExpenseEntity>

    @Query("SELECT * FROM expense ORDER BY createdAt DESC LIMIT :limit")
    suspend fun latestCreated(limit: Int): List<ExpenseEntity>

    @Query(
        "SELECT date, categoryId, SUM(amountPaise) AS totalPaise FROM expense " +
            "WHERE date BETWEEN :from AND :to GROUP BY date, categoryId"
    )
    suspend fun dailyCategoryTotals(from: LocalDate, to: LocalDate): List<DayCategorySum>

    @Query(
        "SELECT DISTINCT e.date FROM expense e INNER JOIN category c ON c.id = e.categoryId " +
            "WHERE c.type = 'SPENDING' AND e.date BETWEEN :from AND :to"
    )
    fun observeSpendingDays(from: LocalDate, to: LocalDate): Flow<List<Long>>

    @Query("DELETE FROM expense WHERE date < :before")
    suspend fun deleteBefore(before: LocalDate): Int
    @Query("SELECT COUNT(*) FROM expense")
    suspend fun count(): Int
    @Query("SELECT MIN(date) FROM expense")
    suspend fun oldestEpochDay(): Long?

    @Query("SELECT * FROM expense ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastCreated(): ExpenseEntity?

    @Query(
        "SELECT categoryId, SUM(amountPaise) AS totalPaise FROM expense " +
            "WHERE date BETWEEN :from AND :to GROUP BY categoryId"
    )
    suspend fun sumsByCategory(from: LocalDate, to: LocalDate): List<CategorySum>

    @Query(
        "SELECT categoryId, SUM(amountPaise) AS totalPaise FROM expense " +
            "WHERE date BETWEEN :from AND :to GROUP BY categoryId"
    )
    fun observeSumsByCategory(from: LocalDate, to: LocalDate): Flow<List<CategorySum>>

    @Query(
        "SELECT date, SUM(amountPaise) AS totalPaise FROM expense " +
            "WHERE date BETWEEN :from AND :to GROUP BY date ORDER BY date"
    )
    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DaySum>>

    @Query(
        "SELECT method, SUM(amountPaise) AS totalPaise FROM expense " +
            "WHERE date BETWEEN :from AND :to GROUP BY method"
    )
    suspend fun sumsByMethod(from: LocalDate, to: LocalDate): List<MethodSum>

    @Query("SELECT amountPaise FROM expense WHERE categoryId = :categoryId ORDER BY date DESC LIMIT :limit")
    suspend fun recentAmountsInCategory(categoryId: Long, limit: Int): List<Long>

    @Query(
        "SELECT e.* FROM expense e LEFT JOIN category c ON c.id = e.categoryId " +
            "WHERE e.note LIKE '%' || :query || '%' OR c.name LIKE '%' || :query || '%' " +
            "OR CAST(e.amountPaise / 100 AS TEXT) = :query " +
            "ORDER BY e.date DESC, e.createdAt DESC"
    )
    fun search(query: String): Flow<List<ExpenseEntity>>

    // Templates
    @Insert suspend fun insertTemplate(template: QuickTemplateEntity): Long
    @Update suspend fun updateTemplate(template: QuickTemplateEntity)
    @Delete suspend fun deleteTemplate(template: QuickTemplateEntity)
    @Query("UPDATE quick_template SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementTemplateUse(id: Long)
    @Query("SELECT * FROM quick_template ORDER BY sortOrder, useCount DESC, id")
    fun observeTemplates(): Flow<List<QuickTemplateEntity>>
}

@Dao
interface PlanningDao {
    // Recurring
    @Insert suspend fun insertRecurring(item: RecurringPaymentEntity): Long
    @Update suspend fun updateRecurring(item: RecurringPaymentEntity)
    @Delete suspend fun deleteRecurring(item: RecurringPaymentEntity)
    @Query("SELECT * FROM recurring_payment WHERE active = 1 ORDER BY nextDueDate, id")
    fun observeRecurring(): Flow<List<RecurringPaymentEntity>>
    @Query("SELECT * FROM recurring_payment WHERE active = 1 AND nextDueDate <= :until ORDER BY nextDueDate")
    suspend fun recurringDueBy(until: LocalDate): List<RecurringPaymentEntity>

    // Savings pot
    @Insert suspend fun insertPotEntry(entry: SavingsPotEntryEntity): Long
    @Delete suspend fun deletePotEntry(entry: SavingsPotEntryEntity)
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM savings_pot_entry")
    fun observePotTotal(): Flow<Long>
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM savings_pot_entry")
    suspend fun potTotal(): Long
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM savings_pot_entry WHERE date < :before")
    suspend fun potSumBefore(before: LocalDate): Long
    @Query("DELETE FROM savings_pot_entry WHERE date < :before")
    suspend fun deletePotBefore(before: LocalDate): Int
    @Query("SELECT * FROM savings_pot_entry ORDER BY date, id")
    suspend fun allPotEntries(): List<SavingsPotEntryEntity>
    @Query("SELECT * FROM savings_pot_entry ORDER BY date DESC, id DESC")
    fun observePotEntries(): Flow<List<SavingsPotEntryEntity>>

    // Goals
    @Insert suspend fun insertGoal(goal: SavingsGoalEntity): Long
    @Update suspend fun updateGoal(goal: SavingsGoalEntity)
    @Query("SELECT * FROM savings_goal WHERE archived = 0 ORDER BY createdAt")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>
    @Insert suspend fun insertContribution(c: GoalContributionEntity): Long
    @Delete suspend fun deleteContribution(c: GoalContributionEntity)
    @Query("SELECT * FROM goal_contribution ORDER BY date, id")
    fun observeContributions(): Flow<List<GoalContributionEntity>>

    // Lent & borrowed
    @Insert suspend fun insertDebt(debt: DebtEntity): Long
    @Update suspend fun updateDebt(debt: DebtEntity)
    @Delete suspend fun deleteDebt(debt: DebtEntity)
    @Query("SELECT * FROM debt ORDER BY settled, COALESCE(dueDate, date), id")
    fun observeDebts(): Flow<List<DebtEntity>>
    @Insert suspend fun insertRepayment(r: DebtRepaymentEntity): Long
    @Delete suspend fun deleteRepayment(r: DebtRepaymentEntity)
    @Query("SELECT * FROM debt_repayment ORDER BY date, id")
    fun observeRepayments(): Flow<List<DebtRepaymentEntity>>
}
