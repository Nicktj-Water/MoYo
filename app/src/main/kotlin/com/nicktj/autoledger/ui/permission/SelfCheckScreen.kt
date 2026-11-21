package com.nicktj.autoledger.ui.permission

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.entity.RecognitionLogEntity
import com.nicktj.autoledger.core.data.repo.RecognitionLogRepository
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.service.listener.AutoLedgerNotificationListener
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitDivider
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconBadge
import com.nicktj.autoledger.ui.kit.KitIconButton
import com.nicktj.autoledger.ui.kit.KitRow
import com.nicktj.autoledger.ui.kit.KitSection
import com.nicktj.autoledger.ui.kit.KitTag
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 自检页状态：权限状态、监听连接状态、最近 5 条识别日志。 */
@HiltViewModel
class SelfCheckViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    recognitionLogRepository: RecognitionLogRepository,
) : ViewModel() {

    private val _items = MutableStateFlow(PermissionChecker.snapshot(context))
    val items: StateFlow<List<PermissionItemState>> = _items.asStateFlow()

    private val _rebindHint = MutableStateFlow<String?>(null)
    val rebindHint: StateFlow<String?> = _rebindHint.asStateFlow()

    /** 通知监听是否连着（直接读服务的 companion Flow）。 */
    val listenerConnected: StateFlow<Boolean> = AutoLedgerNotificationListener.connected.asStateFlow()

    /** 最近 5 条识别日志。 */
    val recentLogs: StateFlow<List<RecognitionLogEntity>> = recognitionLogRepository.observeRecent(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    init {
        viewModelScope.launch {
            while (isActive) {
                _items.value = PermissionChecker.snapshot(context)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        _items.value = PermissionChecker.snapshot(context)
    }

    fun open(kind: PermissionKind) {
        PermissionChecker.open(context, kind)
    }

    /** 请求系统重新绑定通知监听。 */
    fun rebindListener() {
        AutoLedgerNotificationListener.requestRebindNow(context)
        _rebindHint.value = "已请求系统重新绑定通知监听"
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}

/**
 * 自检面板。
 *
 * 分三段：服务状态（通知监听是否连着、无障碍是否开着）→ 权限（每项状态 + 跳转）
 * → 最近识别（最近 5 条日志），用来定位「为什么没记上」。
 */
@Composable
fun SelfCheckScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SelfCheckViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val connected by viewModel.listenerConnected.collectAsState()
    val logs by viewModel.recentLogs.collectAsState()
    val rebindHint by viewModel.rebindHint.collectAsState()

    // Android 13+ 的通知权限直接弹系统授权框。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    val accessibility = items.firstOrNull { it.kind == PermissionKind.ACCESSIBILITY }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Kit.PageHorizontal, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(Kit.Gap),
    ) {
        KitHeader(
            title = "自检",
            subtitle = "确认 AutoLedger 正在正常工作",
            action = {
                KitIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            },
        )

        KitSection(title = "服务状态")
        ServiceStatusCard(
            connected = connected,
            accessibility = accessibility,
            onRebind = { viewModel.rebindListener() },
        )
        rebindHint?.let { hint ->
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        KitSection(title = "权限", count = items.size)
        KitCard(padding = 0.dp) {
            Column {
                items.forEachIndexed { index, item ->
                    if (index > 0) KitDivider()
                    PermissionRow(
                        item = item,
                        onAction = {
                            if (
                                item.kind == PermissionKind.POST_NOTIFICATIONS &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.open(item.kind)
                            }
                        },
                        modifier = Modifier.padding(horizontal = Kit.CardPadding),
                    )
                }
            }
        }

        KitSection(title = "最近识别")
        KitCard(padding = 0.dp) {
            Column {
                if (logs.isEmpty()) {
                    Text(
                        text = "还没有识别记录。去用支付宝 / 微信付一笔，再回来看看。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Kit.CardPadding),
                    )
                } else {
                    logs.forEachIndexed { index, log ->
                        if (index > 0) KitDivider()
                        RecognitionLogRow(
                            log = log,
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** 服务状态：通知监听连接情况 + 无障碍服务；监听断开时给「重新绑定」。 */
@Composable
private fun ServiceStatusCard(
    connected: Boolean,
    accessibility: PermissionItemState?,
    onRebind: () -> Unit,
) {
    val extra = LocalExtra.current
    val listenerColor = if (connected) extra.success else extra.danger
    val accessibilityStatus = accessibility?.status
        ?: PermissionStatus.Unknown("没有读到无障碍状态，请稍后重试")

    KitCard(padding = 0.dp) {
        Column {
            KitRow(
                title = "通知监听",
                subtitle = if (connected) {
                    "服务已连接，正在接收支付通知"
                } else {
                    "服务未连接，系统可能已解绑"
                },
                modifier = Modifier.padding(horizontal = Kit.CardPadding),
                leading = { KitIconBadge(icon = Icons.Outlined.NotificationsActive, tint = listenerColor) },
                trailing = {
                    KitTag(
                        text = if (connected) "已连接" else "已断开",
                        color = listenerColor,
                    )
                },
            )
            KitDivider()
            KitRow(
                title = "无障碍服务",
                subtitle = (accessibilityStatus as? PermissionStatus.Unknown)?.reason
                    ?: "通知被折叠时读取页面文字兜底",
                modifier = Modifier.padding(horizontal = Kit.CardPadding),
                leading = {
                    KitIconBadge(icon = Icons.Outlined.Accessibility, tint = statusTint(accessibilityStatus))
                },
                trailing = {
                    KitTag(
                        text = statusLabel(accessibilityStatus),
                        color = statusTint(accessibilityStatus),
                    )
                },
            )
            if (!connected) {
                KitDivider()
                TextButton(
                    onClick = onRebind,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Kit.CardPadding, vertical = 4.dp),
                ) {
                    Text(text = "重新绑定监听")
                }
            }
        }
    }
}

@Composable
private fun RecognitionLogRow(
    log: RecognitionLogEntity,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtra.current
    val outcomeColor = when (log.outcome) {
        "INSERTED" -> extra.success
        "PENDING" -> extra.warning
        "ERROR" -> extra.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    KitRow(
        title = log.parsedMerchant ?: log.packageName,
        subtitle = buildString {
            append(formatTime(log.receivedAt))
            append(" · ")
            append(channelLabel(log.channel))
        },
        modifier = modifier,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitTag(text = outcomeLabel(log.outcome), color = outcomeColor)
                log.parsedAmountCents?.let { cents ->
                    Spacer(modifier = Modifier.width(Kit.Gap))
                    Text(
                        text = formatYuan(cents),
                        style = MaterialTheme.typography.labelLarge.merge(TabularNums),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    )
}

private fun outcomeLabel(outcome: String): String = when (outcome) {
    "INSERTED" -> "已入账"
    "PENDING" -> "待确认"
    "DUPLICATE_MERGED" -> "已合并"
    "FILTERED_MARKETING" -> "已过滤"
    "UNPARSED" -> "未识别"
    "ERROR" -> "识别出错"
    else -> outcome
}

private fun channelLabel(channel: String): String = when (channel) {
    "NOTIFICATION" -> "通知"
    "ACCESSIBILITY" -> "无障碍"
    "MANUAL" -> "手动"
    else -> channel
}

private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

private fun formatTime(timestamp: Long): String = TIME_FORMAT.format(Date(timestamp))
