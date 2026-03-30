# Time Access

Application code must not call raw wall-clock APIs directly.

Use one of these instead:

- `WallyTime` for non-injected call sites and shared conversions.
- `CurrentDateProvider` when code needs an injectable source of "today".
- `CurrentEpochTimeProvider` when code needs an injectable source of epoch milliseconds.

## Why

Direct calls such as `LocalDate.now()`, `Instant.now()`, `System.currentTimeMillis()`, and
`ZoneId.systemDefault()` have repeatedly caused bugs and review churn. They are easy to add,
hard to audit later, and awkward to replace consistently.

`WallyTime` creates one obvious project-wide entry point for wall-clock access, while the injected
providers keep business logic testable.

## Rules

- Prefer injected providers in domain and view-model code.
- Use `WallyTime` in code that cannot reasonably receive an injected provider.
- Use `WallyTime.startOfDayEpochTimeMs(...)` and related helpers instead of repeating timezone
  conversion snippets inline.
- Do not introduce new direct calls to raw wall-clock APIs in `app/src/main/java`.

## Enforcement

The `:app:verifyAppTimeUsage` task scans production Kotlin sources and fails the build if it finds
direct uses of:

- `LocalDate.now(...)`
- `LocalDateTime.now(...)`
- `ZonedDateTime.now(...)`
- `OffsetDateTime.now(...)`
- `Instant.now(...)`
- `System.currentTimeMillis()`
- `ZoneId.systemDefault()`

`detekt` depends on this verification task, so the rule runs locally and in CI.
