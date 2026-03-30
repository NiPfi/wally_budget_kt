package net.loeu.wallybudget.ui.screens.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.ui.screens.overview.ForecastProjectionChart
import net.loeu.wallybudget.ui.screens.overview.LoadingValuePlaceholder
import net.loeu.wallybudget.util.CurrencyFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
internal fun SpendingTrajectorySection(
    budgetState: BudgetState?,
    spendingForecast: SpendingForecast?,
    effectiveCurrentDate: LocalDate,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("analysis_trajectory_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Spending trajectory",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (isLoading || budgetState == null || spendingForecast == null) {
            LoadingValuePlaceholder(
                sampleText = "MMMMMMMMMMMMMMMMMMMMMMMMMMMMM",
                textStyle = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fillWidth = true
            )
        } else {
            TrajectoryContent(
                budgetState = budgetState,
                spendingForecast = spendingForecast,
                effectiveCurrentDate = effectiveCurrentDate
            )
        }
    }
}

@Composable
private fun TrajectoryContent(
    budgetState: BudgetState,
    spendingForecast: SpendingForecast,
    effectiveCurrentDate: LocalDate
) {
    val daysElapsed = remember(budgetState, effectiveCurrentDate) {
        ChronoUnit.DAYS.between(
            budgetState.cycleStartDate,
            effectiveCurrentDate
        ).toInt().coerceAtLeast(0)
    }
    val totalDays = daysElapsed + budgetState.daysRemainingInCycle
    ForecastProjectionChart(
        totalSpentCents = budgetState.totalSpentThisCycleCents,
        daysElapsed = daysElapsed,
        totalDaysInCycle = totalDays,
        budgetLimitCents = budgetState.monthlyBudgetCents,
        projectedCents = spendingForecast.projectedTotalSpentCents,
        lowerBoundCents = spendingForecast.lowerBoundCents,
        upperBoundCents = spendingForecast.upperBoundCents,
        isLoading = false
    )
    TrajectoryStatsRow(budgetState = budgetState, spendingForecast = spendingForecast)
}

@Composable
private fun TrajectoryStatsRow(
    budgetState: BudgetState,
    spendingForecast: SpendingForecast
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TrajectoryStatItem(
            label = "Spent",
            value = CurrencyFormatter.format(budgetState.totalSpentThisCycleCents)
        )
        TrajectoryStatItem(
            label = "Budget",
            value = CurrencyFormatter.format(budgetState.monthlyBudgetCents)
        )
        TrajectoryStatItem(
            label = "Est. end",
            value = CurrencyFormatter.format(spendingForecast.projectedTotalSpentCents)
        )
    }
}

@Composable
private fun TrajectoryStatItem(label: String, value: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
