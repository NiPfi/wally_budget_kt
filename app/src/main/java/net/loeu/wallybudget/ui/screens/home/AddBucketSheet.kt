@file:Suppress("MaxLineLength", "TooManyFunctions")

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

internal data class HomeBucketEditorState(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val amountText: String,
    val isSystemDefault: Boolean
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeBucketSettingsSheet(
    state: HomeBucketEditorState?,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    portfolioBudgetCents: Long,
    onDismiss: () -> Unit,
    onSaveSettings: (BucketDraft) -> Unit
) {
    val editor = state ?: return
    var name by remember(editor) { mutableStateOf(editor.name) }
    var amountText by remember(editor) { mutableStateOf(editor.amountText) }
    var trackingMode by remember(editor) { mutableStateOf(editor.trackingMode) }
    var balanceBehavior by remember(editor) { mutableStateOf(editor.balanceBehavior) }
    var errorMessage by remember(editor) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Bucket settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
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
                    if (!editor.isSystemDefault) {
                        amountText = it
                    }
                    errorMessage = null
                },
                label = { Text(if (editor.isSystemDefault) "Computed remainder" else "Cycle allocation") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !editor.isSystemDefault,
                modifier = Modifier.fillMaxWidth()
            )
            if (editor.isSystemDefault) {
                Text(
                    text = "This system bucket always absorbs the leftover portfolio budget after your named buckets.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Tracking mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BucketTrackingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = trackingMode == mode,
                            onClick = {
                                trackingMode = mode
                                errorMessage = null
                            },
                            label = { Text(mode.displayLabel()) }
                        )
                    }
                }
                Text(
                    text = "Balance behavior",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BucketBalanceBehavior.entries.forEach { behavior ->
                        FilterChip(
                            selected = balanceBehavior == behavior,
                            onClick = {
                                balanceBehavior = behavior
                                errorMessage = null
                            },
                            label = { Text(behavior.displayLabel()) }
                        )
                    }
                }
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
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
                        val trimmedName = name.trim()
                        val normalizedName = trimmedName.lowercase()
                        val amountCents = CurrencyFormatter.parseAmountToCents(amountText)
                        val otherAllocatedCents = sumOtherNamedBucketAllocationsForValidation(
                            allBuckets = allBuckets,
                            bucketSummaries = bucketSummaries,
                            editedBucketUuid = editor.bucketUuid
                        )
                        when {
                            trimmedName.isBlank() -> errorMessage = "Enter a bucket name."
                            allBuckets.any {
                                it.bucketUuid != editor.bucketUuid &&
                                    !it.isClosed &&
                                    it.name.trim().lowercase() == normalizedName
                            } -> errorMessage = "Bucket names must be unique."
                            !editor.isSystemDefault && (amountCents == null || amountCents < 0L) ->
                                errorMessage = "Enter a valid allocation."
                            !editor.isSystemDefault && otherAllocatedCents + requireNotNull(amountCents) > portfolioBudgetCents ->
                                errorMessage = "Allocation exceeds the portfolio total."
                            else -> {
                                onSaveSettings(
                                    BucketDraft(
                                        bucketUuid = editor.bucketUuid,
                                        name = trimmedName,
                                        trackingMode = trackingMode,
                                        balanceBehavior = balanceBehavior,
                                        defaultAllocatedAmountCents = amountCents ?: 0L,
                                        sortOrder = allBuckets.firstOrNull { it.bucketUuid == editor.bucketUuid }?.sortOrder ?: 0,
                                        closeRequested = false
                                    )
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
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
    var trackingMode by rememberSaveable { mutableStateOf(BucketTrackingMode.DAILY_TARGET) }
    var balanceBehavior by rememberSaveable { mutableStateOf(BucketBalanceBehavior.RETURN_TO_PORTFOLIO) }
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
                    text = "Create a bucket here, then fine-tune it in Settings if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Default bucket remainder: ${CurrencyFormatter.format(computedDefaultBucketCents)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Bucket name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Cycle allocation") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = "Tracking mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BucketTrackingMode.entries.forEach { mode ->
                        FilterChip(
                            selected = trackingMode == mode,
                            onClick = { trackingMode = mode },
                            label = { Text(mode.displayLabel()) }
                        )
                    }
                }
                Text(
                    text = "Balance behavior",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BucketBalanceBehavior.entries.forEach { behavior ->
                        FilterChip(
                            selected = balanceBehavior == behavior,
                            onClick = { balanceBehavior = behavior },
                            label = { Text(behavior.displayLabel()) }
                        )
                    }
                }
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
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
                                            trackingMode = trackingMode,
                                            balanceBehavior = balanceBehavior,
                                            defaultAllocatedAmountCents = allocationCents,
                                            sortOrder = (existingBuckets.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                                            closeRequested = false
                                        )
                                    )
                                    name = ""
                                    amountText = "0.00"
                                    trackingMode = BucketTrackingMode.DAILY_TARGET
                                    balanceBehavior = BucketBalanceBehavior.RETURN_TO_PORTFOLIO
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
        }
    }
}

internal fun buildExistingHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>
): List<BucketDraft> {
    val summaryByBucketUuid = bucketSummaries.associateBy { it.bucket.bucketUuid }
    return allBuckets
        .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
        .map { bucket ->
            val effectiveAllocation = summaryByBucketUuid[bucket.bucketUuid]?.allocatedThisCycleCents
                ?: bucket.defaultAllocatedAmountCents
            BucketDraft(
                bucketUuid = bucket.bucketUuid,
                name = bucket.name,
                trackingMode = bucket.trackingMode,
                balanceBehavior = bucket.balanceBehavior,
                defaultAllocatedAmountCents = effectiveAllocation,
                sortOrder = bucket.sortOrder,
                closeRequested = bucket.isClosed
            )
        }
}

internal fun sumOtherNamedBucketAllocationsForValidation(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    editedBucketUuid: String
): Long {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    )
        .filterNot { draft ->
            draft.bucketUuid == editedBucketUuid ||
                draft.bucketUuid == DEFAULT_SPENDING_BUCKET_UUID ||
                draft.closeRequested
        }
        .sumOf { it.defaultAllocatedAmountCents }
}

internal fun buildHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    newBucketDraft: BucketDraft
): List<BucketDraft> {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    ) + newBucketDraft
}

internal fun buildUpdatedHomeBucketDrafts(
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    updatedBucketDraft: BucketDraft
): List<BucketDraft> {
    return buildExistingHomeBucketDrafts(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries
    ).map { draft ->
        if (draft.bucketUuid == updatedBucketDraft.bucketUuid) updatedBucketDraft else draft
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
