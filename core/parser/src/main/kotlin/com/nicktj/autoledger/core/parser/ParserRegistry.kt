package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.PaySource

/**
 * 解析器注册表：按包名找对应渠道的解析器。
 *
 * 只认三个已知支付包名；手动记账（[PaySource.MANUAL]）没有包名，不在这里处理。
 */
object ParserRegistry {

    /** ALIPAY / WECHAT / UNIONPAY，顺序固定。 */
    val parsers: List<TxnParser> = listOf(
        AlipayParser(),
        WechatParser(),
        UnionpayParser(),
    )

    private val bySource: Map<PaySource, TxnParser> = parsers.associateBy { it.source }

    /** 按通知/无障碍事件包名找解析器；未知包名返回 null。 */
    fun forPackage(pkg: String): TxnParser? {
        val source = PaySource.fromPackageName(pkg) ?: return null
        return bySource[source]
    }
}
