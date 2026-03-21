@file:Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions")

package net.loeu.wallybudget.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.text.DecimalFormatSymbols
import net.loeu.wallybudget.R
import net.loeu.wallybudget.domain.model.BudgetBucket
import net.loeu.wallybudget.domain.model.DEFAULT_SPENDING_BUCKET_UUID
import net.loeu.wallybudget.domain.model.ExpenseCategory
import net.loeu.wallybudget.domain.model.description
import net.loeu.wallybudget.domain.model.iconRes
import net.loeu.wallybudget.util.CurrencyFormatter
import java.util.Locale
import androidx.compose.material3.rememberModalBottomSheetState

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSubmitExpense: (bucketUuid: String, amountCents: Long, description: String, icon: ExpenseCategory?) -> Unit,
    modifier: Modifier = Modifier,
    onDeleteExpense: (() -> Unit)? = null,
    title: String = "Add Expense",
    confirmButtonText: String = "Add Expense",
    dateLabel: String? = null,
    bucketOptions: List<BudgetBucket> = emptyList(),
    initialBucketUuid: String? = null,
    initialBucketName: String? = null,
    preserveInitialBucketSelection: Boolean = false,
    initialAmountCents: Long? = null,
    initialDescription: String = "",
    initialIcon: ExpenseCategory? = null
) {
    val locale = Locale.getDefault()
    val usesVisualMinorUnitInput = CurrencyFormatter.usesVisualMinorUnitInput(locale)
    var amountFieldValue by remember(initialAmountCents, locale) {
        val initialText = initialAmountCents
            ?.let { CurrencyFormatter.centsToExpenseAmountInput(it, locale) }
            .orEmpty()
        val amountText = if (usesVisualMinorUnitInput && initialText.isNotEmpty()) {
            CurrencyFormatter.formatExpenseAmountInput(initialText, locale)
        } else {
            initialText
        }
        mutableStateOf(
            TextFieldValue(
                text = amountText,
                selection = TextRange(amountText.length)
            )
        )
    }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var selectedIcon by remember(initialIcon) { mutableStateOf(initialIcon) }
    var selectedBucketUuid by remember(initialBucketUuid, initialBucketName, bucketOptions) {
        mutableStateOf(
            resolveInitialBucketUuid(
                initialBucketUuid = initialBucketUuid,
                bucketOptions = bucketOptions,
                preserveInitialSelection = preserveInitialBucketSelection
            )
        )
    }
    var showError by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showBucketPicker by remember { mutableStateOf(false) }
    val resolvedBucketName = bucketOptions.firstOrNull { it.bucketUuid == selectedBucketUuid }?.name
        ?: initialBucketName?.takeIf { preserveInitialBucketSelection }
        ?: "Select bucket"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState
    ) {
        AddExpenseSheetContent(
            title = title,
            dateLabel = dateLabel,
            amountFieldValue = amountFieldValue,
            usesVisualMinorUnitInput = usesVisualMinorUnitInput,
            showError = showError,
            description = description,
            selectedIcon = selectedIcon,
            selectedBucketName = resolvedBucketName,
            confirmButtonText = confirmButtonText,
            onDeleteExpense = onDeleteExpense,
            onDismiss = onDismiss,
            onAmountChange = {
                amountFieldValue = if (usesVisualMinorUnitInput) {
                    normalizeMinorUnitAmountFieldValue(
                        previousValue = amountFieldValue,
                        newValue = it,
                        locale = locale
                    )
                } else {
                    it
                }
                showError = false
            },
            onAmountFocusChanged = { isFocused ->
                if (usesVisualMinorUnitInput) {
                    amountFieldValue = when {
                        isFocused && amountFieldValue.text.isEmpty() -> {
                            val placeholder = CurrencyFormatter.expenseAmountPlaceholder(locale)
                            TextFieldValue(text = placeholder, selection = TextRange(placeholder.length))
                        }
                        !isFocused && amountFieldValue.text == CurrencyFormatter.expenseAmountPlaceholder(locale) -> {
                            TextFieldValue()
                        }
                        else -> amountFieldValue
                    }
                }
            },
            onDescriptionChange = { description = it },
            onShowIconPicker = { showIconPicker = true },
            onShowBucketPicker = { showBucketPicker = true },
            onSubmit = {
                showError = !submitExpense(
                    bucketUuid = selectedBucketUuid,
                    amountText = amountFieldValue.text,
                    description = description,
                    selectedIcon = selectedIcon,
                    locale = locale,
                    onSubmitExpense = onSubmitExpense
                )
            }
        )

        if (showIconPicker) {
            ExpenseIconPickerDialog(
                selectedIcon = selectedIcon,
                onDismiss = { showIconPicker = false },
                onIconSelected = { icon ->
                    selectedIcon = icon
                    showIconPicker = false
                },
                onClear = {
                    selectedIcon = null
                    showIconPicker = false
                }
            )
        }

        if (showBucketPicker) {
            BucketPickerDialog(
                bucketOptions = bucketOptions,
                selectedBucketUuid = selectedBucketUuid,
                onDismiss = { showBucketPicker = false },
                onBucketSelected = { bucketUuid ->
                    selectedBucketUuid = bucketUuid
                    showBucketPicker = false
                }
            )
        }
    }
}

@Composable
private fun AddExpenseSheetContent(
    title: String,
    dateLabel: String?,
    amountFieldValue: TextFieldValue,
    usesVisualMinorUnitInput: Boolean,
    showError: Boolean,
    description: String,
    selectedIcon: ExpenseCategory?,
    selectedBucketName: String,
    confirmButtonText: String,
    onDeleteExpense: (() -> Unit)?,
    onDismiss: () -> Unit,
    onAmountChange: (TextFieldValue) -> Unit,
    onAmountFocusChanged: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onShowIconPicker: () -> Unit,
    onShowBucketPicker: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        AddExpenseSheetHeader(
            title = title,
            dateLabel = dateLabel,
            onDeleteExpense = onDeleteExpense,
            onDismiss = onDismiss
        )
        AddExpenseSheetFormFields(
            amountFieldValue = amountFieldValue,
            usesVisualMinorUnitInput = usesVisualMinorUnitInput,
            showError = showError,
            description = description,
            selectedIcon = selectedIcon,
            selectedBucketName = selectedBucketName,
            onAmountChange = onAmountChange,
            onAmountFocusChanged = onAmountFocusChanged,
            onDescriptionChange = onDescriptionChange,
            onShowIconPicker = onShowIconPicker,
            onShowBucketPicker = onShowBucketPicker
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(confirmButtonText)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AddExpenseSheetFormFields(
    amountFieldValue: TextFieldValue,
    usesVisualMinorUnitInput: Boolean,
    showError: Boolean,
    description: String,
    selectedIcon: ExpenseCategory?,
    selectedBucketName: String,
    onAmountChange: (TextFieldValue) -> Unit,
    onAmountFocusChanged: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onShowIconPicker: () -> Unit,
    onShowBucketPicker: () -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onShowBucketPicker),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Bucket",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selectedBucketName,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_more_horiz),
                contentDescription = "Choose bucket"
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = amountFieldValue,
        onValueChange = onAmountChange,
        label = { Text("Amount") },
        placeholder = { Text(CurrencyFormatter.expenseAmountPlaceholder(Locale.getDefault())) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (
                usesVisualMinorUnitInput ||
                !amountFieldValue.text.contains('.') && !amountFieldValue.text.contains(',')
            ) {
                KeyboardType.Number
            } else {
                KeyboardType.Decimal
            }
        ),
        singleLine = true,
        isError = showError && CurrencyFormatter.parseExpenseAmountToCents(
            amountFieldValue.text,
            Locale.getDefault()
        ) == null,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onAmountFocusChanged(it.isFocused) }
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onShowIconPicker,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                painter = painterResource(id = selectedIcon.iconRes),
                contentDescription = "Select category icon"
            )
        }
        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            placeholder = { Text(selectedIcon?.description ?: "What did you buy?") },
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
}

internal fun resolveInitialBucketUuid(
    initialBucketUuid: String?,
    bucketOptions: List<BudgetBucket>,
    preserveInitialSelection: Boolean = false
): String {
    return when {
        preserveInitialSelection && initialBucketUuid != null -> initialBucketUuid
        initialBucketUuid != null && bucketOptions.any { it.bucketUuid == initialBucketUuid } -> initialBucketUuid
        else -> bucketOptions.firstOrNull()?.bucketUuid ?: DEFAULT_SPENDING_BUCKET_UUID
    }
}

@Composable
private fun AddExpenseSheetHeader(
    title: String,
    dateLabel: String?,
    onDeleteExpense: (() -> Unit)?,
    onDismiss: () -> Unit
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
}

@Composable
private fun ExpenseIconPickerDialog(
    selectedIcon: ExpenseCategory?,
    onDismiss: () -> Unit,
    onIconSelected: (ExpenseCategory?) -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                                onClick = { onIconSelected(icon) },
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
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        }
    )
}

@Composable
private fun BucketPickerDialog(
    bucketOptions: List<BudgetBucket>,
    selectedBucketUuid: String,
    onDismiss: () -> Unit,
    onBucketSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select bucket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bucketOptions.filterNot { it.isClosed }.forEach { bucket ->
                    val isSelected = bucket.bucketUuid == selectedBucketUuid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                            .clickable { onBucketSelected(bucket.bucketUuid) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = bucket.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = bucket.trackingMode.name.replace('_', ' '),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Selected",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun submitExpense(
    bucketUuid: String,
    amountText: String,
    description: String,
    selectedIcon: ExpenseCategory?,
    locale: Locale,
    onSubmitExpense: (String, Long, String, ExpenseCategory?) -> Unit
): Boolean {
    val amountCents = CurrencyFormatter.parseExpenseAmountToCents(amountText, locale)
    if (amountCents == null || amountCents <= 0L) {
        return false
    }

    val finalDescription = description.trim()
    onSubmitExpense(bucketUuid, amountCents, finalDescription, selectedIcon)
    return true
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "ComplexCondition")
private fun normalizeMinorUnitAmountFieldValue(
    previousValue: TextFieldValue,
    newValue: TextFieldValue,
    locale: Locale
): TextFieldValue {
    if (!isSupportedMinorUnitText(newValue.text, locale)) {
        return previousValue
    }

    val previousDigits = previousValue.text.filter(Char::isDigit)
    val digits = resolvedMinorUnitDigits(previousValue, newValue, locale)
    if (digits.isEmpty()) {
        return TextFieldValue()
    }

    val formatted = CurrencyFormatter.formatExpenseAmountInput(digits, locale)
    if (newValue.text == formatted) {
        return TextFieldValue(
            text = formatted,
            selection = TextRange(
                start = newValue.selection.start.coerceIn(0, formatted.length),
                end = newValue.selection.end.coerceIn(0, formatted.length)
            )
        )
    }

    if (
        digits == previousDigits &&
        newValue.text.length < previousValue.text.length &&
        previousValue.selection.collapsed &&
        newValue.selection.collapsed
    ) {
        val adjustedResult = when {
            previousValue.selection.start > 0 &&
                previousValue.text[previousValue.selection.start - 1].isDigit().not() -> {
                removeDigitAt(
                    digits = previousDigits,
                    index = rawDigitBoundaryAtOffset(previousValue.text, previousValue.selection.start, locale) - 1
                )?.let { adjustedDigits ->
                    adjustedDigits to newValue.selection.start.coerceIn(
                        0,
                        CurrencyFormatter.formatExpenseAmountInput(adjustedDigits, locale).length
                    )
                }
            }
            previousValue.selection.start < previousValue.text.length &&
                previousValue.text[previousValue.selection.start].isDigit().not() -> {
                removeDigitAt(
                    digits = previousDigits,
                    index = rawDigitBoundaryAtOffset(previousValue.text, previousValue.selection.start, locale)
                )?.let { adjustedDigits ->
                    adjustedDigits to offsetAfterFirstFractionDigit(
                        formatted = CurrencyFormatter.formatExpenseAmountInput(adjustedDigits, locale),
                        locale = locale
                    )
                }
            }
            else -> null
        }

        if (adjustedResult != null) {
            val (adjustedDigits, targetOffset) = adjustedResult
            val adjustedFormatted = CurrencyFormatter.formatExpenseAmountInput(adjustedDigits, locale)
            return TextFieldValue(
                text = adjustedFormatted,
                selection = TextRange(targetOffset.coerceIn(0, adjustedFormatted.length))
            )
        }
    }

    if (
        newValue.text.length < previousValue.text.length &&
        previousValue.selection.collapsed &&
        newValue.selection.collapsed &&
        previousValue.selection.start < previousValue.text.length &&
        previousValue.text[previousValue.selection.start] == '0' &&
        previousValue.selection.start == 0 &&
        previousValue.text.drop(1).firstOrNull()?.isDigit() == false
    ) {
        return TextFieldValue(
            text = formatted,
            selection = TextRange((newValue.selection.start + 1).coerceIn(0, formatted.length))
        )
    }

    val selectionStartFromEnd = (newValue.text.length - newValue.selection.start).coerceAtLeast(0)
    val selectionEndFromEnd = (newValue.text.length - newValue.selection.end).coerceAtLeast(0)

    return TextFieldValue(
        text = formatted,
        selection = TextRange(
            start = (formatted.length - selectionStartFromEnd).coerceIn(0, formatted.length),
            end = (formatted.length - selectionEndFromEnd).coerceIn(0, formatted.length)
        )
    )
}

private fun canonicalMinorUnitDigits(
    text: String,
    locale: Locale
): String {
    val localeDecimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return if (text.any { it == localeDecimalSeparator || it == '.' || it == ',' }) {
        CurrencyFormatter.parseExpenseAmountToCents(text, locale)?.toString().orEmpty()
    } else {
        text.filter(Char::isDigit)
    }
}

private fun resolvedMinorUnitDigits(
    previousValue: TextFieldValue,
    newValue: TextFieldValue,
    locale: Locale
): String {
    val isSingleCaretEdit = previousValue.selection.collapsed &&
        newValue.selection.collapsed &&
        kotlin.math.abs(newValue.text.length - previousValue.text.length) <= 1

    return if (isSingleCaretEdit) {
        newValue.text.filter(Char::isDigit)
    } else {
        canonicalMinorUnitDigits(newValue.text, locale)
    }
}

private fun rawDigitBoundaryAtOffset(
    text: String,
    offset: Int,
    locale: Locale
): Int {
    val rawDigits = canonicalMinorUnitDigits(text, locale)
    if (rawDigits.isEmpty()) {
        return 0
    }

    return text
        .take(offset.coerceIn(0, text.length))
        .count(Char::isDigit)
        .coerceIn(0, rawDigits.length)
}

@Suppress("ReturnCount")
private fun isSupportedMinorUnitText(
    text: String,
    locale: Locale
): Boolean {
    val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    var separatorCount = 0
    for (char in text) {
        when {
            char.isDigit() -> Unit
            char == decimalSeparator || char == '.' || char == ',' -> separatorCount += 1
            else -> return false
        }
        if (separatorCount > 1) {
            return false
        }
    }
    return true
}

private fun removeDigitAt(digits: String, index: Int): String? {
    if (index !in digits.indices) {
        return null
    }
    return buildString(digits.length - 1) {
        append(digits.substring(0, index))
        append(digits.substring(index + 1))
    }
}

private fun offsetAfterFirstFractionDigit(
    formatted: String,
    locale: Locale
): Int {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val separatorIndex = formatted.indexOf(separator)
    if (separatorIndex == -1) {
        return formatted.length
    }
    return (separatorIndex + 2).coerceAtMost(formatted.length)
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
