package com.nicktj.autoledger.ui.permission

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nicktj.autoledger.ui.components.IconTile
import com.nicktj.autoledger.ui.components.Pill
import com.nicktj.autoledger.ui.theme.LocalExtra

/** 权限状态胶囊：已开启 / 未开启 / 待确认。 */
@Composable
fun StatusPill(
    status: PermissionStatus,
    modifier: Modifier = Modifier,
) {
    val extra = LocalExtra.current
    val (text, container, content) = when (status) {
        PermissionStatus.Granted -> Triple(
            "已开启",
            extra.success.copy(alpha = 0.14f),
            extra.success,
        )

        PermissionStatus.Denied -> Triple(
            "未开启",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )

        is PermissionStatus.Unknown -> Triple(
            "待确认",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Pill(text = text, modifier = modifier, containerColor = container, contentColor = content)
}

/** 自检页的一行权限：图标 + 名称/说明 + 状态 + 跳转按钮。 */
@Composable
fun PermissionRow(
    item: PermissionItemState,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "设置",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = item.kind.icon, size = 38.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.kind.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = describe(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        StatusPill(status = item.status)
        TextButton(onClick = onAction) {
            Text(text = actionLabel)
        }
    }
}

/** Unknown 状态展示原因，其余展示权限说明。 */
private fun describe(item: PermissionItemState): String =
    (item.status as? PermissionStatus.Unknown)?.reason ?: item.kind.description
