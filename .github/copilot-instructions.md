# Copilot Instructions for WallyBudget

## Big picture architecture
- App is a single-module Android app (`app`) using Kotlin + Jetpack Compose + Room + DataStore.
- Main data flow is: Compose Screen -> `BudgetViewModel` -> `BudgetRepository` -> Room DAOs / DataStore.
- `BudgetViewModel` exposes `StateFlow` UI models via `stateIn(WhileSubscribed(5000))`; keep this pattern for new streams.
- Budget/cycle business logic belongs in domain services (see `BudgetCalculationService`), not UI.
- Time/rollover scheduling is abstracted behind `CurrentDateProvider` (`SystemCurrentDateProvider`), not hardcoded in repository/UI.

## Key files to understand first
- Entry and navigation: [app/src/main/java/net/loeu/wallybudget/MainActivity.kt](../app/src/main/java/net/loeu/wallybudget/MainActivity.kt), [app/src/main/java/net/loeu/wallybudget/ui/navigation/Screen.kt](../app/src/main/java/net/loeu/wallybudget/ui/navigation/Screen.kt)
- ViewModel/factory: [app/src/main/java/net/loeu/wallybudget/ui/viewmodel/BudgetViewModel.kt](../app/src/main/java/net/loeu/wallybudget/ui/viewmodel/BudgetViewModel.kt), [app/src/main/java/net/loeu/wallybudget/ui/viewmodel/BudgetViewModelFactory.kt](../app/src/main/java/net/loeu/wallybudget/ui/viewmodel/BudgetViewModelFactory.kt)
- Repository/data layer: [app/src/main/java/net/loeu/wallybudget/data/repository/BudgetRepository.kt](../app/src/main/java/net/loeu/wallybudget/data/repository/BudgetRepository.kt)
- Time provider: [app/src/main/java/net/loeu/wallybudget/data/time/SystemCurrentDateProvider.kt](../app/src/main/java/net/loeu/wallybudget/data/time/SystemCurrentDateProvider.kt)
- Room schema/migrations: [app/src/main/java/net/loeu/wallybudget/data/local/BudgetDatabase.kt](../app/src/main/java/net/loeu/wallybudget/data/local/BudgetDatabase.kt), [app/src/main/java/net/loeu/wallybudget/data/local/ExpenseDao.kt](../app/src/main/java/net/loeu/wallybudget/data/local/ExpenseDao.kt)
- Core cycle math: [app/src/main/java/net/loeu/wallybudget/domain/service/BudgetCalculationService.kt](../app/src/main/java/net/loeu/wallybudget/domain/service/BudgetCalculationService.kt)
- Home/expenses UI interaction: [app/src/main/java/net/loeu/wallybudget/ui/screens/HomeScreen.kt](../app/src/main/java/net/loeu/wallybudget/ui/screens/HomeScreen.kt), [app/src/main/java/net/loeu/wallybudget/ui/screens/ExpensesPage.kt](../app/src/main/java/net/loeu/wallybudget/ui/screens/ExpensesPage.kt), [app/src/main/java/net/loeu/wallybudget/ui/screens/OverviewPage.kt](../app/src/main/java/net/loeu/wallybudget/ui/screens/OverviewPage.kt)

## Project-specific conventions
- Monetary values use integer cents (`Long`): `amountCents`, `monthlyBudgetCents`, etc. Do not introduce floating-point currency storage.
- Cycle queries are timestamp range-based and optimized with DB indexes/migrations; prefer DAO SQL over in-memory filtering.
- Current-cycle rollover must archive history but preserve raw expenses.
- Prefer nullable-safe early-return guards for reset flow readability in `BudgetRepository.checkAndPerformMonthlyReset`.
- Keep date/time conversion explicit with `ZoneId.systemDefault()` and cycle bounds `[start, end)`.
- In Compose, avoid Flow operator creation directly in composition unless wrapped with `remember(...)`.

## Build variants and date polling behavior
- BuildConfig flag `USE_ACTIVE_DATE_POLLING` controls date-refresh cadence.
- `debug`: active polling enabled.
- `noDebugPolling`: debug-like variant with polling disabled (midnight-only behavior).
- `release`: polling disabled.
- Variant setup is in [app/build.gradle.kts](../app/build.gradle.kts).

## Critical workflows
- Fast compile check: `./gradlew :app:compileDebugKotlin`
- Lint check: `./gradlew :app:lintDebug`
- Validate noDebugPolling variant: `./gradlew :app:compileNoDebugPollingKotlin`
- Install noDebugPolling on device/emulator: `./gradlew :app:installNoDebugPolling`

## When editing
- If changing Room entities/queries, update `BudgetDatabase` version + migrations and factory migration list.
- If changing cycle logic, verify effects in both `OverviewPage` and `ExpensesPage` (day rollover + selected date behavior).
- Keep user-facing budget metrics consistent: base daily, adjustment, adjusted daily, cycle left, and cumulative totals.
- Favor minimal, surgical changes; preserve existing UI structure and Material3 token usage unless explicitly redesigning.