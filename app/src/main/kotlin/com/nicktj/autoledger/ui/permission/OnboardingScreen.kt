package com.nicktj.autoledger.ui.permission

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.ui.components.IconTile
import com.nicktj.autoledger.ui.components.PageHeader
import com.nicktj.autoledger.ui.components.SoftCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 权限引导页状态：每 2 秒刷新一次，从设置页返回后状态会自动更新。 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _items = MutableStateFlow(PermissionChecker.snapshot(context))
    val items: StateFlow<List<PermissionItemState>> = _items.asStateFlow()

    private val _completed = MutableStateFlow(OnboardingStore.hasCompleted(context))
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _items.value = PermissionChecker.snapshot(context)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    /** 手动刷新（页面进入时也会自动刷新）。 */
    fun refresh() {
        _items.value = PermissionChecker.snapshot(context)
    }

    /** 跳转到该项的系统设置页。 */
    fun open(kind: PermissionKind) {
        PermissionChecker.open(context, kind)
    }

    /** 标记引导完成；MainActivity 可以用 [OnboardingStore.hasCompleted] 判断是否还要进引导页。 */
    fun complete() {
        OnboardingStore.markCompleted(context)
        _completed.value = true
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 2_000L
    }
}

/**
 * 权限引导页。
 *
 * 逐项展示「通知使用权 / 无障碍 / 通知权限 / 自启动 / 省电策略 / 最近任务锁定」的当前状态，
 * 每项一个跳转按钮；底部按钮标记引导完成并回调 [onFinished]。
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val scrollState = rememberScrollState()

    // Android 13+ 的通知权限直接弹系统授权框，比跳到设置页更顺。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeader(
            title = "开启自动记账",
            subtitle = "AutoLedger 只在本机读支付通知，不联网、不上传",
        )

        items.forEach { item ->
            OnboardingPermissionCard(
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
            )
        }

        SoftCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                text = "建议顺序：通知使用权 → 无障碍 → 通知权限 → 自启动 / 省电策略 → 最近任务锁定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(
            onClick = {
                viewModel.complete()
                onFinished()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "进入 AutoLedger")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun OnboardingPermissionCard(
    item: PermissionItemState,
    onAction: () -> Unit,
) {
    SoftCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = item.kind.icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.kind.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(status = item.status)
                }
                Text(
                    text = (item.status as? PermissionStatus.Unknown)?.reason ?: item.kind.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FilledTonalButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
            Text(text = item.kind.actionLabel)
        }
    }
}
