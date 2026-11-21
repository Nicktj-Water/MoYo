package com.nicktj.autoledger.ui.screens.bills

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.domain.BudgetLevel
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.MonthSwitcher
import com.nicktj.autoledger.ui.components.currentMonth
import com.nicktj.autoledger.ui.components.amountColor
import com.nicktj.autoledger.ui.components.signPrefix

import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitDivider
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconBadge
import com.nicktj.autoledger.ui.kit.KitIconButton
import com.nicktj.autoledger.ui.kit.KitRow
import com.nicktj.autoledger.ui.kit.KitTag
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums
import androidx.compose.foundation.lazy.LazyListScope

/**
 * 账单页：一张概览卡 + 按日分组的流水列表。
 *
 * 信息只保留三层：本月概况（概览卡）→ 日期分组 → 单笔账单。
 * 单击编辑，长按删除，右上角 + 手动记一笔。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillsScreen(
    modifier: Modifier = Modifier,
    initialEditTxnId: Long? = null,
    onTxnConsumed: () -> Unit = {},
    openManual: Boolean = false,
    onManualConsumed: () -> Unit = {},
    viewModel: BillsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val editTarget by viewModel.editTarget.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    var showManualEntry by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    // 通知点进来：直接打开对应账单的编辑弹层。
    LaunchedEffect(initialEditTxnId) {
        val id = initialEditTxnId ?: return@LaunchedEffect
        viewModel.openEditorById(id)
        onTxnConsumed()
    }

    // 小组件「记一笔」：直接弹出手动记账，并消费掉这次请求。
    LaunchedEffect(openManual) {
        if (openManual) {
            showManualEntry = true
            onManualConsumed()
        }
    }

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
            bottom = 120.dp, // 为底部导航留白
        ),
        verticalArrangement = Arrangement.spacedBy(Kit.Gap),
    ) {
        item(key = "header") {
            KitHeader(
                title = "账单",
                subtitle = monthTitle(state.month),
                action = {
                    KitIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "手动记一笔",
                        onClick = { showManualEntry = true },
                        filled = true,
                    )
                },
            )
        }

        item(key = "switcher") {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                canGoNext = state.month.isBefore(currentMonth()),
            )
        }

        item(key = "overview") { OverviewCard(state) }

        if (state.groups.isEmpty() && !state.loading) {
            item(key = "empty") { EmptyState() }
        }

        items(state.groups, key = { it.date.toString() }) { group ->
            DayCard(
                title = group.title,
                weekday = group.weekday,
                spentCents = group.spentCents,
                rows = group.items,
                onOpen = { viewModel.openEditor(it) },
                onDelete = { pendingDelete = it },
            )
        }
    }

    if (showManualEntry) {
        com.nicktj.autoledger.ui.screens.bills.ManualEntrySheet(
            categories = state.categories,
            onDismiss = { showManualEntry = false },
            onSubmit = { draft ->
                viewModel.saveManual(draft)
                showManualEntry = false
            },
        )
    }

    editTarget?.let { target ->
        com.nicktj.autoledger.ui.screens.bills.BillEditorSheet(
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
            title = { Text("删除这笔账单？") },
            text = {
                Text(
                    (target.merchant?.takeIf { it.isNotBlank() } ?: "未命名商户") +
                        " · " + formatYuan(target.amountCents) + "\n删除后无法恢复。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/* ---------------------------------------------------------------- 概览卡 */

@Composable
private fun OverviewCard(state: BillsUiState) {
    val extra = LocalExtra.current
    val shape = RoundedCornerShape(Kit.CardRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(extra.heroBrush)
            .padding(Kit.CardPadding + 6.dp),
    ) {
        Column {
            Text(
                text = "本月支出",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatYuan(state.monthSpentCents),
                style = MaterialTheme.typography.displaySmall.merge(TabularNums),
                color = Color.White,
            )
            Spacer(Modifier.height(16.dp))
            if (state.hasBudget) {
                BudgetBar(ratio = state.budgetRatio.toFloat(), level = state.budgetLevel)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "剩余 ${formatYuan(state.budgetRemainingCents)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(state.budgetRatio * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            } else {
                Text(
                    text = "还没设置月度预算，去「设置 → 预算」添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun BudgetBar(ratio: Float, level: BudgetLevel) {
    val fill = when (level) {
        BudgetLevel.NORMAL -> Color.White
        BudgetLevel.WARNING -> Color(0xFFFBBF24)
        BudgetLevel.OVER -> Color(0xFFFF6B6F)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(8.dp)
                .background(fill),
        )
    }
}

/* ---------------------------------------------------------------- 日分组 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCard(
    title: String,
    weekday: String,
    spentCents: Long,
    rows: List<BillRow>,
    onOpen: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
) {
    KitCard(padding = 0.dp) {
        Column {
            // 分组头：日期 + 当日支出
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$title  $weekday",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (spentCents > 0) {
                    Text(
                        text = "支出 ${formatYuan(spentCents)}",
                        style = MaterialTheme.typography.labelLarge.merge(TabularNums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    KitDivider()
                }
                BillLine(row = row, onOpen = { onOpen(row.txn) }, onDelete = { onDelete(row.txn) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BillLine(row: BillRow, onOpen: () -> Unit, onDelete: () -> Unit) {
    val type = row.txn.txnType()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KitIconBadge(icon = row.categoryIcon, tint = MaterialTheme.colorScheme.primary, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = (if (type.signPrefix().isNotEmpty()) type.signPrefix() else "") + formatYuan(row.txn.amountCents),
            style = MaterialTheme.typography.bodyLarge.merge(TabularNums),
            fontWeight = FontWeight.SemiBold,
            color = amountColor(type),
        )
    }
}

/* ---------------------------------------------------------------- 空态 */

@Composable
private fun EmptyState() {
    KitCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "还没有账单",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "点右上角 + 手动记一笔，或等支付通知自动入账。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun monthTitle(month: java.time.YearMonth): String = "${month.year} 年 ${month.monthValue} 月"
