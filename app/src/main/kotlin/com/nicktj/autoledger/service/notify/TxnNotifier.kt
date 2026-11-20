package com.nicktj.autoledger.service.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nicktj.autoledger.MainActivity
import com.nicktj.autoledger.R
import com.nicktj.autoledger.core.data.repo.BudgetWarnStore
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.service.BudgetSnapshot
import com.nicktj.autoledger.service.BudgetSnapshotProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** 支付来源的中文名（通知文案用）。 */
fun PaySource.displayName(): String = when (this) {
    PaySource.ALIPAY -> "支付宝"
    PaySource.WECHAT -> "微信"
    PaySource.UNIONPAY -> "云闪付"
    PaySource.MANUAL -> "手动记账"
}

/**
 * 记账提醒通知。
 *
 * 两个渠道：`txn_alerts`（高优先级，入账/待补金额/预算阈值）、`service_status`（默认优先级，监听断开等）。
 * 不做「上岛」之类的特殊样式，普通通知即可。
 */
@Singleton
class TxnNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotProvider: BudgetSnapshotProvider,
    private val warnStore: BudgetWarnStore,
) {

    /** 入账成功提醒（含今日/本月累计与预算剩余）。 */
    suspend fun notifyTxnLogged(
        txnId: Long,
        amountCents: Long,
        type: TxnType,
        categoryName: String?,
        merchant: String?,
    ) {
        val snapshot = loadSnapshot()
        val amountText = formatYuan(amountCents)
        val title = when (type) {
            TxnType.EXPENSE, TxnType.RED_PACKET_OUT -> "已记账 $amountText"
            TxnType.INCOME, TxnType.RED_PACKET_IN -> "已入账 $amountText"
            TxnType.REFUND -> "已退款 $amountText"
        }
        val body = listOfNotNull(
            categoryName?.takeIf { it.isNotBlank() } ?: "未分类",
            merchant?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

        post(
            id = txnNotificationId(txnId),
            channelId = CHANNEL_TXN,
            title = title,
            content = body,
            bigText = buildBigText(snapshot),
            color = colorFor(snapshot?.usedRatio),
            txnId = txnId,
            highPriority = true,
        )
        checkBudgetThresholds(snapshot)
    }

    /** 金额缺失（例如微信卡片通知里读不到金额）时提示用户手动补金额。 */
    suspend fun notifyNeedsAmount(txnId: Long, sourceName: String) {
        post(
            id = txnNotificationId(txnId),
            channelId = CHANNEL_TXN,
            title = "有一笔待补金额",
            content = "$sourceName 的这条通知里没有金额，点开补一下",
            bigText = "已先存为「待确认」，补上金额后才会计入统计。",
            color = COLOR_WARNING,
            txnId = txnId,
            highPriority = true,
        )
    }

    /** 服务状态提醒：通知监听断开、重新绑定等。 */
    suspend fun notifyServiceIssue(title: String, content: String) {
        post(
            id = SERVICE_NOTIFICATION_ID,
            channelId = CHANNEL_STATUS,
            title = title,
            content = content,
            bigText = null,
            color = null,
            txnId = null,
            highPriority = false,
        )
    }

    /** 通知监听被系统断开。 */
    suspend fun notifyListenerDisconnected() {
        notifyServiceIssue(
            title = "通知监听已断开",
            content = "正在尝试重新绑定；若仍收不到账单，请到「自检」页手动重新绑定。",
        )
    }

    /** 读取快照；读库失败时不阻塞通知本身。 */
    private suspend fun loadSnapshot(): BudgetSnapshot? =
        runCatching { snapshotProvider.snapshot() }.getOrNull()

    private fun buildBigText(snapshot: BudgetSnapshot?): String {
        if (snapshot == null) return "点开查看账单详情"
        val lines = mutableListOf(
            "今日累计 ${formatYuan(snapshot.todaySpentCents)}",
            "本月累计 ${formatYuan(snapshot.monthSpentCents)}",
        )
        if (snapshot.budgetCents > 0L) {
            val percent = (snapshot.usedRatio * 100).roundToInt().coerceAtLeast(0)
            lines += "预算剩余 ${formatYuan(snapshot.remainingCents)}（已用 $percent%）"
        } else {
            lines += "本月还没有设置预算"
        }
        return lines.joinToString("\n")
    }

    /** 预算阈值提醒：80% / 100% 各提醒一次。 */
    private suspend fun checkBudgetThresholds(snapshot: BudgetSnapshot?) {
        if (snapshot == null || snapshot.budgetCents <= 0L) return
        val ratio = snapshot.usedRatio
        val month = snapshot.yearMonth

        // 一次跨过 100% 时只发超支提醒，同时把 80% 标记掉，避免补发一条。
        if (ratio >= 1.0 && !warnStore.isWarned(month, BudgetWarnStore.LEVEL_OVER)) {
            warnStore.markWarned(month, BudgetWarnStore.LEVEL_OVER)
            warnStore.markWarned(month, BudgetWarnStore.LEVEL_WARNING)
            val percent = (ratio * 100).roundToInt()
            post(
                id = BUDGET_NOTIFICATION_ID,
                channelId = CHANNEL_TXN,
                title = "本月预算已超支",
                content = "已用 $percent% · 超出 ${formatYuan(-snapshot.remainingCents).removePrefix("-")}",
                bigText = "本月预算 ${formatYuan(snapshot.budgetCents)}，已花 ${formatYuan(snapshot.monthSpentCents)}。",
                color = COLOR_DANGER,
                txnId = null,
                highPriority = true,
            )
            return
        }

        if (ratio >= 0.8 && ratio < 1.0 && !warnStore.isWarned(month, BudgetWarnStore.LEVEL_WARNING)) {
            warnStore.markWarned(month, BudgetWarnStore.LEVEL_WARNING)
            val percent = (ratio * 100).roundToInt()
            post(
                id = BUDGET_NOTIFICATION_ID,
                channelId = CHANNEL_TXN,
                title = "本月预算已用 $percent%",
                content = "剩余 ${formatYuan(snapshot.remainingCents)}",
                bigText = "本月预算 ${formatYuan(snapshot.budgetCents)}，已花 ${formatYuan(snapshot.monthSpentCents)}。",
                color = COLOR_WARNING,
                txnId = null,
                highPriority = true,
            )
        }
    }

    /** < 80% 正常（主题色）、80%~100% 黄、>= 100% 红。 */
    private fun colorFor(usedRatio: Double?): Int = when {
        usedRatio == null -> COLOR_NORMAL
        usedRatio >= 1.0 -> COLOR_DANGER
        usedRatio >= 0.8 -> COLOR_WARNING
        else -> COLOR_NORMAL
    }

    private fun post(
        id: Int,
        channelId: String,
        title: String,
        content: String,
        bigText: String?,
        color: Int?,
        txnId: Long?,
        highPriority: Boolean,
    ) {
        if (!canPostNotifications()) return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText ?: content))
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setColorized(false)
            .setContentIntent(openAppIntent(txnId))

        if (color != null) builder.setColor(color)

        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }

    /** 点通知打开 App，并带上 txnId（MainActivity 会读这个 extra）。 */
    private fun openAppIntent(txnId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            if (txnId != null) putExtra(EXTRA_TXN_ID, txnId)
        }
        val requestCode = (txnId ?: 0L).let { (it % Int.MAX_VALUE).toInt() }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun canPostNotifications(): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun txnNotificationId(txnId: Long): Int =
        (TXN_NOTIFICATION_BASE + (txnId % 1000L)).toInt()

    companion object {
        /** 记账提醒渠道。 */
        const val CHANNEL_TXN = "txn_alerts"

        /** 服务状态渠道。 */
        const val CHANNEL_STATUS = "service_status"

        /** 点通知时携带的 extra（MainActivity 读这个 key）。 */
        const val EXTRA_TXN_ID = "txnId"

        private const val TXN_NOTIFICATION_BASE = 100
        private const val BUDGET_NOTIFICATION_ID = 2_001
        private const val SERVICE_NOTIFICATION_ID = 3_001

        private const val COLOR_NORMAL = 0xFF4F46E5.toInt()
        private const val COLOR_WARNING = 0xFFF59E0B.toInt()
        private const val COLOR_DANGER = 0xFFE5484D.toInt()

        /** 启动时创建通知渠道（重复调用无副作用）。 */
        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val txnChannel = NotificationChannel(
                CHANNEL_TXN,
                "记账提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "入账、退款、待补金额与预算阈值提醒"
                enableVibration(true)
                setShowBadge(true)
            }
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "服务状态",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "通知监听断开、需要重新绑定等状态提醒"
            }
            manager.createNotificationChannels(listOf(txnChannel, statusChannel))
        }

        /** 百分比文案，保留整数。 */
        fun percentText(ratio: Double): String =
            String.format(Locale.CHINA, "%d%%", (ratio * 100).roundToInt())
    }
}
