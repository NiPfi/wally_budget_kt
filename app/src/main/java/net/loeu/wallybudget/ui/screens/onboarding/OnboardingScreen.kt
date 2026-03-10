package net.loeu.wallybudget.ui.screens.onboarding

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate

private data class OnboardingSubmission(
    val budgetCents: Long,
    val payday: Int,
    val cycleStartDate: LocalDate
)

@Composable
fun OnboardingScreen(
    onComplete: (
        monthlyBudgetCents: Long,
        paydayDate: Int,
        cycleStartDate: LocalDate,
        previousExpensesCents: Long
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var budgetText by remember { mutableStateOf("") }
    var paydayText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Welcome to WallyBudget",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Let's set up your monthly budget",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            value = budgetText,
            onValueChange = {
                budgetText = it
                showError = false
            },
            label = { Text("Monthly Budget") },
            placeholder = { Text("1000.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = showError && budgetText.toDoubleOrNull() == null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = paydayText,
            onValueChange = {
                if (it.isEmpty() || (it.toIntOrNull() != null && it.toInt() in 1..31)) {
                    paydayText = it
                    showError = false
                }
            },
            label = { Text("Payday (1-31)") },
            placeholder = { Text("1") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = showError && (paydayText.toIntOrNull() == null || paydayText.toInt() !in 1..31),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Pick the payday date for your cycle. WallyBudget will keep using this day every month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val submission = resolveOnboardingSubmission(
                    budgetText = budgetText,
                    paydayText = paydayText,
                    today = today
                )
                if (submission != null) {
                    onComplete(
                        submission.budgetCents,
                        submission.payday,
                        submission.cycleStartDate,
                        0L
                    )
                } else {
                    showError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Get Started")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OnboardingHelpText()
    }
}

@Composable
private fun OnboardingHelpText() {
    Text(
        text = "How it works:\n" +
            "• Your budget is divided by the remaining days in your cycle\n" +
            "• Savings from one day roll over to the next\n" +
            "• Your history is stored cycle-by-cycle from payday to payday",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun resolveOnboardingSubmission(
    budgetText: String,
    paydayText: String,
    today: LocalDate
): OnboardingSubmission? {
    val budgetCents = CurrencyFormatter.parseAmountToCents(budgetText)
    val payday = paydayText.toIntOrNull()
    val hasValidBudget = budgetCents != null && budgetCents > 0L
    val hasValidPayday = payday != null && payday in 1..31
    if (!hasValidBudget || !hasValidPayday) {
        return null
    }

    return OnboardingSubmission(
        budgetCents = budgetCents,
        payday = payday,
        cycleStartDate = resolveCycleStartDate(payday, today)
    )
}

private fun resolveCycleStartDate(payday: Int, today: LocalDate): LocalDate {
    val actualPaydayCurrent = minOf(payday, today.lengthOfMonth())
    val currentCycleStart = LocalDate.of(today.year, today.month, actualPaydayCurrent)
    if (!currentCycleStart.isAfter(today)) {
        return currentCycleStart
    }

    val previousMonthDate = today.minusMonths(1)
    val actualPaydayPrevious = minOf(payday, previousMonthDate.lengthOfMonth())
    return LocalDate.of(previousMonthDate.year, previousMonthDate.month, actualPaydayPrevious)
}
