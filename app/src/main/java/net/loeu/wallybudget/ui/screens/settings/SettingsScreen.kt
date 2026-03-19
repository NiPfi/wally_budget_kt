@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MaxLineLength", "MatchingDeclarationName", "UnusedParameter")

package net.loeu.wallybudget.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.BucketBalanceBehavior
import net.loeu.wallybudget.domain.model.BucketTrackingMode
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.BucketSummaryState
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.domain.usecase.BucketDraft
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate

internal data class EditableBucketUi(
    val bucketUuid: String,
    val name: String,
    val trackingMode: BucketTrackingMode,
    val balanceBehavior: BucketBalanceBehavior,
    val amountText: String,
    val sortOrder: Int,
    val isPrimary: Boolean,
    val closeRequested: Boolean,
    val existingClosed: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userSettings: UserSettings,
    allBuckets: List<BudgetBucket>,
    bucketSummaries: List<BucketSummaryState>,
    currentDate: LocalDate,
    onSaveSettings: (Long, Int, List<BucketDraft>, BudgetChangeMode) -> Unit,
    onUndoSettings: () -> Unit,
    isSettingsUndoAvailable: Boolean,
    settingsUndoExpiresAtExclusive: LocalDate?,
    onSettingsMessageConsumed: () -> Unit,
    onRequestExportSnapshot: () -> Unit,
    settingsMessage: String?,
    snapshotMessage: String?,
    snapshotErrorMessage: String?,
    isSnapshotBusy: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var portfolioBudgetText by remember { mutableStateOf("") }
    var paydayText by remember { mutableStateOf("") }
    val bucketDrafts = remember { mutableStateListOf<EditableBucketUi>() }
    var showBudgetError by remember { mutableStateOf(false) }
    var showPaydayError by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val externalBudgetText = CurrencyFormatter.centsToDecimalString(userSettings.resolvedPortfolioMonthlyBudgetCents)
    val externalPaydayText = userSettings.paydayDate.toString()
    val summaryByBucketUuid = bucketSummaries.associateBy { it.bucket.bucketUuid }
    val externalBucketDrafts = allBuckets
        .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
        .map { bucket -> bucket.toEditableUi(summaryByBucketUuid[bucket.bucketUuid]) }
    val hasChanges = bucketDrafts.isNotEmpty() && !settingsDraftsMatch(
        currentBudgetText = portfolioBudgetText,
        currentPaydayText = paydayText,
        currentBucketDrafts = bucketDrafts,
        externalBudgetText = externalBudgetText,
        externalPaydayText = externalPaydayText,
        externalBucketDrafts = externalBucketDrafts
    )

    LaunchedEffect(externalBudgetText, externalPaydayText, externalBucketDrafts) {
        if (
            shouldSyncSettingsDrafts(
                currentBudgetText = portfolioBudgetText,
                currentPaydayText = paydayText,
                currentBucketDrafts = bucketDrafts,
                externalBudgetText = externalBudgetText,
                externalPaydayText = externalPaydayText,
                externalBucketDrafts = externalBucketDrafts,
                isEditorOpen = false
            )
        ) {
            portfolioBudgetText = externalBudgetText
            paydayText = externalPaydayText
            bucketDrafts.clear()
            bucketDrafts += externalBucketDrafts
        }
    }

    SettingsSaveEffect(
        settingsMessage = settingsMessage,
        snackbarHostState = snackbarHostState,
        onSettingsMessageConsumed = onSettingsMessageConsumed
    )
    Scaffold(
        topBar = { SettingsTopBar(onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PortfolioSection(
                portfolioBudgetText = portfolioBudgetText,
                paydayText = paydayText,
                showBudgetError = showBudgetError,
                showPaydayError = showPaydayError,
                onBudgetChange = {
                    portfolioBudgetText = it
                    showBudgetError = false
                },
                onPaydayChange = {
                    paydayText = it.filter(Char::isDigit)
                    showPaydayError = false
                }
            )

            SaveSection(
                hasChanges = hasChanges,
                isSettingsUndoAvailable = isSettingsUndoAvailable,
                settingsUndoExpiresAtExclusive = settingsUndoExpiresAtExclusive,
                onSave = {
                    val portfolioBudgetCents = CurrencyFormatter.parseAmountToCents(portfolioBudgetText)
                    val payday = paydayText.toIntOrNull()
                    showBudgetError = portfolioBudgetCents == null || portfolioBudgetCents <= 0L
                    showPaydayError = payday == null || payday !in 1..31
                    if (showBudgetError || showPaydayError) return@SaveSection

                    val saveDrafts = bucketDrafts.mapNotNull { bucket ->
                        val amount = CurrencyFormatter.parseAmountToCents(bucket.amountText) ?: return@mapNotNull null
                        BucketDraft(
                            bucketUuid = bucket.bucketUuid,
                            name = bucket.name.trim(),
                            trackingMode = bucket.trackingMode,
                            balanceBehavior = bucket.balanceBehavior,
                            defaultAllocatedAmountCents = amount,
                            sortOrder = bucket.sortOrder,
                            isPrimary = bucket.isPrimary,
                            closeRequested = bucket.closeRequested
                        )
                    }
                    if (saveDrafts.size != bucketDrafts.size) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        showBudgetError = false
                        showPaydayError = false
                        return@SaveSection
                    }
                    onSaveSettings(
                        requireNotNull(portfolioBudgetCents),
                        requireNotNull(payday),
                        saveDrafts,
                        BudgetChangeMode.PRORATE_CURRENT_CYCLE
                    )
                },
                onUndoSettings = onUndoSettings
            )

            SnapshotExportCard(
                onRequestExportSnapshot = onRequestExportSnapshot,
                snapshotMessage = snapshotMessage,
                snapshotErrorMessage = snapshotErrorMessage,
                isSnapshotBusy = isSnapshotBusy
            )
        }
    }
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
private fun PortfolioSection(
    portfolioBudgetText: String,
    paydayText: String,
    showBudgetError: Boolean,
    showPaydayError: Boolean,
    onBudgetChange: (String) -> Unit,
    onPaydayChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Portfolio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Changes apply immediately when you save. Bucket allocations can be lower than the portfolio total so the difference stays unassigned.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = portfolioBudgetText,
                onValueChange = onBudgetChange,
                label = { Text("Portfolio budget") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = showBudgetError,
                supportingText = if (showBudgetError) {
                    { Text("Enter a budget greater than 0.00") }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_budget_input")
            )
            OutlinedTextField(
                value = paydayText,
                onValueChange = onPaydayChange,
                label = { Text("Payday (day of month)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = showPaydayError,
                supportingText = {
                    Text(PAYDAY_SUPPORTING_TEXT)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_payday_input")
            )
        }
    }
}

@Composable
private fun SaveSection(
    hasChanges: Boolean,
    isSettingsUndoAvailable: Boolean,
    settingsUndoExpiresAtExclusive: LocalDate?,
    onSave: () -> Unit,
    onUndoSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = onSave,
            enabled = hasChanges,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("settings_save_button")
        ) {
            Text("Save changes")
        }
        if (isSettingsUndoAvailable) {
            UndoSettingsCard(
                expiresAtExclusive = settingsUndoExpiresAtExclusive,
                onUndoSettings = onUndoSettings
            )
        }
    }
}

private fun BudgetBucket.toEditableUi(summary: BucketSummaryState?): EditableBucketUi {
    return EditableBucketUi(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        amountText = CurrencyFormatter.centsToDecimalString(
            summary?.allocatedThisCycleCents ?: defaultAllocatedAmountCents
        ),
        sortOrder = sortOrder,
        isPrimary = isPrimary,
        closeRequested = isClosed,
        existingClosed = isClosed
    )
}
