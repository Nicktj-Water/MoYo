package com.nicktj.autoledger.core.parser

/**
 * 一个支付渠道的全部可调规则。
 *
 * 设计目标：解析流程固定，规则全部外置成数据。以后某家改文案，只改
 * [AlipayRules] / [WechatRules] / [UnionpayRules] 里的字符串或正则，
 * 不需要碰 [BaseTxnParser]。
 *
 * 字段约定：
 * - 类型判定顺序固定为：退款 > 红包 > 收款/到账 > 转账 > 支出。
 * - [amountRegex] 里至少要有一个捕获组包住数字；解析前会先把全角数字、全角逗号、
 *   全角句点转成半角，再跑这个正则。没有捕获组时退化为匹配整段。
 * - [merchantPatterns] 的第一个非空捕获组就是商户名；抽不干净会被丢弃，不会塞整段正文。
 * - [sourceTitleAllowList] 微信按**精确相等**匹配；其他渠道按「标题包含任一词」匹配，
 *   用来过滤联系人昵称之类的聊天标题。
 * - [pageTitleAllowList] 是无障碍页面的标题白名单。微信支付结果页的 title 是
 *   「支付成功」/「已收款」，并不是通知渠道的「微信支付」，所以单独放一份数据。
 */
data class RuleSet(
    /** 支出关键词。 */
    val paymentKeywords: List<String>,

    /** 收款/到账关键词。 */
    val incomeKeywords: List<String>,

    /** 转账关键词（方向另看 [transferInKeywords] / [transferOutKeywords]）。 */
    val transferKeywords: List<String>,

    /** 红包关键词。 */
    val redPacketKeywords: List<String>,

    /** 退款关键词。退款优先级最高。 */
    val refundKeywords: List<String>,

    /** 营销/广告/账单提醒词；命中即 [ParseResult.Marketing]，优先于一切类型判断。 */
    val marketingKeywords: List<String>,

    /** 金额正则，至少一个捕获组。 */
    val amountRegex: Regex,

    /** 商户正则，第一个非空捕获组是商户名。 */
    val merchantPatterns: List<Regex>,

    /** 通知标题白名单。 */
    val sourceTitleAllowList: List<String>,

    /** 红包收入方向词。 */
    val redPacketInKeywords: List<String> = emptyList(),

    /** 红包发出方向词。 */
    val redPacketOutKeywords: List<String> = emptyList(),

    /** 转账收入方向词。 */
    val transferInKeywords: List<String> = emptyList(),

    /** 转账支出方向词。 */
    val transferOutKeywords: List<String> = emptyList(),

    /** 聊天消息前缀，例如 `[语音]`、`[图片]`；命中即 [ParseResult.NotPayment]。 */
    val chatPrefixes: List<String> = emptyList(),

    /** 无障碍页面标题白名单（例如微信的「支付成功」）。 */
    val pageTitleAllowList: List<String> = emptyList(),
)

/** 三家共用的营销词。只放误伤概率低的词，宁漏勿错杀真实交易。 */
internal val commonMarketingKeywords: List<String> = listOf(
    "最高", "最低", "随机", "最高可得", "最高可领", "有机会", "抽奖", "瓜分", "免单",
    "立减", "满减", "优惠券", "领券", "领红包", "券包", "新人礼", "礼包",
    "邀请", "邀你领", "等你领", "关注公众号", "签到", "红包雨",
    "狂欢", "限时", "秒杀", "提额", "额度提升",
    "账单已出", "账单提醒", "账单日", "应还", "还款日", "还款提醒", "出账",
    "为你推荐", "推荐有礼", "推荐好友", "积分兑换", "积分商城",
    "开通会员", "会员福利", "会员日", "活动期间", "活动规则", "限时活动",
    "立即领取", "点击领取", "支付有优惠",
)

/** 红包收入方向词。 */
internal val defaultRedPacketInKeywords: List<String> = listOf(
    "收到", "领到", "到账", "入账", "存入", "获得", "已收", "领取成功",
)

/** 红包发出方向词。 */
internal val defaultRedPacketOutKeywords: List<String> = listOf(
    "发出", "发送", "发放", "转赠", "包好",
)

/** 转账收入方向词。 */
internal val defaultTransferInKeywords: List<String> = listOf(
    "收到", "转入", "到账", "入账", "已收款", "收下", "收款",
)

/** 转账支出方向词。 */
internal val defaultTransferOutKeywords: List<String> = listOf(
    "已向", "转给", "转账给", "转出", "付款给",
)

/** 聊天消息的结构性前缀；通知正文以这些开头时直接当聊天。 */
internal val defaultChatPrefixes: List<String> = listOf(
    "[语音]", "[图片]", "[链接]", "[动画表情]", "[视频]", "[文件]",
    "[位置]", "[名片]", "[表情]", "[小程序]", "[音乐]",
    "【语音】", "【图片】", "【链接】", "【视频】",
)

/**
 * 三家共用的商户抽取正则。
 *
 * 标签类放最前（优先级最高），然后才是「已向…转账」「向…付款」这类句式。
 * `向(?!你|您)` 是为了避免把「张三通过扫码**向你**付款」里的「你」当成商户。
 */
internal val defaultMerchantPatterns: List<Regex> = listOf(
    Regex("""收款方[：:]?\s*([^\s，,。；;、]+)"""),
    Regex("""付款方[：:]?\s*([^\s，,。；;、]+)"""),
    Regex("""收款人[：:]?\s*([^\s，,。；;、]+)"""),
    Regex("""付款人[：:]?\s*([^\s，,。；;、]+)"""),
    Regex("""商户全称[：:]?\s*([^\s，,。；;、]+)"""),
    Regex("""商户名称[：:]?\s*([^\s，,。；;、]+)"""),
    Regex("""商户名[：:]?\s*([^\s，,。；;、]+)"""),
    Regex("""已向([^\s，,。；;、]+?)转账"""),
    Regex("""转账给([^\s，,。；;、]+)"""),
    Regex("""付款给([^\s，,。；;、]+)"""),
    Regex("""向(?!你|您)([^\s，,。；;、]+?)付款"""),
    Regex("""([^\s，,。；;、]+?)已成功向你转了"""),
    Regex("""收到([^\s，,。；;、]+?)的?转账"""),
)
