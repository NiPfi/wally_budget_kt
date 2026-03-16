package net.loeu.wallybudget.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.loeu.wallybudget.domain.model.BudgetChangeMode
import net.loeu.wallybudget.domain.model.UserSettings
import net.loeu.wallybudget.util.CurrencyFormatter

internal class SettingsFormState(
    initialBudgetText: String,
    initialPaydayText: String
) {
    var budgetText by mutableStateOf(initialBudgetText)
    var paydayText by mutableStateOf(initialPaydayText)
    var budgetChangeMode by mutableStateOf(BudgetChangeMode.APPLY_NEXT_CYCLE)
    var showBudgetError by mutableStateOf(false)
    var showPaydayError by mutableStateOf(false)

    fun reset(userSettings: UserSettings) {
        budgetText = initialBudgetText(userSettings)
        paydayText = userSettings.paydayDate.toString()
        showBudgetError = false
        showPaydayError = false
    }
}

@Composable
internal fun rememberSettingsFormState(userSettings: UserSettings): SettingsFormState {
    val state = remember {
        SettingsFormState(
            initialBudgetText = initialBudgetText(userSettings),
            initialPaydayText = userSettings.paydayDate.toString()
        )
    }
    LaunchedEffect(userSettings) {
        state.reset(userSettings)
    }
    return state
}

internal fun initialBudgetText(userSettings: UserSettings): String =
    CurrencyFormatter.centsToDecimalString(userSettings.monthlyBudgetCents)
