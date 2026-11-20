package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.TxnType
import java.util.Locale

/**
 * 退款冲抵：把一笔退款挂到它对应的那笔支出上。
 *
 * 匹配条件（全部满足）：
 * - [refund] 的类型必须是 REFUND，金额必须大于 0
 * - 候选必须是历史支出（EXPENSE），金额与退款完全相等
 * - 候选发生在退款之前，且间隔不超过 [windowDays] 天
 * - 候选商户与退款商户相等，或一方包含另一方（双方都非空才比较，忽略大小写和首尾空格）
 * - 候选还没有被完全冲抵（已冲抵金额 < 原支出金额）
 *
 * 多笔候选取**最近**的一笔。允许部分冲抵：只要还有余额就返回，
 * 退款金额与剩余余额的大小关系交给调用方处理（有的平台允许分多次退款）。
 *
 * @param windowDays 可回溯的天数，默认 30 天
 */
class RefundMatcher(val windowDays: Int = 30) {

    init {
        require(windowDays >= 0) { "windowDays 不能为负，实际是 $windowDays" }
    }

    /**
     * 找出 [refund] 对应的那笔支出，找不到返回 null。
     *
     * @param refund 退款记录
     * @param candidates 历史支出（调用方按时间窗口粗筛即可）
     * @param partiallyRefunded id -> 已冲抵金额（分）；优先级高于账单自带的 refundedCents
     */
    fun findMatch(
        refund: DomainTxn,
        candidates: List<DomainTxn>,
        partiallyRefunded: Map<Long, Long> = emptyMap(),
    ): DomainTxn? {
        if (refund.type != TxnType.REFUND) return null
        if (refund.amountCents <= 0L) return null

        val windowMs = windowDays.toLong() * DAY_MS
        return candidates
            .asSequence()
            .filter { it.type == TxnType.EXPENSE }
            .filter { it.amountCents == refund.amountCents }
            .filter { it.occurredAt < refund.occurredAt }
            .filter { refund.occurredAt - it.occurredAt <= windowMs }
            .filter { merchantsMatch(it.merchant, refund.merchant) }
            .filter { remainingCents(it, partiallyRefunded) > 0L }
            .sortedWith(
                compareByDescending<DomainTxn> { it.occurredAt }
                    .thenByDescending { it.id ?: Long.MIN_VALUE },
            )
            .firstOrNull()
    }

    /**
     * 一笔支出还剩多少可冲抵金额（分）。
     *
     * 优先用 [partiallyRefunded] 里的值（调用方从数据库汇总），没有时回退到账单自带的
     * [DomainTxn.refundedCents]。
     */
    fun remainingCents(
        txn: DomainTxn,
        partiallyRefunded: Map<Long, Long> = emptyMap(),
    ): Long {
        val refunded = txn.id?.let { partiallyRefunded[it] } ?: txn.refundedCents
        return txn.amountCents - refunded.coerceAtLeast(0L)
    }

    /** 商户匹配：都非空，且相等或一方包含另一方（忽略大小写与首尾空格）。 */
    private fun merchantsMatch(a: String?, b: String?): Boolean {
        val left = a?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return false
        val right = b?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return false
        return left == right || left.contains(right) || right.contains(left)
    }

    private companion object {
        /** 一天的毫秒数。 */
        const val DAY_MS = 86_400_000L
    }
}
