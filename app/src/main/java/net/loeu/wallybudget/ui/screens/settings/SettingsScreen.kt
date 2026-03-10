package net.loeu.wallybudget.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
    var budgetText by remember { mutableStateOf(CurrencyFormatter.centsToDecimalString(userSettings.monthlyBudgetCents)) }
    var paydayText by remember { mutableStateOf(userSettings.paydayDate.toString()) }
    var showSaveSnackbar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val paydayEditingEnabled = !userSettings.isOnboardingCompleted

    LaunchedEffect(userSettings) {
        budgetText = CurrencyFormatter.centsToDecimalString(userSettings.monthlyBudgetCents)
        paydayText = userSettings.paydayDate.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
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
                onValueChange = { budgetText = it },
                label = { Text("Monthly Budget") },
                placeholder = { Text("1000.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = paydayText,
                onValueChange = {
                    if (paydayEditingEnabled && (it.isEmpty() || (it.toIntOrNull() ?: 0) in 1..31)) {
                        paydayText = it
                    }
                },
                label = { Text("Payday (Day of Month)") },
                placeholder = { Text("1") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = paydayEditingEnabled,
                supportingText = {
                    Text(
                        if (paydayEditingEnabled) {
                            "Enter a day between 1 and 31"
                        } else {
                            "Locked after setup to keep your existing cycle history accurate."
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val budgetCents = CurrencyFormatter.parseAmountToCents(budgetText)
                    val payday = paydayText.toIntOrNull()?.takeIf { paydayEditingEnabled }

                    if (budgetCents != null && budgetCents > 0L) {
                        onUpdateBudget(budgetCents)
                    }
                    if (payday != null && payday in 1..31) {
                        onUpdatePayday(payday)
                    }

                    val paydaySaveAccepted = !paydayEditingEnabled || (payday != null && payday in 1..31)
                    if (budgetCents != null && budgetCents > 0L && paydaySaveAccepted) {
                        showSaveSnackbar = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Save Changes")
            }

            Spacer(modifier = Modifier.height(24.dp))

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
    }

    LaunchedEffect(showSaveSnackbar) {
        if (showSaveSnackbar) {
            snackbarHostState.showSnackbar("Settings saved!")
            showSaveSnackbar = false
        }
    }
}
