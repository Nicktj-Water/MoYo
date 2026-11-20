package com.nicktj.autoledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nicktj.autoledger.ui.components.PageHeader

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val BOTTOM_TABS = listOf(
    BottomTab(route = "bills", label = "账单", icon = Icons.Outlined.ReceiptLong),
    BottomTab(route = "stats", label = "统计", icon = Icons.Outlined.BarChart),
    BottomTab(route = "pending", label = "待确认", icon = Icons.Outlined.Inbox),
    BottomTab(route = "settings", label = "设置", icon = Icons.Outlined.Settings),
)

private object Routes {
    const val BILLS = "bills"
    const val STATS = "stats"
    const val PENDING = "pending"
    const val SETTINGS = "settings"
}

@Composable
fun AutoLedgerRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                BOTTOM_TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                        label = { Text(text = tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.BILLS,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.BILLS) {
                PlaceholderScreen("账单", "这里会显示按时间排序的收支流水。")
            }
            composable(Routes.STATS) {
                PlaceholderScreen("统计", "这里会显示月度趋势和分类占比。")
            }
            composable(Routes.PENDING) {
                PlaceholderScreen("待确认", "解析不确定的账单会先放在这里等你确认。")
            }
            composable(Routes.SETTINGS) {
                PlaceholderScreen("设置", "通知监听、无障碍、预算与数据保留都会在这里配置。")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(title = title, subtitle = "AutoLedger")
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
