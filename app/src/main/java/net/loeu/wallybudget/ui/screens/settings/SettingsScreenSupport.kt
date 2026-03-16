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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

internal const val PAYDAY_SUPPORTING_TEXT = "Enter a day between 1 and 31"

@Composable
internal fun SettingsSaveEffect(
    settingsMessage: String?,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onSettingsMessageConsumed: () -> Unit
) {
    LaunchedEffect(settingsMessage) {
        if (!settingsMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(settingsMessage)
            onSettingsMessageConsumed()
        }
    }
}

@Composable
internal fun SettingsInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "How the Budget Works",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Your budget follows the saved cycle schedule. " +
                    "Prorated budget changes only affect the remaining days " +
                    "in the active cycle, and payday changes switch over " +
                    "after the active cycle closes.",
                style = MaterialTheme.typography.bodyMedium
            )
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

internal fun firstOccurrenceOnOrAfter(anchor: LocalDate, paydayDayOfMonth: Int): LocalDate {
    val thisMonthDay = minOf(paydayDayOfMonth.coerceIn(1, 31), anchor.lengthOfMonth())
    val thisMonthOccurrence = anchor.withDayOfMonth(thisMonthDay)
    if (!thisMonthOccurrence.isBefore(anchor)) return thisMonthOccurrence
    val nextMonth = anchor.plusMonths(1)
    return nextMonth.withDayOfMonth(
        minOf(paydayDayOfMonth.coerceIn(1, 31), nextMonth.lengthOfMonth())
    )
}
