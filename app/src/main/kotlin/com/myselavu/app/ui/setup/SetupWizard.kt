@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.myselavu.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.myselavu.app.R
import com.myselavu.app.data.CategoryInput
import com.myselavu.app.data.FixedInput
import com.myselavu.app.ui.budget.UnplannedBanner
import com.myselavu.app.ui.components.AppIcons
import com.myselavu.app.ui.components.CategoryColors
import com.myselavu.app.ui.components.MoneyField
import com.myselavu.app.ui.components.parseMoneyOrZero
import com.myselavu.app.ui.components.toInputText
import com.myselavu.core.budget.CategoryType
import com.myselavu.core.money.Money

data class FixedRow(val name: String, val amount: String, val due: String)
data class CatRow(val name: String, val emoji: String, val color: Long, val savings: Boolean, val amount: String)

private const val SEP = "\u0001"

private val FixedRowsSaver = listSaver<SnapshotStateList<FixedRow>, String>(
    save = { list -> list.map { listOf(it.name, it.amount, it.due).joinToString(SEP) } },
    restore = { saved -> saved.map { s -> s.split(SEP).let { FixedRow(it[0], it[1], it[2]) } }.toMutableStateList() },
)

private val CatRowsSaver = listSaver<SnapshotStateList<CatRow>, String>(
    save = { list -> list.map { listOf(it.name, it.emoji, it.color.toString(), it.savings.toString(), it.amount).joinToString(SEP) } },
    restore = { saved ->
        saved.map { s -> s.split(SEP).let { CatRow(it[0], it[1], it[2].toLong(), it[3].toBoolean(), it[4]) } }.toMutableStateList()
    },
)

/** Default categories with a share of the money available after fixed expenses. */
private data class DefaultCat(val nameRes: Int, val emoji: String, val color: Long, val savings: Boolean, val percent: Int)

private val defaultCats = listOf(
    DefaultCat(R.string.sample_food, "🍛", CategoryColors[0], false, 25),
    DefaultCat(R.string.sample_travel, "🛺", CategoryColors[1], false, 10),
    DefaultCat(R.string.sample_personal, "🛍️", CategoryColors[2], false, 10),
    DefaultCat(R.string.sample_savings, "🏦", CategoryColors[3], true, 25),
    DefaultCat(R.string.sample_emergency, "🚑", CategoryColors[4], true, 10),
)

@Composable
fun SetupWizard(
    language: String,
    onLanguage: (String) -> Unit,
    onFinish: (salary: Money, fixed: List<FixedInput>, cats: List<CategoryInput>, startDay: Int, name: String) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var salary by rememberSaveable { mutableStateOf("") }
    var startDay by rememberSaveable { mutableStateOf(1) }
    val fixed = rememberSaveable(saver = FixedRowsSaver) { mutableListOf<FixedRow>().toMutableStateList() }
    val cats = rememberSaveable(saver = CatRowsSaver) { mutableListOf<CatRow>().toMutableStateList() }
    val defaultNames = defaultCats.map { stringResource(it.nameRes) }

    val salaryMoney = parseMoneyOrZero(salary)
    val fixedTotal = Money(fixed.sumOf { parseMoneyOrZero(it.amount).paise })
    val available = salaryMoney - fixedTotal

    fun prefillCategories() {
        if (cats.isEmpty()) {
            defaultCats.forEachIndexed { i, d ->
                val amount = if (available.isPositive) Money(available.paise * d.percent / 100 / 10000 * 10000) else Money.ZERO
                cats += CatRow(defaultNames[i], d.emoji, d.color, d.savings, if (amount.isPositive) amount.toInputText() else "")
            }
        }
    }

    fun finish() {
        val finalCats = cats.filter { it.name.isNotBlank() }.map {
            CategoryInput(it.name.trim(), it.emoji, it.color, if (it.savings) CategoryType.SAVINGS else CategoryType.SPENDING, parseMoneyOrZero(it.amount))
        }
        onFinish(
            salaryMoney,
            fixed.filter { it.name.isNotBlank() && parseMoneyOrZero(it.amount).isPositive }
                .map { FixedInput(it.name.trim(), parseMoneyOrZero(it.amount), it.due.toIntOrNull()?.takeIf { d -> d in 1..31 }) },
            finalCats,
            startDay,
            name,
        )
    }

    val totalSteps = 5
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            LinearProgressIndicator(
                progress = { (step + 1f) / totalSteps },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> WelcomeStep(language, onLanguage, name) { name = it }
                    1 -> SalaryStep(salary, { salary = it }, startDay) { startDay = it }
                    2 -> FixedStep(fixed)
                    3 -> CategoriesStep(cats, available)
                    else -> DoneStep()
                }
            }
            // Main actions at the bottom, within thumb reach.
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step in 1..3) {
                    TextButton(
                        onClick = {
                            when (step) {
                                1 -> salary = ""
                                2 -> fixed.clear()
                                3 -> { for (i in cats.indices) cats[i] = cats[i].copy(amount = "") }
                            }
                            if (step == 2) prefillCategories()
                            step++
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.skip_for_now)) }
                } else if (step == 4) {
                    TextButton(onClick = { step = 3 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.back)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        when (step) {
                            2 -> { prefillCategories(); step++ }
                            4 -> finish()
                            else -> step++
                        }
                    },
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Text(stringResource(if (step == 4) R.string.start_using else R.string.next))
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WelcomeStep(language: String, onLanguage: (String) -> Unit, name: String, onName: (String) -> Unit) {
    Text("🪙", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 24.dp))
    StepTitle(stringResource(R.string.welcome_title), stringResource(R.string.welcome_text))
    Text(stringResource(R.string.choose_language), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = language == "en", onClick = { onLanguage("en") }, label = { Text("English") }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = language == "ta", onClick = { onLanguage("ta") }, label = { Text("தமிழ்") }, modifier = Modifier.heightIn(min = 48.dp))
    }
    OutlinedTextField(name, { onName(it.take(40)) }, label = { Text(stringResource(R.string.your_name_optional)) },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SalaryStep(salary: String, onSalary: (String) -> Unit, startDay: Int, onStartDay: (Int) -> Unit) {
    StepTitle(stringResource(R.string.salary_step_title), stringResource(R.string.salary_step_text))
    MoneyField(salary, onSalary, stringResource(R.string.monthly_salary), Modifier.fillMaxWidth())
    Text(stringResource(R.string.start_day_question), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.start_day_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 5, 10, 25, 28).forEach { d ->
            FilterChip(selected = startDay == d, onClick = { onStartDay(d) },
                label = { Text(stringResource(R.string.day_n, d)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    OutlinedTextField(
        value = startDay.toString(),
        onValueChange = { v -> v.filter { it.isDigit() }.take(2).toIntOrNull()?.let { if (it in 1..28) onStartDay(it) } },
        label = { Text(stringResource(R.string.other_day)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

@Composable
private fun FixedStep(rows: SnapshotStateList<FixedRow>) {
    StepTitle(stringResource(R.string.fixed_step_title), stringResource(R.string.fixed_step_text))
    val suggestions = listOf(R.string.suggest_rent, R.string.suggest_family_fund, R.string.suggest_eb, R.string.suggest_internet)
        .map { stringResource(it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.filter { s -> rows.none { it.name == s } }.forEach { s ->
            FilterChip(selected = false, onClick = { rows += FixedRow(s, "", "") }, label = { Text("+ $s") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        FilterChip(selected = false, onClick = { rows += FixedRow("", "", "") },
            label = { Text(stringResource(R.string.add_other)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
    rows.forEachIndexed { i, row ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(row.name, { rows[i] = row.copy(name = it.take(40)) }, label = { Text(stringResource(R.string.name)) },
                        singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rows.removeAt(i) }) { Icon(AppIcons.Close, stringResource(R.string.remove)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyField(row.amount, { rows[i] = row.copy(amount = it) }, stringResource(R.string.amount), Modifier.weight(1.4f))
                    OutlinedTextField(
                        row.due, { v -> rows[i] = row.copy(due = v.filter { it.isDigit() }.take(2)) },
                        label = { Text(stringResource(R.string.due_day_short)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoriesStep(rows: SnapshotStateList<CatRow>, available: Money) {
    StepTitle(stringResource(R.string.categories_step_title), stringResource(R.string.categories_step_text))
    val allocated = Money(rows.sumOf { parseMoneyOrZero(it.amount).paise })
    UnplannedBanner(available - allocated, hidden = false)
    rows.forEachIndexed { i, row ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(row.name, { rows[i] = row.copy(name = it.take(30)) }, label = { Text(stringResource(R.string.name)) },
                        singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = { rows.removeAt(i) }) { Icon(AppIcons.Close, stringResource(R.string.remove)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyField(row.amount, { rows[i] = row.copy(amount = it) }, stringResource(R.string.monthly_amount), Modifier.weight(1f))
                    FilterChip(selected = row.savings, onClick = { rows[i] = row.copy(savings = !row.savings) },
                        label = { Text(stringResource(R.string.type_savings)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
    TextButton(
        onClick = { rows += CatRow("", "🛒", CategoryColors[(rows.size + 5) % CategoryColors.size], false, "") },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Icon(AppIcons.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_category)) }
    Text(stringResource(R.string.categories_step_more), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DoneStep() {
    Text("🎉", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 24.dp))
    StepTitle(stringResource(R.string.done_title), stringResource(R.string.done_text))
    listOf(R.string.tip_1, R.string.tip_2, R.string.tip_3).forEachIndexed { i, tip ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(32.dp))
                Text(stringResource(tip), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
