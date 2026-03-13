# Wally Budget

Wally Budget is an Android budgeting app built around payday-based monthly cycles instead of calendar months. It helps track spending inside each pay cycle, adjust daily budget guidance as the cycle progresses, and keep local history of completed cycles.

## What the App Does

- Guides first-run onboarding for monthly budget and payday setup
- Shows daily budget guidance with rollover behavior across the active cycle
- Supports adding, editing, deleting, and restoring expenses
- Presents a current-cycle overview alongside today's spending
- Includes an analysis screen with verdicts, evidence, and next steps
- Stores completed cycles in a history view
- Handles pending cycle closeout when a new cycle starts
- Exports and restores local snapshots for backup and recovery
- Uses adaptive Compose layouts and navigation for different screen sizes

## How Budget Cycles Work

Wally Budget treats each budget as a monthly cycle anchored to the user's payday day-of-month. Daily allowance changes based on how much of the cycle budget remains and how many days are left in the current cycle. Spending less than the daily allowance leaves more room for later days in the same cycle, while completed cycles are archived into history.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3 with adaptive navigation components
- AndroidX Navigation Compose
- Room
- DataStore
- Gson
- Gradle Kotlin DSL
- Detekt
- JUnit and AndroidX instrumented/UI tests

## Requirements

- Android Studio with the Android SDK installed
- JDK 17
- Android SDK platform tools available through `local.properties`, `ANDROID_SDK_ROOT`, or `ANDROID_HOME`
- An Android emulator for connected tests and emulator seeding

Android targets:

- `minSdk 26`
- `compileSdk 36`
- `targetSdk 36`

## Getting Started

1. Open the project in Android Studio or work from the included Gradle wrapper.
2. Make sure the Android SDK is configured on the machine.
3. Build the debug app.
4. Run it on an emulator or connected device.

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

You can also run and debug the app directly from Android Studio.

## Useful Gradle Commands

- `./gradlew :app:assembleDebug` builds the debug APK.
- `./gradlew :app:installDebug` installs the debug build on a connected device or emulator.
- `./gradlew :app:testDebugUnitTest` runs JVM unit tests.
- `./gradlew :app:connectedDebugAndroidTest` runs instrumented tests on a connected emulator.
- `./gradlew :app:detekt` runs static analysis.
- `./gradlew :app:lintDebug` runs Android lint for the debug variant.
- `./gradlew :app:seedDebugEmulator` clears app data and seeds a single running emulator with test data.
- `./gradlew :app:assembleRelease` builds the release APK.
- `./gradlew :app:bundleRelease` builds the release app bundle.

## Testing Notes

- Unit tests live in `app/src/test`.
- Instrumentation and UI tests live in `app/src/androidTest`.
- Connected Android tests are intended for emulators.
- The Gradle build logic refuses to run connected tests when physical devices are attached.
- `:app:seedDebugEmulator` requires exactly one running emulator and clears app data before seeding it.

## Project Structure

- `app/src/main/java/net/loeu/wallybudget/ui` contains Compose screens, UI components, navigation, theming, and view models.
- `app/src/main/java/net/loeu/wallybudget/domain` contains models, policies, services, configuration, and use cases.
- `app/src/main/java/net/loeu/wallybudget/data` contains Room, DataStore, snapshot, and time integrations.
- `app/src/test` contains JVM unit tests.
- `app/src/androidTest` contains instrumentation and Compose UI tests.
- `config/detekt` contains Detekt configuration.

## Data and Backup Behavior

App data is stored locally on the device using Room and DataStore. The app supports snapshot export and import for backup and restore, with default filenames in the form `wallybudget-snapshot-YYYYMMDD.json.gz`. The current codebase is local-only; the manifest does not declare internet access and the repository does not contain cloud sync or backend integrations.

## Release Build Notes

Release signing is optional and becomes active when these Gradle properties or environment variables are provided:

- `WALLYBUDGET_RELEASE_STORE_FILE`
- `WALLYBUDGET_RELEASE_STORE_PASSWORD`
- `WALLYBUDGET_RELEASE_KEY_ALIAS`
- `WALLYBUDGET_RELEASE_KEY_PASSWORD`

Optional ABI splits can be enabled with `wallybudget.enableAbiSplits=true`.

## Status

This README documents the repository as it exists today. Screenshots, store links, and release distribution instructions are intentionally omitted because they are not present in this repo.
