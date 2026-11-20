package com.nicktj.autoledger.core.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 把「分」格式化成人民币字符串，例如 123456 -> "¥1,234.56"。
 * 负数把符号放在货币符号前面："-¥1,234.56"。
 */
fun formatYuan(cents: Long): String {
    val value = BigDecimal.valueOf(cents, 2)
    val format = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    val body = format.format(value.abs())
    return if (value.signum() < 0) "-¥$body" else "¥$body"
}
