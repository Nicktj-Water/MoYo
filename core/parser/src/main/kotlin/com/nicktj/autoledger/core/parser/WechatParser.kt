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
        if (rules.sourceTitleAllowList.any { title == it }) return true
        // 无障碍页面：标题是页面状态「支付成功」/「已收款」，且拼接文本通常以它开头。
        // 这样既能解析页面，又不会把昵称恰好叫「支付成功」的联系人聊天放进来。
        return rules.pageTitleAllowList.any { title == it } && body.startsWith(title)
    }

    override fun defaultType(title: String): TxnType =
        if (title.contains("收款")) TxnType.INCOME else TxnType.EXPENSE
}
