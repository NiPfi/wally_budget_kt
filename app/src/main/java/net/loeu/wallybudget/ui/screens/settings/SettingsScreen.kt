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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userSettings: UserSettings,
    currentDate: LocalDate,
    onSaveSettings: (Long, Int, BudgetChangeMode) -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var budgetText by remember { mutableStateOf(initialBudgetText(userSettings)) }
    var paydayText by remember { mutableStateOf(userSettings.paydayDate.toString()) }
    var budgetChangeMode by remember { mutableStateOf(BudgetChangeMode.APPLY_NEXT_CYCLE) }
    var showBudgetError by remember { mutableStateOf(false) }
    var showPaydayError by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userSettings) {
        budgetText = CurrencyFormatter.centsToDecimalString(userSettings.monthlyBudgetCents)
        paydayText = userSettings.paydayDate.toString()
        showBudgetError = false
        showPaydayError = false
    }

    Scaffold(
        topBar = { SettingsTopBar(onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        SettingsScreenContent(
            budgetText = budgetText,
            paydayText = paydayText,
            currentDate = currentDate,
            currentSettings = userSettings,
            selectedBudgetChangeMode = budgetChangeMode,
            showBudgetError = showBudgetError,
            showPaydayError = showPaydayError,
            paddingValues = paddingValues,
            onBudgetChange = {
                budgetText = it
                showBudgetError = false
            },
            onPaydayChange = {
                if (it.isEmpty() || (it.toIntOrNull() ?: 0) in 1..31) {
                    paydayText = it
                    showPaydayError = false
                }
            },
            onBudgetChangeModeChanged = { budgetChangeMode = it },
            onSave = {
                val validation = validateSettingsForm(
                    budgetText = budgetText,
                    paydayText = paydayText,
                    paydayEditingEnabled = true,
                    budgetChangeMode = budgetChangeMode
                )
                showBudgetError = !validation.isBudgetValid
                showPaydayError = !validation.isPaydayValid

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

    SettingsSaveEffect(settingsMessage, snackbarHostState)
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
private fun SettingsSaveEffect(
    settingsMessage: String?,
    snackbarHostState: SnackbarHostState
) {
    LaunchedEffect(settingsMessage) {
        if (!settingsMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(settingsMessage)
        }
    }
}

private fun initialBudgetText(userSettings: UserSettings): String =
    CurrencyFormatter.centsToDecimalString(userSettings.monthlyBudgetCents)

@Composable
@Suppress("LongMethod")
private fun SettingsScreenContent(
    budgetText: String,
    paydayText: String,
    currentDate: LocalDate,
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
            supportingText = { Text(paydaySupportingText(showPaydayError)) },
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
    budgetText: String,
    paydayText: String,
    budgetChangeMode: BudgetChangeMode
) {
    val newBudget = CurrencyFormatter.parseAmountToCents(budgetText)
    val newPayday = paydayText.toIntOrNull()
    val currentCycleEnd = approximateNextCycleStart(currentDate, currentSettings.paydayDate)
    val previewLines = buildList {
        add("Current cycle keeps payday ${currentSettings.paydayDate} until $currentCycleEnd.")
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

@Composable
private fun SettingsInfoCard() {
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
                text = "Your budget follows the saved cycle schedule. Prorated budget changes only affect the remaining days in the active cycle, and payday changes switch over after the active cycle closes.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun paydaySupportingText(
    showPaydayError: Boolean
): String {
    return if (showPaydayError) "Enter a day between 1 and 31" else "Enter a day between 1 and 31"
}

@Composable
private fun SnapshotExportCard(
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
                text = "Export a compressed snapshot of your settings, budget cycles, adjustments, and expenses. Snapshot files are compressed, not encrypted.",
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

private fun approximateNextCycleStart(now: LocalDate, paydayDate: Int): LocalDate {
    val normalizedPayday = paydayDate.coerceIn(1, 31)
    val effectivePayday = minOf(normalizedPayday, now.lengthOfMonth())
    return if (now.dayOfMonth >= effectivePayday) {
        val nextMonth = now.plusMonths(1)
        nextMonth.withDayOfMonth(minOf(normalizedPayday, nextMonth.lengthOfMonth()))
    } else {
        now.withDayOfMonth(effectivePayday)
    }
}

private fun firstOccurrenceOnOrAfter(anchor: LocalDate, paydayDayOfMonth: Int): LocalDate {
    val thisMonthDay = minOf(paydayDayOfMonth.coerceIn(1, 31), anchor.lengthOfMonth())
    val thisMonthOccurrence = anchor.withDayOfMonth(thisMonthDay)
    if (!thisMonthOccurrence.isBefore(anchor)) return thisMonthOccurrence
    val nextMonth = anchor.plusMonths(1)
    return nextMonth.withDayOfMonth(minOf(paydayDayOfMonth.coerceIn(1, 31), nextMonth.lengthOfMonth()))
}
