package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.ParsedTxn
import com.nicktj.autoledger.core.model.PaySource

/**
 * 通知文本解析器。每个支付渠道一个实现，实现放在后续步骤补充。
 */
interface TxnParser {

    /** 该解析器对应的支付来源。 */
    val source: PaySource

    /**
     * 解析一条通知。
     *
     * @param pkg 通知来源包名
     * @param title 通知标题，可能为空
     * @param text 通知正文，可能为空
     * @param bigText 展开后的长文本，可能为空
     * @return 解析成功返回交易；不是交易或无法识别时返回 null
     */
    fun parse(pkg: String, title: String?, text: String?, bigText: String?): ParsedTxn?
}
