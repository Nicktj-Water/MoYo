package com.nicktj.autoledger.core.parser

import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真样本回归：读取 `src/test/resources/samples.json`，逐条跑三家解析器。
 *
 * 断言口径：
 * - `filtered=true`：营销/账单样本必须是 [ParseResult.Marketing]；聊天样本按接口语义是
 *   [ParseResult.NotPayment]（samples.json 把「该丢弃」统一标成 filtered=true，两者都不产生账单）。
 * - `amountCents != null`：金额必须精确到分。
 * - `type`：必须一致。
 * - 金额为 null 的两条（ali-05 / wx-11）：必须 needsAmount=true、amountCents=0、confidence=0.35。
 */
class SampleCorpusTest {

    private data class Case(
        val id: String,
        val scenario: String,
        val packageName: String,
        val title: String,
        val text: String,
        val bigText: String,
        val expectedType: String?,
        val expectedAmountCents: Long?,
        val expectedMerchant: String?,
        val filtered: Boolean,
    )

    private val cases: List<Case> by lazy(::loadCases)

    @Test
    fun `样本数量与交接文件一致`() {
        assertEquals("samples.json 应该有 39 条", 39, cases.size)
    }

    @Test
    fun `全部真实样本逐条回归`() {
        cases.forEach(::assertCase)
    }

    private fun assertCase(case: Case) {
        val parser = ParserRegistry.forPackage(case.packageName)
        assertNotNull("${case.id}: 没有注册 ${case.packageName} 的解析器", parser)

        val result = parser!!.parse(case.title, case.text, case.bigText, FIXED_TIME)
        val label = "${case.id}（${case.scenario}）"

        if (case.filtered) {
            // samples.json 的 filtered 表示「不应该产生账单」，既包含营销也包含聊天。
            // 接口把聊天/系统消息定义为 NotPayment，把营销/账单提醒定义为 Marketing；
            // 这里对两条聊天样本单独断言，其余 filtered 样本仍必须是 Marketing。
            if (case.id in CHAT_CASE_IDS) {
                assertTrue("$label 是聊天消息，应判为 NotPayment，实际是 $result", result is ParseResult.NotPayment)
            } else {
                assertTrue("$label 应判为 Marketing，实际是 $result", result is ParseResult.Marketing)
            }
            return
        }

        assertTrue("$label 应产生 Match，实际是 $result", result is ParseResult.Match)
        val match = result as ParseResult.Match
        val txn = match.txn

        val expectedType = case.expectedType
        assertNotNull("$label 缺少 expected.type", expectedType)
        assertEquals("$label 类型不一致", TxnType.valueOf(expectedType!!), txn.type)

        if (case.expectedAmountCents == null) {
            assertTrue("$label 金额缺失时必须 needsAmount=true", match.needsAmount)
            assertEquals("$label 金额缺失时 amountCents 用 0 占位", 0L, txn.amountCents)
            assertEquals("$label 金额缺失时 confidence 应为 0.35", 0.35, txn.confidence, 1e-9)
        } else {
            assertEquals("$label 金额不一致", case.expectedAmountCents, txn.amountCents)
            assertFalse("$label 有金额时不应 needsAmount", match.needsAmount)
        }

        if (case.expectedMerchant != null) {
            assertEquals("$label 商户不一致", case.expectedMerchant, txn.merchant)
        } else {
            assertNull("$label 期望抽不出商户，实际是 ${txn.merchant}", txn.merchant)
        }

        assertEquals("$label source 不一致", PaySource.fromPackageName(case.packageName), txn.source)
        assertEquals("$label 解析器默认输出通知通道", Channel.NOTIFICATION, txn.channel)
    }

    private fun loadCases(): List<Case> {
        val stream = javaClass.getResourceAsStream("/samples.json")
            ?: error("测试资源 samples.json 不存在")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = MiniJson.parse(text) as? List<*>
            ?: error("samples.json 顶层必须是数组")
        return root.map { item ->
            val obj = item as? Map<*, *> ?: error("样本必须是 JSON 对象")
            val expected = obj["expected"] as? Map<*, *> ?: error("样本缺少 expected 对象")
            Case(
                id = obj.string("id"),
                scenario = obj.string("scenario"),
                packageName = obj.string("packageName"),
                title = obj.string("title"),
                text = obj.string("text"),
                bigText = obj.string("bigText"),
                expectedType = expected["type"] as? String,
                expectedAmountCents = (expected["amountCents"] as? Number)?.toLong(),
                expectedMerchant = expected["merchant"] as? String,
                filtered = expected["filtered"] as? Boolean ?: false,
            )
        }
    }

    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: ""

    private companion object {
        const val FIXED_TIME = 1_700_000_000_000L

        /** 这两条在 samples.json 里 filtered=true，但语义是聊天消息，应返回 NotPayment。 */
        val CHAT_CASE_IDS = setOf("ali-12", "wx-10")
    }
}
