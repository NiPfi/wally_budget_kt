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
    onSavePortfolioPlan: (Long, List<BucketDraft>) -> Unit,
    onSavePayday: (Int) -> Unit,
    onUndoPaydayChange: () -> Unit,
    isPaydayUndoAvailable: Boolean,
    paydayUndoExpiresAtExclusive: LocalDate?,
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
    val externalBucketDrafts = allBuckets
        .sortedWith(compareBy<BudgetBucket> { it.sortOrder }.thenBy { it.createdAtEpochMs })
        .map { bucket -> bucket.toEditableUi() }
    val portfolioPlanHasChanges = bucketDrafts.isNotEmpty() && !settingsDraftsMatch(
        currentBudgetText = portfolioBudgetText,
        currentPaydayText = externalPaydayText,
        currentBucketDrafts = bucketDrafts,
        externalBudgetText = externalBudgetText,
        externalPaydayText = externalPaydayText,
        externalBucketDrafts = externalBucketDrafts
    )
    val paydayHasChanges = paydayText != externalPaydayText

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
            bucketDrafts.clear()
            bucketDrafts += externalBucketDrafts
        }
    }
    LaunchedEffect(externalPaydayText) {
        if (!paydayHasChanges) {
            paydayText = externalPaydayText
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
            PortfolioPlanSection(
                portfolioBudgetText = portfolioBudgetText,
                showBudgetError = showBudgetError,
                onBudgetChange = {
                    portfolioBudgetText = it
                    showBudgetError = false
                }
            )

            PlanningSaveSection(
                hasChanges = portfolioPlanHasChanges,
                onSave = {
                    val portfolioBudgetCents = CurrencyFormatter.parseAmountToCents(portfolioBudgetText)
                    showBudgetError = portfolioBudgetCents == null || portfolioBudgetCents <= 0L
                    if (showBudgetError) return@PlanningSaveSection

                    val saveDrafts = bucketDrafts.mapNotNull { bucket ->
                        val amount = CurrencyFormatter.parseAmountToCents(bucket.amountText) ?: return@mapNotNull null
                        BucketDraft(
                            bucketUuid = bucket.bucketUuid,
                            name = bucket.name.trim(),
                            trackingMode = bucket.trackingMode,
                            balanceBehavior = bucket.balanceBehavior,
                            defaultAllocatedAmountCents = amount,
                            sortOrder = bucket.sortOrder,
                            closeRequested = bucket.closeRequested
                        )
                    }
                    if (saveDrafts.size != bucketDrafts.size) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        showBudgetError = false
                        return@PlanningSaveSection
                    }
                    onSavePortfolioPlan(requireNotNull(portfolioBudgetCents), saveDrafts)
                }
            )

            PaydaySection(
                currentDate = currentDate,
                paydayText = paydayText,
                showPaydayError = showPaydayError,
                onPaydayChange = {
                    paydayText = it.filter(Char::isDigit)
                    showPaydayError = false
                }
            )

            PaydaySaveSection(
                hasChanges = paydayHasChanges,
                isPaydayUndoAvailable = isPaydayUndoAvailable,
                paydayUndoExpiresAtExclusive = paydayUndoExpiresAtExclusive,
                onSave = {
                    val payday = paydayText.toIntOrNull()
                    showPaydayError = payday == null || payday !in 1..31
                    if (showPaydayError) return@PaydaySaveSection
                    onSavePayday(requireNotNull(payday))
                },
                onUndoPaydayChange = onUndoPaydayChange
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
private fun PortfolioPlanSection(
    portfolioBudgetText: String,
    showBudgetError: Boolean,
    onBudgetChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Portfolio Plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Budget and bucket planning changes apply directly. Any amount not allocated to named buckets stays in the default bucket automatically.",
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
        }
    }
}

@Composable
private fun PaydaySection(
    currentDate: LocalDate,
    paydayText: String,
    showPaydayError: Boolean,
    onPaydayChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Payday And Cycle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Changing payday rewrites cycle timing from $currentDate onward and can be undone for a limited time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun PlanningSaveSection(
    hasChanges: Boolean,
    onSave: () -> Unit
) {
    Button(
        onClick = onSave,
        enabled = hasChanges,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("settings_plan_save_button")
    ) {
        Text("Save portfolio plan")
    }
}

@Composable
private fun PaydaySaveSection(
    hasChanges: Boolean,
    isPaydayUndoAvailable: Boolean,
    paydayUndoExpiresAtExclusive: LocalDate?,
    onSave: () -> Unit,
    onUndoPaydayChange: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = onSave,
            enabled = hasChanges,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("settings_payday_save_button")
        ) {
            Text("Save payday")
        }
        if (isPaydayUndoAvailable) {
            UndoPaydayCard(
                expiresAtExclusive = paydayUndoExpiresAtExclusive,
                onUndoSettings = onUndoPaydayChange
            )
        }
    }
}

private fun BudgetBucket.toEditableUi(): EditableBucketUi {
    return EditableBucketUi(
        bucketUuid = bucketUuid,
        name = name,
        trackingMode = trackingMode,
        balanceBehavior = balanceBehavior,
        amountText = CurrencyFormatter.centsToDecimalString(defaultAllocatedAmountCents),
        sortOrder = sortOrder,
        closeRequested = isClosed,
        existingClosed = isClosed
    )
}
