package com.nicktj.autoledger.ui.screens.bills

import com.nicktj.autoledger.core.data.dao.MerchantRuleDao
import com.nicktj.autoledger.core.data.entity.MerchantRuleEntity
import com.nicktj.autoledger.core.domain.Categorizer

/**
 * 写一条「商户 -> 分类」学习规则。
 *
 * 先按 merchantKey 查一次把主键带上再 upsert：`@Upsert` 在
 * 「主键为 0 + merchantKey 唯一索引冲突」时只能走插入路径，会把更新丢掉。
 */
internal suspend fun MerchantRuleDao.learnCategoryRule(
    categorizer: Categorizer,
    merchant: String?,
    categoryId: Long,
) {
    val key = categorizer.normalize(merchant) ?: return
    val existing = findByKey(key)
    upsert(
        MerchantRuleEntity(
            id = existing?.id ?: 0L,
            merchantKey = key,
            categoryId = categoryId,
            updatedAt = System.currentTimeMillis(),
        ),
    )
}
