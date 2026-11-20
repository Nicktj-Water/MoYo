package com.nicktj.autoledger.ui.screens.pending

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.MerchantRuleDao
import com.nicktj.autoledger.core.data.dao.RecognitionLogDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.entity.CategoryEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.domain.Categorizer
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.ui.components.categoryIconFor
import com.nicktj.autoledger.ui.components.displayName
import com.nicktj.autoledger.ui.screens.bills.learnCategoryRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 待确认列表里的一条。 */
data class PendingRow(
    val txn: TransactionEntity,
    val categoryName: String,
    val categoryIcon: ImageVector,
    val rawText: String,
    val channelLabel: String,
    val sourceLabel: String,
)

data class PendingUiState(
    val rows: List<PendingRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class PendingViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val recognitionLogDao: RecognitionLogDao,
) : ViewModel() {

    private val categorizer = Categorizer()

    private val _editTarget = MutableStateFlow<TransactionEntity?>(null)
    val editTarget: StateFlow<TransactionEntity?> = _editTarget.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<PendingUiState> = combine(
        transactionDao.observePending(),
        categoryDao.observeAll(),
    ) { txns, categories -> txns to categories }
        .transform { (txns, categories) ->
            val logIds = txns.mapNotNull { it.rawLogId }.distinct()
            val logs = if (logIds.isEmpty()) {
                emptyMap()
            } else {
                recognitionLogDao.textsByIds(logIds).associateBy { it.id }
            }
            val categoryById = categories.associateBy { it.id }

            val rows = txns.map { txn ->
                val category = txn.categoryId?.let { categoryById[it] }
                val name = category?.name ?: "未分类"
                PendingRow(
                    txn = txn,
                    categoryName = name,
                    categoryIcon = categoryIconFor(category?.icon, name),
                    rawText = txn.rawLogId?.let { logs[it] }?.let { log ->
                        log.text ?: log.bigText ?: log.title.orEmpty()
                    }.orEmpty(),
                    channelLabel = Channel.entries
                        .firstOrNull { it.name == txn.channel }
                        ?.displayName()
                        ?: txn.channel,
                    sourceLabel = PaySource.entries
                        .firstOrNull { it.name == txn.source }
                        ?.displayName()
                        ?: txn.source,
                )
            }
            emit(PendingUiState(rows = rows, categories = categories, loading = false))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PendingUiState(loading = true),
        )

    /** 直接确认入账。 */
    fun confirm(txn: TransactionEntity) {
        viewModelScope.launch {
            transactionDao.update(
                txn.copy(
                    status = TxnStatus.CONFIRMED.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _message.value = "已入账"
        }
    }

    fun openEditor(txn: TransactionEntity) {
        _editTarget.value = txn
    }

    fun closeEditor() {
        _editTarget.value = null
    }

    /** 改完再确认：保存时强制 CONFIRMED。 */
    fun saveConfirmed(updated: TransactionEntity, learnCategory: Boolean) {
        viewModelScope.launch {
            transactionDao.update(
                updated.copy(
                    status = TxnStatus.CONFIRMED.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val newCategoryId = updated.categoryId
            if (learnCategory && newCategoryId != null) {
                merchantRuleDao.learnCategoryRule(
                    categorizer = categorizer,
                    merchant = updated.merchant,
                    categoryId = newCategoryId,
                )
            }
            _editTarget.value = null
            _message.value = "已确认入账"
        }
    }

    /** 丢弃：保留记录但不计入统计，方便回溯。 */
    fun discard(txn: TransactionEntity) {
        viewModelScope.launch {
            transactionDao.update(
                txn.copy(
                    status = TxnStatus.DISCARDED.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _message.value = "已丢弃"
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
