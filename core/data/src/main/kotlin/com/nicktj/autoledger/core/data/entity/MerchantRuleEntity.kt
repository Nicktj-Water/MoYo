package com.nicktj.autoledger.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 商户关键词 -> 分类的学习规则，merchantKey 唯一。 */
@Entity(
    tableName = "merchant_rules",
    indices = [Index(value = ["merchantKey"], unique = true)],
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val merchantKey: String,
    val categoryId: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
