package com.nicktj.autoledger.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nicktj.autoledger.MainActivity
import com.nicktj.autoledger.core.model.formatYuan
import kotlin.math.roundToInt

/**
 * 今日支出小组件（4x2）。
 *
 * Subnook 配色：浅色 #F4F5F9 底 / #4F46E5 强调，深色 #0B0C10 / #A5B4FC。
 * 数据来自 [WidgetRepository]，点击打开 App。
 */
class TodaySpendWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetRepository(context).load()
        provideContent { TodaySpendContent(snapshot) }
    }

    companion object {
        /** 入账后立即刷新，避免要等下一个 updatePeriod（30 分钟）。 */
        suspend fun refresh(context: Context) {
            runCatching { TodaySpendWidget().updateAll(context.applicationContext) }
        }
    }
}

@Composable
private fun TodaySpendContent(snapshot: WidgetSnapshot?) {
    val context = LocalContext.current
    val nightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val background = if (nightMode) Color(0xFF0B0C10) else Color(0xFFF4F5F9)
    val primaryText = if (nightMode) Color(0xFFE8EAEE) else Color(0xFF0F172A)
    val secondaryText = if (nightMode) Color(0xFF9AA1AD) else Color(0xFF6B7280)
    val accent = if (nightMode) Color(0xFFA5B4FC) else Color(0xFF4F46E5)
    val budgetColor = when {
        snapshot == null || snapshot.budgetCents <= 0L -> secondaryText
        snapshot.usedRatio >= 1.0 -> Color(0xFFE5484D)
        snapshot.usedRatio >= 0.8 -> Color(0xFFF59E0B)
        else -> secondaryText
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(background))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        Text(
            text = "今日支出",
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = snapshot?.let { formatYuan(it.todaySpentCents) } ?: "--",
            style = TextStyle(
                color = ColorProvider(primaryText),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = "本月 ${snapshot?.let { formatYuan(it.monthSpentCents) } ?: "--"}",
                style = TextStyle(color = ColorProvider(secondaryText), fontSize = 11.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = budgetText(snapshot),
                style = TextStyle(
                    color = ColorProvider(budgetColor),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

/** 预算行文案。 */
private fun budgetText(snapshot: WidgetSnapshot?): String {
    if (snapshot == null) return "预算剩余 --"
    if (snapshot.budgetCents <= 0L) return "本月未设预算"
    val percent = (snapshot.usedRatio * 100).roundToInt().coerceAtLeast(0)
    return if (snapshot.remainingCents >= 0L) {
        "剩余 ${formatYuan(snapshot.remainingCents)} · $percent%"
    } else {
        "超支 ${formatYuan(-snapshot.remainingCents)}"
    }
}
