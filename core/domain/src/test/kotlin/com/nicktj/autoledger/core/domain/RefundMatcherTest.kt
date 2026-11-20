package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefundMatcherTest {

    private val matcher = RefundMatcher()

    private val expense = txn(
        id = 7L,
        amountCents = 5_000L,
        merchant = "美团",
        occurredAt = BASE_TIME - 3 * DAY_MS,
    )

    private fun refundOf(
        amountCents: Long = 5_000L,
        merchant: String? = "美团",
        occurredAt: Long = BASE_TIME,
    ): DomainTxn = txn(
        id = null,
        amountCents = amountCents,
        type = TxnType.REFUND,
        merchant = merchant,
        occurredAt = occurredAt,
    )

    @Test
    fun `同额同商户在窗口内匹配成功`() {
        assertEquals(expense, matcher.findMatch(refundOf(), listOf(expense)))
    }

    @Test
    fun `恰好 30 天窗口边界可以匹配`() {
        val candidate = expense.copy(occurredAt = BASE_TIME - 30 * DAY_MS)

        assertEquals(candidate, matcher.findMatch(refundOf(), listOf(candidate)))
    }

    @Test
    fun `超过 30 天窗口不匹配`() {
        val candidate = expense.copy(occurredAt = BASE_TIME - 30 * DAY_MS - 1L)

        assertNull(matcher.findMatch(refundOf(), listOf(candidate)))
    }

    @Test
    fun `金额不同不匹配`() {
        assertNull(matcher.findMatch(refundOf(amountCents = 4_999L), listOf(expense)))
    }

    @Test
    fun `商户不同不匹配`() {
        assertNull(matcher.findMatch(refundOf(merchant = "星巴克"), listOf(expense)))
    }

    @Test
    fun `商户一方包含另一方视为匹配`() {
        val candidate = expense.copy(merchant = "美团外卖")

        assertEquals(candidate, matcher.findMatch(refundOf(merchant = "美团"), listOf(candidate)))
    }

    @Test
    fun `商户任一方为空不匹配`() {
        assertNull(matcher.findMatch(refundOf(merchant = null), listOf(expense)))
        assertNull(matcher.findMatch(refundOf(), listOf(expense.copy(merchant = null))))
    }

    @Test
    fun `非退款类型直接返回 null`() {
        assertNull(matcher.findMatch(expense.copy(type = TxnType.EXPENSE), listOf(expense)))
    }

    @Test
    fun `退款早于支出不匹配`() {
        val candidate = expense.copy(occurredAt = BASE_TIME + DAY_MS)

        assertNull(matcher.findMatch(refundOf(), listOf(candidate)))
    }

    @Test
    fun `候选必须是支出`() {
        val income = expense.copy(type = TxnType.INCOME)

        assertNull(matcher.findMatch(refundOf(), listOf(income)))
    }

    @Test
    fun `已完全冲抵的候选被跳过`() {
        val result = matcher.findMatch(
            refund = refundOf(),
            candidates = listOf(expense),
            partiallyRefunded = mapOf(7L to 5_000L),
        )

        assertNull(result)
    }

    @Test
    fun `部分冲抵后仍有余额可以再次匹配`() {
        val result = matcher.findMatch(
            refund = refundOf(),
            candidates = listOf(expense),
            partiallyRefunded = mapOf(7L to 2_000L),
        )

        assertEquals(expense, result)
    }

    @Test
    fun `账单字段里的已冲抵金额同样生效`() {
        val partially = expense.copy(refundedCents = 2_000L)

        assertEquals(partially, matcher.findMatch(refundOf(), listOf(partially)))
    }

    @Test
    fun `账单字段显示全额冲抵时跳过`() {
        val fully = expense.copy(refundedCents = 5_000L)

        assertNull(matcher.findMatch(refundOf(), listOf(fully)))
    }

    @Test
    fun `多笔候选取最近的一笔`() {
        val older = expense.copy(id = 1L, occurredAt = BASE_TIME - 20 * DAY_MS)
        val newer = expense.copy(id = 2L, occurredAt = BASE_TIME - 2 * DAY_MS)

        assertEquals(newer, matcher.findMatch(refundOf(), listOf(older, newer)))
    }

    @Test
    fun `窗口天数可配置`() {
        val sevenDays = RefundMatcher(windowDays = 7)
        val fiveDaysAgo = expense.copy(id = 8L, occurredAt = BASE_TIME - 5 * DAY_MS)
        val tenDaysAgo = expense.copy(id = 9L, occurredAt = BASE_TIME - 10 * DAY_MS)

        assertEquals(
            fiveDaysAgo,
            sevenDays.findMatch(refundOf(), listOf(fiveDaysAgo, tenDaysAgo)),
        )
    }

    @Test
    fun `剩余可冲抵金额计算正确`() {
        assertEquals(3_000L, matcher.remainingCents(expense, mapOf(7L to 2_000L)))
        assertEquals(5_000L, matcher.remainingCents(expense))
    }

    @Test
    fun `退款金额为 0 时返回 null`() {
        assertNull(matcher.findMatch(refundOf(amountCents = 0L), listOf(expense.copy(amountCents = 0L))))
    }
}
