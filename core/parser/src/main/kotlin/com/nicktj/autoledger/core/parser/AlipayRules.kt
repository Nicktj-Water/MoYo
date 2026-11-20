package com.nicktj.autoledger.core.parser

/**
 * 支付宝通知/页面规则。
 *
 * 支付宝「支付助手」的资金变动消息和 App 内消息卡片同源，金额偏全角 `￥`，可能带尾随空格；
 * 标题常见「支付宝」「支付助手」「提现到账成功」等，所以标题白名单按「包含」匹配。
 */
object AlipayRules {

    val default: RuleSet = RuleSet(
        paymentKeywords = listOf(
            "已支付", "支付成功", "付款成功", "支出", "消费", "交易成功",
            "扣款", "付款", "支付",
        ),
        incomeKeywords = listOf(
            "收款", "到账", "入账", "已收款", "向你付款", "向你转了", "向你转账",
            "转账到账", "提现到账成功", "收入", "收到款项",
        ),
        transferKeywords = listOf("转账", "转了", "转入", "转出", "汇款"),
        redPacketKeywords = listOf("红包", "紅包"),
        refundKeywords = listOf("退款", "退回", "已退", "退还款"),
        marketingKeywords = commonMarketingKeywords + listOf(
            "集五福", "五福", "花呗额度", "借呗", "备用金", "余额宝收益",
            "双11", "双十一", "618", "年货节", "红包活动",
        ),
        amountRegex = defaultAmountRegex,
        merchantPatterns = defaultMerchantPatterns,
        sourceTitleAllowList = listOf(
            "支付宝", "支付助手", "提现", "到账", "支付成功", "付款成功",
            "收款", "付款", "退款", "转账", "红包", "交易提醒", "交易成功",
        ),
        redPacketInKeywords = defaultRedPacketInKeywords,
        redPacketOutKeywords = defaultRedPacketOutKeywords,
        transferInKeywords = defaultTransferInKeywords,
        transferOutKeywords = defaultTransferOutKeywords,
        chatPrefixes = defaultChatPrefixes,
        pageTitleAllowList = listOf("支付成功", "已到账", "收款成功", "交易成功"),
    )
}
