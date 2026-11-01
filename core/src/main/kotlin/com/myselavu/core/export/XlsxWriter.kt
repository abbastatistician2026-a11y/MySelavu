package com.myselavu.core.export

import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A tiny .xlsx (Excel) writer built on ZipOutputStream: no big libraries, so the APK stays small.
 * Bold, shaded, frozen header row; ₹ amounts with Indian grouping; real dates; auto-sized columns.
 */
object XlsxWriter {

    sealed interface Cell {
        data class Text(val value: String) : Cell
        /** Money in paise, written exactly (no floating point) and shown as ₹. */
        data class Amount(val paise: Long) : Cell
        data class Integer(val value: Long) : Cell
        data class Date(val value: LocalDate) : Cell
        data object Empty : Cell
    }

    data class Sheet(val name: String, val headers: List<String>, val rows: List<List<Cell>>)

    private const val STYLE_HEADER = 1
    private const val STYLE_AMOUNT = 2
    private const val STYLE_DATE = 3

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "At least one sheet is needed" }
        val names = uniqueSheetNames(sheets.map { it.name })
        val zip = ZipOutputStream(out)
        fun entry(path: String, content: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        entry("[Content_Types].xml", contentTypes(sheets.size))
        entry("_rels/.rels", ROOT_RELS)
        entry("xl/workbook.xml", workbook(names))
        entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
        entry("xl/styles.xml", STYLES)
        sheets.forEachIndexed { i, sheet -> entry("xl/worksheets/sheet${i + 1}.xml", sheetXml(sheet)) }
        zip.finish()
        zip.flush()
    }

    // ---------- helpers (internal for tests) ----------

    /** 0 → "A", 25 → "Z", 26 → "AA". */
    fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun amountText(paise: Long): String {
        val neg = paise < 0
        val abs = if (neg) -paise else paise
        val frac = abs % 100
        return (if (neg) "-" else "") + (abs / 100) + "." + (if (frac < 10) "0" else "") + frac
    }

    fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch < ' ' -> {} // control characters are not allowed in XML
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun uniqueSheetNames(names: List<String>): List<String> {
        val used = HashSet<String>()
        return names.map { raw ->
            var base = raw.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().ifEmpty { "Sheet" }.take(31)
            var candidate = base
            var n = 2
            while (!used.add(candidate.lowercase())) {
                val suffix = " ($n)"
                candidate = base.take(31 - suffix.length) + suffix
                n++
            }
            candidate
        }
    }

    private fun displayLength(cell: Cell): Int = when (cell) {
        is Cell.Text -> cell.value.lineSequence().maxOfOrNull { it.length } ?: 0
        is Cell.Amount -> amountText(cell.paise).length + 4
        is Cell.Integer -> cell.value.toString().length
        is Cell.Date -> 11
        Cell.Empty -> 0
    }

    private fun sheetXml(sheet: Sheet): String {
        val columnCount = maxOf(sheet.headers.size, sheet.rows.maxOfOrNull { it.size } ?: 0)
        val widths = IntArray(columnCount) { i -> (sheet.headers.getOrNull(i)?.length ?: 0) + 2 }
        for (row in sheet.rows) row.forEachIndexed { i, c -> widths[i] = maxOf(widths[i], displayLength(c) + 2) }

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("""<sheetFormatPr defaultRowHeight="15"/>""")
        if (columnCount > 0) {
            sb.append("<cols>")
            for (i in 0 until columnCount) {
                val w = widths[i].coerceIn(8, 60)
                sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        sb.append("""<row r="1">""")
        sheet.headers.forEachIndexed { i, h -> sb.append(textCell("${columnName(i)}1", h, STYLE_HEADER)) }
        sb.append("</row>")
        sheet.rows.forEachIndexed { r, row ->
            val rowNum = r + 2
            sb.append("""<row r="$rowNum">""")
            row.forEachIndexed { i, cell ->
                val ref = "${columnName(i)}$rowNum"
                when (cell) {
                    is Cell.Text -> sb.append(textCell(ref, cell.value, 0))
                    is Cell.Amount -> sb.append("""<c r="$ref" s="$STYLE_AMOUNT"><v>${amountText(cell.paise)}</v></c>""")
                    is Cell.Integer -> sb.append("""<c r="$ref"><v>${cell.value}</v></c>""")
                    is Cell.Date -> {
                        val serial = cell.value.toEpochDay() - EXCEL_EPOCH.toEpochDay()
                        sb.append("""<c r="$ref" s="$STYLE_DATE"><v>$serial</v></c>""")
                    }
                    Cell.Empty -> {}
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun textCell(ref: String, text: String, style: Int): String {
        val s = if (style != 0) """ s="$style"""" else ""
        return """<c r="$ref" t="inlineStr"$s><is><t xml:space="preserve">${escape(text)}</t></is></c>"""
    }

    private fun contentTypes(n: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..n) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun workbook(names: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, name ->
            append("""<sheet name="${escape(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(n: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    // ₹ with Indian grouping (1,23,45,678.00), e.g. the common Excel trick with conditional sections.
    private const val RUPEE_FORMAT =
        "[&gt;=10000000]&quot;₹&quot;##\\,##\\,##\\,##0.00;[&gt;=100000]&quot;₹&quot;##\\,##\\,##0.00;&quot;₹&quot;##,##0.00"

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<numFmts count="2"><numFmt numFmtId="164" formatCode="$RUPEE_FORMAT"/><numFmt numFmtId="165" formatCode="dd\-mmm\-yyyy"/></numFmts>""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFCDEDEA"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="4">""" +
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>""" +
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """<xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
        """</cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        """</styleSheet>"""
}
