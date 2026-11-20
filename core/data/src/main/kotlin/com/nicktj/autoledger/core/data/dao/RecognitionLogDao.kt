package com.nicktj.autoledger.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nicktj.autoledger.core.data.entity.RecognitionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognitionLogDao {

    @Insert
    suspend fun insert(log: RecognitionLogEntity): Long

    @Query("SELECT * FROM recognition_logs ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecognitionLogEntity>>

    @Query(
        "SELECT * FROM recognition_logs WHERE outcome = :outcome " +
            "ORDER BY receivedAt DESC, id DESC"
    )
    fun observeByOutcome(outcome: String): Flow<List<RecognitionLogEntity>>

    @Query("SELECT * FROM recognition_logs WHERE id = :id")
    suspend fun findById(id: Long): RecognitionLogEntity?

    @Query("DELETE FROM recognition_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清理过期日志，返回删除条数。 */
    @Query("DELETE FROM recognition_logs WHERE receivedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    // ------------------------------------------------------------------
    // 以下为识别日志页 / 导出追加的方法
    // ------------------------------------------------------------------

    /** 分页读取（识别日志页每页 50 条）。 */
    @Query(
        "SELECT * FROM recognition_logs ORDER BY receivedAt DESC, id DESC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun page(limit: Int, offset: Int): List<RecognitionLogEntity>

    /** 按 outcome 分页读取。 */
    @Query(
        "SELECT * FROM recognition_logs WHERE outcome = :outcome " +
            "ORDER BY receivedAt DESC, id DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun pageByOutcome(outcome: String, limit: Int, offset: Int): List<RecognitionLogEntity>

    /** 总条数；outcome 传 null 时统计全部。 */
    @Query("SELECT COUNT(*) FROM recognition_logs WHERE :outcome IS NULL OR outcome = :outcome")
    suspend fun countByOutcome(outcome: String?): Int

    /** 导出时按 id 批量取原始文本，避免逐条查询。 */
    @Query("SELECT * FROM recognition_logs WHERE id IN (:ids)")
    suspend fun textsByIds(ids: List<Long>): List<RecognitionLogEntity>

    // ------------------------------------------------------------------
    // 以下为服务层（识别流水线）追加的方法
    // ------------------------------------------------------------------

    /**
     * 同一包名 + 同一文本在 [since] 之后出现过几次。
     * 用于「非支付通知」的记日志去噪：同一段文本 10 分钟内只记一条。
     */
    @Query(
        "SELECT COUNT(*) FROM recognition_logs " +
            "WHERE packageName = :packageName " +
            "AND COALESCE(text, '') = COALESCE(:text, '') " +
            "AND receivedAt >= :since"
    )
    suspend fun countRecentNoise(packageName: String, text: String?, since: Long): Int
}
