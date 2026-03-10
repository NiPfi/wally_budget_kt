package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.description
import net.loeu.wallybudget.domain.model.iconRes
import net.loeu.wallybudget.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSubmitExpense: (amountCents: Long, description: String, icon: ExpenseCategory?) -> Unit,
    modifier: Modifier = Modifier,
    onDeleteExpense: (() -> Unit)? = null,
    title: String = "Add Expense",
    confirmButtonText: String = "Add Expense",
    dateLabel: String? = null,
    initialAmountCents: Long? = null,
    initialDescription: String = "",
    initialIcon: ExpenseCategory? = null
) {
    var amountText by remember(initialAmountCents) {
        mutableStateOf(initialAmountCents?.let { CurrencyFormatter.centsToDecimalString(it) }.orEmpty())
    }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var showError by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (dateLabel != null) {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onDeleteExpense != null) {
                    IconButton(onClick = onDeleteExpense) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "Delete expense",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Close"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    showError = false
                },
                label = { Text("Amount") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = showError && amountText.toDoubleOrNull() == null,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { showIconPicker = true },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painter = painterResource(id = selectedIcon.iconRes),
                        contentDescription = "Select category icon"
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("What did you buy?") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Tap the icon to choose a category",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val amountCents = CurrencyFormatter.parseAmountToCents(amountText)
                    if (amountCents != null && amountCents > 0L) {
                        val finalDescription = when {
                            description.isNotBlank() -> description
                            selectedIcon != null -> selectedIcon.description
                            else -> "Expense"
                        }
                        onSubmitExpense(amountCents, finalDescription, selectedIcon)
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(confirmButtonText)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showIconPicker) {
            AlertDialog(
                onDismissRequest = { showIconPicker = false },
                title = { Text("Choose category") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExpenseCategory.getAllIcons().chunked(4).forEach { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowIcons.forEach { (icon, iconRes) ->
                                    IconOption(
                                        iconRes = iconRes,
                                        isSelected = selectedIcon == icon,
                                        onClick = {
                                            selectedIcon = icon
                                            showIconPicker = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(4 - rowIcons.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showIconPicker = false }) {
                        Text("Done")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            selectedIcon = null
                            showIconPicker = false
                        }
                    ) {
                        Text("Clear")
                    }
                }
            )
        }
    }
}

@Composable
private fun IconOption(
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(32.dp)
        )
    }
}
