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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.ui.components.DatePickerRow
import com.nicktj.autoledger.ui.components.displayName
import com.nicktj.autoledger.ui.components.parseAmountToCents
import com.nicktj.autoledger.ui.theme.TabularNums
import java.time.Instant

/** 手动记账可选的方向；红包/退款也允许手动补录。 */
private val MANUAL_TYPES = listOf(
    TxnType.EXPENSE,
    TxnType.INCOME,
    TxnType.RED_PACKET_OUT,
    TxnType.RED_PACKET_IN,
    TxnType.REFUND,
)

/**
 * 手动记一笔：底部弹层。
 *
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
            Text(
                text = "记一笔",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "手动录入的账单会直接计入统计",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 类型切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
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
                                    MaterialTheme.colorScheme.surfaceContainerLowest
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                            )
                            .clickable {
                                type = candidate
                                categoryId = null
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = candidate.displayName(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center,
                        )
                    }
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
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "商户") },
                placeholder = { Text(text = "可选，例如：麦当劳") },
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
                categories = options,
                selectedId = categoryId,
                onSelect = { categoryId = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            DatePickerRow(
                occurredAt = occurredAt,
                onDateSelected = { occurredAt = it },
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "备注") },
                placeholder = { Text(text = "可选") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(text = "保存")
            }

            Text(
                text = "时间：" + Instant.ofEpochMilli(occurredAt)
                    .atZone(com.nicktj.autoledger.ui.components.ShanghaiZone)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
