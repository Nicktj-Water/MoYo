package com.nicktj.autoledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nicktj.autoledger.ui.nav.BOTTOM_TABS
import com.nicktj.autoledger.ui.nav.PermissionOnboardingRoute
import com.nicktj.autoledger.ui.nav.PermissionSelfCheckRoute
import com.nicktj.autoledger.ui.nav.Routes
import com.nicktj.autoledger.ui.permission.OnboardingStore
import com.nicktj.autoledger.ui.screens.bills.BillsScreen
import com.nicktj.autoledger.ui.screens.budget.BudgetScreen
import com.nicktj.autoledger.ui.screens.logs.LogsScreen
import com.nicktj.autoledger.ui.screens.pending.PendingScreen
import com.nicktj.autoledger.ui.screens.settings.SettingsScreen
import com.nicktj.autoledger.ui.screens.stats.StatsScreen

/**
 * 应用骨架：Scaffold + 5 项底部导航 + NavHost。
 *
 * 识别日志和权限自检是二级页，进入时收起底部导航（保持内容整屏）。
 * 首次启动（引导未完成）会在导航图之上盖一层开启引导，完成后回到账单页。
 *
 * @param initialTxnId 通知点击带进来的账单 id，进入账单页后直接打开编辑弹层
 */
@Composable
fun AutoLedgerRoot(initialTxnId: Long? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current

    // 通知带进来的 txnId 只消费一次；activity 收到新 intent 时还能再触发。
    var pendingTxnId by remember { mutableStateOf(initialTxnId) }
    LaunchedEffect(initialTxnId) {
        if (initialTxnId != null) pendingTxnId = initialTxnId
    }

    // 首次启动先走引导；点通知进来时优先看账单，别拦路。
    var showOnboarding by remember {
        mutableStateOf(initialTxnId == null && !OnboardingStore.hasCompleted(context))
    }

    val showBottomBar = currentRoute == null || currentRoute in Routes.bottomRoutes

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(220)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180)),
                ) {
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
                                icon = {
                                    Icon(imageVector = tab.icon, contentDescription = null)
                                },
                                label = { Text(text = tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
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
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) {
                composable(Routes.BILLS) {
                    BillsScreen(
                        initialEditTxnId = pendingTxnId,
                        onTxnConsumed = { pendingTxnId = null },
                    )
                }
                composable(Routes.STATS) { StatsScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onNavigatePermission = { navController.navigate(Routes.PERMISSION) },
                        onNavigateLogs = { navController.navigate(Routes.LOGS) },
                        onNavigateOnboarding = { showOnboarding = true },
                        onNavigateBudget = { navController.navigate(Routes.BUDGET) },
                        onNavigatePending = { navController.navigate(Routes.PENDING) },
                    )
                }
                composable(Routes.PENDING) {
                    PendingScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.BUDGET) {
                    BudgetScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.LOGS) {
                    LogsScreen(onBack = { navController.popBackStack() })
                }
                // 权限与自检：真实页面由 ui/permission 提供。
                composable(Routes.PERMISSION) {
                    PermissionSelfCheckRoute(onBack = { navController.popBackStack() })
                }
            }
        }

        // 引导盖在导航图之上：完成后直接回到当前页，不折腾返回栈。
        if (showOnboarding) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                PermissionOnboardingRoute(onFinished = { showOnboarding = false })
            }
        }
    }
}
