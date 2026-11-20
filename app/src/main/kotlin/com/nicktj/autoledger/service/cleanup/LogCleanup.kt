package com.nicktj.autoledger.service.cleanup

import android.content.Context
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.repo.RecognitionLogRepository
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 原始通知文本过期清理。
 *
 * 刻意不使用 WorkManager：WorkManager 会带进 FOREGROUND_SERVICE / WAKE_LOCK /
 * ACCESS_NETWORK_STATE 三个权限，而这个 App 承诺「不联网、不常驻」。
 * 改为在冷启动与开机时各跑一次，纯本地、无额外权限。
 */
object LogCleanup {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 后台跑一次清理，不阻塞启动。 */
    fun scheduleOnStart(context: Context) {
        val appContext = context.applicationContext
        scope.launch { runCatching { purgeExpired(appContext) } }
    }

    /** 真正执行清理：删过期识别日志，并把账单上的 rawLogId 置空。 */
    suspend fun purgeExpired(context: Context) {
        val entry = EntryPointAccessors.fromApplication(context, LogCleanupEntryPoint::class.java)
        val retentionDays = entry.settingsRepository().rawTextRetentionDays.first()
        if (retentionDays <= 0) return

        val now = System.currentTimeMillis()
        val cutoff = now - retentionDays * 24L * 60L * 60L * 1000L
        entry.recognitionLogRepository().purgeOlderThan(cutoff)
        entry.transactionDao().detachRawLogs(cutoff, now)
    }
}

/** 清理逻辑需要的依赖（不走构造注入，用 EntryPoint 取）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogCleanupEntryPoint {
    fun settingsRepository(): SettingsRepository

    fun transactionDao(): TransactionDao

    fun recognitionLogRepository(): RecognitionLogRepository
}
