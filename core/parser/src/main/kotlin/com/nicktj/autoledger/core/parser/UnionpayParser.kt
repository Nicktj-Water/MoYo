package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.PaySource

/**
 * 云闪付解析器。
 *
 * 标题「消息推送」没有信息量，主要靠正文；规则来自 [UnionpayRules.default]。
 */
class UnionpayParser(
    ruleSet: RuleSet = UnionpayRules.default,
) : BaseTxnParser(PaySource.UNIONPAY, ruleSet) {

    override val brandNames: List<String> = listOf(
        "中国银联",
        "云闪付",
        "银联",
    )
}
