package com.nicktj.autoledger.core.data.export

/**
 * CSV 导出：UTF-8 BOM + CRLF，字段与 [ExportRow] 一一对应。
 *
 * BOM 是为了让 Windows 上的 Excel 直接认出 UTF-8（否则中文会乱码）；
 * CRLF 是 CSV 的通用换行，兼容 Excel / Numbers / WPS。
 */
object CsvExporter {

    private const val BOM = "\uFEFF"
    private const val CRLF = "\r\n"

    val HEADER: List<String> = listOf(
        "日期时间",
        "类型",
        "金额",
        "分类",
        "商户",
        "来源",
        "通道",
        "状态",
        "备注",
        "原始文本摘要",
    )

    /** 生成完整 CSV 文本（已带 BOM）。 */
    fun build(rows: List<ExportRow>): String {
        val builder = StringBuilder(HEADER.size * 8 + rows.size * 96)
        builder.append(BOM)
        appendRow(builder, HEADER)
        rows.forEach { row ->
            appendRow(
                builder,
                listOf(
                    row.occurredAtText,
                    row.typeLabel,
                    row.amountYuanText,
                    row.categoryName,
                    row.merchant,
                    row.sourceLabel,
                    row.channelLabel,
                    row.statusLabel,
                    row.note,
                    row.rawTextSummary,
                ),
            )
        }
        return builder.toString()
    }

    private fun appendRow(builder: StringBuilder, cells: List<String>) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) builder.append(',')
            builder.append(escape(cell))
        }
        builder.append(CRLF)
    }

    /** 含逗号 / 引号 / 换行时用双引号包裹，内部引号翻倍。 */
    private fun escape(value: String): String {
        val plain = value.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ')
        val needsQuotes = plain.any { it == ',' || it == '"' }
        if (!needsQuotes) return plain
        return "\"" + plain.replace("\"", "\"\"") + "\""
    }
}
