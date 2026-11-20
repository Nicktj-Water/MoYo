package com.nicktj.autoledger.core.domain

/** 去重引擎对一条新账单给出的结论。 */
sealed interface DedupDecision {

    /** 全新的一笔，正常入库。 */
    data object New : DedupDecision

    /**
     * 与已有的某一笔是同一笔交易，但带来了更完整的信息。
     *
     * @property existing 库里已有的那条记录
     * @property merged 合并之后的完整对象（id 沿用 [existing] 的 id）
     */
    data class Merge(val existing: DomainTxn, val merged: DomainTxn) : DedupDecision

    /** 完全重复，没有任何新信息，直接丢弃。 */
    data object Skip : DedupDecision
}
