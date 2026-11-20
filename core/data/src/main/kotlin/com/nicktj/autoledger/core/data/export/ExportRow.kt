package com.nicktj.autoledger.core.data.export

import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.core.model.TxnType
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 导出用的扁平行：CSV / XLSX 共用同一份数据，保证两种格式内容一致。
 *
 * 金额保留 [amountCents] 分，展示层再从分换算成元，避免浮点误差。
 */
data class ExportRow(
    val occurredAt: Long,
    val typeLabel: String,
    val amountCents: Long,
    val categoryName: String,
    val merchant: String,
    val sourceLabel: String,
    val channelLabel: String,
    val statusLabel: String,
    val note: String,
    val rawTextSummary: String,
) {

    /** 形如 2026-11-21 12:30:00（Asia/Shanghai）。 */
    val occurredAtText: String
        get() = EXPORT_DATE_TIME.format(Instant.ofEpochMilli(occurredAt).atZone(EXPORT_ZONE))

    /** 形如 1234.56，纯数字，方便表格软件直接当数字列。 */
    val amountYuanText: String
        get() = BigDecimal.valueOf(amountCents, 2).toPlainString()
}

/** 导出统一使用国内时区，避免设备时区不同导致跨日。 */
internal val EXPORT_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

internal val EXPORT_DATE_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

internal val EXPORT_FILE_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.CHINA)

/** 单行原始文本摘要长度上限。 */
private const val RAW_SUMMARY_MAX_CHARS = 80

/** 把一条账单映射成导出行。 */
internal fun TransactionEntity.toExportRow(
    categoryName: String,
    rawText: String,
): ExportRow = ExportRow(
    occurredAt = occurredAt,
    typeLabel = typeLabelOf(type),
    amountCents = amountCents,
    categoryName = categoryName,
    merchant = merchant.orEmpty(),
    sourceLabel = sourceLabelOf(source),
    channelLabel = channelLabelOf(channel),
    statusLabel = statusLabelOf(status),
    note = note.orEmpty(),
    rawTextSummary = summarizeRawText(rawText),
)

/** 多行文本压成一行，便于 CSV / 表格展示。 */
internal fun summarizeRawText(text: String?, maxChars: Int = RAW_SUMMARY_MAX_CHARS): String {
    val flat = text.orEmpty()
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace(Regex("\\s{2,}"), " ")
        .trim()
    return if (flat.length <= maxChars) flat else flat.take(maxChars) + "…"
}

internal fun typeLabelOf(raw: String): String = when (enumOrNull<TxnType>(raw)) {
    TxnType.EXPENSE -> "支出"
    TxnType.INCOME -> "收入"
    TxnType.RED_PACKET_IN -> "收红包"
    TxnType.RED_PACKET_OUT -> "发红包"
    TxnType.REFUND -> "退款"
    null -> raw
}

internal fun sourceLabelOf(raw: String): String = when (enumOrNull<PaySource>(raw)) {
    PaySource.ALIPAY -> "支付宝"
    PaySource.WECHAT -> "微信"
    PaySource.UNIONPAY -> "云闪付"
    PaySource.MANUAL -> "手动"
    null -> raw
}

internal fun channelLabelOf(raw: String): String = when (enumOrNull<Channel>(raw)) {
    Channel.NOTIFICATION -> "通知"
    Channel.ACCESSIBILITY -> "无障碍"
    Channel.MANUAL -> "手动"
    null -> raw
}

internal fun statusLabelOf(raw: String): String = when (enumOrNull<TxnStatus>(raw)) {
    TxnStatus.CONFIRMED -> "已确认"
    TxnStatus.PENDING -> "待确认"
    TxnStatus.DISCARDED -> "已丢弃"
    null -> raw
}

private inline fun <reified T : Enum<T>> enumOrNull(raw: String): T? =
    runCatching { enumValueOf<T>(raw) }.getOrNull()
