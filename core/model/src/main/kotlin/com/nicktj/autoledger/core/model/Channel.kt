package com.nicktj.autoledger.core.model

/** 账单是从哪个通道拿到的。 */
enum class Channel {
    /** 通知栏监听 */
    NOTIFICATION,

    /** 无障碍服务读取界面节点 */
    ACCESSIBILITY,

    /** 用户手动录入 */
    MANUAL,
}
