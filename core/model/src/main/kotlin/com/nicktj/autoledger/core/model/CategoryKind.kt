package com.nicktj.autoledger.core.model

/** 分类适用的方向。 */
enum class CategoryKind {
    /** 只用于支出 */
    EXPENSE,

    /** 只用于收入 */
    INCOME,

    /** 收支都能用（比如「转账」「红包」「未分类」） */
    BOTH,
}
