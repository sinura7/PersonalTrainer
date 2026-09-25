# Baseline: what was run, and the numbers it gave

Part of the [25 September whole-app audit](AUDIT.md) (packet X6). Every
number below was produced in the audit's own container on the day, not
copied from an earlier record. The commands are the repository's own gate,
run the way `docs/HANDOFF-NEXT.md` says to run it, plus two probes.

## Where and on what

| | |
|---|---|
| Commit | `1ad3b11d2756d63ade44ef0e2a363480079979ab`, `trunk` at PR #412 (audit W2b-3) |
| Container | Claude Code cloud session, 4 vCPU, 15 GB RAM; no emulator, no `/dev/kvm`, no phone |
| JDK | OpenJDK 17.0.20.1 (`/usr/lib/jvm/java-17-openjdk-amd64`), the CI pair; the container default is 21 and was not used |
| Android SDK | 36 at `/opt/android-sdk`, build-tools 36.0.0 |
| Gradle | wrapper 8.11.1, cold `~/.gradle` cache filled through Google's mirror of Maven Central (the session hook), `gradle/verification-metadata.xml` enforced with no missing entry |
| Run window | 01:45 → 02:06 UTC, then the render harness from 02:14 |

## The gate, step by step

| # | Command | Result | Wall clock |
|---|---|---|---|
| 1 | `PT_STATIC_ONLY=1 sh tools/preflight.sh` | `preflight: OK (static only)`. The run prints **24 `check-*` steps, 9 `test_*` fixture proofs, `test_summary_gate.sh`, the three summary-line checks and the syntax check** — 34 steps. (`ci.yml`'s comment says "20 checkers, 4 fixture proofs"; `CURRENT_STRUCTURE.md` says 24 and 8; `DEVELOPMENT.md` says 24 and 9. The run says 24 and 9.) | 2m58s |
| 2 | `sh tools/hang-watchdog.sh ./gradlew --continue testDebugUnitTest assembleDebug jacocoTestReport` | BUILD SUCCESSFUL in 8m39s. **477 test classes, 3,149 tests, 0 failures, 0 errors, 0 skipped.** The watchdog never fired. | 9m04s |
| 3 | `./gradlew lintDebug` | BUILD SUCCESSFUL in 3m27s. **0 errors, 0 warnings**; four informational notes (`AutoboxingStateCreation`: prefer `mutableIntStateOf`, all in the debug previews), which the empty `lint-baseline.xml` does not need to carry. | 3m28s |
| 4 | `python3 tools/check-coverage.py` | 0 findings; every floor held (table below). | 1s |
| 5 | `./gradlew assembleDebugAndroidTest` | BUILD SUCCESSFUL in 12s; the device-lane sources compile after X3 and W2a (test APK 1,570,032 bytes). The lane itself cannot run here. | 12s |
| 6 | `sh tools/preflight.sh` (full lane, run as a probe) | Every static step passes, then `== JVM tests (tools/run-domain-tests.sh build/test-jars)` ends in `FAILED: tests did not compile.` → `preflight: FAIL — JVM tests`. Exactly four test files break the plain-JVM lane: `domain/CompletedTrainingParityTest.kt` (245 error lines: imports `androidx.*`, `FakeAppDependencies`, `clearAndJoinForTest`), `domain/DraftStoreTest.kt` (51), `data/backup/ProtectBackupTest.kt` (7: `ProtectBackup`, `OpenBackup` missing from the lane's main list), `domain/UndoQueueTest.kt` (3). Consequence: `tools/verify.sh`, which runs this lane first, cannot pass on `trunk`. | 2m18s |
| 7 | `./gradlew assembleRelease` (unsigned) | **BUILD FAILED in 1m37s** at `:app:minifyReleaseWithR8`: "Missing class org.slf4j.impl.StaticLoggerBinder (referenced from: void org.slf4j.LoggerFactory.bind() and 3 other contexts)". `lintVitalAnalyzeRelease` passed first. R8 wrote `missing_rules.txt` proposing `-dontwarn org.slf4j.impl.StaticLoggerBinder`. No release APK, no `mapping.txt`. Consequence: a `v*` tag would fail `release.yml` at its build step; no signed gym-floor Temper can be cut from `trunk` until a rule lands in `app/proguard-rules.pro`. Temper Debug is not minified and is unaffected. | 1m37s |

## Coverage (JaCoCo instruction coverage vs `tools/coverage-floors.txt`)

| Package | Reading | Floor | Headroom |
|---|---|---|---|
| `data.backup` | 83.0 % | 81 | 2.0 |
| `data.repository` | 86.5 % | 85 | 1.5 |
| `domain` | 93.3 % | 92 | 1.3 |
| `timer` | 81.7 % | 73 | 8.7 |
| `ui.workout` | 88.2 % | 84 | 4.2 |
| `util` | 82.1 % | 76 | 6.1 |
| `workout` | 86.8 % | 85 | 1.8 |

`timer` reads 81.7 % against the 74.7 % X5 recorded four days earlier; the
W2b-2 and W2b-3 tests raised it and the floor was not moved. Packages with
no floor, from the same report: `data.sync` 48.0 %, `reminder` 43.2 %,
`data.auth` 12.0 %, `data.security` 1.1 %, `data.repository.prefs` 64.1 %,
`ui.home` 16.0 %, `ui.plan` 13.6 %, `ui.settings` 17.0 %, `ui.history`
25.5 %, `ui.navigation` 9.0 %, `ui.progress` 9.1 %, `ui.summary` 11.7 %,
`ui.activity` 24.6 %, `ui.library` 20.9 %, `ui.intro`, `ui.permissions` and
`ui.reminders` 0 %. JaCoCo packages are flat: `ui.workout` is one package,
`domain` excludes `domain.coach`, `data.repository` excludes `prefs`. The
ratchet itself runs in no automated lane (see the record).

## Slowest test classes

| Seconds | Class | Tests |
|---|---|---|
| 21.5 | `ui.exercise.ExerciseDetailThisWorkoutRenderTest` | 18 |
| 8.2 | `PendingOccurrenceTest` | 6 |
| 7.1 | `ui.workout.FirstWorkingSetRenderTest` | 15 |
| 6.1 | `ui.components.MoreVertMarkRenderTest` | 1 |
| 5.2 | `ui.workout.ActiveWorkoutViewModelTest` | 96 |
| 4.7 | `ui.workout.FloorRestAndCoachWiringRenderTest` | 24 |
| 4.1 | `ui.workout.DockVoltRenderTest` | 7 |
| 4.0 | `ui.workout.FloorScreenWiringRenderTest` | 20 |
| 3.3 | `timer.RestLockSkipTest` | 4 |
| 3.1 | `ui.saveposture.FirstLaunchRenderTest` | 2 |
| 2.8 | `ui.routines.RoutineEditorViewModelTest` | 61 |
| 2.7 | `ui.workout.WorkoutDockTimerRenderTest` | 23 |
| 2.3 | `ui.workout.WorkoutFloorRenderTest` | 14 |
| 2.1 | `ui.workout.DockCommitRenderTest` | 12 |
| 2.0 | `data.local.BackupV2RoundTripTest` | 12 |

Everything else runs under two seconds. The whole suite is dominated by
compilation and Robolectric start-up, not by any one test.

## Artefacts

- Debug APK `PersonalTrainer-1.0.0-debug.apk`: 20,750,424 bytes, versionCode 104, no `supabase.properties` inside (this container has none), `DebugProbesKt.bin` and `kotlin-tooling-metadata.json` excluded as the build script says.
- Android-test APK: 1,570,032 bytes.
- JaCoCo XML: 7,400,141 bytes.
- Frames written by the suite's own render tests: `app/build/floor-renders/` (14, `WorkoutFloorRenderTest`), `app/build/screen-renders/q1/`, `app/build/screen-renders/w1d/`.
- Release build: none (step 7).

## Repository facts recorded read-only

- The clone here is shallow (50 commits, 0 tags), so `tools/debug-drop-plan.py` and `tools/check-debug-live-code.py` were not run; `git ls-remote --heads origin` shows 37 heads including the eight stale branches HANDOFF-NEXT asks the owner to delete, no `main`, 106 `debug-live-*` tags and 0 `v*` tags.
- `app/lint-baseline.xml` is empty; `gradle/verification-metadata.xml` is 371,825 bytes, 785 components, 1,423 SHA-256 entries, no PGP.
- The dependency catalog matches every signed toolchain record under `docs/architecture/` with no drift.

## Render matrix (the temporary harness)

Filled in below once the harness run completes: the frame manifest (file,
screen, state, size, font, bytes), the screens that never left their loading
state, and the known limit of the method — the `LocalDensity` override sets
the font scale for Compose but does not change `Configuration.fontScale`,
which is the repository's established gate method (W1d) and is accepted as
such.
