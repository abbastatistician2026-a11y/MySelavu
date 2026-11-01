package com.myselavu.app.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.myselavu.app.R
import com.myselavu.app.data.MonthSummary
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.EmptyState
import com.myselavu.app.ui.components.LabeledValue
import com.myselavu.app.ui.components.ScreenScaffold
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.components.formatDate
import com.myselavu.app.ui.components.formatMonth
import com.myselavu.app.ui.theme.LocalStatusColors
import com.myselavu.core.money.Money

@Composable
fun MonthSummaryScreen(
    months: List<MonthSummary>?,
    categories: Map<Long, CategoryEntity>,
    potTotal: Money,
    hidden: Boolean,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.month_summary), onMenu = onMenu, snackbar = snackbar) { padding ->
        if (months == null) return@ScreenScaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PotCard(potTotal, hidden) }
            if (months.isEmpty()) item { EmptyState(stringResource(R.string.no_months_yet)) }
            items(months, key = { it.period.start.toString() }) { m -> MonthCard(m, categories, hidden) }
        }
    }
}

@Composable
private fun PotCard(potTotal: Money, hidden: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("🏦 " + stringResource(R.string.savings_pot), style = MaterialTheme.typography.titleMedium)
            Text(potTotal.display(hidden), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.savings_pot_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun MonthCard(m: MonthSummary, categories: Map<Long, CategoryEntity>, hidden: Boolean) {
    var expanded by rememberSaveable(m.period.start.toString()) { mutableStateOf(false) }
    val danger = LocalStatusColors.current.danger
    val good = LocalStatusColors.current.good
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = !expanded },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatMonth(m.period.labelMonth), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.period_range, formatDate(m.period.start, "d MMM"), formatDate(m.period.end, "d MMM yyyy")),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(stringResource(if (m.closed) R.string.month_closed else R.string.month_current),
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Icon(if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand))
            }
            LabeledValue(stringResource(R.string.income), m.income.display(hidden))
            LabeledValue(stringResource(R.string.fixed_expenses), m.fixed.display(hidden))
            LabeledValue(stringResource(R.string.spent), m.spent.display(hidden))
            LabeledValue(
                stringResource(if (m.closed) R.string.left_over else R.string.left_so_far), m.moneyLeft.display(hidden),
                valueColor = if (m.moneyLeft.isNegative) danger else good,
            )
            if (m.movedToSavings.isPositive) LabeledValue(stringResource(R.string.moved_to_savings), m.movedToSavings.display(hidden))
            val budget = m.allocated + m.carriedIn
            if (budget.isPositive) {
                LinearProgressIndicator(
                    progress = { (m.spent.paise.toFloat() / budget.paise).coerceIn(0f, 1f) },
                    color = if (m.spent > budget) danger else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    m.categories.sortedByDescending { it.spent }.forEach { c ->
                        val cat = categories[c.categoryId]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${cat?.emoji ?: ""} ${cat?.name ?: "?"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                stringResource(R.string.spent_of_allocated_short, c.spent.display(hidden), c.allocated.display(hidden)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (c.spent > c.allocated && c.allocated.isPositive) danger else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (m.categories.isEmpty()) Text(stringResource(R.string.no_expenses_yet), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

data class YearSummary(
    val year: Int,
    val months: List<MonthSummary>,
) {
    val income: Money get() = Money(months.sumOf { it.income.paise })
    val fixed: Money get() = Money(months.sumOf { it.fixed.paise })
    val spent: Money get() = Money(months.sumOf { it.spent.paise })
    val leftOver: Money get() = Money(months.sumOf { it.moneyLeft.paise })
    val movedToSavings: Money get() = Money(months.sumOf { it.movedToSavings.paise })
}

fun groupByYear(months: List<MonthSummary>): List<YearSummary> =
    months.groupBy { it.period.labelMonth.year }.map { (y, list) -> YearSummary(y, list.sortedBy { it.period.start }) }
        .sortedByDescending { it.year }

@Composable
fun YearSummaryScreen(
    months: List<MonthSummary>?,
    categories: Map<Long, CategoryEntity>,
    hidden: Boolean,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.year_summary), onMenu = onMenu, snackbar = snackbar) { padding ->
        if (months == null) return@ScreenScaffold
        val years = groupByYear(months)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (years.isEmpty()) item { EmptyState(stringResource(R.string.no_months_yet)) }
            items(years, key = { it.year }) { y -> YearCard(y, categories, hidden) }
        }
    }
}

@Composable
private fun YearCard(y: YearSummary, categories: Map<Long, CategoryEntity>, hidden: Boolean) {
    val danger = LocalStatusColors.current.danger
    val good = LocalStatusColors.current.good
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(y.year.toString(), style = MaterialTheme.typography.headlineSmall)
            LabeledValue(stringResource(R.string.income), y.income.display(hidden))
            LabeledValue(stringResource(R.string.fixed_expenses), y.fixed.display(hidden))
            LabeledValue(stringResource(R.string.spent), y.spent.display(hidden))
            LabeledValue(stringResource(R.string.left_over), y.leftOver.display(hidden),
                valueColor = if (y.leftOver.isNegative) danger else good)
            if (y.movedToSavings.isPositive) LabeledValue(stringResource(R.string.moved_to_savings), y.movedToSavings.display(hidden))

            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(stringResource(R.string.spent_by_month), style = MaterialTheme.typography.titleMedium)
            val maxSpent = y.months.maxOfOrNull { it.spent.paise }?.coerceAtLeast(1) ?: 1
            y.months.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDate(m.period.labelMonth.atDay(1), "MMM"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
                    LinearProgressIndicator(
                        progress = { m.spent.paise.toFloat() / maxSpent },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f).height(10.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(m.spent.display(hidden), style = MaterialTheme.typography.bodyMedium)
                }
            }

            val byCat = y.months.flatMap { it.categories }.groupBy { it.categoryId }
                .map { (id, list) -> id to Money(list.sumOf { it.spent.paise }) }
                .filter { it.second.isPositive }.sortedByDescending { it.second }
            if (byCat.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(stringResource(R.string.spent_by_category), style = MaterialTheme.typography.titleMedium)
                byCat.forEach { (id, amount) ->
                    val c = categories[id]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${c?.emoji ?: ""} ${c?.name ?: "?"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(120.dp), maxLines = 1)
                        LinearProgressIndicator(
                            progress = { if (y.spent.isPositive) amount.paise.toFloat() / y.spent.paise else 0f },
                            color = c?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f).height(8.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(amount.display(hidden), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
