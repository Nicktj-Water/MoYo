package com.nicktj.autoledger.service.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.widget.TodaySpendWidget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 接收器用的依赖（BroadcastReceiver 不走构造注入，用 EntryPoint 取）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TxnActionEntryPoint {
    fun transactionDao(): TransactionDao
}

/**
 * 处理入账通知上的「撤销」。
 *
 * 撤销不删记录，而是把账单标记为 DISCARDED：统计与预算不再计入，但日志与原文仍可追溯。
 * 已有退款关联（refundedCents > 0）的支出不能撤销，否则退款会悬空。
 */
class TxnActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TxnNotifier.ACTION_UNDO) return
        val txnId = intent.getLongExtra(TxnNotifier.EXTRA_TXN_ID, -1L)
        if (txnId <= 0L) return

        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                undo(appContext, txnId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun undo(context: Context, txnId: Long) {
        val dao = EntryPointAccessors
            .fromApplication(context, TxnActionEntryPoint::class.java)
            .transactionDao()
        val txn = dao.findById(txnId)
        val message = when {
            txn == null -> "这笔账单已不存在"
            txn.status == "DISCARDED" -> "已经撤销过了"
            txn.refundedCents > 0L -> "这笔已有退款关联，无法撤销"
            else -> {
                dao.update(txn.copy(status = "DISCARDED", updatedAt = System.currentTimeMillis()))
                "已撤销这笔记账"
            }
        }
        NotificationManagerCompat.from(context).cancel(TxnNotifier.notificationIdFor(txnId))
        TodaySpendWidget.refresh(context)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
