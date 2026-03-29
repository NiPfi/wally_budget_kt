package net.loeu.wallybudget

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.loeu.wallybudget.domain.model.BudgetState
import net.loeu.wallybudget.domain.model.SpendingForecast
import net.loeu.wallybudget.domain.config.ForecastConfig
import net.loeu.wallybudget.ui.screens.overview.ForecastCard
import net.loeu.wallybudget.ui.theme.WallyBudgetTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ForecastCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun forecast_card_exposes_loading_state_description_when_loading() {
        setForecastCardContent(
            spendingForecast = SpendingForecast(),
            isLoading = true
        )

        composeRule.onNodeWithTag("forecast_card")
            .assert(hasStateDescription("Forecast details loading"))
    }

    @Test
    fun forecast_card_exposes_low_confidence_state_description() {
        setForecastCardContent(
            spendingForecast = SpendingForecast(
                confidenceScore = ForecastConfig.MIN_CONFIDENCE_THRESHOLD - 0.01
            )
        )

        composeRule.onNodeWithTag("forecast_card")
            .assert(hasStateDescription("Low confidence forecast"))
    }

    @Test
    fun forecast_card_exposes_normal_confidence_state_description() {
        setForecastCardContent(
            spendingForecast = SpendingForecast(
                confidenceScore = ForecastConfig.MIN_CONFIDENCE_THRESHOLD + 0.01
            )
        )

        composeRule.onNodeWithTag("forecast_card")
            .assert(hasStateDescription("Forecast confidence normal"))
    }

    private fun setForecastCardContent(
        spendingForecast: SpendingForecast,
        isLoading: Boolean = false
    ) {
        composeRule.setContent {
            WallyBudgetTheme {
                ForecastCard(
                    spendingForecast = spendingForecast,
                    budgetState = BudgetState(
                        monthlyBudgetCents = 250_000L,
                        totalSpentThisCycleCents = 83_000L,
                        dailyBudgetCents = 9_500L,
                        spentTodayCents = 1_299L,
                        remainingTodayCents = 8_201L,
                        daysRemainingInCycle = 18,
                        cumulativeSavingsCents = 12_000L,
                        paydayDate = 1,
                        cycleStartDate = LocalDate.of(2026, 3, 7)
                    ),
                    effectiveCurrentDate = LocalDate.of(2026, 3, 19),
                    isLoading = isLoading,
                    onClick = {}
                )
            }
        }
    }

    private fun hasStateDescription(value: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
}
