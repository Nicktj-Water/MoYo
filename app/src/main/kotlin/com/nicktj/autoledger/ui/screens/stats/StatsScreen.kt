package com.nicktj.autoledger.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.MonthSwitcher
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.currentMonth
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconBadge
import com.nicktj.autoledger.ui.kit.KitRow
import com.nicktj.autoledger.ui.kit.KitSection
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 统计：本月支出/收入/结余 + 分类占比 + 分类明细 + 近 6 个月趋势。
 *
 * 全部内容只用 Kit 组件：页头 → 月份切换 → hero 卡 → 红包开关 → 分区卡片。
 * 红包开关直接写 SettingsRepository，切换后所有数字重算。
 */
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.month, state.slices) { selectedIndex = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Kit.PageHorizontal, vertical = 16.dp),
    ) {
        KitHeader(title = "统计")

        Spacer(Modifier.height(16.dp))

        MonthSwitcher(
            month = state.month,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            canGoNext = state.month.isBefore(currentMonth()),
        )

        Spacer(Modifier.height(Kit.SectionGap))

        HeroCard(
            expenseCents = state.expenseCents,
            incomeCents = state.incomeCents,
            balanceCents = state.balanceCents,
        )

        Spacer(Modifier.height(Kit.Gap))

        RedPacketCard(
            enabled = state.countRedPacket,
            onToggle = viewModel::setCountRedPacket,
        )

        Spacer(Modifier.height(Kit.SectionGap))

        KitSection(title = "按分类")

        KitCard {
            if (state.slices.isEmpty()) {
                EmptyLine(text = "这个月还没有支出记录")
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CategoryPieChart(
                        slices = state.slices,
                        totalCents = state.expenseCents,
                        selectedIndex = selectedIndex,
                        onSelect = { selectedIndex = it },
                    )
                    Spacer(Modifier.height(16.dp))
                    PieLegend(
                        slices = state.slices,
                        selectedIndex = selectedIndex,
                        onSelect = { selectedIndex = it },
                    )
                }
            }
        }

        if (state.slices.isNotEmpty()) {
            Spacer(Modifier.height(Kit.SectionGap))

            KitSection(title = "分类明细", count = state.slices.size)

            KitCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    state.slices.forEach { slice ->
                        KitRow(
                            title = slice.name,
                            subtitle = "${(slice.fraction * 100).toInt()}%",
                            leading = {
                                KitIconBadge(
                                    icon = categoryIconFor(icon = null, name = slice.name),
                                    tint = slice.color,
                                    size = 36.dp,
                                )
                            },
                            trailing = {
                                Text(
                                    text = formatYuan(slice.cents),
                                    style = MaterialTheme.typography.bodyLarge.merge(TabularNums),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Kit.SectionGap))

        KitSection(title = "月度趋势")

        KitCard {
            if (state.trend.all { it.cents <= 0L }) {
                EmptyLine(text = "近 6 个月还没有支出记录")
            } else {
                TrendBarChart(trend = state.trend)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/* ---------------------------------------------------------------- hero 卡 */

/** 一张渐变卡承载三个关键数字：支出为主，收入 / 结余并排。 */
@Composable
private fun HeroCard(
    expenseCents: Long,
    incomeCents: Long,
    balanceCents: Long,
) {
    val extra = LocalExtra.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Kit.CardRadius))
            .background(extra.heroBrush)
            .padding(Kit.CardPadding + 8.dp),
    ) {
        Text(
            text = "本月支出",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatYuan(expenseCents),
            style = MaterialTheme.typography.displaySmall.merge(TabularNums),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroMetric(
                label = "收入",
                cents = incomeCents,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            HeroMetric(
                label = "结余",
                cents = balanceCents,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroMetric(
    label: String,
    cents: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatYuan(cents),
            style = MaterialTheme.typography.titleMedium.merge(TabularNums),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ---------------------------------------------------------------- 红包开关 */

@Composable
private fun RedPacketCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    KitCard {
        KitRow(
            title = "红包计入收支",
            subtitle = if (enabled) "收发红包会算进支出和收入" else "收发红包不计入统计",
            trailing = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                )
            },
            onClick = { onToggle(!enabled) },
        )
    }
}

/* ---------------------------------------------------------------- 空态 */

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}
