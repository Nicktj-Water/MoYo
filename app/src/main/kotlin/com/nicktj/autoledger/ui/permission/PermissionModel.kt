package com.nicktj.autoledger.ui.permission

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.ui.graphics.vector.ImageVector

/** 单个权限项的当前状态。 */
sealed interface PermissionStatus {

    /** 已获得 / 已开启。 */
    data object Granted : PermissionStatus

    /** 明确未获得 / 未开启。 */
    data object Denied : PermissionStatus

    /** 系统不提供查询接口，或者读取状态失败；[reason] 用于在界面上解释原因。 */
    data class Unknown(val reason: String) : PermissionStatus
}

/**
 * 引导页 / 自检页要展示的权限项。
 *
 * [systemOnly] 表示系统没有查询接口，只能展示提示文案（自启动、最近任务锁定）。
 */
enum class PermissionKind(
    val title: String,
    val description: String,
    val actionLabel: String,
    val icon: ImageVector,
    val systemOnly: Boolean = false,
) {
    NOTIFICATION_LISTENER(
        title = "通知使用权",
        description = "读取支付宝、微信、云闪付的支付通知，这是最主要的入口。",
        actionLabel = "开启通知使用权",
        icon = Icons.Outlined.NotificationsActive,
    ),
    ACCESSIBILITY(
        title = "无障碍服务",
        description = "通知被折叠时，靠页面上的「支付成功」兜底。只读取文字，不读输入框。",
        actionLabel = "开启无障碍",
        icon = Icons.Outlined.Accessibility,
    ),
    POST_NOTIFICATIONS(
        title = "通知权限",
        description = "记账成功、预算阈值和待补金额的提醒需要它。",
        actionLabel = "开启通知权限",
        icon = Icons.Outlined.Notifications,
    ),
    AUTOSTART(
        title = "自启动",
        description = "小米 / 华为等系统会拦截后台启动，建议允许自启动。",
        actionLabel = "去设置",
        icon = Icons.Outlined.PowerSettingsNew,
        systemOnly = true,
    ),
    BATTERY_UNRESTRICTED(
        title = "省电策略无限制",
        description = "省电模式可能杀掉通知监听；把 AutoLedger 设为「无限制」。",
        actionLabel = "去设置",
        icon = Icons.Outlined.BatteryChargingFull,
    ),
    RECENTS_LOCK(
        title = "最近任务锁定",
        description = "在最近任务里下拉/长按 AutoLedger 卡片加锁，避免被一键清理。",
        actionLabel = "打开最近任务",
        icon = Icons.Outlined.Lock,
        systemOnly = true,
    ),
}

/** 一项权限 + 它的当前状态。 */
data class PermissionItemState(
    val kind: PermissionKind,
    val status: PermissionStatus,
)
