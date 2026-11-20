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

    /**
     * 支付宝也会推私聊/群消息。标题是 1–4 个汉字、又不在任何白名单里时，
     * 优先按联系人昵称处理，避免「张三发了一句已支付¥16.00」被记成账单。
     */
    override fun isTitleAccepted(title: String, body: String): Boolean {
        if (title.isEmpty()) return super.isTitleAccepted(title, body)
        if (rules.sourceTitleAllowList.any { title.contains(it) }) return true
        if (rules.pageTitleAllowList.any { title == it }) return true
        if (body.contains("支付宝") || body.contains("支付助手")) return true
        if (title.length in 1..4 && title.all { it in '\u4e00'..'\u9fff' }) return false
        return super.isTitleAccepted(title, body)
    }
}
