package com.nicktj.autoledger.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.model.formatYuan
import com.nicktj.autoledger.ui.components.MonthSwitcher
import com.nicktj.autoledger.ui.components.PageHeader
import com.nicktj.autoledger.ui.components.SectionHeader
import com.nicktj.autoledger.ui.components.SoftCard
import com.nicktj.autoledger.ui.components.currentMonth
import com.nicktj.autoledger.ui.components.monthLabel
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 统计：本月支出/收入/结余 + 分类环形图 + 近 6 个月趋势。
 *
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

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PageHeader(
                title = "统计",
                subtitle = monthLabel(state.month),
            )
        }

        item(key = "month") {
            MonthSwitcher(
                month = state.month,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                canGoNext = state.month.isBefore(currentMonth()),
            )
        }

        item(key = "metrics") {
            MetricsCard(
                expenseCents = state.expenseCents,
                incomeCents = state.incomeCents,
                balanceCents = state.balanceCents,
            )
        }

        item(key = "red-packet") {
            SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "红包计入收支",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (state.countRedPacket) {
                                "收发红包会算进支出和收入"
                            } else {
                                "收发红包不计入统计"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = state.countRedPacket,
                        onCheckedChange = viewModel::setCountRedPacket,
                    )
                }
            }
        }

        item(key = "pie") {
            SoftCard {
                SectionHeader(title = "分类占比")
                Spacer(modifier = Modifier.height(8.dp))
                if (state.slices.isEmpty()) {
                    Text(
                        text = "这个月还没有支出记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    )
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
                        Spacer(modifier = Modifier.height(8.dp))
                        PieLegend(
                            slices = state.slices,
                            selectedIndex = selectedIndex,
                            onSelect = { selectedIndex = it },
                        )
                    }
                }
            }
        }

        item(key = "trend") {
            SoftCard {
                SectionHeader(title = "近 6 个月支出")
                Spacer(modifier = Modifier.height(12.dp))
                TrendBarChart(trend = state.trend)
            }
        }
    }
}

/** 顶部三个 metric：本月支出 / 收入 / 结余。 */
@Composable
private fun MetricsCard(
    expenseCents: Long,
    incomeCents: Long,
    balanceCents: Long,
) {
    val extra = LocalExtra.current
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricItem(
                label = "支出",
                value = formatYuan(expenseCents),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            MetricItem(
                label = "收入",
                value = formatYuan(incomeCents),
                color = extra.success,
                modifier = Modifier.weight(1f),
            )
            MetricItem(
                label = "结余",
                value = formatYuan(balanceCents),
                color = if (balanceCents < 0L) extra.danger else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.merge(TabularNums),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
