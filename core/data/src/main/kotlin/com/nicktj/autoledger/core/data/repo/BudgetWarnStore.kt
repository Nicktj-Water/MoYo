package com.nicktj.autoledger.core.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** 预算提醒标记专用的 DataStore（和设置项分开，避免和 SettingsRepository 抢同一个文件）。 */
private val Context.budgetWarnDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "autoledger_budget_warn")

/**
 * 预算提醒去重。
 *
 * 80% / 100% 两个阈值，同一个月每个阈值只提醒一次；
 * 标记键形如 `budgetWarned_2026-11_80`，存在单独的 DataStore 里。
 */
@Singleton
class BudgetWarnStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dataStore = context.applicationContext.budgetWarnDataStore

    /** 该月该阈值是否已经提醒过。 */
    suspend fun isWarned(yearMonth: String, level: Int): Boolean {
        val preferences = dataStore.data.first()
        return preferences[key(yearMonth, level)] ?: false
    }

    /** 标记该月该阈值已经提醒过。 */
    suspend fun markWarned(yearMonth: String, level: Int) {
        dataStore.edit { preferences ->
            preferences[key(yearMonth, level)] = true
        }
    }

    private fun key(yearMonth: String, level: Int) =
        booleanPreferencesKey("budgetWarned_${yearMonth}_$level")

    companion object {
        /** 80% 提醒。 */
        const val LEVEL_WARNING: Int = 80

        /** 100% 超支提醒。 */
        const val LEVEL_OVER: Int = 100
    }
}
