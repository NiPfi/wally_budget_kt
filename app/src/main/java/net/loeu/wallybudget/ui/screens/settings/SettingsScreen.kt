@file:Suppress("MaxLineLength")

package net.loeu.wallybudget.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
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
            selectedBudgetChangeMode = formState.budgetChangeMode,
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
            onBudgetChangeModeChanged = { formState.budgetChangeMode = it },
            onSave = {
                val validation = validateSettingsForm(
                    budgetText = formState.budgetText,
                    paydayText = formState.paydayText,
                    budgetChangeMode = formState.budgetChangeMode
                )
                formState.showBudgetError = !validation.isBudgetValid
                formState.showPaydayError = !validation.isPaydayValid

                if (!validation.isValid) return@SettingsScreenContent
                onSaveSettings(
                    requireNotNull(validation.budgetCents),
                    requireNotNull(validation.payday),
                    validation.budgetChangeMode
                )
            },
            onRequestExportSnapshot = onRequestExportSnapshot,
            settingsMessage = settingsMessage,
            snapshotMessage = snapshotMessage,
            snapshotErrorMessage = snapshotErrorMessage,
            isSnapshotBusy = isSnapshotBusy
        )
    }

    SettingsSaveEffect(settingsMessage, snackbarHostState, onSettingsMessageConsumed)
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
    selectedBudgetChangeMode: BudgetChangeMode,
    showBudgetError: Boolean,
    showPaydayError: Boolean,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onBudgetChange: (String) -> Unit,
    onPaydayChange: (String) -> Unit,
    onBudgetChangeModeChanged: (BudgetChangeMode) -> Unit,
    onSave: () -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean
) {
    val budgetChanged = budgetText != initialBudgetText(currentSettings)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Text(
            text = "Budget Settings",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = budgetText,
            onValueChange = onBudgetChange,
            label = { Text("Monthly Budget") },
            placeholder = { Text("1000.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = showBudgetError,
            supportingText = if (showBudgetError) {
                { Text("Enter a budget greater than 0.00") }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth()
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
            modifier = Modifier.fillMaxWidth()
        )

        if (budgetChanged) {
            Spacer(modifier = Modifier.height(24.dp))
            BudgetChangeModeCard(
                selectedBudgetChangeMode = selectedBudgetChangeMode,
                onBudgetChangeModeChanged = onBudgetChangeModeChanged
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        EffectivePreviewCard(
            currentSettings = currentSettings,
            currentDate = currentDate,
            budgetState = budgetState,
            budgetText = budgetText,
            paydayText = paydayText,
            budgetChangeMode = selectedBudgetChangeMode
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Save Changes")
        }

        if (!settingsMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = settingsMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SettingsInfoCard()

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
private fun BudgetChangeModeCard(
    selectedBudgetChangeMode: BudgetChangeMode,
    onBudgetChangeModeChanged: (BudgetChangeMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Budget Change Timing",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            BudgetChangeMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedBudgetChangeMode == mode,
                        onClick = { onBudgetChangeModeChanged(mode) }
                    )
                    Text(
                        text = when (mode) {
                            BudgetChangeMode.PRORATE_CURRENT_CYCLE -> "Pro-rate current cycle"
                            BudgetChangeMode.APPLY_NEXT_CYCLE -> "Apply next cycle"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EffectivePreviewCard(
    currentSettings: UserSettings,
    currentDate: LocalDate,
    budgetState: BudgetState,
    budgetText: String,
    paydayText: String,
    budgetChangeMode: BudgetChangeMode
) {
    val newBudget = CurrencyFormatter.parseAmountToCents(budgetText)
    val newPayday = paydayText.toIntOrNull()
    val currentCycleEnd = currentDate.plusDays(budgetState.daysRemainingInCycle.toLong())
    val previewLines = buildList {
        add("Current cycle keeps payday ${budgetState.paydayDate} until $currentCycleEnd.")
        if (newBudget != null && newBudget != currentSettings.monthlyBudgetCents) {
            add(
                when (budgetChangeMode) {
                    BudgetChangeMode.PRORATE_CURRENT_CYCLE ->
                        "Budget updates now and only affects the remaining ${ChronoUnit.DAYS.between(currentDate, currentCycleEnd)} days in this cycle."
                    BudgetChangeMode.APPLY_NEXT_CYCLE ->
                        "Budget updates at the next cycle start on $currentCycleEnd."
                }
            )
        }
        if (newPayday != null && newPayday != currentSettings.paydayDate) {
            val bridgeEnd = firstOccurrenceOnOrAfter(currentCycleEnd, newPayday)
            if (bridgeEnd.isAfter(currentCycleEnd)) {
                add("A bridge cycle runs from $currentCycleEnd to $bridgeEnd before the new payday fully takes over.")
            } else {
                add("The new payday takes over immediately at the next cycle boundary.")
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
            }
        }
    }
}
