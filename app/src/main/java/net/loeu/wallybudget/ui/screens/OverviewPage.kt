package net.loeu.wallybudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.BudgetState
import net.loeu.wallybudget.data.model.Expense
import net.loeu.wallybudget.ui.components.AnimatedCounter
import net.loeu.wallybudget.util.CurrencyFormatter
import kotlin.math.abs

@Composable
fun OverviewPage(
    budgetState: BudgetState,
    previousCycleExpenses: List<Expense>,
    modifier: Modifier = Modifier
) {
    val previousExpensesTotal = remember(previousCycleExpenses) {
        previousCycleExpenses.sumOf { it.amountCents }
    }

    LazyColumn(
        modifier = modifier,
        userScrollEnabled = false,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Today's Budget",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AnimatedCounter(
                        amountCents = budgetState.remainingTodayCents,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.displayLarge,
                        color = if (budgetState.remainingTodayCents >= 0L) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Spent Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = CurrencyFormatter.format(budgetState.spentTodayCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Daily Allowance",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = CurrencyFormatter.format(budgetState.dailyBudgetCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Days Remaining",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = budgetState.daysRemainingInCycle.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "This Cycle",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = CurrencyFormatter.format(budgetState.totalSpentThisCycleCents),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        if (budgetState.cumulativeSavingsCents != 0L) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (budgetState.cumulativeSavingsCents > 0L) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall ${if (budgetState.cumulativeSavingsCents > 0L) "Savings" else "Deficit"}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = CurrencyFormatter.format(abs(budgetState.cumulativeSavingsCents)),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (budgetState.cumulativeSavingsCents > 0L) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Previous Expenses",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "This cycle before today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = previousCycleExpenses.size.toString(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = CurrencyFormatter.format(previousExpensesTotal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
