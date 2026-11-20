package com.nicktj.autoledger.ui.screens.pending

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.ui.components.AmountText
import com.nicktj.autoledger.ui.components.ConfidencePill
import com.nicktj.autoledger.ui.components.EmptyHint
import com.nicktj.autoledger.ui.components.IconTile
import com.nicktj.autoledger.ui.components.PageHeader
import com.nicktj.autoledger.ui.components.Pill
import com.nicktj.autoledger.ui.components.SoftCard
import com.nicktj.autoledger.ui.components.categoryTileColors
import com.nicktj.autoledger.ui.components.copyToClipboard
import com.nicktj.autoledger.ui.components.dateTimeLabel
import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.theme.TabularNums
import com.nicktj.autoledger.ui.screens.bills.BillEditorSheet

/**
 * 待确认列表：低置信度的自动识别结果先放这里，确认后才计入统计。
 *
 * 每条卡片可以展开看原始通知文本，操作为「确认入账 / 改完再确认 / 丢弃」。
 */
@Composable
fun PendingScreen(
    modifier: Modifier = Modifier,
    viewModel: PendingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val editTarget by viewModel.editTarget.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            PageHeader(
                title = "待确认",
                subtitle = when {
                    state.loading -> "正在读取…"
                    state.rows.isEmpty() -> "没有待处理的账单"
                    else -> "${state.rows.size} 条等你确认"
                },
            )
        }

        if (state.rows.isEmpty() && !state.loading) {
            item(key = "empty") {
                SoftCard(contentPadding = PaddingValues(0.dp)) {
                    EmptyHint(
                        title = "暂时没有待确认",
                        description = "识别置信度不够的账单会先落到这里，你确认后才计入统计。",
                    )
                }
            }
        }

        items(state.rows, key = { it.txn.id }) { row ->
            PendingCard(
                row = row,
                onConfirm = { viewModel.confirm(row.txn) },
                onEdit = { viewModel.openEditor(row.txn) },
                onDiscard = { viewModel.discard(row.txn) },
            )
        }
    }

    editTarget?.let { target ->
        BillEditorSheet(
            txn = target,
            categories = state.categories,
            confirmOnSave = true,
            onDismiss = viewModel::closeEditor,
            onSave = { updated, learn -> viewModel.saveConfirmed(updated, learn) },
            onDelete = { viewModel.discard(it) },
        )
    }
}

@Composable
private fun PendingCard(
    row: PendingRow,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDiscard: () -> Unit,
) {
    var expanded by rememberSaveable(row.txn.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val type = row.txn.txnType()
    val (tileBg, tileFg) = categoryTileColors(row.txn.categoryId)

    SoftCard(modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = row.categoryIcon,
                size = 42.dp,
                containerColor = tileBg,
                contentColor = tileFg,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.txn.merchant?.takeIf { it.isNotBlank() } ?: row.categoryName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateTimeLabel(row.txn.occurredAt),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            AmountText(
                amountCents = row.txn.amountCents,
                type = type,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                text = row.channelLabel,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Pill(
                text = row.sourceLabel,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Pill(
                text = row.categoryName,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            ConfidencePill(confidence = row.txn.confidence)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 原始文本：可折叠 + 可复制
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "原始文本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (expanded) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "复制原始文本",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            if (row.rawText.isNotBlank()) {
                                copyToClipboard(context, "识别文本", row.rawText)
                            }
                        },
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.KeyboardArrowUp
                } else {
                    Icons.Outlined.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        if (expanded) {
            Text(
                text = row.rawText.ifBlank { "（没有保留原始文本）" },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onConfirm, shape = RoundedCornerShape(12.dp)) {
                Text(text = "确认入账")
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(text = "改完再确认") }
            TextButton(
                onClick = onDiscard,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(text = "丢弃") }
        }
    }
}
