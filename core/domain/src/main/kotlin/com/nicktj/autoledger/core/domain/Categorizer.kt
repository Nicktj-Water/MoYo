package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.CategoryKind
import com.nicktj.autoledger.core.model.TxnType
import java.util.Locale

/** 分类定义（对应数据库里的 categories 表）。 */
data class CategoryDef(
    val id: Long,
    val name: String,
    val kind: CategoryKind,
)

/** 用户学习到的商户 -> 分类规则；[merchantKey] 是归一化后的商户关键词。 */
data class MerchantRule(
    val merchantKey: String,
    val categoryId: Long,
    val updatedAt: Long,
)

/** 内置关键词规则：命中 [keywords] 里任意一个就归到 [categoryName]。 */
data class BuiltinRule(
    val keywords: List<String>,
    val categoryName: String,
)

/**
 * 内置分类关键词表。
 *
 * 分类名与数据库里的内置分类一一对应；[rules] 按「先长后短」扫描（见 [Categorizer]），
 * 同长度时按本列表的先后顺序决定。
 */
object BuiltinCategoryRules {

    const val NAME_DINING = "餐饮"
    const val NAME_TRANSPORT = "交通"
    const val NAME_SHOPPING = "购物"
    const val NAME_ENTERTAINMENT = "娱乐"
    const val NAME_UTILITIES = "生活缴费"
    const val NAME_MEDICAL = "医疗"
    const val NAME_TRANSFER = "转账"
    const val NAME_RED_PACKET = "红包"
    const val NAME_REFUND = "退款"
    const val NAME_INCOME = "收入"
    const val NAME_OTHER = "其他"
    const val NAME_UNCATEGORIZED = "未分类"

    /** 关键词规则；红包/退款/收入主要靠 TxnType 特判，这里的红包词用于文案兜底。 */
    val rules: List<BuiltinRule> = listOf(
        BuiltinRule(
            keywords = listOf(
                "美团", "饿了么", "肯德基", "麦当劳", "星巴克", "瑞幸", "海底捞",
                "餐厅", "饭店", "食堂", "咖啡", "奶茶", "火锅", "烧烤", "外卖", "小吃",
            ),
            categoryName = NAME_DINING,
        ),
        BuiltinRule(
            keywords = listOf(
                "滴滴", "地铁", "公交", "高铁", "12306", "加油", "中石化", "中石油",
                "出租车", "打车", "共享单车", "停车", "机票", "航空",
            ),
            categoryName = NAME_TRANSPORT,
        ),
        BuiltinRule(
            keywords = listOf(
                "淘宝", "天猫", "京东", "拼多多", "超市", "便利店", "名创优品", "山姆",
                "盒马", "唯品会", "苏宁", "沃尔玛", "永辉", "屈臣氏", "小米有品",
            ),
            categoryName = NAME_SHOPPING,
        ),
        BuiltinRule(
            keywords = listOf(
                "电影", "影院", "游戏", "steam", "网易云", "腾讯视频", "爱奇艺", "优酷",
                "b站", "哔哩哔哩", "剧本杀", "ktv", "演唱会", "密室", "桌游",
            ),
            categoryName = NAME_ENTERTAINMENT,
        ),
        BuiltinRule(
            keywords = listOf(
                "电费", "水费", "燃气", "天然气", "话费", "宽带", "物业", "中国移动",
                "中国联通", "中国电信", "供暖", "缴费",
            ),
            categoryName = NAME_UTILITIES,
        ),
        BuiltinRule(
            keywords = listOf(
                "医院", "药房", "药店", "挂号", "诊所", "体检", "口腔", "牙科", "大药房",
            ),
            categoryName = NAME_MEDICAL,
        ),
        BuiltinRule(
            keywords = listOf("转账", "汇款", "转帐", "打款", "亲属卡"),
            categoryName = NAME_TRANSFER,
        ),
        BuiltinRule(
            keywords = listOf("红包", "压岁钱", "拜年", "拼手气"),
            categoryName = NAME_RED_PACKET,
        ),
        // 「其他」只有在商户文案里真的出现「其他」时才会命中，真正的兜底是「未分类」。
        BuiltinRule(keywords = listOf("其他"), categoryName = NAME_OTHER),
        // 「未分类」不设关键词，只作为兜底分类存在，保证分类表里能查到它。
        BuiltinRule(keywords = emptyList(), categoryName = NAME_UNCATEGORIZED),
    )
}

/**
 * 自动分类。
 *
 * 优先级：用户 [MerchantRule]（归一化后精确匹配，最新规则优先）> 内置关键词（子串包含，先长后短）
 * > 兜底分类。
 *
 * 类型特判（优先级高于一切）：收到/发出红包 -> 「红包」；退款 -> 「退款」；
 * 收入在没有规则/关键词命中时 -> 「收入」；支出没有命中时 -> 「未分类」。
 *
 * @param builtin 内置关键词规则，测试时可替换
 */
class Categorizer(
    private val builtin: List<BuiltinRule> = BuiltinCategoryRules.rules,
) {

    private data class KeywordEntry(
        val keyword: String,
        val categoryName: String,
        val ruleIndex: Int,
    )

    /** 预先把关键词归一化并按长度降序排好，命中时直接顺序扫描即可实现「先长后短」。 */
    private val keywordEntries: List<KeywordEntry> = buildList {
        builtin.forEachIndexed { index, rule ->
            rule.keywords.forEach { raw ->
                val keyword = normalize(raw) ?: return@forEach
                add(KeywordEntry(keyword = keyword, categoryName = rule.categoryName, ruleIndex = index))
            }
        }
    }.sortedWith(
        compareByDescending<KeywordEntry> { it.keyword.length }
            .thenBy { it.ruleIndex },
    )

    /**
     * 商户名归一化：去括号（含括号里的门店名）、去所有空白、全角转半角、转小写，
     * 并去掉「-xx店」「-xx路店」这类门店后缀。
     *
     * 返回 null 表示没有可用商户名。为了避免把「7-11便利店」这种真店名拆坏，
     * 没有分隔符的门店名（如「肯德基中关村店」）保持原样，只处理括号与分隔符两种形态。
     */
    fun normalize(merchant: String?): String? {
        val raw = merchant?.trim().orEmpty()
        if (raw.isEmpty()) return null
        var text = toHalfWidth(raw).lowercase(Locale.ROOT)
        text = WHITESPACE.replace(text, "")

        var changed = true
        var rounds = 0
        while (changed && rounds < MAX_BRACKET_ROUNDS) {
            changed = false
            for (regex in BRACKET_GROUPS) {
                val next = regex.replace(text, "")
                if (next != text) {
                    text = next
                    changed = true
                }
            }
            rounds++
        }

        text = stripBranchSuffix(text)
        text = text.trim { it in TRIM_CHARS }
        return text.ifEmpty { null }
    }

    /**
     * 给一笔账单分类。
     *
     * @param categories 当前分类表
     * @param userRules 用户学习到的商户规则
     * @return 命中/兜底的 categoryId；按名字找不到对应分类时返回 null
     */
    fun categorize(
        txn: DomainTxn,
        categories: List<CategoryDef>,
        userRules: List<MerchantRule>,
    ): Long? {
        val byName = categories.associateBy { it.name }

        // 类型特判优先于一切规则。
        when (txn.type) {
            TxnType.RED_PACKET_IN, TxnType.RED_PACKET_OUT ->
                return byName[BuiltinCategoryRules.NAME_RED_PACKET]?.id
            TxnType.REFUND ->
                return byName[BuiltinCategoryRules.NAME_REFUND]?.id
            else -> Unit
        }

        val merchantKey = normalize(txn.merchant)
        if (merchantKey != null) {
            val categoryIds = categories.mapTo(HashSet()) { it.id }

            // 1. 用户规则：归一化后精确匹配，同 key 取 updatedAt 最新的一条；分类已被删除的规则忽略。
            val userHit = userRules
                .asSequence()
                .filter { normalize(it.merchantKey) == merchantKey }
                .filter { it.categoryId in categoryIds }
                .maxByOrNull { it.updatedAt }
            if (userHit != null) return userHit.categoryId

            // 2. 内置关键词：子串包含，先长后短。
            val keywordHit = keywordEntries.firstOrNull { merchantKey.contains(it.keyword) }
            if (keywordHit != null) {
                return byName[keywordHit.categoryName]?.id
            }
        }

        // 3. 兜底：收入归「收入」，支出归「未分类」。
        if (txn.type == TxnType.INCOME) {
            return byName[BuiltinCategoryRules.NAME_INCOME]?.id
        }
        return byName[BuiltinCategoryRules.NAME_UNCATEGORIZED]?.id
    }

    /** 去掉分隔符开头的门店后缀，例如「美团-望京路店」->「美团」；真实店名（便利店等）不处理。 */
    private fun stripBranchSuffix(text: String): String {
        val match = SEPARATOR_BRANCH_SUFFIX.find(text) ?: return text
        val suffix = match.value
        if (PROTECTED_STORE_WORDS.any { suffix.endsWith(it) }) return text
        return text.substring(0, match.range.first)
    }

    /** 全角字符转半角（含全角空格）。 */
    private fun toHalfWidth(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text) {
            builder.append(
                when {
                    ch == '\u3000' -> ' '
                    ch in '\uFF01'..'\uFF5E' -> (ch.code - FULL_WIDTH_OFFSET).toChar()
                    else -> ch
                },
            )
        }
        return builder.toString()
    }

    private companion object {
        const val FULL_WIDTH_OFFSET = 0xFEE0
        const val MAX_BRACKET_ROUNDS = 4

        /** 需要整组删除的括号内容：(门店)、(备注)、【活动】等。 */
        val BRACKET_GROUPS: List<Regex> = listOf(
            Regex("\\([^()]*\\)"),
            Regex("\\[[^\\[\\]]*\\]"),
            Regex("【[^【】]*】"),
            Regex("\\{[^{}]*\\}"),
            Regex("〔[^〔〕]*〕"),
            Regex("〈[^〈〉]*〉"),
            Regex("《[^《》]*》"),
            Regex("「[^「」]*」"),
        )

        val WHITESPACE: Regex = Regex("\\s+")

        /** 分隔符 + 门店名 + 店；例如「-望京路店」「·国贸店」。 */
        val SEPARATOR_BRANCH_SUFFIX: Regex =
            Regex("[-—–_·•]+[\\u4E00-\\u9FFF0-9a-z]{0,12}(?:路|街|道|巷|广场|中心|大厦)?(?:门店|分店|总店|店)$")

        /** 本身就是完整词语的「xx店」，不能被当成门店后缀删掉。 */
        val PROTECTED_STORE_WORDS: List<String> = listOf(
            "便利店", "药店", "酒店", "饭店", "水果店", "奶茶店",
            "书店", "咖啡店", "网店", "面包店",
        )

        val TRIM_CHARS: CharArray = charArrayOf(
            '-', '_', '—', '–', '·', '•', '.', ',', '，', '。', '、', '(', ')', '（', '）',
        )
    }
}
