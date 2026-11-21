package com.nicktj.autoledger.service.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nicktj.autoledger.MainActivity
import com.nicktj.autoledger.R
import com.nicktj.autoledger.core.data.dao.TransactionDao
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 每日记账提醒的排程与通知。
 *
 * 与 App 其它后台逻辑保持一致：不用 WorkManager、不常驻、不联网。
 * 只用一个 `setInexactRepeating`（周期 = 一天）的 AlarmManager 闹钟，
 * 因此不需要 SCHEDULE_EXACT_ALARM 权限，系统可以按省电策略做小幅顺延。
 *
 * 一天最多一条：闹钟本身一天只响一次；响的时候再判断「今天是否已有账单」，
 * 已有就静默返回，不打扰用户。
 */
object DailyReminderScheduler {

    /** 通知渠道 id。 */
    const val CHANNEL_DAILY_REMINDER = "daily_reminder"

    /** 闹钟广播的 action。 */
    const val ACTION_DAILY_REMINDER = "com.nicktj.autoledger.action.DAILY_REMINDER"

    /** 与 TxnNotifier 的 requestCode 区分开，避免 PendingIntent 复用。 */
    private const val ALARM_REQUEST_CODE = 0x0D41

    /** 点通知打开 App 的 requestCode，同样与 TxnNotifier 的不同。 */
    private const val OPEN_REQUEST_CODE = 0x0D42

    /** 通知 id 固定不变。 */
    private const val NOTIFICATION_ID = 4_001

    /** 计时基准时区（提醒时间按这个时区解释）。 */
    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    /**
     * 按设置排程：关闭则取消，开启则排「下一次 [minutes] 对应时刻 + 每天重复」。
     * 重复调用无副作用（PendingIntent 用 FLAG_UPDATE_CURRENT 覆盖）。
     */
    fun reschedule(context: Context, enabled: Boolean, minutes: Int) {
        val appContext = context.applicationContext
        if (!enabled) {
            cancel(appContext)
            return
        }
        // 只在真正启用时建渠道，避免用户从没开过提醒却多出一个渠道。
        ensureChannel(appContext)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        runCatching {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTriggerAt(minutes),
                AlarmManager.INTERVAL_DAY,
                alarmIntent(appContext),
            )
        }
    }

    /** 取消提醒（关闭开关时调用）。 */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(alarmIntent(appContext)) }
    }

    /** 建通知渠道，重复调用无副作用。 */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_DAILY_REMINDER,
            "每日记账提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "每天固定时间提醒一次；今天已有账单时不提醒"
            setShowBadge(true)
        }
        runCatching { manager.createNotificationChannel(channel) }
    }

    /**
     * 下一次触发时间：今天的 [minutes] 对应时刻，已过则顺延到明天。
     * 返回 UTC 毫秒时间戳（RTC_WAKEUP 用的就是墙上时间）。
     */
    fun nextTriggerAt(minutes: Int, now: LocalDateTime = LocalDateTime.now(ZONE)): Long {
        val safe = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
        val time = LocalTime.of(safe / 60, safe % 60)
        val todayTarget = now.toLocalDate().atTime(time)
        val next = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
        return next.atZone(ZONE).toInstant().toEpochMilli()
    }

    /** 发「今天还没记账」提醒。 */
    fun postReminder(context: Context) {
        if (!canPostNotifications(context)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("今天还没记账吗？")
            .setContentText("点一下记一笔，几秒就好")
            .setStyle(NotificationCompat.BigTextStyle().bigText("点一下记一笔，几秒就好"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(openManualIntent(context))
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }
    }

    /** Android 13+ 需要 POST_NOTIFICATIONS，且用户没关掉通知（参考 TxnNotifier）。 */
    fun canPostNotifications(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 闹钟的 PendingIntent（广播给自己）。 */
    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** 点通知打开 App 的「手动记账」。 */
    private fun openManualIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra(MainActivity.EXTRA_OPEN_MANUAL, true)
        }
        return PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

/**
 * 闹钟到点后的接收器：读设置 → 判断今天有没有账单 → 决定是否发通知。
 *
 * 用 goAsync + 协程读库（DataStore 与 Room 都是 suspend）。
 */
class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { handle(appContext) }
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun handle(context: Context) {
        val entry = EntryPointAccessors.fromApplication(
            context,
            DailyReminderEntryPoint::class.java,
        )
        val settings = entry.settingsRepository()
        if (!settings.dailyReminderEnabled.first()) return

        // 没有通知权限就不必查库了；渠道也可能被清掉，补一次（幂等）。
        if (!DailyReminderScheduler.canPostNotifications(context)) return
        DailyReminderScheduler.ensureChannel(context)

        val zone = DailyReminderScheduler.ZONE
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
        val todaysTxns = entry.transactionDao().findBetween(start, end)
        if (todaysTxns.isNotEmpty()) return

        DailyReminderScheduler.postReminder(context)
    }
}

/** 提醒接收器需要的依赖（不走构造注入，用 EntryPoint 取）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DailyReminderEntryPoint {
    fun settingsRepository(): SettingsRepository

    fun transactionDao(): TransactionDao
}
