package net.loeu.wallybudget.ui.screens.migration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.util.CurrencyFormatter

@Suppress("LongMethod")
@Composable
fun MigrationSetupScreen(
    defaultBucketBudgetCents: Long,
    onCompleteMigration: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var budgetText by remember(defaultBucketBudgetCents) {
        mutableStateOf(CurrencyFormatter.centsToDecimalString(defaultBucketBudgetCents))
    }
    var showError by remember { mutableStateOf(false) }
    val parsedBudgetCents = CurrencyFormatter.parseAmountToCents(budgetText)
    val hasValidationError = showError && (
        parsedBudgetCents == null || parsedBudgetCents < defaultBucketBudgetCents
        )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Finish portfolio setup",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Your existing budget has been migrated into the default Spending money bucket. " +
                "Set the new overall portfolio budget for this cycle.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = {
                        budgetText = it
                        showError = false
                    },
                    label = { Text("Portfolio Budget") },
                    placeholder = { Text("2500.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = hasValidationError,
                    supportingText = {
                        Text(
                            text = if (hasValidationError) {
                                "Portfolio budget must be at least " +
                                    CurrencyFormatter.format(defaultBucketBudgetCents)
                            } else {
                                "Spending money currently uses ${CurrencyFormatter.format(defaultBucketBudgetCents)}."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                val resolvedBudgetCents = parsedBudgetCents
                if (resolvedBudgetCents == null || resolvedBudgetCents < defaultBucketBudgetCents) {
                    showError = true
                } else {
                    onCompleteMigration(resolvedBudgetCents)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Continue")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can split this portfolio budget into more buckets afterwards. " +
                "For now this step only establishes the new overall total.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
