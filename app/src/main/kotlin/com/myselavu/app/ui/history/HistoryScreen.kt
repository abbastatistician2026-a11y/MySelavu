@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.myselavu.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myselavu.app.AppContainer
import com.myselavu.app.MySelavuApp
import com.myselavu.app.R
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.EmptyState
import com.myselavu.app.ui.components.ScreenScaffold
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.components.formatDate
import com.myselavu.app.ui.components.methodEmoji
import com.myselavu.app.ui.components.methodLabel
import com.myselavu.app.ui.home.ExpenseRow
import com.myselavu.app.ui.theme.LocalStatusColors
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.money.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class RangePreset { THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL, CUSTOM }

data class HistoryFilter(
    val from: LocalDate,
    val to: LocalDate,
    val preset: RangePreset,
    val categoryId: Long? = null,
    val method: PaymentMethod? = null,
    val query: String = "",
)

data class DayGroup(val date: LocalDate, val total: Money, val items: List<ExpenseEntity>)

data class HistoryState(
    val filter: HistoryFilter,
    val total: Money,
    val count: Int,
    val byCategory: List<Pair<Long, Money>>,
    val days: List<DayGroup>,
)

/** Pure: filters and groups expenses by day (newest first) with daily totals. */
fun buildHistory(all: List<ExpenseEntity>, filter: HistoryFilter, categories: Map<Long, CategoryEntity>): HistoryState {
    val q = filter.query.trim().lowercase()
    val list = all.filter { e ->
        (filter.categoryId == null || e.categoryId == filter.categoryId) &&
            (filter.method == null || e.method == filter.method) &&
            (q.isEmpty() || e.note.lowercase().contains(q) ||
                (categories[e.categoryId]?.name?.lowercase()?.contains(q) == true) ||
                (e.amountPaise / 100).toString() == q || Money(e.amountPaise).toString().contains(q))
    }
    val days = list.groupBy { it.date }.toSortedMap(compareByDescending { it }).map { (d, items) ->
        DayGroup(d, Money(items.sumOf { it.amountPaise }), items.sortedByDescending { it.createdAt })
    }
    val byCat = list.groupBy { it.categoryId }.map { (id, items) -> id to Money(items.sumOf { it.amountPaise }) }
        .sortedByDescending { it.second }
    return HistoryState(filter, Money(list.sumOf { it.amountPaise }), list.size, byCat, days)
}

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(presetFilter(RangePreset.THIS_MONTH))

    val state: StateFlow<HistoryState?> = filter.flatMapLatest { f ->
        combine(c.database.expenseDao().observeBetween(f.from, f.to), c.database.rulesDao().observeAllCategories()) { list, cats ->
            buildHistory(list, f, cats.associateBy { it.id })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun presetFilter(p: RangePreset, keep: HistoryFilter? = null): HistoryFilter {
        val today = c.clock.today()
        val (from, to) = when (p) {
            RangePreset.THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(today.lengthOfMonth())
            RangePreset.LAST_MONTH -> today.minusMonths(1).withDayOfMonth(1) to today.withDayOfMonth(1).minusDays(1)
            RangePreset.THIS_YEAR -> today.withDayOfYear(1) to today.withDayOfYear(today.lengthOfYear())
            RangePreset.ALL, RangePreset.CUSTOM -> LocalDate.of(2000, 1, 1) to LocalDate.of(2100, 12, 31)
        }
        return (keep ?: HistoryFilter(from, to, p)).copy(from = from, to = to, preset = p)
    }

    fun setPreset(p: RangePreset) { filter.value = presetFilter(p, filter.value) }
    fun setCustom(from: LocalDate, to: LocalDate) { filter.value = filter.value.copy(from = from, to = to, preset = RangePreset.CUSTOM) }
    fun setCategory(id: Long?) { filter.value = filter.value.copy(categoryId = id) }
    fun setMethod(m: PaymentMethod?) { filter.value = filter.value.copy(method = m) }
    fun setQuery(q: String) { filter.value = filter.value.copy(query = q) }

    companion object {
        val Factory = viewModelFactory { initializer { HistoryViewModel((this[APPLICATION_KEY] as MySelavuApp).container) } }
    }
}

@Composable
fun HistoryScreen(
    state: HistoryState?,
    categories: List<CategoryEntity>,
    hidden: Boolean,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onPreset: (RangePreset) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    onCategory: (Long?) -> Unit,
    onMethod: (PaymentMethod?) -> Unit,
    onQuery: (String) -> Unit,
    onEdit: (ExpenseEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
) {
    val byId = remember(categories) { categories.associateBy { it.id } }
    var showRange by remember { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.history), onMenu = onMenu, snackbar = snackbar) { padding ->
        if (state == null) return@ScreenScaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.filter.query, onValueChange = onQuery,
                    label = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(AppIcons.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val presets = listOf(
                        RangePreset.THIS_MONTH to R.string.range_this_month,
                        RangePreset.LAST_MONTH to R.string.range_last_month,
                        RangePreset.THIS_YEAR to R.string.range_this_year,
                        RangePreset.ALL to R.string.range_all,
                    )
                    presets.forEach { (p, label) ->
                        FilterChip(selected = state.filter.preset == p, onClick = { onPreset(p) },
                            label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                    FilterChip(
                        selected = state.filter.preset == RangePreset.CUSTOM, onClick = { showRange = true },
                        label = {
                            Text(
                                if (state.filter.preset == RangePreset.CUSTOM)
                                    "${formatDate(state.filter.from, "d MMM yy")} – ${formatDate(state.filter.to, "d MMM yy")}"
                                else stringResource(R.string.range_choose)
                            )
                        },
                        leadingIcon = { Icon(AppIcons.Calendar, null) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDropdown(
                        label = state.filter.categoryId?.let { id -> byId[id]?.let { "${it.emoji} ${it.name}" } } ?: stringResource(R.string.all_categories),
                        options = listOf<Pair<String, () -> Unit>>(stringResource(R.string.all_categories) to { onCategory(null) }) +
                            categories.map { c -> "${c.emoji} ${c.name}" to { onCategory(c.id) } },
                        modifier = Modifier.weight(1f),
                    )
                    FilterDropdown(
                        label = state.filter.method?.let { "${methodEmoji(it)} ${methodLabel(it)}" } ?: stringResource(R.string.all_methods),
                        options = listOf<Pair<String, () -> Unit>>(stringResource(R.string.all_methods) to { onMethod(null) }) +
                            PaymentMethod.entries.map { m -> "${methodEmoji(m)} ${methodLabel(m)}" to { onMethod(m) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item { TotalsCard(state, byId, hidden) }
            if (state.days.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_expenses_in_range)) }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date}") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp).semantics(mergeDescendants = true) { heading() }) {
                        Text(formatDate(day.date, "EEE, d MMM yyyy"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(day.total.display(hidden), style = MaterialTheme.typography.titleSmall)
                    }
                }
                items(day.items, key = { it.id }) { e ->
                    SwipeToDelete(onDelete = { onDelete(e) }) {
                        ExpenseRow(e, byId[e.categoryId], hidden, onClick = { onEdit(e) }, showDate = false)
                    }
                }
            }
        }
    }

    if (showRange) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state?.filter?.from?.takeIf { state.filter.preset != RangePreset.ALL }?.toEpochDay()?.times(86_400_000L),
            initialSelectedEndDateMillis = state?.filter?.to?.takeIf { state.filter.preset != RangePreset.ALL }?.toEpochDay()?.times(86_400_000L),
        )
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(
                    enabled = rangeState.selectedStartDateMillis != null,
                    onClick = {
                        val s = rangeState.selectedStartDateMillis
                        if (s != null) {
                            val e = rangeState.selectedEndDateMillis ?: s
                            onCustomRange(LocalDate.ofEpochDay(Math.floorDiv(s, 86_400_000L)), LocalDate.ofEpochDay(Math.floorDiv(e, 86_400_000L)))
                        }
                        showRange = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TotalsCard(state: HistoryState, byId: Map<Long, CategoryEntity>, hidden: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.total_spent_in_range), style = MaterialTheme.typography.titleMedium)
            Text(state.total.display(hidden), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.n_expenses, state.count), style = MaterialTheme.typography.bodyMedium)
            if (state.byCategory.isNotEmpty() && state.total.isPositive) {
                Spacer(Modifier.height(4.dp))
                state.byCategory.take(8).forEach { (id, amount) ->
                    val c = byId[id]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c?.emoji ?: ""} ${c?.name ?: "?"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(130.dp), maxLines = 1)
                        LinearProgressIndicator(
                            progress = { (amount.paise.toFloat() / state.total.paise).coerceIn(0f, 1f) },
                            color = c?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.weight(1f).height(8.dp).padding(horizontal = 8.dp),
                        )
                        Text(amount.display(hidden), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun FilterDropdown(label: String, options: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = false, onClick = { open = true },
            label = { Text(label, maxLines = 1) },
            trailingIcon = { Icon(AppIcons.ExpandMore, null) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { action(); open = false })
            }
        }
    }
}

/** Swipe left to delete (with Undo in the snackbar). */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Delete, contentDescription = stringResource(R.string.delete), tint = LocalStatusColors.current.danger)
            }
        },
    ) { content() }
}
