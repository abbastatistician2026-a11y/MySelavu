package com.myselavu.core

import com.myselavu.core.budget.CategoryType
import com.myselavu.core.export.XlsxWriter
import com.myselavu.core.export.XlsxWriter.Cell
import com.myselavu.core.money.Money
import com.myselavu.core.plan.PlanSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class PlanAndExportTest {
    private fun r(x: Long) = Money.rupees(x)

    @Test fun planUsesAverageWithCushionAndMovesLeftoverToSavings() {
        val plan = PlanSuggestion.suggest(
            listOf(
                PlanSuggestion.Input(1, CategoryType.SPENDING, r(8000), listOf(r(6000), r(7000), r(5000), r(20000))),
                PlanSuggestion.Input(2, CategoryType.SPENDING, r(3000), emptyList()),
                PlanSuggestion.Input(3, CategoryType.SAVINGS, r(10000), emptyList()),
            ),
            available = r(30000),
        )
        val food = plan.suggestions.first { it.categoryId == 1L }
        // average of the latest 3 months = 6000; +10% = 6600
        assertEquals(r(6000), food.averageSpent)
        assertEquals(r(6600), food.suggested)
        val travel = plan.suggestions.first { it.categoryId == 2L }
        assertEquals(PlanSuggestion.Reason.NO_HISTORY, travel.reason)
        assertEquals(r(3000), travel.suggested)
        // 30000 − 6600 − 3000 − 10000 = 10400 extra → savings
        val savings = plan.suggestions.first { it.categoryId == 3L }
        assertEquals(r(20400), savings.suggested)
        assertEquals(Money.ZERO, plan.unplanned)
        assertTrue(plan.hasHistory)
    }

    @Test fun planScalesDownWhenTooBig() {
        val plan = PlanSuggestion.suggest(
            listOf(
                PlanSuggestion.Input(1, CategoryType.SPENDING, r(5000), listOf(r(10000))),
                PlanSuggestion.Input(2, CategoryType.SPENDING, r(5000), listOf(r(10000))),
                PlanSuggestion.Input(3, CategoryType.SAVINGS, r(5000), emptyList()),
            ),
            available = r(16000),
        )
        assertTrue(plan.totalSuggested <= r(16000))
        assertEquals(r(5500), plan.suggestions[0].suggested)
        assertEquals(PlanSuggestion.Reason.SCALED_TO_FIT, plan.suggestions[0].reason)
        assertEquals(r(5000), plan.suggestions[2].suggested)
    }

    @Test fun roundUp() {
        assertEquals(r(100), PlanSuggestion.roundUpTo100(Money(1)))
        assertEquals(r(200), PlanSuggestion.roundUpTo100(r(200)))
        assertEquals(Money.ZERO, PlanSuggestion.roundUpTo100(Money.ZERO))
    }

    @Test fun xlsxHelpers() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AZ", XlsxWriter.columnName(51))
        assertEquals("1234.05", XlsxWriter.amountText(123405))
        assertEquals("-0.50", XlsxWriter.amountText(-50))
        assertEquals("a &amp; b &lt;c&gt;", XlsxWriter.escape("a & b <c>\u0001"))
        assertEquals(listOf("Food Travel", "Food Travel (2)", "Sheet"), XlsxWriter.uniqueSheetNames(listOf("Food/Travel", "Food:Travel", "")))
    }

    @Test fun writesAValidWorkbook() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(
            listOf(
                XlsxWriter.Sheet("Transactions", listOf("Date", "Category", "Amount", "Note"), listOf(
                    listOf(Cell.Date(LocalDate.of(2026, 11, 18)), Cell.Text("Food"), Cell.Amount(12345), Cell.Text("Tea & <snacks>")),
                    listOf(Cell.Date(LocalDate.of(2026, 11, 19)), Cell.Text("உணவு"), Cell.Amount(50000), Cell.Empty),
                )),
                XlsxWriter.Sheet("Summary", listOf("Month", "Count"), listOf(listOf(Cell.Text("November 2026"), Cell.Integer(2)))),
            ),
            out,
        )
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries[e.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
                "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"),
            entries.keys,
        )
        // Every part must be well-formed XML
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        for ((_, xml) in entries) builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val sheet1 = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet1.contains("<v>123.45</v>"))
        assertTrue(sheet1.contains("Tea &amp; &lt;snacks&gt;"))
        assertTrue(sheet1.contains("உணவு"))
        assertTrue(sheet1.contains("state=\"frozen\""))
        // 18 Nov 2026 = Excel serial 46344
        assertTrue(sheet1.contains("<v>46344</v>"))
        assertFalse(sheet1.contains("\u0001"))
    }
}
