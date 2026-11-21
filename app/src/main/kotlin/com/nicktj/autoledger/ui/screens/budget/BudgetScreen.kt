package com.nicktj.autoledger.ui.screens.budget

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.domain.BudgetLevel
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.UsageBar
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.categoryTileColors
import com.nicktj.autoledger.ui.components.centsToPlainText
import com.nicktj.autoledger.ui.components.parseAmountToCents
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitDivider
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconBadge
import com.nicktj.autoledger.ui.kit.KitIconButton
import com.nicktj.autoledger.ui.kit.KitRow
import com.nicktj.autoledger.ui.kit.KitSection
import com.nicktj.autoledger.ui.kit.budgetLevelColor
import com.nicktj.autoledger.ui.theme.TabularNums

/** 编辑中的分类预算行（金额保持字符串，避免输入过程被规范化）。 */
private data class BudgetDraftRow(
    val categoryId: Long,
    val amountText: String,
)

/** 金额输入限制：最多 10 位，只允许数字和小数点。 */
private fun isAmountInput(input: String): Boolean =
    input.length <= 10 && input.all { it.isDigit() || it == '.' }

/** 分类预算的配色等级（与全应用一致：80% 警告，100% 超支）。 */
private fun levelOfRatio(ratio: Float): BudgetLevel = when {
    ratio >= 1f -> BudgetLevel.OVER
    ratio >= 0.8f -> BudgetLevel.WARNING
    else -> BudgetLevel.NORMAL
}

/**
 * 预算页：月总预算概览 + 分类预算。
 *
 * 总预算与各分类预算先写在本地草稿里，点「保存预算」后由 ViewModel 整体重写
 * BudgetEntity（yearMonth = null，对所有月份生效）。取消对话框不会改动草稿。
 */
@Composable
fun BudgetScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    var totalText by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<BudgetDraftRow>() }
    var initialized by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showTotalEditor by remember { mutableStateOf(false) }
    var editingCategoryId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.loading) {
        if (!state.loading && !initialized) {
            totalText = centsToPlainText(state.totalBudgetCents)
            drafts.clear()
            state.rows.forEach { row ->
                drafts.add(BudgetDraftRow(row.categoryId, centsToPlainText(row.budgetCents)))
            }
            initialized = true
        }
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    val spentByCategory = remember(state.rows) {
        state.rows.associate { it.categoryId to it.spentCents }
    }
    val totalBudgetCents = parseAmountToCents(totalText) ?: 0L
    val remainingCents = totalBudgetCents - state.totalSpentCents
    val totalRatio = if (totalBudgetCents > 0L) {
        (state.totalSpentCents.toDouble() / totalBudgetCents.toDouble()).toFloat()
    } else {
        0f
    }
    val levelColor = budgetLevelColor(state.level)

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
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(Modifier.width(12.dp))
                KitHeader(
                    title = "预算",
                    subtitle = state.monthName,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "total") {
            TotalBudgetCard(
                budgetCents = totalBudgetCents,
                spentCents = state.totalSpentCents,
                remainingCents = remainingCents,
                ratio = totalRatio,
                levelColor = levelColor,
                onEdit = { showTotalEditor = true },
            )
        }

        item(key = "category-header") {
            Column {
                Spacer(Modifier.height(Kit.Gap))
                KitSection(
                    title = "分类预算",
                    count = drafts.size,
                    trailing = {
                        KitIconButton(
                            icon = Icons.Outlined.Add,
                            contentDescription = "添加分类预算",
                            onClick = { showCategoryPicker = !showCategoryPicker },
                            filled = true,
                        )
                    },
                )
            }
        }

        if (showCategoryPicker) {
            item(key = "category-picker") {
                val used = drafts.map { it.categoryId }.toSet()
                val available = state.categories.filter { it.id !in used }
                KitCard {
                    Column {
                        if (available.isEmpty()) {
                            Text(
                                text = "所有分类都加过预算了",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "选择一个分类",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(available, key = { it.id }) { category ->
                                    CategoryPickChip(category = category) {
                                        drafts.add(BudgetDraftRow(category.id, ""))
                                        showCategoryPicker = false
                                        editingCategoryId = category.id
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (drafts.isEmpty()) {
            item(key = "category-empty") {
                KitCard {
                    Column {
                        Text(
                            text = "还没有分类预算",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "给「餐饮」「购物」这类常花项单独设个上限，超了会在账单页提醒。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item(key = "category-list") {
                KitCard {
                    Column {
                        drafts.forEachIndexed { index, draft ->
                            if (index > 0) {
                                KitDivider()
                            }
                            val category = state.categories.firstOrNull { it.id == draft.categoryId }
                            CategoryBudgetLine(
                                category = category,
                                budgetCents = parseAmountToCents(draft.amountText) ?: 0L,
                                spentCents = spentByCategory[draft.categoryId] ?: 0L,
                                onClick = { editingCategoryId = draft.categoryId },
                            )
                        }
                    }
                }
            }
        }

        item(key = "save") {
            Column {
                Spacer(Modifier.height(Kit.Gap))
                Button(
                    onClick = {
                        val categoryBudgets = drafts
                            .mapNotNull { draft ->
                                parseAmountToCents(draft.amountText)?.let { draft.categoryId to it }
                            }
                            .toMap()
                        viewModel.save(totalBudgetCents, categoryBudgets)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(text = "保存预算")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "只改预算不会影响已有账单；预算按 Asia/Shanghai 的月份切分。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }

    if (showTotalEditor) {
        TotalBudgetDialog(
            initialText = totalText,
            onDismiss = { showTotalEditor = false },
            onConfirm = { newText ->
                totalText = newText
                showTotalEditor = false
            },
        )
    }

    val editingDraft = drafts.firstOrNull { it.categoryId == editingCategoryId }
    if (editingDraft != null) {
        val category = state.categories.firstOrNull { it.id == editingDraft.categoryId }
        CategoryBudgetDialog(
            title = category?.name ?: "分类预算",
            spentCents = spentByCategory[editingDraft.categoryId] ?: 0L,
            initialText = editingDraft.amountText,
            onDismiss = {
                // 新加的分类如果什么都没填就取消，直接把这条空行去掉。
                val savedBudget = state.rows.firstOrNull { it.categoryId == editingDraft.categoryId }?.budgetCents ?: 0L
                if (savedBudget <= 0L && editingDraft.amountText.isBlank()) {
                    drafts.removeAll { it.categoryId == editingDraft.categoryId }
                }
                editingCategoryId = null
            },
            onConfirm = { newText ->
                val index = drafts.indexOfFirst { it.categoryId == editingDraft.categoryId }
                if (index >= 0) {
                    drafts[index] = editingDraft.copy(amountText = newText)
                }
                editingCategoryId = null
            },
            onRemove = {
                drafts.removeAll { it.categoryId == editingDraft.categoryId }
                editingCategoryId = null
            },
        )
    }
}

/* ------------------------------------------------------------ 月总预算卡 */

@Composable
private fun TotalBudgetCard(
    budgetCents: Long,
    spentCents: Long,
    remainingCents: Long,
    ratio: Float,
    levelColor: Color,
    onEdit: () -> Unit,
) {
    KitCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "月总预算",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) {
                    Text(text = if (budgetCents > 0L) "调整" else "设置")
                }
            }
            Text(
                text = if (budgetCents > 0L) formatYuan(budgetCents) else "未设置",
                style = MaterialTheme.typography.headlineMedium.merge(TabularNums),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            Row {
                TotalStat(
                    label = "已花",
                    value = formatYuan(spentCents),
                    modifier = Modifier.weight(1f),
                )
                TotalStat(
                    label = "剩余",
                    value = if (budgetCents > 0L) formatYuan(remainingCents) else "—",
                    modifier = Modifier.weight(1f),
                    valueColor = if (remainingCents < 0L) MaterialTheme.colorScheme.error else null,
                )
                TotalStat(
                    label = "使用率",
                    value = if (budgetCents > 0L) "${(ratio * 100).toInt()}%" else "—",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            UsageBar(ratio = ratio, color = levelColor, height = 8.dp)

            if (budgetCents <= 0L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "还没设置月总预算。设一个每月上限，超支时会在这里提醒。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (remainingCents < 0L) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "已超出预算 " + formatYuan(-remainingCents),
                    style = MaterialTheme.typography.bodySmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TotalStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.merge(TabularNums),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/* ------------------------------------------------------------ 分类预算行 */

@Composable
private fun CategoryBudgetLine(
    category: CategoryEntity?,
    budgetCents: Long,
    spentCents: Long,
    onClick: () -> Unit,
) {
    val name = category?.name ?: "未知分类"
    val tint = categoryTileColors(category?.id).second
    val ratio = if (budgetCents > 0L) {
        (spentCents.toDouble() / budgetCents.toDouble()).toFloat()
    } else {
        0f
    }

    KitRow(
        title = name,
        leading = {
            KitIconBadge(
                icon = categoryIconFor(category?.icon, name),
                tint = tint,
                size = 40.dp,
            )
        },
        trailing = {
            Column(
                modifier = Modifier.width(136.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "已用 " + formatYuan(spentCents),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "预算 " + formatYuan(budgetCents),
                    style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                UsageBar(
                    ratio = ratio,
                    color = budgetLevelColor(levelOfRatio(ratio)),
                    height = 8.dp,
                )
            }
        },
        onClick = onClick,
    )
}

/* -------------------------------------------------------------- 编辑弹层 */

@Composable
private fun TotalBudgetDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "月总预算") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        if (isAmountInput(input)) value = input
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "每月预算") },
                    prefix = { Text(text = "¥") },
                    placeholder = { Text(text = "0.00") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "留空或填 0 表示不设总预算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(text = "确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
    )
}

@Composable
private fun CategoryBudgetDialog(
    title: String,
    spentCents: Long,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var value by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                Text(
                    text = "本月已花 " + formatYuan(spentCents),
                    style = MaterialTheme.typography.bodySmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        if (isAmountInput(input)) value = input
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "预算") },
                    prefix = { Text(text = "¥") },
                    placeholder = { Text(text = "0.00") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "填 0 或留空表示这个分类不设上限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(text = "确定")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRemove) {
                    Text(
                        text = "移除",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "取消")
                }
            }
        },
    )
}

/* ---------------------------------------------------------------- 选择器 */

@Composable
private fun CategoryPickChip(category: CategoryEntity, onClick: () -> Unit) {
    val tint = categoryTileColors(category.id).second
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = categoryIconFor(category.icon, category.name),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
