@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myselavu.app.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myselavu.app.R
import com.myselavu.app.data.db.CategoryEntity
import com.myselavu.app.data.db.ExpenseEntity
import com.myselavu.app.data.db.QuickTemplateEntity
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.display
import com.myselavu.app.ui.components.formatDate
import com.myselavu.app.ui.components.methodEmoji
import com.myselavu.app.ui.components.methodLabel
import com.myselavu.app.ui.components.tick
import com.myselavu.app.ui.components.toInputText
import com.myselavu.core.budget.PaymentMethod
import com.myselavu.core.money.AmountExpression
import com.myselavu.core.money.IndianFormat
import com.myselavu.core.money.Money
import java.time.LocalDate

/**
 * The bottom sheet for adding or editing an expense.
 * Fast path: tap +, type the amount, (category is pre-chosen from your habits), tap Save.
 */
@Composable
fun AddExpenseSheet(
    existing: ExpenseEntity?,
    presetCategoryId: Long?,
    categories: List<CategoryEntity>,
    rankedIds: List<Long>,
    templates: List<QuickTemplateEntity>,
    lastMethod: PaymentMethod,
    today: LocalDate,
    onSave: (ExpenseEntity, saveAsTemplate: Boolean, templateName: String) -> Unit,
    onTemplate: (QuickTemplateEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
    onDuplicate: (ExpenseEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        AddExpenseContent(
            existing, presetCategoryId, categories, rankedIds, templates, lastMethod, today,
            onSave = { e, t, n -> onSave(e, t, n); onDismiss() },
            onTemplate = { onTemplate(it); onDismiss() },
            onDelete = { onDelete(it); onDismiss() },
            onDuplicate = { onDuplicate(it); onDismiss() },
        )
    }
}

@Composable
fun AddExpenseContent(
    existing: ExpenseEntity?,
    presetCategoryId: Long?,
    categories: List<CategoryEntity>,
    rankedIds: List<Long>,
    templates: List<QuickTemplateEntity>,
    lastMethod: PaymentMethod,
    today: LocalDate,
    onSave: (ExpenseEntity, Boolean, String) -> Unit,
    onTemplate: (QuickTemplateEntity) -> Unit,
    onDelete: (ExpenseEntity) -> Unit,
    onDuplicate: (ExpenseEntity) -> Unit,
) {
    val active = categories.filter { !it.archived || it.id == existing?.categoryId }
    val ordered = remember(active, rankedIds) {
        val byId = active.associateBy { it.id }
        (rankedIds.mapNotNull { byId[it] } + active.filter { it.id !in rankedIds }).distinctBy { it.id }
    }
    var expression by rememberSaveable { mutableStateOf(existing?.let { Money(it.amountPaise).toInputText() } ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: presetCategoryId ?: ordered.firstOrNull()?.id) }
    LaunchedEffect(ordered) { if (categoryId == null) categoryId = ordered.firstOrNull()?.id }
    var method by rememberSaveable { mutableStateOf(existing?.method ?: lastMethod) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var dateEpoch by rememberSaveable { mutableStateOf((existing?.date ?: today).toEpochDay()) }
    var asTemplate by rememberSaveable { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    val view = LocalView.current

    val result = AmountExpression.evaluate(expression)
    val amount = (result as? AmountExpression.Result.Ok)?.amount
    val canSave = amount != null && amount.isPositive && categoryId != null

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (existing == null) R.string.add_expense else R.string.edit_expense),
            style = MaterialTheme.typography.titleLarge,
        )

        if (existing == null && templates.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates, key = { it.id }) { t ->
                    AssistChip(
                        onClick = { view.tick(); onTemplate(t) },
                        label = { Text("${t.name} ${IndianFormat.format(Money(t.amountPaise))}") },
                        leadingIcon = { Icon(AppIcons.Star, null) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }

        // Amount display
        val shownExpression = expression.ifEmpty { "0" }
        val a11yAmount = amount?.let { IndianFormat.format(it) } ?: shownExpression
        Column(Modifier.fillMaxWidth().semantics { contentDescription = a11yAmount; liveRegion = LiveRegionMode.Polite }) {
            Text(
                IndianFormat.RUPEE + shownExpression.replace("-", "−"),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
            )
            val sub = when {
                result is AmountExpression.Result.Ok && (expression.contains('+') || expression.contains('-')) ->
                    "= " + IndianFormat.format(result.amount)
                result is AmountExpression.Result.Negative -> stringResource(R.string.amount_negative)
                result is AmountExpression.Result.Invalid -> stringResource(R.string.amount_invalid)
                result is AmountExpression.Result.TooLarge -> stringResource(R.string.amount_too_large)
                else -> ""
            }
            Text(sub, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Keypad(onKey = { key ->
            expression = when (key) {
                "⌫" -> expression.dropLast(1)
                "C" -> ""
                else -> (expression + key).take(40)
            }
        })

        Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium)
        if (ordered.isEmpty()) {
            Text(stringResource(R.string.no_categories_yet), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ordered.forEach { c ->
                FilterChip(
                    selected = c.id == categoryId,
                    onClick = { categoryId = c.id },
                    label = { Text("${c.emoji} ${c.name}") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        Text(stringResource(R.string.paid_with), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PaymentMethod.entries.forEach { m ->
                FilterChip(
                    selected = m == method,
                    onClick = { method = m },
                    label = { Text("${methodEmoji(m)} ${methodLabel(m)}") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }

        OutlinedTextField(
            value = note, onValueChange = { note = it.take(120) },
            label = { Text(stringResource(R.string.note_optional)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                val d = LocalDate.ofEpochDay(dateEpoch)
                Text(if (d == today) stringResource(R.string.today) else formatDate(d))
            }
            Spacer(Modifier.weight(1f))
            if (existing == null) {
                Text(stringResource(R.string.save_as_quick), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.padding(4.dp))
                Switch(checked = asTemplate, onCheckedChange = { asTemplate = it })
            }
        }

        if (existing != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDelete(existing) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(AppIcons.Delete, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.delete))
                }
                FilledTonalButton(onClick = { onDuplicate(existing) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.add_again_today))
                }
            }
        }

        Button(
            onClick = {
                val amt = amount ?: return@Button
                val cat = categoryId ?: return@Button
                view.tick()
                val base = existing ?: ExpenseEntity(amountPaise = 0, categoryId = cat, date = today, createdAt = 0)
                onSave(
                    base.copy(amountPaise = amt.paise, categoryId = cat, method = method, note = note.trim(), date = LocalDate.ofEpochDay(dateEpoch)),
                    asTemplate, note.trim(),
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(AppIcons.Check, null)
            Spacer(Modifier.padding(4.dp))
            Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateEpoch * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateEpoch = Math.floorDiv(it, 86_400_000L) }
                    showDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }
}

/** Big-digit keypad with + and − so "120+45" works. */
@Composable
fun Keypad(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "-"),
        listOf(".", "0", "00", "C"),
    )
    val backspace = stringResource(R.string.backspace)
    val clear = stringResource(R.string.clear)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val isOp = key in setOf("⌫", "+", "-", "C")
                    val label = if (key == "-") "−" else key
                    val desc = when (key) { "⌫" -> backspace; "C" -> clear; else -> label }
                    FilledTonalButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = desc },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        if (key == "⌫") Icon(AppIcons.Backspace, null)
                        else Text(label, fontSize = 24.sp, fontWeight = if (isOp) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}
