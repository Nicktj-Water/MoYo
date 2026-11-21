package com.nicktj.autoledger.ui.screens.pending

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.nicktj.autoledger.ui.components.copyToClipboard
import com.nicktj.autoledger.ui.components.dateTimeLabel
import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitDivider
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconButton
import com.nicktj.autoledger.ui.kit.KitTag
import com.nicktj.autoledger.ui.screens.bills.BillEditorSheet
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 待确认列表：低置信度的自动识别结果先放这里，确认后才计入统计。
 *
 * 每条卡片可以展开看原始通知文本，操作为「确认入账 / 修改 / 丢弃」。
 */
@Composable
fun PendingScreen(
    onBack: () -> Unit = {},
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
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Kit.PageHorizontal,
            end = Kit.PageHorizontal,
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Kit.Gap),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KitIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(8.dp))
                KitHeader(
                    title = "待确认",
                    modifier = Modifier.weight(1f),
                    subtitle = if (state.loading) "正在读取…" else "${state.rows.size} 条待确认",
                )
            }
        }

        if (state.rows.isEmpty() && !state.loading) {
            item(key = "empty") {
                KitCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "没有待确认的账单",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

    KitCard(modifier = Modifier.animateContentSize()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.txn.merchant?.takeIf { it.isNotBlank() } ?: row.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${row.sourceLabel} · ${row.channelLabel} · ${dateTimeLabel(row.txn.occurredAt)}",
                        style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                AmountText(
                    amountCents = row.txn.amountCents,
                    type = type,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KitTag(text = row.categoryName, color = MaterialTheme.colorScheme.primary)
                ConfidencePill(confidence = row.txn.confidence)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 原始文本：默认折叠，点「查看原文」展开；展开后可复制。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "收起原文" else "查看原文",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "原始文本",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
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
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = row.rawText.ifBlank { "（没有保留原始文本）" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            KitDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onConfirm,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(text = "确认入账")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text(text = "修改") }
                TextButton(
                    onClick = onDiscard,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(text = "丢弃") }
            }
        }
    }
}
