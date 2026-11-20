package com.nicktj.autoledger.service.recognition

import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.domain.CategoryDef
import com.nicktj.autoledger.core.domain.DomainTxn
import com.nicktj.autoledger.core.model.CategoryKind
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType

/**
 * 数据库实体与领域对象之间的转换。
 *
 * 数据库里的枚举一律以「枚举名」保存，读取时做一次容错转换，
 * 避免历史数据里出现未知名字时整条流程崩掉。
 */
internal fun TransactionEntity.toDomainTxn(): DomainTxn = DomainTxn(
    id = id,
    amountCents = amountCents,
    type = type.toTxnType(),
    merchant = merchant,
    source = source.toPaySource(),
    channel = channel.toChannel(),
    occurredAt = occurredAt,
    confidence = confidence,
    // 账单表不存原始文本（存在 recognition_logs 里，通过 rawLogId 关联），
    // 这里留空串，让 DedupEngine 合并时直接采用新上报的完整文本。
    rawText = "",
    categoryId = categoryId,
    refundedCents = refundedCents,
)

internal fun CategoryEntity.toCategoryDef(): CategoryDef = CategoryDef(
    id = id,
    name = name,
    kind = kind.toCategoryKind(),
)

internal fun String.toTxnType(): TxnType =
    runCatching { TxnType.valueOf(this) }.getOrDefault(TxnType.EXPENSE)

internal fun String.toPaySource(): PaySource =
    PaySource.entries.firstOrNull { it.name == this } ?: PaySource.MANUAL

internal fun String.toChannel(): Channel =
    runCatching { Channel.valueOf(this) }.getOrDefault(Channel.NOTIFICATION)

internal fun String.toCategoryKind(): CategoryKind =
    runCatching { CategoryKind.valueOf(this) }.getOrDefault(CategoryKind.BOTH)
