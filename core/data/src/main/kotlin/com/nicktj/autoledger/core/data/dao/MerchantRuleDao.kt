package com.nicktj.autoledger.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.nicktj.autoledger.core.data.entity.MerchantRuleEntity

@Dao
interface MerchantRuleDao {

    /** 按 merchantKey 唯一键 upsert：存在则更新分类和时间。 */
    @Upsert
    suspend fun upsert(rule: MerchantRuleEntity)

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :merchantKey")
    suspend fun findByKey(merchantKey: String): MerchantRuleEntity?

    @Query("SELECT * FROM merchant_rules ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Delete
    suspend fun delete(rule: MerchantRuleEntity)
}
