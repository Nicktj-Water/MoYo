package com.nicktj.autoledger.ui.screens.budget

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.dao.BudgetDao
import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.entity.BudgetEntity
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import com.nicktj.autoledger.core.domain.BudgetDef
import com.nicktj.autoledger.core.domain.BudgetEngine
import com.nicktj.autoledger.core.domain.BudgetLevel
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.currentMonth
import com.nicktj.autoledger.ui.components.monthEndMillis
import com.nicktj.autoledger.ui.components.monthKey
import com.nicktj.autoledger.ui.components.monthLabel
import com.nicktj.autoledger.ui.components.monthStartMillis
import com.nicktj.autoledger.ui.components.toDomainTxn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 分类预算在当前月的执行情况。 */
data class CategoryBudgetRow(
    val categoryId: Long,
    val name: String,
    val icon: ImageVector,
    val budgetCents: Long,
    val spentCents: Long,
    val ratio: Float,
)

data class BudgetUiState(
    val monthName: String = monthLabel(currentMonth()),
    val totalBudgetCents: Long = 0L,
    val totalSpentCents: Long = 0L,
    val remainingCents: Long = 0L,
    val ratio: Float = 0f,
    val level: BudgetLevel = BudgetLevel.NORMAL,
    val rows: List<CategoryBudgetRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val loading: Boolean = true,
)

private data class BudgetSlice(
    val txns: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val countRedPacket: Boolean,
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val budgetEngine = BudgetEngine()
    private val month = currentMonth()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val slice = combine(
        transactionDao.observeBetween(monthStartMillis(month), monthEndMillis(month)),
        categoryDao.observeAll(),
        budgetDao.observeAll(),
        settingsRepository.countRedPacket,
    ) { txns, categories, budgets, countRedPacket ->
        BudgetSlice(txns, categories, budgets, countRedPacket)
    }

    val uiState: StateFlow<BudgetUiState> = slice
        .map { data -> buildState(data) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BudgetUiState(loading = true),
        )

    /**
     * 保存预算：整体重写「默认每月」的那一组，避免残留已删除的分类预算。
     * 月总预算写 categoryId = null，分类预算写对应 categoryId。
     */
    fun save(totalCents: Long, categoryBudgets: Map<Long, Long>) {
        viewModelScope.launch {
            budgetDao.deleteDefaults()
            if (totalCents > 0L) {
                budgetDao.upsert(
                    BudgetEntity(yearMonth = null, categoryId = null, amountCents = totalCents),
                )
            }
            categoryBudgets.forEach { (categoryId, amountCents) ->
                if (amountCents > 0L) {
                    budgetDao.upsert(
                        BudgetEntity(
                            yearMonth = null,
                            categoryId = categoryId,
                            amountCents = amountCents,
                        ),
                    )
                }
            }
            _message.value = "预算已保存"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun buildState(data: BudgetSlice): BudgetUiState {
        val key = monthKey(month)
        val budgetDefs = data.budgets
            .filter { it.yearMonth == null || it.yearMonth == key }
            .map { BudgetDef(categoryId = it.categoryId, amountCents = it.amountCents) }

        val status = budgetEngine.compute(
            budgets = budgetDefs,
            txns = data.txns
                .filter { it.status == TxnStatus.CONFIRMED.name }
                .map { it.toDomainTxn() },
            countRedPacket = data.countRedPacket,
            yearMonth = key,
        )

        val categoryById = data.categories.associateBy { it.id }
        val rows = status.byCategory.entries
            .mapNotNull { (categoryId, categoryStatus) ->
                val category = categoryById[categoryId] ?: return@mapNotNull null
                CategoryBudgetRow(
                    categoryId = categoryId,
                    name = category.name,
                    icon = categoryIconFor(category.icon, category.name),
                    budgetCents = categoryStatus.budgetCents,
                    spentCents = categoryStatus.spentCents,
                    ratio = categoryStatus.usedRatio.toFloat(),
                )
            }
            .sortedByDescending { it.ratio }

        return BudgetUiState(
            monthName = monthLabel(month),
            totalBudgetCents = status.totalBudgetCents,
            totalSpentCents = status.totalSpentCents,
            remainingCents = status.totalRemainingCents,
            ratio = status.totalUsedRatio.toFloat(),
            level = status.level,
            rows = rows,
            categories = data.categories,
            loading = false,
        )
    }
}
