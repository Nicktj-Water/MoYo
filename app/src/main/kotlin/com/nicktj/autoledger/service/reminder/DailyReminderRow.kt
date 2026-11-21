package com.nicktj.autoledger.service.reminder

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitDivider
import com.nicktj.autoledger.ui.kit.KitRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * 设置页里的「每日记账提醒」开关 + 时间选择。
 *
 * 集成方只需要在设置页插入 `DailyReminderSettingsRow()`，不需要传任何参数。
 */
@Composable
fun DailyReminderSettingsRow(modifier: Modifier = Modifier) {
    val viewModel: DailyReminderViewModel = hiltViewModel()
    val enabled by viewModel.enabled.collectAsState()
    val minutes by viewModel.minutes.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    KitCard(modifier = modifier) {
        Column {
            KitRow(
                title = "每日记账提醒",
                subtitle = "每天固定时间提醒一次，今天已有账单时不提醒",
                trailing = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                },
            )
            if (enabled) {
                Spacer(modifier = Modifier.height(4.dp))
                KitDivider()
                KitRow(
                    title = "提醒时间",
                    subtitle = "一天最多一条；省电策略下可能略有延迟",
                    trailing = {
                        Text(
                            text = formatReminderTime(minutes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = { showPicker = true },
                )
            }
        }
    }

    if (showPicker) {
        ReminderTimePickerDialog(
            initialMinutes = minutes,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                viewModel.setMinutes(picked)
                showPicker = false
            },
        )
    }
}

/** 时:分 → HH:mm（24 小时制）。 */
private fun formatReminderTime(minutes: Int): String {
    val safe = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
    return String.format(Locale.CHINA, "%02d:%02d", safe / 60, safe % 60)
}

/** Material3 的 TimePicker 弹窗（material3 没有官方 TimePickerDialog，用 AlertDialog 包一层）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val safe = initialMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
    val state = rememberTimePickerState(
        initialHour = safe / 60,
        initialMinute = safe % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每日提醒时间") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 只负责设置读写与闹钟重排；UI 状态直接来自 DataStore。 */
@HiltViewModel
class DailyReminderViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.dailyReminderEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    val minutes: StateFlow<Int> = settings.dailyReminderMinutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DEFAULT_MINUTES,
    )

    /** 开关：先写库，再按最新值排程（关闭即取消）。 */
    fun setEnabled(value: Boolean) {
        viewModelScope.launch {
            settings.setDailyReminderEnabled(value)
            val currentMinutes = settings.dailyReminderMinutes.first()
            DailyReminderScheduler.reschedule(context, value, currentMinutes)
        }
    }

    /** 改时间：写库，开启状态下立即重排。 */
    fun setMinutes(value: Int) {
        viewModelScope.launch {
            settings.setDailyReminderMinutes(value)
            val enabledNow = settings.dailyReminderEnabled.first()
            if (enabledNow) DailyReminderScheduler.reschedule(context, true, value)
        }
    }

    private companion object {
        /** 默认 21:00，与 SettingsRepository 的默认值一致。 */
        const val DEFAULT_MINUTES = 21 * 60
    }
}

/** 一天 24 小时对应的分钟数。 */
private const val MINUTES_PER_DAY = 24 * 60
