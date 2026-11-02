package com.myselavu.app.data

import androidx.room.withTransaction
import com.myselavu.app.data.db.AppDatabase
import com.myselavu.app.data.db.BudgetPeriodEntity
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.CategoryRuleEntity
import com.myselavu.app.data.db.CategoryTransferEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.ExtraIncomeEntity
import com.myselavu.app.data.db.FixedExpenseEntity
import com.myselavu.app.data.db.FixedExpenseRuleEntity
import com.myselavu.app.data.db.PeriodCategoryEntity
import com.myselavu.app.data.db.PeriodFixedExpenseEntity
import com.myselavu.app.data.db.PotEntryKind
import com.myselavu.app.data.db.QuickTemplateEntity
import com.myselavu.app.data.db.SalaryRuleEntity
import com.myselavu.app.data.db.SavingsPotEntryEntity
import com.myselavu.app.data.settings.SettingsRepository
import com.myselavu.app.time.AppClock
import com.myselavu.core.budget.BudgetRules
import com.myselavu.core.budget.CategoryRule
import com.myselavu.core.budget.CategorySnapshot
import com.myselavu.core.budget.CategoryTransfer
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.FixedExpenseRule
import com.myselavu.core.budget.FixedSnapshot
import com.myselavu.core.budget.PeriodActivity
import com.myselavu.core.budget.PeriodSnapshot
import com.myselavu.core.budget.Rollover
import com.myselavu.core.budget.Snapshotter
import com.myselavu.core.insight.UnusualExpense
import com.myselavu.core.money.Money
import com.myselavu.core.period.BudgetPeriod
import com.myselavu.core.period.PeriodCalendar
import com.myselavu.core.rules.ApplyFrom
import com.myselavu.core.rules.Effective
import com.myselavu.core.rules.EffectiveFrom
import com.myselavu.core.warning.SpendingPattern
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Input rows used by the setup wizard and the editing screens. */
data class FixedInput(val name: String, val amount: Money, val dueDay: Int? = null)
data class CategoryInput(
    val name: String,
    val emoji: String,
    val color: Long,
    val type: CategoryType,
    val allocation: Money,
)

/** One month in the month-wise / year-wise summaries. */
data class MonthSummary(
    val period: BudgetPeriod,
    val closed: Boolean,
    val salary: Money,
    val extraIncome: Money,
    val fixed: Money,
    val allocated: Money,
    val carriedIn: Money,
    val spent: Money,
    /** Money moved to the Savings pot at month end (closed months only). */
    val movedToSavings: Money,
    val categories: List<CategoryMonth>,
) {
    val income: Money get() = salary + extraIncome
    /** Money left = income + carried in − fixed − spent (can be negative). */
    val moneyLeft: Money get() = income + carriedIn - fixed - spent
}

data class CategoryMonth(val categoryId: Long, val allocated: Money, val spent: Money)

/**
 * Everything that reads or writes budget data. ALL dates come from [clock].
 * Screens never talk to the database directly.
 */
class BudgetRepository(
    val db: AppDatabase,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    private val rulesDao = db.rulesDao()
    private val periodDao = db.periodDao()
    private val expenseDao = db.expenseDao()
    private val planningDao = db.planningDao()
    private val catchUpLock = Mutex()

    private fun now(): Long = clock.millis()

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    /** Saves the first-time setup and creates the current budget month. */
    suspend fun completeSetup(
        salary: Money,
        fixed: List<FixedInput>,
        categories: List<CategoryInput>,
        startDay: Int,
    ) {
        settings.setBudgetStartDay(startDay)
        saveInitialRules(PeriodCalendar.periodContaining(clock.today(), startDay).start, salary, fixed, categories)
        settings.setSetupDone(true)
        ensureUpToDate()
    }

    /** Demo data: the same as setup, but the rules start in an earlier month (periods made by [bootstrapFrom]). */
    suspend fun completeSetupFrom(start: LocalDate, salary: Money, fixed: List<FixedInput>, categories: List<CategoryInput>) {
        val startDay = settings.current().budgetStartDay
        saveInitialRules(PeriodCalendar.periodContaining(start, startDay).start, salary, fixed, categories)
        settings.setSetupDone(true)
    }

    private suspend fun saveInitialRules(from: LocalDate, salary: Money, fixed: List<FixedInput>, categories: List<CategoryInput>) {
        db.withTransaction {
            if (salary.isPositive) {
                rulesDao.insertSalaryRule(SalaryRuleEntity(effectiveFrom = from, amountPaise = salary.paise, createdAt = now()))
            }
            fixed.forEachIndexed { i, f -> insertFixed(f, from, i) }
            categories.forEachIndexed { i, c -> insertCategory(c, from, i) }
        }
    }

    private suspend fun insertFixed(f: FixedInput, effectiveFrom: LocalDate, order: Int): Long {
        val id = rulesDao.insertFixedExpense(FixedExpenseEntity(sortOrder = order, createdAt = now()))
        rulesDao.insertFixedExpenseRule(
            FixedExpenseRuleEntity(
                fixedExpenseId = id, effectiveFrom = effectiveFrom, name = f.name.trim(),
                amountPaise = f.amount.paise, dueDay = f.dueDay, createdAt = now(),
            )
        )
        return id
    }

    private suspend fun insertCategory(c: CategoryInput, effectiveFrom: LocalDate, order: Int): Long {
        val id = rulesDao.insertCategory(
            CategoryEntity(name = c.name.trim(), emoji = c.emoji, color = c.color, type = c.type, sortOrder = order, createdAt = now())
        )
        rulesDao.insertCategoryRule(
            CategoryRuleEntity(categoryId = id, effectiveFrom = effectiveFrom, allocationPaise = c.allocation.paise, createdAt = now())
        )
        return id
    }

    // ------------------------------------------------------------------
    // Month rollover: create every missing month, in order, applying the unspent-money rule
    // ------------------------------------------------------------------

    suspend fun ensureUpToDate() = catchUpLock.withLock {
        val s = settings.current()
        if (!s.setupDone) return@withLock
        val today = clock.today()
        val latestEntity = periodDao.latestPeriod()
        if (latestEntity != null && !today.isAfter(latestEntity.endDate)) return@withLock
        val rules = loadRules()
        db.withTransaction {
            var current: PeriodSnapshot = if (latestEntity != null) {
                loadSnapshot(latestEntity)
            } else {
                Snapshotter.snapshot(PeriodCalendar.periodContaining(today, s.budgetStartDay), rules).also { writeSnapshot(it) }
            }
            var guard = 0
            while (today.isAfter(current.period.end) && guard++ < 240) {
                // Same engine as the unit tests (Rollover.close), fed with the real spending.
                val result = Rollover.close(current, activityFor(current.period), s.unspentMode)
                val entity = periodDao.period(current.period.start) ?: break
                periodDao.updatePeriod(
                    entity.copy(
                        closed = true, closedAt = now(), closeMode = s.unspentMode,
                        moneyLeftAtClosePaise = result.moneyLeft.paise,
                        savingsDepositPaise = result.savingsDeposit.paise,
                    )
                )
                if (result.savingsDeposit.isPositive) {
                    planningDao.insertPotEntry(
                        SavingsPotEntryEntity(
                            date = current.period.end, amountPaise = result.savingsDeposit.paise,
                            kind = PotEntryKind.MONTH_END, periodStart = current.period.start, createdAt = now(),
                        )
                    )
                }
                val next = Snapshotter.snapshot(PeriodCalendar.nextPeriod(current.period, s.budgetStartDay), rules, result.carryToNext)
                writeSnapshot(next)
                current = next
            }
        }
    }

    private suspend fun writeSnapshot(snapshot: PeriodSnapshot) {
        val start = snapshot.period.start
        periodDao.insertPeriod(BudgetPeriodEntity(start, snapshot.period.end, snapshot.salary.paise))
        periodDao.insertPeriodFixed(snapshot.fixedExpenses.map {
            PeriodFixedExpenseEntity(start, it.fixedExpenseId, it.name, it.amount.paise, it.dueDay)
        })
        periodDao.insertPeriodCategories(snapshot.categories.map {
            PeriodCategoryEntity(start, it.categoryId, it.type, it.allocation.paise, it.carryIn.paise)
        })
    }

    suspend fun loadRules(): BudgetRules {
        val salary = rulesDao.salaryRules().map { Effective(it.effectiveFrom, Money(it.amountPaise), it.id) }
        val fixed = rulesDao.fixedExpenseRules().groupBy { it.fixedExpenseId }.mapValues { (_, rows) ->
            rows.map { Effective(it.effectiveFrom, FixedExpenseRule(it.name, Money(it.amountPaise), it.dueDay, it.active), it.id) }
        }
        val catRules = rulesDao.categoryRules().groupBy { it.categoryId }.mapValues { (_, rows) ->
            rows.map { Effective(it.effectiveFrom, CategoryRule(Money(it.allocationPaise), it.active), it.id) }
        }
        val types = rulesDao.categories().associate { it.id to it.type }
        return BudgetRules(salary, fixed, catRules, types)
    }

    suspend fun loadSnapshot(p: BudgetPeriodEntity): PeriodSnapshot = PeriodSnapshot(
        period = BudgetPeriod(p.startDate, p.endDate),
        salary = Money(p.salaryPaise),
        fixedExpenses = periodDao.periodFixed(p.startDate).map { FixedSnapshot(it.fixedExpenseId, it.name, Money(it.amountPaise), it.dueDay) },
        categories = periodDao.periodCategories(p.startDate).map {
            CategorySnapshot(it.categoryId, it.type, Money(it.allocationPaise), Money(it.carryInPaise))
        },
    )

    suspend fun activityFor(period: BudgetPeriod): PeriodActivity = PeriodActivity(
        spentByCategory = expenseDao.sumsByCategory(period.start, period.end).associate { it.categoryId to Money(it.totalPaise) },
        extraIncome = Money(rulesDao.extraIncomeTotal(period.start, period.end)),
        transfers = periodDao.transfers(period.start).map { CategoryTransfer(it.fromCategoryId, it.toCategoryId, Money(it.amountPaise)) },
    )

    /** Daily spending per category for the last few closed months, for pattern learning. */
    suspend fun patternHistory(beforeStart: LocalDate, months: Int = 6): Map<Long, List<SpendingPattern.ClosedMonth>> {
        val periods = periodDao.allPeriods().filter { it.closed && it.startDate.isBefore(beforeStart) }.takeLast(months)
        val out = HashMap<Long, MutableList<SpendingPattern.ClosedMonth>>()
        for (p in periods) {
            val period = BudgetPeriod(p.startDate, p.endDate)
            val rows = expenseDao.dailyCategoryTotals(p.startDate, p.endDate)
            val byCategory = rows.groupBy { it.categoryId }
            for ((catId, catRows) in byCategory) {
                val daily = MutableList(period.lengthInDays) { Money.ZERO }
                for (r in catRows) {
                    val idx = period.daysElapsed(r.date) - 1
                    if (idx in daily.indices) daily[idx] = daily[idx] + Money(r.totalPaise)
                }
                out.getOrPut(catId) { ArrayList() } += SpendingPattern.ClosedMonth(daily)
            }
        }
        return out
    }

    suspend fun currentPeriod(): BudgetPeriodEntity? = periodDao.latestPeriod()

    suspend fun firstPeriodStart(): LocalDate? = periodDao.firstPeriodStartEpochDay()?.let(LocalDate::ofEpochDay)

    // ------------------------------------------------------------------
    // Salary & extra income
    // ------------------------------------------------------------------

    suspend fun setSalary(amount: Money, applyFrom: ApplyFrom) {
        val current = currentPeriod() ?: return
        val period = BudgetPeriod(current.startDate, current.endDate)
        db.withTransaction {
            rulesDao.insertSalaryRule(
                SalaryRuleEntity(effectiveFrom = EffectiveFrom.effectiveDateFor(applyFrom, period), amountPaise = amount.paise, createdAt = now())
            )
            if (applyFrom == ApplyFrom.THIS_PERIOD) periodDao.updatePeriod(current.copy(salaryPaise = amount.paise))
        }
    }

    suspend fun addExtraIncome(amount: Money, date: LocalDate, note: String): Long =
        rulesDao.insertExtraIncome(ExtraIncomeEntity(date = date, amountPaise = amount.paise, note = note.trim(), createdAt = now()))

    suspend fun deleteExtraIncome(e: ExtraIncomeEntity) = rulesDao.deleteExtraIncome(e)
    suspend fun restoreExtraIncome(e: ExtraIncomeEntity) { rulesDao.insertExtraIncome(e) }

    // ------------------------------------------------------------------
    // Fixed expenses
    // ------------------------------------------------------------------

    suspend fun addFixedExpense(input: FixedInput, applyFrom: ApplyFrom) {
        val current = currentPeriod() ?: return
        val period = BudgetPeriod(current.startDate, current.endDate)
        db.withTransaction {
            val id = insertFixed(input, EffectiveFrom.effectiveDateFor(applyFrom, period), 999)
            if (applyFrom == ApplyFrom.THIS_PERIOD) {
                periodDao.upsertPeriodFixed(PeriodFixedExpenseEntity(current.startDate, id, input.name.trim(), input.amount.paise, input.dueDay))
            }
        }
    }

    suspend fun editFixedExpense(id: Long, input: FixedInput, applyFrom: ApplyFrom) {
        val current = currentPeriod() ?: return
        val period = BudgetPeriod(current.startDate, current.endDate)
        db.withTransaction {
            rulesDao.insertFixedExpenseRule(
                FixedExpenseRuleEntity(
                    fixedExpenseId = id, effectiveFrom = EffectiveFrom.effectiveDateFor(applyFrom, period),
                    name = input.name.trim(), amountPaise = input.amount.paise, dueDay = input.dueDay, createdAt = now(),
                )
            )
            if (applyFrom == ApplyFrom.THIS_PERIOD) {
                val old = periodDao.periodFixed(current.startDate).firstOrNull { it.fixedExpenseId == id }
                periodDao.upsertPeriodFixed(
                    PeriodFixedExpenseEntity(current.startDate, id, input.name.trim(), input.amount.paise, input.dueDay,
                        paid = old?.paid ?: false, paidAt = old?.paidAt)
                )
            }
        }
    }

    suspend fun deleteFixedExpense(id: Long, last: FixedInput, applyFrom: ApplyFrom) {
        val current = currentPeriod() ?: return
        val period = BudgetPeriod(current.startDate, current.endDate)
        db.withTransaction {
            rulesDao.insertFixedExpenseRule(
                FixedExpenseRuleEntity(
                    fixedExpenseId = id, effectiveFrom = EffectiveFrom.effectiveDateFor(applyFrom, period),
                    name = last.name, amountPaise = last.amount.paise, dueDay = last.dueDay, active = false, createdAt = now(),
                )
            )
            if (applyFrom == ApplyFrom.THIS_PERIOD) periodDao.deletePeriodFixed(current.startDate, id)
        }
    }

    suspend fun setFixedPaid(row: PeriodFixedExpenseEntity, paid: Boolean) =
        periodDao.updatePeriodFixed(row.copy(paid = paid, paidAt = if (paid) now() else null))

    // ------------------------------------------------------------------
    // Categories
    // ------------------------------------------------------------------

    suspend fun addCategory(input: CategoryInput) {
        val current = currentPeriod() ?: return
        db.withTransaction {
            val order = (rulesDao.categories().maxOfOrNull { it.sortOrder } ?: 0) + 1
            val id = insertCategory(input, current.startDate, order)
            periodDao.upsertPeriodCategory(PeriodCategoryEntity(current.startDate, id, input.type, input.allocation.paise))
        }
    }

    suspend fun editCategory(id: Long, input: CategoryInput, applyFrom: ApplyFrom) {
        val current = currentPeriod() ?: return
        val period = BudgetPeriod(current.startDate, current.endDate)
        val existing = rulesDao.category(id) ?: return
        db.withTransaction {
            // Name, emoji, colour and type change straight away.
            rulesDao.updateCategory(existing.copy(name = input.name.trim(), emoji = input.emoji, color = input.color, type = input.type))
            rulesDao.insertCategoryRule(
                CategoryRuleEntity(
                    categoryId = id, effectiveFrom = EffectiveFrom.effectiveDateFor(applyFrom, period),
                    allocationPaise = input.allocation.paise, createdAt = now(),
                )
            )
            val row = periodDao.periodCategories(current.startDate).firstOrNull { it.categoryId == id }
            if (applyFrom == ApplyFrom.THIS_PERIOD) {
                periodDao.upsertPeriodCategory(
                    PeriodCategoryEntity(current.startDate, id, input.type, input.allocation.paise, row?.carryInPaise ?: 0)
                )
            } else if (row != null && row.type != input.type) {
                periodDao.upsertPeriodCategory(row.copy(type = input.type))
            }
        }
    }

    /** Archive (soft delete). History is kept; the money goes back to "unplanned" this month. */
    suspend fun archiveCategory(id: Long): PeriodCategoryEntity? {
        val current = currentPeriod() ?: return null
        val existing = rulesDao.category(id) ?: return null
        val row = periodDao.periodCategories(current.startDate).firstOrNull { it.categoryId == id }
        db.withTransaction {
            rulesDao.updateCategory(existing.copy(archived = true))
            rulesDao.insertCategoryRule(
                CategoryRuleEntity(categoryId = id, effectiveFrom = current.startDate, allocationPaise = row?.allocationPaise ?: 0,
                    active = false, createdAt = now())
            )
            periodDao.deletePeriodCategory(current.startDate, id)
        }
        return row
    }

    /** Undo for [archiveCategory]. */
    suspend fun unarchiveCategory(id: Long, previousRow: PeriodCategoryEntity?) {
        val current = currentPeriod() ?: return
        val existing = rulesDao.category(id) ?: return
        db.withTransaction {
            rulesDao.updateCategory(existing.copy(archived = false))
            rulesDao.insertCategoryRule(
                CategoryRuleEntity(categoryId = id, effectiveFrom = current.startDate,
                    allocationPaise = previousRow?.allocationPaise ?: 0, createdAt = now())
            )
            periodDao.upsertPeriodCategory(previousRow ?: PeriodCategoryEntity(current.startDate, id, existing.type, 0))
        }
    }

    suspend fun moveCategory(id: Long, up: Boolean) {
        val list = rulesDao.categories().filter { !it.archived }.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return
        java.util.Collections.swap(list, i, j)
        db.withTransaction { list.forEachIndexed { idx, c -> if (c.sortOrder != idx) rulesDao.updateCategory(c.copy(sortOrder = idx)) } }
    }

    /** "Move ₹500 from Personal to Food" for this month. */
    suspend fun moveMoney(fromId: Long, toId: Long, amount: Money) {
        val current = currentPeriod() ?: return
        if (fromId == toId || !amount.isPositive) return
        periodDao.insertTransfer(
            CategoryTransferEntity(periodStart = current.startDate, fromCategoryId = fromId, toCategoryId = toId,
                amountPaise = amount.paise, createdAt = now())
        )
    }

    /** Apply a suggested plan's allocations. */
    suspend fun applyAllocations(allocations: Map<Long, Money>, applyFrom: ApplyFrom) {
        val cats = rulesDao.categories().associateBy { it.id }
        for ((id, amount) in allocations) {
            val c = cats[id] ?: continue
            editCategory(id, CategoryInput(c.name, c.emoji, c.color, c.type, amount), applyFrom)
        }
    }

    // ------------------------------------------------------------------
    // Expenses & templates
    // ------------------------------------------------------------------

    /** Saves an expense. Returns its id and whether it looks unusually large for the category. */
    suspend fun saveExpense(expense: ExpenseEntity): Pair<Long, Boolean> {
        val previous = expenseDao.recentAmountsInCategory(expense.categoryId, 50).map(::Money)
        val id = if (expense.id == 0L) {
            expenseDao.insert(expense.copy(createdAt = now(), updatedAt = now()))
        } else {
            expenseDao.update(expense.copy(updatedAt = now()))
            expense.id
        }
        settings.setLastPaymentMethod(expense.method)
        val unusual = expense.id == 0L && UnusualExpense.isUnusual(Money(expense.amountPaise), previous)
        return id to unusual
    }

    suspend fun deleteExpense(e: ExpenseEntity) = expenseDao.delete(e)
    suspend fun restoreExpense(e: ExpenseEntity) { expenseDao.insert(e) }
    suspend fun expense(id: Long): ExpenseEntity? = expenseDao.byId(id)

    suspend fun recentForSuggestions(): List<ExpenseEntity> = expenseDao.latestCreated(200)

    suspend fun addTemplate(t: QuickTemplateEntity) { expenseDao.insertTemplate(t) }
    suspend fun deleteTemplate(t: QuickTemplateEntity) = expenseDao.deleteTemplate(t)

    suspend fun addFromTemplate(t: QuickTemplateEntity): Long {
        expenseDao.incrementTemplateUse(t.id)
        return saveExpense(
            ExpenseEntity(
                amountPaise = t.amountPaise, categoryId = t.categoryId, subCategoryId = t.subCategoryId,
                method = t.method, note = t.note.ifBlank { t.name }, date = clock.today(), createdAt = now(), templateId = t.id,
            )
        ).first
    }

    // ------------------------------------------------------------------
    // Summaries
    // ------------------------------------------------------------------

    suspend fun monthSummaries(): List<MonthSummary> {
        val periods = periodDao.allPeriods()
        return periods.map { p ->
            val snap = loadSnapshot(p)
            val activity = activityFor(snap.period)
            MonthSummary(
                period = snap.period,
                closed = p.closed,
                salary = snap.salary,
                extraIncome = activity.extraIncome,
                fixed = snap.totalFixed,
                allocated = snap.totalAllocated,
                carriedIn = snap.totalCarryIn,
                spent = activity.totalSpent,
                movedToSavings = Money(p.savingsDepositPaise ?: 0),
                categories = (snap.categories.map { it.categoryId } + activity.spentByCategory.keys).distinct().map { id ->
                    CategoryMonth(id, snap.category(id)?.allocation ?: Money.ZERO, activity.spent(id))
                },
            )
        }.sortedByDescending { it.period.start }
    }

    suspend fun potTotal(): Money = Money(planningDao.potTotal())

    // ------------------------------------------------------------------
    // Clearing old history (keeps the app small after many years)
    // ------------------------------------------------------------------

    data class ClearResult(val expensesDeleted: Int, val monthsDeleted: Int, val keptFrom: LocalDate)

    /**
     * Deletes expenses, extra income and finished months that start before the budget month
     * containing [requested]. The current month is never touched. The Savings pot total is kept
     * (old entries are folded into one "balance" entry). Salary/bill/category settings are kept.
     */
    suspend fun clearHistoryBefore(requested: LocalDate): ClearResult {
        val current = currentPeriod()
        val periods = periodDao.allPeriods()
        val containing = periods.firstOrNull { !requested.isBefore(it.startDate) && !requested.isAfter(it.endDate) }
        var keepFrom = containing?.startDate ?: requested
        if (current != null && keepFrom.isAfter(current.startDate)) keepFrom = current.startDate
        var expenses = 0
        var months = 0
        db.withTransaction {
            val potOld = planningDao.potSumBefore(keepFrom)
            val oldPotEntries = planningDao.deletePotBefore(keepFrom)
            if (oldPotEntries > 0 && potOld != 0L) {
                planningDao.insertPotEntry(
                    SavingsPotEntryEntity(date = keepFrom, amountPaise = potOld, kind = PotEntryKind.CARRIED_BALANCE,
                        note = "Balance from cleared history", createdAt = now())
                )
            }
            expenses = expenseDao.deleteBefore(keepFrom)
            rulesDao.deleteExtraIncomeBefore(keepFrom)
            periodDao.deleteTransfersBefore(keepFrom)
            months = periodDao.deletePeriodsBefore(keepFrom)
        }
        return ClearResult(expenses, months, keepFrom)
    }

    /** Applies the "keep history for N years" setting. Returns null when there was nothing to clear. */
    suspend fun applyRetention(): ClearResult? {
        val years = settings.current().keepHistoryYears
        if (years <= 0) return null
        val cutoff = com.myselavu.core.backup.HistoryRetention.cutoff(clock.today(), years)
        val oldest = periodDao.firstPeriodStartEpochDay()?.let(LocalDate::ofEpochDay) ?: return null
        if (!oldest.isBefore(cutoff)) return null
        return clearHistoryBefore(cutoff)
    }

    data class StorageInfo(val expenseCount: Int, val monthCount: Int, val oldest: LocalDate?)

    suspend fun storageInfo(): StorageInfo = StorageInfo(
        expenseCount = expenseDao.count(),
        monthCount = periodDao.allPeriods().size,
        oldest = listOfNotNull(expenseDao.oldestEpochDay(), periodDao.firstPeriodStartEpochDay()).minOrNull()?.let(LocalDate::ofEpochDay),
    )

    // ------------------------------------------------------------------
    // Developer tools
    // ------------------------------------------------------------------

    suspend fun wipeAll() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        settings.setSetupDone(false)
    }

    /** Creates the period containing [start] then catches up to today (used by demo data). */
    suspend fun bootstrapFrom(start: LocalDate) {
        val s = settings.current()
        val first = Snapshotter.snapshot(PeriodCalendar.periodContaining(start, s.budgetStartDay), loadRules())
        db.withTransaction { writeSnapshot(first) }
        ensureUpToDate()
    }
}
