package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.Channel

/**
 * 去重引擎：通知监听和无障碍服务经常对同一笔交易各上报一次，需要合并成一条。
 *
 * 判定「同一笔」的条件（全部满足）：
 * - 同一个支付来源 [DomainTxn.source]
 * - 金额相同（允许 [amountToleranceCents] 分的误差）
 * - 类型相同（支出/收入/红包/退款方向一致）
 * - 发生时间相差不超过 [windowMs]
 *
 * 金额为 0 的记录不参与去重（往往是解析失败或占位数据）。
 *
 * 合并策略：以已有记录为底，保留信息更全的字段——商户非空优先、rawText 更长优先、
 * confidence 更高优先、ACCESSIBILITY 通道优先于 NOTIFICATION（无障碍读的是界面节点，文案更精确）。
 *
 * @param windowMs 同一笔的时间窗口（毫秒），默认 90 秒
 * @param amountToleranceCents 金额容差（分），默认 0 即金额必须完全相等
 */
class DedupEngine(
    val windowMs: Long = 90_000L,
    val amountToleranceCents: Long = 0L,
) {

    init {
        require(windowMs >= 0L) { "windowMs 不能为负，实际是 $windowMs" }
        require(amountToleranceCents >= 0L) {
            "amountToleranceCents 不能为负，实际是 $amountToleranceCents"
        }
    }

    /**
     * 判断 [incoming] 是否与 [recent] 中的某一条重复。
     *
     * [recent] 由调用方按时间窗口粗筛（比如最近 10 分钟），引擎内部会再做精确判定；
     * 同一条新账单只与「最接近」的那条已有记录合并。
     */
    fun decide(incoming: DomainTxn, recent: List<DomainTxn>): DedupDecision {
        if (incoming.amountCents <= 0L) return DedupDecision.New

        val existing = recent
            .asSequence()
            .filter { it.amountCents > 0L }
            .filter { it.source == incoming.source }
            .filter { it.type == incoming.type }
            .filter { absDiff(it.occurredAt, incoming.occurredAt) <= windowMs }
            .filter { absDiff(it.amountCents, incoming.amountCents) <= amountToleranceCents }
            .minWithOrNull(
                compareBy<DomainTxn>(
                    { absDiff(it.occurredAt, incoming.occurredAt) },
                    { absDiff(it.amountCents, incoming.amountCents) },
                    { it.id ?: Long.MAX_VALUE },
                ),
            )
            ?: return DedupDecision.New

        val merged = merge(existing, incoming)
        return if (merged == existing) {
            DedupDecision.Skip
        } else {
            DedupDecision.Merge(existing = existing, merged = merged)
        }
    }

    /**
     * 以 [existing] 为底合并 [incoming] 的更完整信息。
     *
     * 金额、来源、类型、发生时间沿用已有记录（它们是判定「同一笔」的依据，不应该漂移），
     * 分类只在已有记录没有分类时采用新值，已冲抵金额取两者较大值。
     */
    private fun merge(existing: DomainTxn, incoming: DomainTxn): DomainTxn {
        val merchant = moreCompleteMerchant(existing.merchant, incoming.merchant)
        val rawText = if (incoming.rawText.length > existing.rawText.length) {
            incoming.rawText
        } else {
            existing.rawText
        }
        val confidence = maxOf(existing.confidence, incoming.confidence)
        val channel = if (channelRank(incoming.channel) > channelRank(existing.channel)) {
            incoming.channel
        } else {
            existing.channel
        }

        return existing.copy(
            merchant = merchant,
            rawText = rawText,
            confidence = confidence,
            channel = channel,
            categoryId = existing.categoryId ?: incoming.categoryId,
            refundedCents = maxOf(existing.refundedCents, incoming.refundedCents),
        )
    }

    /** 商户名：非空优先，都非空时取更长（更具体）的一方。 */
    private fun moreCompleteMerchant(existing: String?, incoming: String?): String? {
        val left = existing?.takeIf { it.isNotBlank() }
        val right = incoming?.takeIf { it.isNotBlank() }
        return when {
            left == null -> right
            right == null -> left
            right.length > left.length -> right
            else -> left
        }
    }

    /** 通道的信息量排序：无障碍（界面节点）> 手动 > 通知。 */
    private fun channelRank(channel: Channel): Int = when (channel) {
        Channel.ACCESSIBILITY -> 2
        Channel.MANUAL -> 1
        Channel.NOTIFICATION -> 0
    }

    private companion object {
        /** 两个 Long 的绝对差；时间戳/金额都是正常量级，不考虑 Long 边界溢出。 */
        fun absDiff(a: Long, b: Long): Long {
            val diff = a - b
            return if (diff < 0L) -diff else diff
        }
    }
}
