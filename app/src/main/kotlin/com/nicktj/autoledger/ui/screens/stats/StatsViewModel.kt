package com.nicktj.autoledger.ui.screens.stats

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.ui.components.currentMonth
import com.nicktj.autoledger.ui.components.effectiveExpenseCents
import com.nicktj.autoledger.ui.components.isExpenseSide
import com.nicktj.autoledger.ui.components.isIncomeSide
import com.nicktj.autoledger.ui.components.monthEndMillis
import com.nicktj.autoledger.ui.components.monthStartMillis
import com.nicktj.autoledger.ui.components.shortMonthLabel
import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.components.yearMonthOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/** 饼图的一块。 */
data class PieSlice(
    val categoryId: Long?,
    val name: String,
    val cents: Long,
    val fraction: Float,
    val color: Color,
)

/** 趋势图的一根柱。 */
data class TrendBar(
    val label: String,
    val cents: Long,
    val fraction: Float,
)

data class StatsUiState(
    val month: YearMonth = currentMonth(),
    val expenseCents: Long = 0L,
    val incomeCents: Long = 0L,
    val balanceCents: Long = 0L,
    val slices: List<PieSlice> = emptyList(),
    val trend: List<TrendBar> = emptyList(),
    val countRedPacket: Boolean = false,
    val loading: Boolean = true,
)

/** 饼图配色（固定顺序，保证同分类颜色稳定）。 */
internal val PIE_PALETTE: List<Color> = listOf(
    Color(0xFF4F46E5),
    Color(0xFF7C3AED),
    Color(0xFF0EA5E9),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF64748B),
)

/** 趋势图展示的月份数。 */
private const val TREND_MONTHS = 6
private const val PIE_MAX_SLICES = 6

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())

    /** 趋势要看近 6 个月，所以一次把窗口内的账单都读出来自己按月切。 */
    private val windowTxns: Flow<List<TransactionEntity>> = month.flatMapLatest { selected ->
        val from = monthStartMillis(selected.minusMonths((TREND_MONTHS - 1).toLong()))
        val to = monthEndMillis(selected)
        transactionDao.observeBetween(from, to)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        month,
        windowTxns,
        categoryDao.observeAll(),
        settingsRepository.countRedPacket,
    ) { selected, txns, categories, countRedPacket ->
        buildState(selected, txns, categories, countRedPacket)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(loading = true),
    )

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (!next.isAfter(currentMonth())) {
            month.value = next
        }
    }

    fun setCountRedPacket(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCountRedPacket(enabled) }
    }

    private fun buildState(
        selected: YearMonth,
        txns: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        countRedPacket: Boolean,
    ): StatsUiState {
        val confirmed = txns.filter { it.status == TxnStatus.CONFIRMED.name }
        val monthTxns = confirmed.filter { yearMonthOf(it.occurredAt) == selected }

        val expenseCents = monthTxns
            .filter { it.txnType().isExpenseSide(countRedPacket) }
            .sumOf { if (it.txnType() == TxnType.EXPENSE) it.effectiveExpenseCents() else it.amountCents }

        val incomeCents = monthTxns
            .filter { it.txnType().isIncomeSide(countRedPacket) }
            .sumOf { it.amountCents }

        // 分类占比：只统计支出侧
        val categoryNameById = categories.associate { it.id to it.name }
        val byCategory = monthTxns
            .filter { it.txnType().isExpenseSide(countRedPacket) }
            .groupBy { it.categoryId }
            .mapValues { (_, items) ->
                items.sumOf {
                    if (it.txnType() == TxnType.EXPENSE) it.effectiveExpenseCents() else it.amountCents
                }
            }
            .filterValues { it > 0L }
            .toList()
            .sortedByDescending { it.second }

        val head = byCategory.take(PIE_MAX_SLICES)
        val tailTotal = byCategory.drop(PIE_MAX_SLICES).sumOf { it.second }
        val buckets: List<Pair<Long?, Long>> = buildList {
            addAll(head)
            if (tailTotal > 0L) add(Pair<Long?, Long>(null, tailTotal))
        }

        val slices = if (expenseCents <= 0L) {
            emptyList()
        } else {
            buckets.mapIndexed { index, (categoryId, cents) ->
                PieSlice(
                    categoryId = categoryId,
                    name = if (categoryId == null) {
                        "其他"
                    } else {
                        categoryNameById[categoryId] ?: "未分类"
                    },
                    cents = cents,
                    fraction = (cents.toDouble() / expenseCents.toDouble()).toFloat(),
                    color = PIE_PALETTE[index % PIE_PALETTE.size],
                )
            }
        }

        // 近 6 个月趋势（同样受红包开关影响）
        val months = (TREND_MONTHS - 1 downTo 0).map { selected.minusMonths(it.toLong()) }
        val monthlyTotals = months.map { target ->
            target to confirmed
                .filter { yearMonthOf(it.occurredAt) == target }
                .filter { it.txnType().isExpenseSide(countRedPacket) }
                .sumOf {
                    if (it.txnType() == TxnType.EXPENSE) it.effectiveExpenseCents() else it.amountCents
                }
        }
        val maxTotal = monthlyTotals.maxOfOrNull { it.second } ?: 0L
        val trend = monthlyTotals.map { (target, cents) ->
            TrendBar(
                label = shortMonthLabel(target),
                cents = cents,
                fraction = if (maxTotal <= 0L) 0f else (cents.toDouble() / maxTotal.toDouble()).toFloat(),
            )
        }

        return StatsUiState(
            month = selected,
            expenseCents = expenseCents,
            incomeCents = incomeCents,
            balanceCents = incomeCents - expenseCents,
            slices = slices,
            trend = trend,
            countRedPacket = countRedPacket,
            loading = false,
        )
    }
}
