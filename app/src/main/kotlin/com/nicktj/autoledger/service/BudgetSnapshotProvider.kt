package com.nicktj.autoledger.service

import com.nicktj.autoledger.core.data.dao.BudgetDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import com.nicktj.autoledger.core.domain.BudgetDef
import com.nicktj.autoledger.core.domain.BudgetEngine
import com.nicktj.autoledger.core.domain.BudgetLevel
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.service.recognition.toDomainTxn
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 某一时刻的收支/预算快照，提醒通知和桌面组件共用。
 *
 * @param budgetCents 本月总预算（分），0 表示没设置总预算
 * @param remainingCents 预算剩余（分），可能为负
 * @param usedRatio 预算使用比例，未设预算时为 0.0
 */
data class BudgetSnapshot(
    val todaySpentCents: Long,
    val monthSpentCents: Long,
    val budgetCents: Long,
    val remainingCents: Long,
    val usedRatio: Double,
    val level: BudgetLevel,
    val yearMonth: String,
)

/**
 * 读库生成 [BudgetSnapshot]。
 *
 * 只统计 CONFIRMED 的账单，退款通过原账单的 refundedCents 冲抵，
 * 与统计页保持一致（复用 [BudgetEngine]）。
 */
@Singleton
class BudgetSnapshotProvider @Inject constructor(
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val settingsRepository: SettingsRepository,
) {

    private val budgetEngine = BudgetEngine()
    private val zone: ZoneId = ZoneId.of(BudgetEngine.DEFAULT_ZONE_ID)

    suspend fun snapshot(now: Long = System.currentTimeMillis()): BudgetSnapshot {
        val nowTime = Instant.ofEpochMilli(now).atZone(zone)
        val monthStart = nowTime.toLocalDate().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayStart = nowTime.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val yearMonth = YearMonth.from(nowTime).toString()

        val countRedPacket = settingsRepository.countRedPacket.first()
        val confirmed = transactionDao.findBetween(monthStart, now)
            .filter { it.status == TxnStatus.CONFIRMED.name }
            .map { it.toDomainTxn() }

        val budgets = budgetDao.findForMonth(yearMonth).map {
            BudgetDef(categoryId = it.categoryId, amountCents = it.amountCents)
        }
        val status = budgetEngine.compute(budgets, confirmed, countRedPacket, yearMonth)

        val todaySpentCents = confirmed.asSequence()
            .filter { it.occurredAt >= todayStart }
            .filter { isSpending(it.type, countRedPacket) }
            .sumOf { effectiveSpentCents(it.amountCents, it.refundedCents) }

        return BudgetSnapshot(
            todaySpentCents = todaySpentCents,
            monthSpentCents = status.totalSpentCents,
            budgetCents = status.totalBudgetCents,
            remainingCents = status.totalRemainingCents,
            usedRatio = status.totalUsedRatio,
            level = status.level,
            yearMonth = yearMonth,
        )
    }

    /** 支出：EXPENSE，以及设置开启时的发出红包。 */
    private fun isSpending(type: TxnType, countRedPacket: Boolean): Boolean = when (type) {
        TxnType.EXPENSE -> true
        TxnType.RED_PACKET_OUT -> countRedPacket
        else -> false
    }

    /** 已退款冲抵的部分要从支出里扣掉，且不会扣成负数。 */
    private fun effectiveSpentCents(amountCents: Long, refundedCents: Long): Long =
        (amountCents - refundedCents.coerceIn(0L, amountCents)).coerceAtLeast(0L)
}
