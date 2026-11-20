package com.nicktj.autoledger.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.domain.DomainTxn
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.RecognitionOutcome
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.ui.theme.LocalExtra
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------------------------------------------------------------------------
// 时间
// ---------------------------------------------------------------------------

/** 全 App 统一时区：国内本地时间，避免跨日/跨月漂移。 */
val ShanghaiZone: ZoneId = ZoneId.of("Asia/Shanghai")

private val TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

private val DATE_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 当前月份（Asia/Shanghai）。 */
fun currentMonth(): YearMonth = YearMonth.now(ShanghaiZone)

/** 月份存库 / 对比用的 key，形如 2026-11。 */
fun monthKey(month: YearMonth): String =
    String.format(Locale.US, "%04d-%02d", month.year, month.monthValue)

/** 月份中文标签，形如 2026年11月。 */
fun monthLabel(month: YearMonth): String = "${month.year}年${month.monthValue}月"

/** 短月份标签，形如 11月。 */
fun shortMonthLabel(month: YearMonth): String = "${month.monthValue}月"

/** 某个月第一毫秒。 */
fun monthStartMillis(month: YearMonth): Long =
    month.atDay(1).atStartOfDay(ShanghaiZone).toInstant().toEpochMilli()

/** 某个月最后一毫秒。 */
fun monthEndMillis(month: YearMonth): Long =
    month.atEndOfMonth().atTime(23, 59, 59, 999_000_000)
        .atZone(ShanghaiZone).toInstant().toEpochMilli()

fun dayOf(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ShanghaiZone).toLocalDate()

fun yearMonthOf(epochMillis: Long): YearMonth = YearMonth.from(dayOf(epochMillis))

fun weekdayLabel(date: LocalDate): String = WEEKDAY_LABELS[date.dayOfWeek.value - 1]

/** 分组标题：11月21日 周五。 */
fun dayTitle(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

/** 行内时间：14:05。 */
fun timeLabel(epochMillis: Long): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ShanghaiZone))

/** 完整时间：2026-11-21 14:05。 */
fun dateTimeLabel(epochMillis: Long): String =
    DATE_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ShanghaiZone))

/**
 * 把日期选择器返回的 UTC 零点毫秒，转成「保留原时分秒」的本地时间戳。
 */
fun applyPickedDate(pickedUtcMillis: Long, originalMillis: Long): Long {
    val pickedDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val originalTime: LocalTime = Instant.ofEpochMilli(originalMillis)
        .atZone(ShanghaiZone)
        .toLocalTime()
    return pickedDate.atTime(originalTime).atZone(ShanghaiZone).toInstant().toEpochMilli()
}

// ---------------------------------------------------------------------------
// 枚举中文名
// ---------------------------------------------------------------------------

fun txnTypeOf(raw: String): TxnType =
    TxnType.entries.firstOrNull { it.name == raw } ?: TxnType.EXPENSE

fun TxnType.displayName(): String = when (this) {
    TxnType.EXPENSE -> "支出"
    TxnType.INCOME -> "收入"
    TxnType.RED_PACKET_IN -> "收红包"
    TxnType.RED_PACKET_OUT -> "发红包"
    TxnType.REFUND -> "退款"
}

fun Channel.displayName(): String = when (this) {
    Channel.NOTIFICATION -> "通知"
    Channel.ACCESSIBILITY -> "无障碍"
    Channel.MANUAL -> "手动"
}

fun PaySource.displayName(): String = when (this) {
    PaySource.ALIPAY -> "支付宝"
    PaySource.WECHAT -> "微信"
    PaySource.UNIONPAY -> "云闪付"
    PaySource.MANUAL -> "手动"
}

fun TxnStatus.displayName(): String = when (this) {
    TxnStatus.CONFIRMED -> "已确认"
    TxnStatus.PENDING -> "待确认"
    TxnStatus.DISCARDED -> "已丢弃"
}

fun RecognitionOutcome.displayName(): String = when (this) {
    RecognitionOutcome.INSERTED -> "已入账"
    RecognitionOutcome.PENDING -> "待确认"
    RecognitionOutcome.DUPLICATE_MERGED -> "重复合并"
    RecognitionOutcome.FILTERED_MARKETING -> "营销过滤"
    RecognitionOutcome.UNPARSED -> "未能解析"
    RecognitionOutcome.ERROR -> "解析异常"
}

/** 日志列表里的来源 App 名（按包名反查，未知包名取最后一段）。 */
fun appNameOf(packageName: String): String =
    PaySource.fromPackageName(packageName)?.displayName()
        ?: packageName.substringAfterLast('.').ifEmpty { packageName }

// ---------------------------------------------------------------------------
// 金额
// ---------------------------------------------------------------------------

/** 支出 / 发红包是流向外的，加负号；收入 / 退款 / 收红包加正号。 */
fun TxnType.signPrefix(): String = when (this) {
    TxnType.EXPENSE, TxnType.RED_PACKET_OUT -> "-"
    else -> "+"
}

/** 是否计入「支出侧」统计（受红包开关影响）。 */
fun TxnType.isExpenseSide(countRedPacket: Boolean): Boolean = when (this) {
    TxnType.EXPENSE -> true
    TxnType.RED_PACKET_OUT -> countRedPacket
    else -> false
}

/** 是否计入「收入侧」统计（受红包开关影响）。 */
fun TxnType.isIncomeSide(countRedPacket: Boolean): Boolean = when (this) {
    TxnType.INCOME -> true
    TxnType.RED_PACKET_IN -> countRedPacket
    else -> false
}

/** 支出实际金额：扣掉已退款冲抵的部分，且不会小于 0。 */
fun TransactionEntity.effectiveExpenseCents(): Long =
    amountCents - refundedCents.coerceIn(0L, amountCents)

fun TransactionEntity.txnType(): TxnType = txnTypeOf(type)

/** 把界面输入（元）换算成分，非法或非正数返回 null。 */
fun parseAmountToCents(input: String): Long? {
    val text = input.trim().replace(",", "").replace("¥", "").replace("￥", "")
    if (text.isEmpty()) return null
    val cents = runCatching {
        BigDecimal(text).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }.getOrNull() ?: return null
    return cents.takeIf { it > 0L }
}

/** 分 -> 输入框里的纯数字，例如 12345 -> "123.45"。 */
fun centsToPlainText(cents: Long): String =
    if (cents <= 0L) "" else BigDecimal.valueOf(cents, 2).toPlainString()

/** 柱状图上的紧凑金额：1.2万 / 860。 */
fun compactYuanText(cents: Long): String {
    val yuan = cents / 100.0
    return when {
        yuan >= 10_000.0 -> String.format(Locale.US, "%.1f万", yuan / 10_000.0)
        yuan >= 1_000.0 -> String.format(Locale.US, "%.0f", yuan)
        yuan > 0.0 -> String.format(Locale.US, "%.0f", yuan)
        else -> "0"
    }
}

/** 金额颜色：支出深色、收入/退款绿色、红包紫色。 */
@Composable
fun amountColor(type: TxnType): Color {
    val extra = LocalExtra.current
    return when (type) {
        TxnType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        TxnType.INCOME, TxnType.REFUND, TxnType.RED_PACKET_IN -> extra.success
        TxnType.RED_PACKET_OUT -> if (extra.dark) Color(0xFFC4B5FD) else Color(0xFF7C3AED)
    }
}

// ---------------------------------------------------------------------------
// 分类图标 / 配色
// ---------------------------------------------------------------------------

/** 分类图标：优先用库里存的语义化 icon 名，其次按中文分类名兜底。 */
fun categoryIconFor(icon: String?, name: String?): ImageVector {
    val key = (icon ?: "").lowercase(Locale.ROOT)
    val label = name.orEmpty()
    return when {
        key in setOf("restaurant", "fastfood", "local_cafe") || label == "餐饮" ->
            Icons.Outlined.Restaurant

        key in setOf("directions_bus", "directions_car", "train") ||
            label == "交通" || label == "出行" -> Icons.Outlined.DirectionsBus

        key in setOf("shopping_bag", "shopping_cart", "store") ||
            label == "购物" -> Icons.Outlined.ShoppingBag

        key in setOf("sports_esports", "movie", "celebration") ||
            label == "娱乐" -> Icons.Outlined.SportsEsports

        key in setOf("receipt_long", "bolt", "water_drop") ||
            label == "生活缴费" -> Icons.Outlined.ReceiptLong

        key in setOf("local_hospital", "medication") ||
            label == "医疗" -> Icons.Outlined.LocalHospital

        key in setOf("swap_horiz", "sync_alt") || label == "转账" -> Icons.Outlined.SwapHoriz
        key in setOf("redeem", "card_giftcard") || label == "红包" -> Icons.Outlined.Redeem
        key in setOf("payments", "account_balance") || label == "收入" ->
            Icons.Outlined.Payments

        key in setOf("undo", "keyboard_return") || label == "退款" -> Icons.Outlined.Undo
        key in setOf("more_horiz") || label == "其他" -> Icons.Outlined.MoreHoriz
        key in setOf("help_outline") || label == "未分类" -> Icons.Outlined.HelpOutline
        else -> Icons.Outlined.AccountBalanceWallet
    }
}

/** 分类色块用的一组基色（浅色直接铺底，深色降透明度）。 */
private val CATEGORY_BASE_COLORS = listOf(
    Color(0xFF4F46E5),
    Color(0xFFEA580C),
    Color(0xFF0EA5E9),
    Color(0xFF16A34A),
    Color(0xFFDB2777),
    Color(0xFF7C3AED),
    Color(0xFFCA8A04),
    Color(0xFF0891B2),
)

/** 分类图标块的（底色, 前景色）。 */
@Composable
fun categoryTileColors(seed: Long?): Pair<Color, Color> {
    val extra = LocalExtra.current
    val base = CATEGORY_BASE_COLORS[((seed ?: 0L).mod(CATEGORY_BASE_COLORS.size.toLong())).toInt()]
    return if (extra.dark) {
        base.copy(alpha = 0.22f) to base.copy(alpha = 0.95f)
    } else {
        base.copy(alpha = 0.12f) to base
    }
}

// ---------------------------------------------------------------------------
// 实体映射
// ---------------------------------------------------------------------------

/**
 * 账单实体 -> 领域对象。
 *
 * 预算 / 统计只需要金额、类型、时间、分类、已冲抵金额，[rawText] 默认空串；
 * 需要原始文本时由调用方传入。
 */
fun TransactionEntity.toDomainTxn(rawText: String = ""): DomainTxn = DomainTxn(
    id = id,
    amountCents = amountCents,
    type = txnType(),
    merchant = merchant,
    source = PaySource.entries.firstOrNull { it.name == source } ?: PaySource.MANUAL,
    channel = Channel.entries.firstOrNull { it.name == channel } ?: Channel.MANUAL,
    occurredAt = occurredAt,
    confidence = confidence,
    rawText = rawText,
    categoryId = categoryId,
    refundedCents = refundedCents,
)
