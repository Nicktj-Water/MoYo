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
}
