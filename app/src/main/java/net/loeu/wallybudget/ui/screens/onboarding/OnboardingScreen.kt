package net.loeu.wallybudget.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import net.loeu.wallybudget.domain.model.SnapshotImportPreview
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    onRequestRestoreSnapshot: () -> Unit,
    onApplySnapshotRestore: () -> Unit,
    onDismissSnapshotPreview: () -> Unit,
    snapshotPreview: SnapshotImportPreview?,
    snapshotErrorMessage: String?,
    snapshotStatusMessage: String?,
    isSnapshotBusy: Boolean,
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
        OnboardingIntro()
        OnboardingFormFields(
            budgetText = budgetText,
            paydayText = paydayText,
            showError = showError,
            onBudgetChange = {
                budgetText = it
                showError = false
            },
            onPaydayChange = {
                if (it.isEmpty() || (it.toIntOrNull() != null && it.toInt() in 1..31)) {
                    paydayText = it
                    showError = false
                }
            }
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

        Button(
            onClick = onRequestRestoreSnapshot,
            enabled = !isSnapshotBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isSnapshotBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Restore Snapshot")
            }
        }

        snapshotStatusMessage?.let { message ->
            StatusCard(
                title = "Snapshot status",
                body = message
            )
        }

        snapshotErrorMessage?.let { message ->
            StatusCard(
                title = "Restore blocked",
                body = message,
                error = true
            )
        }

        snapshotPreview?.let { preview ->
            SnapshotPreviewCard(
                preview = preview,
                isSnapshotBusy = isSnapshotBusy,
                onApplySnapshotRestore = onApplySnapshotRestore,
                onDismissSnapshotPreview = onDismissSnapshotPreview
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OnboardingHelpText()
    }
}

@Composable
private fun OnboardingIntro() {
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
}

@Composable
private fun OnboardingFormFields(
    budgetText: String,
    paydayText: String,
    showError: Boolean,
    onBudgetChange: (String) -> Unit,
    onPaydayChange: (String) -> Unit
) {
    OutlinedTextField(
        value = budgetText,
        onValueChange = onBudgetChange,
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
        onValueChange = onPaydayChange,
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

@Composable
private fun SnapshotPreviewCard(
    preview: SnapshotImportPreview,
    isSnapshotBusy: Boolean,
    onApplySnapshotRestore: () -> Unit,
    onDismissSnapshotPreview: () -> Unit
) {
    val exportedAt = remember(preview.exportedAtEpochMs) {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(
            preview.exportedAtEpochMs
                .let { java.time.Instant.ofEpochMilli(it) }
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Snapshot preview",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Exported: $exportedAt")
            Text("Expenses: ${preview.expenseCount}")
            Text("Deleted records: ${preview.tombstoneCount}")
            Text("Budget cycles: ${preview.budgetPolicyCount}")
            Text("Payday: ${preview.paydayDate}")
            Text(
                "Default budget: ${CurrencyFormatter.format(preview.defaultMonthlyBudgetCents)}"
            )
            Text(if (preview.compressed) "File type: compressed snapshot" else "File type: plain JSON snapshot")
            Text(
                "Backup files are compressed, not encrypted.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onApplySnapshotRestore,
                enabled = !isSnapshotBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply restore")
            }
            Button(
                onClick = onDismissSnapshotPreview,
                enabled = !isSnapshotBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose another file")
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    error: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = body,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
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
