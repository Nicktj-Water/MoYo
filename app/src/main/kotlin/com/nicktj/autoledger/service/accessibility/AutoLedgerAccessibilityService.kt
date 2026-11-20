package com.nicktj.autoledger.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.service.recognition.RecognitionPipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 无障碍兜底：通知被折叠/被系统吞掉时，直接读支付结果页上的「支付成功」文案。
 *
 * 隐私边界：
 * - 只处理 `typeWindowStateChanged` / `typeWindowContentChanged`，**不处理** `TYPE_VIEW_TEXT_CHANGED`；
 * - 只读节点的 `text` / `contentDescription`，以及与支付关键词匹配的文本；
 * - 非匹配节点的文本只在内存里用于定位金额，不落库、不打日志；
 * - 2 秒内只处理一次，避免页面刷新重复入库。
 */
@AndroidEntryPoint
class AutoLedgerAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var pipeline: RecognitionPipeline

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 上一次真正处理的时间（elapsedRealtime，避免系统时间被改）。 */
    @Volatile
    private var lastHandledAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventValue = event ?: return

        // 只看窗口/内容变化；文本变化事件（输入框）直接忽略，不读输入内容。
        if (
            eventValue.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventValue.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val pkg = eventValue.packageName?.toString() ?: return
        if (PaySource.fromPackageName(pkg) == null) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHandledAt < PageTextInspector.DEBOUNCE_MS) return

        val root = rootInActiveWindow ?: return
        // 先用系统 API 粗筛：页面上有没有「支付成功」类关键词，没有就立刻返回，避免全量遍历。
        val hasKeyword = PageTextInspector.PAYMENT_KEYWORDS.any { keyword ->
            !root.findAccessibilityNodeInfosByText(keyword).isNullOrEmpty()
        }
        if (!hasKeyword) return

        // 深度 ≤ 25、节点数 ≤ 400，防止在长列表页面里卡住。
        val paymentText = PageTextInspector.extractPaymentText(collectTexts(root)) ?: return

        lastHandledAt = now
        val occurredAt = System.currentTimeMillis()
        scope.launch {
            pipeline.handle(
                pkg = pkg,
                title = null,
                text = paymentText,
                bigText = null,
                channel = Channel.ACCESSIBILITY,
                occurredAt = occurredAt,
            )
        }
    }

    /** 广度优先收集节点文本（text + contentDescription），带上深度与条数上限。 */
    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val result = ArrayList<String>(64)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES && result.size < MAX_TEXTS) {
            val (node, depth) = queue.removeFirst()
            visited++

            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) result.add(text)
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            if (description.isNotEmpty() && description != text) result.add(description)

            if (depth < MAX_DEPTH) {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    queue.addLast(child to depth + 1)
                }
            }
        }
        return result
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** 遍历最大深度。 */
        const val MAX_DEPTH = 25

        /** 最多访问的节点数。 */
        const val MAX_NODES = 400

        /** 最多收集的文本条数。 */
        const val MAX_TEXTS = 200
    }
}
