package com.nicktj.autoledger.core.data

import com.nicktj.autoledger.core.data.db.PRESET_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预置分类的纯 JVM 测试。
 *
 * 注：Room DAO 的行为测试需要 Android 运行时，已改为 instrumented 测试
 * （见 app/src/androidTest），这里只守住「建库必须写入哪些分类」这个契约。
 */
class PresetCategoryTest {

    @Test
    fun `contains the eleven builtin categories`() {
        assertEquals(12, PRESET_CATEGORIES.size)
        val names = PRESET_CATEGORIES.map { it.name }
        listOf("餐饮", "交通", "购物", "娱乐", "生活缴费", "医疗", "转账", "红包", "其他", "未分类", "收入", "退款")
            .forEach { assertTrue("缺少分类 $it", it in names) }
    }

    @Test
    fun `ids are unique and positive`() {
        val ids = PRESET_CATEGORIES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it > 0 })
    }

    @Test
    fun `kinds are valid`() {
        PRESET_CATEGORIES.forEach {
            assertTrue("非法 kind: ${it.kind}", it.kind in setOf("EXPENSE", "INCOME", "BOTH"))
        }
    }
}
