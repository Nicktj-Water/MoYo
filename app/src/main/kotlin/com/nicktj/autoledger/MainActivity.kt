package com.nicktj.autoledger

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nicktj.autoledger.core.data.settings.ThemeMode
import com.nicktj.autoledger.ui.AutoLedgerRoot
import com.nicktj.autoledger.ui.MainViewModel
import com.nicktj.autoledger.ui.theme.AutoLedgerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 唯一 Activity。
 *
 * 通知点击可以带 `extra_txn_id`（Long）进来，应用会直接打开那笔账单的编辑弹层；
 * 兼容字段 `txnId` 同样会被读取。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** 通知带进来的账单 id；onNewIntent 时也会更新。 */
    private val pendingTxnId = MutableStateFlow<Long?>(null)

    /** 小组件「记一笔」带进来的意图：直接打开手动记账。 */
    private val pendingManual = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTxnId.value = intent.readTargetTxnId()
        pendingManual.value = intent.readOpenManual()
        setContent {
            // 主题：跟随系统 / 浅色 / 深色，偏好变化会即时重组。
            val themeMode by viewModel.themeMode.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemInDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // 状态栏 / 导航栏图标跟随应用主题，而不是只在启动时判断一次。
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim = android.graphics.Color.TRANSPARENT,
                        darkScrim = android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
            }

            val txnId by pendingTxnId.collectAsState()
            val openManual by pendingManual.collectAsState()

            AutoLedgerTheme(darkTheme = darkTheme) {
                AutoLedgerRoot(
                    initialTxnId = txnId,
                    openManual = openManual,
                    onManualConsumed = { pendingManual.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTxnId.value = intent.readTargetTxnId()
        pendingManual.value = intent.readOpenManual()
    }

    private fun Intent?.readOpenManual(): Boolean = this?.getBooleanExtra(EXTRA_OPEN_MANUAL, false) ?: false

    private fun Intent?.readTargetTxnId(): Long? {
        val primary = this?.getLongExtra(EXTRA_TXN_ID, INVALID_ID) ?: INVALID_ID
        val fallback = this?.getLongExtra(EXTRA_TXN_ID_COMPAT, INVALID_ID) ?: INVALID_ID
        val id = if (primary > 0L) primary else fallback
        return id.takeIf { it > 0L }
    }

    companion object {
        /** 通知点击时带上的账单 id（Long）。 */
        const val EXTRA_TXN_ID: String = "extra_txn_id"

        /** 小组件「记一笔」带上的 extra：为 true 时直接打开手动记账。 */
        const val EXTRA_OPEN_MANUAL: String = "open_manual"

        /** 兼容写法：另一个代理如果用了这个 key 也能打开。 */
        const val EXTRA_TXN_ID_COMPAT: String = "txnId"

        private const val INVALID_ID = -1L
    }
}
