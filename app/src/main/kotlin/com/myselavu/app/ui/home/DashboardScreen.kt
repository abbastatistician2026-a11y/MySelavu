@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myselavu.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myselavu.app.R
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.PeriodFixedExpenseEntity
import com.myselavu.app.data.db.QuickTemplateEntity
import com.myselavu.app.ui.components.AnimatedMoneyText
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.CategoryAvatar
import com.myselavu.app.ui.components.EmptyState
import com.myselavu.app.ui.components.ScreenScaffold
import com.myselavu.app.ui.components.SectionTitle
import com.myselavu.app.ui.components.StatusChip
import com.myselavu.app.ui.components.WarningLine
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.components.formatDate
import com.myselavu.app.ui.components.formatMonth
import com.myselavu.app.ui.components.levelStyle
import com.myselavu.app.ui.components.methodEmoji
import com.myselavu.app.ui.components.methodLabel
import com.myselavu.app.ui.theme.LocalStatusColors
import com.myselavu.app.ui.theme.MoneyHero
import com.myselavu.core.money.Money

/** Everything the dashboard can ask the app to do. */
data class DashboardActions(
    val onMenu: () -> Unit = {},
    val onAddExpense: (categoryId: Long?) -> Unit = {},
    val onEditExpense: (ExpenseEntity) -> Unit = {},
    val onTemplate: (QuickTemplateEntity) -> Unit = {},
    val onManageTemplates: () -> Unit = {},
    val onToggleHide: () -> Unit = {},
    val onBillPaid: (PeriodFixedExpenseEntity, Boolean) -> Unit = { _, _ -> },
    val onSeeAll: () -> Unit = {},
    val onOpenCategories: () -> Unit = {},
    val onOpenSalary: () -> Unit = {},
)

@Composable
fun DashboardScreen(
    state: DashboardState?,
    templates: List<QuickTemplateEntity>,
    snackbar: SnackbarHostState,
    actions: DashboardActions,
) {
    ScreenScaffold(
        title = stringResource(R.string.app_name),
        onMenu = actions.onMenu,
        snackbar = snackbar,
        actions = {
            if (state != null) {
                IconButton(onClick = actions.onToggleHide) {
                    Icon(
                        if (state.hideAmounts) AppIcons.Visibility else AppIcons.VisibilityOff,
                        contentDescription = stringResource(if (state.hideAmounts) R.string.show_amounts else R.string.hide_amounts),
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { actions.onAddExpense(null) },
                icon = { Icon(AppIcons.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_expense)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (state == null) {
            EmptyState(stringResource(R.string.loading), Modifier.padding(padding))
            return@ScreenScaffold
        }
        val hidden = state.hideAmounts
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(formatMonth(state.period.labelMonth), style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() })
                        Text(
                            stringResource(R.string.period_range, formatDate(state.period.start, "d MMM"), formatDate(state.period.end, "d MMM")),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusChip(state.overall.level)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BalanceCard(stringResource(R.string.money_left), state.moneyLeft, stringResource(R.string.money_left_hint),
                        hidden, Modifier.weight(1f), onClick = actions.onOpenSalary)
                    BalanceCard(stringResource(R.string.unplanned_money), state.unplanned, stringResource(R.string.unplanned_money_hint),
                        hidden, Modifier.weight(1f), onClick = actions.onOpenCategories,
                        negative = state.unplanned.isNegative)
                }
            }
            item { SafeToSpendCard(state, hidden) }
            if (templates.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.quick_add), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = actions.onManageTemplates) { Text(stringResource(R.string.edit)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { t ->
                            AssistChip(
                                onClick = { actions.onTemplate(t) },
                                label = { Text("${t.name} ${Money(t.amountPaise).display(hidden)}") },
                                leadingIcon = { Text(state.categoriesById[t.categoryId]?.emoji ?: "⭐") },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.categories), Modifier.weight(1f))
                    TextButton(onClick = actions.onOpenCategories) { Text(stringResource(R.string.edit)) }
                }
            }
            if (state.cards.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_categories_yet), emoji = "🗂️") }
            }
            items(state.cards, key = { it.category.id }) { card ->
                CategoryCardView(card, hidden, onClick = { actions.onAddExpense(card.category.id) })
            }
            if (state.bills.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.bills_this_month)) }
                items(state.bills, key = { "bill-" + it.row.fixedExpenseId }) { bill ->
                    BillRowView(bill, hidden, onPaid = { actions.onBillPaid(bill.row, it) })
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.recent_expenses), Modifier.weight(1f))
                    TextButton(onClick = actions.onSeeAll) { Text(stringResource(R.string.see_all)) }
                }
            }
            if (state.recent.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_expenses_yet)) }
            }
            items(state.recent, key = { "exp-" + it.id }) { e ->
                ExpenseRow(e, state.categoriesById[e.categoryId], hidden, onClick = { actions.onEditExpense(e) })
            }
        }
    }
}

@Composable
private fun BalanceCard(
    title: String, amount: Money, hint: String, hidden: Boolean, modifier: Modifier,
    onClick: () -> Unit, negative: Boolean = false,
) {
    Card(
        modifier = modifier.semantics(mergeDescendants = true) {}.clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            AnimatedMoneyText(amount, MaterialTheme.typography.headlineSmall, hidden = hidden,
                color = if (negative) LocalStatusColors.current.danger else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SafeToSpendCard(state: DashboardState, hidden: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.safe_to_spend_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.streak > 0) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Flame, null, tint = LocalStatusColors.current.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(pluralStringResource(R.plurals.no_spend_streak, state.streak, state.streak),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            AnimatedMoneyText(state.safeToSpend, MoneyHero, hidden = hidden)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.spent_of_allocated, state.spendingSpent.display(hidden), state.spendingAvailable.display(hidden)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(pluralStringResource(R.plurals.days_left, state.daysLeft, state.daysLeft), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            WarningLine(state.overall, hidden)
        }
    }
}

@Composable
fun CategoryCardView(card: CategoryCard, hidden: Boolean, onClick: () -> Unit) {
    val barColor = card.warning?.let { levelStyle(it.level).color } ?: MaterialTheme.colorScheme.primary
    val progress = if (card.available.paise <= 0) (if (card.spent.isPositive) 1f else 0f)
    else (card.spent.paise.toFloat() / card.available.paise).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.clickable(role = Role.Button, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(card.category.emoji, card.category.color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.category.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.left_amount, card.remaining.display(hidden)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (card.remaining.isNegative) LocalStatusColors.current.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.spent_of_allocated_short, card.spent.display(hidden), card.available.display(hidden)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(10.dp),
            )
            if (card.warning == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.savings_category_note), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                WarningLine(card.warning, hidden)
                if (card.usesLearnedPattern) {
                    Text(stringResource(R.string.uses_your_pattern), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BillRowView(bill: BillRow, hidden: Boolean, onPaid: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = bill.row.paid, onCheckedChange = onPaid)
            Column(Modifier.weight(1f)) {
                Text(bill.row.name, style = MaterialTheme.typography.titleMedium)
                val sub = when {
                    bill.row.paid -> stringResource(R.string.paid)
                    bill.dueDate != null && bill.dueSoon -> stringResource(R.string.due_soon, formatDate(bill.dueDate, "d MMM"))
                    bill.dueDate != null -> stringResource(R.string.due_on, formatDate(bill.dueDate, "d MMM"))
                    else -> stringResource(R.string.mark_paid_hint)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = if (bill.dueSoon) LocalStatusColors.current.careful else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(Money(bill.row.amountPaise).display(hidden), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp))
        }
    }
}

/** One expense in a list. Tap to edit. */
@Composable
fun ExpenseRow(e: ExpenseEntity, category: CategoryEntity?, hidden: Boolean, onClick: () -> Unit, showDate: Boolean = true) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(category?.emoji ?: "❔", category?.color ?: 0xFF888888)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.note.ifBlank { category?.name ?: "" }, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val method = methodLabel(e.method)
                Text(
                    listOfNotNull(category?.name?.takeIf { e.note.isNotBlank() }, if (showDate) formatDate(e.date, "d MMM") else null, method)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(methodEmoji(e.method), modifier = Modifier.padding(horizontal = 6.dp))
            Text(Money(e.amountPaise).display(hidden), style = MaterialTheme.typography.titleMedium)
        }
    }
}
