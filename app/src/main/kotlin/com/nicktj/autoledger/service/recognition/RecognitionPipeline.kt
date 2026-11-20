package com.nicktj.autoledger.service.recognition

import android.content.Context
import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.MerchantRuleDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.data.repo.RecognitionLogRepository
import com.nicktj.autoledger.core.domain.Categorizer
import com.nicktj.autoledger.core.domain.DedupDecision
import com.nicktj.autoledger.core.domain.DedupEngine
import com.nicktj.autoledger.core.domain.DomainTxn
import com.nicktj.autoledger.core.domain.MerchantRule
import com.nicktj.autoledger.core.domain.RefundMatcher
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.ParsedTxn
import com.nicktj.autoledger.core.model.RecognitionOutcome
import com.nicktj.autoledger.core.model.TxnStatus
import com.nicktj.autoledger.core.model.TxnType
import com.nicktj.autoledger.core.parser.ParseResult
import com.nicktj.autoledger.core.parser.ParserRegistry
import com.nicktj.autoledger.core.parser.withChannel
import com.nicktj.autoledger.service.notify.TxnNotifier
import com.nicktj.autoledger.service.notify.displayName
import com.nicktj.autoledger.widget.TodaySpendWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 识别流水线：通知监听和无障碍兜底都只把原始文本丢进来，剩下的都在这里完成。
 *
 * 流程：找解析器 → 解析 → 分类 → 去重 → 退款冲抵 → 入库 → 写识别日志 → 发提醒。
 * 全部在 [Dispatchers.IO] 上跑，并用 [Mutex] 串行化，避免通知和无障碍同时上报时重复入库。
 */
@Singleton
class RecognitionPipeline @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val recognitionLogRepository: RecognitionLogRepository,
    private val notifier: TxnNotifier,
    @ApplicationContext private val context: Context,
) {

    // 这三个引擎都是无状态的纯计算类，直接持有即可，不必走 Hilt。
    private val categorizer = Categorizer()
    private val dedupEngine = DedupEngine()
    private val refundMatcher = RefundMatcher()

    /** 串行化整条流水线：同一时刻只处理一条通知。 */
    private val mutex = Mutex()

    /**
     * 处理一条来自通知栏或无障碍服务的文本。
     *
     * @param pkg 事件来源包名（支付宝 / 微信 / 云闪付）
     * @param title 通知标题
     * @param text 通知正文（无障碍通道传页面里识别出的支付结果文本）
     * @param bigText 展开后的长文本
     * @param channel 采集通道
     * @param occurredAt 事件发生时间（毫秒）
     */
    suspend fun handle(
        pkg: String,
        title: String?,
        text: String?,
        bigText: String?,
        channel: Channel,
        occurredAt: Long,
    ) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    process(pkg, title, text, bigText, channel, occurredAt)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    // 任何异常都要落一条 ERROR 日志，方便在自检面板里看到「为什么没记上」。
                    runCatching {
                        recognitionLogRepository.record(
                            receivedAt = System.currentTimeMillis(),
                            packageName = pkg,
                            channel = channel,
                            title = title,
                            text = text,
                            bigText = bigText,
                            outcome = RecognitionOutcome.ERROR,
                            note = "流水线异常：${throwable.javaClass.simpleName} ${throwable.message.orEmpty()}".trim(),
                        )
                    }
                }
            }
        }
    }

    private suspend fun process(
        pkg: String,
        title: String?,
        text: String?,
        bigText: String?,
        channel: Channel,
        occurredAt: Long,
    ) {
        // 1. 没有对应解析器的包名直接忽略（也顺手过滤掉其他 App 的通知）。
        val parser = ParserRegistry.forPackage(pkg) ?: return

        // 2. 解析；解析器只产出 NOTIFICATION 通道，这里统一改成实际采集通道。
        val result = parser.parse(title, text, bigText, occurredAt).withChannel(channel)

        val match: ParseResult.Match = when (result) {
            is ParseResult.Marketing -> {
                record(
                    pkg = pkg, channel = channel, title = title, text = text, bigText = bigText,
                    outcome = RecognitionOutcome.FILTERED_MARKETING,
                    note = "营销/账单提醒，已过滤",
                )
                return
            }

            is ParseResult.NotPayment -> {
                // 非支付通知量很大，同一段文本 10 分钟内只记一条日志。
                val noiseKey = text ?: bigText
                if (!recognitionLogRepository.isRepeatedNoise(pkg, noiseKey)) {
                    record(
                        pkg = pkg, channel = channel, title = title, text = text, bigText = bigText,
                        outcome = RecognitionOutcome.UNPARSED,
                        note = "非支付通知，已忽略",
                    )
                }
                return
            }

            is ParseResult.Match -> result
        }

        val parsed = match.txn
        // 金额缺失（微信无金额分支）时解析器会给 0 + needsAmount，这里两者都当「待补金额」。
        val needsAmount = match.needsAmount || parsed.amountCents <= 0L

        // 3. 分类：用户商户规则 > 内置关键词 > 兜底。
        val categories = categoryDao.getAll().map { it.toCategoryDef() }
        val userRules = merchantRuleDao.getAll().map {
            MerchantRule(merchantKey = it.merchantKey, categoryId = it.categoryId, updatedAt = it.updatedAt)
        }
        val base = DomainTxn(
            id = null,
            amountCents = parsed.amountCents,
            type = parsed.type,
            merchant = parsed.merchant,
            source = parsed.source,
            channel = parsed.channel,
            occurredAt = parsed.occurredAt,
            confidence = parsed.confidence,
            rawText = parsed.rawText,
        )
        var categoryId = categorizer.categorize(base, categories, userRules)
        var finalType = parsed.type
        var refundOfId: Long? = null
        var refundedExpense: TransactionEntity? = null

        // 4. 去重：只有金额 > 0 才参与，金额为 0 的占位记录一律当新记录。
        if (base.amountCents > 0L) {
            val recent = transactionDao
                .findBetween(base.occurredAt - dedupEngine.windowMs, base.occurredAt + dedupEngine.windowMs)
                .map { it.toDomainTxn() }
            when (val decision = dedupEngine.decide(base, recent)) {
                is DedupDecision.Merge -> {
                    mergeInto(decision.existing, decision.merged)
                    record(
                        pkg = pkg, channel = channel, title = title, text = text, bigText = bigText,
                        parsed = parsed,
                        outcome = RecognitionOutcome.DUPLICATE_MERGED,
                        linkedTxnId = decision.existing.id,
                        note = "与已有账单合并（通知 + 无障碍重复上报）",
                    )
                    return
                }

                DedupDecision.Skip -> {
                    record(
                        pkg = pkg, channel = channel, title = title, text = text, bigText = bigText,
                        parsed = parsed,
                        outcome = RecognitionOutcome.DUPLICATE_MERGED,
                        note = "完全重复，已忽略",
                    )
                    return
                }

                DedupDecision.New -> Unit
            }
        }

        // 5. 退款：先找原支出，找到就把退款冲抵到原账单上；找不到按普通收入记。
        if (finalType == TxnType.REFUND && base.amountCents > 0L) {
            val windowStart = base.occurredAt - refundMatcher.windowDays.toLong() * DAY_MS
            val candidates = transactionDao
                .findBetween(windowStart, base.occurredAt - 1L)
                .filter { it.status != TxnStatus.DISCARDED.name && it.type == TxnType.EXPENSE.name }
                .map { it.toDomainTxn() }
            val matched = refundMatcher.findMatch(base, candidates)
            val matchedEntity = matched?.id?.let { transactionDao.findById(it) }
            if (matchedEntity == null) {
                // 没匹配到原支出：按普通收入入库，避免退款金额被漏掉。
                finalType = TxnType.INCOME
                categoryId = categorizer.categorize(base.copy(type = TxnType.INCOME), categories, userRules)
            } else {
                refundOfId = matchedEntity.id
                refundedExpense = matchedEntity
            }
        }

        // 6. 置信度定状态：>= 0.8 直接确认，否则进待确认（金额缺失也必须待确认）。
        val status = if (needsAmount || parsed.confidence < CONFIRMED_THRESHOLD) {
            TxnStatus.PENDING
        } else {
            TxnStatus.CONFIRMED
        }

        val note = when {
            needsAmount -> "金额缺失，待手动补金额"
            refundedExpense != null -> "已冲抵原支出 #${refundedExpense?.id}"
            parsed.type == TxnType.REFUND && base.amountCents > 0L -> "未匹配到原支出，按收入记录"
            else -> null
        }

        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            amountCents = base.amountCents,
            type = finalType.name,
            categoryId = categoryId,
            merchant = parsed.merchant,
            source = parsed.source.name,
            channel = channel.name,
            occurredAt = parsed.occurredAt,
            note = note,
            confidence = parsed.confidence,
            status = status.name,
            refundOfId = refundOfId,
            refundedCents = 0L,
            createdAt = now,
            updatedAt = now,
        )
        val txnId = transactionDao.insert(entity)

        val outcome = if (status == TxnStatus.CONFIRMED) {
            RecognitionOutcome.INSERTED
        } else {
            RecognitionOutcome.PENDING
        }
        val logId = record(
            pkg = pkg, channel = channel, title = title, text = text, bigText = bigText,
            parsed = parsed,
            outcome = outcome,
            linkedTxnId = txnId,
            note = note,
        )
        // 账单只保留日志引用，原始文本随日志一起过期清理。
        transactionDao.update(entity.copy(id = txnId, rawLogId = logId))

        // 6b. 退款冲抵原支出：累加已退金额，但不超过原额。
        refundedExpense?.let { expense ->
            val remaining = (expense.amountCents - expense.refundedCents).coerceAtLeast(0L)
            val applied = minOf(remaining, base.amountCents)
            if (applied > 0L) {
                transactionDao.update(
                    expense.copy(
                        refundedCents = expense.refundedCents + applied,
                        updatedAt = now,
                    ),
                )
            }
        }

        // 7. 提醒：确认的才发通知；金额缺失单独提示补金额；待确认不打扰。
        val categoryName = categories.firstOrNull { it.id == categoryId }?.name
        when {
            needsAmount -> notifier.notifyNeedsAmount(txnId = txnId, sourceName = parsed.source.displayName())
            status == TxnStatus.CONFIRMED -> notifier.notifyTxnLogged(
                txnId = txnId,
                amountCents = base.amountCents,
                type = finalType,
                categoryName = categoryName,
                merchant = parsed.merchant,
            )
            else -> Unit
        }

        // 桌面组件立即刷新，避免要等下一个 updatePeriod。
        TodaySpendWidget.refresh(context)
    }

    /** 把重复上报里更完整的信息合并回已有账单。 */
    private suspend fun mergeInto(existing: DomainTxn, merged: DomainTxn) {
        val id = existing.id ?: return
        val entity = transactionDao.findById(id) ?: return
        // 合并后置信度够了，就把待确认升级成已确认。
        val status = if (
            entity.status == TxnStatus.PENDING.name && merged.confidence >= CONFIRMED_THRESHOLD
        ) {
            TxnStatus.CONFIRMED.name
        } else {
            entity.status
        }
        transactionDao.update(
            entity.copy(
                merchant = merged.merchant,
                channel = merged.channel.name,
                confidence = merged.confidence,
                categoryId = merged.categoryId ?: entity.categoryId,
                status = status,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** 写一条识别日志，返回日志 id。 */
    private suspend fun record(
        pkg: String,
        channel: Channel,
        title: String?,
        text: String?,
        bigText: String?,
        outcome: RecognitionOutcome,
        parsed: ParsedTxn? = null,
        linkedTxnId: Long? = null,
        note: String? = null,
    ): Long = recognitionLogRepository.record(
        receivedAt = System.currentTimeMillis(),
        packageName = pkg,
        channel = channel,
        title = title,
        text = text,
        bigText = bigText,
        outcome = outcome,
        parsedAmountCents = parsed?.amountCents?.takeIf { it > 0L },
        parsedType = parsed?.type,
        parsedMerchant = parsed?.merchant,
        confidence = parsed?.confidence ?: 0.0,
        linkedTxnId = linkedTxnId,
        note = note,
    )

    private companion object {
        /** 置信度阈值：≥ 0.8 直接入账，低于则进待确认。 */
        const val CONFIRMED_THRESHOLD = 0.8

        /** 一天毫秒数，用于退款回溯窗口。 */
        const val DAY_MS = 86_400_000L
    }
}
