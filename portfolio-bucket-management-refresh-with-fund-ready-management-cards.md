# Portfolio Bucket Management Refresh With Fund-Ready Management Cards

## Summary
Move bucket management into the portfolio list by reusing the existing bucket settings sheet/card flow, then reshape the portfolio sections so the same list-item and editor orchestration can later support funds.

This change will:
- make each bucket row in Portfolio directly editable
- keep add-bucket in Portfolio
- add a close action inside the reused bucket settings sheet
- preserve current backend behavior for bucket closing via `UpdatePortfolioPlanUseCase`
- introduce shared UI/state structure for “manageable portfolio items” so funds can adopt the same pattern later
- keep fund behavior read-only for now, but refactor the funds section onto the same management-list framework where sensible

## Scope
In scope:
- Portfolio screen bucket row interaction
- Reuse/refactor of `HomeBucketSettingsSheet`
- Close-bucket UX inside that sheet
- Shared management-row/editor plumbing intended for future fund support
- Unit/UI tests for the new behavior

Out of scope:
- new fund persistence use cases
- editing or closing funds
- reopening closed buckets
- settings screen redesign beyond extracting reusable bucket-management pieces

## Product Decisions Locked
- Funds scope: architecture groundwork only
- Close UX: close action lives inside the edit sheet/card
- Default bucket behavior: rename only from Portfolio; no close; no manual allocation edits

## Implementation Design

### 1. Extract reusable portfolio-management UI building blocks
Create a small reusable layer under `ui/screens/home` for list-based management cards.

Likely shape:
- a reusable section shell for “manageable portfolio items”
- a reusable clickable row/card presentation used by buckets now and funds later
- a lightweight descriptor model for row rendering, keeping bucket-specific editing logic outside the generic shell

Suggested responsibilities:
- generic row shell handles title, subtitle/supporting lines, optional trailing affordance, click handling
- bucket adapter builds row content from `BucketSummaryState` and `BudgetBucket`
- funds section can reuse the same row shell but remain non-clickable/read-only for now

This groundwork should avoid introducing a fake common domain type. Keep sharing at the UI/presentation layer only.

### 2. Refactor bucket editor state into a reusable bucket-management surface
Promote the current bucket editor model and helpers out of the narrow add-sheet file so both Home and Portfolio can use the same bucket edit flow.

Move or rename:
- `HomeBucketEditorState`
- `HomeBucketSettingsSheet`
- helper functions that build/update bucket drafts from current buckets/summaries

Target outcome:
- one bucket editor sheet component
- one mapping from existing bucket + current summary allocation into editable state
- one save callback shape producing `BucketDraft`

Do not duplicate the validation logic currently in the sheet. Centralize it.

### 3. Make bucket rows editable from Portfolio
Update `PortfolioScreen` and `PortfolioOverviewPage` so active bucket rows are interactive.

Behavior:
- tapping a bucket row opens the shared bucket settings sheet
- save applies `onSavePortfolioPlan` immediately using the existing draft-rebuild helper path
- the list remains based on open buckets only
- after successful save/close, the sheet dismisses and the refreshed state comes from the existing flows

State to add in `PortfolioScreen`:
- selected/editing bucket editor state
- open/close state for the bucket settings sheet

Data flow:
- derive editor state from `allBuckets` plus `bucketSummaries`
- on save, call `buildUpdatedHomeBucketDrafts(...)` or equivalent extracted helper
- keep portfolio monthly budget sourced from `userSettings.resolvedPortfolioMonthlyBudgetCents`

### 4. Add close-bucket action to the shared sheet
Extend the reused bucket settings sheet with a close interaction.

Behavior:
- non-default open buckets show a dedicated destructive/secondary “Close bucket” action inside the sheet
- default bucket never shows close
- already-closed buckets are not expected in Portfolio, so no reopen path is needed
- closing submits a `BucketDraft` with `closeRequested = true`
- allocation/name validations should not block close unless required by the backend contract
- sheet copy should make it clear the bucket will be closed, not deleted

Recommended implementation detail:
- handle close through a separate button in the sheet footer, not a toggle, because the backend does not support reopen and the action is one-way
- when close is triggered, preserve current bucket name/allocation/tracking metadata in the submitted draft and set `closeRequested = true`

### 5. Preserve and clarify default bucket behavior
For the default/system bucket in Portfolio:
- row is still tappable
- editor allows renaming
- allocation remains disabled/read-only as today
- no close action appears
- descriptive text remains explicit that it absorbs leftover portfolio budget

Validation:
- uniqueness check still applies to renamed default bucket
- amount remains computed remainder, so the submitted draft should continue to carry `0L` or the existing default amount exactly as the current backend expects; choose the value already used by the current bucket settings save path and keep it consistent across edit and close flows

### 6. Keep funds read-only but align them with the new management layout
Refactor `FundsSection` so it uses the same section shell and row/card presentation primitives introduced for buckets.

Behavior:
- funds remain non-editable
- current product behavior stays unchanged: only the default fund is shown
- row should visually read as part of the same management system, but without an edit affordance yet

Groundwork to leave behind:
- section/row APIs should support future `onClick` and editor launch for funds
- naming should be neutral enough to accommodate both buckets and funds

### 7. Navigation and settings behavior
Keep the top-right settings action on the portfolio summary card unchanged. This work adds in-place bucket management; it does not remove the full settings screen.

No navigation changes are required.

## Public API / Interface Changes
Internal UI interfaces will change more than public app-facing APIs.

Expected interface changes:
- `PortfolioOverviewPage(...)` will need bucket click handling, either by:
  - adding `onEditBucket: (String) -> Unit`, or
  - receiving a richer bucket item model with click callbacks
- `ActiveBucketsSection(...)` will become interactive and likely accept:
  - `allBuckets`
  - `bucketSummaries`
  - `onEditBucket`
  instead of only `bucketSummaries`
- `HomeBucketSettingsSheet(...)` will be renamed or generalized, and its callback surface will expand to include close:
  - either `onSaveSettings` plus `onCloseBucket`
  - or one unified `onSubmit(BucketDraft)` with separate sheet actions producing different draft values
- helper builders currently in [`AddBucketSheet.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/AddBucketSheet.kt) will likely move to a more neutral file used by both add/edit flows

No domain-model or Room schema changes are required for this plan.

## Validation And Edge Cases
The implementation should explicitly cover:
- editing a named bucket’s name and allocation from Portfolio
- renaming the default bucket from Portfolio
- preventing manual default bucket allocation edits
- preventing duplicate open-bucket names
- preventing named bucket allocations from exceeding portfolio total
- closing a named bucket from the sheet
- ensuring closed buckets disappear from the active Portfolio list after refresh
- ensuring default bucket cannot be closed
- ensuring closing the currently selected bucket still relies on existing use-case logic to resolve a valid selected open bucket
- ensuring no reopen path is exposed
- ensuring funds remain visible/read-only with no accidental edit affordance

## Tests And Scenarios

### Unit tests
Add or update unit tests for extracted helper logic:
- mapping an existing bucket into editor state
- building an updated bucket draft list from a Portfolio edit
- building a close request draft for a named bucket
- refusing close action for default bucket at the presentation/helper layer if that logic is extracted there

Keep existing `UpdatePortfolioPlanUseCaseTest` coverage for close behavior and add only if a gap appears.

### Compose/UI tests
Add Portfolio-focused tests, likely under `androidTest`:
- bucket row tap opens bucket settings sheet
- editing and saving a bucket from Portfolio triggers `onSavePortfolioPlan` with the updated draft list
- default bucket editor disables allocation input
- named bucket editor shows close action
- default bucket editor does not show close action
- tapping close submits a draft list where the selected bucket has `closeRequested = true`
- funds section still renders with the default fund after refactor

If there is not yet a Portfolio screen test file, create one targeted at these interactions.

### Regression checks
Run:
- `./gradlew testDebugUnitTest`
- `./gradlew connectedDebugAndroidTest` if emulator coverage is feasible
- `./gradlew detekt`

## File Areas Expected To Change
Primary files:
- [`PortfolioScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/PortfolioScreen.kt)
- [`HomeScreen.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/HomeScreen.kt)
- [`AddBucketSheet.kt`](/home/nicola/AndroidStudioProjects/WallyBudget/app/src/main/java/net/loeu/wallybudget/ui/screens/home/AddBucketSheet.kt)

Likely new or split files:
- a neutral bucket-management/editor file under `ui/screens/home`
- a reusable manageable-row/section file under `ui/screens/home`

Possible test files:
- new Portfolio screen instrumentation test
- small unit test for extracted bucket-management helpers

## Assumptions And Defaults
- Portfolio should only show open buckets in its active management list.
- Closing a bucket should be immediate from the sheet action and not require a second confirmation dialog unless implementation friction reveals a UX need.
- Closed buckets remain managed by existing backend rules and are not reopenable from UI.
- The current shared bucket settings sheet is the canonical editor to reuse, even if it is renamed and refactored.
- Funds are intentionally left non-editable in this change; only the UI scaffolding should become fund-ready.
