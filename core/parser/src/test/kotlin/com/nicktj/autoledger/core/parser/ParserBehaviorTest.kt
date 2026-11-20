package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 针对性单测：营销过滤、聊天不误判、全角金额、千分位、无金额微信、红包方向、
 * 退款优先级、置信度分级、商户抽取、注册表分发等。
 */
class ParserBehaviorTest {

    private val alipay = AlipayParser()
    private val wechat = WechatParser()
    private val unionpay = UnionpayParser()
    private val occurredAt = 1_700_000_000_000L

    @Test
    fun `营销关键词优先于金额解析`() {
        assertEquals(
            ParseResult.Marketing,
            alipay.parse("支付宝", "双11狂欢开启，最高8888元红包等你领", null, occurredAt),
        )
        assertEquals(
            ParseResult.Marketing,
            wechat.parse("微信支付", "支付有优惠：领5元立减券", null, occurredAt),
        )
        assertEquals(
            ParseResult.Marketing,
            unionpay.parse("消息推送", "信用卡账单已出，本期应还1,234.56元", null, occurredAt),
        )
    }

    @Test
    fun `微信非白名单标题即使是支付文案也不入账`() {
        assertEquals(ParseResult.NotPayment, wechat.parse("张三", "已支付¥16.00", null, occurredAt))
        assertEquals(ParseResult.NotPayment, wechat.parse("微信支付助手", "已支付¥16.00", null, occurredAt))
        assertEquals(ParseResult.NotPayment, wechat.parse(null, "已支付¥16.00", null, occurredAt))
    }

    @Test
    fun `支付宝联系人聊天不误判`() {
        assertEquals(ParseResult.NotPayment, alipay.parse("张三", "在吗", null, occurredAt))
    }

    @Test
    fun `全角金额与全角数字`() {
        val match = requireMatch(alipay.parse("支付宝", "付款成功￥１２．３４", null, occurredAt))
        assertEquals(1_234L, match.txn.amountCents)
        assertEquals(TxnType.EXPENSE, match.txn.type)
        assertEquals(0.95, match.txn.confidence, 1e-9)
        assertFalse(match.needsAmount)
    }

    @Test
    fun `千分位金额取第一个带货币符号的值`() {
        val match = requireMatch(
            unionpay.parse("交易成功", "交易成功 ¥1,234.56 优惠 0.50元", null, occurredAt),
        )
        assertEquals(123_456L, match.txn.amountCents)
        assertEquals(TxnType.EXPENSE, match.txn.type)
        assertEquals("优惠行不能覆盖大字金额", 123_456L, match.txn.amountCents)
    }

    @Test
    fun `微信卡片通知没有金额时进入待补金额`() {
        val match = requireMatch(wechat.parse("微信支付", "微信支付", null, occurredAt))
        assertEquals(0L, match.txn.amountCents)
        assertEquals(TxnType.EXPENSE, match.txn.type)
        assertTrue(match.needsAmount)
        assertEquals(0.35, match.txn.confidence, 1e-9)
    }

    @Test
    fun `红包收入与发出方向`() {
        val incoming = requireMatch(alipay.parse("支付宝", "你收到一个红包，金额1.00元", null, occurredAt))
        assertEquals(TxnType.RED_PACKET_IN, incoming.txn.type)
        assertEquals(100L, incoming.txn.amountCents)

        val outgoing = requireMatch(wechat.parse("微信支付", "红包已发出¥8.88", null, occurredAt))
        assertEquals(TxnType.RED_PACKET_OUT, outgoing.txn.type)
        assertEquals(888L, outgoing.txn.amountCents)
    }

    @Test
    fun `退款优先于到账收入`() {
        val wechatRefund = requireMatch(wechat.parse("微信支付", "退款到账¥16.00", null, occurredAt))
        assertEquals(TxnType.REFUND, wechatRefund.txn.type)
        assertEquals(1_600L, wechatRefund.txn.amountCents)

        val alipayRefund = requireMatch(alipay.parse("支付宝", "退款到账￥16.00，已退回余额宝", null, occurredAt))
        assertEquals(TxnType.REFUND, alipayRefund.txn.type)
        assertEquals(1_600L, alipayRefund.txn.amountCents)
    }

    @Test
    fun `置信度分级`() {
        val high = requireMatch(alipay.parse("支付宝", "付款成功￥25.00", null, occurredAt))
        assertEquals(0.95, high.txn.confidence, 1e-9)
        assertFalse(high.needsAmount)

        val low = requireMatch(alipay.parse("支付宝", "李四已成功向你转了1笔钱", null, occurredAt))
        assertEquals(TxnType.INCOME, low.txn.type)
        assertEquals(0.35, low.txn.confidence, 1e-9)
        assertTrue(low.needsAmount)

        // 有金额但金额是裸写的（没有 ¥/元），格式可疑，只给 0.6。
        val suspicious = requireMatch(alipay.parse("支付宝", "付款成功 12.34", null, occurredAt))
        assertEquals(1_234L, suspicious.txn.amountCents)
        assertEquals(0.6, suspicious.txn.confidence, 1e-9)

        // 只有金额、没有类型关键词，按最低置信度支出处理。
        val guessed = requireMatch(alipay.parse("交易提醒", "¥25.00", null, occurredAt))
        assertEquals(TxnType.EXPENSE, guessed.txn.type)
        assertEquals(2_500L, guessed.txn.amountCents)
        assertEquals(0.6, guessed.txn.confidence, 1e-9)
    }

    @Test
    fun `云闪付入账与支付方向`() {
        val income = requireMatch(unionpay.parse("消息推送", "云闪付入账100.00元", null, occurredAt))
        assertEquals(TxnType.INCOME, income.txn.type)
        assertEquals(10_000L, income.txn.amountCents)

        val expense = requireMatch(unionpay.parse("消息推送", "云闪付支付成功，金额25.00元", null, occurredAt))
        assertEquals(TxnType.EXPENSE, expense.txn.type)
        assertEquals(2_500L, expense.txn.amountCents)
    }

    @Test
    fun `收款方标签不会被当成收入关键词`() {
        val match = requireMatch(alipay.parse("支付宝", "付款成功￥25.00，收款方：瑞幸咖啡", null, occurredAt))
        assertEquals(TxnType.EXPENSE, match.txn.type)
        assertEquals(2_500L, match.txn.amountCents)
        assertEquals("瑞幸咖啡", match.txn.merchant)
    }

    @Test
    fun `商户从标签或转账句式抽取`() {
        val page = requireMatch(
            wechat.parse(
                "支付成功",
                "支付成功 ¥16.00 当前状态 支付成功 商户全称 瑞幸咖啡",
                null,
                occurredAt,
            ),
        )
        assertEquals("瑞幸咖啡", page.txn.merchant)

        val transfer = requireMatch(unionpay.parse("消息推送", "转账成功，已向李四转账100.00元", null, occurredAt))
        assertEquals(TxnType.EXPENSE, transfer.txn.type)
        assertEquals("李四", transfer.txn.merchant)
    }

    @Test
    fun `向你付款不会被抽成商户`() {
        val match = requireMatch(alipay.parse("支付宝", "张三通过扫码向你付款1.00元", null, occurredAt))
        assertEquals(TxnType.INCOME, match.txn.type)
        assertEquals(100L, match.txn.amountCents)
        assertNull(match.txn.merchant)
    }

    @Test
    fun `聚合通知先剥掉条数前缀`() {
        val match = requireMatch(wechat.parse("微信支付", "[2条]微信支付：已支付¥16.00", null, occurredAt))
        assertEquals(TxnType.EXPENSE, match.txn.type)
        assertEquals(1_600L, match.txn.amountCents)
    }

    @Test
    fun `金额为零不生成账单`() {
        assertEquals(ParseResult.NotPayment, alipay.parse("支付宝", "付款成功￥0.00", null, occurredAt))
        assertEquals(ParseResult.NotPayment, wechat.parse("微信支付", "已支付¥0.00", null, occurredAt))
    }

    @Test
    fun `ParserRegistry 按包名分发`() {
        assertEquals(PaySource.ALIPAY, ParserRegistry.forPackage("com.eg.android.AlipayGphone")?.source)
        assertEquals(PaySource.WECHAT, ParserRegistry.forPackage("com.tencent.mm")?.source)
        assertEquals(PaySource.UNIONPAY, ParserRegistry.forPackage("com.unionpay")?.source)
        assertNull(ParserRegistry.forPackage("com.example.unknown"))
        assertEquals(3, ParserRegistry.parsers.size)
    }

    @Test
    fun `rawText 只拼接非空字段`() {
        val match = requireMatch(alipay.parse("支付宝", "付款成功￥1.00", "", occurredAt))
        assertEquals("支付宝|付款成功￥1.00", match.txn.rawText)
    }

    @Test
    fun `RuleSet 可以从外部覆盖`() {
        val custom = WechatRules.default.copy(
            sourceTitleAllowList = WechatRules.default.sourceTitleAllowList + "微信支付助手",
        )
        val parser = WechatParser(custom)
        val match = requireMatch(parser.parse("微信支付助手", "已支付¥1.00", null, occurredAt))
        assertEquals(TxnType.EXPENSE, match.txn.type)
        assertEquals(100L, match.txn.amountCents)
    }

    @Test
    fun `解析器默认输出通知通道且可以改成无障碍`() {
        val match = requireMatch(alipay.parse("支付宝", "付款成功￥25.00", null, occurredAt))
        assertEquals(Channel.NOTIFICATION, match.txn.channel)

        val accessibility = match.withChannel(Channel.ACCESSIBILITY) as ParseResult.Match
        assertEquals(Channel.ACCESSIBILITY, accessibility.txn.channel)
        assertEquals(match.txn.amountCents, accessibility.txn.amountCents)
    }

    private fun requireMatch(result: ParseResult): ParseResult.Match {
        assertTrue("期望 Match，实际是 $result", result is ParseResult.Match)
        return result as ParseResult.Match
    }
}
