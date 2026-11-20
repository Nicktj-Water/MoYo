package com.nicktj.autoledger.core.domain

import com.nicktj.autoledger.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorizerTest {

    private val categorizer = Categorizer()

    private fun expenseOf(merchant: String?, type: TxnType = TxnType.EXPENSE): DomainTxn =
        txn(id = null, amountCents = 2_000L, type = type, merchant = merchant)

    // ---------- 用户规则 ----------

    @Test
    fun `用户规则优先于内置关键词`() {
        val rules = listOf(MerchantRule("美团", Cats.MEDICAL, 1L))

        assertEquals(
            Cats.MEDICAL,
            categorizer.categorize(expenseOf("美团"), DEFAULT_CATEGORIES, rules),
        )
    }

    @Test
    fun `用户规则按归一化后的商户精确匹配`() {
        val rules = listOf(MerchantRule("瑞幸咖啡", Cats.ENTERTAINMENT, 1L))

        assertEquals(
            Cats.ENTERTAINMENT,
            categorizer.categorize(expenseOf("瑞幸咖啡（国贸店）"), DEFAULT_CATEGORIES, rules),
        )
    }

    @Test
    fun `同商户多条规则时最新的生效`() {
        val rules = listOf(
            MerchantRule("美团", Cats.MEDICAL, 100L),
            MerchantRule("美团", Cats.ENTERTAINMENT, 200L),
        )

        assertEquals(
            Cats.ENTERTAINMENT,
            categorizer.categorize(expenseOf("美团"), DEFAULT_CATEGORIES, rules),
        )
    }

    @Test
    fun `失效的用户规则回退到内置关键词`() {
        val rules = listOf(MerchantRule("美团", 999L, 1L))

        assertEquals(
            Cats.DINING,
            categorizer.categorize(expenseOf("美团"), DEFAULT_CATEGORIES, rules),
        )
    }

    // ---------- 内置关键词 ----------

    @Test
    fun `餐饮关键词命中`() {
        assertEquals(
            Cats.DINING,
            categorizer.categorize(expenseOf("肯德基(中关村店)"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `交通关键词命中`() {
        assertEquals(
            Cats.TRANSPORT,
            categorizer.categorize(expenseOf("滴滴出行"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `购物关键词命中`() {
        assertEquals(
            Cats.SHOPPING,
            categorizer.categorize(expenseOf("京东"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `娱乐关键词命中`() {
        assertEquals(
            Cats.ENTERTAINMENT,
            categorizer.categorize(expenseOf("腾讯视频"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `生活缴费关键词命中`() {
        assertEquals(
            Cats.UTILITIES,
            categorizer.categorize(expenseOf("中国移动"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `医疗关键词命中`() {
        assertEquals(
            Cats.MEDICAL,
            categorizer.categorize(expenseOf("大药房"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `转账关键词命中`() {
        assertEquals(
            Cats.TRANSFER,
            categorizer.categorize(expenseOf("微信转账"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `购物类店名不会因为保护词被误分类`() {
        assertEquals(
            Cats.SHOPPING,
            categorizer.categorize(expenseOf("全家便利店"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `全角数字归一化后仍能命中关键词`() {
        assertEquals(
            Cats.TRANSPORT,
            categorizer.categorize(expenseOf("１２３０６"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `英文关键词忽略大小写`() {
        assertEquals(
            Cats.ENTERTAINMENT,
            categorizer.categorize(expenseOf("KTV"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `内置关键词按先长后短匹配`() {
        val builtin = listOf(
            BuiltinRule(listOf("咖啡"), BuiltinCategoryRules.NAME_DINING),
            BuiltinRule(listOf("咖啡店"), BuiltinCategoryRules.NAME_SHOPPING),
        )
        val custom = Categorizer(builtin)

        assertEquals(
            Cats.SHOPPING,
            custom.categorize(expenseOf("星巴克咖啡店"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    // ---------- 归一化 ----------

    @Test
    fun `归一化去掉括号里的门店名`() {
        assertEquals("星巴克", categorizer.normalize("星巴克（国贸店）"))
    }

    @Test
    fun `归一化去掉分隔符门店后缀`() {
        assertEquals("美团", categorizer.normalize("美团-望京路店"))
    }

    @Test
    fun `归一化全角转半角并转小写`() {
        assertEquals("starbucks咖啡", categorizer.normalize("ＳＴＡＲＢＵＣＫＳ　咖啡"))
    }

    @Test
    fun `归一化去掉所有空白`() {
        assertEquals("美团外卖", categorizer.normalize(" 美 团 外 卖 "))
    }

    @Test
    fun `归一化空白输入返回 null`() {
        assertNull(categorizer.normalize(null))
        assertNull(categorizer.normalize("   "))
        assertNull(categorizer.normalize("（ ）"))
    }

    @Test
    fun `归一化不会误删便利店这类真实店名`() {
        assertEquals("7-11便利店", categorizer.normalize("7-11便利店"))
    }

    // ---------- 类型特判 ----------

    @Test
    fun `收到红包直接归红包`() {
        assertEquals(
            Cats.RED_PACKET,
            categorizer.categorize(expenseOf("美团", TxnType.RED_PACKET_IN), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `发出红包直接归红包`() {
        assertEquals(
            Cats.RED_PACKET,
            categorizer.categorize(expenseOf("美团", TxnType.RED_PACKET_OUT), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `退款直接归退款`() {
        assertEquals(
            Cats.REFUND,
            categorizer.categorize(expenseOf("美团", TxnType.REFUND), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `收入没有命中时归收入`() {
        assertEquals(
            Cats.INCOME,
            categorizer.categorize(expenseOf("某某公司", TxnType.INCOME), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `收入命中用户规则时以规则为准`() {
        val rules = listOf(MerchantRule("某某公司", Cats.TRANSFER, 1L))

        assertEquals(
            Cats.TRANSFER,
            categorizer.categorize(expenseOf("某某公司", TxnType.INCOME), DEFAULT_CATEGORIES, rules),
        )
    }

    // ---------- 兜底与缺分类 ----------

    @Test
    fun `支出没有命中时归未分类`() {
        assertEquals(
            Cats.UNCATEGORIZED,
            categorizer.categorize(expenseOf("张三"), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `商户为空时支出归未分类`() {
        assertEquals(
            Cats.UNCATEGORIZED,
            categorizer.categorize(expenseOf(null), DEFAULT_CATEGORIES, emptyList()),
        )
    }

    @Test
    fun `缺少未分类分类时返回 null`() {
        val categories = DEFAULT_CATEGORIES.filterNot {
            it.name == BuiltinCategoryRules.NAME_UNCATEGORIZED
        }

        assertNull(categorizer.categorize(expenseOf("张三"), categories, emptyList()))
    }

    @Test
    fun `缺少对应内置分类名时返回 null`() {
        val categories = DEFAULT_CATEGORIES.filterNot {
            it.name == BuiltinCategoryRules.NAME_DINING
        }

        assertNull(categorizer.categorize(expenseOf("美团"), categories, emptyList()))
    }

    @Test
    fun `缺少红包分类时返回 null`() {
        val categories = DEFAULT_CATEGORIES.filterNot {
            it.name == BuiltinCategoryRules.NAME_RED_PACKET
        }

        assertNull(
            categorizer.categorize(
                expenseOf("美团", TxnType.RED_PACKET_IN),
                categories,
                emptyList(),
            ),
        )
    }
}
