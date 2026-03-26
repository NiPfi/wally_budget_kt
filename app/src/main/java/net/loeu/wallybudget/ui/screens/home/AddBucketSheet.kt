@file:Suppress("MaxLineLength", "LongMethod", "MatchingDeclarationName")

package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.util.CurrencyFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddBucketSheet(
    showSheet: Boolean,
    portfolioBudgetCents: Long,
    existingBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    onDismiss: () -> Unit,
    onCreateBucket: (BucketDraft) -> Unit,
) {
    if (!showSheet) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AddBucketForm(
            portfolioBudgetCents = portfolioBudgetCents,
            existingBuckets = existingBuckets,
            bucketSummaries = bucketSummaries,
            onDismiss = onDismiss,
            onCreateBucket = onCreateBucket
        )
    }
}

@Composable
internal fun AddBucketForm(
    portfolioBudgetCents: Long,
    existingBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    onDismiss: () -> Unit,
    onCreateBucket: (BucketDraft) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("0.00") }
    var monthScoped by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val allocatedToNamedBucketsCents = bucketSummaries
        .filterNot { it.bucket.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID || it.bucket.isClosed }
        .sumOf { it.allocatedThisCycleCents }
    val computedDefaultBucketCents = (portfolioBudgetCents - allocatedToNamedBucketsCents).coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Add bucket",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Create a bucket and set how much of the portfolio it should receive each cycle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Default bucket remainder: ${CurrencyFormatter.format(computedDefaultBucketCents)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                BucketNameAndAllocationFields(
                    name = name,
                    onNameChange = {
                        name = it
                        errorMessage = null
                    },
                    amountText = amountText,
                    onAmountChange = {
                        amountText = it
                        errorMessage = null
                    },
                    isAllocationEditable = true,
                    allocationLabel = "Cycle allocation",
                    supportingText = null,
                    errorMessage = errorMessage
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Month scoped",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Shows cycle totals only — no daily pacing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = monthScoped,
                        onCheckedChange = { monthScoped = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val allocationCents = CurrencyFormatter.parseAmountToCents(amountText)
                            val trimmedName = name.trim()
                            val normalizedName = trimmedName.lowercase()
                            when {
                                trimmedName.isBlank() -> errorMessage = "Enter a bucket name."
                                existingBuckets.any { it.name.trim().lowercase() == normalizedName && !it.isClosed } ->
                                    errorMessage = "Bucket names must be unique."
                                allocationCents == null || allocationCents < 0L ->
                                    errorMessage = "Enter a valid allocation."
                                allocatedToNamedBucketsCents + allocationCents > portfolioBudgetCents ->
                                    errorMessage = "Allocation exceeds the portfolio total."
                                else -> {
                                    onCreateBucket(
                                        BucketDraft(
                                            bucketUuid = UUID.randomUUID().toString(),
                                            name = trimmedName,
                                            trackingMode = BucketTrackingMode.DAILY_TARGET,
                                            balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
                                            defaultAllocatedAmountCents = allocationCents,
                                            sortOrder = (existingBuckets.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                                            closeRequested = false,
                                            monthScoped = monthScoped
                                        )
                                    )
                                    name = ""
                                    amountText = "0.00"
                                    monthScoped = false
                                    errorMessage = null
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create bucket")
                    }
                }
            }
        }
        if (bucketSummaries.isNotEmpty()) {
            item {
                CurrentAllocationsSection(bucketSummaries = bucketSummaries)
            }
        }
    }
}

@Composable
internal fun BucketNameAndAllocationFields(
    name: String,
    onNameChange: (String) -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit,
    isAllocationEditable: Boolean,
    allocationLabel: String,
    supportingText: String?,
    errorMessage: String?
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Bucket name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = amountText,
        onValueChange = onAmountChange,
        label = { Text(allocationLabel) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        enabled = isAllocationEditable,
        modifier = Modifier.fillMaxWidth()
    )
    supportingText?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    errorMessage?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun CurrentAllocationsSection(bucketSummaries: List<BucketSummaryState>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Current allocations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        bucketSummaries.forEachIndexed { index, summary ->
            if (index > 0) {
                HorizontalDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.padding(end = 16.dp)) {
                    Text(
                        text = summary.bucket.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary.bucket.trackingMode.displayLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = CurrencyFormatter.format(summary.allocatedThisCycleCents),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

internal fun BucketTrackingMode.displayLabel(): String = when (this) {
    BucketTrackingMode.DAILY_TARGET -> "Daily target"
    BucketTrackingMode.CYCLE_RESERVE -> "Cycle reserve"
}

internal fun BucketBalanceBehavior.displayLabel(): String = when (this) {
    BucketBalanceBehavior.RETURN_TO_PORTFOLIO -> "Return to portfolio"
    BucketBalanceBehavior.RETAIN_IN_BUCKET -> "Retain in bucket"
}
