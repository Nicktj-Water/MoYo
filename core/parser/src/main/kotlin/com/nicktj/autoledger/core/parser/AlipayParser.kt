package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.PaySource

/**
 * 支付宝解析器。
 *
 * 规则来自 [AlipayRules.default]；需要临时适配某个版本时，可以传入自定义 [RuleSet]。
 */
class AlipayParser(
    ruleSet: RuleSet = AlipayRules.default,
) : BaseTxnParser(PaySource.ALIPAY, ruleSet) {

    override val brandNames: List<String> = listOf(
        "支付宝支付助手",
        "支付助手",
        "支付宝",
    )
}
