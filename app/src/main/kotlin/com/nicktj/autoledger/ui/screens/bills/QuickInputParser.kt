package com.nicktj.autoledger.ui.screens.bills

import com.nicktj.autoledger.core.model.TxnType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 「一句话记一笔」的解析结果。
 *
 * @param amountCents 金额，单位「分」，永远为正数
 * @param type 收支方向
 * @param merchant 去掉金额与类型词之后剩下的文本，可能为 null
 * @param categoryHint 命中的分类名（餐饮 / 交通 / ...），未命中为 null
 */
data class QuickInput(
    val amountCents: Long,
    val type: TxnType,
    val merchant: String?,
    val categoryHint: String?,
)

/**
 * 把「麦当劳 28」「打车 35.5元」「工资 8000 收入」这类一句话解析成记账草稿。
 *
 * 纯 Kotlin 实现，不依赖 Android，方便单元测试。
 * 解析不出合法金额（没有数字 / 为 0 / 小数超过 2 位）时返回 null，由调用方提示改用表单。
 */
object QuickInputParser {

    /**
     * 第一个数字片段。优先匹配带千分位的写法（1,200），再匹配普通写法（28 / 28.50）。
     */
    private val AMOUNT_PATTERN = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?""")

    /** 商户文本里要剔除的类型词，长词在前，避免「红包」先被剔掉只剩「发」。 */
    private val TYPE_WORDS = listOf(
        "收红包",
        "发红包",
        "收入",
        "工资",
        "到账",
        "收到",
        "转入",
        "退款",
        "红包",
    )

    /** 出现任意一个就按收入处理。 */
    private val INCOME_WORDS = listOf("收入", "工资", "到账", "收到", "转入", "退款")

    /** 分类提示词，按这个顺序匹配，命中即返回。 */
    private val CATEGORY_HINTS = listOf(
        "餐饮",
        "交通",
        "购物",
        "娱乐",
        "生活缴费",
        "医疗",
        "转账",
        "红包",
        "其他",
    )

    private const val RED_PACKET = "红包"

    private val CURRENCY_SYMBOLS = listOf("¥", "￥", "\$")

    private val UNIT_WORDS = listOf("元", "块")

    private val WHITESPACE = Regex("""\s+""")

    fun parse(input: String): QuickInput? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val amountSource = stripAmountDecorations(text)
        val match = AMOUNT_PATTERN.find(amountSource) ?: return null
        val amountCents = toCents(match.value) ?: return null
        if (amountCents <= 0L) return null

        return QuickInput(
            amountCents = amountCents,
            type = typeOf(text),
            merchant = merchantOf(text, match.value),
            categoryHint = CATEGORY_HINTS.firstOrNull { text.contains(it) },
        )
    }

    /** 去掉货币符号与「元 / 块」，方便正则只盯数字。 */
    private fun stripAmountDecorations(text: String): String {
        var result = text
        CURRENCY_SYMBOLS.forEach { result = result.replace(it, " ") }
        UNIT_WORDS.forEach { result = result.replace(it, " ") }
        return result
    }

    /** 数字片段 -> 分；小数超过 2 位或数值过大返回 null。 */
    private fun toCents(fragment: String): Long? {
        val plain = fragment.replace(",", "")
        val dot = plain.indexOf('.')
        if (dot >= 0 && plain.length - dot - 1 > 2) return null
        return runCatching {
            BigDecimal(plain)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        }.getOrNull()
    }

    private fun typeOf(text: String): TxnType {
        if (text.contains(RED_PACKET)) {
            return if (text.contains("收") || text.contains("领")) {
                TxnType.RED_PACKET_IN
            } else {
                TxnType.RED_PACKET_OUT
            }
        }
        return if (INCOME_WORDS.any { text.contains(it) }) TxnType.INCOME else TxnType.EXPENSE
    }

    /** 去掉金额片段、类型词与货币符号后剩下的文本即商户。 */
    private fun merchantOf(text: String, amountFragment: String): String? {
        var rest = text.replace(amountFragment, " ")
        TYPE_WORDS.forEach { rest = rest.replace(it, " ") }
        rest = stripAmountDecorations(rest)
        val compact = rest.replace(WHITESPACE, " ").trim()
        return compact.ifEmpty { null }
    }
}
