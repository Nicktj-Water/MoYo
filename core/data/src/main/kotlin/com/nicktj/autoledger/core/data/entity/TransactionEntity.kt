package com.nicktj.autoledger.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 账单主表。枚举都以名字（String）保存，避免数据库层依赖 model 模块。
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["status"]),
        Index(value = ["source", "amountCents"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val amountCents: Long,
    /** [com.nicktj.autoledger.core.model.TxnType] 的枚举名。 */
    val type: String,
    val categoryId: Long? = null,
    val merchant: String? = null,
    /** [com.nicktj.autoledger.core.model.PaySource] 的枚举名。 */
    val source: String,
    /** [com.nicktj.autoledger.core.model.Channel] 的枚举名。 */
    val channel: String,
    val occurredAt: Long,
    val note: String? = null,
    val confidence: Double = 0.0,
    /** CONFIRMED / PENDING / DISCARDED */
    val status: String = "PENDING",
    val refundOfId: Long? = null,
    val refundedCents: Long = 0L,
    val rawLogId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
