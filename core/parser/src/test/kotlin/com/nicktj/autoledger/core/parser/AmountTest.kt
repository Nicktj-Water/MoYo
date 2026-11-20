package com.nicktj.autoledger.core.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 金额换算与金额正则的纯函数测试。 */
class AmountTest {

    @Test
    fun `带货币符号与元后缀的金额`() {
        assertEquals(1_234L, toCents("¥12.34"))
        assertEquals(123_456L, toCents("￥1,234.56"))
        assertEquals(100L, toCents("1.00元"))
        assertEquals(1L, toCents("0.01元"))
        assertEquals(123_456L, toCents("1,234.56"))
    }

    @Test
    fun `全角数字与全角逗号`() {
        assertEquals(1_234L, toCents("１２.３４"))
        assertEquals(123_456L, toCents("￥１，２３４．５６"))
        assertEquals(1_234L, toCents("１２．３４"))
    }

    @Test
    fun `尾随空格和换行会被清掉`() {
        assertEquals(16_100L, toCents(" ￥161.00 \n"))
        assertEquals(16_100L, toCents("161.00元 "))
    }

    @Test
    fun `整数元与零`() {
        assertEquals(8_800L, toCents("88"))
        assertEquals(0L, toCents("0"))
        assertEquals(0L, toCents("0.00元"))
    }

    @Test
    fun `非法金额返回 null`() {
        assertNull(toCents(""))
        assertNull(toCents("abc"))
        assertNull(toCents("12.345"))
        assertNull(toCents("-1.00"))
        assertNull(toCents("12..3"))
        assertNull(toCents("¥"))
    }

    @Test
    fun `默认金额正则覆盖五种写法`() {
        val regex = AlipayRules.default.amountRegex
        assertNotNull(regex.find("¥12.34"))
        assertNotNull(regex.find("￥1,234.56"))
        assertNotNull(regex.find("12.34元"))
        assertNotNull(regex.find("0.01元"))
        assertNotNull(regex.find("1,234.56"))
    }

    @Test
    fun `金额正则不会把一笔钱里的 1 当金额`() {
        val hit = AlipayRules.default.findAmount("李四已成功向你转了1笔钱")
        assertNull(hit)
    }

    @Test
    fun `三位小数不会被截断成金额`() {
        assertNull(AlipayRules.default.amountRegex.find("12.345"))
        assertNull(AlipayRules.default.findAmount("付款成功 12.345"))
    }

    @Test
    fun `优先取带货币符号的金额而不是优惠行`() {
        val hit = UnionpayRules.default.findAmount("交易成功 ¥1,234.56 优惠 0.50元")
        assertNotNull(hit)
        val amount = hit!!
        assertEquals(123_456L, amount.cents)
        assertTrue(amount.anchored)
    }

    @Test
    fun `裸金额可以被识别但标记为不可信`() {
        val hit = AlipayRules.default.findAmount("付款成功 12.34")
        assertNotNull(hit)
        val amount = hit!!
        assertEquals(1_234L, amount.cents)
        assertFalse(amount.anchored)
    }

    @Test
    fun `全角金额先规范化再匹配`() {
        val hit = AlipayRules.default.findAmount("付款成功￥１２．３４")
        assertNotNull(hit)
        val amount = hit!!
        assertEquals(1_234L, amount.cents)
        assertTrue(amount.anchored)
    }
}
