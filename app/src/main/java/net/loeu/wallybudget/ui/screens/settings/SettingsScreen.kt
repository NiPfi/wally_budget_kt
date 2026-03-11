package net.loeu.wallybudget.ui.screens.settings

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userSettings: UserSettings,
    onUpdateBudget: (Long) -> Unit,
    onUpdatePayday: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var budgetText by remember { mutableStateOf(initialBudgetText(userSettings)) }
    var paydayText by remember { mutableStateOf(userSettings.paydayDate.toString()) }
    var showBudgetError by remember { mutableStateOf(false) }
    var showPaydayError by remember { mutableStateOf(false) }
    var showSaveSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val paydayEditingEnabled = !userSettings.isOnboardingCompleted

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
            showBudgetError = showBudgetError,
            showPaydayError = showPaydayError,
            paydayEditingEnabled = paydayEditingEnabled,
            paddingValues = paddingValues,
            onBudgetChange = {
                budgetText = it
                showBudgetError = false
            },
            onPaydayChange = {
                if (paydayEditingEnabled && (it.isEmpty() || (it.toIntOrNull() ?: 0) in 1..31)) {
                    paydayText = it
                    showPaydayError = false
                }
            },
            onSave = {
                val validation = validateSettingsForm(
                    budgetText = budgetText,
                    paydayText = paydayText,
                    paydayEditingEnabled = paydayEditingEnabled
                )
                showBudgetError = !validation.isBudgetValid
                showPaydayError = !validation.isPaydayValid

                if (!validation.isValid) return@SettingsScreenContent
                onUpdateBudget(requireNotNull(validation.budgetCents))
                validation.payday?.let(onUpdatePayday)
                showSaveSnackbar = true
            }
        )
    }
    SettingsSaveEffect(showSaveSnackbar, snackbarHostState) { showSaveSnackbar = false }
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
    showSaveSnackbar: Boolean,
    snackbarHostState: SnackbarHostState,
    onSnackbarShown: () -> Unit
) {
    LaunchedEffect(showSaveSnackbar) {
        if (showSaveSnackbar) {
            snackbarHostState.showSnackbar("Settings saved!")
            onSnackbarShown()
        }
    }
}

private fun initialBudgetText(userSettings: UserSettings): String =
    CurrencyFormatter.centsToDecimalString(userSettings.monthlyBudgetCents)

@Composable
private fun SettingsScreenContent(
    budgetText: String,
    paydayText: String,
    showBudgetError: Boolean,
    showPaydayError: Boolean,
    paydayEditingEnabled: Boolean,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    onBudgetChange: (String) -> Unit,
    onPaydayChange: (String) -> Unit,
    onSave: () -> Unit
) {
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
            enabled = paydayEditingEnabled,
            isError = showPaydayError,
            supportingText = {
                Text(paydaySupportingText(showPaydayError, paydayEditingEnabled))
            },
            modifier = Modifier.fillMaxWidth()
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

        Spacer(modifier = Modifier.height(24.dp))

        SettingsInfoCard()
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
                text = "Your monthly budget is divided by the days remaining in your cycle. " +
                    "If you spend less than your daily allowance, the savings roll over to the next day. " +
                    "Forecasts automatically balance your current-cycle pace with prior cycle history.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun paydaySupportingText(
    showPaydayError: Boolean,
    paydayEditingEnabled: Boolean
): String {
    return when {
        showPaydayError -> "Enter a day between 1 and 31"
        paydayEditingEnabled -> "Enter a day between 1 and 31"
        else -> "Locked after setup to keep your existing cycle history accurate."
    }
}
