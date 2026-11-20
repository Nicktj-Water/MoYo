package com.nicktj.autoledger.core.parser

/** 取正则匹配里的第一个非空捕获组；没有捕获组时返回 null。 */
internal fun MatchResult.firstCapturedValue(): String? {
    for (index in 1..groupValues.lastIndex) {
        val value = groupValues[index]
        if (value.isNotBlank()) return value
    }
    return null
}

/** 文本里是否包含任一关键词（大小写不敏感，空串忽略）。 */
internal fun containsAny(text: String, keywords: List<String>): Boolean =
    keywords.any { it.isNotEmpty() && text.contains(it, ignoreCase = true) }

/** 商户标签，类型判定前先剔掉，避免「收款方」里的「收款」被当成收入。 */
internal val merchantLabelRegex: Regex = Regex(
    """商户全称|商户名称|商户名|收款方|付款方|收款人|付款人|收款账户|付款账户|对方账户"""
)

/** 商户名两端要去掉的标点。 */
private val merchantTrimChars = charArrayOf(
    '，', ',', '。', '.', '：', ':', '；', ';', '、', '"', '\'', '“', '”', '(', ')', '（', '）',
)

/** 明显不是商户名的词；命中就放弃，避免把整段正文塞进 merchant。 */
private val merchantRejectWords = listOf(
    "支付", "付款", "转账", "收款", "退款", "红包", "成功", "失败",
    "通知", "消息", "账单", "余额", "零钱", "银行", "交易", "金额",
)

/** 纯数字/金额串，通常说明正则抓错了，不能当商户。 */
private val amountLikeRegex = Regex("""[\d.,，¥￥元块\s]+\z""")

/**
 * 清洗捕获到的商户名。
 *
 * 抽不干净就返回 null —— 宁可不显示商户，也不要把「￥25.00，收款方：」这类整段正文塞进去。
 */
internal fun cleanMerchant(raw: String): String? {
    var value = raw.trim()
    value = value.trim(*merchantTrimChars).trim()
    value = value.removeSuffix("的").trim()

    if (value.isEmpty() || value.length > 30) return null
    if (value == "你" || value == "您" || value == "我") return null
    if (amountLikeRegex.matches(value)) return null
    if (merchantRejectWords.any { value.contains(it) }) return null
    return value
}
