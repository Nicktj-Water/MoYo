package com.nicktj.autoledger.ui.permission

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nicktj.autoledger.ui.kit.KitIconBadge
import com.nicktj.autoledger.ui.kit.KitRow
import com.nicktj.autoledger.ui.kit.KitTag
import com.nicktj.autoledger.ui.theme.LocalExtra

/** 权限状态的语义色：已开启 = 成功绿，未开启 = 警告黄，待确认 = 中性灰。 */
@Composable
internal fun statusTint(status: PermissionStatus): Color {
    val extra = LocalExtra.current
    return when (status) {
        PermissionStatus.Granted -> extra.success
        PermissionStatus.Denied -> extra.warning
        is PermissionStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** 权限状态的短标签文案。 */
internal fun statusLabel(status: PermissionStatus): String = when (status) {
    PermissionStatus.Granted -> "已开启"
    PermissionStatus.Denied -> "未开启"
    is PermissionStatus.Unknown -> "待确认"
}

/** 权限状态胶囊：已开启 / 未开启 / 待确认。 */
@Composable
fun StatusPill(
    status: PermissionStatus,
    modifier: Modifier = Modifier,
) {
    KitTag(text = statusLabel(status), color = statusTint(status), modifier = modifier)
}

/** 自检页的一行权限：图标 + 名称/说明 + 状态 + 跳转按钮。 */
@Composable
fun PermissionRow(
    item: PermissionItemState,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "设置",
) {
    KitRow(
        title = item.kind.title,
        subtitle = describe(item),
        modifier = modifier,
        leading = { KitIconBadge(icon = item.kind.icon, tint = statusTint(item.status)) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(status = item.status)
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onAction) {
                    Text(text = actionLabel)
                }
            }
        },
        onClick = onAction,
    )
}

/** Unknown 状态展示原因，其余展示权限说明。 */
private fun describe(item: PermissionItemState): String =
    (item.status as? PermissionStatus.Unknown)?.reason ?: item.kind.description
