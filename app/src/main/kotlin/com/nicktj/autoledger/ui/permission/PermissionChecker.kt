package com.nicktj.autoledger.ui.permission

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nicktj.autoledger.service.accessibility.AutoLedgerAccessibilityService
import java.util.Locale

/**
 * 权限状态检查与跳转。
 *
 * 只做「读系统状态 + 发 Intent」，不持有 Context；页面通过 ViewModel 调用。
 */
object PermissionChecker {

    /** 一次性拿到所有权限项的状态。 */
    fun snapshot(context: Context): List<PermissionItemState> =
        PermissionKind.entries.map { kind -> PermissionItemState(kind = kind, status = status(context, kind)) }

    fun status(context: Context, kind: PermissionKind): PermissionStatus = when (kind) {
        PermissionKind.NOTIFICATION_LISTENER -> notificationListenerStatus(context)
        PermissionKind.ACCESSIBILITY -> accessibilityStatus(context)
        PermissionKind.POST_NOTIFICATIONS -> postNotificationStatus(context)
        PermissionKind.AUTOSTART -> PermissionStatus.Unknown("系统不提供查询接口，请在设置里确认已允许自启动")
        PermissionKind.BATTERY_UNRESTRICTED -> batteryStatus(context)
        PermissionKind.RECENTS_LOCK -> PermissionStatus.Unknown("系统不提供查询接口，请在最近任务里手动加锁")
    }

    /** 一键跳到对应设置页；跳转失败时回退到应用详情页。 */
    fun open(context: Context, kind: PermissionKind) {
        when (kind) {
            PermissionKind.NOTIFICATION_LISTENER ->
                launch(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

            PermissionKind.ACCESSIBILITY ->
                launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

            PermissionKind.POST_NOTIFICATIONS -> openNotificationSettings(context)
            PermissionKind.AUTOSTART -> openAutostart(context)
            PermissionKind.BATTERY_UNRESTRICTED -> openBatteryOptimization(context)
            PermissionKind.RECENTS_LOCK -> openRecents(context)
        }
    }

    /** 通知使用权（已授权的监听包名里有没有自己）。 */
    private fun notificationListenerStatus(context: Context): PermissionStatus = runCatching {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabled.contains(context.packageName)) PermissionStatus.Granted else PermissionStatus.Denied
    }.getOrElse {
        PermissionStatus.Unknown("读取通知使用权失败：${it.message.orEmpty()}")
    }

    /** 无障碍：在 ENABLED_ACCESSIBILITY_SERVICES 里找自己的组件名。 */
    private fun accessibilityStatus(context: Context): PermissionStatus = runCatching {
        val enabled = Settings.Secure
            .getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
        if (enabled.isBlank()) {
            PermissionStatus.Denied
        } else {
            val target = ComponentName(context, AutoLedgerAccessibilityService::class.java)
            val candidates = setOf(target.flattenToString(), target.flattenToShortString())
                .map { it.lowercase(Locale.ROOT) }
            val matched = enabled.split(':').any { it.lowercase(Locale.ROOT) in candidates }
            if (matched) PermissionStatus.Granted else PermissionStatus.Denied
        }
    }.getOrElse {
        PermissionStatus.Unknown("读取无障碍设置失败：${it.message.orEmpty()}")
    }

    /** 通知权限：Android 13+ 看运行时权限，低版本看通知总开关。 */
    private fun postNotificationStatus(context: Context): PermissionStatus {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return PermissionStatus.Denied
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return PermissionStatus.Denied
        }
        return PermissionStatus.Granted
    }

    /** 省电策略：是否已把本应用加入「无限制」。 */
    private fun batteryStatus(context: Context): PermissionStatus = runCatching {
        val powerManager = context.getSystemService(PowerManager::class.java)
            ?: return@runCatching PermissionStatus.Unknown("无法读取电源管理服务")
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            PermissionStatus.Granted
        } else {
            PermissionStatus.Denied
        }
    }.getOrElse {
        PermissionStatus.Unknown("读取省电策略失败：${it.message.orEmpty()}")
    }

    private fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!launch(context, intent)) openAppDetails(context)
    }

    /** 小米的自启动页；没有该 Activity 的系统回退到应用详情页。 */
    private fun openAutostart(context: Context) {
        val miui = Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        if (launch(context, miui)) return
        openAppDetails(context)
    }

    /** 直接申请「忽略电池优化」；系统不支持时打开优化列表页。 */
    private fun openBatteryOptimization(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        if (launch(context, direct)) return
        if (launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) return
        openAppDetails(context)
    }

    /**
     * 打开最近任务。系统没有公开 API，按 ROM 的隐式 Action 依次尝试，
     * 都失败就回桌面，让用户自己从最近任务里加锁。
     */
    private fun openRecents(context: Context) {
        if (launch(context, Intent("com.android.systemui.recents.SHOW_RECENTS"))) return
        if (launch(context, Intent("android.intent.action.SHOW_RECENTS"))) return
        launch(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
    }

    private fun openAppDetails(context: Context) {
        launch(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    /** 起 Activity，失败返回 false（调用方决定回退策略）。 */
    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (exception: Exception) {
        false
    }
}

/**
 * 首次启动引导是否完成。
 *
 * 只记录一个布尔值，不参与数据清理；MainActivity 可以据此决定是否进引导页。
 */
object OnboardingStore {

    private const val PREFS_NAME = "autoledger_onboarding"
    private const val KEY_COMPLETED = "completed"

    fun hasCompleted(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETED, false)

    fun markCompleted(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }
}
