package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.PaySource

/**
 * 支付通知文本解析器。
 *
 * 每个渠道一个实现（支付宝/微信/云闪付），规则全部来自 [RuleSet]；
 * 本接口只负责把「标题 + 正文 + 展开文本」翻成 [ParseResult]。
 */
interface TxnParser {

    /** 该解析器对应的支付来源。 */
    val source: PaySource

    /**
     * 解析一条通知。
     *
     * @param title 通知标题，可能为空。[java.lang.String.trim] 由实现内部完成。
     * @param text 通知正文，可能为空。
     * @param bigText 展开后的长文本，可能为空。
     * @param occurredAt 事件发生时间（毫秒时间戳），默认取当前时间。
     * @return [ParseResult.Match] 表示一笔钱；[ParseResult.NotPayment] 与钱无关；
     *   [ParseResult.Marketing] 营销/账单提醒，必须过滤。
     */
    fun parse(
        title: String?,
        text: String?,
        bigText: String?,
        occurredAt: Long = System.currentTimeMillis(),
    ): ParseResult
}
