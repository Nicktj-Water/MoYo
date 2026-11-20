package com.nicktj.autoledger.ui.screens.bills

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.dao.BudgetDao
import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.MerchantRuleDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.entity.BudgetEntity
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.data.entity.MerchantRuleEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import com.nicktj.autoledger.core.domain.BudgetDef
import com.nicktj.autoledger.core.domain.BudgetEngine
import com.nicktj.autoledger.core.domain.BudgetLevel
import com.nicktj.autoledger.core.domain.Categorizer
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.currentMonth
import com.nicktj.autoledger.ui.components.dayOf
import com.nicktj.autoledger.ui.components.dayTitle
import com.nicktj.autoledger.ui.components.isExpenseSide
import com.nicktj.autoledger.ui.components.monthEndMillis
import com.nicktj.autoledger.ui.components.monthKey
import com.nicktj.autoledger.ui.components.monthStartMillis
import com.nicktj.autoledger.ui.components.timeLabel
import com.nicktj.autoledger.ui.components.toDomainTxn
import com.nicktj.autoledger.ui.components.txnType
import com.nicktj.autoledger.ui.components.weekdayLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** 列表里的一行账单。 */
data class BillRow(
    val txn: TransactionEntity,
    val categoryName: String,
    val categoryIcon: ImageVector,
    val title: String,
    val subtitle: String,
)

/** 按日期分组后的卡片。 */
data class DayGroup(
    val date: LocalDate,
    val title: String,
    val weekday: String,
    val spentCents: Long,
    val items: List<BillRow>,
)

data class BillsUiState(
    val month: YearMonth = currentMonth(),
    val groups: List<DayGroup> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val monthSpentCents: Long = 0L,
    val monthIncomeCents: Long = 0L,
    val budgetTotalCents: Long = 0L,
    val budgetRemainingCents: Long = 0L,
    val budgetRatio: Double = 0.0,
    val budgetLevel: BudgetLevel = BudgetLevel.NORMAL,
    val hasBudget: Boolean = false,
    val txnCount: Int = 0,
    val loading: Boolean = true,
)

private data class BillsSlice(
    val txns: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val countRedPacket: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val budgetDao: BudgetDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val categorizer = Categorizer()
    private val budgetEngine = BudgetEngine()

    private val month = MutableStateFlow(currentMonth())

    private val monthTxns: Flow<List<TransactionEntity>> = month.flatMapLatest { selected ->
        transactionDao.observeBetween(monthStartMillis(selected), monthEndMillis(selected))
    }

    private val slice: Flow<BillsSlice> = combine(
        monthTxns,
        categoryDao.observeAll(),
        budgetDao.observeAll(),
        settingsRepository.countRedPacket,
    ) { txns, categories, budgets, countRedPacket ->
        BillsSlice(txns, categories, budgets, countRedPacket)
    }

    val uiState: StateFlow<BillsUiState> = combine(month, slice) { selected, data ->
        buildState(selected, data)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BillsUiState(loading = true),
    )

    /** 正在编辑的账单；null 表示编辑弹层关闭。 */
    private val _editTarget = MutableStateFlow<TransactionEntity?>(null)
    val editTarget: StateFlow<TransactionEntity?> = _editTarget.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ------------------------------------------------------------------
    // 月份
    // ------------------------------------------------------------------

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (!next.isAfter(currentMonth())) {
            month.value = next
        }
    }

    // ------------------------------------------------------------------
    // 编辑 / 删除 / 手动记账
    // ------------------------------------------------------------------

    fun openEditor(txn: TransactionEntity) {
        _editTarget.value = txn
    }

    fun openEditorById(id: Long) {
        viewModelScope.launch {
            _editTarget.value = transactionDao.findById(id) ?: run {
                _message.value = "这笔账单已经不存在了"
                null
            }
        }
    }

    fun closeEditor() {
        _editTarget.value = null
    }

    fun save(updated: TransactionEntity, learnCategory: Boolean, confirm: Boolean = false) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val status = if (confirm) TxnStatus.CONFIRMED.name else updated.status
            transactionDao.update(updated.copy(status = status, updatedAt = now))
            val newCategoryId = updated.categoryId
            if (learnCategory && newCategoryId != null) {
                learnRule(updated.merchant, newCategoryId)
            }
            _editTarget.value = null
            _message.value = if (confirm) "已确认入账" else "已保存"
        }
    }

    fun delete(txn: TransactionEntity) {
        viewModelScope.launch {
            transactionDao.delete(txn)
            _editTarget.value = null
            _message.value = "已删除"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** 手动记一笔：固定 MANUAL 通道、置信度 1.0、直接确认。 */
    fun saveManual(draft: ManualDraft) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val merchant = draft.merchant?.trim()?.ifEmpty { null }
            transactionDao.insert(
                TransactionEntity(
                    amountCents = draft.amountCents,
                    type = draft.type.name,
                    categoryId = draft.categoryId,
                    merchant = merchant,
                    source = PaySource.MANUAL.name,
                    channel = Channel.MANUAL.name,
                    occurredAt = draft.occurredAt,
                    note = draft.note?.trim()?.ifEmpty { null },
                    confidence = 1.0,
                    status = TxnStatus.CONFIRMED.name,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (merchant != null) {
                val draftCategoryId = draft.categoryId
                if (draftCategoryId != null) {
                    learnRule(merchant, draftCategoryId)
                }
            }
            // 记到别的月份时自动跳到那个月，否则用户会以为没存上。
            val targetMonth = com.nicktj.autoledger.ui.components.yearMonthOf(draft.occurredAt)
            if (targetMonth != month.value) {
                month.value = targetMonth
            }
            _message.value = "已记一笔"
        }
    }

    /**
     * 学习商户 -> 分类规则，具体实现见 [learnCategoryRule]。
     */
    private suspend fun learnRule(merchant: String?, categoryId: Long) {
        merchantRuleDao.learnCategoryRule(categorizer, merchant, categoryId)
    }

    // ------------------------------------------------------------------
    // 组装 UI 状态
    // ------------------------------------------------------------------

    private fun buildState(selected: YearMonth, data: BillsSlice): BillsUiState {
        val key = monthKey(selected)
        val confirmed = data.txns.filter { it.status == TxnStatus.CONFIRMED.name }

        val budgetDefs = data.budgets
            .filter { it.yearMonth == null || it.yearMonth == key }
            .map { BudgetDef(categoryId = it.categoryId, amountCents = it.amountCents) }

        val budgetStatus = budgetEngine.compute(
            budgets = budgetDefs,
            txns = confirmed.map { it.toDomainTxn() },
            countRedPacket = data.countRedPacket,
            yearMonth = key,
        )

        val categoryById = data.categories.associateBy { it.id }
        val groups = confirmed
            .groupBy { dayOf(it.occurredAt) }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayTxns) ->
                val ordered = dayTxns.sortedByDescending { item -> item.occurredAt }
                DayGroup(
                    date = date,
                    title = dayTitle(date),
                    weekday = weekdayLabel(date),
                    spentCents = ordered
                        .filter { it.txnType().isExpenseSide(data.countRedPacket) }
                        .sumOf { it.amountCents },
                    items = ordered.map { it.toRow(categoryById) },
                )
            }

        val incomeCents = confirmed
            .filter { it.txnType().let { type -> type == TxnType.INCOME } }
            .sumOf { it.amountCents }

        return BillsUiState(
            month = selected,
            groups = groups,
            categories = data.categories,
            monthSpentCents = budgetStatus.totalSpentCents,
            monthIncomeCents = incomeCents,
            budgetTotalCents = budgetStatus.totalBudgetCents,
            budgetRemainingCents = budgetStatus.totalRemainingCents,
            budgetRatio = budgetStatus.totalUsedRatio,
            budgetLevel = budgetStatus.level,
            hasBudget = budgetStatus.totalBudgetCents > 0L,
            txnCount = confirmed.size,
            loading = false,
        )
    }

    private fun TransactionEntity.toRow(categories: Map<Long, CategoryEntity>): BillRow {
        val category = categoryId?.let { categories[it] }
        val categoryName = category?.name ?: "未分类"
        val merchantText = merchant?.trim().orEmpty()
        val title = merchantText.ifEmpty { categoryName }
        val subtitle = buildString {
            append(timeLabel(occurredAt))
            if (merchantText.isNotEmpty()) {
                append(" · ")
                append(categoryName)
            }
        }
        return BillRow(
            txn = this,
            categoryName = categoryName,
            categoryIcon = categoryIconFor(category?.icon, categoryName),
            title = title,
            subtitle = subtitle,
        )
    }
}

/** 手动记账弹层提交的草稿。 */
data class ManualDraft(
    val amountCents: Long,
    val type: TxnType,
    val categoryId: Long?,
    val merchant: String?,
    val note: String?,
    val occurredAt: Long,
)
