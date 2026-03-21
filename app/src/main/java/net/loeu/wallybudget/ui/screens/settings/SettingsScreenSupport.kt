package net.loeu.wallybudget.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BudgetState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

internal const val PAYDAY_SUPPORTING_TEXT = "Enter a day between 1 and 31"

internal data class SettingsChangePreview(
    val currentCycleEnd: LocalDate,
    val projectedCycleEnd: LocalDate,
    val currentDailyBudgetCents: Long,
    val projectedDailyBudgetCents: Long,
    val currentRemainingTodayCents: Long,
    val projectedRemainingTodayCents: Long,
    val currentRemainingCycleCents: Long,
    val projectedRemainingCycleCents: Long
)

@Composable
internal fun SettingsSaveEffect(
    settingsMessage: String?,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onSettingsMessageConsumed: () -> Unit
) {
    LaunchedEffect(settingsMessage) {
        if (!settingsMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = settingsMessage)
            onSettingsMessageConsumed()
        }
    }
}

@Composable
internal fun SnapshotExportCard(
    onRequestExportSnapshot: () -> Unit,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Data Snapshot",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Export a compressed snapshot of your settings, " +
                    "budget cycles, adjustments, and expenses. Snapshot " +
                    "files are compressed, not encrypted.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRequestExportSnapshot,
                enabled = !isSnapshotBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSnapshotBusy) "Exporting..." else "Export compressed snapshot")
            }
            snapshotMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            snapshotErrorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun UndoPaydayCard(
    expiresAtExclusive: LocalDate?,
    onUndoSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Payday change undo",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = expiresAtExclusive?.let {
                    "You can restore the previous payday and cycle timing until $it."
                } ?: "You can restore the previous payday and cycle timing for the rest of this cycle.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onUndoSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_undo_button")
            ) {
                Text("Undo Payday Change")
            }
        }
    }
}

internal fun firstOccurrenceOnOrAfter(anchor: LocalDate, paydayDayOfMonth: Int): LocalDate {
    val thisMonthDay = minOf(paydayDayOfMonth.coerceIn(1, 31), anchor.lengthOfMonth())
    val thisMonthOccurrence = anchor.withDayOfMonth(thisMonthDay)
    if (!thisMonthOccurrence.isBefore(anchor)) return thisMonthOccurrence
    val nextMonth = anchor.plusMonths(1)
    return nextMonth.withDayOfMonth(
        minOf(paydayDayOfMonth.coerceIn(1, 31), nextMonth.lengthOfMonth())
    )
}

internal fun adjustedCycleEndForImmediatePaydayChange(
    currentDate: LocalDate,
    currentCycleEnd: LocalDate,
    paydayDayOfMonth: Int
): LocalDate {
    return nextOccurrenceWithinCurrentCycle(
        currentDate = currentDate,
        currentCycleEnd = currentCycleEnd,
        paydayDayOfMonth = paydayDayOfMonth
    ) ?: firstOccurrenceOnOrAfter(currentCycleEnd, paydayDayOfMonth)
}

internal fun nextOccurrenceWithinCurrentCycle(
    currentDate: LocalDate,
    currentCycleEnd: LocalDate,
    paydayDayOfMonth: Int
): LocalDate? {
    val candidate = firstOccurrenceOnOrAfter(currentDate.plusDays(1), paydayDayOfMonth)
    return candidate.takeIf { it.isBefore(currentCycleEnd) }
}

internal fun calculateSettingsChangePreview(
    budgetState: BudgetState,
    currentDate: LocalDate,
    currentMonthlyBudgetCents: Long,
    proposedMonthlyBudgetCents: Long,
    proposedPaydayDayOfMonth: Int
): SettingsChangePreview {
    val currentCycleEnd = currentDate.plusDays(budgetState.daysRemainingInCycle.toLong())
    val projectedCycleEnd = if (proposedPaydayDayOfMonth == budgetState.paydayDate) {
        currentCycleEnd
    } else {
        adjustedCycleEndForImmediatePaydayChange(
            currentDate = currentDate,
            currentCycleEnd = currentCycleEnd,
            paydayDayOfMonth = proposedPaydayDayOfMonth
        )
    }
    val projectedBudget = calculateProjectedBudgetState(
        budgetState = budgetState,
        currentDate = currentDate,
        currentCycleEnd = projectedCycleEnd,
        currentMonthlyBudgetCents = currentMonthlyBudgetCents,
        proposedMonthlyBudgetCents = proposedMonthlyBudgetCents
    )
    return SettingsChangePreview(
        currentCycleEnd = currentCycleEnd,
        projectedCycleEnd = projectedCycleEnd,
        currentDailyBudgetCents = budgetState.dailyBudgetCents,
        projectedDailyBudgetCents = projectedBudget.dailyBudgetCents,
        currentRemainingTodayCents = budgetState.remainingTodayCents,
        projectedRemainingTodayCents = projectedBudget.remainingTodayCents,
        currentRemainingCycleCents = budgetState.remainingCycleCents,
        projectedRemainingCycleCents = projectedBudget.remainingCycleCents
    )
}

private data class ProjectedBudgetValues(
    val dailyBudgetCents: Long,
    val remainingTodayCents: Long,
    val remainingCycleCents: Long
)

private fun calculateProjectedBudgetState(
    budgetState: BudgetState,
    currentDate: LocalDate,
    currentCycleEnd: LocalDate,
    currentMonthlyBudgetCents: Long,
    proposedMonthlyBudgetCents: Long
): ProjectedBudgetValues {
    val projectedFullCycleDays = ChronoUnit.DAYS.between(
        budgetState.cycleStartDate,
        currentCycleEnd
    ).toInt().coerceAtLeast(1)
    val remainingDaysIncludingToday = ChronoUnit.DAYS.between(
        currentDate,
        currentCycleEnd
    ).toInt().coerceAtLeast(1)

    val allocatedBeforeTodayCents = currentAllocatedBeforeTodayCents(
        budgetState = budgetState
    )
    val currentEffectiveCycleBudgetCents = budgetState.totalSpentThisCycleCents + budgetState.remainingCycleCents
    val projectedRemainingSegmentBudgetCents = if (proposedMonthlyBudgetCents == currentMonthlyBudgetCents) {
        (currentEffectiveCycleBudgetCents - allocatedBeforeTodayCents).coerceAtLeast(0L)
    } else {
        proratedSegmentBudget(
            monthlyBudgetCents = proposedMonthlyBudgetCents,
            coveredDays = remainingDaysIncludingToday,
            fullCycleDays = projectedFullCycleDays
        )
    }
    val effectiveCycleBudgetCents = allocatedBeforeTodayCents + projectedRemainingSegmentBudgetCents
    val plannedTodayBudgetCents = (proposedMonthlyBudgetCents.toDouble() / projectedFullCycleDays).roundToLong()

    val spentBeforeTodayCents =
        (budgetState.totalSpentThisCycleCents - budgetState.spentTodayCents).coerceAtLeast(0L)
    val cycleVarianceBeforeTodayCents = allocatedBeforeTodayCents - spentBeforeTodayCents
    val distributedAdjustmentCents =
        (cycleVarianceBeforeTodayCents.toDouble() / remainingDaysIncludingToday).roundToLong()

    return ProjectedBudgetValues(
        dailyBudgetCents = plannedTodayBudgetCents,
        remainingTodayCents = plannedTodayBudgetCents + distributedAdjustmentCents - budgetState.spentTodayCents,
        remainingCycleCents = effectiveCycleBudgetCents - budgetState.totalSpentThisCycleCents
    )
}

private fun currentAllocatedBeforeTodayCents(
    budgetState: BudgetState
): Long {
    val remainingDaysIncludingToday = budgetState.daysRemainingInCycle.coerceAtLeast(1)
    val spentBeforeTodayCents = (budgetState.totalSpentThisCycleCents - budgetState.spentTodayCents).coerceAtLeast(0L)
    val currentEffectiveDailyBudgetCents = budgetState.remainingTodayCents + budgetState.spentTodayCents
    val cycleVarianceBeforeTodayCents =
        (currentEffectiveDailyBudgetCents - budgetState.dailyBudgetCents) * remainingDaysIncludingToday
    return (spentBeforeTodayCents + cycleVarianceBeforeTodayCents).coerceAtLeast(0L)
}

private fun proratedSegmentBudget(
    monthlyBudgetCents: Long,
    coveredDays: Int,
    fullCycleDays: Int
): Long {
    if (coveredDays <= 0) return 0L
    return ((monthlyBudgetCents.toDouble() * coveredDays) / fullCycleDays).roundToLong()
}
