package com.nicktj.autoledger.ui.screens.logs

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.data.entity.RecognitionLogEntity
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.RecognitionOutcome
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.AmountText
import com.nicktj.autoledger.ui.components.ConfidencePill
import com.nicktj.autoledger.ui.components.EmptyHint
import com.nicktj.autoledger.ui.components.Pill
import com.nicktj.autoledger.ui.components.RoundIconButton
import com.nicktj.autoledger.ui.components.SoftCard
import com.nicktj.autoledger.ui.components.appNameOf
import com.nicktj.autoledger.ui.components.copyToClipboard
import com.nicktj.autoledger.ui.components.dateTimeLabel
import com.nicktj.autoledger.ui.components.displayName
import com.nicktj.autoledger.ui.components.txnTypeOf
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 识别日志：按 outcome 过滤 + 每页 50 条 + 滚到底部继续加载。
 */
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selected = state.outcome?.let { name ->
        RecognitionOutcome.entries.firstOrNull { it.name == name }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(
                    icon = Icons.Outlined.KeyboardArrowLeft,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "识别日志",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = if (state.total > 0) "共 ${state.total} 条" else "暂时没有记录",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "all") {
                    OutcomeChip(
                        label = "全部",
                        selected = selected == null,
                        onClick = { viewModel.setOutcome(null) },
                    )
                }
                items(RecognitionOutcome.entries.toList(), key = { it.name }) { outcome ->
                    OutcomeChip(
                        label = outcome.displayName(),
                        selected = selected == outcome,
                        color = outcomeColor(outcome),
                        onClick = { viewModel.setOutcome(outcome) },
                    )
                }
            }
        }

        if (state.items.isEmpty() && state.initialized) {
            item(key = "empty") {
                SoftCard(contentPadding = PaddingValues(0.dp)) {
                    EmptyHint(
                        title = "没有匹配的日志",
                        description = "通知/无障碍抓到的原文都会记在这里，方便回查为什么没入账。",
                    )
                }
            }
        }

        items(state.items, key = { it.id }) { log ->
            LogCard(log = log, onCopy = { text -> copyToClipboard(context, "识别原文", text) })
        }

        item(key = "footer") {
            // 到底部自动加载下一页；加载完 items.size 变化会重新触发，直到 endReached。
            LaunchedEffect(state.items.size, state.endReached, state.loading) {
                if (!state.loading && !state.endReached && state.initialized) {
                    viewModel.loadMore()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else if (state.endReached && state.items.isNotEmpty()) {
                    Text(
                        text = "已经到底了 · 共 ${state.items.size} 条",
                        style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogCard(
    log: RecognitionLogEntity,
    onCopy: (String) -> Unit,
) {
    val outcome = RecognitionOutcome.entries.firstOrNull { it.name == log.outcome }
    val rawText = log.text ?: log.bigText ?: log.title.orEmpty()
    val note = log.note
    val parsedCents = log.parsedAmountCents
    val linkedTxnId = log.linkedTxnId
    val channelLabel = Channel.entries.firstOrNull { it.name == log.channel }?.displayName()
        ?: log.channel

    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 来源 App 的小头像：用名称首字，避免依赖各 App 的图标资源。
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = appNameOf(log.packageName).take(1),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appNameOf(log.packageName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = dateTimeLabel(log.receivedAt),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Pill(
                text = outcome?.displayName() ?: log.outcome,
                containerColor = outcomeColor(outcome).copy(alpha = 0.14f),
                contentColor = outcomeColor(outcome),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(
                text = channelLabel,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ConfidencePill(confidence = log.confidence)
            if (parsedCents != null && parsedCents > 0L) {
                val parsedType = log.parsedType?.let { txnTypeOf(it) }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    AmountText(
                        amountCents = parsedCents,
                        type = parsedType,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { if (rawText.isNotBlank()) onCopy(rawText) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = rawText.ifBlank { "（这条记录没有保留原始文本）" },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "复制原文",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            )
        }

        if (linkedTxnId != null) {
            Text(
                text = "已关联账单 #" + linkedTxnId + " · " + formatYuan(parsedCents ?: 0L),
                style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp),
            )
        }
    }
}

/** outcome 配色：成功绿、待确认黄、合并靛蓝、过滤灰、未解析橙、异常红。 */
@Composable
private fun outcomeColor(outcome: RecognitionOutcome?): Color {
    val extra = LocalExtra.current
    return when (outcome) {
        RecognitionOutcome.INSERTED -> extra.success
        RecognitionOutcome.PENDING -> extra.warning
        RecognitionOutcome.DUPLICATE_MERGED -> MaterialTheme.colorScheme.primary
        RecognitionOutcome.FILTERED_MARKETING -> MaterialTheme.colorScheme.onSurfaceVariant
        RecognitionOutcome.UNPARSED -> Color(0xFFEA580C)
        RecognitionOutcome.ERROR -> extra.danger
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
