package com.nicktj.autoledger.core.model

/**
 * 账单类型。
 *
 * 约定：金额本身永远是正数（Long 分），收支方向由类型表达。
 */
enum class TxnType {
    /** 支出 */
    EXPENSE,

    /** 收入（工资、转入等） */
    INCOME,

    /** 收到红包（计入收入侧；是否参与统计由设置决定） */
    RED_PACKET_IN,

    /** 发出红包（计入支出侧；是否参与统计由设置决定） */
    RED_PACKET_OUT,

    /** 退款（amounCents 是退回的金额） */
    REFUND,
}
