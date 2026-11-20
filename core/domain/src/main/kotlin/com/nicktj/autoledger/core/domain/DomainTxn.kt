package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType

/**
 * 领域层的账单对象：解析结果入库前的中间形态，也是去重/退款/分类/预算的统一输入。
 *
 * 约定：
 * - [amountCents] 永远是正数（Long 分），收支方向由 [type] 表达。
 * - [id] 为 null 表示还没入库；入库后由数据库生成。
 * - [categoryId] 为 null 表示还没归类（未分类）。
 * - [refundedCents] 记录这笔支出已经被退款冲抵的金额，预算计算时要从支出里扣掉。
 */
data class DomainTxn(
    val id: Long?,
    val amountCents: Long,
    val type: TxnType,
    val merchant: String?,
    val source: PaySource,
    val channel: Channel,
    val occurredAt: Long,
    val confidence: Double,
    val rawText: String,
    /** 已归到的分类 id，未归类为 null。 */
    val categoryId: Long? = null,
    /** 已被退款冲抵的金额（分），0 表示没有被冲抵过。 */
    val refundedCents: Long = 0L,
)
