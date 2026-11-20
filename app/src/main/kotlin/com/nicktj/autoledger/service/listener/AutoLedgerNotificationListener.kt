package com.nicktj.autoledger.service.listener

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.service.notify.TxnNotifier
import com.nicktj.autoledger.service.recognition.RecognitionPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知监听服务：只把支付宝 / 微信 / 云闪付的通知交给 [RecognitionPipeline]。
 *
 * 不做任何解析，也不落库；所有逻辑都在流水线里，方便单测与复用。
 */
@AndroidEntryPoint
class AutoLedgerNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var pipeline: RecognitionPipeline

    @Inject
    lateinit var notifier: TxnNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val sbnValue = sbn ?: return
        val pkg = sbnValue.packageName ?: return
        // 只处理三个支付包名；其他 App 的通知一律不读。
        if (PaySource.fromPackageName(pkg) == null) return

        val extras = sbnValue.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        // 折叠通知的多行文本（例如「[2条]微信支付：…」）也合并进来，交给解析器处理。
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { line -> line?.toString()?.trim()?.takeIf { it.isNotEmpty() } }
            .orEmpty()
        val mergedBigText = buildList {
            if (bigText != null) add(bigText)
            if (textLines.isNotEmpty()) add(textLines.joinToString("\n"))
        }.joinToString("\n").ifEmpty { null }

        val occurredAt = sbnValue.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        scope.launch {
            pipeline.handle(
                pkg = pkg,
                title = title,
                text = text,
                bigText = mergedBigText,
                channel = Channel.NOTIFICATION,
                occurredAt = occurredAt,
            )
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected.value = false
        // 系统把监听解绑了（常见于省电策略/系统清理），主动请求重新绑定。
        requestRebindNow(this)
        scope.launch { notifier.notifyListenerDisconnected() }
    }

    override fun onDestroy() {
        connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** 当前通知监听是否连着，自检面板直接读这个 Flow。 */
        val connected: MutableStateFlow<Boolean> = MutableStateFlow(false)

        /** 本服务的组件名（绑定/解绑都靠它）。 */
        fun componentName(context: Context): ComponentName =
            ComponentName(context, AutoLedgerNotificationListener::class.java)

        /** 请求系统重新绑定监听（自检页「重新绑定监听」按钮调这里）。 */
        fun requestRebindNow(context: Context) {
            runCatching { NotificationListenerService.requestRebind(componentName(context)) }
        }
    }
}
