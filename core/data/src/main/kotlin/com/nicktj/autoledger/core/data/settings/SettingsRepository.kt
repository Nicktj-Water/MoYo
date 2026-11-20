package com.nicktj.autoledger.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "autoledger_settings")

/**
 * 应用设置。所有值都有默认值，读不到时返回默认，不抛异常。
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    /** 原始通知文本保留天数，默认 30 天。 */
    val rawTextRetentionDays: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[KEY_RAW_TEXT_RETENTION_DAYS] ?: DEFAULT_RAW_TEXT_RETENTION_DAYS
        }

    /** 红包是否计入收支，默认关闭。 */
    val countRedPacket: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[KEY_COUNT_RED_PACKET] ?: false }

    /** 数据库是否加密（SQLCipher 等后续步骤接入），默认关闭。 */
    val dbEncrypted: Flow<Boolean> =
        dataStore.data.map { preferences -> preferences[KEY_DB_ENCRYPTED] ?: false }

    suspend fun setRawTextRetentionDays(days: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_RAW_TEXT_RETENTION_DAYS] = days.coerceAtLeast(0)
        }
    }

    suspend fun setCountRedPacket(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_COUNT_RED_PACKET] = enabled }
    }

    suspend fun setDbEncrypted(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_DB_ENCRYPTED] = enabled }
    }

    private companion object {
        const val DEFAULT_RAW_TEXT_RETENTION_DAYS = 30

        val KEY_RAW_TEXT_RETENTION_DAYS = intPreferencesKey("rawTextRetentionDays")
        val KEY_COUNT_RED_PACKET = booleanPreferencesKey("countRedPacket")
        val KEY_DB_ENCRYPTED = booleanPreferencesKey("dbEncrypted")
    }
}
