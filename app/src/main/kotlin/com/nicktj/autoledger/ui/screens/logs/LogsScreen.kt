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
import com.nicktj.autoledger.ui.components.appNameOf
import com.nicktj.autoledger.ui.components.copyToClipboard
import com.nicktj.autoledger.ui.components.dateTimeLabel
import com.nicktj.autoledger.ui.components.displayName
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconButton
import com.nicktj.autoledger.ui.kit.KitTag
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 识别日志：按 outcome 过滤 + 每页 50 条 + 底部「加载更多」。
 *
 * 每张卡只回答三件事：什么时候、从哪个 App 的哪条通道、结果如何；
 * 原始文本单独一块等宽字展示，点一下复制，方便回查「为什么没入账」。
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
                    icon = Icons.Outlined.KeyboardArrowLeft,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(modifier = Modifier.width(12.dp))
                KitHeader(
                    title = "识别日志",
                    subtitle = if (state.total > 0) "共 ${state.total} 条" else "暂无记录",
                    modifier = Modifier.weight(1f),
                )
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
                        onClick = { viewModel.setOutcome(outcome) },
                    )
                }
            }
        }

        if (state.initialized && state.items.isEmpty()) {
            item(key = "empty") {
                KitCard {
                    Text(
                        text = if (state.total == 0) {
                            "还没有识别日志，抓到通知后会出现在这里。"
                        } else {
                            "没有符合当前筛选条件的日志。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(state.items, key = { it.id }) { log ->
            LogCard(
                log = log,
                onCopy = { text -> copyToClipboard(context, "识别原文", text) },
            )
        }

        item(key = "footer") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading && state.items.isNotEmpty() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }

                    state.items.isEmpty() -> Unit

                    !state.endReached -> {
                        LoadMoreButton(onClick = { viewModel.loadMore() })
                    }

                    else -> {
                        Text(
                            text = "已经到底了 · 共 ${state.total} 条",
                            style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 可点击胶囊筛选：选中态用主色。 */
@Composable
private fun OutcomeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 底部「加载更多」：分页仍由 ViewModel 的 loadMore/endReached 决定。 */
@Composable
private fun LoadMoreButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "加载更多",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LogCard(
    log: RecognitionLogEntity,
    onCopy: (String) -> Unit,
) {
    val outcome = RecognitionOutcome.entries.firstOrNull { it.name == log.outcome }
    val tagColor = outcomeColor(outcome)
    val rawText = log.text ?: log.bigText ?: log.title.orEmpty()
    val channelLabel = Channel.entries.firstOrNull { it.name == log.channel }?.displayName()
        ?: log.channel
    val parsedCents = log.parsedAmountCents
    val confidence = (log.confidence * 100).toInt()

    KitCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateTimeLabel(log.receivedAt),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                KitTag(
                    text = outcome?.displayName() ?: log.outcome,
                    color = tagColor,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appNameOf(log.packageName),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = channelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = buildString {
                    append("置信度 ")
                    append(confidence)
                    append('%')
                    if (parsedCents != null && parsedCents > 0L) {
                        append(" · 识别 ")
                        append(formatYuan(parsedCents))
                    }
                },
                style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { if (rawText.isNotBlank()) onCopy(rawText) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = rawText.ifBlank { "（这条记录没有保留原始文本）" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
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

            val note = log.note
            if (!note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val linkedTxnId = log.linkedTxnId
            if (linkedTxnId != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已关联账单 #$linkedTxnId",
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** outcome 配色：已入账=成功绿、待确认=警告黄、异常=危险红，其余为中性灰。 */
@Composable
private fun outcomeColor(outcome: RecognitionOutcome?): Color {
    val extra = LocalExtra.current
    return when (outcome) {
        RecognitionOutcome.INSERTED -> extra.success
        RecognitionOutcome.PENDING -> extra.warning
        RecognitionOutcome.DUPLICATE_MERGED -> MaterialTheme.colorScheme.onSurfaceVariant
        RecognitionOutcome.FILTERED_MARKETING -> MaterialTheme.colorScheme.onSurfaceVariant
        RecognitionOutcome.UNPARSED -> MaterialTheme.colorScheme.onSurfaceVariant
        RecognitionOutcome.ERROR -> extra.danger
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
