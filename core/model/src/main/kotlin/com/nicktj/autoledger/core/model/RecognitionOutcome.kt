package com.nicktj.autoledger.core.model

/** 一次识别流程的结局，写进 recognition_logs 方便回查为什么没入账。 */
enum class RecognitionOutcome {
    /** 直接入库 */
    INSERTED,

    /** 置信度不足，进待确认 */
    PENDING,

    /** 判定为重复，合并到已有账单 */
    DUPLICATE_MERGED,

    /** 营销/广告通知，被过滤 */
    FILTERED_MARKETING,

    /** 没解析出金额或类型 */
    UNPARSED,

    /** 解析过程抛异常 */
    ERROR,
}
