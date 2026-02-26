Task receipt: create a detailed implementation plan for the Android budgeting app features requested.
High-level approach: outline data model and storage, then UI flow, gauge animation and formatting, then reset logic and tests.
Checklist: define models/storage, design Compose UI, implement gauge/formatting, implement monthly reset, add tests.

## Plan: Android Budgeting App Implementation

Outline entities for budget, spending, and settings; choose local persistence; design Compose screens and a gauge with smooth animation; ensure currency formatting respects locale; implement monthly reset with configurable start day; and cover key behavior with unit and UI tests.

### Steps
1. Define data model entities and state in `app/src/main/java/net/loeu/wallybudget/` (e.g., `Budget`, `Expense`, `UserSettings`, `MonthlyCycle`) and a `BudgetRepository` interface.  
2. Add storage dependencies (Room + DataStore) in `app/build.gradle.kts`, create Room schema/DAOs for expenses and history, and use DataStore for user settings (monthly budget, start day, last reset date).  
3. Build Compose UI scaffolding in `app/src/main/java/net/loeu/wallybudget/MainActivity.kt` and new screens (e.g., `HomeScreen`, `AddExpenseSheet`) wired to a `BudgetViewModel`.  
4. Implement an analog counter composable (e.g., `AnimatedCounter`) that displays currency amounts with a rolling digit animation when values change; integrate locale currency formatting utilities for all amounts.  
5. Add monthly reset logic that calculates daily budget as: (remaining budget) / (days until next payday), ensuring months with fewer days result in slightly higher daily budgets; persist last-reset metadata in DataStore settings.  
6. Add unit tests for calculations and reset rules, plus Compose UI tests for counter animation and formatting.

### Implementation Details

#### Storage Architecture
- **Room Database**: Store `Expense` entities (id, amount, description, timestamp, icon) with DAOs for CRUD operations, queries by date range, and monthly aggregations.
- **DataStore Preferences**: Store user settings (monthly budget amount, payday/start day of month, last reset timestamp).
- **Repository pattern**: `BudgetRepository` abstracts Room + DataStore access for ViewModels.

#### Daily Budget Calculation
- Formula: `dailyBudget = (monthlyBudget - totalSpentThisCycle) / daysUntilNextPayday`
- This ensures shorter months naturally get higher daily allowances, and spending adjusts the remaining days' budgets dynamically.
- Handle edge case: if payday is day 31 but current month has 30 days, use day 30 as the payday for that month.

#### Analog Counter Animation
- Display daily allowance as large animated digits (not a circular gauge).
- When recording an expense, animate each digit rolling/spinning like an odometer or slot machine.
- Use Compose's `animateFloatAsState` or `Animatable` to smoothly transition digit positions with easing.
- Show currency symbol and decimal formatting per system locale.

#### Icon System
- Provide a small set of optional predefined icons using Material Icons (system SVG icons available in Compose).
- Icons: shopping cart, restaurant, car/transit, entertainment, home, health, other/misc.
- Icons are optional; expenses can be created without selecting an icon.
- Display icon in expense log entries when present.

#### Monthly History & Savings Tracking
- At the end of each budget cycle, calculate: `surplus/deficit = monthlyBudget - totalSpent`
- Maintain a running "overall savings/deficit" counter that accumulates these values across all months.
- Historical view shows:
  - Visual comparison bar/chart of budgeted vs actual spending for previous months
  - Month-by-month surplus/deficit with color coding (green for surplus, red for deficit)
  - Cumulative savings/deficit prominently displayed to show if budget needs adjustment
- This helps users see patterns and adjust their monthly budget target over time.

#### Animation Timing
- Counter animation: Quick and snappy at ~250-300ms with smooth easing (e.g., FastOutSlowIn).
- Keep UI responsive and avoid long animation delays that interrupt expense entry flow.

### Potential Flaws & Missing Considerations

#### 1. First-Time Setup & Onboarding
- **Issue**: No plan for initial setup flow when user first opens the app.
- **Solution**: Add onboarding screen to collect: monthly budget amount, payday date (1-31), and optionally explain how the daily budget calculation works.
- **Implementation**: Check if monthly budget and payday are set in DataStore on app startup. If missing, show onboarding; if present, proceed to main screen.

#### 2. Mid-Cycle Budget Changes
- **Issue**: What happens if user changes their monthly budget or payday in the middle of a cycle?
- **Solution**: Allow changes but recalculate daily budget immediately with remaining budget. This is the simplest and most flexible approach.
- **Implementation**: When settings are updated, trigger recalculation: `newDailyBudget = (newMonthlyBudget - totalSpentThisCycle) / daysRemainingInCycle`

#### 3. Negative Daily Budget Handling
- **Issue**: If user overspends heavily, daily budget could go negative or near-zero for many days.
- **Solution**: 
  - Display negative daily budgets clearly (e.g., "-€5.23 per day")
  - Add helpful message suggesting budget increase or spending reduction
  - Don't block expense entry even with negative budget

#### 4. UI Layout & Daily Focus
- **Primary View**: Daily remaining budget displayed prominently in large animated digits at top of screen.
- **Expense Log**: List of today's expenses visible below or accessible by swiping up.
- **Monthly Budget**: Available but not directly visible (e.g., in settings or secondary view) to maintain focus on daily spending.
- **Daily Rollover**: Savings from one day accumulate to next day's budget; overspending is deducted from next day's budget.
- **Multiple Expenses**: Allow unlimited expenses per day, grouped by date in the log with daily totals.
- **Expense Entry**: Quick, straightforward input should be the primary action (e.g., FAB or prominent button).

#### 5. Expense Editing Impact
- **Issue**: Editing/deleting past expenses affects daily budget calculations and cumulative savings.
- **Solution**:
  - Recalculate daily budgets and savings when expenses are modified within the current cycle
  - Allow editing/deleting expenses from previous cycles, but only update that cycle's historical surplus/deficit
  - Keep expense log easy to edit: swipe actions or tap-to-edit with confirmation for deletion

#### 6. Time Zone & Date Handling
- **Issue**: Payday/reset logic needs consistent date handling across time zones and DST changes.
- **Solution**: Use `LocalDate` (not timestamp) for budget cycle calculations; store expense timestamps in UTC but display in local time.

#### 7. Data Backup & Export
- **Solution**: Add export functionality to CSV/JSON for data portability (FOSS principle).
- **Implementation**:
  - Export all expenses with columns: date, amount, description, icon, cycle
  - Include settings and cumulative savings/deficit in export
  - Provide share intent to allow user to save/send file via any app
  - Format: CSV for spreadsheet compatibility, or JSON for complete data structure

#### 8. Accessibility
- **Solution**: 
  - Add proper content descriptions for TalkBack
  - Respect system "Reduce Motion" setting: when enabled, update counter instantly without animation
  - Ensure sufficient color contrast for deficit/surplus indicators

#### 9. Edge Case: First Day of Cycle
- **Issue**: On payday (day 0), the calculation `daysUntilNextPayday` would be ~30, but this is the START of a fresh cycle.
- **Solution**: Clarify that "days until next payday" means "days remaining in current cycle" and calculate as: `daysInCycle - daysSincePayday`.

#### 10. Minimum SDK Version
- **Issue**: Current `build.gradle.kts` sets `minSdk = 35` which only supports Android 15+ (very limited audience).
- **Solution**: Lower to `minSdk = 26` (Android 8.0, ~95% device coverage) or `minSdk = 24` (Android 7.0, ~98%) for better FOSS accessibility.

#### 11. Offline-First Architecture
- **Clarification**: Room + DataStore are inherently offline-first. This is a key feature: no internet required, fully privacy-respecting, all data stays on device.

#### 12. Testing Strategy
- **Focus**: UI and integration testing to ensure proper expense entry flow, counter animation rendering, delete confirmation, and visual correctness.
- **Edge cases to test**: Month transitions, leap years, payday > month days, negative budgets, rapid expense entry.

