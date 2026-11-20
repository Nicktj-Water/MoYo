package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType

/**
 * 微信解析器。
 *
 * 标题是硬门槛：必须精确等于 [WechatRules.default] 里的通知标题，或命中无障碍页面标题；
 * 其他标题（联系人/群名）一律 [ParseResult.NotPayment]，不看正文。
 */
class WechatParser(
    ruleSet: RuleSet = WechatRules.default,
) : BaseTxnParser(PaySource.WECHAT, ruleSet) {

    override val brandNames: List<String> = listOf(
        "微信收款商业版",
        "微信收款助手",
        "微信支付",
        "微信",
    )

    override val allowAmountlessGuess: Boolean = true

    override fun isTitleAccepted(title: String, body: String): Boolean {
        if (title.isEmpty()) return false
        return rules.sourceTitleAllowList.any { title == it } ||
            rules.pageTitleAllowList.any { title == it }
    }

    override fun defaultType(title: String): TxnType =
        if (title.contains("收款")) TxnType.INCOME else TxnType.EXPENSE
}
