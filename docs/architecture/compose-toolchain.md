# Compose toolchain matrix

- **Status:** Accepted — P4.3
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P4.3
- **Does not reopen:** compile/target 36; minSdk 26; Core / Lifecycle /
  Activity / coroutines; Room; DataStore; Google Sign-In; Kotlin 2.0.21

This packet upgrades Compose, Material, Navigation, and the Compose
compiler as one matrix. Versions that require AGP 9 or compileSdk 37
are refused. The compiler half is the Kotlin Compose plugin and stays
paired with Kotlin 2.0.21.

## Signed matrix

| Family | From | To | Why it stops here |
|---|---|---|---|
| Compose BOM | 2024.12.01 | **2026.06.01** | Last stable BOM that compiles on API 36 / AGP 8.9.2. Resolves UI, runtime, and foundation **1.11.4** and Material3 **1.4.0**. |
| Navigation Compose | 2.8.5 | **2.9.8** | Latest 2.9 stable. 2.10.0 is still an RC and is left for a later train. |
| Compose compiler | Kotlin plugin 2.0.21 | **Kotlin plugin 2.0.21** | Compiler version is the Kotlin Compose plugin. Bumping it means bumping Kotlin. That is a later train. |

## Decisions

1. **Refuse Compose BOM 2026.08.00 and Compose UI 1.12.**
   `checkDebugAarMetadata` requires compileSdk 37 and AGP ≥ 9.1.0.
2. **Do not bump Kotlin, KSP, Room, DataStore, or play-services-auth.**
   Those stay P4.4–P4.5 and the current Kotlin pin.
3. **Keep string-route `NavHost`.** Navigation 2.9 still serves the
   existing `Route` paths. Type-safe routes are not this packet.
4. **Keep `createComposeRule` (junit4).** The BOM’s v2 rule uses
   `StandardTestDispatcher` instead of `UnconfinedTestDispatcher`.
   Migrating it here would change golden and journey timing. A later
   packet can move tests with explicit waits.
5. **`ComposeToolchain` lives in `toolchain/`.** Same reason as
   `CoreToolchain`: the plain-JVM domain lane stays plugin-free.
6. **Weekday labels read `LocalLocale`.** Compose UI 1.11 lint
   (`NonObservableLocale`) rejected `Locale.getDefault()` inside
   `TrainingCalendarCard`. The calendar now recomposes when the
   platform locale changes.

## Finding coverage

FND-027 continues. Room, Sign-In, and lint-baseline cleanup remain
P4.4–P4.6.
