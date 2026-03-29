# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app in [`app/`](./app). Kotlin source lives under `app/src/main/java/net/loeu/wallybudget`, organized by layer: `data/` for Room, DataStore, and snapshot I/O, `domain/` for models, policies, services, and use cases, and `ui/` for Compose screens, components, theming, and view models. Resources are in `app/src/main/res`. JVM unit tests live in `app/src/test/java`; emulator-only instrumentation and UI tests live in `app/src/androidTest/java`. Static analysis config is in `config/detekt/`.

## Build, Test, and Development Commands
Run all commands from the repository root.

- `./gradlew assembleDebug`: build the debug APK.
- `./gradlew testDebugUnitTest`: run local JVM tests.
- `./gradlew connectedDebugAndroidTest`: run instrumentation and Compose UI tests on a connected emulator.
- `./gradlew seedDebugEmulator`: clear app data, install debug builds, and seed one running emulator before device tests.
- `./gradlew detekt`: run Kotlin static analysis using `config/detekt/detekt.yml`.
- `./gradlew lintDebug`: run Android lint for the debug variant.

Connected test tasks are guarded to fail if a physical device is attached; use an emulator only.

## Coding Style & Naming Conventions
Use Kotlin with 4-space indentation, Java/Kotlin 17 targets, and package paths that mirror directories. Follow existing naming patterns: `PascalCase` for classes, screens, and use cases (`ObserveForecastUseCase`), `camelCase` for functions and properties, and descriptive file names that match the main declaration. Keep lines at 120 characters or less. Detekt enforces rules such as no wildcard imports, newline at EOF, and restrained return counts.

## Testing Guidelines
Name tests with the subject under test plus `Test`, for example `BudgetCalculationServiceTest.kt`. Keep fast logic tests in `src/test` and database/UI/device behavior in `src/androidTest`. Add or update tests with every behavior change, especially around `domain/usecase`, Room migrations, and Compose screens. When finishing code changes, run `./gradlew detekt` in addition to the relevant test task unless there is a clear reason you cannot.
For any change to bucket allocation math, transfers, baselines, or default-bucket repair logic, add or update a unit test that proves portfolio totals are conserved and funds do not "appear" or disappear across internal bucket movements.

## Commit & Pull Request Guidelines
Use short, imperative commit subjects. Keep subjects concise, action-first, and reference issues or PRs when useful.
PR summaries must be Markdown-formatted and should describe the user-visible change, list verification steps run, and include screenshots or recordings for UI changes when relevant.

### Pull Request Review
When there's review findings in pull requests, first check if the comments are valid and reasonable. Then try to create a minimal change that resolves the issue outlined in the comments. Where it makes sense, batch changes for multiple comments together in one commit. Then reply to the initial comment with your resolution and close the conversation.

## Security & Configuration Tips
Do not commit signing secrets. Release signing is read from `WALLYBUDGET_RELEASE_STORE_FILE`, `WALLYBUDGET_RELEASE_STORE_PASSWORD`, `WALLYBUDGET_RELEASE_KEY_ALIAS`, and `WALLYBUDGET_RELEASE_KEY_PASSWORD`, or equivalent Gradle properties. Keep SDK paths in local machine config such as `local.properties`.
