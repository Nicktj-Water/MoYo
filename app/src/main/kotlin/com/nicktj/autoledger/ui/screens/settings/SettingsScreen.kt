package com.nicktj.autoledger.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nicktj.autoledger.core.data.settings.ThemeMode
import com.nicktj.autoledger.ui.components.CardDivider
import com.nicktj.autoledger.ui.components.PageHeader
import com.nicktj.autoledger.ui.components.SectionHeader
import com.nicktj.autoledger.ui.components.SettingRow
import com.nicktj.autoledger.ui.components.SoftCard
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 设置：数据保留、红包口径、主题、导出、权限自检入口、关于。
 */
@Composable
fun SettingsScreen(
    onNavigatePermission: () -> Unit,
    onNavigateLogs: () -> Unit,
    onNavigateOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val exporting by viewModel.exporting.collectAsState()
    val exportTarget by viewModel.exportTarget.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    // 版本号从 PackageManager 读，避免依赖不同 AGP 版本里 BuildConfig 的字段差异。
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    var retention by remember(state.retentionDays) {
        mutableStateOf(state.retentionDays.toFloat())
    }

    LaunchedEffect(exportTarget) {
        val target = exportTarget ?: return@LaunchedEffect
        val error = ExportSharer.share(context, target.file, target.mimeType)
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
        viewModel.consumeExport()
    }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            PageHeader(
                title = "设置",
                subtitle = "AutoLedger $versionName",
            )
        }

        item(key = "retention") {
            SoftCard {
                SectionHeader(title = "原始文本保留")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "识别日志里的通知原文最多保留 ${state.retentionDays} 天，过期自动清理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "1 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = retention,
                        onValueChange = { retention = it },
                        modifier = Modifier.weight(1f),
                        valueRange = 1f..90f,
                        steps = 88,
                        onValueChangeFinished = {
                            viewModel.setRetentionDays(retention.toInt())
                        },
                    )
                    Text(
                        text = "90 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${retention.toInt()} 天",
                    style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item(key = "red-packet") {
            SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                SettingRow(
                    title = "红包计入收支",
                    subtitle = if (state.countRedPacket) {
                        "收发红包会计入支出与收入"
                    } else {
                        "收发红包不计入统计（推荐）"
                    },
                    icon = Icons.Outlined.Redeem,
                    trailing = {
                        Switch(
                            checked = state.countRedPacket,
                            onCheckedChange = viewModel::setCountRedPacket,
                        )
                    },
                )
            }
        }

        item(key = "theme") {
            SoftCard {
                SectionHeader(title = "主题")
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOption(
                        label = "跟随系统",
                        selected = state.themeMode == ThemeMode.SYSTEM,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    )
                    ThemeOption(
                        label = "浅色",
                        selected = state.themeMode == ThemeMode.LIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    )
                    ThemeOption(
                        label = "深色",
                        selected = state.themeMode == ThemeMode.DARK,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    )
                }
            }
        }

        item(key = "export") {
            SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                SettingRow(
                    title = "导出 CSV",
                    subtitle = "UTF-8 BOM + CRLF，Excel / WPS 直接打开",
                    icon = Icons.Outlined.FileDownload,
                    onClick = { if (!exporting) viewModel.exportCsv() },
                    trailing = {
                        if (exporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                )
                CardDivider()
                SettingRow(
                    title = "导出 XLSX",
                    subtitle = "金额为数字列，可直接求和",
                    icon = Icons.Outlined.TableChart,
                    onClick = { if (!exporting) viewModel.exportXlsx() },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "文件保存在应用外部目录 export/ 下，导出后会弹出系统分享面板。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        item(key = "more") {
            SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                SettingRow(
                    title = "识别日志",
                    subtitle = "看看每条通知为什么被记或被忽略",
                    icon = Icons.Outlined.History,
                    onClick = onNavigateLogs,
                )
                CardDivider()
                SettingRow(
                    title = "权限与自检",
                    subtitle = "通知监听、无障碍、省电策略检查",
                    icon = Icons.Outlined.Lock,
                    onClick = onNavigatePermission,
                )
                CardDivider()
                SettingRow(
                    title = "重新走一遍开启引导",
                    subtitle = "逐项检查并授权通知使用权、无障碍",
                    icon = Icons.Outlined.NotificationsActive,
                    onClick = onNavigateOnboarding,
                )
            }
        }

        item(key = "about") {
            SoftCard {
                SectionHeader(
                    title = "关于",
                    action = {
                        Text(
                            text = "v$versionName",
                            style = MaterialTheme.typography.labelMedium.merge(TabularNums),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "自用记账 App：从支付宝 / 微信 / 云闪付的通知里自动抓账单，" +
                            "不联网、不上传。数据只存在本机数据库里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "footer") {
            Text(
                text = "金额全程以「分」存储，时间统一按 Asia/Shanghai 切分。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
