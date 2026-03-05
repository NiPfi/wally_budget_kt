# Plan: Enhanced Forecast Algorithm with Material3 Visualizations for Non-Technical Users

Implement the complete Duck.ai algorithm (data prep + outlier detection, weighted moving averages, weighted trend regression, confidence calculation, confidence-adjusted bounds) with tunable parameters hidden in a centralized config. Surface forecast uncertainty via a low-confidence warning badge and an intuitive Material3-based range visualization.

## Algorithm Overview

**The algorithm combines:**
1. Data preparation with IQR-based outlier detection
2. Weighted moving averages (exponential decay) prioritizing recent spending
3. Weighted linear regression for trend detection
4. Multi-factor confidence scoring (consistency, sample size, outlier ratio, early-month penalty)
5. Confidence-adjusted margin-of-error bounds using historical month data to reduce early-month bias

---

## Steps

### 1. Create [ForecastConfig](app/src/main/java/net/loeu/wallybudget/domain/config/) object

Centralize all tunable parameters with sensible defaults and comprehensive documentation:

**Parameters:**
- `HISTORICAL_DAYS_LOOKBACK` (default: 60) — days of historical expense data to include
- `WEIGHTED_AVERAGE_WINDOW_DAYS` (default: 14) — window size for weighted moving average
- `DECAY_FACTOR` (default: 0.93) — exponential decay for weight prioritization
- `MIN_CONFIDENCE_THRESHOLD` (default: 0.60) — minimum acceptable confidence score
- `IQR_MULTIPLIER` (default: 1.5) — IQR multiplier for outlier detection

**Documented recommended ranges inline:**
- Window: 7–21 days (larger = smoother, less responsive; smaller = more responsive)
- Decay factor: 0.90–0.97 (higher = emphasize recent days; lower = balance recent + historical)
- Threshold: 0.50–0.85 (higher = stricter filtering)
- IQR multiplier: 1.5 (standard) to 3.0 (highly volatile spending)

### 2. Implement [SpendingForecastCalculator](app/src/main/java/net/loeu/wallybudget/domain/service/) service

Implement the complete Duck.ai algorithm with these methods:

**`prepareAndCleanData(allExpenses: List<Long>): DataPrep`**
- Combine current month + historical month expenses into single list
- Calculate Q1, Q3, IQR from all expenses
- Identify outliers: values < Q1 - (1.5 × IQR) or > Q3 + (1.5 × IQR)
- Return cleaned expense list + outlier count + quartile data

**`calculateWeightedMovingAverage(expenses: List<Long>, windowSize: Int, decayFactor: Double): Long`**
- Take the last N days (windowSize) from expense list
- Calculate exponential weights: weight[i] = decayFactor ^ (windowSize - 1 - i)
- Normalize weights to sum to 1.0
- Return weighted average of recent expenses

**`calculateWeightedTrend(expenses: List<Long>, decayFactor: Double): TrendData`**
- Perform weighted linear regression on (day index, expense amount)
- Use exponential weights with decay factor
- Calculate slope (trend per day) and intercept
- Return slope + intercept for trend interpretation

**`calculateForecastConfidence(cleanedExpenses: List<Long>, outlierCount: Int, daysElapsed: Int): Double`**
- **Consistency score (35% weight)**: 1 / (1 + coefficientOfVariation) where CV = stdDev / mean
- **Sample size score (35% weight)**: min(daysElapsed / 30, 1.0)
- **Outlier ratio score (20% weight)**: 1 - (outlierCount / totalExpenseCount)
- **Early-month penalty (10% weight)**: max(0.7, daysElapsed / 10) — mitigated by historical data
- Return weighted sum of all factors, capped at 1.0

**`forecastMonthlySpending(budgetState: BudgetState, now: LocalDate, allHistoricalExpenses: List<Long>, currentCycleExpenses: List<Long>, daysInMonth: Int): SpendingForecast`**
- Combine all historical + current expenses
- Call prepareAndCleanData() → get cleaned list, outlier count
- Call calculateWeightedMovingAverage() → get daily forecast
- Call calculateWeightedTrend() → get slope
- Call calculateForecastConfidence() → get confidence score
- Calculate remaining days = daysInMonth - daysElapsed
- Base forecast = (dailyForecast + trendSlope × 0.5) × daysRemaining
- Standard error = stdDev(cleanedExpenses) / √daysRemaining
- Margin of error = 1.96 × stdDev × (1 - confidence) — confidence-adjusted
- Lower bound = monthForecast - marginOfError (clamped ≥ 0)
- Upper bound = monthForecast + marginOfError
- Return full SpendingForecast object with all fields

Inject `ForecastConfig` for all parameter access.

### 3. Add new [ExpenseDao](app/src/main/java/net/loeu/wallybudget/data/local/ExpenseDao.kt) query

**`getExpensesLastNDays(daysCount: Int): Flow<List<Expense>>`**
- Query expenses from the last N days (configurable via `HISTORICAL_DAYS_LOOKBACK`)
- Filter by timestamp: now - N days ≤ timestamp < now
- Return as Flow for reactive updates
- Leverage existing `index_expenses_timestamp` for performance

### 4. Extend [SpendingForecast](app/src/main/java/net/loeu/wallybudget/data/model/SpendingForecast.kt) data model

Add all Duck.ai output fields:

```kotlin
data class SpendingForecast(
    // Existing fields
    val estimatedEndCycleRemainingCents: Long,
    val projectedTotalSpentCents: Long,
    val projectedDailySpendCents: Long,
    val historicalAdjustmentPercent: Int,
    val historyCyclesUsed: Int,
    
    // New fields from Duck.ai
    val confidenceScore: Double,  // 0.0 to 1.0
    val confidenceRating: String,  // "Very High" / "High" / "Moderate" / "Low" / "Very Low"
    val lowerBoundCents: Long,
    val upperBoundCents: Long,
    val dailyAverageWeightedCents: Long,  // weighted moving average
    val trendSlopeCents: Double,  // per-day trend from regression
    val detectedOutlierCount: Int,
    val usedDataPoints: Int  // cleaned expense count
) {
    val isProjectedOverBudget: Boolean
        get() = estimatedEndCycleRemainingCents < 0L
}
```

Confidence rating scale:
- Very High: ≥ 0.85
- High: ≥ 0.70
- Moderate: ≥ 0.55
- Low: ≥ 0.40
- Very Low: < 0.40

### 5. Refactor [BudgetCalculationService.calculateSpendingForecast()](app/src/main/java/net/loeu/wallybudget/domain/service/BudgetCalculationService.kt)

Update to inject `SpendingForecastCalculator` and delegate all logic:

- Inject `SpendingForecastCalculator` via constructor
- Combine historical [MonthlyHistory](app/src/main/java/net/loeu/wallybudget/data/model/MonthlyHistory.kt) expenses (convert cycle totals to daily distribution or use per-day data) with recent [Expense](app/src/main/java/net/loeu/wallybudget/data/model/Expense.kt) records
- Create continuous daily expense list per Duck.ai spec (previous months + current month)
- Call calculator.forecastMonthlySpending() with all data
- Return SpendingForecast with all fields populated

### 6. Update [BudgetRepository.getSpendingForecast()](app/src/main/java/net/loeu/wallybudget/data/repository/BudgetRepository.kt)

Combine data sources before calculation:

- Call `getExpensesLastNDays(ForecastConfig.HISTORICAL_DAYS_LOOKBACK)` to get recent expenses
- Call `monthlyHistoryDao.getAllHistory()` to get previous months
- Convert MonthlyHistory records to daily expense lists (see Further Considerations)
- Pass both to `BudgetCalculationService.calculateSpendingForecast()`

### 7. Replace forecast card in [OverviewPage](app/src/main/java/net/loeu/wallybudget/ui/screens/OverviewPage.kt)

Replace existing forecast section with:

**(a) Low-confidence warning badge** (Material3 `AssistChip`)
- Visible only if `confidenceScore < ForecastConfig.MIN_CONFIDENCE_THRESHOLD`
- Icon: `Icons.Default.Warning`
- Text: "Forecast uncertain"
- On-click shows info dialog: "Your spending has been variable. The forecast range is wider. Check back in a few days for a more reliable estimate."

**(b) Range visualization** (Material3 `LinearProgressIndicator`)
- Styled as confidence range bar with labeled currency bounds
- Left label: `CurrencyFormatter.format(lowerBoundCents)` ("Low estimate")
- Center: Visual divider at point estimate
- Right label: `CurrencyFormatter.format(upperBoundCents)` ("High estimate")
- Center text/accent: `CurrencyFormatter.format(projectedTotalSpentCents)` ("Expected")
- Color: tertiary if on-budget (remaining > 0), error if projected over-budget
- Always visible for transparency

**(c) Supporting details row**
- Confidence rating text: "Moderate confidence" (use `confidenceRating` field)
- Trend indicator: ↑/↓/↔ icon + short text
  - ↑ "Spending trend: up $X/day" if slope > 0.50
  - ↓ "Spending trend: down $X/day" if slope < -0.50
  - ↔ "Spending trend: stable" otherwise
  - Format slope: `CurrencyFormatter.format((trendSlopeCents * 100).toLong())` (slope in cents per day)
- Data quality badge: "📊 Based on N days" where N = `usedDataPoints`

### 8. Create reusable [ForecastRangeIndicator](app/src/main/java/net/loeu/wallybudget/ui/components/) composable

Material3-based range visualization component:

**Layout:**
- Vertical Column with three label rows above a range bar
- Top row: Three labels ("Low estimate", "Expected", "High estimate")
- Bottom row: `LinearProgressIndicator` (determinate) with:
  - Visual accent or divider at center (point estimate position)
  - Left edge labeled with lower bound currency
  - Right edge labeled with upper bound currency
  - Center labeled with point estimate currency
  - Background color: secondary container
  - Progress color: tertiary if on-budget, error if over-budget

**Optional expanded details** (collapsible or icon button):
- Outlier count: "X anomalies detected and excluded"
- Data points: "Calculated from X cleaned expense records"
- Confidence breakdown (if needed): consistency score, sample size score, outlier score, early-month penalty

**Accessibility:**
- Semantic labels describing the range (not just numbers)
- Use Material3 color scheme tokens for contrast
- Larger touch targets for icon buttons

---

## Further Considerations

### 1. Daily Expense Aggregation from MonthlyHistory

Current [MonthlyHistory](app/src/main/java/net/loeu/wallybudget/data/model/MonthlyHistory.kt) schema stores only cycle totals (budgetAmountCents, totalSpentCents, surplusCents), not daily breakdowns.

**Approach:**
- **Option A (Preferred for simplicity)**: Reconstruct daily distribution by spreading historical cycle total evenly across the cycle (approximation). E.g., if cycle = 30 days and total = $900, daily estimate = $30.
- **Option B (More accurate, requires migration)**: Add new `DailyExpenseAggregate` entity with daily breakdowns per cycle; add Room migration; query daily data directly.

**Recommendation:** Start with Option A. It's fast, doesn't require schema changes, and the algorithm's confidence scoring will account for the approximation error. Refactor to Option B if testing reveals unacceptable bias.

### 2. Range Bar Numerical Precision

The three labels (lower, expected, upper) display currency values via `CurrencyFormatter`.

- If range is very narrow (e.g., ±$2), consider collapsing to point estimate only (hide bounds) or show "~$X ± $Y" notation
- Keep label formatting consistent: use `CurrencyFormatter.format()` for all three bounds
- Consider font size and padding to avoid visual clutter in the card

### 3. Trend Direction Clarity

Slope from regression can be positive (increasing), negative (decreasing), or near-zero (stable).

**Display strategy:**
- ↑ icon (color: warn if slope > 1.0, otherwise secondary) + text "Spending trend: up $X/day"
- ↓ icon (color: tertiary) + text "Spending trend: down $X/day"
- ↔ icon (color: secondary) + text "Spending trend: stable"
- Threshold: treat |slope| < 0.50 cents/day as "stable" to avoid noise

### 4. Performance and Query Optimization

The algorithm processes up to 60 days of expenses × all historical month data.

- **Query performance**: Test `getExpensesLastNDays(60)` latency; if slow, reduce `HISTORICAL_DAYS_LOOKBACK` or add query optimization
- **Daily aggregation**: If using Option A above, the in-memory spread is O(cycles × days) — negligible
- **Confidence calculation**: O(n) where n = cleaned expense count; negligible for typical data sizes
- **Linear regression**: O(n) with exponential weights — negligible

**No new database migrations needed** unless historical data requires daily granularity (Option B).

### 5. User Education and Tooltip/Dialog Content

The warning badge dialog should be simple and non-technical:

**Low-confidence dialog:**
> "Your spending has been variable, so the forecast range is wider than usual. This means the amount you spend by the end of the cycle could be higher or lower than expected. Check back in a few days for a more reliable estimate."

**Confidence rating explanations (optional, in-app help):**
- Very High: "Confident forecast based on stable, consistent spending."
- High: "Fairly confident forecast; some normal variation expected."
- Moderate: "Cautious forecast; your spending has been variable."
- Low: "Uncertain forecast; spending varies significantly."
- Very Low: "Very little data or very high variation; forecast may be unreliable."

### 6. Testing and Validation Strategy

- **Unit tests for calculator methods**: Test each Duck.ai step (prep, WMA, trend, confidence, bounds) with synthetic data
- **Integration tests**: Combine real expense data; verify bounds and confidence scores are reasonable
- **UI tests**: Verify badge visibility logic, range visualization renders correctly, supporting details display
- **Performance profiling**: Measure query latency and algorithm runtime with realistic datasets

---

## Configuration Example Usage

```kotlin
// In ForecastConfig.kt
object ForecastConfig {
    // Recommended range: 30–90 days. Balance between comprehensive history and query performance.
    // Reduce to 45 if query latency exceeds 500ms on typical devices.
    const val HISTORICAL_DAYS_LOOKBACK = 60
    
    // Recommended range: 7–21 days. Larger = smoother forecast; smaller = more responsive to recent changes.
    const val WEIGHTED_AVERAGE_WINDOW_DAYS = 14
    
    // Recommended range: 0.90–0.97. Higher = prioritize recent days; lower = balance recent + historical.
    const val DECAY_FACTOR = 0.93
    
    // Recommended range: 0.50–0.85. Set based on risk tolerance. 0.60 is a reasonable default.
    const val MIN_CONFIDENCE_THRESHOLD = 0.60
    
    // Standard: 1.5 for normal data. Use 3.0 for highly volatile spending.
    const val IQR_MULTIPLIER = 1.5
}
```

## Output Example

```kotlin
SpendingForecast(
    estimatedEndCycleRemainingCents = 15000,  // $150 left
    projectedTotalSpentCents = 135000,  // $1,350 projected total
    projectedDailySpendCents = 4365,  // $43.65/day
    historicalAdjustmentPercent = 5,
    historyCyclesUsed = 6,
    
    confidenceScore = 0.76,
    confidenceRating = "High",
    lowerBoundCents = 118000,  // $1,180
    upperBoundCents = 152000,  // $1,520
    dailyAverageWeightedCents = 4235,  // $42.35/day (WMA)
    trendSlopeCents = 0.85,  // +$0.85/day trend
    detectedOutlierCount = 2,
    usedDataPoints = 47
)
```

## Key Advantages

- **Reduces early-month bias**: Historical data provides context for incomplete current month
- **Adapts to recent behavior**: Weighted moving average captures spending shifts
- **Handles anomalies**: IQR outlier detection prevents one-time purchases from skewing forecasts
- **Quantifies uncertainty**: Confidence score lets users decide whether to trust the forecast
- **Transparent filtering**: Warning badge only appears for low confidence; range always visible
- **Non-technical UX**: Material3 components, plain-language labels, simple trend/data indicators
- **Centralized tuning**: All parameters in one place with documented ranges and sensible defaults

---

## Algorithm Overview

**This algorithm reduces early-month bias by treating previous months' spending as extended historical data, detects and handles outliers, uses weighted moving averages to prioritize recent behavior, and generates confidence-adjusted forecasts that you can filter by your desired confidence threshold.**

---

## Step-by-Step Implementation

### 1. Data Preparation and Outlier Detection

Begin by combining current month data with historical data and identifying anomalies:

```
function prepareAndCleanData(currentMonthExpenses, previousMonthsExpenses):
    // Combine all available spending data
    allExpenses = previousMonthsExpenses + currentMonthExpenses
    daysElapsed = length(currentMonthExpenses)

    // Calculate IQR-based outlier bounds
    Q1 = percentile(allExpenses, 25)
    Q3 = percentile(allExpenses, 75)
    IQR = Q3 - Q1

    lowerBound = Q1 - (1.5 * IQR)
    upperBound = Q3 + (1.5 * IQR)

    // Separate outliers and normal values
    outliers = []
    cleanedExpenses = []

    for each expense in allExpenses:
        if expense < lowerBound or expense > upperBound:
            outliers.append(expense)
        else:
            cleanedExpenses.append(expense)

    return {
        cleaned: cleanedExpenses,
        outliers: outliers,
        daysElapsed: daysElapsed,
        Q1: Q1,
        Q3: Q3,
        IQR: IQR
    }
```

**Key points:**
- **Interquartile Range (IQR) method**: Values beyond 1.5 × IQR from Q1/Q3 are flagged as outliers
- **Preserve outliers**: Store them separately; you may want to manually review or reinclude them based on context
- **Combined dataset**: Using previous months reduces early-month bias by providing a larger baseline

### 2. Weighted Moving Average Calculation

Prioritize recent spending patterns while maintaining historical context:

```
function calculateWeightedMovingAverage(expenses, windowSize, decayFactor):
    // decayFactor typically 0.9-0.95 (higher = more weight to recent days)

    if length(expenses) < windowSize:
        windowSize = length(expenses)

    recentExpenses = expenses[length(expenses) - windowSize : ]

    // Calculate exponential weights (most recent = highest weight)
    weights = []
    totalWeight = 0

    for i = 0 to windowSize - 1:
        weight = decayFactor ^ (windowSize - 1 - i)
        weights.append(weight)
        totalWeight += weight

    // Normalize weights to sum to 1
    normalizedWeights = weights / totalWeight

    // Calculate weighted average
    wma = sum(recentExpenses[i] * normalizedWeights[i] for i in 0 to windowSize - 1)

    return wma
```

**Explanation:**
- **Window size**: Typically 7-14 days (balance between recent behavior and stability)
- **Decay factor**: 0.95 means each prior day gets 95% the weight of the day after it
- **Exponential weighting**: Most recent days have highest influence on the forecast

### 3. Trend Detection with Weighted Regression

Calculate spending trend using weighted linear regression:

```
function calculateWeightedTrend(expenses, decayFactor):
    n = length(expenses)

    // Create weights (exponential decay)
    weights = []
    for i = 0 to n - 1:
        weight = decayFactor ^ (n - 1 - i)
        weights.append(weight)

    // Normalize weights
    totalWeight = sum(weights)
    weights = weights / totalWeight

    // Weighted linear regression: y = mx + b
    // where x = day index, y = expense amount

    weightedMeanX = sum(i * weights[i] for i in 0 to n - 1)
    weightedMeanY = sum(expenses[i] * weights[i] for i in 0 to n - 1)

    numerator = sum(weights[i] * (i - weightedMeanX) * (expenses[i] - weightedMeanY)
                    for i in 0 to n - 1)
    denominator = sum(weights[i] * (i - weightedMeanX)^2 for i in 0 to n - 1)

    slope = numerator / denominator  // trend per day
    intercept = weightedMeanY - slope * weightedMeanX

    return { slope: slope, intercept: intercept }
```

**Result:** A trend line that emphasizes recent spending changes while accounting for historical patterns.

### 4. Confidence Calculation for the Forecast

Quantify how confident the forecast is based on data consistency:

```
function calculateForecastConfidence(cleanedExpenses, outlierCount, daysElapsed):
    n = length(cleanedExpenses)

    // Factor 1: Data consistency (coefficient of variation)
    mean = average(cleanedExpenses)
    stdDev = standardDeviation(cleanedExpenses)
    coefficientOfVariation = stdDev / mean  // lower = more consistent

    consistencyScore = 1 / (1 + coefficientOfVariation)  // 0 to 1 scale

    // Factor 2: Sample size adequacy
    // Confidence increases with more data points
    sampleSizeScore = min(daysElapsed / 30, 1.0)  // caps at 1.0 after 30 days

    // Factor 3: Outlier ratio
    // More outliers = less confidence
    outlierRatio = outlierCount / (n + outlierCount)
    outlierScore = 1 - outlierRatio

    // Factor 4: Early month penalty (reduced when using historical data)
    // With historical data, this is minimized
    earlyMonthPenalty = max(0.7, daysElapsed / 10)  // minimum 0.7, increases with days

    // Combine factors (weighted average)
    confidence = (consistencyScore * 0.35 +
                  sampleSizeScore * 0.35 +
                  outlierScore * 0.20 +
                  earlyMonthPenalty * 0.10)

    return min(confidence, 1.0)  // cap at 100%
```

**Confidence components:**
- **Consistency (35%)**: Stable spending patterns increase confidence
- **Sample size (35%)**: More historical data increases confidence
- **Outlier ratio (20%)**: Fewer outliers increase confidence
- **Early month penalty (10%)**: Minor factor since historical data mitigates this

### 5. Confidence-Adjusted Forecast with Bounds

Generate the final forecast with adjustable confidence thresholds:

```
function forecastMonthlySpending(currentMonthExpenses, previousMonthsExpenses,
                                 daysInMonth, minConfidenceThreshold,
                                 windowSize, decayFactor):

    // Step 1: Clean data and detect outliers
    dataPrep = prepareAndCleanData(currentMonthExpenses, previousMonthsExpenses)
    cleanedExpenses = dataPrep.cleaned

    // Step 2: Calculate weighted moving average
    wma = calculateWeightedMovingAverage(cleanedExpenses, windowSize, decayFactor)

    // Step 3: Calculate trend
    trend = calculateWeightedTrend(cleanedExpenses, decayFactor)

    // Step 4: Calculate confidence
    confidence = calculateForecastConfidence(cleanedExpenses,
                                            length(dataPrep.outliers),
                                            length(currentMonthExpenses))

    // Step 5: Calculate remaining days forecast
    daysElapsed = length(currentMonthExpenses)
    daysRemaining = daysInMonth - daysElapsed

    // Base forecast using WMA
    dailyForecast = wma

    // Adjust for trend
    trendAdjustment = trend.slope * daysRemaining / 2  // dampen trend slightly
    remainingDaysExpense = (dailyForecast + trendAdjustment) * daysRemaining

    // Step 6: Calculate confidence interval
    stdDev = standardDeviation(cleanedExpenses)
    standardError = stdDev / sqrt(daysRemaining)

    // Confidence-adjusted margin of error
    // Higher confidence = narrower bounds
    zScore = 1.96  // 95% confidence level
    adjustedZScore = zScore * (1 - confidence)  // reduces margin with higher confidence
    marginOfError = adjustedZScore * standardError

    // Step 7: Calculate total month forecast
    currentTotal = sum(currentMonthExpenses)
    monthEndForecast = currentTotal + remainingDaysExpense

    lowerBound = monthEndForecast - marginOfError
    upperBound = monthEndForecast + marginOfError

    // Step 8: Check against minimum confidence threshold
    meetsThreshold = confidence >= minConfidenceThreshold

    return {
        forecast: monthEndForecast,
        lowerBound: max(lowerBound, 0),  // spending can't be negative
        upperBound: upperBound,
        confidence: confidence,
        meetsThreshold: meetsThreshold,
        confidenceRating: getConfidenceRating(confidence),
        dailyAverageProjected: dailyForecast,
        trendSlope: trend.slope,
        outlierCount: length(dataPrep.outliers),
        dataPoints: length(cleanedExpenses)
    }

function getConfidenceRating(confidence):
    if confidence >= 0.85:
        return "Very High"
    else if confidence >= 0.70:
        return "High"
    else if confidence >= 0.55:
        return "Moderate"
    else if confidence >= 0.40:
        return "Low"
    else:
        return "Very Low"
```

---

## Output Structure

| Field | Description |
|-------|-------------|
| **forecast** | Most likely month-end total spending (point estimate) |
| **lowerBound** | Lower bound of confidence interval |
| **upperBound** | Upper bound of confidence interval |
| **confidence** | Confidence score (0.0 to 1.0) |
| **meetsThreshold** | Boolean: does forecast meet your minimum confidence requirement? |
| **confidenceRating** | Categorical rating (Very High, High, Moderate, Low, Very Low) |
| **dailyAverageProjected** | Weighted moving average of daily spending |
| **trendSlope** | Daily spending trend (positive = increasing, negative = decreasing) |
| **outlierCount** | Number of anomalies detected and excluded |
| **dataPoints** | Number of clean data points used in calculation |

---

## Usage Example

```
currentMonth = [45.50, 32.20, 67.80, 28.40]  // March 1-4, 2026
previousMonths = [38.20, 41.10, 35.50, 62.30, 39.80, 44.20, ...]  // Jan & Feb data

result = forecastMonthlySpending(
    currentMonthExpenses: currentMonth,
    previousMonthsExpenses: previousMonths,
    daysInMonth: 31,
    minConfidenceThreshold: 0.70,  // only accept forecasts 70%+ confident
    windowSize: 14,  // use 14-day weighted average
    decayFactor: 0.93  // exponential decay
)

// Output example:
{
    forecast: 1245.50,
    lowerBound: 1180.20,
    upperBound: 1310.80,
    confidence: 0.76,
    meetsThreshold: true,
    confidenceRating: "High",
    dailyAverageProjected: 42.35,
    trendSlope: 0.85,  // spending increasing by ~€0.85/day
    outlierCount: 2,
    dataPoints: 47
}
```

---

## Configuration Recommendations

| Parameter | Recommended Range | Purpose |
|-----------|-------------------|---------|
| **windowSize** | 7–21 days | Larger = smoother, less responsive to recent changes; smaller = more responsive |
| **decayFactor** | 0.90–0.97 | Higher (0.95+) = emphasize recent days; lower (0.90) = balance recent and historical |
| **minConfidenceThreshold** | 0.50–0.85 | Set based on your risk tolerance; higher = stricter filtering |
| **IQR multiplier** | 1.5 (standard) | Use 1.5 for normal data; use 3.0 for highly volatile spending |

---

## Key Advantages Over Basic Approach

- **Reduces early-month bias**: Historical data provides context for 4 days of current spending
- **Adapts to recent behavior**: Weighted moving average captures spending trend shifts
- **Handles anomalies**: Outlier detection prevents one-time purchases from skewing forecasts
- **Quantifies uncertainty**: Confidence score lets you decide whether to trust the forecast
- **Actionable filtering**: Set a confidence threshold to automatically flag unreliable forecasts
- **Transparent metrics**: Provides trend, outlier count, and data quality indicators for manual review

