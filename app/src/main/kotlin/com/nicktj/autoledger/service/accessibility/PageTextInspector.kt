package com.nicktj.autoledger.service.accessibility

/**
 * 无障碍页面文本提取（纯函数，方便单测）。
 *
 * 只关心「支付成功」类关键词和 `¥` 金额；其余节点文本只在内存里过一遍，
 * 不落库、不打日志。调研结论：取「支付成功」之后出现的第一个 ¥ 金额。
 */
object PageTextInspector {

    /** 防抖窗口：2 秒内只处理一次，避免列表滚动重复触发。 */
    const val DEBOUNCE_MS: Long = 2_000L

    /** 命中即认为这是一次支付结果页。 */
    val PAYMENT_KEYWORDS: List<String> = listOf(
        "支付成功",
        "付款成功",
        "转账成功",
        "收款成功",
        "退款成功",
    )

    /** `¥12.34` / `￥ 12.34` / `¥1,234.56`。 */
    private val YUAN_AMOUNT = Regex("[¥￥]\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)")

    /** 没有货币符号时的兜底：`12.34元`。 */
    private val YUAN_SUFFIX_AMOUNT =
        Regex("((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{1,2})?)\\s*[元块]")

    /** 这行文本里是否有支付结果关键词。 */
    fun containsPaymentKeyword(line: String): Boolean =
        PAYMENT_KEYWORDS.any { line.contains(it) }

    /**
     * 从页面文本里抽出「关键词 + 金额」这一小段，交给识别流水线。
     *
     * @return 形如 `支付成功 ¥12.34`；连关键词都没有时返回 null；
     *   有关键词但找不到金额时只返回关键词那一行（流水线会存成待补金额）。
     */
    fun extractPaymentText(lines: List<String>): String? {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val index = cleaned.indexOfFirst { containsPaymentKeyword(it) }
        if (index < 0) return null

        val keywordLine = cleaned[index]
        // 1. 同一行里「关键词之后」的第一个 ¥ 金额。
        // 2. 否则取关键词后面几行里出现的第一个 ¥ 金额。
        val amount = amountAfterKeyword(keywordLine)
            ?: cleaned.drop(index + 1).firstNotNullOfOrNull { firstYuanAmount(it) }
        if (amount != null) return "$keywordLine $amount"

        // 3. 没有 ¥ 时退到「xx.xx元」写法。
        val suffixAmount = firstYuanSuffixAmount(keywordLine)
            ?: cleaned.drop(index + 1).firstNotNullOfOrNull { firstYuanSuffixAmount(it) }
        return if (suffixAmount != null) "$keywordLine $suffixAmount" else keywordLine
    }

    /**
     * 同一行里，关键词之后出现的第一个 ¥ 金额（带货币符号）。
     * 关键词之前的金额不取，避免把「优惠 ¥0.50」当成实付。
     */
    fun amountAfterKeyword(line: String): String? {
        val hit = PAYMENT_KEYWORDS
            .mapNotNull { keyword ->
                val position = line.indexOf(keyword)
                if (position >= 0) position to keyword else null
            }
            .minByOrNull { it.first }
            ?: return null
        val tail = line.substring(hit.first + hit.second.length)
        return YUAN_AMOUNT.find(tail)?.value
    }

    /** 一行里第一个带 ¥ 的金额。 */
    fun firstYuanAmount(line: String): String? = YUAN_AMOUNT.find(line)?.value

    /** 一行里第一个「xx元」金额。 */
    private fun firstYuanSuffixAmount(line: String): String? =
        YUAN_SUFFIX_AMOUNT.find(line)?.value
}
