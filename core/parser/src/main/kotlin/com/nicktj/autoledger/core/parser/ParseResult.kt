package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.ParsedTxn

/**
 * 一条通知/页面文本的解析结局。
 *
 * 解析器只回答三件事：这是一笔钱、这跟钱无关、这是营销噪声。
 */
sealed interface ParseResult {

    /**
     * 识别为一笔资金变动。
     *
     * @property txn 解析出的账单。金额缺失时 [ParsedTxn.amountCents] 固定为 0，仅作占位，
     *   真正的「这笔多少钱」由用户补录，不要把它当成 0 元账单入库。
     * @property needsAmount 金额缺失、需要用户补录时为 true。
     *   此时 [txn] 的 confidence 固定为 0.35，调用方应进「待确认」而不是直接入账。
     */
    data class Match(
        val txn: ParsedTxn,
        val needsAmount: Boolean = false,
    ) : ParseResult

    /** 与钱无关的通知：普通聊天、系统提示、版本更新、权限提醒等。 */
    data object NotPayment : ParseResult

    /** 营销、广告、账单/还款提醒等必须过滤的通知。 */
    data object Marketing : ParseResult
}

/**
 * 把解析结果换到另一个采集通道。
 *
 * 解析器只做纯文本识别，默认输出 [Channel.NOTIFICATION]；无障碍服务读取页面节点后，
 * 可以用本函数把结果标成 [Channel.ACCESSIBILITY]，字段本身不变。
 */
fun ParseResult.withChannel(channel: Channel): ParseResult = when (this) {
    is ParseResult.Match -> copy(txn = txn.copy(channel = channel))
    else -> this
}
