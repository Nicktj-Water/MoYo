package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DedupEngineTest {

    private val engine = DedupEngine()

    private val saved = txn(
        id = 42L,
        amountCents = 12_345L,
        merchant = "美团",
        occurredAt = BASE_TIME,
        confidence = 0.90,
    )

    @Test
    fun `同额同源且在窗口内合并到已有记录`() {
        val incoming = saved.copy(
            id = null,
            occurredAt = BASE_TIME + 89_000L,
            channel = Channel.ACCESSIBILITY,
        )

        val merged = mergeOf(engine.decide(incoming, listOf(saved))).merged

        assertEquals(42L, merged.id)
        assertEquals(12_345L, merged.amountCents)
        assertEquals(Channel.ACCESSIBILITY, merged.channel)
    }

    @Test
    fun `恰好 90 秒的窗口边界仍然合并`() {
        val incoming = saved.copy(
            id = null,
            occurredAt = BASE_TIME + 90_000L,
            channel = Channel.ACCESSIBILITY,
        )

        val merged = mergeOf(engine.decide(incoming, listOf(saved))).merged

        assertEquals(42L, merged.id)
    }

    @Test
    fun `超过 90 秒不再合并`() {
        val incoming = saved.copy(
            id = null,
            occurredAt = BASE_TIME + 90_001L,
            channel = Channel.ACCESSIBILITY,
        )

        assertEquals(DedupDecision.New, engine.decide(incoming, listOf(saved)))
    }

    @Test
    fun `不同支付来源不合并`() {
        val incoming = saved.copy(
            id = null,
            source = PaySource.WECHAT,
            channel = Channel.ACCESSIBILITY,
        )

        assertEquals(DedupDecision.New, engine.decide(incoming, listOf(saved)))
    }

    @Test
    fun `不同类型方向不合并`() {
        val incoming = saved.copy(
            id = null,
            type = TxnType.INCOME,
            channel = Channel.ACCESSIBILITY,
        )

        assertEquals(DedupDecision.New, engine.decide(incoming, listOf(saved)))
    }

    @Test
    fun `金额为 0 的入账不参与去重`() {
        val zeroIncoming = saved.copy(id = null, amountCents = 0L)
        val zeroExisting = saved.copy(amountCents = 0L)

        assertEquals(DedupDecision.New, engine.decide(zeroIncoming, listOf(zeroExisting)))
    }

    @Test
    fun `金额为 0 的已有记录不参与去重`() {
        val zeroExisting = txn(id = 1L, amountCents = 0L)

        assertEquals(DedupDecision.New, engine.decide(saved.copy(id = null), listOf(zeroExisting)))
    }

    @Test
    fun `容差内的金额差异可以合并`() {
        val tolerant = DedupEngine(amountToleranceCents = 1L)
        val incoming = saved.copy(
            id = null,
            amountCents = 12_346L,
            channel = Channel.ACCESSIBILITY,
        )

        val merged = mergeOf(tolerant.decide(incoming, listOf(saved))).merged

        assertEquals(12_345L, merged.amountCents)
    }

    @Test
    fun `超出容差的金额差异不合并`() {
        val tolerant = DedupEngine(amountToleranceCents = 5L)
        val incoming = saved.copy(
            id = null,
            amountCents = 12_351L,
            channel = Channel.ACCESSIBILITY,
        )

        assertEquals(DedupDecision.New, tolerant.decide(incoming, listOf(saved)))
    }

    @Test
    fun `信息完全相同的重复记录直接 Skip`() {
        val incoming = saved.copy(id = null)

        assertEquals(DedupDecision.Skip, engine.decide(incoming, listOf(saved)))
    }

    @Test
    fun `合并时用非空商户覆盖空商户`() {
        val existing = saved.copy(merchant = null)
        val incoming = saved.copy(
            id = null,
            merchant = "星巴克",
            channel = Channel.ACCESSIBILITY,
        )

        val merged = mergeOf(engine.decide(incoming, listOf(existing))).merged

        assertEquals("星巴克", merged.merchant)
        assertEquals(42L, merged.id)
    }

    @Test
    fun `合并时保留更长的商户名`() {
        val incoming = saved.copy(id = null, merchant = "美团外卖")

        val merged = mergeOf(engine.decide(incoming, listOf(saved))).merged

        assertEquals("美团外卖", merged.merchant)
    }

    @Test
    fun `合并时保留更长的原始文案`() {
        val existing = saved.copy(rawText = "支付宝")
        val incoming = saved.copy(id = null, rawText = "支付宝 支出 123.45 元")

        val merged = mergeOf(engine.decide(incoming, listOf(existing))).merged

        assertEquals("支付宝 支出 123.45 元", merged.rawText)
    }

    @Test
    fun `合并时保留更高的置信度`() {
        val existing = saved.copy(confidence = 0.60)
        val incoming = saved.copy(id = null, confidence = 0.98)

        val merged = mergeOf(engine.decide(incoming, listOf(existing))).merged

        assertEquals(0.98, merged.confidence, 1e-9)
    }

    @Test
    fun `无障碍通道覆盖通知通道`() {
        val existing = saved.copy(channel = Channel.NOTIFICATION)
        val incoming = saved.copy(
            id = null,
            channel = Channel.ACCESSIBILITY,
            confidence = 0.95,
        )

        val merged = mergeOf(engine.decide(incoming, listOf(existing))).merged

        assertEquals(Channel.ACCESSIBILITY, merged.channel)
        assertEquals(0.95, merged.confidence, 1e-9)
    }

    @Test
    fun `通知通道不会覆盖无障碍通道`() {
        val existing = saved.copy(channel = Channel.ACCESSIBILITY, confidence = 0.60)
        val incoming = saved.copy(id = null, channel = Channel.NOTIFICATION, confidence = 0.99)

        val merged = mergeOf(engine.decide(incoming, listOf(existing))).merged

        assertEquals(Channel.ACCESSIBILITY, merged.channel)
        assertEquals(0.99, merged.confidence, 1e-9)
    }

    @Test
    fun `合并时保留已有分类和已冲抵金额`() {
        val existing = saved.copy(
            categoryId = Cats.DINING,
            refundedCents = 300L,
            rawText = "支付宝 支出 123.45 元（含备注信息）",
        )
        val incoming = saved.copy(
            id = null,
            categoryId = Cats.MEDICAL,
            refundedCents = 0L,
            channel = Channel.ACCESSIBILITY,
        )

        val merged = mergeOf(engine.decide(incoming, listOf(existing))).merged

        assertEquals(Cats.DINING, merged.categoryId)
        assertEquals(300L, merged.refundedCents)
        assertEquals("支付宝 支出 123.45 元（含备注信息）", merged.rawText)
    }

    @Test
    fun `多个候选取时间最近的一笔`() {
        val far = saved.copy(id = 1L, occurredAt = BASE_TIME - 80_000L, merchant = "远")
        val near = saved.copy(id = 2L, occurredAt = BASE_TIME - 10_000L, merchant = "近")
        val incoming = saved.copy(
            id = null,
            occurredAt = BASE_TIME,
            channel = Channel.ACCESSIBILITY,
        )

        val merge = mergeOf(engine.decide(incoming, listOf(far, near)))

        assertEquals(2L, merge.existing.id)
    }

    @Test
    fun `未入库的两笔记录也能合并`() {
        val existing = saved.copy(id = null)
        val incoming = saved.copy(id = null, confidence = 0.95)

        val merged = mergeOf(engine.decide(incoming, listOf(existing))).merged

        assertNull(merged.id)
        assertEquals(0.95, merged.confidence, 1e-9)
    }

    @Test
    fun `自定义窗口按毫秒比较`() {
        val narrow = DedupEngine(windowMs = 1_000L)
        val incoming = saved.copy(
            id = null,
            occurredAt = BASE_TIME + 2_000L,
            channel = Channel.ACCESSIBILITY,
        )

        assertEquals(DedupDecision.New, narrow.decide(incoming, listOf(saved)))
    }

    @Test
    fun `窗口参数为负数时抛异常`() {
        val error = runCatching { DedupEngine(windowMs = -1L) }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
