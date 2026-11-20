package com.nicktj.autoledger.core.data.di

import android.content.Context
import androidx.room.Room
import com.nicktj.autoledger.core.data.dao.BudgetDao
import com.nicktj.autoledger.core.data.dao.CategoryDao
import com.nicktj.autoledger.core.data.dao.MerchantRuleDao
import com.nicktj.autoledger.core.data.dao.RecognitionLogDao
import com.nicktj.autoledger.core.data.dao.TransactionDao
import com.nicktj.autoledger.core.data.db.AutoLedgerDatabase
import com.nicktj.autoledger.core.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoLedgerDatabase =
        Room.databaseBuilder(context, AutoLedgerDatabase::class.java, AutoLedgerDatabase.NAME)
            .addCallback(AutoLedgerDatabase.prepopulateCallback)
            .build()

    @Provides
    fun provideTransactionDao(database: AutoLedgerDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: AutoLedgerDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideMerchantRuleDao(database: AutoLedgerDatabase): MerchantRuleDao = database.merchantRuleDao()

    @Provides
    fun provideRecognitionLogDao(database: AutoLedgerDatabase): RecognitionLogDao =
        database.recognitionLogDao()

    @Provides
    fun provideBudgetDao(database: AutoLedgerDatabase): BudgetDao = database.budgetDao()

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}
