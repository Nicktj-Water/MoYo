package com.nicktj.autoledger.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 按时间范围（毫秒时间戳，闭区间）查账单。 */
    @Query(
        "SELECT * FROM transactions WHERE occurredAt BETWEEN :from AND :to " +
            "ORDER BY occurredAt DESC, id DESC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    /** 待确认账单。 */
    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY occurredAt DESC, id DESC")
    fun observePending(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query(
        "SELECT COALESCE(SUM(amountCents), 0) FROM transactions " +
            "WHERE status = 'CONFIRMED' AND type = :type AND occurredAt BETWEEN :from AND :to"
    )
    fun observeTotalByType(type: String, from: Long, to: Long): Flow<Long>

    /** 按分类汇总（categoryId 可能为 null，表示未分类）。 */
    @Query(
        """
        SELECT categoryId AS categoryId, COALESCE(SUM(amountCents), 0) AS totalCents
        FROM transactions
        WHERE status = 'CONFIRMED' AND type = :type AND occurredAt BETWEEN :from AND :to
        GROUP BY categoryId
        ORDER BY totalCents DESC
        """
    )
    fun observeCategoryTotals(type: String, from: Long, to: Long): Flow<List<CategoryTotal>>

    /** 按月汇总，yearMonth 形如 2026-11（设备本地时区）。 */
    @Query(
        """
        SELECT strftime('%Y-%m', occurredAt / 1000, 'unixepoch', 'localtime') AS yearMonth,
               COALESCE(SUM(amountCents), 0) AS totalCents
        FROM transactions
        WHERE status = 'CONFIRMED' AND type = :type
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
        """
    )
    fun observeMonthlyTotals(type: String): Flow<List<MonthTotal>>
}

data class CategoryTotal(
    val categoryId: Long?,
    val totalCents: Long,
)

data class MonthTotal(
    val yearMonth: String,
    val totalCents: Long,
)
