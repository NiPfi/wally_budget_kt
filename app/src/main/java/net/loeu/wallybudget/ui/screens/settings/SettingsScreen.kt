@file:Suppress("MaxLineLength")

package net.loeu.wallybudget.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userSettings: UserSettings,
    budgetState: BudgetState,
    currentDate: LocalDate,
    onSaveSettings: (Long, Int, BudgetChangeMode) -> Unit,
    onUndoSettings: () -> Unit,
    isSettingsUndoAvailable: Boolean,
    settingsUndoExpiresAtExclusive: LocalDate?,
    onSettingsMessageConsumed: () -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formState = rememberSettingsFormState(userSettings)
    val snackbarHostState = remember { SnackbarHostState() }
    SettingsScreenScaffold(
        formState = formState,
        snackbarHostState = snackbarHostState,
        userSettings = userSettings,
        budgetState = budgetState,
        currentDate = currentDate,
        isSettingsUndoAvailable = isSettingsUndoAvailable,
        settingsUndoExpiresAtExclusive = settingsUndoExpiresAtExclusive,
        snapshotMessage = snapshotMessage,
        snapshotErrorMessage = snapshotErrorMessage,
        isSnapshotBusy = isSnapshotBusy,
        onSaveSettings = onSaveSettings,
        onUndoSettings = onUndoSettings,
        onRequestExportSnapshot = onRequestExportSnapshot,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
    SettingsSaveEffect(
        settingsMessage = settingsMessage,
        snackbarHostState = snackbarHostState,
        onSettingsMessageConsumed = onSettingsMessageConsumed
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
private fun SettingsScreenScaffold(
    formState: SettingsFormState,
    snackbarHostState: SnackbarHostState,
    userSettings: UserSettings,
    budgetState: BudgetState,
    currentDate: LocalDate,
    isSettingsUndoAvailable: Boolean,
    settingsUndoExpiresAtExclusive: LocalDate?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean,
    onSaveSettings: (Long, Int, BudgetChangeMode) -> Unit,
    onUndoSettings: () -> Unit,
    onRequestExportSnapshot: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { SettingsTopBar(onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        SettingsScreenContent(
            budgetText = formState.budgetText,
            paydayText = formState.paydayText,
            currentDate = currentDate,
            budgetState = budgetState,
            currentSettings = userSettings,
            isSettingsUndoAvailable = isSettingsUndoAvailable,
            settingsUndoExpiresAtExclusive = settingsUndoExpiresAtExclusive,
            showBudgetError = formState.showBudgetError,
            showPaydayError = formState.showPaydayError,
            paddingValues = paddingValues,
            onBudgetChange = {
                formState.budgetText = it
                formState.showBudgetError = false
            },
            onPaydayChange = {
                if (it.isEmpty() || (it.toIntOrNull() ?: 0) in 1..31) {
                    formState.paydayText = it
                    formState.showPaydayError = false
                }
            },
            onSave = {
                val validation = validateSettingsForm(
                    budgetText = formState.budgetText,
                    paydayText = formState.paydayText
                )
                formState.showBudgetError = !validation.isBudgetValid
                formState.showPaydayError = !validation.isPaydayValid

                if (!validation.isValid) return@SettingsScreenContent
                val validatedBudget = requireNotNull(validation.budgetCents)
                val validatedPayday = requireNotNull(validation.payday)
                onSaveSettings(
                    validatedBudget,
                    validatedPayday,
                    BudgetChangeMode.PRORATE_CURRENT_CYCLE
                )
            },
            onUndoSettings = onUndoSettings,
            onRequestExportSnapshot = onRequestExportSnapshot,
            snapshotMessage = snapshotMessage,
            snapshotErrorMessage = snapshotErrorMessage,
            isSnapshotBusy = isSnapshotBusy
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back"
                )
            }
        }
    )
}

@Composable
@Suppress("LongMethod")
private fun SettingsScreenContent(
    budgetText: String,
    paydayText: String,
    currentDate: LocalDate,
    budgetState: BudgetState,
    currentSettings: UserSettings,
    isSettingsUndoAvailable: Boolean,
    settingsUndoExpiresAtExclusive: LocalDate?,
    showBudgetError: Boolean,
    showPaydayError: Boolean,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onBudgetChange: (String) -> Unit,
    onPaydayChange: (String) -> Unit,
    onSave: () -> Unit,
    onUndoSettings: () -> Unit,
    onRequestExportSnapshot: () -> Unit,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean
) {
    val budgetChanged = budgetText != initialBudgetText(currentSettings)
    val paydayChanged = paydayText != currentSettings.paydayDate.toString()
    val hasChanges = budgetChanged || paydayChanged
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cycle Settings",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Changes apply immediately when you save. " +
                        "If the new payday is earlier and still ahead, the current cycle shortens to that date. " +
                        "If the payday moves later, the remaining budget is spread across the longer cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = budgetText,
                    onValueChange = onBudgetChange,
                    label = { Text("Portfolio Budget") },
                    placeholder = { Text("1000.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = showBudgetError,
                    supportingText = if (showBudgetError) {
                        { Text("Enter a budget greater than 0.00") }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_budget_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = paydayText,
                    onValueChange = onPaydayChange,
                    label = { Text("Payday (Day of Month)") },
                    placeholder = { Text("1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = showPaydayError,
                    supportingText = { Text(PAYDAY_SUPPORTING_TEXT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_payday_input")
                )

                if (hasChanges) {
                    Spacer(modifier = Modifier.height(20.dp))
                    EffectivePreviewCard(
                        currentSettings = currentSettings,
                        currentDate = currentDate,
                        budgetState = budgetState,
                        budgetText = budgetText,
                        paydayText = paydayText
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onSave,
                    enabled = hasChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("settings_save_button")
                ) {
                    Text("Save Changes")
                }

                if (isSettingsUndoAvailable) {
                    Spacer(modifier = Modifier.height(16.dp))
                    UndoSettingsCard(
                        expiresAtExclusive = settingsUndoExpiresAtExclusive,
                        onUndoSettings = onUndoSettings
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SnapshotExportCard(
            onRequestExportSnapshot = onRequestExportSnapshot,
            snapshotMessage = snapshotMessage,
            snapshotErrorMessage = snapshotErrorMessage,
            isSnapshotBusy = isSnapshotBusy
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun EffectivePreviewCard(
    currentSettings: UserSettings,
    currentDate: LocalDate,
    budgetState: BudgetState,
    budgetText: String,
    paydayText: String
) {
    val newBudget = CurrencyFormatter.parseAmountToCents(budgetText)
    val newPayday = paydayText.toIntOrNull()?.coerceIn(1, 31)
    val preview = calculateSettingsChangePreview(
        budgetState = budgetState,
        currentDate = currentDate,
        currentMonthlyBudgetCents = currentSettings.resolvedPortfolioMonthlyBudgetCents,
        proposedMonthlyBudgetCents = newBudget ?: currentSettings.resolvedPortfolioMonthlyBudgetCents,
        proposedPaydayDayOfMonth = newPayday ?: currentSettings.paydayDate
    )
    val previewLines = buildList {
        when {
            preview.projectedCycleEnd.isBefore(preview.currentCycleEnd) -> {
                add("Cycle end updates to ${preview.projectedCycleEnd}, so the new payday takes over sooner.")
            }
            preview.projectedCycleEnd.isAfter(preview.currentCycleEnd) -> {
                add("Cycle end updates to ${preview.projectedCycleEnd}, which spreads the remaining budget over more days.")
            }
            else -> {
                add("Cycle end stays ${preview.projectedCycleEnd}.")
            }
        }
        if (newBudget != null && newBudget != currentSettings.resolvedPortfolioMonthlyBudgetCents) {
            add(
                "The new portfolio budget is prorated across the remaining " +
                    "${ChronoUnit.DAYS.between(currentDate, preview.projectedCycleEnd)} days of this cycle."
            )
        }
        if (newPayday != null && newPayday != currentSettings.paydayDate) {
            if (preview.projectedCycleEnd.isAfter(preview.currentCycleEnd)) {
                add("Your current cycle budget stays fixed for the elapsed days, and the remaining allowance is recalculated across the longer cycle.")
            } else if (preview.projectedCycleEnd.isBefore(preview.currentCycleEnd)) {
                add("The cycle closes earlier, so the remaining allowance is compressed into fewer days.")
            } else {
                add("The new payday applies immediately without changing this cycle's boundary.")
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Change Preview",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            previewLines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            PreviewMetricsHeaderRow()
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            PreviewMetricRow(
                label = "Cycle end",
                currentValue = preview.currentCycleEnd.toString(),
                projectedValue = preview.projectedCycleEnd.toString()
            )
            PreviewMetricRow(
                label = "Base daily allowance",
                currentValue = CurrencyFormatter.format(preview.currentDailyBudgetCents),
                projectedValue = CurrencyFormatter.format(preview.projectedDailyBudgetCents)
            )
            PreviewMetricRow(
                label = "Left today",
                currentValue = CurrencyFormatter.formatSigned(preview.currentRemainingTodayCents),
                projectedValue = CurrencyFormatter.formatSigned(preview.projectedRemainingTodayCents)
            )
            PreviewMetricRow(
                label = "Left in cycle",
                currentValue = CurrencyFormatter.formatSigned(preview.currentRemainingCycleCents),
                projectedValue = CurrencyFormatter.formatSigned(preview.projectedRemainingCycleCents)
            )
        }
    }
}

@Composable
private fun PreviewMetricsHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "",
            modifier = Modifier.width(124.dp)
        )
        Text(
            text = "Before",
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "After save",
            modifier = Modifier.width(92.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewMetricRow(
    label: String,
    currentValue: String,
    projectedValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.width(124.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = currentValue,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (currentValue != projectedValue) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textDecoration = if (currentValue != projectedValue) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            }
        )
        Text(
            text = projectedValue,
            modifier = Modifier.width(92.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
