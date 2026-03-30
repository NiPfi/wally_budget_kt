# Repository Guidelines

## Project Structure
`WallyBudget` is a single-module Android app in [`app/`](./app).

Main code lives in `app/src/main/java/net/loeu/wallybudget`:
- `data/local`: Room database, DAO, entities, query models, and DataStore preferences
- `data/snapshot`: snapshot import/export models and IO
- `data/time`: time-related adapters
- `domain`: config, models, policies, services, and use cases
- `ui`: Jetpack Compose screens, navigation, components, theme, and view models
- `util`: shared utilities

Tests:
- `app/src/test/java`: JVM tests for domain logic, view models, and non-Android helpers
- `app/src/androidTest/java`: emulator-only instrumentation tests for Room and seeding flows

Static analysis config lives in `config/detekt/`.

## Build, Test, And Analysis
Run commands from the repository root.

- `./gradlew assembleDebug`: build the debug APK
- `./gradlew testDebugUnitTest`: run local JVM tests
- `./gradlew connectedDebugAndroidTest`: run emulator-only instrumentation tests
- `./gradlew seedDebugEmulator`: clear app data, install debug builds, and seed exactly one running emulator
- `./gradlew detekt`: run static analysis
- `./gradlew lintDebug`: run Android lint

## Commit & Pull Request Guidelines
Recent history uses short, imperative commit subjects such as `Fix onboarding snapshot restore overwrite (#48)` and `Address PR #47 review feedback`. Keep subjects concise, action-first, and reference issues or PRs when useful. PRs should describe the user-visible change, list verification steps run, and include screenshots or recordings for UI changes.
Connected Android test tasks fail when a physical device is attached. Use an emulator only.

## Change Guidelines
Keep persistence, preferences, and snapshot IO in `data`, business rules and calculations in `domain`, and Compose UI state in `ui`.
Prefer minimal changes that fit the existing package structure and patterns already used nearby.
When changing bucket allocation math, transfers, baselines, or default-bucket repair logic, preserve portfolio-total conservation across internal movements.

## Testing Expectations
Add or update tests with every behavior change.
Use `src/test` for domain/use case/service logic and other non-Android behavior. Use `src/androidTest` for Room and device-only behavior.
For allocation math, transfers, baselines, or default-bucket repair changes, include a unit test that proves funds do not appear or disappear.
Before finishing, run `./gradlew detekt` and the most relevant test task(s), and note anything you could not run.

## Commits And PRs
Use short, imperative commit subjects.
PR summaries must be Markdown-formatted.
PR summaries should describe the user-visible change, list verification steps run, and include screenshots or recordings for UI changes when relevant.

## Pull Request Review
Check whether review comments are valid before changing code.
Prefer the smallest change that resolves the issue.
Batch related review fixes when that keeps the diff coherent.
Reply to the review comment with the resolution, then close the conversation.

## Security And Configuration
Do not commit signing secrets.
Release signing is read from `WALLYBUDGET_RELEASE_STORE_FILE`, `WALLYBUDGET_RELEASE_STORE_PASSWORD`, `WALLYBUDGET_RELEASE_KEY_ALIAS`, and `WALLYBUDGET_RELEASE_KEY_PASSWORD`, or equivalent Gradle properties.
