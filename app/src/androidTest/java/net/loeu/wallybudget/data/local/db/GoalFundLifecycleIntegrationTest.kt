package net.loeu.wallybudget.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.loeu.wallybudget.data.local.entity.FundEntity
import net.loeu.wallybudget.data.local.preferences.UserSettingsStore
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.domain.model.PendingPaydayUndo
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.service.HybridLogicalClockService
import net.loeu.wallybudget.domain.usecase.CreateGoalFundRequest
import net.loeu.wallybudget.domain.usecase.CreateGoalFundUseCase
import net.loeu.wallybudget.domain.usecase.UpdateGoalFundRequest
import net.loeu.wallybudget.domain.usecase.UpdateGoalFundUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GoalFundLifecycleIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val hybridLogicalClockService = HybridLogicalClockService()

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun createGoalFundPersistsTargetAndSortOrder() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            database.fundDao().insert(
                fundEntity(
                    uuid = "reserve",
                    name = "Savings",
                    fundType = FundType.DEFAULT_RESERVE,
                    targetAmountCents = null,
                    sortOrder = 0
                )
            )
            val useCase = CreateGoalFundUseCase(
                fundDao = database.fundDao(),
                userSettingsStore = TestUserSettingsStore(),
                hybridLogicalClockService = hybridLogicalClockService
            )

            val createdUuid = useCase(
                CreateGoalFundRequest(
                    name = "  Travel  ",
                    targetAmountCents = 250_00L
                )
            )

            val created = database.fundDao().findByUuid(createdUuid)
            assertNotNull(created)
            assertEquals("Travel", created?.name)
            assertEquals(FundType.GOAL, created?.fundType)
            assertEquals(250_00L, created?.targetAmountCents)
            assertEquals(1, created?.sortOrder)
        } finally {
            database.close()
        }
    }

    @Test
    fun updateGoalFundPersistsChangesInRoom() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, BudgetDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            database.fundDao().insert(
                fundEntity(
                    uuid = "reserve",
                    name = "Savings",
                    fundType = FundType.DEFAULT_RESERVE,
                    targetAmountCents = null,
                    sortOrder = 0
                )
            )
            database.fundDao().insert(
                fundEntity(
                    uuid = "goal-a",
                    name = "Travel",
                    fundType = FundType.GOAL,
                    balanceCents = 40_00L,
                    targetAmountCents = 100_00L,
                    sortOrder = 1
                )
            )
            val useCase = UpdateGoalFundUseCase(
                fundDao = database.fundDao(),
                userSettingsStore = TestUserSettingsStore(),
                hybridLogicalClockService = hybridLogicalClockService
            )

            useCase(
                UpdateGoalFundRequest(
                    fundUuid = "goal-a",
                    name = "  Weekend Trip  ",
                    targetAmountCents = 125_00L
                )
            )

            val updated = database.fundDao().findByUuid("goal-a")
            assertNotNull(updated)
            assertEquals("Weekend Trip", updated?.name)
            assertEquals(125_00L, updated?.targetAmountCents)
            assertEquals(40_00L, updated?.balanceCents)
            assertEquals(1, updated?.sortOrder)
        } finally {
            database.close()
        }
    }

    private fun fundEntity(
        uuid: String,
        name: String,
        fundType: FundType,
        balanceCents: Long = 0L,
        targetAmountCents: Long?,
        sortOrder: Int
    ): FundEntity {
        return FundEntity(
            uuid = uuid,
            name = name,
            fundType = fundType,
            balanceCents = balanceCents,
            allocationPerCycleCents = 0L,
            targetAmountCents = targetAmountCents,
            sortOrder = sortOrder,
            originInstallId = "test-install-id",
            lastModifiedByInstallId = "test-install-id",
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
            modClock = "0000000000001-0000-test-install-id"
        )
    }

    private class TestUserSettingsStore(
        initialSettings: UserSettings = UserSettings()
    ) : UserSettingsStore {
        private val mutableUserSettings = MutableStateFlow(initialSettings)
        private val mutablePendingPaydayUndo = MutableStateFlow<PendingPaydayUndo?>(null)

        override val userSettings: Flow<UserSettings> = mutableUserSettings
        override val pendingPaydayUndo: Flow<PendingPaydayUndo?> = mutablePendingPaydayUndo

        override suspend fun ensureIdentity(): UserSettings {
            if (mutableUserSettings.value.installDeviceId.isBlank()) {
                mutableUserSettings.value = mutableUserSettings.value.copy(
                    installDeviceId = "test-install-id",
                    settingsRecordUuid = UUID.randomUUID().toString(),
                    settingsUpdatedAtEpochMs = 1L,
                    settingsModClock = "0000000000001-0000-test-install-id",
                    settingsLastModifiedByInstallId = "test-install-id"
                )
            }
            return mutableUserSettings.value
        }

        override suspend fun updateCycleSettings(monthlyBudgetCents: Long, paydayDate: Int) {
            mutableUserSettings.value = mutableUserSettings.value.copy(
                monthlyBudgetCents = monthlyBudgetCents,
                paydayDate = paydayDate
            )
        }

        override suspend fun updatePortfolioMonthlyBudget(amountCents: Long?) {
            mutableUserSettings.value = mutableUserSettings.value.copy(portfolioMonthlyBudgetCents = amountCents)
        }

        override suspend fun updateMonthlyBudget(amountCents: Long) {
            mutableUserSettings.value = mutableUserSettings.value.copy(monthlyBudgetCents = amountCents)
        }

        override suspend fun updatePaydayDate(day: Int) {
            mutableUserSettings.value = mutableUserSettings.value.copy(paydayDate = day)
        }

        override suspend fun updateSelectedBucket(selectedBucketUuid: String?) {
            mutableUserSettings.value = mutableUserSettings.value.copy(selectedBucketUuid = selectedBucketUuid)
        }

        override suspend fun updateLastResetTimestamp(timestamp: Long) {
            mutableUserSettings.value = mutableUserSettings.value.copy(lastResetTimestamp = timestamp)
        }

        override suspend fun updateLastSeenDate(date: java.time.LocalDate) {
            mutableUserSettings.value = mutableUserSettings.value.copy(lastSeenDate = date.toString())
        }

        override suspend fun completeOnboarding() {
            mutableUserSettings.value = mutableUserSettings.value.copy(isOnboardingCompleted = true)
        }

        override suspend fun setPendingCycle(
            cycleStartDate: java.time.LocalDate,
            cycleEndDateExclusive: java.time.LocalDate,
            detectedAtTimestamp: Long
        ) {
            mutableUserSettings.value = mutableUserSettings.value.copy(
                pendingCycleStartDate = cycleStartDate.toString(),
                pendingCycleEndDateExclusive = cycleEndDateExclusive.toString(),
                pendingCycleDetectedAtTimestamp = detectedAtTimestamp
            )
        }

        override suspend fun clearPendingCycle() {
            mutableUserSettings.value = mutableUserSettings.value.copy(
                pendingCycleStartDate = null,
                pendingCycleEndDateExclusive = null,
                pendingCycleDetectedAtTimestamp = 0L
            )
        }

        override suspend fun savePendingPaydayUndo(pendingPaydayUndo: PendingPaydayUndo) {
            mutablePendingPaydayUndo.value = pendingPaydayUndo
        }

        override suspend fun clearPendingPaydayUndo() {
            mutablePendingPaydayUndo.value = null
        }

        override suspend fun restoreFromSnapshot(settings: UserSettings, onboardingCompleted: Boolean) {
            mutableUserSettings.value = settings.copy(isOnboardingCompleted = onboardingCompleted)
        }
    }

    private companion object {
        const val databaseName = "goal-fund-lifecycle-test"
    }
}
