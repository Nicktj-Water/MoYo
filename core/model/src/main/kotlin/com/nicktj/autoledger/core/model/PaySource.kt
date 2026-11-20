package com.nicktj.autoledger.core.model

/**
 * 支付来源。
 *
 * [packageName] 是识别时用的 Android 包名；[MANUAL] 是手动记账，没有包名。
 */
enum class PaySource(
    val packageName: String?,
) {
    /** 支付宝：com.eg.android.AlipayGphone */
    ALIPAY("com.eg.android.AlipayGphone"),

    /** 微信：com.tencent.mm */
    WECHAT("com.tencent.mm"),

    /** 云闪付：com.unionpay */
    UNIONPAY("com.unionpay"),

    /** 手动记账 */
    MANUAL(null),
    ;

    companion object {
        /** 按通知/无障碍事件的包名反查来源，未知包名返回 null。 */
        fun fromPackageName(packageName: String): PaySource? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
