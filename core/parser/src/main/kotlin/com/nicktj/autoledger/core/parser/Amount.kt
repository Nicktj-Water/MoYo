package com.nicktj.autoledger.core.parser

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 默认金额正则。
 *
 * 三种写法：
 * 1. 带货币符号：`¥12.34`、`￥1,234.56`
 * 2. 带「元/块」后缀：`12.34元`、`0.01元`
 * 3. 裸金额：`1,234.56`、`12.34`（必须带千分位或两位小数，避免把「1笔钱」的 1 当金额）
 *
 * 匹配前的文本会先做全角转半角，所以这里只写半角 `\d`。
 */
internal val defaultAmountRegex: Regex = Regex(
    """(?:[¥￥]\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?))""" +
        """|((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)\s*[元块]""" +
        """|((?:\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+\.\d{2}))"""
)

/**
 * 把一段金额文本换成 **Long 分**，全程不用 Double 累加。
 *
 * 支持：
 * - 全角数字 `１２３`、全角逗号 `，`、全角句点 `．`
 * - 半角/全角货币符号 `¥` `￥`
 * - 千分位 `1,234.56`
 * - 尾随空格、`元` / `块` 后缀
 *
 * 返回 null 的情况：空串、非数字、负数、小数超过 2 位、溢出。
 */
fun toCents(raw: String): Long? {
    var text = normalizeAmountText(raw)
    text = text
        .replace("￥", "")
        .replace("¥", "")
        .replace("元", "")
        .replace("块", "")
        .replace(",", "")
        .replace(" ", "")
        .trim()

    if (text.isEmpty()) return null
    // 只接受「可选正号 + 整数 + 最多两位小数」；负号会在这里被拒掉。
    if (!amountNumberRegex.matches(text)) return null
    val dot = text.indexOf('.')
    if (dot >= 0 && text.length - dot - 1 > 2) return null

    return try {
        val value = BigDecimal(text)
        if (value.signum() < 0) return null
        value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

/** 匹配后得到的金额候选。 */
internal data class AmountHit(
    /** 金额，单位分。 */
    val cents: Long,
    /** 是否带货币符号或「元/块」后缀；裸金额的置信度要打折。 */
    val anchored: Boolean,
)

/**
 * 从文本里找第一段金额。
 *
 * 优先返回带货币符号/「元」的金额；只有实在找不到时，才退而使用裸金额。
 * 这样 `¥1,234.56 优惠 0.50元` 会取第一个大字金额，而不是后面的优惠行。
 */
internal fun RuleSet.findAmount(text: String): AmountHit? {
    if (text.isBlank()) return null
    val normalized = normalizeAmountText(text)
    var loose: AmountHit? = null

    for (match in amountRegex.findAll(normalized)) {
        val raw = match.firstCapturedValue() ?: match.value
        val cents = toCents(raw) ?: continue
        val anchored = match.value.any { it == '¥' || it == '￥' || it == '元' || it == '块' }
        val hit = AmountHit(cents, anchored)
        if (anchored) return hit
        if (loose == null) loose = hit
    }
    return loose
}

/**
 * 金额文本规范化：全角数字/逗号/句点/负号/空格转半角。
 *
 * 统一在这里做，金额正则就只需要处理半角字符。
 */
internal fun normalizeAmountText(text: String): String {
    val builder = StringBuilder(text.length)
    for (ch in text) {
        builder.append(
            when {
                ch in '０'..'９' -> '0' + (ch - '０')
                ch == '，' -> ','
                ch == '．' -> '.'
                ch == '－' || ch == '−' -> '-'
                ch == '\u00A0' || ch == '\u3000' -> ' '
                else -> ch
            }
        )
    }
    return builder.toString()
}

/** 规范化之后只允许这些字符。 */
private val amountNumberRegex = Regex("""\+?\d+(?:\.\d+)?\z""")
