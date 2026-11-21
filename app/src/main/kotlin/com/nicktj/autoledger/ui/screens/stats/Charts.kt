package com.nicktj.autoledger.ui.screens.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.compactYuanText
import com.nicktj.autoledger.ui.theme.TabularNums
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * 分类饼图（环形）。
 *
 * - 出现时用 [Animatable] 从 0 长到 1（tween 700ms）
 * - 点击扇区高亮对应分类，被选中的扇区半径略大
 * - 环心留白用卡片底色，所以必须放在 KitCard（surfaceContainerLowest）里
 */
@Composable
fun CategoryPieChart(
    slices: List<PieSlice>,
    totalCents: Long,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 208.dp,
) {
    val progress = remember { Animatable(0f) }
    val holeColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val emptyRingColor = MaterialTheme.colorScheme.surfaceContainerHigh

    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 700))
    }

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        if (slices.isEmpty()) return@detectTapGestures
                        val centerX = this.size.width / 2f
                        val centerY = this.size.height / 2f
                        val radius = min(this.size.width, this.size.height) / 2f
                        val dx = offset.x - centerX
                        val dy = offset.y - centerY
                        val distance = hypot(dx, dy)
                        if (distance > radius || distance < radius * 0.42f) {
                            return@detectTapGestures
                        }
                        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        if (angle < 0f) angle += 360f
                        // 饼图从 12 点开始，顺时针画。
                        val relative = (angle + 90f + 360f) % 360f
                        var accumulated = 0f
                        slices.forEachIndexed { index, slice ->
                            val sweep = slice.fraction * 360f
                            if (relative >= accumulated && relative < accumulated + sweep) {
                                onSelect(if (selectedIndex == index) null else index)
                                return@detectTapGestures
                            }
                            accumulated += sweep
                        }
                    }
                },
        ) {
            val canvasRadius = min(size.width, size.height) / 2f - 10.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            if (slices.isEmpty()) {
                drawCircle(color = emptyRingColor, radius = canvasRadius, center = center)
            } else {
                var startAngle = -90f
                val animated = progress.value

                slices.forEachIndexed { index, slice ->
                    val sweep = slice.fraction * 360f * animated
                    if (sweep > 0.05f) {
                        val gap = if (sweep > 4f) 1.2f else 0f
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle + gap / 2f,
                            sweepAngle = (sweep - gap).coerceAtLeast(0.1f),
                            useCenter = true,
                            topLeft = Offset(center.x - canvasRadius, center.y - canvasRadius),
                            size = Size(canvasRadius * 2f, canvasRadius * 2f),
                        )
                    }
                    startAngle += sweep
                }

                // 选中的扇区画得更大一圈，盖在其它扇区上面。
                selectedIndex?.let { index ->
                    val slice = slices.getOrNull(index)
                    if (slice != null) {
                        val popRadius = canvasRadius + 6.dp.toPx()
                        var angle = -90f
                        for (i in 0 until index) {
                            angle += slices[i].fraction * 360f * animated
                        }
                        drawArc(
                            color = slice.color,
                            startAngle = angle + 0.6f,
                            sweepAngle = (slice.fraction * 360f * animated - 1.2f).coerceAtLeast(0.1f),
                            useCenter = true,
                            topLeft = Offset(center.x - popRadius, center.y - popRadius),
                            size = Size(popRadius * 2f, popRadius * 2f),
                        )
                    }
                }
            }

            // 挖洞成环形
            drawCircle(
                color = holeColor,
                radius = canvasRadius * 0.58f,
                center = center,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "支出合计",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatYuan(totalCents),
                style = MaterialTheme.typography.titleLarge.merge(TabularNums),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** 饼图图例：色点 + 分类名 + 占比，点击可高亮对应扇区。 */
@Composable
fun PieLegend(
    slices: List<PieSlice>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        slices.forEachIndexed { index, slice ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable { onSelect(if (selected) null else index) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(slice.color),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = slice.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(slice.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 近 6 个月趋势柱状图：逐柱从 0 长到目标高度（延迟依次叠加）。
 *
 * 只有「当前月」用主题色实心，其余月份用同色低透明度，全页保持一个主题色。
 */
@Composable
fun TrendBarChart(
    trend: List<TrendBar>,
    modifier: Modifier = Modifier,
    barAreaHeight: Dp = 108.dp,
) {
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        trend.forEachIndexed { index, bar ->
            val fraction by animateFloatAsState(
                targetValue = bar.fraction.coerceIn(0.02f, 1f),
                animationSpec = tween(durationMillis = 650, delayMillis = index * 70),
                label = "bar$index",
            )
            val isCurrent = index == trend.lastIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = compactYuanText(bar.cents),
                    style = MaterialTheme.typography.labelSmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barAreaHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(barAreaHeight * fraction)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(if (isCurrent) primary else primary.copy(alpha = 0.22f)),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
