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
import androidx.compose.material3.HorizontalDivider
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Days Left",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = budgetState.daysRemainingInCycle.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Cycle Left",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = CurrencyFormatter.format(abs(budgetState.remainingCycleCents)),
                                style = MaterialTheme.typography.titleLarge,
                                color = if (budgetState.remainingCycleCents >= 0L) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Today's Budget",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(6.dp))

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

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Daily allowance: ${CurrencyFormatter.format(budgetState.dailyBudgetCents)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Cycle Spending",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = CurrencyFormatter.format(budgetState.totalSpentThisCycleCents),
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Before today",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = CurrencyFormatter.format(previousExpensesTotal),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = CurrencyFormatter.format(budgetState.spentTodayCents),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall ${if (budgetState.cumulativeSavingsCents > 0L) "Savings" else "Deficit"}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = CurrencyFormatter.format(abs(budgetState.cumulativeSavingsCents)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (budgetState.cumulativeSavingsCents > 0L) {
                                MaterialTheme.colorScheme.tertiary
                            } else if (budgetState.cumulativeSavingsCents < 0L) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}
