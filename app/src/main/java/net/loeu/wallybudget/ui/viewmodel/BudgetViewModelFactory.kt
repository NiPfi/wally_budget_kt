package net.loeu.wallybudget.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import net.loeu.wallybudget.data.local.db.BudgetDatabase
import net.loeu.wallybudget.data.local.preferences.UserPreferencesManager
import net.loeu.wallybudget.data.snapshot.AndroidDocumentUriGateway
import net.loeu.wallybudget.data.snapshot.GzipSnapshotCodec
import net.loeu.wallybudget.data.snapshot.SnapshotCompatibilityService
import net.loeu.wallybudget.data.snapshot.SnapshotHasher
import net.loeu.wallybudget.data.snapshot.SnapshotJsonCodec
import net.loeu.wallybudget.data.time.SystemCurrentDateProvider
import net.loeu.wallybudget.domain.service.BudgetCalculationService
import net.loeu.wallybudget.domain.service.BudgetAdjustmentResolver
import net.loeu.wallybudget.domain.service.BucketAllocationResolver
import net.loeu.wallybudget.domain.service.CycleScheduleResolver
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.service.PortfolioCalculationService
import net.loeu.wallybudget.domain.service.SpendingForecastCalculator
import net.loeu.wallybudget.domain.usecase.ApplyOnboardingRestoreUseCase
import net.loeu.wallybudget.domain.usecase.AddExpenseUseCase
import net.loeu.wallybudget.domain.usecase.CompleteOnboardingUseCase
import net.loeu.wallybudget.domain.usecase.ConcludePendingCycleUseCase
import net.loeu.wallybudget.domain.usecase.ClearPendingSettingsUndoUseCase
import net.loeu.wallybudget.domain.usecase.DeleteExpenseUseCase
import net.loeu.wallybudget.domain.usecase.EnsureDefaultBucketStateUseCase
import net.loeu.wallybudget.domain.usecase.EnsureBudgetPolicyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ExportSnapshotUseCase
import net.loeu.wallybudget.domain.usecase.ObserveBudgetBucketsUseCase
import net.loeu.wallybudget.domain.usecase.ObserveForecastUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHistoryUseCase
import net.loeu.wallybudget.domain.usecase.ObserveHomeOverviewUseCase
import net.loeu.wallybudget.domain.usecase.PerformMonthlyResetUseCase
import net.loeu.wallybudget.domain.usecase.PrepareSnapshotImportUseCase
import net.loeu.wallybudget.domain.usecase.ResolveMutationEffectiveDateUseCase
import net.loeu.wallybudget.domain.usecase.RestoreDeletedExpenseUseCase
import net.loeu.wallybudget.domain.usecase.RebuildBucketMonthlyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.RebuildMonthlyHistoryUseCase
import net.loeu.wallybudget.domain.usecase.SelectBucketUseCase
import net.loeu.wallybudget.domain.usecase.SyncObservedDateUseCase
import net.loeu.wallybudget.domain.usecase.UndoBudgetSettingsChangeUseCase
import net.loeu.wallybudget.domain.usecase.UpdatePaydayUseCase
import net.loeu.wallybudget.domain.usecase.UpdatePortfolioPlanUseCase
import net.loeu.wallybudget.domain.usecase.UpdateExpenseUseCase

class BudgetViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    private val hybridLogicalClockService by lazy { HybridLogicalClockService() }

    private val userPreferencesManager by lazy {
        UserPreferencesManager(
            context.applicationContext,
            hybridLogicalClockService = hybridLogicalClockService
        )
    }

    private val installId by lazy {
        UserPreferencesManager.getOrCreateInstallId(context.applicationContext)
    }

    private val database by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            BudgetDatabase::class.java,
            "budget_database"
        )
            .addMigrations(
                BudgetDatabase.MIGRATION_1_2,
                BudgetDatabase.MIGRATION_2_3,
                BudgetDatabase.MIGRATION_3_4,
                BudgetDatabase.MIGRATION_4_5,
                BudgetDatabase.MIGRATION_5_6,
                BudgetDatabase.MIGRATION_6_7,
                BudgetDatabase.migration7To8(installId),
                BudgetDatabase.MIGRATION_8_9,
                BudgetDatabase.MIGRATION_9_10,
                BudgetDatabase.MIGRATION_10_11
            )
            .build()
    }

    private val currentDateProvider by lazy {
        SystemCurrentDateProvider(context.applicationContext)
    }
    private val documentUriGateway by lazy {
        AndroidDocumentUriGateway(context.applicationContext)
    }
    private val snapshotJsonCodec by lazy { SnapshotJsonCodec() }
    private val gzipSnapshotCodec by lazy { GzipSnapshotCodec() }
    private val snapshotHasher by lazy { SnapshotHasher() }
    private val snapshotCompatibilityService by lazy { SnapshotCompatibilityService() }
    private val appVersionName by lazy {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName
                ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private val forecastCalculator by lazy {
        SpendingForecastCalculator()
    }

    private val budgetCalculationService by lazy {
        BudgetCalculationService(forecastCalculator)
    }
    private val budgetAdjustmentResolver by lazy { BudgetAdjustmentResolver() }
    private val bucketAllocationResolver by lazy { BucketAllocationResolver() }
    private val portfolioCalculationService by lazy { PortfolioCalculationService() }
    private val cycleScheduleResolver by lazy { CycleScheduleResolver(budgetCalculationService) }

    private val expenseDao by lazy { database.expenseDao() }
    private val monthlyHistoryDao by lazy { database.monthlyHistoryDao() }
    private val cycleOverviewDao by lazy { database.cycleOverviewDao() }
    private val budgetPolicyDao by lazy { database.budgetPolicyDao() }
    private val budgetAdjustmentDao by lazy { database.budgetAdjustmentDao() }
    private val budgetBucketDao by lazy { database.budgetBucketDao() }
    private val bucketAllocationPolicyDao by lazy { database.bucketAllocationPolicyDao() }
    private val bucketAllocationAdjustmentDao by lazy { database.bucketAllocationAdjustmentDao() }
    private val bucketMonthlyHistoryDao by lazy { database.bucketMonthlyHistoryDao() }

    private val observeHomeOverviewUseCase by lazy {
        ObserveHomeOverviewUseCase(
            expenseDao = expenseDao,
            cycleOverviewDao = cycleOverviewDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            userSettingsStore = userPreferencesManager,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            bucketAllocationResolver = bucketAllocationResolver,
            portfolioCalculationService = portfolioCalculationService
        )
    }

    private val observeBudgetBucketsUseCase by lazy {
        ObserveBudgetBucketsUseCase(budgetBucketDao)
    }

    private val observeHistoryUseCase by lazy {
        ObserveHistoryUseCase(
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            userSettingsStore = userPreferencesManager,
            cycleScheduleResolver = cycleScheduleResolver
        )
    }

    private val observeForecastUseCase by lazy {
        ObserveForecastUseCase(
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            expenseDao = expenseDao,
            userSettingsStore = userPreferencesManager,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            bucketAllocationResolver = bucketAllocationResolver
        )
    }

    private val addExpenseUseCase by lazy {
        AddExpenseUseCase(expenseDao, userPreferencesManager, hybridLogicalClockService)
    }
    private val updateExpenseUseCase by lazy {
        UpdateExpenseUseCase(expenseDao, userPreferencesManager, hybridLogicalClockService)
    }
    private val deleteExpenseUseCase by lazy {
        DeleteExpenseUseCase(expenseDao, userPreferencesManager, hybridLogicalClockService)
    }
    private val restoreDeletedExpenseUseCase by lazy {
        RestoreDeletedExpenseUseCase(expenseDao, userPreferencesManager, hybridLogicalClockService)
    }
    private val ensureBudgetPolicyHistoryUseCase by lazy {
        EnsureBudgetPolicyHistoryUseCase(
            budgetPolicyDao = budgetPolicyDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userSettingsStore = userPreferencesManager,
            budgetCalculationService = budgetCalculationService,
            hybridLogicalClockService = hybridLogicalClockService
        )
    }
    private val rebuildMonthlyHistoryUseCase by lazy {
        RebuildMonthlyHistoryUseCase(
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            expenseDao = expenseDao,
            monthlyHistoryDao = monthlyHistoryDao,
            budgetCalculationService = budgetCalculationService,
            budgetAdjustmentResolver = budgetAdjustmentResolver
        )
    }
    private val rebuildBucketMonthlyHistoryUseCase by lazy {
        RebuildBucketMonthlyHistoryUseCase(
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            expenseDao = expenseDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            budgetCalculationService = budgetCalculationService,
            bucketAllocationResolver = bucketAllocationResolver
        )
    }
    private val updatePortfolioPlanUseCase by lazy {
        UpdatePortfolioPlanUseCase(
            transactionRunner = database,
            userSettingsStore = userPreferencesManager,
            budgetPolicyDao = budgetPolicyDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            currentDateProvider = currentDateProvider,
            cycleScheduleResolver = cycleScheduleResolver,
            hybridLogicalClockService = hybridLogicalClockService
        )
    }
    private val updatePaydayUseCase by lazy {
        UpdatePaydayUseCase(
            transactionRunner = database,
            userSettingsStore = userPreferencesManager,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            currentDateProvider = currentDateProvider,
            cycleScheduleResolver = cycleScheduleResolver,
            hybridLogicalClockService = hybridLogicalClockService
        )
    }
    private val undoBudgetSettingsChangeUseCase by lazy {
        UndoBudgetSettingsChangeUseCase(
            transactionRunner = database,
            userSettingsStore = userPreferencesManager,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            currentDateProvider = currentDateProvider
        )
    }
    private val clearPendingSettingsUndoUseCase by lazy {
        ClearPendingSettingsUndoUseCase(userPreferencesManager)
    }
    private val selectBucketUseCase by lazy {
        SelectBucketUseCase(userPreferencesManager)
    }
    private val completeOnboardingUseCase by lazy {
        CompleteOnboardingUseCase(
            transactionRunner = database,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            budgetPolicyDao = budgetPolicyDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userSettingsStore = userPreferencesManager,
            currentDateProvider = currentDateProvider,
            budgetCalculationService = budgetCalculationService,
            hybridLogicalClockService = hybridLogicalClockService
        )
    }
    private val ensureDefaultBucketStateUseCase by lazy {
        EnsureDefaultBucketStateUseCase(
            transactionRunner = database,
            userSettingsStore = userPreferencesManager,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            budgetCalculationService = budgetCalculationService,
            hybridLogicalClockService = hybridLogicalClockService
        )
    }
    private val performMonthlyResetUseCase by lazy {
        PerformMonthlyResetUseCase(
            transactionRunner = database,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userSettingsStore = userPreferencesManager,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            rebuildBucketMonthlyHistoryUseCase = rebuildBucketMonthlyHistoryUseCase,
            hybridLogicalClockService = hybridLogicalClockService
        )
    }
    private val concludePendingCycleUseCase by lazy {
        ConcludePendingCycleUseCase(
            transactionRunner = database,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            monthlyHistoryDao = monthlyHistoryDao,
            userSettingsStore = userPreferencesManager,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver,
            budgetAdjustmentResolver = budgetAdjustmentResolver,
            rebuildBucketMonthlyHistoryUseCase = rebuildBucketMonthlyHistoryUseCase
        )
    }
    private val exportSnapshotUseCase by lazy {
        ExportSnapshotUseCase(
            documentUriGateway = documentUriGateway,
            gzipSnapshotCodec = gzipSnapshotCodec,
            snapshotJsonCodec = snapshotJsonCodec,
            snapshotHasher = snapshotHasher,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            userSettingsStore = userPreferencesManager,
            hybridLogicalClockService = hybridLogicalClockService,
            appVersionName = appVersionName
        )
    }
    private val prepareSnapshotImportUseCase by lazy {
        PrepareSnapshotImportUseCase(
            documentUriGateway = documentUriGateway,
            gzipSnapshotCodec = gzipSnapshotCodec,
            snapshotJsonCodec = snapshotJsonCodec,
            snapshotCompatibilityService = snapshotCompatibilityService
        )
    }
    private val applyOnboardingRestoreUseCase by lazy {
        ApplyOnboardingRestoreUseCase(
            transactionRunner = database,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetAdjustmentDao = budgetAdjustmentDao,
            budgetBucketDao = budgetBucketDao,
            bucketAllocationPolicyDao = bucketAllocationPolicyDao,
            bucketAllocationAdjustmentDao = bucketAllocationAdjustmentDao,
            bucketMonthlyHistoryDao = bucketMonthlyHistoryDao,
            userSettingsStore = userPreferencesManager,
            rebuildMonthlyHistoryUseCase = rebuildMonthlyHistoryUseCase,
            rebuildBucketMonthlyHistoryUseCase = rebuildBucketMonthlyHistoryUseCase
        )
    }
    private val resolveMutationEffectiveDateUseCase by lazy {
        ResolveMutationEffectiveDateUseCase(
            userSettingsStore = userPreferencesManager,
            expenseDao = expenseDao,
            budgetPolicyDao = budgetPolicyDao,
            budgetCalculationService = budgetCalculationService,
            cycleScheduleResolver = cycleScheduleResolver
        )
    }
    private val syncObservedDateUseCase by lazy { SyncObservedDateUseCase(userPreferencesManager) }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BudgetViewModel::class.java)) {
            return BudgetViewModel(
                upstreamUserSettingsFlow = userPreferencesManager.userSettings,
                observeHomeOverviewUseCase = observeHomeOverviewUseCase,
                observeBudgetBucketsUseCase = observeBudgetBucketsUseCase,
                observeHistoryUseCase = observeHistoryUseCase,
                observeForecastUseCase = observeForecastUseCase,
                addExpenseUseCase = addExpenseUseCase,
                updateExpenseUseCase = updateExpenseUseCase,
                deleteExpenseUseCase = deleteExpenseUseCase,
                restoreDeletedExpenseUseCase = restoreDeletedExpenseUseCase,
                updatePortfolioPlanUseCase = updatePortfolioPlanUseCase,
                updatePaydayUseCase = updatePaydayUseCase,
                undoBudgetSettingsChangeUseCase = undoBudgetSettingsChangeUseCase,
                completeOnboardingUseCase = completeOnboardingUseCase,
                performMonthlyResetUseCase = performMonthlyResetUseCase,
                concludePendingCycleUseCase = concludePendingCycleUseCase,
                exportSnapshotUseCase = exportSnapshotUseCase,
                prepareSnapshotImportUseCase = prepareSnapshotImportUseCase,
                applyOnboardingRestoreUseCase = applyOnboardingRestoreUseCase,
                ensureDefaultBucketStateUseCase = ensureDefaultBucketStateUseCase,
                ensureBudgetPolicyHistoryUseCase = ensureBudgetPolicyHistoryUseCase,
                rebuildMonthlyHistoryUseCase = rebuildMonthlyHistoryUseCase,
                resolveMutationEffectiveDateUseCase = resolveMutationEffectiveDateUseCase,
                selectBucketUseCase = selectBucketUseCase,
                clearPendingSettingsUndoUseCase = clearPendingSettingsUndoUseCase,
                pendingSettingsUndoFlow = userPreferencesManager.pendingSettingsUndo,
                syncObservedDateUseCase = syncObservedDateUseCase,
                currentDateProvider = currentDateProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
