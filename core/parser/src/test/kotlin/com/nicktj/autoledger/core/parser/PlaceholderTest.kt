package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.PaySource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 原占位测试文件，现改成最小契约测试：三个解析器必须各自绑定正确的支付来源。
 * 真正的行为覆盖见 [ParserBehaviorTest]、[SampleCorpusTest]、[AmountTest]。
 */
class PlaceholderTest {

    @Test
    fun `三个解析器绑定各自的支付来源`() {
        assertEquals(PaySource.ALIPAY, AlipayParser().source)
        assertEquals(PaySource.WECHAT, WechatParser().source)
        assertEquals(PaySource.UNIONPAY, UnionpayParser().source)
    }
}
