package com.nicktj.autoledger.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** 今日支出小组件的系统入口，布局与元数据见 res/xml/today_spend_widget_info.xml。 */
class TodaySpendReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodaySpendWidget()
}
