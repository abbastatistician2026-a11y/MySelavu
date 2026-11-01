@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myselavu.app.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.myselavu.app.BuildConfig
import com.myselavu.app.R
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.settings.AppSettings
import com.myselavu.app.data.settings.ThemeMode
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.ApplyFromDialog
import com.myselavu.app.ui.components.ConfirmDialog
import com.myselavu.app.ui.components.EmptyState
import com.myselavu.app.ui.components.LabeledValue
import com.myselavu.app.ui.components.ScreenScaffold
import com.myselavu.app.ui.components.SectionTitle
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.components.formatDate
import com.myselavu.app.ui.history.FilterDropdown
import com.myselavu.app.ui.theme.LocalStatusColors
import com.myselavu.core.budget.UnspentMode
import com.myselavu.core.plan.PlanSuggestion
import com.myselavu.core.rules.ApplyFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDate

// ======================================================================
// Suggested plan
// ======================================================================

@Composable
fun PlanScreen(
    loadPlan: suspend () -> PlanSuggestion.Plan?,
    categories: Map<Long, CategoryEntity>,
    hidden: Boolean,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onApply: (PlanSuggestion.Plan, ApplyFrom) -> Unit,
) {
    var plan by remember { mutableStateOf<PlanSuggestion.Plan?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { plan = loadPlan(); loaded = true }
    ScreenScaffold(title = stringResource(R.string.suggested_plan), onMenu = onMenu, snackbar = snackbar) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.plan_intro), style = MaterialTheme.typography.bodyLarge)
            val p = plan
            if (!loaded) return@Column
            if (p == null || p.suggestions.isEmpty()) {
                EmptyState(stringResource(R.string.no_categories_yet), emoji = "💡")
                return@Column
            }
            if (!p.hasHistory) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(stringResource(R.string.plan_needs_history), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            p.suggestions.forEach { s ->
                val c = categories[s.categoryId]
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${c?.emoji ?: ""} ${c?.name ?: "?"}", style = MaterialTheme.typography.titleMedium)
                        LabeledValue(stringResource(R.string.plan_now), s.current.display(hidden))
                        LabeledValue(
                            stringResource(R.string.plan_suggested), s.suggested.display(hidden),
                            valueColor = when {
                                s.suggested > s.current -> LocalStatusColors.current.careful
                                s.suggested < s.current -> LocalStatusColors.current.good
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        val why = when (s.reason) {
                            PlanSuggestion.Reason.BASED_ON_AVERAGE ->
                                stringResource(R.string.plan_reason_average, (s.averageSpent ?: s.suggested).display(hidden))
                            PlanSuggestion.Reason.SCALED_TO_FIT -> stringResource(R.string.plan_reason_scaled)
                            PlanSuggestion.Reason.NO_HISTORY -> stringResource(R.string.plan_reason_no_history)
                            PlanSuggestion.Reason.SAVINGS_KEPT -> stringResource(R.string.plan_reason_savings)
                            PlanSuggestion.Reason.SAVINGS_EXTRA -> stringResource(R.string.plan_reason_savings_extra)
                        }
                        Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            LabeledValue(stringResource(R.string.unplanned_money), p.unplanned.display(hidden))
            Button(onClick = { ask = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.use_this_plan))
            }
        }
    }
    if (ask) {
        ApplyFromDialog(onChoose = { plan?.let { p -> onApply(p, it) }; ask = false }, onDismiss = { ask = false })
    }
}

// ======================================================================
// Excel export
// ======================================================================

@Composable
fun ExportScreen(
    today: LocalDate,
    periodStart: LocalDate?,
    periodEnd: LocalDate?,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    export: suspend (OutputStream, LocalDate?, LocalDate?) -> Unit,
    onDone: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf(0) } // 0 all, 1 this month, 2 range
    var range by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var pickRange by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                        when (choice) {
                            1 -> export(out, periodStart, periodEnd)
                            2 -> export(out, range?.first, range?.second)
                            else -> export(out, null, null)
                        }
                    }
                }
            }.isSuccess
            busy = false
            onDone(ok)
        }
    }
    ScreenScaffold(title = stringResource(R.string.export_excel), onMenu = onMenu, snackbar = snackbar) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.export_intro), style = MaterialTheme.typography.bodyLarge)
            val options = listOf(
                stringResource(R.string.export_all),
                stringResource(R.string.export_this_month),
                range?.let { "${formatDate(it.first)} – ${formatDate(it.second)}" } ?: stringResource(R.string.export_range),
            )
            options.forEachIndexed { i, label ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .selectable(selected = choice == i, role = Role.RadioButton, onClick = {
                            choice = i
                            if (i == 2) pickRange = true
                        }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice == i, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(stringResource(R.string.export_sheets), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = !busy && (choice != 2 || range != null),
                onClick = {
                    val name = when (choice) {
                        1 -> "MySelavu-${periodStart ?: today}.xlsx"
                        2 -> "MySelavu-${range?.first}-to-${range?.second}.xlsx"
                        else -> "MySelavu-all-$today.xlsx"
                    }
                    launcher.launch(name)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(AppIcons.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (busy) R.string.exporting else R.string.save_excel_file))
            }
        }
    }
    if (pickRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null, onClick = {
                    val s = state.selectedStartDateMillis!!
                    val e = state.selectedEndDateMillis ?: s
                    range = LocalDate.ofEpochDay(Math.floorDiv(s, 86_400_000L)) to LocalDate.ofEpochDay(Math.floorDiv(e, 86_400_000L))
                    pickRange = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DateRangePicker(state = state, modifier = Modifier.weight(1f)) }
    }
}

// ======================================================================
// Settings & profile
// ======================================================================

@Composable
fun SettingsScreen(
    settings: AppSettings,
    language: String,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onName: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onStartDay: (Int) -> Unit,
    onUnspentMode: (UnspentMode) -> Unit,
    onHideAmounts: (Boolean) -> Unit,
    onOpenDevTools: (() -> Unit)?,
) {
    var name by remember(settings.profileName) { mutableStateOf(settings.profileName) }
    ScreenScaffold(title = stringResource(R.string.settings), onMenu = onMenu, snackbar = snackbar) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.profile))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.your_name)) },
                    singleLine = true, modifier = Modifier.weight(1f))
                TextButton(enabled = name.trim() != settings.profileName, onClick = { onName(name) }) { Text(stringResource(R.string.save)) }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = language == "en", onClick = { onLanguage("en") }, label = { Text("English") },
                    modifier = Modifier.heightIn(min = 48.dp))
                FilterChip(selected = language == "ta", onClick = { onLanguage("ta") }, label = { Text("தமிழ்") },
                    modifier = Modifier.heightIn(min = 48.dp))
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.app_theme))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
                    .forEach { (mode, label) ->
                        FilterChip(selected = settings.theme == mode, onClick = { onTheme(mode) }, label = { Text(stringResource(label)) },
                            modifier = Modifier.heightIn(min = 48.dp))
                    }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.hide_amounts_setting), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = settings.hideAmounts, onCheckedChange = onHideAmounts)
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.budget_month))
            Text(stringResource(R.string.start_day_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterDropdown(
                label = stringResource(R.string.start_day_value, settings.budgetStartDay),
                options = (1..28).map { d -> "$d" to { onStartDay(d) } },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionTitle(stringResource(R.string.unspent_money))
            Text(stringResource(R.string.unspent_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                UnspentMode.MOVE_TO_SAVINGS to (R.string.unspent_savings to R.string.unspent_savings_hint),
                UnspentMode.CARRY_FORWARD to (R.string.unspent_carry to R.string.unspent_carry_hint),
                UnspentMode.RESET to (R.string.unspent_reset to R.string.unspent_reset_hint),
            ).forEach { (mode, labels) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected = settings.unspentMode == mode, role = Role.RadioButton, onClick = { onUnspentMode(mode) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.unspentMode == mode, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(labels.first), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(labels.second), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()
            SectionTitle(stringResource(R.string.about))
            Text(stringResource(R.string.about_text, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            if (onOpenDevTools != null) {
                OutlinedButton(onClick = onOpenDevTools) {
                    Icon(AppIcons.Build, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.dev_tools))
                }
            }
        }
    }
}

// ======================================================================
// Developer tools (TEST app only)
// ======================================================================

@Composable
fun DevToolsScreen(
    today: LocalDate,
    offsetDays: Long,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onDemo: () -> Unit,
    onTimeTravel: (Long) -> Unit,
    onWipe: () -> Unit,
) {
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.dev_tools), onMenu = onMenu, snackbar = snackbar) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.dev_intro), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { confirmDemo = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.dev_load_demo))
            }
            HorizontalDivider()
            SectionTitle(stringResource(R.string.dev_time_travel))
            Text(stringResource(R.string.dev_fake_today, formatDate(today), offsetDays), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1L to "+1 day", 7L to "+7 days", 30L to "+30 days", 92L to "+3 months").forEach { (d, label) ->
                    OutlinedButton(onClick = { onTimeTravel(d) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
                }
                OutlinedButton(onClick = { onTimeTravel(0) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.dev_back_to_real))
                }
            }
            HorizontalDivider()
            OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(AppIcons.Delete, null, tint = LocalStatusColors.current.danger)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dev_wipe))
            }
        }
    }
    if (confirmWipe) {
        ConfirmDialog(stringResource(R.string.dev_wipe), stringResource(R.string.dev_wipe_confirm), stringResource(R.string.dev_wipe),
            onConfirm = { onWipe(); confirmWipe = false }, onDismiss = { confirmWipe = false })
    }
    if (confirmDemo) {
        ConfirmDialog(stringResource(R.string.dev_load_demo), stringResource(R.string.dev_demo_confirm), stringResource(R.string.dev_load_demo),
            onConfirm = { onDemo(); confirmDemo = false }, onDismiss = { confirmDemo = false })
    }
}
