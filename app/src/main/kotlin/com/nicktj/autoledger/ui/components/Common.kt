package com.nicktj.autoledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums
import java.time.YearMonth

// ---------------------------------------------------------------------------
// 金额
// ---------------------------------------------------------------------------

/**
 * 金额文本：统一等宽数字（[TabularNums]），可选带收支符号与类型配色。
 */
@Composable
fun AmountText(
    amountCents: Long,
    modifier: Modifier = Modifier,
    type: TxnType? = null,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color? = null,
    showSign: Boolean = false,
) {
    val resolvedColor = color ?: type?.let { amountColor(it) } ?: MaterialTheme.colorScheme.onSurface
    val prefix = if (showSign && type != null) type.signPrefix() else ""
    Text(
        text = prefix + formatYuan(amountCents),
        style = style.merge(TabularNums),
        color = resolvedColor,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// 月份切换
// ---------------------------------------------------------------------------

/** 左右箭头 + 月份胶囊。 */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoNext: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Outlined.KeyboardArrowLeft,
            contentDescription = "上个月",
            onClick = onPrevious,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Pill(
                text = monthLabel(month),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (canGoNext) {
            RoundIconButton(
                icon = Icons.Outlined.KeyboardArrowRight,
                contentDescription = "下个月",
                onClick = onNext,
            )
        } else {
            // 占位保持左右对称，避免「不能往后翻」时胶囊跳位。
            Spacer(modifier = Modifier.size(44.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 空态
// ---------------------------------------------------------------------------

/** 空态：Canvas 画的极简账单插画 + 文案。 */
@Composable
fun EmptyHint(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(width = 136.dp, height = 108.dp)) {
            val width = size.width
            val height = size.height
            val cardLeft = width * 0.2f
            val cardTop = height * 0.06f
            val cardWidth = width * 0.6f
            val cardHeight = height * 0.88f

            drawRoundRect(
                color = cardColor,
                topLeft = Offset(cardLeft, cardTop),
                size = Size(cardWidth, cardHeight),
                cornerRadius = CornerRadius(18f, 18f),
            )
            val lineStroke = Stroke(width = 3f)
            listOf(0.28f, 0.44f, 0.6f).forEach { fraction ->
                val y = cardTop + cardHeight * fraction
                drawLine(
                    color = lineColor,
                    start = Offset(cardLeft + cardWidth * 0.16f, y),
                    end = Offset(cardLeft + cardWidth * 0.84f, y),
                    strokeWidth = lineStroke.width,
                )
            }
            drawCircle(
                color = accentColor,
                radius = cardWidth * 0.13f,
                center = Offset(cardLeft + cardWidth * 0.5f, cardTop + cardHeight * 0.8f),
            )
            drawCircle(
                color = cardColor,
                radius = cardWidth * 0.05f,
                center = Offset(cardLeft + cardWidth * 0.5f, cardTop + cardHeight * 0.8f),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 进度条
// ---------------------------------------------------------------------------

/** 预算使用率进度条，比例超过 1 时只画满。 */
@Composable
fun UsageBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val animated by animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "usageBar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

// ---------------------------------------------------------------------------
// 设置行
// ---------------------------------------------------------------------------

/** 设置页的一行：图标 + 标题/说明 + 右侧控件。 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(
                icon = icon,
                size = 38.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** 卡片内分隔线，左右留白。 */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 2.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ---------------------------------------------------------------------------
// 日期选择
// ---------------------------------------------------------------------------

/** 日期行：点一下弹出 Material3 日期选择器，保留原来的时分。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerRow(
    occurredAt: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "日期",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = dayTitle(dayOf(occurredAt)) + " " + weekdayLabel(dayOf(occurredAt)) +
                    " " + timeLabel(occurredAt),
                style = MaterialTheme.typography.titleSmall.merge(TabularNums),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            text = "修改",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (showDialog) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            onDateSelected(applyPickedDate(picked, occurredAt))
                        }
                        showDialog = false
                    },
                ) { Text(text = "确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(text = "取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ---------------------------------------------------------------------------
// 置信度
// ---------------------------------------------------------------------------

/** 置信度胶囊：高绿 / 中黄 / 低红。 */
@Composable
fun ConfidencePill(confidence: Double, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    val (container, content) = when {
        confidence >= 0.8 -> extra.success.copy(alpha = 0.14f) to extra.success
        confidence >= 0.5 -> extra.warning.copy(alpha = 0.16f) to extra.warning
        else -> extra.danger.copy(alpha = 0.14f) to extra.danger
    }
    Pill(
        text = "置信度 ${(confidence * 100).toInt()}%",
        modifier = modifier,
        containerColor = container,
        contentColor = content,
    )
}

/** 支出/收入标签颜色，供 Pill 使用。 */
@Composable
fun typePillColors(type: TxnType): Pair<Color, Color> {
    val color = amountColor(type)
    return color.copy(alpha = 0.14f) to color
}
