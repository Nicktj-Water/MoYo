package com.nicktj.autoledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nicktj.autoledger.service.cleanup.LogCleanup
import com.nicktj.autoledger.service.listener.AutoLedgerNotificationListener
import com.nicktj.autoledger.service.notify.TxnNotifier
import com.nicktj.autoledger.service.reminder.DailyReminderEntryPoint
import com.nicktj.autoledger.service.reminder.DailyReminderScheduler
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机后做四件事：建渠道、重新请求绑定通知监听、跑一次过期清理、
 * 若「每日记账提醒」开启则重新排程（重启会清掉 AlarmManager 的闹钟）。
 *
 * 不需要 Hilt，系统广播里只做轻量操作。
 */
class AutoLedgerBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != ACTION_QUICKBOOT) return

        TxnNotifier.ensureChannels(context)
        LogCleanup.scheduleOnStart(context)
        // 部分系统会在重启后把通知监听解绑，这里主动请求重新绑定。
        AutoLedgerNotificationListener.requestRebindNow(context)
        // 闹钟不会跨重启存活，提醒开着就重新排一次。
        rescheduleDailyReminder(context)
    }

    /** 读设置需要协程，用 goAsync 撑住广播生命周期，读完立刻 finish。 */
    private fun rescheduleDailyReminder(context: Context) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    val entry = EntryPointAccessors.fromApplication(
                        appContext,
                        DailyReminderEntryPoint::class.java,
                    )
                    val settings = entry.settingsRepository()
                    DailyReminderScheduler.reschedule(
                        appContext,
                        settings.dailyReminderEnabled.first(),
                        settings.dailyReminderMinutes.first(),
                    )
                }
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        /** 部分国产 ROM 的快速开机广播。 */
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
