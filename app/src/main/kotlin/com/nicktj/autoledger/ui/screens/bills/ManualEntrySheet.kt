package com.nicktj.autoledger.ui.screens.bills

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.ui.components.DatePickerRow
import com.nicktj.autoledger.ui.components.ShanghaiZone
import com.nicktj.autoledger.ui.components.centsToPlainText
import com.nicktj.autoledger.ui.components.displayName
import com.nicktj.autoledger.ui.components.parseAmountToCents
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitSection
import com.nicktj.autoledger.ui.theme.TabularNums
import java.time.Instant
import java.time.format.DateTimeFormatter

/** 手动记账可选的方向；红包/退款也允许手动补录。 */
private val MANUAL_TYPES = listOf(
    TxnType.EXPENSE,
    TxnType.INCOME,
    TxnType.RED_PACKET_OUT,
    TxnType.RED_PACKET_IN,
    TxnType.REFUND,
)

private val FIELD_SHAPE = RoundedCornerShape(14.dp)

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/**
 * 手动记一笔：底部弹层。
 *
 * 顶部支持「一句话记一笔」（例如「麦当劳 28」）先解析预填，下面仍保留完整表单。
 * 保存时由 ViewModel 固定 `channel = MANUAL`、`confidence = 1.0`、`status = CONFIRMED`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSubmit: (ManualDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var quickText by remember { mutableStateOf("") }
    var quickError by remember { mutableStateOf(false) }

    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var occurredAt by remember { mutableStateOf(System.currentTimeMillis()) }

    val options = remember(categories, type) { categoriesFor(categories, type) }
    val parsedCents = parseAmountToCents(amountText)

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
                .padding(
                    start = Kit.PageHorizontal,
                    end = Kit.PageHorizontal,
                    bottom = Kit.SectionGap,
                ),
        ) {
            KitHeader(title = "记一笔")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "手动录入的账单会直接计入统计",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // 一句话记一笔：解析成功就预填下面的表单
            KitCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = quickText,
                            onValueChange = { input ->
                                quickText = input.take(60)
                                quickError = false
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(text = "一句话记一笔，如：麦当劳 28") },
                            singleLine = true,
                            shape = FIELD_SHAPE,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val parsed = QuickInputParser.parse(quickText)
                                if (parsed == null) {
                                    quickError = true
                                } else {
                                    quickError = false
                                    amountText = centsToPlainText(parsed.amountCents)
                                    type = parsed.type
                                    parsed.merchant?.let { merchantText = it.take(40) }
                                    val hintName = parsed.categoryHint
                                    categoryId = if (hintName == null) {
                                        null
                                    } else {
                                        val hit = categoriesFor(categories, parsed.type)
                                            .firstOrNull { it.name == hintName }
                                            ?: categories.firstOrNull { it.name == hintName }
                                        hit?.id
                                    }
                                }
                            },
                            enabled = quickText.isNotBlank(),
                        ) {
                            Text(text = "解析")
                        }
                    }
                    if (quickError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "没认出金额，请用表单填写",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            KitSection(title = "明细")

            // 表单
            KitCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "类型",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FIELD_SHAPE)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MANUAL_TYPES.forEach { candidate ->
                            val selected = candidate == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .clickable {
                                        type = candidate
                                        categoryId = null
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = candidate.displayName(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

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
                        placeholder = { Text(text = "0.00") },
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
                        shape = FIELD_SHAPE,
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = merchantText,
                        onValueChange = { merchantText = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "商户") },
                        placeholder = { Text(text = "可选，例如：麦当劳") },
                        singleLine = true,
                        shape = FIELD_SHAPE,
                    )

                    Spacer(Modifier.height(16.dp))

                    KitSection(title = "分类")
                    CategoryChipRow(
                        categories = options,
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                    )

                    Spacer(Modifier.height(16.dp))

                    DatePickerRow(
                        occurredAt = occurredAt,
                        onDateSelected = { occurredAt = it },
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(text = "备注") },
                        placeholder = { Text(text = "可选") },
                        minLines = 2,
                        maxLines = 3,
                        shape = FIELD_SHAPE,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val cents = parsedCents ?: return@Button
                    onSubmit(
                        ManualDraft(
                            amountCents = cents,
                            type = type,
                            categoryId = categoryId,
                            merchant = merchantText.trim().ifEmpty { null },
                            note = noteText.trim().ifEmpty { null },
                            occurredAt = occurredAt,
                        ),
                    )
                },
                enabled = parsedCents != null,
                modifier = Modifier.fillMaxWidth(),
                shape = FIELD_SHAPE,
            ) {
                Text(text = "保存")
            }

            Text(
                text = "时间：" + Instant.ofEpochMilli(occurredAt)
                    .atZone(ShanghaiZone)
                    .format(TIME_FORMATTER),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
