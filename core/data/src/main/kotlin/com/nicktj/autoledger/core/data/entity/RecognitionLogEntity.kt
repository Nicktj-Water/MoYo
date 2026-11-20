package com.nicktj.autoledger.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 识别日志：所有收到的通知都先记一条，便于回溯「为什么没记上」。
 * outcome 存 INSERTED / PENDING / DUPLICATE_MERGED / FILTERED_MARKETING / UNPARSED / ERROR。
 */
@Entity(tableName = "recognition_logs")
data class RecognitionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val receivedAt: Long,
    val packageName: String,
    val channel: String,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val parsedAmountCents: Long? = null,
    val parsedType: String? = null,
    val parsedMerchant: String? = null,
    val confidence: Double = 0.0,
    val outcome: String,
    val linkedTxnId: Long? = null,
    val note: String? = null,
)
