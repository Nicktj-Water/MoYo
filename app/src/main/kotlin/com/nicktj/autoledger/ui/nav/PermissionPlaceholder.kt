package com.nicktj.autoledger.ui.nav

import androidx.compose.runtime.Composable
import com.nicktj.autoledger.ui.permission.OnboardingScreen
import com.nicktj.autoledger.ui.permission.SelfCheckScreen

/**
 * 二级页路由包装。
 *
 * `ui/permission` 下的 [OnboardingScreen] / [SelfCheckScreen] 由负责 service 侧的代理提供，
 * 这里只负责把「返回」「引导完成」映射成导航动作，让导航图保持在 ui/nav 里。
 *
 * 文件名沿用早期的占位页（PermissionPlaceholder.kt），内容已换成真实页面的接线。
 */
@Composable
fun PermissionSelfCheckRoute(onBack: () -> Unit) {
    SelfCheckScreen(onBack = onBack)
}

@Composable
fun PermissionOnboardingRoute(onFinished: () -> Unit) {
    OnboardingScreen(onFinished = onFinished)
}
