@file:OptIn(ExperimentalCoroutinesApi::class)

package com.myselavu.app.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myselavu.app.AppContainer
import com.myselavu.app.MySelavuApp
import com.myselavu.app.R
import com.myselavu.app.data.CategoryInput
import com.myselavu.app.data.FixedInput
import com.myselavu.app.data.MonthSummary
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.ExtraIncomeEntity
import com.myselavu.app.data.db.FixedExpenseRuleEntity
import com.myselavu.app.data.db.PeriodFixedExpenseEntity
import com.myselavu.app.data.db.QuickTemplateEntity
import com.myselavu.app.data.db.SalaryRuleEntity
import com.myselavu.app.data.settings.AppSettings
import com.myselavu.app.data.settings.ThemeMode
import com.myselavu.app.ui.home.DashboardState
import com.myselavu.app.ui.home.buildDashboard
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.UnspentMode
import com.myselavu.core.insight.CategorySuggestions
import com.myselavu.core.money.IndianFormat
import com.myselavu.core.money.Money
import com.myselavu.core.plan.PlanSuggestion
import com.myselavu.core.rules.ApplyFrom
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A snackbar message, optionally with an action such as Undo. */
data class UiMessage(
    @StringRes val text: Int,
    val args: List<Any> = emptyList(),
    @StringRes val actionLabel: Int? = null,
    val action: (suspend () -> Unit)? = null,
)

/** One fixed expense as shown on the "Fixed expenses" screen. */
data class FixedItem(val id: Long, val latest: FixedExpenseRuleEntity, val inThisMonth: PeriodFixedExpenseEntity?)

class AppViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val db = c.database

    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categories: StateFlow<List<CategoryEntity>> =
        db.rulesDao().observeAllCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val templates: StateFlow<List<QuickTemplateEntity>> =
        db.expenseDao().observeTemplates().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val salaryHistory: StateFlow<List<SalaryRuleEntity>> =
        db.rulesDao().observeSalaryRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extraIncome: StateFlow<List<ExtraIncomeEntity>> =
        db.rulesDao().observeAllExtraIncome().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    val dashboard: StateFlow<DashboardState?> = db.periodDao().observeLatestPeriod().filterNotNull().flatMapLatest { p ->
        val start = p.startDate
        val end = p.endDate
        val pd = db.periodDao()
        val ed = db.expenseDao()
        val snapshotParts = combine(pd.observePeriodCategories(start), pd.observePeriodFixed(start), pd.observeTransfers(start)) { a, b, t -> Triple(a, b, t) }
        val today = c.clock.today()
        val activityParts = combine(
            ed.observeSumsByCategory(start, end),
            db.rulesDao().observeExtraIncomeTotal(start, end),
            ed.observeSpendingDays(today.minusDays(120), today),
        ) { sums, extra, days -> Triple(sums, extra, days) }
        val learned = flow { emit(repo.patternHistory(start) to repo.firstPeriodStart()) }
        combine(snapshotParts, activityParts, db.rulesDao().observeAllCategories(), ed.observeRecent(5), combine(learned, c.settings.settings) { l, s -> l to s }) {
                snap, act, cats, recent, ls ->
            buildDashboard(
                period = p, categoryRows = snap.first, fixedRows = snap.second, transfers = snap.third,
                sums = act.first, extraIncome = act.second, spendingDays = act.third,
                categories = cats, recent = recent, history = ls.first.first, firstStart = ls.first.second,
                settings = ls.second, today = c.clock.today(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val fixedItems: StateFlow<List<FixedItem>> = db.periodDao().observeLatestPeriod().filterNotNull().flatMapLatest { p ->
        combine(db.rulesDao().observeFixedExpenseRules(), db.periodDao().observePeriodFixed(p.startDate)) { rules, rows ->
            rules.groupBy { it.fixedExpenseId }.mapNotNull { (id, list) ->
                val latest = list.maxWith(compareBy<FixedExpenseRuleEntity>({ it.effectiveFrom }, { it.id }))
                val row = rows.firstOrNull { it.fixedExpenseId == id }
                if (!latest.active && row == null) null else FixedItem(id, latest, row)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _summaries = MutableStateFlow<List<MonthSummary>?>(null)
    val summaries: StateFlow<List<MonthSummary>?> = _summaries.asStateFlow()
    private val _potTotal = MutableStateFlow(Money.ZERO)
    val potTotal: StateFlow<Money> = _potTotal.asStateFlow()

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            c.clock.offsetDays = s.timeTravelDays
            repo.ensureUpToDate()
            // "Keep history for N years": quietly clear very old history to save space.
            if (repo.applyRetention() != null) c.backup.compact()
        }
    }

    /** Called when the app comes back to the screen: a new month may have started. */
    fun onResume() = viewModelScope.launch { repo.ensureUpToDate() }

    private fun say(msg: UiMessage) { _messages.tryEmit(msg) }

    fun today(): LocalDate = c.clock.today()

    // ---------- Setup ----------

    fun completeSetup(salary: Money, fixed: List<FixedInput>, cats: List<CategoryInput>, startDay: Int, name: String) =
        viewModelScope.launch {
            if (name.isNotBlank()) c.settings.setProfileName(name)
            repo.completeSetup(salary, fixed, cats, startDay)
        }

    // ---------- Expenses ----------

    /** Categories ranked for the add-expense sheet: most used + time of day + recent. */
    suspend fun rankedCategoryIds(): List<Long> {
        val active = categories.value.filter { !it.archived }
        val recent = repo.recentForSuggestions().map {
            CategorySuggestions.Use(it.categoryId, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.createdAt), ZoneId.systemDefault()))
        }
        return CategorySuggestions.rank(recent, c.clock.now(), active.map { it.id })
    }

    fun saveExpense(expense: ExpenseEntity, saveAsTemplate: Boolean, templateName: String) = viewModelScope.launch {
        val (id, unusual) = repo.saveExpense(expense)
        if (saveAsTemplate) {
            repo.addTemplate(
                QuickTemplateEntity(
                    name = templateName.ifBlank { expense.note.ifBlank { IndianFormat.format(Money(expense.amountPaise)) } },
                    amountPaise = expense.amountPaise, categoryId = expense.categoryId, method = expense.method, note = expense.note,
                )
            )
        }
        val catName = categories.value.firstOrNull { it.id == expense.categoryId }?.name ?: ""
        if (unusual) {
            say(UiMessage(R.string.unusual_hint, listOf(catName)))
        } else if (expense.id == 0L) {
            val saved = repo.expense(id)
            say(UiMessage(R.string.expense_saved, listOf(IndianFormat.format(Money(expense.amountPaise)), catName),
                R.string.undo, saved?.let { s -> suspend { repo.deleteExpense(s) } }))
        } else {
            say(UiMessage(R.string.expense_updated))
        }
    }

    fun deleteExpense(e: ExpenseEntity) = viewModelScope.launch {
        repo.deleteExpense(e)
        say(UiMessage(R.string.expense_deleted, actionLabel = R.string.undo, action = { repo.restoreExpense(e) }))
    }

    fun duplicateExpense(e: ExpenseEntity, toToday: Boolean) = viewModelScope.launch {
        val copy = e.copy(id = 0, date = if (toToday) c.clock.today() else e.date, createdAt = c.clock.millis())
        val (id, _) = repo.saveExpense(copy)
        val saved = repo.expense(id)
        say(UiMessage(R.string.expense_added_again, actionLabel = R.string.undo, action = { saved?.let { repo.deleteExpense(it) } }))
    }

    fun addFromTemplate(t: QuickTemplateEntity) = viewModelScope.launch {
        val id = repo.addFromTemplate(t)
        val saved = repo.expense(id)
        say(UiMessage(R.string.expense_saved, listOf(IndianFormat.format(Money(t.amountPaise)), t.name),
            R.string.undo, saved?.let { s -> suspend { repo.deleteExpense(s) } }))
    }

    fun deleteTemplate(t: QuickTemplateEntity) = viewModelScope.launch {
        repo.deleteTemplate(t)
        say(UiMessage(R.string.template_deleted, actionLabel = R.string.undo, action = { repo.addTemplate(t) }))
    }

    // ---------- Salary & income ----------

    fun setSalary(amount: Money, applyFrom: ApplyFrom) = viewModelScope.launch {
        repo.setSalary(amount, applyFrom)
        say(UiMessage(if (applyFrom == ApplyFrom.THIS_PERIOD) R.string.saved_this_month else R.string.saved_next_month))
    }

    fun addExtraIncome(amount: Money, date: LocalDate, note: String) = viewModelScope.launch {
        repo.addExtraIncome(amount, date, note)
        say(UiMessage(R.string.extra_income_added))
    }

    fun deleteExtraIncome(e: ExtraIncomeEntity) = viewModelScope.launch {
        repo.deleteExtraIncome(e)
        say(UiMessage(R.string.deleted, actionLabel = R.string.undo, action = { repo.restoreExtraIncome(e) }))
    }

    // ---------- Fixed expenses ----------

    fun addFixed(input: FixedInput, applyFrom: ApplyFrom) = viewModelScope.launch {
        repo.addFixedExpense(input, applyFrom)
        say(UiMessage(if (applyFrom == ApplyFrom.THIS_PERIOD) R.string.saved_this_month else R.string.saved_next_month))
    }

    fun editFixed(id: Long, input: FixedInput, applyFrom: ApplyFrom) = viewModelScope.launch {
        repo.editFixedExpense(id, input, applyFrom)
        say(UiMessage(if (applyFrom == ApplyFrom.THIS_PERIOD) R.string.saved_this_month else R.string.saved_next_month))
    }

    fun deleteFixed(item: FixedItem, applyFrom: ApplyFrom) = viewModelScope.launch {
        val last = FixedInput(item.latest.name, Money(item.latest.amountPaise), item.latest.dueDay)
        repo.deleteFixedExpense(item.id, last, applyFrom)
        say(UiMessage(R.string.fixed_removed, listOf(item.latest.name), R.string.undo, action = {
            repo.editFixedExpense(item.id, last, applyFrom)
        }))
    }

    fun setFixedPaid(row: PeriodFixedExpenseEntity, paid: Boolean) = viewModelScope.launch { repo.setFixedPaid(row, paid) }

    // ---------- Categories ----------

    fun addCategory(input: CategoryInput) = viewModelScope.launch {
        repo.addCategory(input)
        say(UiMessage(R.string.category_added, listOf(input.name)))
    }

    fun editCategory(id: Long, input: CategoryInput, applyFrom: ApplyFrom) = viewModelScope.launch {
        repo.editCategory(id, input, applyFrom)
        say(UiMessage(if (applyFrom == ApplyFrom.THIS_PERIOD) R.string.saved_this_month else R.string.saved_next_month))
    }

    fun archiveCategory(cat: CategoryEntity) = viewModelScope.launch {
        val row = repo.archiveCategory(cat.id)
        say(UiMessage(R.string.category_archived, listOf(cat.name), R.string.undo, action = { repo.unarchiveCategory(cat.id, row) }))
    }

    fun moveCategory(id: Long, up: Boolean) = viewModelScope.launch { repo.moveCategory(id, up) }

    fun moveMoney(fromId: Long, toId: Long, amount: Money) = viewModelScope.launch {
        repo.moveMoney(fromId, toId, amount)
        val from = categories.value.firstOrNull { it.id == fromId }?.name ?: ""
        val to = categories.value.firstOrNull { it.id == toId }?.name ?: ""
        say(UiMessage(R.string.money_moved, listOf(IndianFormat.format(amount), from, to)))
    }

    // ---------- Summaries & plan ----------

    fun loadSummaries() = viewModelScope.launch {
        _summaries.value = repo.monthSummaries()
        _potTotal.value = repo.potTotal()
    }

    /** Suggested plan for next month from past months' spending. */
    suspend fun suggestPlan(): PlanSuggestion.Plan? {
        val months = repo.monthSummaries()
        val current = months.firstOrNull { !it.closed } ?: months.firstOrNull() ?: return null
        val closed = months.filter { it.closed }.sortedByDescending { it.period.start }
        val active = categories.value.filter { !it.archived }
        val inputs = active.map { cat ->
            PlanSuggestion.Input(
                categoryId = cat.id,
                type = cat.type,
                currentAllocation = current.categories.firstOrNull { it.categoryId == cat.id }?.allocated ?: Money.ZERO,
                pastSpends = closed.map { m -> m.categories.firstOrNull { it.categoryId == cat.id }?.spent ?: Money.ZERO }
                    .take(PlanSuggestion.MAX_MONTHS)
                    .let { if (closed.isEmpty()) emptyList() else it },
            )
        }
        return PlanSuggestion.suggest(inputs, current.salary - current.fixed)
    }

    fun applyPlan(plan: PlanSuggestion.Plan, applyFrom: ApplyFrom) = viewModelScope.launch {
        repo.applyAllocations(plan.suggestions.associate { it.categoryId to it.suggested }, applyFrom)
        say(UiMessage(R.string.plan_applied))
    }

    // ---------- Export ----------

    suspend fun exportExcel(out: OutputStream, from: LocalDate?, to: LocalDate?) {
        c.exporter.export(out, from, to, c.clock.today())
    }

    fun exportDone(ok: Boolean) = say(UiMessage(if (ok) R.string.export_done else R.string.export_failed))

    // ---------- Settings ----------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }
    fun setProfileName(name: String) = viewModelScope.launch { c.settings.setProfileName(name) }
    fun setStartDay(day: Int) = viewModelScope.launch {
        c.settings.setBudgetStartDay(day)
        say(UiMessage(R.string.start_day_saved))
    }
    fun setUnspentMode(mode: UnspentMode) = viewModelScope.launch { c.settings.setUnspentMode(mode) }
    fun setHideAmounts(hide: Boolean) = viewModelScope.launch { c.settings.setHideAmounts(hide) }

    // ---------- Backup, restore & storage ----------

    data class StorageState(val sizeBytes: Long, val info: com.myselavu.app.data.BudgetRepository.StorageInfo)

    private val _storage = MutableStateFlow<StorageState?>(null)
    val storage: StateFlow<StorageState?> = _storage.asStateFlow()

    fun loadStorage() = viewModelScope.launch {
        _storage.value = StorageState(c.backup.dataSizeBytes(), repo.storageInfo())
    }

    /** Writes the backup file; returns true on success. */
    suspend fun createBackup(out: OutputStream, password: String): Boolean = runCatching {
        c.backup.createBackup(out, password)
    }.isSuccess.also { ok -> say(UiMessage(if (ok) R.string.backup_saved else R.string.backup_failed)) }

    suspend fun readBackupBytes(input: java.io.InputStream): ByteArray = c.backup.readAll(input)
    fun backupIsLocked(bytes: ByteArray): Boolean = c.backup.isEncrypted(bytes)

    /** Throws BackupCodec exceptions for a wrong password or a file that isn't a backup. */
    suspend fun previewBackup(bytes: ByteArray, password: String?): com.myselavu.app.data.BackupPreview =
        c.backup.readBackup(bytes, password)

    fun restoreBackup(preview: com.myselavu.app.data.BackupPreview) = viewModelScope.launch {
        val ok = runCatching { c.backup.restore(preview) }.isSuccess
        if (ok) {
            c.settings.setLastBackupAt(c.clock.millis())
            repo.ensureUpToDate()
            loadStorage()
        }
        say(UiMessage(if (ok) R.string.restore_done else R.string.restore_failed))
    }

    fun snoozeBackupReminder() = viewModelScope.launch {
        c.settings.snoozeBackupReminder(c.clock.millis() + 30L * 24 * 60 * 60 * 1000)
    }

    /** [years] = 0 means "all past months" (everything before this month). */
    fun clearHistory(years: Int) = viewModelScope.launch {
        val cutoff = if (years <= 0) c.clock.today() else com.myselavu.core.backup.HistoryRetention.cutoff(c.clock.today(), years)
        val result = repo.clearHistoryBefore(cutoff)
        c.backup.compact()
        loadStorage()
        _summaries.value = null
        say(UiMessage(R.string.history_cleared, listOf(result.expensesDeleted, result.monthsDeleted)))
    }

    fun setKeepHistoryYears(years: Int) = viewModelScope.launch {
        c.settings.setKeepHistoryYears(years)
        if (repo.applyRetention() != null) c.backup.compact()
        loadStorage()
    }

    fun deleteEverything() = viewModelScope.launch {
        repo.wipeAll()
        c.backup.compact()
    }

    // ---------- Developer tools (TEST app only) ----------

    fun loadDemoData() = viewModelScope.launch {
        c.demoData.load()
        say(UiMessage(R.string.dev_demo_loaded))
    }

    fun timeTravel(days: Long) = viewModelScope.launch {
        val total = if (days == 0L) 0L else c.clock.offsetDays + days
        c.settings.setTimeTravelDays(total)
        c.clock.offsetDays = total
        repo.ensureUpToDate()
        say(UiMessage(R.string.dev_today_is, listOf(c.clock.today().toString())))
    }

    fun wipeAll() = viewModelScope.launch {
        repo.wipeAll()
        c.settings.setTimeTravelDays(0)
        c.clock.offsetDays = 0
    }

    val categoryTypeOf: (Long) -> CategoryType? = { id -> categories.value.firstOrNull { it.id == id }?.type }

    companion object {
        val Factory = viewModelFactory {
            initializer { AppViewModel((this[APPLICATION_KEY] as MySelavuApp).container) }
        }
    }
}

