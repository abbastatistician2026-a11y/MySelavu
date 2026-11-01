package com.myselavu.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myselavu.app.BuildConfig
import com.myselavu.app.R
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.settings.LocaleHelper
import com.myselavu.app.data.settings.ThemeMode
import com.myselavu.app.ui.budget.CategoriesScreen
import com.myselavu.app.ui.budget.FixedExpensesScreen
import com.myselavu.app.ui.budget.SalaryScreen
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.expense.AddExpenseSheet
import com.myselavu.app.ui.history.HistoryScreen
import com.myselavu.app.ui.history.HistoryViewModel
import com.myselavu.app.ui.home.DashboardActions
import com.myselavu.app.ui.home.DashboardScreen
import com.myselavu.app.ui.more.DevToolsScreen
import com.myselavu.app.ui.more.ExportScreen
import com.myselavu.app.ui.more.PlanScreen
import com.myselavu.app.ui.more.SettingsScreen
import com.myselavu.app.ui.setup.SetupWizard
import com.myselavu.app.ui.summary.MonthSummaryScreen
import com.myselavu.app.ui.summary.YearSummaryScreen
import com.myselavu.app.ui.theme.MySelavuTheme
import com.myselavu.core.money.Money
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch

enum class Dest { HOME, HISTORY, MONTHS, YEARS, SALARY, FIXED, CATEGORIES, PLAN, EXPORT, SETTINGS, DEV }

/** What the add/edit expense sheet is showing. */
private data class SheetRequest(val existing: ExpenseEntity?, val presetCategory: Long?)

@Composable
fun MySelavuRoot(vm: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    val dark = when (s?.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    MySelavuTheme(darkTheme = dark) {
        val context = LocalContext.current
        val language = remember { LocaleHelper.language(context) }
        val changeLanguage: (String) -> Unit = { tag ->
            if (tag != language) {
                LocaleHelper.setLanguage(context, tag)
                (context as? Activity)?.recreate()
            }
        }
        when {
            s == null -> Spacer(Modifier.fillMaxSize())
            !s.setupDone -> SetupWizard(language, changeLanguage) { salary, fixed, cats, startDay, name ->
                vm.completeSetup(salary, fixed, cats, startDay, name)
            }
            else -> MainApp(vm, language, changeLanguage)
        }
    }
}

private data class DrawerEntry(val dest: Dest, val label: Int, val icon: ImageVector)

@Composable
private fun MainApp(vm: AppViewModel, language: String, changeLanguage: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val dashboard by vm.dashboard.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val byId = remember(categories) { categories.associateBy { it.id } }
    val hidden = s.hideAmounts

    val backStack = remember { mutableStateListOf(Dest.HOME) }
    val current = backStack.last()
    fun go(d: Dest) {
        if (d == Dest.HOME) { backStack.clear(); backStack.add(Dest.HOME) }
        else if (current != d) { backStack.remove(d); backStack.add(d) }
    }
    BackHandler(enabled = backStack.size > 1) { backStack.removeAt(backStack.lastIndex) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenu: () -> Unit = { scope.launch { drawerState.open() } }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val text = context.getString(msg.text, *msg.args.toTypedArray())
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = msg.actionLabel?.let { context.getString(it) },
                withDismissAction = msg.action == null,
                duration = if (msg.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }

    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    var ranked by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(sheet) { if (sheet != null) ranked = vm.rankedCategoryIds() }
    var manageTemplates by remember { mutableStateOf(false) }

    val entries = buildList {
        add(DrawerEntry(Dest.HOME, R.string.nav_home, AppIcons.Home))
        add(DrawerEntry(Dest.HISTORY, R.string.history, AppIcons.ListIcon))
        add(DrawerEntry(Dest.MONTHS, R.string.month_summary, AppIcons.Calendar))
        add(DrawerEntry(Dest.YEARS, R.string.year_summary, AppIcons.BarChart))
        add(DrawerEntry(Dest.SALARY, R.string.salary_and_income, AppIcons.Wallet))
        add(DrawerEntry(Dest.FIXED, R.string.fixed_expenses, AppIcons.Receipt))
        add(DrawerEntry(Dest.CATEGORIES, R.string.categories, AppIcons.Category))
        add(DrawerEntry(Dest.PLAN, R.string.suggested_plan, AppIcons.Lightbulb))
        add(DrawerEntry(Dest.EXPORT, R.string.export_excel, AppIcons.Download))
        add(DrawerEntry(Dest.SETTINGS, R.string.settings_profile, AppIcons.Settings))
        if (BuildConfig.DEBUG) add(DrawerEntry(Dest.DEV, R.string.dev_tools, AppIcons.Build))
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text("🪙", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 12.dp, top = 8.dp))
                    Text(
                        if (s.profileName.isBlank()) stringResource(R.string.hello) else stringResource(R.string.hello_name, s.profileName),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                    )
                    dashboard?.let { d ->
                        Text(
                            stringResource(R.string.drawer_money_left, d.moneyLeft.display(hidden)),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    entries.forEach { e ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(e.label)) },
                            icon = { Icon(e.icon, null) },
                            selected = current == e.dest,
                            onClick = { go(e.dest); scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        },
    ) {
        when (current) {
            Dest.HOME -> DashboardScreen(
                state = dashboard, templates = templates, snackbar = snackbar,
                actions = DashboardActions(
                    onMenu = openMenu,
                    onAddExpense = { sheet = SheetRequest(null, it) },
                    onEditExpense = { sheet = SheetRequest(it, null) },
                    onTemplate = { vm.addFromTemplate(it) },
                    onManageTemplates = { manageTemplates = true },
                    onToggleHide = { vm.setHideAmounts(!hidden) },
                    onBillPaid = { row, paid -> vm.setFixedPaid(row, paid) },
                    onSeeAll = { go(Dest.HISTORY) },
                    onOpenCategories = { go(Dest.CATEGORIES) },
                    onOpenSalary = { go(Dest.SALARY) },
                ),
            )
            Dest.HISTORY -> {
                val hvm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
                val state by hvm.state.collectAsStateWithLifecycle()
                HistoryScreen(
                    state = state, categories = categories, hidden = hidden, snackbar = snackbar, onMenu = openMenu,
                    onPreset = hvm::setPreset, onCustomRange = hvm::setCustom, onCategory = hvm::setCategory,
                    onMethod = hvm::setMethod, onQuery = hvm::setQuery,
                    onEdit = { sheet = SheetRequest(it, null) }, onDelete = { vm.deleteExpense(it) },
                )
            }
            Dest.MONTHS, Dest.YEARS -> {
                LaunchedEffect(current) { vm.loadSummaries() }
                val months by vm.summaries.collectAsStateWithLifecycle()
                val pot by vm.potTotal.collectAsStateWithLifecycle()
                if (current == Dest.MONTHS) MonthSummaryScreen(months, byId, pot, hidden, snackbar, openMenu)
                else YearSummaryScreen(months, byId, hidden, snackbar, openMenu)
            }
            Dest.SALARY -> {
                val history by vm.salaryHistory.collectAsStateWithLifecycle()
                val extra by vm.extraIncome.collectAsStateWithLifecycle()
                SalaryScreen(
                    currentSalary = dashboard?.salary ?: Money.ZERO, history = history, extraIncome = extra,
                    today = vm.today(), hidden = hidden, snackbar = snackbar, onMenu = openMenu,
                    onSetSalary = { a, f -> vm.setSalary(a, f) },
                    onAddExtra = { a, d, n -> vm.addExtraIncome(a, d, n) },
                    onDeleteExtra = { vm.deleteExtraIncome(it) },
                )
            }
            Dest.FIXED -> {
                val items by vm.fixedItems.collectAsStateWithLifecycle()
                FixedExpensesScreen(
                    items = items, hidden = hidden, snackbar = snackbar, onMenu = openMenu,
                    onAdd = { i, f -> vm.addFixed(i, f) },
                    onEdit = { id, i, f -> vm.editFixed(id, i, f) },
                    onDelete = { item, f -> vm.deleteFixed(item, f) },
                )
            }
            Dest.CATEGORIES -> CategoriesScreen(
                cards = dashboard?.cards.orEmpty(), unplanned = dashboard?.unplanned ?: Money.ZERO,
                hidden = hidden, snackbar = snackbar, onMenu = openMenu,
                onAdd = { vm.addCategory(it) },
                onEdit = { id, input, f -> vm.editCategory(id, input, f) },
                onArchive = { vm.archiveCategory(it) },
                onMove = { id, up -> vm.moveCategory(id, up) },
                onMoveMoney = { a, b, m -> vm.moveMoney(a, b, m) },
            )
            Dest.PLAN -> PlanScreen(
                loadPlan = { vm.suggestPlan() }, categories = byId, hidden = hidden, snackbar = snackbar, onMenu = openMenu,
                onApply = { p, f -> vm.applyPlan(p, f) },
            )
            Dest.EXPORT -> ExportScreen(
                today = vm.today(), periodStart = dashboard?.period?.start, periodEnd = dashboard?.period?.end,
                snackbar = snackbar, onMenu = openMenu,
                export = { out, from, to -> vm.exportExcel(out, from, to) },
                onDone = { vm.exportDone(it) },
            )
            Dest.SETTINGS -> SettingsScreen(
                settings = s, language = language, snackbar = snackbar, onMenu = openMenu,
                onName = { vm.setProfileName(it) }, onLanguage = changeLanguage, onTheme = { vm.setTheme(it) },
                onStartDay = { vm.setStartDay(it) }, onUnspentMode = { vm.setUnspentMode(it) },
                onHideAmounts = { vm.setHideAmounts(it) },
                onOpenDevTools = if (BuildConfig.DEBUG) ({ go(Dest.DEV) }) else null,
            )
            Dest.DEV -> DevToolsScreen(
                today = vm.today(), offsetDays = s.timeTravelDays, snackbar = snackbar, onMenu = openMenu,
                onDemo = { vm.loadDemoData(); go(Dest.HOME) },
                onTimeTravel = { vm.timeTravel(it) },
                onWipe = { vm.wipeAll() },
            )
        }
    }

    sheet?.let { req ->
        AddExpenseSheet(
            existing = req.existing,
            presetCategoryId = req.presetCategory,
            categories = categories,
            rankedIds = ranked,
            templates = templates,
            lastMethod = s.lastPaymentMethod,
            today = vm.today(),
            onSave = { e, asTemplate, name -> vm.saveExpense(e, asTemplate, name) },
            onTemplate = { vm.addFromTemplate(it) },
            onDelete = { vm.deleteExpense(it) },
            onDuplicate = { vm.duplicateExpense(it, toToday = true) },
            onDismiss = { sheet = null },
        )
    }

    if (manageTemplates) {
        TemplatesDialog(templates, hidden, onDelete = { vm.deleteTemplate(it) }, onDismiss = { manageTemplates = false })
    }
}

@Composable
private fun TemplatesDialog(
    templates: List<com.myselavu.app.data.db.QuickTemplateEntity>,
    hidden: Boolean,
    onDelete: (com.myselavu.app.data.db.QuickTemplateEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_add)) },
        text = {
            Column {
                Text(stringResource(R.string.templates_hint), style = MaterialTheme.typography.bodyMedium)
                templates.forEach { t ->
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${t.name} ${Money(t.amountPaise).display(hidden)}", modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { onDelete(t) }) {
                            Icon(AppIcons.Delete, stringResource(R.string.delete))
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
