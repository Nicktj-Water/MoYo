package com.nicktj.autoledger.core.parser

/**
 * 微信通知/页面规则。
 *
 * 微信最关键的坑：
 * 1. 通知标题必须精确等于三个收款号之一，否则就是普通聊天（标题是联系人昵称）；
 * 2. 支付成功通知常用自定义卡片（RemoteViews），`EXTRA_TEXT` 里可能只有「微信支付」，
 *    金额读不到 —— 这时要产出 needsAmount 的待确认账单，不能丢账，也不能记 0 元。
 */
object WechatRules {

    val default: RuleSet = RuleSet(
        paymentKeywords = listOf(
            "已支付", "支付成功", "付款成功", "支出", "消费", "交易成功",
            "扣款", "付款", "支付",
        ),
        incomeKeywords = listOf(
            "收款", "到账", "入账", "已收款", "向你付款", "向你转了",
            "转账到账", "收款到账", "收到款项",
        ),
        transferKeywords = listOf("转账", "转了", "转入", "转出"),
        redPacketKeywords = listOf("红包", "紅包"),
        refundKeywords = listOf("退款", "退回", "已退"),
        marketingKeywords = commonMarketingKeywords + listOf(
            "立减金", "免密支付", "亲属卡", "提现免费券", "微信支付有优惠",
        ),
        amountRegex = defaultAmountRegex,
        merchantPatterns = defaultMerchantPatterns,
        // 精确匹配，不做 contains：联系人叫「微信支付小王」也不能放进来。
        sourceTitleAllowList = listOf("微信支付", "微信收款助手", "微信收款商业版"),
        redPacketInKeywords = defaultRedPacketInKeywords,
        redPacketOutKeywords = defaultRedPacketOutKeywords,
        transferInKeywords = defaultTransferInKeywords,
        transferOutKeywords = defaultTransferOutKeywords,
        chatPrefixes = defaultChatPrefixes,
        // 无障碍支付结果页的标题是「支付成功」「已收款」，不是通知渠道标题。
        pageTitleAllowList = listOf(
            "支付成功", "支付结果", "已收款", "收款成功", "交易成功", "转账成功", "交易详情",
        ),
    )
}
