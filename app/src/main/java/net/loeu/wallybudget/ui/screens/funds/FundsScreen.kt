@file:Suppress("LongMethod")

package net.loeu.wallybudget.ui.screens.funds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.Fund
import net.loeu.wallybudget.domain.model.FundType
import net.loeu.wallybudget.util.CurrencyFormatter

internal data class FundsOverviewUiState(
    val reserveFund: Fund?,
    val activeGoals: List<FundGoalUiState>
)

internal data class FundGoalUiState(
    val uuid: String,
    val name: String,
    val balanceCents: Long,
    val targetAmountCents: Long?,
    val remainingAmountCents: Long?,
    val progressFraction: Float?,
    val priorityOrder: Int
)

private data class GoalFundEditorState(
    val fundUuid: String?,
    val name: String,
    val targetAmountText: String
) {
    val isEditing: Boolean
        get() = fundUuid != null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundsScreen(
    funds: List<Fund>,
    onNavigateBack: () -> Unit,
    onCreateGoalFund: suspend (String, Long) -> Unit,
    onUpdateGoalFund: suspend (String, String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = remember(funds) { buildFundsOverviewUiState(funds) }
    var editorState by remember { mutableStateOf<GoalFundEditorState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Funds") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            editorState = GoalFundEditorState(
                                fundUuid = null,
                                name = "",
                                targetAmountText = ""
                            )
                        },
                        modifier = Modifier.testTag("fund_goal_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null
                        )
                        Text("Add goal")
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "reserve") {
                ReserveSection(
                    reserveFund = uiState.reserveFund,
                    modifier = Modifier.testTag("fund_reserve_card")
                )
            }
            item(key = "goals") {
                ActiveGoalsSection(
                    activeGoals = uiState.activeGoals,
                    onEditGoal = { goal ->
                        editorState = GoalFundEditorState(
                            fundUuid = goal.uuid,
                            name = goal.name,
                            targetAmountText = goal.targetAmountCents
                                ?.let(CurrencyFormatter::centsToExpenseAmountInput)
                                .orEmpty()
                        )
                    }
                )
            }
        }
    }

    editorState?.let { editor ->
        GoalFundEditorSheet(
            editor = editor,
            onDismiss = { editorState = null },
            onCreateGoalFund = onCreateGoalFund,
            onUpdateGoalFund = onUpdateGoalFund
        )
    }
}

internal fun buildFundsOverviewUiState(funds: List<Fund>): FundsOverviewUiState {
    val activeFunds = funds.filterNot { it.isClosed }
    val reserveFund = activeFunds.firstOrNull { it.fundType == FundType.DEFAULT_RESERVE }
    val activeGoals = activeFunds
        .filterNot { it.fundType == FundType.DEFAULT_RESERVE }
        .sortedWith(compareBy<Fund> { it.sortOrder }.thenBy { it.createdAtEpochMs }.thenBy { it.uuid })
        .mapIndexed { index, fund -> fund.toFundGoalUiState(priorityOrder = index + 1) }

    return FundsOverviewUiState(
        reserveFund = reserveFund,
        activeGoals = activeGoals
    )
}

private fun Fund.toFundGoalUiState(priorityOrder: Int): FundGoalUiState {
    val target = targetAmountCents?.takeIf { it > 0L }
    val remaining = target?.let { (it - balanceCents).coerceAtLeast(0L) }
    val progress = target?.let { (balanceCents.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat() }
    return FundGoalUiState(
        uuid = uuid,
        name = name,
        balanceCents = balanceCents,
        targetAmountCents = target,
        remainingAmountCents = remaining,
        progressFraction = progress,
        priorityOrder = priorityOrder
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalFundEditorSheet(
    editor: GoalFundEditorState,
    onDismiss: () -> Unit,
    onCreateGoalFund: suspend (String, Long) -> Unit,
    onUpdateGoalFund: suspend (String, String, Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var name by remember(editor) { mutableStateOf(editor.name) }
    var targetAmountText by remember(editor) { mutableStateOf(editor.targetAmountText) }
    var errorMessage by remember(editor) { mutableStateOf<String?>(null) }
    var isSaving by remember(editor) { mutableStateOf(false) }
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
                text = if (editor.isEditing) "Edit goal" else "Add goal",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (!isSaving) {
                        name = it
                        errorMessage = null
                    }
                },
                label = { Text("Goal name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("goal_name_field"),
                enabled = !isSaving
            )
            OutlinedTextField(
                value = targetAmountText,
                onValueChange = {
                    if (!isSaving) {
                        targetAmountText = it
                        errorMessage = null
                    }
                },
                label = { Text("Target amount") },
                supportingText = {
                    Text("Targets must be greater than zero.")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("goal_target_field"),
                enabled = !isSaving
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("goal_editor_error")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    enabled = !isSaving,
                    onClick = {
                        val trimmedName = name.trim()
                        val targetAmountCents = CurrencyFormatter.parseAmountToCents(targetAmountText)
                        when {
                            trimmedName.isBlank() -> errorMessage = "Enter a goal name."
                            targetAmountCents == null || targetAmountCents <= 0L ->
                                errorMessage = "Enter a positive target amount."
                            else -> {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        if (editor.isEditing) {
                                            onUpdateGoalFund(
                                                requireNotNull(editor.fundUuid),
                                                trimmedName,
                                                targetAmountCents
                                            )
                                        } else {
                                            onCreateGoalFund(trimmedName, targetAmountCents)
                                        }
                                        onDismiss()
                                    } catch (exception: IllegalArgumentException) {
                                        errorMessage = exception.message ?: "Unable to save goal."
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("goal_save_button")
                ) {
                    Text(if (editor.isEditing) "Save goal" else "Create goal")
                }
            }
        }
    }
}

@Composable
private fun ReserveSection(
    reserveFund: Fund?,
    modifier: Modifier = Modifier
) {
    if (reserveFund == null) {
        ListItem(
            modifier = modifier,
            headlineContent = {
                Text(
                    text = "No default reserve",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        return
    }
    val target = reserveFund.targetAmountCents?.takeIf { it > 0L }
    val supportText = if (target != null) {
        "${CurrencyFormatter.format(reserveFund.balanceCents)} of ${CurrencyFormatter.format(target)}"
    } else {
        CurrencyFormatter.format(reserveFund.balanceCents)
    }
    Column(modifier = modifier) {
        ListItem(
            overlineContent = {
                Text(
                    text = "Default reserve",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            headlineContent = {
                Text(
                    text = reserveFund.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Text(
                    text = supportText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        val progress = reserveFund.progressPercent?.div(100f)
        if (progress != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun ActiveGoalsSection(
    activeGoals: List<FundGoalUiState>,
    onEditGoal: (FundGoalUiState) -> Unit
) {
    HorizontalDivider()
    if (activeGoals.isEmpty()) {
        ListItem(
            modifier = Modifier.testTag("funds_empty_state"),
            headlineContent = {
                Text(
                    text = "No active goals yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        return
    }
    ListItem(
        headlineContent = {
            Text(
                text = "ACTIVE GOALS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
    activeGoals.forEachIndexed { index, goal ->
        if (index > 0) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        FundListItem(
            uuid = goal.uuid,
            title = goal.name,
            balanceCents = goal.balanceCents,
            targetAmountCents = goal.targetAmountCents,
            progressFraction = goal.progressFraction,
            trailingLabel = "Priority ${goal.priorityOrder}",
            onEditClick = { onEditGoal(goal) },
            modifier = Modifier.testTag("fund_goal_${goal.uuid}")
        )
    }
}

@Composable
private fun FundListItem(
    uuid: String,
    title: String,
    balanceCents: Long,
    targetAmountCents: Long?,
    progressFraction: Float?,
    trailingLabel: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            supportingContent = {
                val supportText = if (targetAmountCents != null) {
                    "${CurrencyFormatter.format(balanceCents)} of ${CurrencyFormatter.format(targetAmountCents)}"
                } else {
                    CurrencyFormatter.format(balanceCents)
                }
                Text(
                    text = supportText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trailingLabel != null) {
                        Text(
                            text = trailingLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.testTag("fund_goal_edit_button_$uuid")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit goal"
                        )
                    }
                }
            }
        )
        if (progressFraction != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }
}
