package com.nicktj.autoledger.core.model

/**
 * 解析器的输出：一条「准备入库」的账单。
 *
 * 约定：
 * - [amountCents] 是 Long 分，全程不用 Double，避免出现 12.34 -> 1233.9999 这种误差。
 * - [confidence] 在 0.0（完全猜的）到 1.0（明确解析）之间，低于阈值的记录进「待确认」。
 * - [rawText] 保留原始通知/节点文本，用于回查；保留天数由设置决定。
 */
data class ParsedTxn(
    val amountCents: Long,
    val type: TxnType,
    val merchant: String?,
    val source: PaySource,
    val occurredAt: Long,
    val rawText: String,
    val confidence: Double,
    val channel: Channel,
) {
    init {
        require(amountCents >= 0L) {
            "amountCents 不能为负（方向请用 type 表达），实际是 $amountCents"
        }
        require(confidence in 0.0..1.0) {
            "confidence 必须在 0.0 到 1.0 之间，实际是 $confidence"
        }
    }
}
