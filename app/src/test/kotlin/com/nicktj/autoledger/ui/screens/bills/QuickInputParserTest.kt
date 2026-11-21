package com.nicktj.autoledger.ui.screens.bills

import com.nicktj.autoledger.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** QuickInputParser 的单元测试：只覆盖纯 Kotlin 解析逻辑。 */
class QuickInputParserTest {

    private fun parse(input: String): QuickInput =
        requireNotNull(QuickInputParser.parse(input)) { "解析失败：$input" }

    @Test
    fun parseMerchantAndIntegerAmount() {
        val result = parse("麦当劳 28")
        assertEquals(2800L, result.amountCents)
        assertEquals(TxnType.EXPENSE, result.type)
        assertEquals("麦当劳", result.merchant)
        assertNull(result.categoryHint)
    }

    @Test
    fun parseDecimalAmountWithYuanUnit() {
        val result = parse("打车 35.5元")
        assertEquals(3550L, result.amountCents)
        assertEquals(TxnType.EXPENSE, result.type)
        assertEquals("打车", result.merchant)
    }

    @Test
    fun parseIncomeKeyword() {
        val result = parse("工资 8000 收入")
        assertEquals(800000L, result.amountCents)
        assertEquals(TxnType.INCOME, result.type)
    }

    @Test
    fun parseCategoryHintWithCurrencyPrefix() {
        val result = parse("午饭 ￥32.00 餐饮")
        assertEquals(3200L, result.amountCents)
        assertEquals(TxnType.EXPENSE, result.type)
        assertEquals("餐饮", result.categoryHint)
    }

    @Test
    fun parseThousandSeparator() {
        val result = parse("1,200 房租")
        assertEquals(120000L, result.amountCents)
        assertEquals("房租", result.merchant)
    }

    @Test
    fun parseRedPacketIn() {
        val result = parse("收到红包 66")
        assertEquals(6600L, result.amountCents)
        assertEquals(TxnType.RED_PACKET_IN, result.type)
    }

    @Test
    fun parseRedPacketOut() {
        val result = parse("发红包 8.8")
        assertEquals(880L, result.amountCents)
        assertEquals(TxnType.RED_PACKET_OUT, result.type)
    }

    @Test
    fun parseEmptyInputReturnsNull() {
        assertNull(QuickInputParser.parse(""))
        assertNull(QuickInputParser.parse("   "))
    }

    @Test
    fun parseWithoutDigitsReturnsNull() {
        assertNull(QuickInputParser.parse("没有数字"))
    }

    @Test
    fun parseAmountOnlyHasNoMerchant() {
        val result = parse("28")
        assertEquals(2800L, result.amountCents)
        assertNull(result.merchant)
        assertNull(result.categoryHint)
    }

    @Test
    fun parseTooManyDecimalsReturnsNull() {
        assertNull(QuickInputParser.parse("12.345"))
    }

    @Test
    fun parseZeroAmountReturnsNull() {
        assertNull(QuickInputParser.parse("0"))
        assertNull(QuickInputParser.parse("0.00 早饭"))
    }

    @Test
    fun parseTrailingZeroDecimals() {
        val result = parse("28.50")
        assertEquals(2850L, result.amountCents)
    }
}
