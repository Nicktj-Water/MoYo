package com.nicktj.autoledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nicktj.autoledger.service.cleanup.LogCleanup
import com.nicktj.autoledger.service.listener.AutoLedgerNotificationListener
import com.nicktj.autoledger.service.notify.TxnNotifier

/**
 * 开机后做三件事：建渠道、重新请求绑定通知监听、跑一次过期清理。
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
    }

    private companion object {
        /** 部分国产 ROM 的快速开机广播。 */
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
