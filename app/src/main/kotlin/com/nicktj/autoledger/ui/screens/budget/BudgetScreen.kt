package com.nicktj.autoledger.ui.screens.budget

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.IconTile
import com.nicktj.autoledger.ui.components.PageHeader
import com.nicktj.autoledger.ui.components.SectionHeader
import com.nicktj.autoledger.ui.components.SoftCard
import com.nicktj.autoledger.ui.components.UsageBar
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.categoryTileColors
import com.nicktj.autoledger.ui.components.centsToPlainText
import com.nicktj.autoledger.ui.components.parseAmountToCents
import com.nicktj.autoledger.ui.screens.bills.budgetLevelColor
import com.nicktj.autoledger.ui.theme.TabularNums

/** 编辑中的分类预算行（金额保持字符串，避免输入过程被规范化）。 */
private data class BudgetDraftRow(
    val categoryId: Long,
    val amountText: String,
)

/**
 * 预算设置：月总预算 + 分类预算，保存后整体重写 BudgetEntity。
 *
 * 目前只编辑「默认每月」那一组（yearMonth = null），对每个月生效。
 */
@Composable
fun BudgetScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var totalText by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<BudgetDraftRow>() }
    var initialized by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

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

    val spentByCategory = remember(state.rows) { state.rows.associate { it.categoryId to it.spentCents } }
    val levelColor = budgetLevelColor(state.level)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PageHeader(
                title = "预算",
                subtitle = "${state.monthName} · 每月通用",
            )
        }

        item(key = "total") {
            SoftCard {
                SectionHeader(title = "月总预算")
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { input ->
                        if (input.length <= 10 && input.all { it.isDigit() || it == '.' }) {
                            totalText = input
                        }
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
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "本月已用 " + (state.ratio * 100).toInt() + "%",
                        style = MaterialTheme.typography.titleSmall.merge(TabularNums),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            state.totalBudgetCents <= 0L -> "未设置"
                            state.remainingCents >= 0L -> "剩 " + formatYuan(state.remainingCents)
                            else -> "超 " + formatYuan(-state.remainingCents)
                        },
                        style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                        color = if (state.remainingCents >= 0L) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            levelColor
                        },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                UsageBar(ratio = state.ratio, color = levelColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "已花 " + formatYuan(state.totalSpentCents),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "category-header") {
            SectionHeader(
                title = "分类预算",
                action = {
                    TextButton(onClick = { showCategoryPicker = !showCategoryPicker }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "添加")
                    }
                },
            )
        }

        if (showCategoryPicker) {
            item(key = "category-picker") {
                val used = drafts.map { it.categoryId }.toSet()
                val available = state.categories.filter { it.id !in used }
                SoftCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                    if (available.isEmpty()) {
                        Text(
                            text = "所有分类都加过预算了",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(available, key = { it.id }) { category ->
                                CategoryPickChip(category = category) {
                                    drafts.add(BudgetDraftRow(category.id, ""))
                                    showCategoryPicker = false
                                }
                            }
                        }
                    }
                }
            }
        }

        if (drafts.isEmpty()) {
            item(key = "category-empty") {
                SoftCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)) {
                    Text(
                        text = "还没有分类预算。给「餐饮」「购物」这类常花项单独设个上限，超了会在账单页提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        items(drafts, key = { it.categoryId }) { draft ->
            val category = state.categories.firstOrNull { it.id == draft.categoryId }
            CategoryBudgetEditor(
                draft = draft,
                category = category,
                spentCents = spentByCategory[draft.categoryId] ?: 0L,
                onAmountChange = { newText ->
                    val index = drafts.indexOfFirst { it.categoryId == draft.categoryId }
                    if (index >= 0) {
                        drafts[index] = draft.copy(amountText = newText)
                    }
                },
                onRemove = {
                    drafts.removeAll { it.categoryId == draft.categoryId }
                },
            )
        }

        item(key = "save") {
            Button(
                onClick = {
                    val total = parseAmountToCents(totalText) ?: 0L
                    val categoryBudgets = drafts
                        .mapNotNull { draft ->
                            parseAmountToCents(draft.amountText)?.let { draft.categoryId to it }
                        }
                        .toMap()
                    viewModel.save(total, categoryBudgets)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = "保存预算")
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "只改预算不会影响已有账单；预算按 Asia/Shanghai 的月份切分。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun CategoryBudgetEditor(
    draft: BudgetDraftRow,
    category: CategoryEntity?,
    spentCents: Long,
    onAmountChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val (tileBg, tileFg) = categoryTileColors(draft.categoryId)
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = categoryIconFor(category?.icon, category?.name),
                size = 38.dp,
                containerColor = tileBg,
                contentColor = tileFg,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category?.name ?: "未知分类",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "本月已花 " + formatYuan(spentCents),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = { input ->
                    if (input.length <= 10 && input.all { it.isDigit() || it == '.' }) {
                        onAmountChange(input)
                    }
                },
                modifier = Modifier.width(118.dp),
                placeholder = { Text(text = "预算") },
                prefix = { Text(text = "¥") },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.merge(TabularNums),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除这个分类预算",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        val budget = parseAmountToCents(draft.amountText) ?: 0L
        if (budget > 0L) {
            val ratio = (spentCents.toDouble() / budget.toDouble()).toFloat()
            Spacer(modifier = Modifier.height(10.dp))
            UsageBar(
                ratio = ratio,
                color = budgetLevelColor(
                    when {
                        ratio >= 1f -> com.nicktj.autoledger.core.domain.BudgetLevel.OVER
                        ratio >= 0.8f -> com.nicktj.autoledger.core.domain.BudgetLevel.WARNING
                        else -> com.nicktj.autoledger.core.domain.BudgetLevel.NORMAL
                    },
                ),
                height = 6.dp,
            )
        }
    }
}

@Composable
private fun CategoryPickChip(category: CategoryEntity, onClick: () -> Unit) {
    val (tileBg, tileFg) = categoryTileColors(category.id)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = categoryIconFor(category.icon, category.name),
            contentDescription = null,
            tint = tileFg,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
