package com.nicktj.autoledger.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ParsedTxnTest {

    @Test
    fun `合法账单保留所有字段`() {
        val txn = sample()

        assertEquals(1_234L, txn.amountCents)
        assertEquals(TxnType.EXPENSE, txn.type)
        assertEquals("星巴克", txn.merchant)
        assertEquals(PaySource.ALIPAY, txn.source)
        assertEquals(Channel.NOTIFICATION, txn.channel)
        assertEquals(0.9, txn.confidence, 0.0001)
    }

    @Test
    fun `置信度超出 0 到 1 会抛异常`() {
        assertThrows(IllegalArgumentException::class.java) { sample(confidence = 1.2) }
        assertThrows(IllegalArgumentException::class.java) { sample(confidence = -0.1) }
    }

    @Test
    fun `金额为负会抛异常`() {
        assertThrows(IllegalArgumentException::class.java) { sample(amountCents = -1L) }
    }

    @Test
    fun `包名能映射到支付来源`() {
        assertEquals(PaySource.ALIPAY, PaySource.fromPackageName("com.eg.android.AlipayGphone"))
        assertEquals(PaySource.WECHAT, PaySource.fromPackageName("com.tencent.mm"))
        assertEquals(PaySource.UNIONPAY, PaySource.fromPackageName("com.unionpay"))
        assertNull(PaySource.fromPackageName("com.example.unknown"))
        assertNull(PaySource.MANUAL.packageName)
    }

    private fun sample(
        amountCents: Long = 1_234L,
        confidence: Double = 0.9,
    ) = ParsedTxn(
        amountCents = amountCents,
        type = TxnType.EXPENSE,
        merchant = "星巴克",
        source = PaySource.ALIPAY,
        occurredAt = 1_700_000_000_000L,
        rawText = "支付宝付款 12.34 元",
        confidence = confidence,
        channel = Channel.NOTIFICATION,
    )
}
