package com.nicktj.autoledger.ui.screens.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.nicktj.autoledger.ui.components.amountColor
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.centsToPlainText
import com.nicktj.autoledger.ui.components.displayName
import com.nicktj.autoledger.ui.components.parseAmountToCents
import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitDivider
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconButton
import com.nicktj.autoledger.ui.kit.KitSection
import com.nicktj.autoledger.ui.kit.KitTag
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
    var rememberMerchant by remember(txn.id) { mutableStateOf(true) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val categoryOptions = remember(categories, type) { categoriesFor(categories, type) }
    val parsedCents = parseAmountToCents(amountText)
    val categoryChanged = categoryId != null && categoryId != txn.categoryId
    val learnCategory = rememberMerchant && categoryChanged

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = Kit.PageHorizontal, end = Kit.PageHorizontal, bottom = Kit.SectionGap),
        ) {
            KitHeader(
                title = "编辑账单",
                subtitle = type.displayName() + " · " +
                    (if (confirmOnSave) "保存后直接入账" else "修改后立即生效"),
                action = {
                    KitIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        onClick = onDismiss,
                    )
                },
            )

            Spacer(modifier = Modifier.height(Kit.SectionGap))

            // 金额（大号、等宽数字，可直接编辑）
            KitCard(padding = 20.dp) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "金额",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        KitTag(text = type.displayName(), color = amountColor(type))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { input ->
                            if (input.length <= 12 && input.all { it.isDigit() || it == '.' || it == ',' }) {
                                amountText = input
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        prefix = {
                            Text(
                                text = "¥",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        placeholder = { Text(text = "0.00") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displaySmall.merge(TabularNums),
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
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Kit.SectionGap))

            // 分类：横向胶囊，选中为主色
            KitSection(title = "分类")
            CategoryChipRow(
                categories = categoryOptions,
                selectedId = categoryId,
                onSelect = { categoryId = it },
            )

            Spacer(modifier = Modifier.height(Kit.SectionGap))

            // 其余字段：商户 / 备注 / 日期时间
            KitCard {
                Column {
                    OutlinedTextField(
                        value = merchantText,
                        onValueChange = { merchantText = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "商户") },
                        placeholder = { Text(text = "例如：星巴克") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Spacer(modifier = Modifier.height(Kit.Gap))
                    KitDivider()
                    Spacer(modifier = Modifier.height(Kit.Gap))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "备注") },
                        placeholder = { Text(text = "可选") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                    )
                    Spacer(modifier = Modifier.height(Kit.Gap))
                    KitDivider()
                    Spacer(modifier = Modifier.height(Kit.Gap))
                    DatePickerRow(
                        occurredAt = occurredAt,
                        onDateSelected = { occurredAt = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(Kit.Gap))

            // 商户规则学习开关
            KitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "记住这个商户的分类",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (merchantText.isBlank()) {
                                "填写商户并改分类后生效"
                            } else {
                                "下次识别到「${merchantText.trim()}」自动归到所选分类"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(Kit.Gap))
                    Switch(
                        checked = rememberMerchant,
                        onCheckedChange = { rememberMerchant = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(Kit.SectionGap))

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
                        learnCategory,
                    )
                },
                enabled = parsedCents != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = if (confirmOnSave) "确认入账" else "保存",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "删除")
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

/** 横向分类选择器：胶囊，选中为主色。 */
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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .clickable { onSelect(category.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = categoryIconFor(category.icon, category.name),
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
