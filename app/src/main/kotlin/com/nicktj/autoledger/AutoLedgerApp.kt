package com.nicktj.autoledger

import android.app.Application
import com.nicktj.autoledger.service.cleanup.LogCleanup
import com.nicktj.autoledger.service.notify.TxnNotifier
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口。
 *
 * 只做两件轻量初始化：
 * 1. 建通知渠道（重复调用无副作用）；
 * 2. 冷启动时做一次原始文本过期清理（纯本地协程，不用 WorkManager、不常驻）。
 *
 * 引导是否完成不影响这里，也不清空任何数据；MainActivity 自己决定进哪个页面。
 */
@HiltAndroidApp
class AutoLedgerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        TxnNotifier.ensureChannels(this)
        LogCleanup.scheduleOnStart(this)
    }
}
