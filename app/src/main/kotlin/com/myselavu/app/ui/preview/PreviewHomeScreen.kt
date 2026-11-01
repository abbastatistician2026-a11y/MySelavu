package com.myselavu.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myselavu.app.R
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.StatusChip
import com.myselavu.app.ui.components.WarningLine
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.components.levelStyle
import com.myselavu.app.ui.theme.MoneyHero
import com.myselavu.core.budget.CategorySnapshot
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.budget.FixedSnapshot
import com.myselavu.core.budget.PeriodActivity
import com.myselavu.core.budget.PeriodSnapshot
import com.myselavu.core.budget.PeriodSummary
import com.myselavu.core.money.Money
import com.myselavu.core.period.PeriodCalendar
import com.myselavu.core.warning.Warnings
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** A sample category for the Phase 1 design preview. */
private data class SampleCategory(val id: Long, val nameRes: Int, val emoji: String, val color: Color)

private val sampleCategories = listOf(
    SampleCategory(1, R.string.sample_food, "🍛", Color(0xFFE07A2E)),
    SampleCategory(2, R.string.sample_travel, "🛺", Color(0xFF2E86AB)),
    SampleCategory(3, R.string.sample_personal, "🛍️", Color(0xFF8E5BB5)),
    SampleCategory(4, R.string.sample_savings, "🏦", Color(0xFF2E7D32)),
)

/** Builds realistic sample numbers with the REAL budget engine from :core. */
fun sampleSummary(today: LocalDate): PeriodSummary {
    fun r(x: Int) = Money.rupees(x)
    val period = PeriodCalendar.periodContaining(today, 1)
    val snapshot = PeriodSnapshot(
        period = period,
        salary = r(55000),
        fixedExpenses = listOf(
            FixedSnapshot(1, "Rent", r(14000), 5),
            FixedSnapshot(2, "Family fund", r(8000), 10),
            FixedSnapshot(3, "EB bill", r(1500), 15),
        ),
        categories = listOf(
            CategorySnapshot(1, CategoryType.SPENDING, r(8000)),
            CategorySnapshot(2, CategoryType.SPENDING, r(3000)),
            CategorySnapshot(3, CategoryType.SPENDING, r(4000)),
            CategorySnapshot(4, CategoryType.SAVINGS, r(12000)),
        ),
    )
    // Spending scaled to how far into the month we are, with Travel running hot.
    val elapsed = period.daysElapsed(today).toLong()
    val days = period.lengthInDays.toLong()
    fun share(total: Long, factor: Double) = Money.rupees((total * elapsed * factor / days).toLong())
    val activity = PeriodActivity(
        spentByCategory = mapOf(
            1L to share(8000, 0.95),
            2L to share(3000, 1.5).let { if (it > r(2900)) r(2900) else it },
            3L to share(4000, 0.5),
            4L to Money.ZERO,
        ),
        extraIncome = r(2000),
    )
    return PeriodSummary.compute(snapshot, activity, today)
}

@Composable
fun PreviewHomeScreen(today: LocalDate, hideAmounts: Boolean = false) {
    val summary = remember(today) { sampleSummary(today) }
    val period = summary.snapshot.period
    val locale = LocalConfiguration.current.locales[0]
    val monthLabel = remember(period, locale) {
        period.labelMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    }
    val elapsed = period.daysElapsed(today)
    val results = summary.spendingCategories.associate { c ->
        c.categoryId to Warnings.evaluate(c.spent, c.available, elapsed, period.lengthInDays)
    }
    val overall = Warnings.evaluate(summary.spendingSpent, summary.spendingAvailable, elapsed, period.lengthInDays)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.preview_add_coming_soon)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { snackbar.showSnackbar(comingSoon) } },
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_expense)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        monthLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    StatusChip(overall.level)
                }
            }
            item { PreviewBanner() }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BalanceCard(stringResource(R.string.money_left), summary.moneyLeft.display(hideAmounts),
                        stringResource(R.string.money_left_hint), Modifier.weight(1f))
                    BalanceCard(stringResource(R.string.unplanned_money), summary.unplannedMoney.display(hideAmounts),
                        stringResource(R.string.unplanned_money_hint), Modifier.weight(1f))
                }
            }
            item { SafeToSpendCard(summary, hideAmounts) }
            item {
                Text(
                    stringResource(R.string.categories),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            items(summary.categories, key = { it.categoryId }) { status ->
                val meta = sampleCategories.first { it.id == status.categoryId }
                CategoryCard(
                    name = stringResource(meta.nameRes),
                    emoji = meta.emoji,
                    spent = status.spent,
                    available = status.available,
                    isSavings = status.type == CategoryType.SAVINGS,
                    result = results[status.categoryId],
                    hidden = hideAmounts,
                )
            }
        }
    }
}

@Composable
private fun PreviewBanner() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            stringResource(R.string.preview_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun BalanceCard(title: String, amount: String, hint: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(amount, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SafeToSpendCard(summary: PeriodSummary, hidden: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.safe_to_spend_today), style = MaterialTheme.typography.titleMedium)
            Text(summary.safeToSpendToday.display(hidden), style = MoneyHero)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.spent_of_allocated, summary.spendingSpent.display(hidden), summary.spendingAvailable.display(hidden)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                pluralStringResource(R.plurals.days_left, summary.daysLeftIncludingToday, summary.daysLeftIncludingToday),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CategoryCard(
    name: String,
    emoji: String,
    spent: Money,
    available: Money,
    isSavings: Boolean,
    result: com.myselavu.core.warning.WarningResult?,
    hidden: Boolean,
) {
    val barColor = when {
        result != null -> levelStyle(result.level).color
        else -> MaterialTheme.colorScheme.primary
    }
    val progress = if (available.paise <= 0) 1f else (spent.paise.toFloat() / available.paise).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.spent_of_allocated_short, spent.display(hidden), available.display(hidden)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
            if (isSavings) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.savings_category_note), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (result != null) {
                WarningLine(result, hidden)
            }
        }
    }
}
