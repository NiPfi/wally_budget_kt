@file:Suppress("MaxLineLength", "LongMethod")

package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BucketEditorSheet(
    state: HomeBucketEditorState?,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    portfolioBudgetCents: Long,
    onRequestDismiss: () -> Unit,
    onSubmit: (BucketDraft) -> Unit
) {
    val editor = state ?: return
    var name by remember(editor) { mutableStateOf(editor.name) }
    var amountText by remember(editor) { mutableStateOf(editor.amountText) }
    var monthScoped by remember(editor) { mutableStateOf(editor.monthScoped) }
    var errorMessage by remember(editor) { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by remember(editor) { mutableStateOf(false) }
    var showCloseConfirmation by remember(editor) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDirty = remember(editor, name, amountText, monthScoped) {
        name != editor.name || amountText != editor.amountText || monthScoped != editor.monthScoped
    }
    val canCloseBucket = remember(editor.bucketUuid, allBuckets) {
        editor.bucketUuid != DEFAULT_SPENDING_BUCKET_UUID &&
            allBuckets.any { it.bucketUuid == editor.bucketUuid && !it.isClosed }
    }

    ModalBottomSheet(
        onDismissRequest = onRequestDismiss,
        sheetState = sheetState
    ) {
        BucketEditorSheetContent(
            editor = editor,
            name = name,
            onNameChange = {
                name = it
                errorMessage = null
            },
            amountText = amountText,
            onAmountChange = {
                if (!editor.isSystemDefault) {
                    amountText = it
                }
                errorMessage = null
            },
            monthScoped = monthScoped,
            onMonthScopedChange = { monthScoped = it },
            errorMessage = errorMessage,
            canCloseBucket = canCloseBucket,
            onRequestClose = { showCloseConfirmation = true },
            onCancel = {
                if (isDirty) {
                    showDiscardConfirmation = true
                } else {
                    onRequestDismiss()
                }
            },
            onSave = {
                when (val result = validateBucketEditorSaveRequest(
                    editor = editor,
                    name = name,
                    amountText = amountText,
                    monthScoped = monthScoped,
                    allBuckets = allBuckets,
                    bucketSummaries = bucketSummaries,
                    portfolioBudgetCents = portfolioBudgetCents
                )) {
                    is BucketEditorValidationResult.Invalid -> errorMessage = result.message
                    is BucketEditorValidationResult.Valid -> onSubmit(result.draft)
                }
            }
        )
    }

    BucketEditorConfirmationDialogs(
        showDiscardConfirmation = showDiscardConfirmation,
        onDismissDiscardConfirmation = { showDiscardConfirmation = false },
        onConfirmDiscard = {
            showDiscardConfirmation = false
            onRequestDismiss()
        },
        showCloseConfirmation = showCloseConfirmation,
        onDismissCloseConfirmation = { showCloseConfirmation = false },
        onConfirmClose = {
            showCloseConfirmation = false
            onSubmit(
                buildBucketEditorDraft(
                    editor = editor,
                    allBuckets = allBuckets,
                    name = name,
                    amountText = amountText,
                    monthScoped = monthScoped,
                    closeRequested = true
                )
            )
        }
    )
}

@Composable
private fun BucketEditorSheetContent(
    editor: HomeBucketEditorState,
    name: String,
    onNameChange: (String) -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit,
    monthScoped: Boolean,
    onMonthScopedChange: (Boolean) -> Unit,
    errorMessage: String?,
    canCloseBucket: Boolean,
    onRequestClose: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BucketEditorHeader(
            canCloseBucket = canCloseBucket,
            onRequestClose = onRequestClose
        )
        BucketNameAndAllocationFields(
            name = name,
            onNameChange = onNameChange,
            amountText = amountText,
            onAmountChange = onAmountChange,
            isAllocationEditable = !editor.isSystemDefault,
            allocationLabel = if (editor.isSystemDefault) "Computed remainder" else "Cycle allocation",
            supportingText = if (editor.isSystemDefault) {
                "This system bucket always absorbs the leftover portfolio budget after your named buckets."
            } else {
                null
            },
            errorMessage = errorMessage
        )
        if (!editor.isSystemDefault) {
            MonthScopedToggle(
                monthScoped = monthScoped,
                onMonthScopedChange = onMonthScopedChange
            )
        }
        BucketEditorActionRow(
            onCancel = onCancel,
            onSave = onSave
        )
    }
}

@Composable
private fun MonthScopedToggle(
    monthScoped: Boolean,
    onMonthScopedChange: (Boolean) -> Unit
) {
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
            onCheckedChange = onMonthScopedChange
        )
    }
}

@Composable
private fun BucketEditorActionRow(
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onCancel,
            modifier = Modifier.weight(1f)
        ) {
            Text("Cancel")
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun BucketEditorConfirmationDialogs(
    showDiscardConfirmation: Boolean,
    onDismissDiscardConfirmation: () -> Unit,
    onConfirmDiscard: () -> Unit,
    showCloseConfirmation: Boolean,
    onDismissCloseConfirmation: () -> Unit,
    onConfirmClose: () -> Unit
) {
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDiscardConfirmation,
            title = { Text("Discard changes?") },
            text = { Text("Your edits will be lost.") },
            dismissButton = {
                TextButton(onClick = onDismissDiscardConfirmation) {
                    Text("Keep editing")
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard) {
                    Text("Discard changes")
                }
            }
        )
    }

    if (showCloseConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissCloseConfirmation,
            title = { Text("Close bucket?") },
            text = {
                Text(
                    "This bucket will stop being active, future planning for it is cleared, " +
                        "this cannot be undone from the UI, and existing history and expenses are not deleted."
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissCloseConfirmation) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmClose) {
                    Text("Close bucket")
                }
            }
        )
    }
}

private sealed interface BucketEditorValidationResult {
    data class Valid(val draft: BucketDraft) : BucketEditorValidationResult
    data class Invalid(val message: String) : BucketEditorValidationResult
}

@Composable
private fun BucketEditorHeader(
    canCloseBucket: Boolean,
    onRequestClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Bucket settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (canCloseBucket) {
            Column(
                modifier = Modifier
                    .testTag("bucket_editor_close_action")
                    .clickable(onClick = onRequestClose)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "Close bucket",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Close bucket",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun validateBucketEditorSaveRequest(
    editor: HomeBucketEditorState,
    name: String,
    amountText: String,
    monthScoped: Boolean,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    portfolioBudgetCents: Long
): BucketEditorValidationResult {
    val trimmedName = name.trim()
    val normalizedName = trimmedName.lowercase()
    val amountCents = CurrencyFormatter.parseAmountToCents(amountText)
    val otherAllocatedCents = sumOtherNamedBucketAllocationsForValidation(
        allBuckets = allBuckets,
        bucketSummaries = bucketSummaries,
        editedBucketUuid = editor.bucketUuid
    )
    return when {
        trimmedName.isBlank() -> BucketEditorValidationResult.Invalid("Enter a bucket name.")
        allBuckets.any {
            it.bucketUuid != editor.bucketUuid &&
                !it.isClosed &&
                it.name.trim().lowercase() == normalizedName
        } -> BucketEditorValidationResult.Invalid("Bucket names must be unique.")
        !editor.isSystemDefault && (amountCents == null || amountCents < 0L) ->
            BucketEditorValidationResult.Invalid("Enter a valid allocation.")
        !editor.isSystemDefault && otherAllocatedCents + requireNotNull(amountCents) > portfolioBudgetCents ->
            BucketEditorValidationResult.Invalid("Allocation exceeds the portfolio total.")
        else -> BucketEditorValidationResult.Valid(
            buildBucketEditorDraft(
                editor = editor,
                allBuckets = allBuckets,
                name = name,
                amountText = amountText,
                monthScoped = monthScoped,
                closeRequested = false
            )
        )
    }
}

private fun buildBucketEditorDraft(
    editor: HomeBucketEditorState,
    allBuckets: List<BudgetBucket>,
    name: String,
    amountText: String,
    monthScoped: Boolean,
    closeRequested: Boolean
): BucketDraft {
    val latestBucket = allBuckets.firstOrNull { it.bucketUuid == editor.bucketUuid }
    return BucketDraft(
        bucketUuid = editor.bucketUuid,
        name = name.trim(),
        trackingMode = latestBucket?.trackingMode ?: BucketTrackingMode.DAILY_TARGET,
        balanceBehavior = latestBucket?.balanceBehavior ?: BucketBalanceBehavior.RETURN_TO_PORTFOLIO,
        defaultAllocatedAmountCents = if (editor.isSystemDefault) {
            0L
        } else {
            CurrencyFormatter.parseAmountToCents(amountText) ?: 0L
        },
        sortOrder = latestBucket?.sortOrder ?: 0,
        closeRequested = closeRequested,
        monthScoped = if (editor.isSystemDefault) false else monthScoped
    )
}
