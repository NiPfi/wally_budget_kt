# Portfolio Bucket Editing And Safe Close Flow

## Summary
Extend the Portfolio screen so buckets can be edited from their list row using the same bucket settings sheet already used from the bucket cogwheel, and add a safe close flow for named buckets.

This revision keeps the scope tight:
- make active bucket rows in Portfolio tappable
- reuse the existing bucket settings sheet instead of inventing a generic portfolio-item framework
- add an explicit close confirmation flow for irreversible bucket closure
- define the post-close UI behavior and stale-state handling
- leave funds read-only and unchanged except for any minimal visual cleanup that falls out naturally from the bucket work

## Scope
In scope:
- Portfolio bucket row interaction
- shared reuse of the existing bucket settings sheet/card
- safe close flow for named buckets
- explicit handling of closing the selected bucket
- stale-state protection on save/close
- tests for edit/close edge cases

Out of scope:
- fund editing
- fund closing
- new shared bucket/fund abstraction layer
- moving editor state into `BudgetViewModel`
- reopening closed buckets
- changing how historical expenses tied to closed buckets are modeled

## Product Decisions Locked
- Close requires confirmation.
- Close remains initiated from inside the bucket settings sheet.
- The default bucket can be renamed only.
- Funds remain read-only for this PR.
- The implementation will use a unified submit path based on `BucketDraft`; no separate save-vs-close persistence APIs.

## Implementation Design

### 1. Reuse and rename the existing bucket editor surface
Refactor the existing sheet in [`AddBucketSheet.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/AddBucketSheet.kt) only as much as needed so both Home and Portfolio call the same editor.

Concrete direction:
- keep one bucket editor sheet composable
- keep one editor state model
- keep one validation path
- if naming is misleading, rename the file or the composable, but do not split it into multiple new abstractions unless the code becomes materially clearer

Expected changes:
- `HomeBucketSettingsSheet` becomes a neutral bucket editor sheet name
- `HomeBucketEditorState` stays a simple local UI state model
- helper functions for updating drafts remain colocated with this sheet unless a rename-only extraction is clearly cleaner

### 2. Keep editor state local to the screen for this PR
Do not move editor state into `BudgetViewModel` in this change.

Reason:
- there is no shared cross-screen editor flow active at the same time
- both Home and Portfolio can safely hold short-lived local sheet state
- lifting this into the ViewModel would broaden scope without solving the immediate feature

Local state to add in `PortfolioScreen`:
- `editingBucketUuid: String?` or equivalent editor key
- derived editor state from current `allBuckets` and `bucketSummaries`
- `showCloseConfirmationForBucketUuid: String?` or equivalent confirmation dialog state
- optional dirty-state tracking inside the sheet for discard confirmation

Important detail:
- store the selected bucket UUID, not a frozen copy of the whole editor payload, so the latest flow values are always used to derive the rendered form and submit data

### 3. Make active bucket rows in Portfolio tappable
Update [`PortfolioScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/PortfolioScreen.kt) and the active-buckets section in [`HomeScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/HomeScreen.kt) so bucket rows can open the shared editor.

Behavior:
- tapping an active bucket row opens the shared bucket settings sheet
- only open buckets appear in the Portfolio active-buckets list
- the row remains simple; no inline close button is added
- funds remain read-only and non-interactive

No generic “manageable item” abstraction should be introduced. Keep the bucket section direct and concrete.

### 4. Use a unified submit path based on `BucketDraft`
The editor sheet will submit a single `BucketDraft` shape for both save and close.

Behavior:
- normal save submits `closeRequested = false`
- close submits `closeRequested = true`
- callers always rebuild the full bucket draft list and call `onSavePortfolioPlan(...)` through one path

This keeps the call sites simple and aligned with the existing domain API.

### 5. Add close confirmation for named buckets
Closing is irreversible enough to require an explicit confirmation dialog.

UI behavior:
- named, open, non-default buckets show a `Close bucket` action inside the editor sheet
- tapping `Close bucket` does not persist immediately
- instead, it opens a confirmation dialog
- confirmation copy must explicitly say that closing the bucket removes it from active planning and cannot be undone from the UI
- dialog actions:
  - `Cancel`
  - `Close bucket`

Confirmation copy should mention the real impact in plain language:
- the bucket will stop being active
- future planning for that bucket is cleared
- existing history/expenses are not deleted

Implementation detail:
- the actual submitted `BucketDraft` on confirm must carry:
  - the correct `bucketUuid`
  - the current bucket name
  - the current tracking and balance behavior
  - the current sort order
  - `closeRequested = true`

### 6. Define post-close UI behavior explicitly
Post-close behavior must be deterministic.

When a bucket is closed from Portfolio:
- the confirmation dialog dismisses
- the editor sheet dismisses
- Portfolio remains on the Portfolio screen
- the active bucket list refreshes from flows and the closed bucket disappears

When the closed bucket was the currently selected bucket elsewhere in app state:
- no explicit client-side navigation jump is performed from Portfolio
- the app relies on `UpdatePortfolioPlanUseCase` to resolve and persist a valid open `selectedBucketUuid`
- any screen that later reads selected-bucket state will observe that updated selection from the normal flows
- there is no temporary custom UI workaround in Portfolio for the old selected bucket

This PR should not add navigation side effects after close. It should rely on the existing selection-resolution backend behavior, but the test plan must verify that a selected bucket close persists a different valid selection.

### 7. Handle stale state on submit
Do not submit a frozen editor snapshot captured when the sheet opened.

On both save and close:
- re-read the current bucket from the latest `allBuckets`
- rebuild the current draft list from the latest `allBuckets` plus latest `bucketSummaries`
- apply the user’s edits on top of that fresh data
- submit the resulting full draft list to `onSavePortfolioPlan(...)`

This avoids submitting stale sort order / metadata / allocation context if a sync, cycle event, or other refresh happens while the sheet is open.

If the edited bucket no longer exists or is already closed by the time the user submits:
- dismiss the sheet
- do not submit
- optionally surface a brief message if the existing screen has a suitable message channel; otherwise fail silently for this PR rather than inventing new messaging infrastructure

### 8. Pin default-bucket behavior and draft value
For the default bucket:
- the row is tappable
- the editor allows renaming
- allocation remains disabled/read-only
- no close action is shown

Draft rule:
- always submit `defaultAllocatedAmountCents = 0L` for the default bucket from this editor path
- the backend already recomputes the actual default remainder, so the UI should not try to preserve or infer that value

This removes ambiguity and keeps the UI honest.

### 9. Clarify allocation validation during close
Close should not be blocked by allocation validation against the bucket being closed.

Rule:
- when closing a bucket, validation should treat that bucket as removed from the surviving named allocations
- no extra “remaining allocations exceed portfolio total” validation is needed beyond the normal backend rules, because the closed bucket’s allocation is dropped and the default bucket absorbs the remainder

Practical implementation:
- the confirmation-submit path should build a draft list where only the target bucket flips to `closeRequested = true`
- the existing `UpdatePortfolioPlanUseCase` validation is then sufficient

### 10. Add dirty-dismiss protection
The editor sheet should not silently drop user edits on swipe-dismiss or cancel.

Behavior:
- track whether the editor form is dirty relative to the current derived bucket state
- if the user dismisses the sheet with no changes, close immediately
- if the user dismisses the sheet with unsaved changes, show a discard confirmation dialog
- dialog actions:
  - `Keep editing`
  - `Discard changes`

This applies to both swipe-to-dismiss and cancel/back affordances inside the sheet.

### 11. Acknowledge behavior of expenses tied to closed buckets
This PR will not change expense ownership/history behavior.

Explicit assumption to preserve:
- existing expenses remain tagged with their original `bucketUuid`
- closing a bucket only removes it from active planning/open-bucket surfaces
- historical expense rendering and any closed-bucket history treatment stay as-is

The plan should not attempt to redesign this now, but the close confirmation copy must avoid implying that historical expenses are deleted.

## Public API / Interface Changes
Expected internal interface changes:

- `PortfolioOverviewPage(...)` will gain a bucket-click callback, likely:
  - `onEditBucket: (String) -> Unit`
- `ActiveBucketsSection(...)` will become interactive and likely accept:
  - `allBuckets` or enough info to resolve row click identity
  - `bucketSummaries`
  - `onEditBucket`
- the shared bucket editor sheet will expose:
  - a unified `onSubmit(BucketDraft)` callback
  - an `onRequestClose()` path that only opens confirmation locally, not a second persistence callback
  - dismiss interception for dirty-state discard confirmation

No domain or database interface changes are required.

## Tests And Scenarios

### Unit tests
Add or update unit tests for helper logic around the reused editor:
- building a fresh updated draft list for a normal save
- building a fresh updated draft list for close with `closeRequested = true`
- default bucket editor submission always uses `defaultAllocatedAmountCents = 0L`
- stale submit path uses latest bucket metadata/sort order rather than a frozen initial snapshot

Keep existing `UpdatePortfolioPlanUseCaseTest` close coverage and add only missing gaps:
- closing the selected bucket resolves to a different valid open selection if one exists
- closing the last named bucket succeeds and leaves only the default bucket open

### Compose/UI tests
Add Portfolio interaction tests:
- tapping a bucket row opens the bucket settings sheet
- saving bucket edits from Portfolio calls `onSavePortfolioPlan` with the updated draft list
- named bucket editor shows `Close bucket`
- default bucket editor does not show `Close bucket`
- tapping `Close bucket` opens the confirmation dialog
- confirming close submits a draft list where only that bucket has `closeRequested = true`
- dismissing the editor with unsaved changes opens discard confirmation
- closing the last named bucket removes it from the active list after state refresh
- funds section still renders read-only and is not clickable

### Close-related regression scenarios
Cover these explicitly:
- closing a non-default named bucket
- closing the currently selected bucket
- closing the last named bucket so only the default bucket remains
- verifying the close draft retains the correct `bucketUuid`, `sortOrder`, and behavior metadata
- verifying the close path does not require manual default-bucket allocation edits

## Verification
Run:
- `./gradlew testDebugUnitTest`
- `./gradlew detekt`
- `./gradlew connectedDebugAndroidTest` if emulator execution is available for this task

## File Areas Expected To Change
Primary files:
- [`PortfolioScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/PortfolioScreen.kt)
- [`HomeScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/HomeScreen.kt)
- [`AddBucketSheet.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/AddBucketSheet.kt)

Likely tests:
- a new Portfolio screen instrumentation test file
- helper/unit tests near existing bucket UI tests or use case tests

## Assumptions And Defaults
- The existing backend close semantics are accepted for this PR; the UI’s responsibility is to make that action explicit and safe.
- Portfolio should not navigate away after close; it just refreshes.
- The selected bucket transition is handled by existing use-case persistence logic rather than new UI routing.
- Funds remain untouched functionally in this PR.
- Historical expenses on closed buckets remain intact and unchanged.
