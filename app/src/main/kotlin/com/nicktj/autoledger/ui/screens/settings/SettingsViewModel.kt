package com.nicktj.autoledger.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.export.ExportRepository
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import com.nicktj.autoledger.core.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 导出完成后要分享的文件。 */
data class ExportTarget(
    val file: File,
    val mimeType: String,
)

data class SettingsUiState(
    val retentionDays: Int = 30,
    val countRedPacket: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val exportRepository: ExportRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.rawTextRetentionDays,
        settingsRepository.countRedPacket,
        settingsRepository.themeMode,
    ) { days, countRedPacket, themeMode ->
        SettingsUiState(
            retentionDays = days,
            countRedPacket = countRedPacket,
            themeMode = themeMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _exportTarget = MutableStateFlow<ExportTarget?>(null)
    val exportTarget: StateFlow<ExportTarget?> = _exportTarget.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setRawTextRetentionDays(days) }
    }

    fun setCountRedPacket(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCountRedPacket(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun exportCsv() {
        startExport(isCsv = true)
    }

    fun exportXlsx() {
        startExport(isCsv = false)
    }

    fun consumeExport() {
        _exportTarget.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun startExport(isCsv: Boolean) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            runCatching {
                val directory = exportDirectory()
                if (isCsv) {
                    ExportTarget(
                        file = exportRepository.exportCsv(directory),
                        mimeType = MIME_CSV,
                    )
                } else {
                    ExportTarget(
                        file = exportRepository.exportXlsx(directory),
                        mimeType = MIME_XLSX,
                    )
                }
            }.onSuccess { target ->
                _exportTarget.value = target
            }.onFailure { throwable ->
                _message.value = "导出失败：" + (throwable.message ?: throwable.javaClass.simpleName)
            }
            _exporting.value = false
        }
    }

    /** 落盘位置：getExternalFilesDir(null)/export/，失败时退回内部存储。 */
    private fun exportDirectory(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "export")
    }

    private companion object {
        const val MIME_CSV = "text/csv"
        const val MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
