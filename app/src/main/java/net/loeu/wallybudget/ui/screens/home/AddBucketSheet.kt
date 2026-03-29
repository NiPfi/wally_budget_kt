@file:Suppress("MaxLineLength", "LongMethod", "MatchingDeclarationName")

package net.loeu.wallybudget.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    val remainingBudgetCents = (portfolioBudgetCents - allocatedToNamedBucketsCents).coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                text = "Add bucket",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Bucket name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Cycle allocation") },
                    supportingText = {
                        Text("${CurrencyFormatter.format(remainingBudgetCents)} unallocated from portfolio")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            BucketModeSelector(
                monthScoped = monthScoped,
                onMonthScopedChange = { monthScoped = it }
            )
        }
        item {
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
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
                    }
                ) {
                    Text("Create bucket")
                }
            }
        }
    }
}

@Composable
private fun BucketModeSelector(
    monthScoped: Boolean,
    onMonthScopedChange: (Boolean) -> Unit
) {
    val selectedMode = if (monthScoped) BucketMode.MONTHLY_TOTAL else BucketMode.DAILY_PACING

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Tracking mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BucketMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onMonthScopedChange(mode == BucketMode.MONTHLY_TOTAL) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = BucketMode.entries.size
                    ),
                    label = { Text(mode.title) }
                )
            }
        }
        Crossfade(
            targetState = selectedMode,
            animationSpec = tween(durationMillis = 180),
            label = "bucketModeDescription"
        ) { mode ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = mode.summary,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class BucketMode(
    val title: String,
    val summary: String,
    val description: String
) {
    DAILY_PACING(
        title = "Daily pacing",
        summary = "Best when you want a day-by-day spending target.",
        description = "Splits the allocation into a daily allowance and shows what you can spend each day."
    ),
    MONTHLY_TOTAL(
        title = "Monthly total",
        summary = "Best when only the full cycle total matters.",
        description = "Tracks spending against the whole cycle allocation without a daily breakdown."
    )
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

internal fun BucketTrackingMode.displayLabel(): String = when (this) {
    BucketTrackingMode.DAILY_TARGET -> "Daily target"
    BucketTrackingMode.CYCLE_RESERVE -> "Cycle reserve"
}

internal fun BucketBalanceBehavior.displayLabel(): String = when (this) {
    BucketBalanceBehavior.RETURN_TO_PORTFOLIO -> "Return to portfolio"
    BucketBalanceBehavior.RETAIN_IN_BUCKET -> "Retain in bucket"
}
