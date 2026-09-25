# Baseline summary (Phase A), audit X6

- Container: Claude Code cloud, 4 vCPU, JDK 17.0.20.1 (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`), Android SDK 36 at /opt/android-sdk, Gradle 8.11.1 wrapper, cold `~/.gradle` cache filled through Google's mirror of Maven Central (session hook), `verification-metadata.xml` enforced with no missing entries.
- HEAD: `1ad3b11d2756d63ade44ef0e2a363480079979ab` (`trunk`, PR #412), 25 September 2026.
- Run window: 01:45:41 → 02:01:24 UTC.

| Step | Command | Result | Wall clock |
|---|---|---|---|
| 1 | `PT_STATIC_ONLY=1 sh tools/preflight.sh` | `preflight: OK (static only)` | 2m58s |
| 2 | `sh tools/hang-watchdog.sh ./gradlew --continue testDebugUnitTest assembleDebug jacocoTestReport` | BUILD SUCCESSFUL in 8m39s; 477 classes, 3,149 tests, 0 failures, 0 errors, 0 skipped; watchdog never fired | 9m04s |
| 3 | `./gradlew lintDebug` | BUILD SUCCESSFUL in 3m27s; 0 errors, 0 warnings | 3m28s |
| 4 | `python3 tools/check-coverage.py` | 0 findings (table below) | 1s |
| 5 | `./gradlew assembleDebugAndroidTest` | BUILD SUCCESSFUL in 12s; test APK 1,570,032 bytes | 12s |

Coverage (JaCoCo instruction, `tools/coverage-floors.txt`):

| Package | Reading | Floor |
|---|---|---|
| data.backup | 83.0 % | 81 |
| data.repository | 86.5 % | 85 |
| domain | 93.3 % | 92 |
| timer | 81.7 % | 73 |
| ui.workout | 88.2 % | 84 |
| util | 82.1 % | 76 |
| workout | 86.8 % | 85 |

Slowest test classes (s): ExerciseDetailThisWorkoutRenderTest 21.5 · PendingOccurrenceTest 8.2 · FirstWorkingSetRenderTest 7.1 · MoreVertMarkRenderTest 6.1 · ActiveWorkoutViewModelTest 5.2 (96 tests) · FloorRestAndCoachWiringRenderTest 4.7 · DockVoltRenderTest 4.1 · FloorScreenWiringRenderTest 4.0 · RestLockSkipTest 3.3 · FirstLaunchRenderTest 3.1 · RoutineEditorViewModelTest 2.8 (61) · WorkoutDockTimerRenderTest 2.7 · WorkoutFloorRenderTest 2.3 · DockCommitRenderTest 2.1 · BackupV2RoundTripTest 2.0.

Artefacts: `app/build/outputs/apk/debug/PersonalTrainer-1.0.0-debug.apk` 20,750,424 bytes; JaCoCo XML 7,400,141 bytes; render frames written by the run: `app/build/floor-renders/` (14 frames, WorkoutFloorRenderTest), `app/build/screen-renders/q1/`, `app/build/screen-renders/w1d/`.

(Probes 7–8 — full preflight, unsigned assembleRelease — appended below when done.)

## Probes (after Phase A)

| Step | Command | Result | Wall clock |
|---|---|---|---|
| 7 | `sh tools/preflight.sh` (full, expected to fail) | every static step OK; `== JVM tests (tools/run-domain-tests.sh build/test-jars)` → `FAILED: tests did not compile.` → `preflight: FAIL — JVM tests`. Four test files fail to compile in the plain-JVM lane: `domain/CompletedTrainingParityTest.kt` (245 error lines; imports `androidx.*`, `FakeAppDependencies`, `clearAndJoinForTest`), `domain/DraftStoreTest.kt` (51), `data/backup/ProtectBackupTest.kt` (7; unresolved `ProtectBackup`, `OpenBackup`), `domain/UndoQueueTest.kt` (3). Consequence: `tools/verify.sh` cannot pass on `trunk`. | 2m18s |
| 8 | `./gradlew assembleRelease` (unsigned) | **BUILD FAILED in 1m37s** at `:app:minifyReleaseWithR8`: "Missing class org.slf4j.impl.StaticLoggerBinder (referenced from: void org.slf4j.LoggerFactory.bind() and 3 other contexts)". `lintVitalAnalyzeRelease` ran first and passed. R8 wrote `app/build/outputs/mapping/release/missing_rules.txt`; no release APK and no `mapping.txt` were produced. Consequence: `release.yml` (tag `v*`) would fail at `assembleRelease`; no signed gym-floor build can be cut from `trunk` until a `-dontwarn`/keep rule lands in `app/proguard-rules.pro`. | 1m37s |
