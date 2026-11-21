package com.nicktj.autoledger.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "autoledger_settings")

/** 主题模式：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        /** 从 DataStore 里存的字符串解析，非法值回退到跟随系统。 */
        fun fromStorage(raw: String?): ThemeMode =
            ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }
}

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

    /** 主题模式，默认跟随系统。 */
    val themeMode: Flow<ThemeMode> =
        dataStore.data.map { preferences ->
            ThemeMode.fromStorage(preferences[KEY_THEME_MODE])
        }

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

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.name }
    }

    // ------------------------------------------------------------------
    // 以下为「每日记账提醒」追加的成员（默认关闭，时间默认 21:00）
    // ------------------------------------------------------------------

    /** 每日记账提醒是否开启，默认关闭。 */
    val dailyReminderEnabled: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[KEY_DAILY_REMINDER_ENABLED] ?: false
        }

    /** 每日提醒时间：一天中的分钟数，默认 1260（21:00）。 */
    val dailyReminderMinutes: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[KEY_DAILY_REMINDER_MINUTES] ?: DEFAULT_DAILY_REMINDER_MINUTES
        }

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_DAILY_REMINDER_ENABLED] = enabled }
    }

    suspend fun setDailyReminderMinutes(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_DAILY_REMINDER_MINUTES] = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
        }
    }

    private companion object {
        const val DEFAULT_RAW_TEXT_RETENTION_DAYS = 30

        val KEY_RAW_TEXT_RETENTION_DAYS = intPreferencesKey("rawTextRetentionDays")
        val KEY_COUNT_RED_PACKET = booleanPreferencesKey("countRedPacket")
        val KEY_DB_ENCRYPTED = booleanPreferencesKey("dbEncrypted")
        val KEY_THEME_MODE = stringPreferencesKey("themeMode")
    }
}

// ----------------------------------------------------------------------
// 每日记账提醒的 DataStore key（新 key，与已有 key 不冲突）。
// 放在类外，避免改动上面已有的 private companion object。
// ----------------------------------------------------------------------

private const val MINUTES_PER_DAY = 24 * 60

/** 默认提醒时间 21:00，即一天中的第 1260 分钟。 */
private const val DEFAULT_DAILY_REMINDER_MINUTES = 21 * 60

private val KEY_DAILY_REMINDER_ENABLED = booleanPreferencesKey("dailyReminderEnabled")
private val KEY_DAILY_REMINDER_MINUTES = intPreferencesKey("dailyReminderMinutes")
