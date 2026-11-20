package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.TxnType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 预算定义：categoryId 为 null 表示「月总预算」。 */
data class BudgetDef(
    val categoryId: Long?,
    val amountCents: Long,
)

/** 单个分类的预算执行情况。 */
data class CategoryBudgetStatus(
    val budgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    /** spent / budget，保留原始比例（可能大于 1）；预算为 0 时返回 0.0。 */
    val usedRatio: Double,
)

/** 预算使用程度。 */
enum class BudgetLevel {
    /** 使用比例 < 0.8 */
    NORMAL,

    /** 0.8 <= 比例 < 1.0 */
    WARNING,

    /** 比例 >= 1.0（严格：正好 1.0 也算超支） */
    OVER,
}

/** 某个月的预算总览。 */
data class BudgetStatus(
    val totalBudgetCents: Long,
    val totalSpentCents: Long,
    val totalRemainingCents: Long,
    /** spent / budget，保留原始比例（可能大于 1）；没有总预算时返回 0.0。 */
    val totalUsedRatio: Double,
    /** categoryId -> 该分类的执行情况，只包含配置了预算的分类。 */
    val byCategory: Map<Long, CategoryBudgetStatus>,
    val level: BudgetLevel,
)

/**
 * 预算计算。
 *
 * 计入支出的类型：EXPENSE，以及 [compute] 的 `countRedPacket = true` 时的 RED_PACKET_OUT。
 * 每笔支出的可计入金额 = amountCents - refundedCents（已退款冲抵的部分要扣掉，且不会扣成负数）。
 * 收入、退款本身不直接计入支出（退款通过原支出的 refundedCents 冲抵）。
 *
 * 月份按 [zone]（默认 Asia/Shanghai）的本地时间切分，`yearMonth` 形如 "2026-11"。
 */
class BudgetEngine(
    private val zone: ZoneId = ZoneId.of(DEFAULT_ZONE_ID),
) {

    /**
     * 计算指定月份的预算执行情况。
     *
     * @param budgets 预算定义列表；多条总预算会相加，同一分类的多条预算也会相加
     * @param txns 候选账单（可以不按月份过滤，这里会自己筛）
     * @param countRedPacket 是否把发出的红包计入支出
     * @param yearMonth 目标月份，形如 "2026-11"；格式非法会抛 DateTimeParseException
     */
    fun compute(
        budgets: List<BudgetDef>,
        txns: List<DomainTxn>,
        countRedPacket: Boolean = false,
        yearMonth: String,
    ): BudgetStatus {
        val targetMonth = YearMonth.parse(yearMonth)

        val totalBudgetCents = budgets
            .filter { it.categoryId == null }
            .sumOf { it.amountCents }

        val categoryBudgets: Map<Long, Long> = budgets
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, defs) -> defs.sumOf { it.amountCents } }

        var totalSpentCents = 0L
        val spentByCategory = mutableMapOf<Long, Long>()

        for (txn in txns) {
            if (!isInMonth(txn.occurredAt, targetMonth)) continue
            if (!isSpendingType(txn.type, countRedPacket)) continue

            val spent = effectiveSpentCents(txn)
            if (spent <= 0L) continue

            totalSpentCents += spent
            val categoryId = txn.categoryId
            if (categoryId != null) {
                spentByCategory[categoryId] = (spentByCategory[categoryId] ?: 0L) + spent
            }
        }

        val byCategory: Map<Long, CategoryBudgetStatus> = categoryBudgets.entries
            .sortedBy { it.key }
            .associate { (categoryId, budgetCents) ->
                val spentCents = spentByCategory[categoryId] ?: 0L
                categoryId to CategoryBudgetStatus(
                    budgetCents = budgetCents,
                    spentCents = spentCents,
                    remainingCents = budgetCents - spentCents,
                    usedRatio = ratioOf(spentCents, budgetCents),
                )
            }

        val totalUsedRatio = ratioOf(totalSpentCents, totalBudgetCents)
        return BudgetStatus(
            totalBudgetCents = totalBudgetCents,
            totalSpentCents = totalSpentCents,
            totalRemainingCents = totalBudgetCents - totalSpentCents,
            totalUsedRatio = totalUsedRatio,
            byCategory = byCategory,
            level = levelOf(totalUsedRatio),
        )
    }

    /** 支出按已退款冲抵金额扣减，且不会小于 0。 */
    private fun effectiveSpentCents(txn: DomainTxn): Long {
        val refunded = txn.refundedCents.coerceIn(0L, txn.amountCents)
        return txn.amountCents - refunded
    }

    /** 是否计入支出的类型。 */
    private fun isSpendingType(type: TxnType, countRedPacket: Boolean): Boolean = when (type) {
        TxnType.EXPENSE -> true
        TxnType.RED_PACKET_OUT -> countRedPacket
        else -> false
    }

    /** 按本地时区判断是否落在目标月份。 */
    private fun isInMonth(occurredAt: Long, targetMonth: YearMonth): Boolean {
        val month = YearMonth.from(Instant.ofEpochMilli(occurredAt).atZone(zone))
        return month == targetMonth
    }

    /** 预算为 0（或为负）时返回 0.0，避免除零。 */
    private fun ratioOf(spentCents: Long, budgetCents: Long): Double =
        if (budgetCents <= 0L) 0.0 else spentCents.toDouble() / budgetCents.toDouble()

    private fun levelOf(ratio: Double): BudgetLevel = when {
        ratio >= OVER_RATIO -> BudgetLevel.OVER
        ratio >= WARNING_RATIO -> BudgetLevel.WARNING
        else -> BudgetLevel.NORMAL
    }

    companion object {
        /** 默认时区：预算按国内本地月切分。 */
        const val DEFAULT_ZONE_ID: String = "Asia/Shanghai"

        /** 使用比例达到该值进入 WARNING。 */
        const val WARNING_RATIO: Double = 0.8

        /** 使用比例达到该值进入 OVER（严格：1.0 也算超支）。 */
        const val OVER_RATIO: Double = 1.0
    }
}
