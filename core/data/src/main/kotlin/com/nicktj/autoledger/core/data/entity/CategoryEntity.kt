package com.nicktj.autoledger.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 分类。kind 存 EXPENSE / INCOME / BOTH。 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val kind: String,
    val icon: String,
    val builtin: Boolean = false,
    val sortOrder: Int = 0,
)
