package com.nicktj.autoledger.ui.screens.bills

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.domain.BudgetLevel
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.AmountText
import com.nicktj.autoledger.ui.components.EmptyHint
import com.nicktj.autoledger.ui.components.IconTile
import com.nicktj.autoledger.ui.components.MonthSwitcher
import com.nicktj.autoledger.ui.components.PageHeader
import com.nicktj.autoledger.ui.components.Pill
import com.nicktj.autoledger.ui.components.RoundIconButton
import com.nicktj.autoledger.ui.components.SoftCard
import com.nicktj.autoledger.ui.components.categoryTileColors
import com.nicktj.autoledger.ui.components.currentMonth
import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 账单列表：本月支出抬头 + 按月切换 + 按日期分组。
 *
 * 单击一行打开编辑弹层，长按直接确认删除；点日期行可折叠这一天的明细。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillsScreen(
    modifier: Modifier = Modifier,
    initialEditTxnId: Long? = null,
    onTxnConsumed: () -> Unit = {},
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val editTarget by viewModel.editTarget.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    // 从通知点进来时直接打开对应账单的编辑弹层。
    LaunchedEffect(initialEditTxnId) {
        val id = initialEditTxnId ?: return@LaunchedEffect
        viewModel.openEditorById(id)
        onTxnConsumed()
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PageHeader(
                title = "账单",
                subtitle = "本月 ${state.txnCount} 笔",
                actions = {
                    RoundIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "手动记一笔",
                        onClick = { showManualEntry = true },
                    )
                },
            )
        }

        item(key = "hero") { MonthHero(state = state) }

        item(key = "month-switcher") {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                canGoNext = state.month.isBefore(currentMonth()),
            )
        }

        if (state.groups.isEmpty() && !state.loading) {
            item(key = "empty") {
                SoftCard(contentPadding = PaddingValues(0.dp)) {
                    EmptyHint(
                        title = "还没有账单",
                        description = "点右上角 + 手动记一笔，或者等通知自动入账。",
                    )
                }
            }
        }

        items(state.groups, key = { it.date.toString() }) { group ->
            DayGroupCard(
                group = group,
                onRowClick = { viewModel.openEditor(it) },
                onRowLongClick = { pendingDelete = it },
                modifier = Modifier.animateItem(
                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ),
            )
        }
    }

    if (showManualEntry) {
        ManualEntrySheet(
            categories = state.categories,
            onDismiss = { showManualEntry = false },
            onSubmit = { draft ->
                viewModel.saveManual(draft)
                showManualEntry = false
            },
        )
    }

    editTarget?.let { target ->
        BillEditorSheet(
            txn = target,
            categories = state.categories,
            onDismiss = viewModel::closeEditor,
            onSave = { updated, learn -> viewModel.save(updated, learn) },
            onDelete = { viewModel.delete(it) },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = "删除这笔账单？") },
            text = {
                Text(
                    text = (target.merchant?.takeIf { it.isNotBlank() } ?: "未命名商户") +
                        " · " + formatYuan(target.amountCents) + "\n删除后无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        pendingDelete = null
                    },
                ) { Text(text = "删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(text = "取消") }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 顶部渐变抬头
// ---------------------------------------------------------------------------

@Composable
private fun MonthHero(state: BillsUiState) {
    val extra = LocalExtra.current
    val ratioPercent = (state.budgetRatio * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(extra.heroBrush)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column {
            Text(
                text = "本月支出",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
            AnimatedContent(
                targetState = state.monthSpentCents,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically { -it / 3 })
                },
                label = "monthSpent",
            ) { cents ->
                Text(
                    text = formatYuan(cents),
                    style = MaterialTheme.typography.displaySmall.merge(TabularNums),
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.hasBudget) {
                        "预算剩 ${formatYuan(state.budgetRemainingCents)} · 已用 $ratioPercent%"
                    } else {
                        "还没设置月度预算"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.weight(1f),
                )
                if (state.hasBudget) {
                    val levelColor = budgetLevelColor(state.budgetLevel)
                    Pill(
                        text = when (state.budgetLevel) {
                            BudgetLevel.NORMAL -> "正常"
                            BudgetLevel.WARNING -> "接近超支"
                            BudgetLevel.OVER -> "已超支"
                        },
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = if (state.budgetLevel == BudgetLevel.NORMAL) {
                            Color.White
                        } else {
                            levelColor
                        },
                    )
                }
            }
        }
    }
}

/** 预算等级配色：正常用主题色，80% 起黄色，超支红色。 */
@Composable
fun budgetLevelColor(level: BudgetLevel): Color {
    val extra = LocalExtra.current
    return when (level) {
        BudgetLevel.NORMAL -> extra.success
        BudgetLevel.WARNING -> extra.warning
        BudgetLevel.OVER -> extra.danger
    }
}

// ---------------------------------------------------------------------------
// 日期分组卡片
// ---------------------------------------------------------------------------

@Composable
private fun DayGroupCard(
    group: DayGroup,
    onRowClick: (TransactionEntity) -> Unit,
    onRowLongClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var collapsed by rememberSaveable(group.date.toString()) { mutableStateOf(false) }

    SoftCard(
        modifier = modifier.animateContentSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { collapsed = !collapsed }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${group.weekday} · ${group.items.size} 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = "支出 " + formatYuan(group.spentCents),
                style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (collapsed) {
                    Icons.Outlined.KeyboardArrowDown
                } else {
                    Icons.Outlined.KeyboardArrowUp
                },
                contentDescription = if (collapsed) "展开" else "收起",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        if (!collapsed) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            group.items.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                BillRowItem(
                    row = row,
                    onClick = { onRowClick(row.txn) },
                    onLongClick = { onRowLongClick(row.txn) },
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BillRowItem(
    row: BillRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val type = row.txn.txnType()
    val (tileBg, tileFg) = categoryTileColors(row.txn.categoryId)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = row.categoryIcon,
            size = 42.dp,
            containerColor = tileBg,
            contentColor = tileFg,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                amountCents = row.txn.amountCents,
                type = type,
                style = MaterialTheme.typography.titleSmall,
                showSign = type != TxnType.EXPENSE,
            )
            if (row.txn.refundedCents > 0L) {
                Text(
                    text = "已退 " + formatYuan(row.txn.refundedCents),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalExtra.current.success,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
