package com.nicktj.autoledger.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 预算。yearMonth 为 null 表示「默认每月」，categoryId 为 null 表示「月总预算」。
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val yearMonth: String? = null,
    val categoryId: Long? = null,
    val amountCents: Long,
)
