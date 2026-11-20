package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetEngineTest {

    private val engine = BudgetEngine()
    private val month = "2026-11"

    private fun totalBudget(amountCents: Long): BudgetDef =
        BudgetDef(categoryId = null, amountCents = amountCents)

    private fun categoryBudget(categoryId: Long, amountCents: Long): BudgetDef =
        BudgetDef(categoryId = categoryId, amountCents = amountCents)

    private fun spent(
        amountCents: Long,
        categoryId: Long? = null,
        occurredAt: Long = BASE_TIME,
    ): DomainTxn = txn(
        id = null,
        amountCents = amountCents,
        type = TxnType.EXPENSE,
        categoryId = categoryId,
        occurredAt = occurredAt,
    )

    @Test
    fun `总预算与总支出正确汇总`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(30_000L), spent(20_000L)),
            yearMonth = month,
        )

        assertEquals(100_000L, result.totalBudgetCents)
        assertEquals(50_000L, result.totalSpentCents)
        assertEquals(50_000L, result.totalRemainingCents)
        assertEquals(0.5, result.totalUsedRatio, 1e-9)
        assertEquals(BudgetLevel.NORMAL, result.level)
    }

    @Test
    fun `分类预算分别统计`() {
        val result = engine.compute(
            budgets = listOf(
                totalBudget(100_000L),
                categoryBudget(Cats.DINING, 20_000L),
                categoryBudget(Cats.TRANSPORT, 10_000L),
            ),
            txns = listOf(
                spent(30_000L, Cats.DINING),
                spent(5_000L, Cats.TRANSPORT),
                spent(15_000L),
            ),
            yearMonth = month,
        )

        assertEquals(50_000L, result.totalSpentCents)

        val dining = result.byCategory.getValue(Cats.DINING)
        assertEquals(20_000L, dining.budgetCents)
        assertEquals(30_000L, dining.spentCents)
        assertEquals(-10_000L, dining.remainingCents)
        assertEquals(1.5, dining.usedRatio, 1e-9)

        val transport = result.byCategory.getValue(Cats.TRANSPORT)
        assertEquals(10_000L, transport.budgetCents)
        assertEquals(5_000L, transport.spentCents)
        assertEquals(5_000L, transport.remainingCents)
        assertEquals(0.5, transport.usedRatio, 1e-9)
    }

    @Test
    fun `没有预算的分类不进入分类汇总`() {
        val result = engine.compute(
            budgets = listOf(categoryBudget(Cats.DINING, 10_000L)),
            txns = listOf(spent(5_000L, Cats.DINING), spent(7_000L, Cats.MEDICAL)),
            yearMonth = month,
        )

        assertEquals(setOf(Cats.DINING), result.byCategory.keys)
        assertEquals(12_000L, result.totalSpentCents)
    }

    @Test
    fun `红包默认不计入支出`() {
        val redPacket = txn(amountCents = 10_000L, type = TxnType.RED_PACKET_OUT)

        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(5_000L), redPacket),
            yearMonth = month,
        )

        assertEquals(5_000L, result.totalSpentCents)
    }

    @Test
    fun `开启红包统计后计入支出`() {
        val redPacket = txn(amountCents = 10_000L, type = TxnType.RED_PACKET_OUT)

        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(5_000L), redPacket),
            countRedPacket = true,
            yearMonth = month,
        )

        assertEquals(15_000L, result.totalSpentCents)
    }

    @Test
    fun `退款冲抵部分从支出中扣减`() {
        val refunded = spent(10_000L).copy(refundedCents = 4_000L)

        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(refunded),
            yearMonth = month,
        )

        assertEquals(6_000L, result.totalSpentCents)
    }

    @Test
    fun `退款冲抵不会把支出扣成负数`() {
        val overRefunded = spent(10_000L).copy(refundedCents = 20_000L)

        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(overRefunded),
            yearMonth = month,
        )

        assertEquals(0L, result.totalSpentCents)
    }

    @Test
    fun `退款与收入类型不会直接计入支出`() {
        val refund = txn(amountCents = 5_000L, type = TxnType.REFUND)
        val income = txn(amountCents = 8_000L, type = TxnType.INCOME)

        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(refund, income),
            yearMonth = month,
        )

        assertEquals(0L, result.totalSpentCents)
    }

    @Test
    fun `比例达到 0_8 时进入警告`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(80_000L)),
            yearMonth = month,
        )

        assertEquals(0.8, result.totalUsedRatio, 1e-9)
        assertEquals(BudgetLevel.WARNING, result.level)
    }

    @Test
    fun `比例略低于 0_8 时仍然正常`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(79_000L)),
            yearMonth = month,
        )

        assertEquals(BudgetLevel.NORMAL, result.level)
    }

    @Test
    fun `比例正好 1_0 时算超支`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(100_000L)),
            yearMonth = month,
        )

        assertEquals(1.0, result.totalUsedRatio, 1e-9)
        assertEquals(BudgetLevel.OVER, result.level)
        assertEquals(0L, result.totalRemainingCents)
    }

    @Test
    fun `超支比例保留原始值且不截断`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(150_000L)),
            yearMonth = month,
        )

        assertEquals(1.5, result.totalUsedRatio, 1e-9)
        assertEquals(-50_000L, result.totalRemainingCents)
        assertEquals(BudgetLevel.OVER, result.level)
    }

    @Test
    fun `没有总预算时比例为 0 且不除零`() {
        val result = engine.compute(
            budgets = emptyList(),
            txns = listOf(spent(5_000L)),
            yearMonth = month,
        )

        assertEquals(0L, result.totalBudgetCents)
        assertEquals(5_000L, result.totalSpentCents)
        assertEquals(-5_000L, result.totalRemainingCents)
        assertEquals(0.0, result.totalUsedRatio, 1e-9)
        assertEquals(BudgetLevel.NORMAL, result.level)
    }

    @Test
    fun `分类预算为 0 时比例返回 0`() {
        val result = engine.compute(
            budgets = listOf(categoryBudget(Cats.DINING, 0L)),
            txns = listOf(spent(5_000L, Cats.DINING)),
            yearMonth = month,
        )

        val dining = result.byCategory.getValue(Cats.DINING)
        assertEquals(0L, dining.budgetCents)
        assertEquals(5_000L, dining.spentCents)
        assertEquals(-5_000L, dining.remainingCents)
        assertEquals(0.0, dining.usedRatio, 1e-9)
    }

    @Test
    fun `空账单时分类预算仍然展示且支出为 0`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L), categoryBudget(Cats.DINING, 20_000L)),
            txns = emptyList(),
            yearMonth = month,
        )

        assertEquals(0L, result.totalSpentCents)
        assertEquals(0.0, result.totalUsedRatio, 1e-9)

        val dining = result.byCategory.getValue(Cats.DINING)
        assertEquals(20_000L, dining.budgetCents)
        assertEquals(0L, dining.spentCents)
        assertEquals(20_000L, dining.remainingCents)
        assertEquals(0.0, dining.usedRatio, 1e-9)
    }

    @Test
    fun `多条总预算相加`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(30_000L), totalBudget(20_000L)),
            txns = emptyList(),
            yearMonth = month,
        )

        assertEquals(50_000L, result.totalBudgetCents)
    }

    @Test
    fun `同一分类的多条预算相加`() {
        val result = engine.compute(
            budgets = listOf(categoryBudget(Cats.DINING, 10_000L), categoryBudget(Cats.DINING, 5_000L)),
            txns = listOf(spent(9_000L, Cats.DINING)),
            yearMonth = month,
        )

        val dining = result.byCategory.getValue(Cats.DINING)
        assertEquals(15_000L, dining.budgetCents)
        assertEquals(6_000L, dining.remainingCents)
        assertEquals(0.6, dining.usedRatio, 1e-9)
    }

    @Test
    fun `月初边界按本地时区计入`() {
        val firstSecond = at(2026, 11, 1, 0, 0, 0)
        val lastSecondOfPrevMonth = at(2026, 10, 31, 23, 59, 59)

        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(
                spent(1_000L, occurredAt = firstSecond),
                spent(9_000L, occurredAt = lastSecondOfPrevMonth),
            ),
            yearMonth = month,
        )

        assertEquals(1_000L, result.totalSpentCents)
    }

    @Test
    fun `其他月份的账单不计入目标月份`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(5_000L, occurredAt = at(2026, 10, 20))),
            yearMonth = month,
        )

        assertEquals(0L, result.totalSpentCents)
    }

    @Test
    fun `查询未来月份时没有数据`() {
        val result = engine.compute(
            budgets = listOf(totalBudget(100_000L)),
            txns = listOf(spent(5_000L)),
            yearMonth = "2026-12",
        )

        assertEquals(0L, result.totalSpentCents)
        assertEquals(0.0, result.totalUsedRatio, 1e-9)
    }

    @Test
    fun `分类预算也会扣减退款冲抵`() {
        val refunded = spent(10_000L, Cats.DINING).copy(refundedCents = 3_000L)

        val result = engine.compute(
            budgets = listOf(categoryBudget(Cats.DINING, 10_000L)),
            txns = listOf(refunded),
            yearMonth = month,
        )

        val dining = result.byCategory.getValue(Cats.DINING)
        assertEquals(7_000L, dining.spentCents)
        assertEquals(3_000L, dining.remainingCents)
        assertEquals(0.7, dining.usedRatio, 1e-9)
    }
}
