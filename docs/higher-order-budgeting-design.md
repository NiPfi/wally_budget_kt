# Higher-Order Budgeting Design

## Goal

Introduce an overall budget that is split into multiple buckets.

- Each bucket has its own planned amount for a cycle.
- Each expense belongs to exactly one bucket.
- Buckets do not directly change each other's remaining amount.
- The overall surplus or deficit is the sum of all bucket outcomes.
- Buckets can be long-lived or temporary.

This is groundwork for future features such as savings goals, but it does not implement savings goals yet.

## Non-goals

- No savings goal entity, goal tracking, or goal forecast yet.
- No income tracking.
- No explicit manual transfer flow between buckets.
- No per-bucket payday or cycle schedule. All buckets share one global cycle schedule.
- No dedicated "trip planning" workflow. The goal here is only to support ephemeral buckets in the core model.

## Why The Current Model Is Not Enough

The current app is built around one active cycle budget:

- `UserSettings.monthlyBudgetCents` is the planned amount for the whole app.
- `ExpenseEntity` has no bucket identity.
- `MonthlyHistoryEntity` stores one row per cycle, not per bucket.
- `BudgetState` represents one active budget state.
- `BudgetPolicy` mixes cycle schedule and cycle amount in one record.

That works for a single "spending money" budget, but it cannot express:

- separate bill and spending buckets,
- bucket-local overspend without mutating another bucket's budget state,
- an overall reserve that is larger than any single bucket,
- a temporary bucket that accrues money across cycles and is later closed,
- future savings features based on aggregate surplus.

## Proposed Concepts

### Portfolio

The higher-order budget. It represents the full money plan for a cycle.

- Owns the global cycle schedule.
- Aggregates all bucket allocations and spending.
- Exposes the overall reserve and cycle surplus.

### Bucket

A child budget inside the portfolio.

- Has a name and display order.
- Has a per-cycle planned allocation.
- Owns its own expenses and history.
- Can overspend or underspend independently.

### Reserve

The money available at the portfolio level.

- Completed-cycle reserve: sum of archived cycle surplus across all buckets.
- Active-cycle net: sum of current bucket remaining amounts.
- Net reserve: completed-cycle reserve plus active-cycle net.
- Earmarked reserve: the part of net reserve currently retained by open carryover buckets.
- Unassigned reserve: the part of net reserve that is not currently earmarked.

This is the number that a later savings-goal feature would use.

### Ephemeral Bucket

A normal bucket with two extra characteristics:

- it can retain its own surplus across cycles instead of returning it immediately to the general portfolio pool,
- it can later be closed, at which point any positive leftover stops being earmarked and becomes general portfolio reserve again.

This avoids creating a special "trip" feature. A trip bucket is just a bucket with the right balance behavior and lifecycle.

## Core Rules

1. All active buckets share the same cycle boundaries.
2. Every expense belongs to one bucket.
3. A bucket's remaining amount is based only on that bucket's allocation and that bucket's expenses.
4. The portfolio remaining amount is the sum of all bucket remaining amounts.
5. Bucket overspend is allowed. It reduces portfolio surplus, but it does not rewrite another bucket's budget state.
6. Bucket surplus is not "transferred" explicitly. It simply remains part of the portfolio reserve.
7. A bucket can be closed. Closed buckets keep history but cannot receive new expenses or new allocations.
8. Closing a bucket releases any positive earmarked leftover back to the portfolio. Negative carry does not need a special close action because overspend has already reduced the portfolio base.

## Bucket Balance Behavior

Tracking mode and balance behavior are separate concerns.

- `RETURN_TO_PORTFOLIO`: cycle leftover remains part of portfolio reserve and is not earmarked to the bucket next cycle.
- `RETAIN_IN_BUCKET`: positive leftover remains earmarked to that bucket across cycles until spent or the bucket is closed.

This makes the common combinations explicit:

- daily spending: `DAILY_TARGET` + `RETURN_TO_PORTFOLIO`
- bills: `CYCLE_RESERVE` + `RETURN_TO_PORTFOLIO`
- trip or temporary fund: `CYCLE_RESERVE` + `RETAIN_IN_BUCKET`

## Bucket Tracking Modes

Not every bucket needs the same presentation.

- `DAILY_TARGET`: current behavior. Show daily budget, today's remaining amount, and forecast.
- `CYCLE_RESERVE`: show cycle allocation and cycle remaining amount, but do not center the UI on daily pacing.

This matters for the concrete use case:

- "Spending money" is a `DAILY_TARGET` bucket.
- "Bills" is usually a `CYCLE_RESERVE` bucket.

The calculation engine can still reuse the same cycle math underneath, but the UI does not need to force a daily-budget framing onto every bucket.

## Target Domain Model

### New entities

- `BudgetBucket`
  - `bucketUuid`
  - `name`
  - `trackingMode`
  - `balanceBehavior`
  - `sortOrder`
  - `isPrimary`
  - `closedAtEpochMs`
  - standard sync metadata

- `BucketAllocationPolicy`
  - `allocationUuid`
  - `bucketUuid`
  - `cycleStartDate`
  - `cycleEndDateExclusive`
  - `allocatedAmountCents`
  - standard sync metadata

- `BucketAllocationAdjustment`
  - optional for mid-cycle proration, mirroring the current adjustment model
  - scoped to `bucketUuid` and `cycleStartDate`

- `BucketMonthlyHistory`
  - `bucketUuid`
  - `cycleStartDate`
  - `cycleEndDateExclusive`
  - `budgetAmountCents`
  - `totalSpentCents`
  - `surplusCents`
  - `endTimestamp`

### Existing entities to extend

- `Expense`
  - add `bucketUuid`

- `UserSettings`
  - add `primaryBucketUuid`
  - add `selectedBucketUuid`
  - stop using `monthlyBudgetCents` as an active source of truth once buckets exist

`closedAtEpochMs` is enough for the lifecycle groundwork. Buckets should be treated as append-only historical records after closure. If the user wants a new trip later, create a new bucket instead of reopening the old one.

## Keep Schedule Global

The current `BudgetPolicy` carries both schedule and amount. That coupling becomes awkward with multiple buckets because changing the payday would require rewriting matching policy rows for every bucket.

The cleaner long-term split is:

- global cycle schedule stays global,
- bucket allocations become bucket-scoped.

For an incremental rollout, the current `BudgetPolicy` infrastructure can remain the source of cycle boundaries while new bucket allocation records own the per-bucket amounts.

That avoids multiplying schedule records by bucket count.

## Read Model

The current single `HomeOverviewState` should become a portfolio-plus-selected-bucket read model.

- `PortfolioOverviewState`
  - `effectiveCurrentDate`
  - `portfolioState`
  - `bucketSummaries`
  - `selectedBucket`
  - `selectedBucketExpenses`
  - `selectedBucketExpenseSections`
  - `pendingCycleCloseoutState`
  - `timelineLockState`

- `PortfolioState`
  - `totalAllocatedThisCycleCents`
  - `totalSpentThisCycleCents`
  - `remainingThisCycleCents`
  - `completedCycleReserveCents`
  - `netReserveCents`
  - `earmarkedReserveCents`
  - `unassignedReserveCents`
  - `cycleStartDate`
  - `cycleEndDateExclusive`

- `BucketSummaryState`
  - `bucket`
  - `allocatedThisCycleCents`
  - `spentThisCycleCents`
  - `remainingThisCycleCents`
  - `overspentCents`
  - `earmarkedBalanceCents`
  - optional daily pacing fields when `trackingMode == DAILY_TARGET`

The current `BudgetState` can survive as the bucket-local state for `DAILY_TARGET` buckets, but it should stop pretending to be the whole app budget.

## Calculation Semantics

### Bucket level

For each bucket in the active cycle:

- `bucketRemaining = bucketAllocation - bucketSpent`
- `bucketOverspent = max(0, -bucketRemaining)`

If the bucket is `DAILY_TARGET`, keep the current daily-budget logic:

- `dailyBudgetCents`
- `spentTodayCents`
- `remainingTodayCents`
- `daysRemainingInCycle`
- bucket-local forecast

If the bucket is `CYCLE_RESERVE`, the important outputs are:

- `allocatedThisCycleCents`
- `spentThisCycleCents`
- `remainingThisCycleCents`

For buckets with `RETAIN_IN_BUCKET`:

- `earmarkedBalanceCents = max(0, completedCycleSurplusCarryover + remainingThisCycleCents)`

Where:

- `completedCycleSurplusCarryover` is the sum of completed-cycle surplus for that bucket across its lifetime while the bucket is open,
- negative outcomes reduce the bucket's effective balance, but only positive net balance remains earmarked.

### Portfolio level

- `totalAllocated = sum(bucketAllocation)`
- `totalSpent = sum(bucketSpent)`
- `remainingThisCycle = totalAllocated - totalSpent`
- `completedCycleReserve = sum(all archived bucket surplus)`
- `netReserve = completedCycleReserve + remainingThisCycle`
- `earmarkedReserve = sum(earmarkedBalance of open RETAIN_IN_BUCKET buckets)`
- `unassignedReserve = netReserve - earmarkedReserve`

This gives the exact behavior requested:

- leftover spending money remains available at the portfolio level,
- bill overspend reduces overall surplus,
- one bucket can offset another without changing its internal budget state.
- a trip bucket can accrue across cycles without leaving the portfolio accounting model.

## Bucket Lifecycle

### Open bucket

- participates in cycle allocation,
- can receive expenses,
- contributes to active bucket lists,
- may retain surplus if `balanceBehavior == RETAIN_IN_BUCKET`.

### Closed bucket

- remains in history and snapshots,
- is excluded from active allocation and expense-entry choices,
- keeps its past expenses and cycle history visible,
- no longer contributes earmarked balance to active reserve accounting.

Closing a bucket should therefore have this semantic effect:

- if `earmarkedBalanceCents > 0`, that amount becomes part of `unassignedReserveCents`,
- if `earmarkedBalanceCents <= 0`, there is nothing to release because the portfolio base has already absorbed the overspend.

## Ephemeral Bucket Example

Trip bucket flow:

1. Create a `CYCLE_RESERVE` bucket with `RETAIN_IN_BUCKET`.
2. Allocate part of the portfolio budget to it for one or more cycles.
3. Let its leftover accumulate as earmarked bucket balance.
4. Record trip expenses against that bucket when spending starts.
5. Close the bucket after the trip.
6. Any positive leftover returns to unassigned portfolio reserve automatically through the accounting rules above.

## Persistence Changes

### Database

Recommended additive schema changes:

1. Create `budget_buckets`.
2. Create `bucket_allocation_policies`.
3. Create `bucket_allocation_adjustments` if current-cycle proration must remain supported.
4. Add `bucketUuid` to `expenses`.
5. Replace single-row-per-cycle history with bucket-scoped history.

For history, a new table is cleaner than mutating the existing primary key:

- `bucket_monthly_history`
  - primary key: `bucketUuid`, `cycleStartDate`

Aggregate history should be derived by grouping bucket history rows by cycle.

Bucket closure should be persisted on the bucket record itself. No separate closure table is required for the groundwork if the release behavior is derived from the active read model.

### Snapshot

Introduce a new snapshot schema version that includes:

- bucket metadata,
- bucket allocation policies,
- bucket allocation adjustments if present,
- bucket id on expenses,
- bucket-scoped history.

Importing the current snapshot version should create one default bucket and map all legacy data into it.

## Migration Strategy

### Existing users

On first migration:

1. Create one default bucket, for example "Spending money".
2. Mark it as the primary and selected bucket.
3. Assign all existing expenses to that bucket.
4. Backfill bucket history from existing monthly history rows.
5. Backfill bucket allocation policies from existing single-budget settings and policies.

This preserves current behavior exactly for migrated users until they add more buckets.

### New users

Keep onboarding simple:

- Create one default primary bucket during onboarding.
- Let users add more buckets later from settings.

That avoids turning onboarding into a multi-step allocation wizard before the foundation is proven.

## Use Case Changes

### Expense flows

- `AddExpenseUseCase` and `UpdateExpenseUseCase` must require a `bucketUuid`.
- The add-expense UI must either:
  - default to the selected bucket, or
  - let the user choose a bucket explicitly.

### Home overview

`ObserveHomeOverviewUseCase` should:

1. observe all buckets,
2. resolve the active cycle once,
3. load aggregate expenses for portfolio math,
4. load selected-bucket expenses for the detailed list,
5. compute earmarked versus unassigned reserve,
6. build both portfolio and bucket states.

### Forecast

`ObserveForecastUseCase` should become bucket-scoped.

- Keep the current forecast behavior for `DAILY_TARGET` buckets.
- Skip forecast for `CYCLE_RESERVE` buckets in v1.

That keeps the current feature intact without inventing a weak forecast for bill-style buckets.

### History

History should be stored per bucket and read in two ways:

- aggregate by cycle for the main history screen,
- filtered by bucket for bucket drill-downs later.

## Settings Model

Settings work should split into two kinds of changes:

- cycle schedule changes: payday and cycle boundary behavior,
- bucket allocation changes: amounts and bucket lifecycle.

The existing settings flow for one global `monthlyBudgetCents` should not remain the long-term source of truth. Once multiple buckets exist, the overall amount is derived:

- `overallCycleBudget = sum(active bucket allocations for that cycle)`

That prevents drift between a global number and the bucket totals.

## Recommended UI Shape

For the first user-facing iteration:

- Home header shows the portfolio reserve and overall cycle remaining.
- Portfolio reserve should distinguish `unassigned` versus `earmarked` when retained buckets exist.
- Below that, show a horizontal bucket switcher or summary row.
- The main detail area shows the selected bucket.
- The current daily-budget widgets stay attached to the selected `DAILY_TARGET` bucket.
- `CYCLE_RESERVE` buckets show cycle remaining and expense history, not daily pacing.
- Closed buckets stay out of active navigation but remain available in history.

This preserves the current mental model while adding the higher-order layer.

## Why This Is Good Groundwork For Savings Goals

Even without implementing goals yet, this design creates the primitives a future goal feature needs:

- a portfolio-level available reserve,
- a distinction between total reserve and earmarked reserve,
- bucket-scoped spending behavior,
- aggregate cycle surplus over time,
- bucket lifecycle with closure and leftover release,
- a clean place to add goal projection logic later without entangling it with one bucket's daily budget.

The important part is that savings become a portfolio concept, not a side effect of one bucket's state.
