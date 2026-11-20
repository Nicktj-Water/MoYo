package com.nicktj.autoledger.core.model

/** 账单确认状态：自动识别的低置信度记录先落 [PENDING]，由用户确认。 */
enum class TxnStatus {
    /** 已确认，计入统计 */
    CONFIRMED,

    /** 待确认，暂不计入统计 */
    PENDING,

    /** 已丢弃 */
    DISCARDED,
}
