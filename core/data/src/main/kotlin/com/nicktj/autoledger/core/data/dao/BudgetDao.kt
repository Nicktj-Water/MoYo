package com.nicktj.autoledger.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nicktj.autoledger.core.data.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets ORDER BY yearMonth DESC")
    fun observeAll(): Flow<List<BudgetEntity>>

    /** 查某个月的预算：通用默认（yearMonth 为 null）和该月专属都会返回。 */
    @Query("SELECT * FROM budgets WHERE yearMonth IS NULL OR yearMonth = :yearMonth")
    suspend fun findForMonth(yearMonth: String): List<BudgetEntity>

    // ------------------------------------------------------------------
    // 以下为预算设置页追加的方法
    // ------------------------------------------------------------------

    /** 只取「默认每月」的预算（yearMonth 为 null），设置页编辑的就是这一组。 */
    @Query("SELECT * FROM budgets WHERE yearMonth IS NULL ORDER BY id ASC")
    suspend fun getDefaults(): List<BudgetEntity>

    /** 保存预算前整体重写默认预算，避免残留已删除的分类预算。 */
    @Query("DELETE FROM budgets WHERE yearMonth IS NULL")
    suspend fun deleteDefaults()
}
