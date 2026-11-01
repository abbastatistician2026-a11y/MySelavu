package com.myselavu.app.data

import com.myselavu.app.data.db.AppDatabase
import com.myselavu.core.export.XlsxWriter
import com.myselavu.core.export.XlsxWriter.Cell
import com.myselavu.core.warning.Warnings
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the Excel file. [from]/[to] limit the export to a date range; null = everything. */
class ExcelExporter(private val db: AppDatabase, private val repo: BudgetRepository) {

    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    suspend fun export(out: OutputStream, from: LocalDate?, to: LocalDate?, today: LocalDate) {
        val lo = from ?: LocalDate.of(1970, 1, 1)
        val hi = to ?: LocalDate.of(9999, 12, 31)
        val categories = db.rulesDao().categories().associateBy { it.id }
        val subs = mutableMapOf<Long, String>()
        fun catName(id: Long) = categories[id]?.let { "${it.emoji} ${it.name}" } ?: "?"
        fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

        val months = repo.monthSummaries()
            .filter { !it.period.end.isBefore(lo) && !it.period.start.isAfter(hi) }
            .sortedBy { it.period.start }

        val summary = XlsxWriter.Sheet(
            "Summary",
            listOf("Month", "From", "To", "Salary", "Extra income", "Fixed expenses", "Allocated to categories",
                "Spent", "Money left", "Moved to savings", "Status"),
            months.map { m ->
                listOf(
                    Cell.Text(m.period.labelMonth.format(monthFormat)), Cell.Date(m.period.start), Cell.Date(m.period.end),
                    Cell.Amount(m.salary.paise), Cell.Amount(m.extraIncome.paise), Cell.Amount(m.fixed.paise),
                    Cell.Amount(m.allocated.paise), Cell.Amount(m.spent.paise), Cell.Amount(m.moneyLeft.paise),
                    Cell.Amount(m.movedToSavings.paise), Cell.Text(if (m.closed) "Closed" else "Current"),
                )
            },
        )

        val expenses = db.expenseDao().between(lo, hi)
        val transactions = XlsxWriter.Sheet(
            "Transactions",
            listOf("Date", "Category", "Sub-category", "Amount", "Payment method", "Note"),
            expenses.map { e ->
                listOf(
                    Cell.Date(e.date), Cell.Text(catName(e.categoryId)),
                    Cell.Text(e.subCategoryId?.let { subs[it] } ?: ""), Cell.Amount(e.amountPaise),
                    Cell.Text(methodName(e.method)), Cell.Text(e.note),
                )
            },
        )

        val categoryMonthly = XlsxWriter.Sheet(
            "Category Monthly",
            listOf("Month", "Category", "Type", "Allocated", "Spent", "Remaining", "Warning level"),
            months.flatMap { m ->
                val elapsed = m.period.daysElapsed(if (m.closed) m.period.end else today)
                m.categories.map { c ->
                    val type = categories[c.categoryId]?.type
                    val level = if (type == com.myselavu.core.budget.CategoryType.SAVINGS) "-" else
                        Warnings.evaluate(c.spent, c.allocated, elapsed, m.period.lengthInDays).level.name
                    listOf(
                        Cell.Text(m.period.labelMonth.format(monthFormat)), Cell.Text(catName(c.categoryId)),
                        Cell.Text(type?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""),
                        Cell.Amount(c.allocated.paise), Cell.Amount(c.spent.paise),
                        Cell.Amount((c.allocated - c.spent).paise), Cell.Text(level),
                    )
                }
            },
        )

        val salary = XlsxWriter.Sheet(
            "Salary History",
            listOf("Applies from", "Salary", "Changed on"),
            db.rulesDao().allSalaryRules().map {
                listOf(Cell.Date(it.effectiveFrom), Cell.Amount(it.amountPaise), Cell.Date(day(it.createdAt)))
            },
        )

        val fixed = XlsxWriter.Sheet(
            "Fixed Expense History",
            listOf("Name", "Amount", "Due day", "Applies from", "Status", "Changed on"),
            db.rulesDao().fixedExpenseRules().map {
                listOf(
                    Cell.Text(it.name), Cell.Amount(it.amountPaise),
                    it.dueDay?.let { d -> Cell.Integer(d.toLong()) } ?: Cell.Empty,
                    Cell.Date(it.effectiveFrom), Cell.Text(if (it.active) "Active" else "Removed"), Cell.Date(day(it.createdAt)),
                )
            },
        )

        val extra = XlsxWriter.Sheet(
            "Extra Income",
            listOf("Date", "Amount", "Note"),
            db.rulesDao().allExtraIncome().filter { it.date in lo..hi }.map {
                listOf(Cell.Date(it.date), Cell.Amount(it.amountPaise), Cell.Text(it.note))
            },
        )

        val pot = XlsxWriter.Sheet(
            "Savings Pot",
            listOf("Date", "Amount", "Type", "Note"),
            db.planningDao().allPotEntries().filter { it.date in lo..hi }.map {
                listOf(Cell.Date(it.date), Cell.Amount(it.amountPaise),
                    Cell.Text(if (it.kind.name == "MONTH_END") "Moved to savings at month end" else it.kind.name), Cell.Text(it.note))
            },
        )

        XlsxWriter.write(listOf(summary, transactions, categoryMonthly, salary, fixed, extra, pot), out)
    }

    companion object {
        fun methodName(m: com.myselavu.core.budget.PaymentMethod): String = when (m) {
            com.myselavu.core.budget.PaymentMethod.UPI -> "UPI"
            com.myselavu.core.budget.PaymentMethod.CASH -> "Cash"
            com.myselavu.core.budget.PaymentMethod.DEBIT_CARD -> "Debit card"
            com.myselavu.core.budget.PaymentMethod.CREDIT_CARD -> "Credit card"
            com.myselavu.core.budget.PaymentMethod.OTHER -> "Other"
        }
    }
}
