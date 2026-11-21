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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Redeem
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
import com.nicktj.autoledger.service.reminder.DailyReminderSettingsRow
import com.nicktj.autoledger.ui.kit.Kit
import com.nicktj.autoledger.ui.kit.KitCard
import com.nicktj.autoledger.ui.kit.KitDivider
import com.nicktj.autoledger.ui.kit.KitHeader
import com.nicktj.autoledger.ui.kit.KitIconBadge
import com.nicktj.autoledger.ui.kit.KitRow
import com.nicktj.autoledger.ui.kit.KitSection
import com.nicktj.autoledger.ui.theme.LocalExtra
import com.nicktj.autoledger.ui.theme.TabularNums

/**
 * 设置：记账口径、外观、数据导出、工具入口、关于。
 *
 * 全部用 Kit 搭：KitHeader → 每个分组 = KitSection + KitCard(KitRow…)。
 * 卡片内不写内边距，由每行自己留 Kit.CardPadding，分割线才能通栏。
 */
@Composable
fun SettingsScreen(
    onNavigatePermission: () -> Unit,
    onNavigateLogs: () -> Unit,
    onNavigateOnboarding: () -> Unit,
    onNavigateBudget: () -> Unit = {},
    onNavigatePending: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val exporting by viewModel.exporting.collectAsState()
    val exportTarget by viewModel.exportTarget.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val extra = LocalExtra.current

    // 版本号从 PackageManager 读，避免依赖不同 AGP 版本里 BuildConfig 的字段差异。
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    // 滑杆拖动时先本地跟手，松手才写 DataStore。
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
        contentPadding = PaddingValues(
            start = Kit.PageHorizontal,
            end = Kit.PageHorizontal,
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Kit.SectionGap),
    ) {
        item(key = "header") {
            KitHeader(
                title = "设置",
                subtitle = "AutoLedger $versionName",
            )
        }

        /* ------------------------------------------------------------ 记账 */
        item(key = "group-bookkeeping") {
            Column {
                KitSection(title = "记账")
                KitCard(padding = 0.dp) {
                    Column {
                        KitRow(
                            title = "红包计入收支",
                            subtitle = if (state.countRedPacket) {
                                "收发红包会计入支出与收入"
                            } else {
                                "收发红包不计入统计（推荐）"
                            },
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.Redeem,
                                    tint = if (state.countRedPacket) {
                                        extra.success
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            trailing = {
                                Switch(
                                    checked = state.countRedPacket,
                                    onCheckedChange = viewModel::setCountRedPacket,
                                )
                            },
                        )
                        KitDivider()
                        KitRow(
                            title = "原文保留天数",
                            subtitle = "识别日志里的通知原文过期自动清理",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.History,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailing = {
                                Text(
                                    text = "${retention.toInt()} 天",
                                    style = MaterialTheme.typography.labelLarge.merge(TabularNums),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        Row(
                            modifier = Modifier.padding(
                                start = Kit.CardPadding,
                                end = Kit.CardPadding,
                                bottom = Kit.CardPadding,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "1 天",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = retention,
                                onValueChange = { retention = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
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
                        DailyReminderSettingsRow(modifier = Modifier.padding(horizontal = Kit.CardPadding))
                    }
                }
            }
        }

        /* ------------------------------------------------------------ 外观 */
        item(key = "group-appearance") {
            Column {
                KitSection(title = "外观")
                KitCard(padding = 0.dp) {
                    Column {
                        KitRow(
                            title = "主题",
                            subtitle = themeLabel(state.themeMode),
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.Palette,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                        Row(
                            modifier = Modifier.padding(
                                start = Kit.CardPadding,
                                end = Kit.CardPadding,
                                bottom = Kit.CardPadding,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
            }
        }

        /* ------------------------------------------------------------ 数据 */
        item(key = "group-data") {
            Column {
                KitSection(title = "数据")
                KitCard(padding = 0.dp) {
                    Column {
                        KitRow(
                            title = "导出 CSV",
                            subtitle = "UTF-8 BOM + CRLF，Excel / WPS 直接打开",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.FileDownload,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
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
                        KitDivider()
                        KitRow(
                            title = "导出 XLSX",
                            subtitle = "金额为数字列，可直接求和",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.TableChart,
                                    tint = extra.success,
                                )
                            },
                            onClick = { if (!exporting) viewModel.exportXlsx() },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "文件保存在应用外部目录 export/ 下，导出后会弹出系统分享面板。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        /* ------------------------------------------------------------ 工具 */
        item(key = "group-tools") {
            Column {
                KitSection(title = "工具")
                KitCard(padding = 0.dp) {
                    Column {
                        KitRow(
                            title = "待确认",
                            subtitle = "低置信度的自动记账，确认或丢弃",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.Inbox,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onNavigatePending,
                        )
                        KitDivider()
                        KitRow(
                            title = "预算",
                            subtitle = "月总预算与分类预算",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onNavigateBudget,
                        )
                        KitDivider()
                        KitRow(
                            title = "识别日志",
                            subtitle = "看看每条通知为什么被记或被忽略",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.History,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onNavigateLogs,
                        )
                        KitDivider()
                        KitRow(
                            title = "权限与自检",
                            subtitle = "通知监听、无障碍、省电策略检查",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.Lock,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onNavigatePermission,
                        )
                        KitDivider()
                        KitRow(
                            title = "重新走一遍开启引导",
                            subtitle = "逐项检查并授权通知使用权、无障碍",
                            modifier = Modifier.padding(horizontal = Kit.CardPadding),
                            leading = {
                                KitIconBadge(
                                    icon = Icons.Outlined.NotificationsActive,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = onNavigateOnboarding,
                        )
                    }
                }
            }
        }

        /* ------------------------------------------------------------ 关于 */
        item(key = "group-about") {
            Column {
                KitSection(title = "关于")
                KitCard {
                    KitRow(
                        title = "AutoLedger",
                        subtitle = "本机记账，不联网、不上传",
                        leading = {
                            KitIconBadge(
                                icon = Icons.Outlined.Info,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailing = {
                            Text(
                                text = "v$versionName",
                                style = MaterialTheme.typography.labelLarge.merge(TabularNums),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "自用记账 App：从支付宝 / 微信 / 云闪付的通知里自动抓账单，" +
                            "数据只存在本机数据库里。",
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

/** 当前主题的一句话说明。 */
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统的浅色 / 深色"
    ThemeMode.LIGHT -> "始终使用浅色"
    ThemeMode.DARK -> "始终使用深色"
}

/** 主题三选一：选中填 primaryContainer，未选中用中性底色。 */
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
            .padding(vertical = 12.dp),
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
