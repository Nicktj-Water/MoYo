package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.CategoryKind
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertTrue

/** 测试统一用上海时区，避免机器时区不同导致月份边界漂移。 */
internal val SHANGHAI: ZoneId = ZoneId.of("Asia/Shanghai")

internal const val MINUTE_MS: Long = 60_000L
internal const val HOUR_MS: Long = 3_600_000L
internal const val DAY_MS: Long = 86_400_000L

/** 用上海本地时间构造时间戳，测试里不手算毫秒。 */
internal fun at(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 12,
    minute: Int = 0,
    second: Int = 0,
): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, SHANGHAI)
    .toInstant()
    .toEpochMilli()

/** 测试基准时间：2026-11-15 12:00:00 +08:00。 */
internal val BASE_TIME: Long = at(2026, 11, 15, 12, 0, 0)

/** 构造一笔领域账单，参数都有合理的默认值。 */
internal fun txn(
    id: Long? = null,
    amountCents: Long = 10_000L,
    type: TxnType = TxnType.EXPENSE,
    merchant: String? = "美团",
    source: PaySource = PaySource.ALIPAY,
    channel: Channel = Channel.NOTIFICATION,
    occurredAt: Long = BASE_TIME,
    confidence: Double = 0.90,
    rawText: String = "支付宝 支出 100.00 元",
    categoryId: Long? = null,
    refundedCents: Long = 0L,
): DomainTxn = DomainTxn(
    id = id,
    amountCents = amountCents,
    type = type,
    merchant = merchant,
    source = source,
    channel = channel,
    occurredAt = occurredAt,
    confidence = confidence,
    rawText = rawText,
    categoryId = categoryId,
    refundedCents = refundedCents,
)

internal fun category(
    id: Long,
    name: String,
    kind: CategoryKind = CategoryKind.BOTH,
): CategoryDef = CategoryDef(id = id, name = name, kind = kind)

/** 测试用分类表，id 与 [Cats] 对应。 */
internal object Cats {
    const val DINING = 1L
    const val TRANSPORT = 2L
    const val SHOPPING = 3L
    const val ENTERTAINMENT = 4L
    const val UTILITIES = 5L
    const val MEDICAL = 6L
    const val TRANSFER = 7L
    const val RED_PACKET = 8L
    const val REFUND = 9L
    const val INCOME = 10L
    const val OTHER = 11L
    const val UNCATEGORIZED = 12L
}

internal val DEFAULT_CATEGORIES: List<CategoryDef> = listOf(
    category(Cats.DINING, BuiltinCategoryRules.NAME_DINING, CategoryKind.EXPENSE),
    category(Cats.TRANSPORT, BuiltinCategoryRules.NAME_TRANSPORT, CategoryKind.EXPENSE),
    category(Cats.SHOPPING, BuiltinCategoryRules.NAME_SHOPPING, CategoryKind.EXPENSE),
    category(Cats.ENTERTAINMENT, BuiltinCategoryRules.NAME_ENTERTAINMENT, CategoryKind.EXPENSE),
    category(Cats.UTILITIES, BuiltinCategoryRules.NAME_UTILITIES, CategoryKind.EXPENSE),
    category(Cats.MEDICAL, BuiltinCategoryRules.NAME_MEDICAL, CategoryKind.EXPENSE),
    category(Cats.TRANSFER, BuiltinCategoryRules.NAME_TRANSFER),
    category(Cats.RED_PACKET, BuiltinCategoryRules.NAME_RED_PACKET),
    category(Cats.REFUND, BuiltinCategoryRules.NAME_REFUND),
    category(Cats.INCOME, BuiltinCategoryRules.NAME_INCOME, CategoryKind.INCOME),
    category(Cats.OTHER, BuiltinCategoryRules.NAME_OTHER),
    category(Cats.UNCATEGORIZED, BuiltinCategoryRules.NAME_UNCATEGORIZED),
)

/** 断言决定是 Merge 并返回它，方便逐字段断言。 */
internal fun mergeOf(decision: DedupDecision): DedupDecision.Merge {
    assertTrue("期望 Merge，实际是 $decision", decision is DedupDecision.Merge)
    return decision as DedupDecision.Merge
}
