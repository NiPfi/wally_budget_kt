package net.loeu.wallybudget.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction
import net.loeu.wallybudget.data.local.dao.BudgetAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BudgetBucketDao
import net.loeu.wallybudget.data.local.dao.BudgetPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationAdjustmentDao
import net.loeu.wallybudget.data.local.dao.BucketAllocationPolicyDao
import net.loeu.wallybudget.data.local.dao.BucketCycleBaselineDao
import net.loeu.wallybudget.data.local.dao.BucketMonthlyHistoryDao
import net.loeu.wallybudget.data.local.dao.BucketTransferDao
import net.loeu.wallybudget.data.local.dao.CycleOverviewDao
import net.loeu.wallybudget.data.local.dao.ExpenseDao
import net.loeu.wallybudget.data.local.dao.FundDao
import net.loeu.wallybudget.data.local.dao.FundTransactionDao
import net.loeu.wallybudget.data.local.dao.MonthlyHistoryDao
import net.loeu.wallybudget.data.local.entity.BudgetAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BudgetBucketEntity
import net.loeu.wallybudget.data.local.entity.BudgetPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationAdjustmentEntity
import net.loeu.wallybudget.data.local.entity.BucketAllocationPolicyEntity
import net.loeu.wallybudget.data.local.entity.BucketCycleBaselineEntity
import net.loeu.wallybudget.data.local.entity.BucketMonthlyHistoryEntity
import net.loeu.wallybudget.data.local.entity.BucketTransferEntity
import net.loeu.wallybudget.data.local.entity.ExpenseEntity
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.entity.FundTransactionEntity
import net.loeu.wallybudget.data.local.entity.MonthlyHistoryEntity

@Database(
    entities = [
        ExpenseEntity::class,
        MonthlyHistoryEntity::class,
        BudgetPolicyEntity::class,
        BudgetAdjustmentEntity::class,
        BudgetBucketEntity::class,
        BucketAllocationPolicyEntity::class,
        BucketCycleBaselineEntity::class,
        BucketTransferEntity::class,
        BucketAllocationAdjustmentEntity::class,
        BucketMonthlyHistoryEntity::class,
        FundEntity::class,
        FundTransactionEntity::class
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
@Suppress("TooManyFunctions")
abstract class BudgetDatabase : RoomDatabase(), TransactionRunner {
    abstract fun expenseDao(): ExpenseDao
    abstract fun monthlyHistoryDao(): MonthlyHistoryDao
    abstract fun cycleOverviewDao(): CycleOverviewDao
    abstract fun budgetPolicyDao(): BudgetPolicyDao
    abstract fun budgetAdjustmentDao(): BudgetAdjustmentDao
    abstract fun budgetBucketDao(): BudgetBucketDao
    abstract fun bucketAllocationPolicyDao(): BucketAllocationPolicyDao
    abstract fun bucketCycleBaselineDao(): BucketCycleBaselineDao
    abstract fun bucketTransferDao(): BucketTransferDao
    abstract fun bucketAllocationAdjustmentDao(): BucketAllocationAdjustmentDao
    abstract fun bucketMonthlyHistoryDao(): BucketMonthlyHistoryDao
    abstract fun fundDao(): FundDao
    abstract fun fundTransactionDao(): FundTransactionDao

    override suspend fun <T> inTransaction(block: suspend () -> T): T = withTransaction { block() }
}
