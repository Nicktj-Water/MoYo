package com.nicktj.autoledger.ui.screens.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.model.CategoryKind
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.DatePickerRow
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.centsToPlainText
import com.nicktj.autoledger.ui.components.displayName
import com.nicktj.autoledger.ui.components.parseAmountToCents
import com.nicktj.autoledger.ui.components.typePillColors
import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 账单编辑弹层：改金额 / 分类 / 商户 / 备注 / 日期，可删除。
 *
 * 改分类后由调用方决定是否「学习」成商户规则（见 `learnCategory` 参数）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditorSheet(
    txn: TransactionEntity,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (updated: TransactionEntity, learnCategory: Boolean) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
    confirmOnSave: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val type = txn.txnType()

    var amountText by remember(txn.id) { mutableStateOf(centsToPlainText(txn.amountCents)) }
    var merchantText by remember(txn.id) { mutableStateOf(txn.merchant.orEmpty()) }
    var noteText by remember(txn.id) { mutableStateOf(txn.note.orEmpty()) }
    var categoryId by remember(txn.id) { mutableStateOf(txn.categoryId) }
    var occurredAt by remember(txn.id) { mutableStateOf(txn.occurredAt) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val categoryOptions = remember(categories, type) { categoriesFor(categories, type) }
    val parsedCents = parseAmountToCents(amountText)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "编辑账单",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (confirmOnSave) "改完保存后会直接入账" else "修改后立即生效",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val (pillBg, pillFg) = typePillColors(type)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = type.displayName(),
                        style = MaterialTheme.typography.labelMedium,
                        color = pillFg,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.length <= 12 && input.all { it.isDigit() || it == '.' || it == ',' }) {
                        amountText = input
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "金额") },
                prefix = { Text(text = "¥") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.merge(TabularNums),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                isError = amountText.isNotEmpty() && parsedCents == null,
                supportingText = if (amountText.isNotEmpty() && parsedCents == null) {
                    { Text(text = "请输入大于 0 的金额") }
                } else {
                    null
                },
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "商户") },
                placeholder = { Text(text = "例如：星巴克") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "分类",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            CategoryChipRow(
                categories = categoryOptions,
                selectedId = categoryId,
                onSelect = { categoryId = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            DatePickerRow(occurredAt = occurredAt, onDateSelected = { occurredAt = it })

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "备注") },
                placeholder = { Text(text = "可选") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "删除")
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val cents = parsedCents ?: return@Button
                        val targetStatus = if (confirmOnSave) {
                            TxnStatus.CONFIRMED.name
                        } else {
                            txn.status
                        }
                        onSave(
                            txn.copy(
                                amountCents = cents,
                                categoryId = categoryId,
                                merchant = merchantText.trim().ifEmpty { null },
                                note = noteText.trim().ifEmpty { null },
                                occurredAt = occurredAt,
                                status = targetStatus,
                            ),
                            categoryId != null && categoryId != txn.categoryId,
                        )
                    },
                    enabled = parsedCents != null,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(text = if (confirmOnSave) "保存并确认" else "保存")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(text = "删除这笔账单？") },
            text = {
                Text(
                    text = listOfNotNull(
                        txn.merchant?.takeIf { it.isNotBlank() } ?: type.displayName(),
                        formatYuan(txn.amountCents),
                    ).joinToString(" · ") + "\n删除后无法恢复。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(txn)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(text = "删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(text = "取消") }
            },
        )
    }
}

/** 根据账单类型筛出可用分类。 */
fun categoriesFor(categories: List<CategoryEntity>, type: TxnType): List<CategoryEntity> =
    categories.filter { category ->
        val kind = runCatching { CategoryKind.valueOf(category.kind) }.getOrNull()
        when (type) {
            TxnType.EXPENSE -> kind == CategoryKind.EXPENSE || kind == CategoryKind.BOTH
            TxnType.INCOME -> kind == CategoryKind.INCOME || kind == CategoryKind.BOTH
            TxnType.REFUND -> kind == CategoryKind.INCOME || kind == CategoryKind.BOTH
            TxnType.RED_PACKET_IN, TxnType.RED_PACKET_OUT -> kind == CategoryKind.BOTH
        }
    }

/** 横向分类选择器。 */
@Composable
fun CategoryChipRow(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            val shape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .then(
                        if (selected) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(category.id) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = categoryIconFor(category.icon, category.name),
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
