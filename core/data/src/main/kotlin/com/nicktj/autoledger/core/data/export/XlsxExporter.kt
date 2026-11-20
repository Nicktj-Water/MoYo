package com.nicktj.autoledger.core.data.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 最小 XLSX 生成器：不依赖 Apache POI，直接手写 OOXML 包。
 *
 * 包内结构（Excel / WPS / Numbers 都能打开的最小集合）：
 * ```
 * [Content_Types].xml
 * _rels/.rels
 * xl/workbook.xml
 * xl/_rels/workbook.xml.rels
 * xl/worksheets/sheet1.xml
 * ```
 * 文本一律用 inline string（不需要 sharedStrings.xml），金额写成数字单元格，
 * 列宽通过 `<cols>` 固定，避免打开时内容挤在一起。
 */
object XlsxExporter {

    /** Excel 的 sheet 名限制 31 字符、不能含 []:*?/\。 */
    const val SHEET_NAME: String = "账单"

    private const val XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private data class ColumnSpec(val title: String, val width: Double)

    private val COLUMNS = listOf(
        ColumnSpec("日期时间", 21.0),
        ColumnSpec("类型", 9.0),
        ColumnSpec("金额", 13.0),
        ColumnSpec("分类", 13.0),
        ColumnSpec("商户", 26.0),
        ColumnSpec("来源", 10.0),
        ColumnSpec("通道", 10.0),
        ColumnSpec("状态", 10.0),
        ColumnSpec("备注", 30.0),
        ColumnSpec("原始文本摘要", 52.0),
    )

    /** 生成完整 xlsx 字节数组。 */
    fun build(rows: List<ExportRow>): ByteArray {
        val buffer = ByteArrayOutputStream(8 * 1024 + rows.size * 512)
        ZipOutputStream(buffer).use { zip ->
            zip.putTextEntry("[Content_Types].xml", contentTypesXml())
            zip.putTextEntry("_rels/.rels", rootRelsXml())
            zip.putTextEntry("xl/workbook.xml", workbookXml())
            zip.putTextEntry("xl/_rels/workbook.xml.rels", workbookRelsXml())
            zip.putTextEntry("xl/worksheets/sheet1.xml", sheetXml(rows))
        }
        return buffer.toByteArray()
    }

    // ------------------------------------------------------------------
    // 包内各部件
    // ------------------------------------------------------------------

    private fun contentTypesXml(): String = buildString {
        append(XML_DECL)
        append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
        append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
        append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
        append(
            "<Override PartName=\"/xl/workbook.xml\" " +
                "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>",
        )
        append(
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" " +
                "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>",
        )
        append("</Types>")
    }

    private fun rootRelsXml(): String = buildString {
        append(XML_DECL)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        append(
            "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" " +
                "Target=\"xl/workbook.xml\"/>",
        )
        append("</Relationships>")
    }

    private fun workbookXml(): String = buildString {
        append(XML_DECL)
        append(
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">",
        )
        append("<sheets><sheet name=\"")
        append(escapeXml(SHEET_NAME))
        append("\" sheetId=\"1\" r:id=\"rId1\"/></sheets>")
        append("</workbook>")
    }

    private fun workbookRelsXml(): String = buildString {
        append(XML_DECL)
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        append(
            "<Relationship Id=\"rId1\" " +
                "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" " +
                "Target=\"worksheets/sheet1.xml\"/>",
        )
        append("</Relationships>")
    }

    private fun sheetXml(rows: List<ExportRow>): String = buildString(8 * 1024 + rows.size * 320) {
        append(XML_DECL)
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")

        append("<cols>")
        COLUMNS.forEachIndexed { index, column ->
            val oneBased = index + 1
            append("<col min=\"")
            append(oneBased)
            append("\" max=\"")
            append(oneBased)
            append("\" width=\"")
            append(column.width)
            append("\" customWidth=\"1\"/>")
        }
        append("</cols>")

        append("<sheetData>")

        // 表头
        append("<row r=\"1\">")
        COLUMNS.forEachIndexed { index, column ->
            append(inlineCell(columnName(index) + "1", column.title))
        }
        append("</row>")

        rows.forEachIndexed { rowIndex, row ->
            val excelRow = rowIndex + 2
            append("<row r=\"")
            append(excelRow)
            append("\">")
            append(inlineCell(cellRef(0, excelRow), row.occurredAtText))
            append(inlineCell(cellRef(1, excelRow), row.typeLabel))
            // 金额写成数字，表格里可以直接求和。
            append("<c r=\"")
            append(cellRef(2, excelRow))
            append("\"><v>")
            append(row.amountYuanText)
            append("</v></c>")
            append(inlineCell(cellRef(3, excelRow), row.categoryName))
            append(inlineCell(cellRef(4, excelRow), row.merchant))
            append(inlineCell(cellRef(5, excelRow), row.sourceLabel))
            append(inlineCell(cellRef(6, excelRow), row.channelLabel))
            append(inlineCell(cellRef(7, excelRow), row.statusLabel))
            append(inlineCell(cellRef(8, excelRow), row.note))
            append(inlineCell(cellRef(9, excelRow), row.rawTextSummary))
            append("</row>")
        }

        append("</sheetData>")
        append("</worksheet>")
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun cellRef(columnIndex: Int, rowNumber: Int): String =
        columnName(columnIndex) + rowNumber

    private fun inlineCell(ref: String, value: String): String = buildString {
        append("<c r=\"")
        append(ref)
        append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
        append(escapeXml(value))
        append("</t></is></c>")
    }

    /** 0 -> A, 25 -> Z, 26 -> AA。 */
    private fun columnName(index: Int): String {
        var remaining = index
        val builder = StringBuilder(2)
        while (true) {
            builder.append(('A' + remaining % 26))
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return builder.reverse().toString()
    }

    /** XML 文本转义；& 必须先替换，避免二次转义。 */
    private fun escapeXml(value: String): String {
        val builder = StringBuilder(value.length + 8)
        value.forEach { ch ->
            when (ch) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                '\'' -> builder.append("&apos;")
                // XML 1.0 不允许的控制字符直接丢掉，否则 Excel 会报文件损坏。
                '\t', '\n', '\r' -> builder.append(' ')
                else -> if (ch.code < 0x20) Unit else builder.append(ch)
            }
        }
        return builder.toString()
    }

    private fun ZipOutputStream.putTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
