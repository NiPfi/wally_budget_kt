@file:Suppress("LongMethod")

package net.loeu.wallybudget.ui.screens.funds

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.DEFAULT_FUND_UUID
import net.loeu.wallybudget.domain.model.Fund
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundsScreen(
    funds: List<Fund>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = remember(funds) { buildFundsOverviewUiState(funds) }

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
                ActiveGoalsSection(activeGoals = uiState.activeGoals)
            }
        }
    }
}

internal fun buildFundsOverviewUiState(funds: List<Fund>): FundsOverviewUiState {
    val activeFunds = funds.filterNot { it.isClosed }
    val reserveFund = activeFunds.firstOrNull { it.uuid == DEFAULT_FUND_UUID }
    val activeGoals = activeFunds
        .filterNot { it.uuid == DEFAULT_FUND_UUID }
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
            LinearProgressIndicator(
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
private fun ActiveGoalsSection(activeGoals: List<FundGoalUiState>) {
    HorizontalDivider()
    if (activeGoals.isEmpty()) {
        ListItem(
            headlineContent = {
                Text(
                    text = "No active goals yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("funds_empty_state")
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
            title = goal.name,
            balanceCents = goal.balanceCents,
            targetAmountCents = goal.targetAmountCents,
            progressFraction = goal.progressFraction,
            trailingLabel = "Priority ${goal.priorityOrder}",
            modifier = Modifier.testTag("fund_goal_${goal.uuid}")
        )
    }
}

@Composable
private fun FundListItem(
    title: String,
    balanceCents: Long,
    targetAmountCents: Long?,
    progressFraction: Float?,
    trailingLabel: String?,
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
            trailingContent = if (trailingLabel != null) {
                {
                    Text(
                        text = trailingLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else null
        )
        if (progressFraction != null) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }
}
