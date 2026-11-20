package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.ParsedTxn
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType

/**
 * 三家共用的解析流程。
 *
 * 顺序固定，不能随便调换：
 * 1. 标题白名单：微信是硬门槛，其他渠道允许正文自证；
 * 2. 营销过滤：命中营销词直接 [ParseResult.Marketing]，不再看金额（否则「最高8888元」会被记账）；
 * 3. 聊天/系统消息过滤；
 * 4. 类型判定：退款 > 红包 > 收款/到账 > 转账 > 支出；
 * 5. 金额提取：先做全角/千分位规范化，再换成分。
 *
 * 解析器本身只做文本识别，输出通道固定为 [Channel.NOTIFICATION]。
 */
abstract class BaseTxnParser(
    override val source: PaySource,
    protected val rules: RuleSet,
) : TxnParser {

    /** 正文里出现这些品牌词时，即使标题不在白名单也认为通知来自本渠道。 */
    protected abstract val brandNames: List<String>

    /**
     * 既没有类型关键词、也没有金额时，是否仍产出一笔「待补金额」的账单。
     *
     * 目前只有微信需要：微信支付的自定义卡片通知，第三方可能只读得到「微信支付」四个字。
     */
    protected open val allowAmountlessGuess: Boolean = false

    /** 没有类型关键词、但有金额时的默认类型。 */
    protected open fun defaultType(title: String): TxnType = TxnType.EXPENSE

    /**
     * 标题是否属于本渠道。
     *
     * 默认策略：标题包含白名单词，或正文里出现品牌词，或正文本身有「钱」的语义。
     * 微信覆写为精确白名单，普通聊天（标题是联系人昵称）一律不进解析。
     */
    protected open fun isTitleAccepted(title: String, body: String): Boolean {
        if (title.isNotEmpty() && rules.sourceTitleAllowList.any { title.contains(it) }) return true
        if (title.isNotEmpty() && rules.pageTitleAllowList.any { title == it }) return true
        if (brandNames.any { body.contains(it) }) return true
        // 标题不认识（可能是版本改名），但正文明确在说钱：兜底继续解析。
        return hasMoneySignal(body)
    }

    override fun parse(
        title: String?,
        text: String?,
        bigText: String?,
        occurredAt: Long,
    ): ParseResult {
        val safeTitle = title.orEmpty().trim()
        val safeText = text.orEmpty().trim()
        val safeBigText = bigText.orEmpty().trim()

        val rawText = listOf(safeTitle, safeText, safeBigText)
            .filter { it.isNotEmpty() }
            .joinToString("|")

        val body = stripAggregationPrefix(
            listOf(safeText, safeBigText)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        )
        val analysis = listOf(safeTitle, body)
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        if (!isTitleAccepted(safeTitle, body)) return ParseResult.NotPayment
        if (containsAny(analysis, rules.marketingKeywords)) return ParseResult.Marketing
        if (isChatNoise(body)) return ParseResult.NotPayment

        val typeDecision = detectType(stripBrandsAndLabels(analysis))
        val amountHit = rules.findAmount(body) ?: rules.findAmount(safeTitle)

        // 明确解析出 0 元或负数：不生成账单。
        // 注意「没有金额」是 amountHit == null，会走下面的 needsAmount 分支，两者不是一回事。
        if (amountHit != null && amountHit.cents <= 0L) return ParseResult.NotPayment

        val amountCents = amountHit?.cents
        val anchoredAmount = amountHit?.anchored == true
        val merchant = extractMerchant(body)

        return when {
            typeDecision != null && amountCents != null -> {
                val confidence = if (typeDecision.confident && anchoredAmount) {
                    CONFIDENCE_HIGH
                } else {
                    CONFIDENCE_MEDIUM
                }
                matched(
                    type = typeDecision.type,
                    amountCents = amountCents,
                    needsAmount = false,
                    confidence = confidence,
                    merchant = merchant,
                    occurredAt = occurredAt,
                    rawText = rawText,
                )
            }

            typeDecision != null -> {
                // 只有类型、没有金额：不猜 0，也不丢账，交给用户补金额。
                matched(
                    type = typeDecision.type,
                    amountCents = 0L,
                    needsAmount = true,
                    confidence = CONFIDENCE_LOW,
                    merchant = merchant,
                    occurredAt = occurredAt,
                    rawText = rawText,
                )
            }

            amountCents != null -> {
                // 有金额但没命中任何类型词：按最低置信度的支出处理。
                matched(
                    type = defaultType(safeTitle),
                    amountCents = amountCents,
                    needsAmount = false,
                    confidence = CONFIDENCE_MEDIUM,
                    merchant = merchant,
                    occurredAt = occurredAt,
                    rawText = rawText,
                )
            }

            allowAmountlessGuess -> {
                matched(
                    type = defaultType(safeTitle),
                    amountCents = 0L,
                    needsAmount = true,
                    confidence = CONFIDENCE_LOW,
                    merchant = merchant,
                    occurredAt = occurredAt,
                    rawText = rawText,
                )
            }

            else -> ParseResult.NotPayment
        }
    }

    /** 类型判定的结果。[confident] 为 false 表示方向/类型只是猜的（例如「转账成功」不知进出）。 */
    private data class TypeDecision(
        val type: TxnType,
        val confident: Boolean,
    )

    /**
     * 类型判定。顺序必须是退款 > 红包 > 收款/到账 > 转账 > 支出，
     * 否则「退款到账」会被收入抢走，「红包已发出」会被支出抢走。
     */
    private fun detectType(text: String): TypeDecision? {
        if (containsAny(text, rules.refundKeywords)) {
            return TypeDecision(TxnType.REFUND, confident = true)
        }

        if (containsAny(text, rules.redPacketKeywords)) {
            val outgoing = containsAny(text, rules.redPacketOutKeywords)
            val incoming = containsAny(text, rules.redPacketInKeywords)
            return when {
                outgoing && !incoming -> TypeDecision(TxnType.RED_PACKET_OUT, confident = true)
                incoming && !outgoing -> TypeDecision(TxnType.RED_PACKET_IN, confident = true)
                outgoing -> TypeDecision(TxnType.RED_PACKET_OUT, confident = true)
                else -> TypeDecision(TxnType.RED_PACKET_IN, confident = false)
            }
        }

        if (containsAny(text, rules.incomeKeywords)) {
            return TypeDecision(TxnType.INCOME, confident = true)
        }

        if (containsAny(text, rules.transferKeywords)) {
            val outgoing = containsAny(text, rules.transferOutKeywords)
            val incoming = containsAny(text, rules.transferInKeywords)
            return when {
                outgoing && !incoming -> TypeDecision(TxnType.EXPENSE, confident = true)
                incoming && !outgoing -> TypeDecision(TxnType.INCOME, confident = true)
                // 「转账成功」既可能转出也可能转入：先按支出记，但置信度只有 0.6。
                else -> TypeDecision(TxnType.EXPENSE, confident = false)
            }
        }

        if (containsAny(text, rules.paymentKeywords)) {
            return TypeDecision(TxnType.EXPENSE, confident = true)
        }

        return null
    }

    /** 去掉品牌名和商户标签，避免「支付宝」里的「支付」、「收款方」里的「收款」干扰类型判定。 */
    private fun stripBrandsAndLabels(text: String): String {
        var result = text
        for (brand in brandNames.sortedByDescending { it.length }) {
            result = result.replace(brand, " ")
        }
        return merchantLabelRegex.replace(result, " ")
    }

    /** 正文是否以聊天消息前缀开头（[语音]、[图片] 等）。 */
    private fun isChatNoise(body: String): Boolean {
        if (body.isEmpty()) return false
        return rules.chatPrefixes.any { body.startsWith(it) }
    }

    /** 标题不认识时，正文里有没有「钱」的语义。 */
    private fun hasMoneySignal(body: String): Boolean {
        if (body.isEmpty()) return false
        if (body.contains('¥') || body.contains('￥')) return true
        if (body.contains("元") || body.contains("块")) return true
        if (rules.findAmount(body) != null) return true
        return containsAny(body, rules.paymentKeywords) ||
            containsAny(body, rules.incomeKeywords) ||
            containsAny(body, rules.transferKeywords) ||
            containsAny(body, rules.refundKeywords) ||
            containsAny(body, rules.redPacketKeywords)
    }

    /** 从正文里抽商户；抽不到返回 null，绝不回退成整段正文。 */
    private fun extractMerchant(body: String): String? {
        if (body.isEmpty()) return null
        for (pattern in rules.merchantPatterns) {
            val match = pattern.find(body) ?: continue
            val raw = match.firstCapturedValue() ?: continue
            val merchant = cleanMerchant(raw)
            if (merchant != null) return merchant
        }
        return null
    }

    private fun matched(
        type: TxnType,
        amountCents: Long,
        needsAmount: Boolean,
        confidence: Double,
        merchant: String?,
        occurredAt: Long,
        rawText: String,
    ): ParseResult.Match = ParseResult.Match(
        txn = ParsedTxn(
            amountCents = amountCents,
            type = type,
            merchant = merchant,
            source = source,
            occurredAt = occurredAt,
            rawText = rawText,
            confidence = confidence,
            channel = Channel.NOTIFICATION,
        ),
        needsAmount = needsAmount,
    )

    private companion object {
        /** 金额和类型都明确。 */
        const val CONFIDENCE_HIGH = 0.95

        /** 关键词命中但格式/方向可疑，或只有金额没有类型。 */
        const val CONFIDENCE_MEDIUM = 0.6

        /** 只有类型没有金额，需要用户补录。 */
        const val CONFIDENCE_LOW = 0.35

        /** 系统折叠通知的前缀，例如 `[2条]微信支付：已支付¥16.00`。 */
        val AGGREGATION_PREFIX = Regex("""^\[[0-9]+条\]\s*""")

        fun stripAggregationPrefix(text: String): String = AGGREGATION_PREFIX.replace(text, "")
    }
}
