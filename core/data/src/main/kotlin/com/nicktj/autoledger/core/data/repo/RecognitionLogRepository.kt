package com.nicktj.autoledger.core.data.repo

import com.nicktj.autoledger.core.data.dao.RecognitionLogDao
import com.nicktj.autoledger.core.data.entity.RecognitionLogEntity
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.RecognitionOutcome
import com.nicktj.autoledger.core.model.TxnType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 识别日志仓库。
 *
 * 通知监听 / 无障碍兜底 / 自检面板都通过它读写 recognition_logs，
 * 避免各处重复拼 RecognitionLogEntity。
 */
@Singleton
class RecognitionLogRepository @Inject constructor(
    private val recognitionLogDao: RecognitionLogDao,
) {

    /** 写一条识别日志，返回日志 id。 */
    suspend fun record(
        receivedAt: Long,
        packageName: String,
        channel: Channel,
        title: String?,
        text: String?,
        bigText: String?,
        outcome: RecognitionOutcome,
        parsedAmountCents: Long? = null,
        parsedType: TxnType? = null,
        parsedMerchant: String? = null,
        confidence: Double = 0.0,
        linkedTxnId: Long? = null,
        note: String? = null,
    ): Long = recognitionLogDao.insert(
        RecognitionLogEntity(
            receivedAt = receivedAt,
            packageName = packageName,
            channel = channel.name,
            title = title,
            text = text,
            bigText = bigText,
            parsedAmountCents = parsedAmountCents,
            parsedType = parsedType?.name,
            parsedMerchant = parsedMerchant,
            confidence = confidence.coerceIn(0.0, 1.0),
            outcome = outcome.name,
            linkedTxnId = linkedTxnId,
            note = note,
        ),
    )

    /** 最近 [limit] 条日志（自检面板用）。 */
    fun observeRecent(limit: Int): Flow<List<RecognitionLogEntity>> =
        recognitionLogDao.observeRecent(limit)

    /**
     * 同一个包名 + 同一段文本，在 [withinMs] 内是否已经记过日志。
     *
     * 「非支付通知」量很大（聊天、系统提示），用这个做去噪：同一段文本 10 分钟内只记一条。
     */
    suspend fun isRepeatedNoise(
        packageName: String,
        text: String?,
        withinMs: Long = NOISE_WINDOW_MS,
    ): Boolean {
        val since = System.currentTimeMillis() - withinMs
        return recognitionLogDao.countRecentNoise(packageName, text, since) > 0
    }

    /** 删除 [before] 之前的日志，返回删除条数。 */
    suspend fun purgeOlderThan(before: Long): Int = recognitionLogDao.deleteOlderThan(before)

    companion object {
        /** 非支付通知的记日志去噪窗口：10 分钟。 */
        const val NOISE_WINDOW_MS: Long = 10 * 60 * 1000L
    }
}
