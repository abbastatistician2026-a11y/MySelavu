@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myselavu.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.myselavu.app.R
import com.myselavu.app.data.CategoryInput
import com.myselavu.app.data.FixedInput
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.ExtraIncomeEntity
import com.myselavu.app.data.db.SalaryRuleEntity
import com.myselavu.app.ui.FixedItem
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.ApplyFromDialog
import com.myselavu.app.ui.components.CategoryAvatar
import com.myselavu.app.ui.components.CategoryColors
import com.myselavu.app.ui.components.CategoryEmojis
import com.myselavu.app.ui.components.EmptyState
import com.myselavu.app.ui.components.LabeledValue
import com.myselavu.app.ui.components.MoneyField
import com.myselavu.app.ui.components.ScreenScaffold
import com.myselavu.app.ui.components.SectionTitle
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.components.formatDate
import com.myselavu.app.ui.components.formatMonth
import com.myselavu.app.ui.components.parseMoneyOrZero
import com.myselavu.app.ui.components.toInputText
import com.myselavu.app.ui.home.CategoryCard
import com.myselavu.app.ui.history.FilterDropdown
import com.myselavu.app.ui.theme.LocalStatusColors
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.money.IndianFormat
import com.myselavu.core.money.Money
import com.myselavu.core.rules.ApplyFrom
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// ======================================================================
// Salary & extra income
// ======================================================================

@Composable
fun SalaryScreen(
    currentSalary: Money,
    history: List<SalaryRuleEntity>,
    extraIncome: List<ExtraIncomeEntity>,
    today: LocalDate,
    hidden: Boolean,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onSetSalary: (Money, ApplyFrom) -> Unit,
    onAddExtra: (Money, LocalDate, String) -> Unit,
    onDeleteExtra: (ExtraIncomeEntity) -> Unit,
) {
    var editSalary by remember { mutableStateOf(false) }
    var addExtra by remember { mutableStateOf(false) }
    ScreenScaffold(title = stringResource(R.string.salary_and_income), onMenu = onMenu, snackbar = snackbar) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.salary_this_month), style = MaterialTheme.typography.titleMedium)
                        Text(currentSalary.display(hidden), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.salary_repeats_hint), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { editSalary = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(AppIcons.Edit, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.change_salary))
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.extra_income), Modifier.weight(1f))
                    TextButton(onClick = { addExtra = true }) { Text(stringResource(R.string.add)) }
                }
                Text(stringResource(R.string.extra_income_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (extraIncome.isEmpty()) item { EmptyState(stringResource(R.string.no_extra_income), emoji = "🎁") }
            items(extraIncome, key = { "x" + it.id }) { e ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Row(Modifier.padding(start = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                            Text(e.note.ifBlank { stringResource(R.string.extra_income) }, style = MaterialTheme.typography.titleMedium)
                            Text(formatDate(e.date), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(Money(e.amountPaise).display(hidden), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { onDeleteExtra(e) }) { Icon(AppIcons.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.salary_history)) }
            if (history.isEmpty()) item { EmptyState(stringResource(R.string.no_salary_yet), emoji = "💼") }
            items(history, key = { "s" + it.id }) { s ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        LabeledValue(stringResource(R.string.from_month, formatMonth(YearMonth.from(s.effectiveFrom))), Money(s.amountPaise).display(hidden))
                        Text(
                            stringResource(R.string.changed_on, formatDate(Instant.ofEpochMilli(s.createdAt).atZone(ZoneId.systemDefault()).toLocalDate())),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (editSalary) {
        AmountThenApplyDialog(
            title = stringResource(R.string.change_salary), initial = currentSalary,
            onDismiss = { editSalary = false },
            onDone = { amount, applyFrom -> onSetSalary(amount, applyFrom); editSalary = false },
        )
    }
    if (addExtra) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addExtra = false },
            title = { Text(stringResource(R.string.add_extra_income)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyField(amount, { amount = it }, stringResource(R.string.amount))
                    OutlinedTextField(note, { note = it.take(60) }, label = { Text(stringResource(R.string.extra_income_note_hint)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = parseMoneyOrZero(amount).isPositive, onClick = {
                    onAddExtra(parseMoneyOrZero(amount), today, note); addExtra = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addExtra = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Ask for an amount, then "this month or next month?". */
@Composable
fun AmountThenApplyDialog(title: String, initial: Money, onDismiss: () -> Unit, onDone: (Money, ApplyFrom) -> Unit) {
    var text by remember { mutableStateOf(if (initial.isPositive) initial.toInputText() else "") }
    var askApply by remember { mutableStateOf(false) }
    if (!askApply) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { MoneyField(text, { text = it }, stringResource(R.string.amount)) },
            confirmButton = {
                TextButton(enabled = IndianFormat.parse(text) != null, onClick = { askApply = true }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    } else {
        ApplyFromDialog(onChoose = { onDone(parseMoneyOrZero(text), it) }, onDismiss = onDismiss)
    }
}

// ======================================================================
// Fixed expenses
// ======================================================================

@Composable
fun FixedExpensesScreen(
    items: List<FixedItem>,
    hidden: Boolean,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onAdd: (FixedInput, ApplyFrom) -> Unit,
    onEdit: (Long, FixedInput, ApplyFrom) -> Unit,
    onDelete: (FixedItem, ApplyFrom) -> Unit,
) {
    var editing by remember { mutableStateOf<FixedItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    val total = Money(items.sumOf { it.inThisMonth?.amountPaise ?: 0L })
    ScreenScaffold(
        title = stringResource(R.string.fixed_expenses), onMenu = onMenu, snackbar = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(AppIcons.Add, null) },
                text = { Text(stringResource(R.string.add_fixed)) })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(stringResource(R.string.fixed_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LabeledValue(stringResource(R.string.total_this_month), total.display(hidden), Modifier.padding(top = 8.dp))
            }
            if (items.isEmpty()) item { EmptyState(stringResource(R.string.no_fixed_yet), emoji = "🏠") }
            items(items, key = { it.id }) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { editing = item },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.latest.name, style = MaterialTheme.typography.titleMedium)
                            val note = when {
                                !item.latest.active -> stringResource(R.string.ends_after_this_month)
                                item.inThisMonth == null -> stringResource(R.string.starts_next_month)
                                item.inThisMonth.amountPaise != item.latest.amountPaise ->
                                    stringResource(R.string.changes_next_month, Money(item.latest.amountPaise).display(hidden))
                                item.latest.dueDay != null -> stringResource(R.string.due_day_n, item.latest.dueDay)
                                else -> stringResource(R.string.every_month)
                            }
                            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(Money(item.inThisMonth?.amountPaise ?: item.latest.amountPaise).display(hidden), style = MaterialTheme.typography.titleMedium)
                        Icon(AppIcons.Edit, stringResource(R.string.edit), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (adding) {
        FixedDialog(null, onDismiss = { adding = false }, onSave = { input, apply -> onAdd(input, apply); adding = false }, onDelete = null)
    }
    editing?.let { item ->
        FixedDialog(
            item, onDismiss = { editing = null },
            onSave = { input, apply -> onEdit(item.id, input, apply); editing = null },
            onDelete = { apply -> onDelete(item, apply); editing = null },
        )
    }
}

@Composable
private fun FixedDialog(
    item: FixedItem?,
    onDismiss: () -> Unit,
    onSave: (FixedInput, ApplyFrom) -> Unit,
    onDelete: ((ApplyFrom) -> Unit)?,
) {
    var name by remember { mutableStateOf(item?.latest?.name ?: "") }
    var amount by remember { mutableStateOf(item?.latest?.amountPaise?.let { Money(it).toInputText() } ?: "") }
    var due by remember { mutableStateOf(item?.latest?.dueDay?.toString() ?: "") }
    var ask by remember { mutableStateOf<String?>(null) } // "save" or "delete"
    val valid = name.isNotBlank() && parseMoneyOrZero(amount).isPositive && (due.isEmpty() || due.toIntOrNull() in 1..31)
    if (ask == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (item == null) R.string.add_fixed else R.string.edit_fixed)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                    if (item == null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(R.string.suggest_rent, R.string.suggest_family_fund, R.string.suggest_eb, R.string.suggest_internet).forEach { res ->
                                val label = stringResource(res)
                                FilterChip(selected = name == label, onClick = { name = label }, label = { Text(label) })
                            }
                        }
                    }
                    MoneyField(amount, { amount = it }, stringResource(R.string.amount))
                    OutlinedTextField(
                        due, { v -> due = v.filter { it.isDigit() }.take(2) },
                        label = { Text(stringResource(R.string.due_day_optional)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (onDelete != null) {
                        OutlinedButton(onClick = { ask = "delete" }) {
                            Icon(AppIcons.Delete, null, tint = LocalStatusColors.current.danger)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.remove))
                        }
                    }
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { ask = "save" }) { Text(stringResource(R.string.next)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    } else {
        ApplyFromDialog(
            onChoose = { apply ->
                if (ask == "delete") onDelete?.invoke(apply)
                else onSave(FixedInput(name.trim(), parseMoneyOrZero(amount), due.toIntOrNull()), apply)
            },
            onDismiss = onDismiss,
        )
    }
}

// ======================================================================
// Categories
// ======================================================================

@Composable
fun CategoriesScreen(
    cards: List<CategoryCard>,
    unplanned: Money,
    hidden: Boolean,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onAdd: (CategoryInput) -> Unit,
    onEdit: (Long, CategoryInput, ApplyFrom) -> Unit,
    onArchive: (CategoryEntity) -> Unit,
    onMove: (Long, Boolean) -> Unit,
    onMoveMoney: (Long, Long, Money) -> Unit,
) {
    var editing by remember { mutableStateOf<CategoryCard?>(null) }
    var adding by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    ScreenScaffold(
        title = stringResource(R.string.categories), onMenu = onMenu, snackbar = snackbar,
        actions = {
            if (cards.size >= 2) IconButton(onClick = { moving = true }) { Icon(AppIcons.Swap, stringResource(R.string.move_money)) }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(AppIcons.Add, null) },
                text = { Text(stringResource(R.string.add_category)) })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { UnplannedBanner(unplanned, hidden) }
            if (cards.isEmpty()) item { EmptyState(stringResource(R.string.no_categories_yet), emoji = "🗂️") }
            items(cards, key = { it.category.id }) { card ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { editing = card },
                ) {
                    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(card.category.emoji, card.category.color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.category.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (card.category.type == CategoryType.SAVINGS) R.string.type_savings else R.string.type_spending),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(card.allocation.display(hidden), style = MaterialTheme.typography.titleMedium)
                        Column {
                            IconButton(onClick = { onMove(card.category.id, true) }) { Icon(AppIcons.ArrowUp, stringResource(R.string.move_up)) }
                            IconButton(onClick = { onMove(card.category.id, false) }) { Icon(AppIcons.ArrowDown, stringResource(R.string.move_down)) }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        CategoryDialog(null, onDismiss = { adding = false }, onSave = { input, _ -> onAdd(input); adding = false }, onArchive = null)
    }
    editing?.let { card ->
        CategoryDialog(
            card, onDismiss = { editing = null },
            onSave = { input, apply -> onEdit(card.category.id, input, apply); editing = null },
            onArchive = { onArchive(card.category); editing = null },
        )
    }
    if (moving) {
        MoveMoneyDialog(cards, onDismiss = { moving = false }, onMove = { f, t, a -> onMoveMoney(f, t, a); moving = false })
    }
}

@Composable
fun UnplannedBanner(unplanned: Money, hidden: Boolean) {
    val negative = unplanned.isNegative
    val colors = LocalStatusColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = if (negative) colors.dangerContainer else MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (negative) Icon(AppIcons.Error, null, tint = colors.danger, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.unplanned_money), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (negative) R.string.unplanned_negative_hint else R.string.unplanned_money_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(unplanned.display(hidden), style = MaterialTheme.typography.headlineSmall,
                color = if (negative) colors.danger else MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Add or edit a category: name, emoji, colour, type and monthly amount. */
@Composable
private fun CategoryDialog(
    card: CategoryCard?,
    onDismiss: () -> Unit,
    onSave: (CategoryInput, ApplyFrom) -> Unit,
    onArchive: (() -> Unit)?,
) {
    val c = card?.category
    var name by remember { mutableStateOf(c?.name ?: "") }
    var emoji by remember { mutableStateOf(c?.emoji ?: CategoryEmojis.first()) }
    var color by remember { mutableStateOf(c?.color ?: CategoryColors.first()) }
    var type by remember { mutableStateOf(c?.type ?: CategoryType.SPENDING) }
    var amount by remember { mutableStateOf(card?.allocation?.let { if (it.isPositive) it.toInputText() else "" } ?: "") }
    var askApply by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && IndianFormat.parse(amount.ifEmpty { "0" }) != null
    fun input() = CategoryInput(name.trim(), emoji, color, type, parseMoneyOrZero(amount))

    if (askApply) {
        ApplyFromDialog(onChoose = { onSave(input(), it) }, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (c == null) R.string.add_category else R.string.edit_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                MoneyField(amount, { amount = it }, stringResource(R.string.monthly_amount))
                Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryEmojis.forEach { e ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable(role = Role.RadioButton) { emoji = e }
                                .semantics { selected = e == emoji },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, style = MaterialTheme.typography.titleLarge) }
                    }
                }
                Text(stringResource(R.string.colour), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryColors.forEachIndexed { i, col ->
                        val desc = stringResource(R.string.colour_n, i + 1)
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(col))
                                .border(if (col == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable(role = Role.RadioButton) { color = col }
                                .semantics { selected = col == color; contentDescription = desc },
                        )
                    }
                }
                Text(stringResource(R.string.category_type), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == CategoryType.SPENDING, onClick = { type = CategoryType.SPENDING },
                        label = { Text(stringResource(R.string.type_spending)) })
                    FilterChip(selected = type == CategoryType.SAVINGS, onClick = { type = CategoryType.SAVINGS },
                        label = { Text(stringResource(R.string.type_savings)) })
                }
                Text(stringResource(R.string.type_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onArchive != null) {
                    OutlinedButton(onClick = onArchive) {
                        Icon(AppIcons.Delete, null, tint = LocalStatusColors.current.danger)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.archive_category))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                when {
                    card == null -> onSave(input(), ApplyFrom.THIS_PERIOD)
                    parseMoneyOrZero(amount) == card.allocation -> onSave(input(), ApplyFrom.THIS_PERIOD)
                    else -> askApply = true
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MoveMoneyDialog(cards: List<CategoryCard>, onDismiss: () -> Unit, onMove: (Long, Long, Money) -> Unit) {
    var from by remember { mutableStateOf(cards.maxByOrNull { it.remaining.paise }?.category) }
    var to by remember { mutableStateOf(cards.minByOrNull { it.remaining.paise }?.category) }
    var amount by remember { mutableStateOf("") }
    val value = parseMoneyOrZero(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_money)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.move_money_hint), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.from), style = MaterialTheme.typography.labelLarge)
                FilterDropdown(
                    label = from?.let { "${it.emoji} ${it.name}" } ?: "",
                    options = cards.map { c -> "${c.category.emoji} ${c.category.name}" to { from = c.category } },
                )
                Text(stringResource(R.string.to), style = MaterialTheme.typography.labelLarge)
                FilterDropdown(
                    label = to?.let { "${it.emoji} ${it.name}" } ?: "",
                    options = cards.map { c -> "${c.category.emoji} ${c.category.name}" to { to = c.category } },
                )
                MoneyField(amount, { amount = it }, stringResource(R.string.amount))
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.isPositive && from != null && to != null && from?.id != to?.id,
                onClick = { onMove(from!!.id, to!!.id, value) },
            ) { Text(stringResource(R.string.move)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
