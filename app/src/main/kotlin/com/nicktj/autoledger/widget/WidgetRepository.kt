package com.nicktj.autoledger.widget

import android.content.Context
import com.nicktj.autoledger.service.BudgetSnapshotProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** 小组件在 Compose 外拿依赖的入口（Glance 里不能用 @AndroidEntryPoint）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun budgetSnapshotProvider(): BudgetSnapshotProvider
}

/** 小组件要展示的最小数据集合。 */
data class WidgetSnapshot(
    val todaySpentCents: Long,
    val monthSpentCents: Long,
    val budgetCents: Long,
    val remainingCents: Long,
    val usedRatio: Double,
)

/**
 * 小组件数据仓库：通过 [WidgetEntryPoint] 拿 Hilt 依赖，读 DAO 现算快照。
 *
 * 任何异常都返回 null，让组件显示占位符而不是崩掉桌面。
 */
class WidgetRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun load(): WidgetSnapshot? = runCatching {
        val provider = EntryPointAccessors
            .fromApplication(appContext, WidgetEntryPoint::class.java)
            .budgetSnapshotProvider()
        val snapshot = provider.snapshot()
        WidgetSnapshot(
            todaySpentCents = snapshot.todaySpentCents,
            monthSpentCents = snapshot.monthSpentCents,
            budgetCents = snapshot.budgetCents,
            remainingCents = snapshot.remainingCents,
            usedRatio = snapshot.usedRatio,
        )
    }.getOrNull()
}
