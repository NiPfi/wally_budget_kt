# Two-Phase Bucket Closure With Rollover Carryover

## Summary
Change bucket closure from an immediate hard close into a two-phase lifecycle:

1. **Scheduled close** during the active cycle.
   The bucket stops accepting new expenses and is shown in a separate locked section, but its current-cycle allocation and expenses remain intact.
2. **Final close** at cycle rollover.
   The bucket becomes definitively closed only when the cycle is concluded, and its final surplus or deficit is absorbed into the portfolio via the default Spending Money bucket in the next active cycle.

This fixes the current bug where closing a bucket inserts a same-cycle `0` allocation adjustment and effectively releases planned money back to Spending Money before rollover.

## Behavior Spec

### Lifecycle states
Use three effective bucket states:

- **Open**
  Normal behavior.
- **Scheduled to close**
  Bucket remains part of current-cycle accounting, but:
  - is excluded from bucket pickers for new expenses
  - is excluded from editable/open bucket lists
  - is displayed in a separate read-only “Closing this cycle” section
  - can be reopened by clearing the scheduled-close flag before rollover
- **Closed**
  Finalized after rollover; no longer participates in active/future bucket lists.

### Accounting rules during the active cycle
When a bucket is scheduled to close mid-cycle:

- Keep its current-cycle allocation policy and current-cycle adjustments unchanged.
- Do **not** insert a same-cycle `0` allocation adjustment.
- Do **not** release any remaining planned amount into Spending Money during the same cycle.
- Keep all existing expenses assigned to that bucket unless manually reassigned.
- Remove/deactivate only **future-cycle** bucket policies and future adjustments.
- Keep the bucket visible in current-cycle totals and summaries so its locked funds remain accounted for.

### Rollover carryover rule
At rollover, when the scheduled close becomes definitive:

- compute the bucket’s final cycle balance as:
  - `cycle allocation - cycle spend`
- absorb that result into the portfolio through the default Spending Money bucket for the **new** cycle:
  - if positive, add the surplus to Spending Money
  - if negative, reduce Spending Money by the deficit
- then finalize the bucket as closed

This means:
- leftover funds are not stranded
- overspend is not silently discarded
- the next cycle starts with the closed bucket removed, but its net effect preserved in the portfolio/default bucket

## Data / API / Schema Changes

### Bucket persistence
Extend bucket persistence and domain models with scheduled-close metadata, for example:

- `scheduledCloseCycleEndDateExclusive: String?`
- optionally `scheduledCloseRequestedAtEpochMs: Long?`

Apply to:
- [`BudgetBucket.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/domain/model/BudgetBucket.kt)
- [`BudgetBucketEntity.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/data/local/entity/BudgetBucketEntity.kt)
- [`BudgetDatabase.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/data/local/db/BudgetDatabase.kt)
- snapshot import/export models with schema bump

Derived helpers:
- `isScheduledToClose`
- `isUnavailableForNewExpenses`
- `isClosed` remains definitive close/delete only

## Implementation Plan

### 1. Add scheduled-close state
Persist scheduled closure separately from `closedAtEpochMs`.

Decisions:
- `closedAtEpochMs` stays definitive-only
- scheduled close stores the cycle end when closure becomes effective
- undo is modeled as clearing scheduled-close state

### 2. Replace immediate same-cycle zeroing
Update:
- [`UpdateBudgetSettingsUseCase.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/domain/usecase/UpdateBudgetSettingsUseCase.kt)
- [`UpdatePortfolioPlanUseCase.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/domain/usecase/UpdatePortfolioPlanUseCase.kt)

When closing mid-cycle:
- set scheduled-close metadata
- keep current-cycle policy/allocation untouched
- soft-delete future policies/adjustments only

When reopening before rollover:
- clear scheduled-close metadata
- regenerate future policies for the bucket from its default allocation
- keep current-cycle policy untouched

### 3. Finalize closure and apply carryover at rollover
Update [`ConcludePendingCycleUseCase.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/domain/usecase/ConcludePendingCycleUseCase.kt):

For each bucket scheduled to close for the concluded cycle:
- resolve its effective cycle allocation
- load its total spend for that cycle
- compute `netCarryoverCents = allocation - spend`
- finalize the bucket by setting `closedAtEpochMs` and clearing scheduled-close metadata

Then apply carryover to the default Spending Money bucket for the **next** cycle:

- if the next-cycle default bucket policy already exists, update it by adding `netCarryoverCents`
- if it does not exist yet, create it with:
  - `default allocation for next cycle + netCarryoverCents`
- clamp only if required by current invariants; preferred behavior is to allow negative effective carryover to reduce the default bucket normally

If multiple buckets close at the same rollover:
- sum all their carryovers
- apply the aggregate delta once to the next-cycle default bucket policy

### 4. Keep scheduled-close buckets in current-cycle accounting
Update [`ObserveHomeOverviewUseCase.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/domain/usecase/ObserveHomeOverviewUseCase.kt):

- include scheduled-close buckets in current-cycle bucket summaries
- exclude only definitively closed/deleted buckets
- split output into:
  - open buckets
  - scheduled-close buckets shown in a locked section
- keep portfolio totals inclusive of scheduled-close allocations so Spending Money is not inflated mid-cycle

### 5. Keep scheduled-close buckets visible in current-cycle history
Update [`ObserveHistoryUseCase.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/domain/usecase/ObserveHistoryUseCase.kt):

- current cycle should show scheduled-close buckets by name
- they should not appear as orphaned/deleted buckets while pending final close
- once rollover completes, history continues from archived bucket monthly history

### 6. Exclude scheduled-close buckets from selection for new expenses
Update filters in:
- [`HomeScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/HomeScreen.kt)
- [`AddExpenseSheet.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/AddExpenseSheet.kt)
- [`HistoryScreenScaffold.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/history/HistoryScreenScaffold.kt)
- [`PendingCycleExpenseSheets.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/PendingCycleExpenseSheets.kt)

Rule:
- new expense assignment excludes scheduled-close buckets
- existing expenses can still be reassigned away from them

### 7. UI section for closing buckets
Update portfolio/home/history UI to render scheduled-close buckets in a separate locked section.

Expected UX:
- section title such as `Closing this cycle`
- rows show spend/allocation/remaining
- rows are read-only
- bucket editor allows reopening until rollover

Relevant files:
- [`PortfolioScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/PortfolioScreen.kt)
- [`BucketHomePage.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/BucketHomePage.kt)

### 8. Update close messaging and validation
Update [`BucketEditorSheet.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/BucketEditorSheet.kt):

- closing takes effect at end of current cycle
- funds remain locked until rollover
- final surplus or deficit moves into Spending Money at rollover
- bucket can be reopened until rollover

Validation:
- scheduled-close buckets are unavailable for new use
- definitively closed buckets remain non-reopenable

## Test Cases and Scenarios

### Domain/use-case tests
Add/update tests for:

1. Closing a bucket mid-cycle does not create a `0` current-cycle adjustment.
2. Closing a bucket mid-cycle does not increase current-cycle Spending Money.
3. Scheduled-close bucket remains visible in current-cycle summaries with locked allocation and spend.
4. Scheduled-close bucket is excluded from new-expense pickers.
5. Reopening before rollover clears scheduled-close state and restores future policies.
6. Rollover finalizes scheduled-close bucket by setting `closedAtEpochMs`.
7. Positive leftover from a closed bucket increases next-cycle default Spending Money allocation.
8. Negative leftover from a closed bucket decreases next-cycle default Spending Money allocation.
9. Multiple simultaneously closed buckets aggregate carryover into one next-cycle default-bucket delta.
10. Scheduled-close buckets do not appear as “Deleted bucket” in current-cycle history.
11. Current-cycle closeout/fund deposit calculations still use the locked allocation through cycle end.

Primary files:
- [`UpdateBudgetSettingsUseCaseBucketsTest.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/test/java/net/loeu/wallybudget/domain/usecase/UpdateBudgetSettingsUseCaseBucketsTest.kt)
- [`UpdatePortfolioPlanUseCaseTest.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/test/java/net/loeu/wallybudget/domain/usecase/UpdatePortfolioPlanUseCaseTest.kt)
- [`ObserveHomeOverviewUseCaseTest.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/test/java/net/loeu/wallybudget/domain/usecase/ObserveHomeOverviewUseCaseTest.kt)
- [`ObserveHistoryUseCaseTest.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/test/java/net/loeu/wallybudget/domain/usecase/ObserveHistoryUseCaseTest.kt)
- [`ConcludePendingCycleUseCaseTest.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/test/java/net/loeu/wallybudget/domain/usecase/ConcludePendingCycleUseCaseTest.kt)

### UI tests
Add/update tests for:
- scheduled-close bucket appears in separate locked section
- bucket picker excludes scheduled-close bucket
- bucket close copy mentions end-of-cycle carryover into Spending Money
- reopening before rollover removes the bucket from the locked section

## Assumptions and Defaults
- Carryover from a closing bucket is applied to the **next cycle’s default Spending Money bucket**, not released into the current cycle.
- Carryover is based on the bucket’s final current-cycle net result at rollover.
- Scheduled-close is the undoable state; no separate snackbar-only undo mechanism is required.
- Deficit carryover is allowed to reduce next-cycle Spending Money.
- Definitive closed buckets remain non-reopenable.
