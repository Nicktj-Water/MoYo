package com.nicktj.autoledger.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nicktj.autoledger.core.data.db.AutoLedgerDatabase
import com.nicktj.autoledger.core.data.entity.MerchantRuleEntity
import com.nicktj.autoledger.core.data.entity.TransactionEntity
import com.nicktj.autoledger.core.model.Channel
import com.nicktj.autoledger.core.model.PaySource
import com.nicktj.autoledger.core.model.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoLedgerDatabaseTest {

    private lateinit var database: AutoLedgerDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AutoLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(AutoLedgerDatabase.prepopulateCallback)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserts and queries a transaction`() = runBlocking {
        val dao = database.transactionDao()
        val id = dao.insert(
            TransactionEntity(
                amountCents = 12_345L,
                type = TxnType.EXPENSE.name,
                source = PaySource.ALIPAY.name,
                channel = Channel.NOTIFICATION.name,
                occurredAt = 1_700_000_000_000L,
                status = "CONFIRMED",
            ),
        )

        assertTrue(id > 0L)

        val rows = dao.observeBetween(1_699_000_000_000L, 1_701_000_000_000L).first()
        assertEquals(1, rows.size)
        assertEquals(12_345L, rows.first().amountCents)
        assertEquals(TxnType.EXPENSE.name, rows.first().type)

        val pending = dao.observePending().first()
        assertTrue(pending.isEmpty())
    }

    @Test
    fun `prepopulates builtin categories on create`() = runBlocking {
        val categories = database.categoryDao().getAll()

        assertEquals(12, categories.size)
        assertEquals(12, database.categoryDao().count())
        assertTrue(categories.all { it.builtin })
        assertTrue(categories.any { it.name == "餐饮" && it.kind == "EXPENSE" })
        assertTrue(categories.any { it.name == "收入" && it.kind == "INCOME" })
        assertEquals(categories.sortedBy { it.sortOrder }, categories)
    }

    @Test
    fun `upserts merchant rule by unique key`() = runBlocking {
        val dao = database.merchantRuleDao()
        dao.upsert(MerchantRuleEntity(merchantKey = "星巴克", categoryId = 1L))
        dao.upsert(MerchantRuleEntity(merchantKey = "星巴克", categoryId = 3L))

        val rules = dao.getAll()
        assertEquals(1, rules.size)
        assertEquals(3L, rules.first().categoryId)
    }
}
