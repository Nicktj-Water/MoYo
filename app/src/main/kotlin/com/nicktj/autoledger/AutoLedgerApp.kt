package com.nicktj.autoledger

import android.app.Application
import com.nicktj.autoledger.service.cleanup.LogCleanup
import com.nicktj.autoledger.service.notify.TxnNotifier
import com.nicktj.autoledger.service.reminder.DailyReminderEntryPoint
import com.nicktj.autoledger.service.reminder.DailyReminderScheduler
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 应用入口。
 *
 * 只做几件轻量初始化：
 * 1. 建通知渠道（重复调用无副作用）；
 * 2. 冷启动时做一次原始文本过期清理（纯本地协程，不用 WorkManager、不常驻）；
 * 3. 若「每日记账提醒」开启则重排一次闹钟（系统/重启可能清掉过）。
 *
 * 引导是否完成不影响这里，也不清空任何数据；MainActivity 自己决定进哪个页面。
 */
@HiltAndroidApp
class AutoLedgerApp : Application() {

    /** 启动期的轻量后台任务，用完即走，不常驻。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        TxnNotifier.ensureChannels(this)
        LogCleanup.scheduleOnStart(this)
        restoreDailyReminder()
    }

    /** 提醒开着就重新排一次；关闭状态下 [DailyReminderScheduler.reschedule] 会顺手取消。 */
    private fun restoreDailyReminder() {
        val context = this
        appScope.launch {
            runCatching {
                val entry = EntryPointAccessors.fromApplication(
                    context,
                    DailyReminderEntryPoint::class.java,
                )
                val settings = entry.settingsRepository()
                DailyReminderScheduler.reschedule(
                    context,
                    settings.dailyReminderEnabled.first(),
                    settings.dailyReminderMinutes.first(),
                )
            }
        }
    }
}
