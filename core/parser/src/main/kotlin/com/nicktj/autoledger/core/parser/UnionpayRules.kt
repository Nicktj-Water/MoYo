package com.nicktj.autoledger.core.parser

/**
 * 云闪付通知/页面规则。
 *
 * 云闪付通知的标题恒为「消息推送」，本身没有信息量；方向只能靠正文里的
 * 「云闪付收款」「入账」「支付成功」「已向…转账」判断。
 */
object UnionpayRules {

    val default: RuleSet = RuleSet(
        paymentKeywords = listOf(
            "支付成功", "付款成功", "交易成功", "支出", "消费", "扣款", "付款", "支付",
        ),
        incomeKeywords = listOf(
            "入账", "到账", "收款", "已收款", "转账到账", "收到款项", "收入",
        ),
        transferKeywords = listOf("转账", "转入", "转出", "汇款"),
        redPacketKeywords = listOf("红包"),
        refundKeywords = listOf("退款", "退回", "已退"),
        marketingKeywords = commonMarketingKeywords + listOf(
            "云闪付红包活动", "62节", "新人礼包",
        ),
        amountRegex = defaultAmountRegex,
        merchantPatterns = defaultMerchantPatterns,
        sourceTitleAllowList = listOf(
            "消息推送", "支付助手", "云闪付", "银联", "中国银联", "交易", "到账", "收款",
            "付款", "退款", "转账", "红包", "支付成功",
        ),
        redPacketInKeywords = defaultRedPacketInKeywords,
        redPacketOutKeywords = defaultRedPacketOutKeywords,
        transferInKeywords = defaultTransferInKeywords,
        transferOutKeywords = defaultTransferOutKeywords,
        chatPrefixes = defaultChatPrefixes,
        pageTitleAllowList = listOf("交易成功", "支付成功"),
    )
}
