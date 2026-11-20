package com.nicktj.autoledger.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nicktj.autoledger.core.data.dao.BudgetDao
import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.MerchantRuleDao
import com.nicktj.autoledger.core.data.dao.RecognitionLogDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.entity.BudgetEntity
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.data.entity.MerchantRuleEntity
import com.nicktj.autoledger.core.data.entity.RecognitionLogEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        MerchantRuleEntity::class,
        RecognitionLogEntity::class,
        BudgetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AutoLedgerDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun merchantRuleDao(): MerchantRuleDao

    abstract fun recognitionLogDao(): RecognitionLogDao

    abstract fun budgetDao(): BudgetDao

    companion object {
        const val NAME = "autoledger.db"

        /**
         * 建库时插入预置分类（builtin = true）。
         * 直接用 SQL 而不是 DAO，避免在 onCreate 阶段拿到还没打开的数据库实例。
         */
        val prepopulateCallback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                PRESET_CATEGORIES.forEach { category ->
                    db.execSQL(
                        "INSERT INTO categories (id, name, kind, icon, builtin, sortOrder) " +
                            "VALUES (?, ?, ?, ?, 1, ?)",
                        arrayOf<Any?>(category.id, category.name, category.kind, category.icon, category.sortOrder),
                    )
                }
            }
        }
    }
}

internal data class PresetCategory(
    val id: Long,
    val name: String,
    val kind: String,
    val icon: String,
    val sortOrder: Int,
)

internal val PRESET_CATEGORIES = listOf(
    PresetCategory(1L, "餐饮", "EXPENSE", "restaurant", 0),
    PresetCategory(2L, "交通", "EXPENSE", "directions_bus", 1),
    PresetCategory(3L, "购物", "EXPENSE", "shopping_bag", 2),
    PresetCategory(4L, "娱乐", "EXPENSE", "sports_esports", 3),
    PresetCategory(5L, "生活缴费", "EXPENSE", "receipt_long", 4),
    PresetCategory(6L, "医疗", "EXPENSE", "local_hospital", 5),
    PresetCategory(7L, "转账", "BOTH", "swap_horiz", 6),
    PresetCategory(8L, "红包", "BOTH", "redeem", 7),
    PresetCategory(9L, "其他", "EXPENSE", "more_horiz", 8),
    PresetCategory(10L, "未分类", "BOTH", "help_outline", 9),
    PresetCategory(11L, "收入", "INCOME", "payments", 10),
    PresetCategory(12L, "退款", "INCOME", "undo", 11),
)
