package com.nicktj.autoledger.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** 全部导航路由。 */
object Routes {
    const val BILLS = "bills"
    const val STATS = "stats"
    const val PENDING = "pending"
    const val BUDGET = "budget"
    const val SETTINGS = "settings"

    /** 从设置页进入，不占底部导航。 */
    const val LOGS = "logs"

    /** 权限与自检（ui/permission/SelfCheckScreen），从设置页进入。 */
    const val PERMISSION = "permission"

    /** 需要显示底部导航的一级页面。 */
    val bottomRoutes: Set<String> = setOf(BILLS, STATS, PENDING, BUDGET, SETTINGS)
}

/** 底部导航的一项。 */
data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** 底部导航 5 项：账单 / 统计 / 待确认 / 预算 / 设置。 */
val BOTTOM_TABS: List<BottomTab> = listOf(
    BottomTab(route = Routes.BILLS, label = "账单", icon = Icons.Outlined.ReceiptLong),
    BottomTab(route = Routes.STATS, label = "统计", icon = Icons.Outlined.BarChart),
    BottomTab(route = Routes.PENDING, label = "待确认", icon = Icons.Outlined.Inbox),
    BottomTab(route = Routes.BUDGET, label = "预算", icon = Icons.Outlined.AccountBalanceWallet),
    BottomTab(route = Routes.SETTINGS, label = "设置", icon = Icons.Outlined.Settings),
)
