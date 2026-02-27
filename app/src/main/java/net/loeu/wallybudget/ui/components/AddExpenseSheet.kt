package net.loeu.wallybudget.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.loeu.wallybudget.data.model.ExpenseIcon
import net.loeu.wallybudget.util.IconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSubmitExpense: (amount: Double, description: String, icon: ExpenseIcon?) -> Unit,
    title: String = "Add Expense",
    confirmButtonText: String = "Add Expense",
    initialAmount: Double? = null,
    initialDescription: String = "",
    initialIcon: ExpenseIcon? = null,
    modifier: Modifier = Modifier
) {
    var amountText by remember(initialAmount) { mutableStateOf(initialAmount?.toString().orEmpty()) }
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
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
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
                        imageVector = selectedIcon?.let { IconMapper.getIcon(it) } ?: Icons.Default.AttachMoney,
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
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        val finalDescription = when {
                            description.isNotBlank() -> description
                            selectedIcon != null -> defaultDescriptionFor(selectedIcon!!)
                            else -> "Expense"
                        }
                        onSubmitExpense(amount, finalDescription, selectedIcon)
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
                        IconMapper.getAllIcons().chunked(4).forEach { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowIcons.forEach { (icon, imageVector) ->
                                    IconOption(
                                        icon = imageVector,
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

private fun defaultDescriptionFor(icon: ExpenseIcon): String {
    return when (icon) {
        ExpenseIcon.SHOPPING -> "Shopping"
        ExpenseIcon.RESTAURANT -> "Food"
        ExpenseIcon.TRANSPORT -> "Transport"
        ExpenseIcon.ENTERTAINMENT -> "Entertainment"
        ExpenseIcon.HOME -> "Home"
        ExpenseIcon.HEALTH -> "Health"
        ExpenseIcon.OTHER -> "Other"
    }
}

@Composable
private fun IconOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
            imageVector = icon,
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

