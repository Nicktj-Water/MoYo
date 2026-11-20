package com.nicktj.autoledger.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.dao.RecognitionLogDao
import com.nicktj.autoledger.core.data.entity.RecognitionLogEntity
import com.nicktj.autoledger.core.model.RecognitionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 每页条数。 */
const val LOGS_PAGE_SIZE: Int = 50

data class LogsUiState(
    /** null 表示不过滤；否则是 [RecognitionOutcome] 的枚举名。 */
    val outcome: String? = null,
    val items: List<RecognitionLogEntity> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val initialized: Boolean = false,
)

/**
 * 识别日志：每页 50 条，滚到底部再加载下一页。
 *
 * 用「手动分页」而不是 observe 全表，日志表可能有几千条，全量 Flow 会拖慢列表。
 */
@HiltViewModel
class LogsViewModel @Inject constructor(
    private val recognitionLogDao: RecognitionLogDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        loadPage(reset = true)
    }

    fun currentOutcome(): RecognitionOutcome? =
        _uiState.value.outcome?.let { name ->
            RecognitionOutcome.entries.firstOrNull { it.name == name }
        }

    fun setOutcome(outcome: RecognitionOutcome?) {
        val target = outcome?.name
        if (_uiState.value.outcome == target) return
        _uiState.value = _uiState.value.copy(
            outcome = target,
            items = emptyList(),
            total = 0,
            endReached = false,
            loading = false,
            initialized = false,
        )
        loadPage(reset = true)
    }

    fun reload() {
        _uiState.value = _uiState.value.copy(
            items = emptyList(),
            total = 0,
            endReached = false,
            loading = false,
        )
        loadPage(reset = true)
    }

    /** 底部加载下一页；到底或正在加载时直接忽略。 */
    fun loadMore() {
        val state = _uiState.value
        if (state.loading || state.endReached) return
        loadPage(reset = false)
    }

    private fun loadPage(reset: Boolean) {
        val snapshot = _uiState.value
        if (snapshot.loading) return
        _uiState.update { it.copy(loading = true) }

        viewModelScope.launch {
            val outcome = snapshot.outcome
            val offset = if (reset) 0 else snapshot.items.size
            val total = recognitionLogDao.countByOutcome(outcome)
            val page = if (outcome == null) {
                recognitionLogDao.page(LOGS_PAGE_SIZE, offset)
            } else {
                recognitionLogDao.pageByOutcome(outcome, LOGS_PAGE_SIZE, offset)
            }
            _uiState.update { current ->
                // 翻页期间可能有新日志插到最前面，按 id 去重避免 LazyColumn 撞 key。
                val merged = (if (reset) page else current.items + page)
                    .distinctBy { it.id }
                current.copy(
                    items = merged,
                    total = total,
                    loading = false,
                    initialized = true,
                    endReached = merged.size >= total || page.isEmpty(),
                )
            }
        }
    }
}
