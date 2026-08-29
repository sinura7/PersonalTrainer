# Development

How to work on this app day to day. Cursor on the web writes the packet.
**Obtainium** is the phone. Android Studio is not the install path.

The current program is [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md). Signed
decisions are [architecture/](architecture/README.md). This file is the
operational runbook: layout, tests, Windows notes, things that bite you.

## First run

Cursor clones the repo and runs the JVM gate. The owner never has to open
Android Studio. Debug and release are **separate apps**. Debug is
`com.sinura.personaltrainer.debug`. Release stays `com.sinura.personaltrainer`.
Different signing keys, different databases, different icons.

### Obtainium checkpoint

After `trunk` is green, Temper Debug arrives as a GitHub **pre-release**
tagged `debug-live-YYYY-MM-DD` with `PersonalTrainer-*-debug.apk`. Obtainium
on the phone: this repo, include pre-releases, prefer that debug APK. Gym-floor
**Temper** stays on the signed APK entry. Do not mix them. See
[SETUP.md](../SETUP.md) §6.

Walk this once, in order:

1. Home — last session, days since, Start, Library, Goals.
2. Start today's plan (or Start a workout), log a set, rest, finish.
3. Body — This week / Last 30 days. A muscle last trained more than
   30 days ago should still show recency, not "Not trained yet."
   Recommendations sit above the map.
4. Plan — pin, replay, or suggest a week. Two timed items can share a day.
5. History — Day / Week / Month / Year / All chips, readout, then a session row. No Start on this tab.
6. Settings — export a file. Do not restore over a phone that holds real history.
   Share diagnostics is optional and redacted.

Physical TalkBack is still required before Public Candidate. Do not
expect a fifth tab or cloud sync.

**A new debug-live drop is a new Temper Debug install.** It will not open,
overwrite, or even see the release history. The new icon is labelled
**Temper Debug**. Release stays **Temper**. You will have two icons. If an
*old* debug is still on the phone (debug-signed, but living on the release
id), uninstall that one — not the release app. Android already refused to
let release and that old debug coexist, so if you already have release on
the phone you only gain the new debug icon beside it. Do not uninstall
release to "make room." There is nothing to make room for.

## Project layout

```
app/src/main/java/com/sinura/personaltrainer/
  data/local        Room entities, DAOs, TemperDatabase
  data/repository   repositories — the only things that touch DAOs
  data/backup       backup document, JSON codec, validator, Drive client
  domain            pure Kotlin: models, units, heat, recommendations, planner, progression
  timer             rest timer: store, controller, foreground service, alarm, notifications
  workout           in-progress workout draft (memory + saved state)
  ui/<screen>       one package per screen: Screen.kt + ViewModel.kt
app/src/test/       JVM unit tests
app/schemas/        Room schema JSON, one per DB version — committed on purpose
docs/               audit, roadmap, recovery runbook, design spec
```

Two rules keep this navigable:

- **`domain/` stays pure Kotlin.** No `android.*` imports. That is what makes it testable
  without a device, and most of the test suite lives there.
- **ViewModels talk to repositories, never to DAOs.** Dependencies are constructor-injected
  `AppDependencies`. `AppContainer` is the production graph, resolved by the default
  `viewModel()` factory; tests pass a fake graph instead. No Hilt.

## Running tests

In the terminal (Cursor):

```bash
./gradlew testDebugUnitTest          # the whole JVM suite
./gradlew testDebugUnitTest --tests '*ProgressionBasisTest*'
```

These are plain JVM tests — no emulator, a few seconds. Run them
before every commit, here on Cursor. Obtainium is the live install
path. Do not wait on a GitHub Actions run.

Run everything mechanical with one command — the static checks plus the JVM test
lanes, which is what every foundation-program packet gates on:

```bash
tools/preflight.sh
```

The single local verification command for a foundation-program packet is:

```bash
tools/verify.sh
```

That runs preflight, `testDebugUnitTest`, `lintDebug` (new issues fail; existing
ones live in `app/lint-baseline.xml`), `jacocoTestReport`, and
`tools/check-coverage.py`. If an emulator is already running it also runs
`connectedDebugAndroidTest` against `com.sinura.personaltrainer.debug`.

`InstrumentationSmokeTest` asserts `BuildConfig.APPLICATION_ID` and that the
debug lane uses the `.debug` suffix. Do not hardcode the release package.

The individual checks in `tools/` cover the gap when you cannot build — they are a
pre-flight, not a substitute for `./gradlew assembleDebug`:

```bash
python3 tools/check-named-args.py app/src/main/java        # named args vs. declarations
python3 tools/check-when-exhaustive.py app/src/main/java   # sealed/enum when coverage
python3 tools/check-unused-imports.py app/src/main/java    # dead imports
python3 tools/check-internal-imports.py app/src/main/java  # in-project names actually exist
python3 tools/check-missing-imports.py                     # names used but never imported
python3 tools/check-design-tokens.py app/src/main/java     # no raw colours/radii/elevation
python3 tools/check-screen-wiring.py app/src/main/java     # every callback is actually called
python3 tools/check-state-members.py app/src/main/java     # state.foo exists on that UiState
python3 tools/check-annotation-targets.py                  # annotations still on a declaration
python3 tools/check-required-args.py                       # every required parameter supplied
python3 tools/check-import-hygiene.py                      # no duplicate imports; `by` delegates importable
python3 tools/check-doc-authority.py                       # current-voice docs, FND map, relative links
tools/syntax-check.sh app/src/main/java                    # parse-level diagnostics only
```

Each targets an error class that survives a parse-only check and still breaks the build or
the product: a call site passing a parameter the function no longer has, a `when` that lost
its exhaustiveness, a name that was never declared, a name that was used but never imported,
a colour that escaped the token layer, a screen that quietly stopped calling one of its
callbacks, and a screen reading a state field its view model never exposed. See
[tools/README.md](../tools/README.md).

Every one of these was written from a real miss, because a check nobody has watched fail is a
check nobody knows works. `check-missing-imports.py` came from `Surface1` shipping un-imported
in `ExerciseDetailScreen`, and before it `LaunchedEffect` in `HomeScreen` and `ScheduleScreen`.
It takes both source roots at once, so it needs no argument.

`check-required-args.py` grew a second half while the block frame was being built.
`OnboardingApplier.apply` gained two required parameters and its test called it positionally
with the old three; the tool watched that go past in silence, because until then it only judged
calls where *every* argument was named. An all-positional call with no trailing lambda is
decidable too — arguments fill parameters left to right, so a count is enough. The first draft
of that reported 245 findings and every one was wrong: `data class Foo(` matches the call
pattern and its parameters are not named arguments, and `: AppViewModel(application) {` is a
supertype call followed by a class body rather than a call with a block. Both are excluded now,
and so is over-supply, which needs the split to be exactly right to mean anything. It still
cannot see `applier.apply(...)`: a lowercase call needs its receiver's type resolved, so only
Gradle finds a method that grew a parameter.

`check-import-hygiene.py` came from build breaks a
compile audit found in code eleven phases deep that no compiler had ever seen.
`WorkoutRepository.kt` carried the same import line five times, which reads as harmless
copy-paste and is a hard K2 failure — `conflicting import: imported name is ambiguous`, once per
occurrence. And `HomeScreen.kt` had `var startOptionsOpen by rememberSaveable { … }` with
`getValue` imported but not `setValue`: the delegate desugars to a `setValue` call, the property
is assigned in two places, and the build fails. That second one is structurally invisible to
`check-missing-imports.py`, because the token `setValue` never appears in the source at all —
the compiler synthesizes it. A check driven by the names actually written can never see a name
that is never written.

`check-state-members.py` came from Phase 6b: Home's new week strip read
`state.loggedEpochDays` and `HomeUiState` had no such property. Member access was the one error
class nothing here could see — `check-missing-imports.py` skips dotted names on purpose, since
they are resolved by a receiver rather than an import. It is checkable at all only because the
project is consistent about two things: a screen's collected state is always called `state`,
and a view model always declares `val uiState: StateFlow<SomethingUiState>`. The same phase
also taught `check-missing-imports.py` to flag a SCREAMING_SNAKE constant that resolves
nowhere, after lifting two composables into new files left their `private val` dimensions
behind in the old ones — three constants that would each have failed the build.

On a machine with no Android SDK, `tools/run-domain-tests.sh` runs the host-runnable tests on
a plain JVM, which is possible only because `domain/` is pure Kotlin. Point it at a directory
holding the Kotlin compiler, stdlib, coroutines, JUnit and hamcrest jars:

```bash
PT_JARS=build/test-jars tools/run-domain-tests.sh
```

It runs two lanes. **domain** always: `domain/`, `util/`, `logging/`, plus a hand-picked list
of files elsewhere that carry no Android imports — `WorkoutDraftCache`, `WorkoutDraftRecovery`,
`RestTimerStore`, `RestTimerStatePersistence` — against `test/…/{domain,util,workout,timer}/`.
**backup** whenever a Gson jar is present as well: `BackupDocument`/`BackupJson`/
`BackupValidator` against `test/…/data/backup/`. Both lanes name files individually rather than
passing directories, because every one of those packages also holds files that *do* need
Android (`RestTimerService`, `StartTrainingDay`, the Drive clients). Without Gson the backup
lane says so and is skipped; it is never skipped silently.

Adding a file to `EXTRA_MAIN` is what makes its test directory runnable, and the two lists in
the script move together. `RestTimerStatePersistence` needs three stubs — `SystemClock`,
`Context`, `SharedPreferences` — which, like the `android.util.Log` stub the lane was built on,
throw if anything ever actually calls them. A stub that returned a plausible value instead would
let the code under test start depending on Android behaviour with nothing noticing.

That expansion took the executed count from 384 to 467. None of the newly reached tests failed,
which is the good outcome and not the expected one — see the backup lane below.

The backup lane earned its keep the hour it existed. Those four test files had been written
against backup v1 and never once executed — `./gradlew test` has never run in this
environment — and fourteen of them were red: twelve fixtures that predate the `equipment` and
`loadType` fields the validator now requires, and two assertions describing a codec that
normalises less than it does. It also surfaced a real defect. `BackupJson.normalized()` reached
a Gson-injected null through `copy()`, whose generated parameter check threw, and everything
thrown inside `decode()` is reported as one generic "this file is damaged" — so a backup with
one missing exercise name lost the validator's specific *"an exercise is missing its name or
id"*. The file was refused either way; the owner was just no longer told which field was wrong.

If you add a file to `data/backup/` that has no Android imports, add it to the lane.

## Continuous integration

`.github/workflows/ci.yml` exists and already lists `trunk`. We do
**not** use GitHub-hosted runners to test. The owner will not add
billing, make the repo public, or attach a self-hosted runner for
this. A red X on a commit is noise. Do not open it. Do not ask the
owner about it. Do not file a packet to "fix CI."

The test path is Cursor (`./gradlew testDebugUnitTest assembleDebug`)
and Obtainium on the phone. That is the whole lane.

## Instrumented tests

Two lanes exist for anything Room touches:

- **JVM lane (primary)**: Robolectric tests under `app/src/test` (e.g.
  `SchemaV1BaselineTest`) run inside `./gradlew testDebugUnitTest` — no device.
  Robolectric bundles its own SQLite, which is not the phone's; green here is
  necessary, never sufficient. **This lane requires a macOS or Linux host**: on
  Windows Robolectric falls back to legacy SQLite (3.7.10), whose `PRAGMA table_info`
  cannot express composite primary keys, so Room schema validation fails falsely for
  entities with compound primary keys. On Windows, use the device lane only — **this
  project's owner is on Windows, so that is the standing case here; see § On Windows.**
- **Device lane (truth)**: `app/src/androidTest`, run with

  ```bash
  ./gradlew connectedDebugAndroidTest
  ```

  against a **running API 26+ emulator** (Device Manager → start one first).
  Expected: `BUILD SUCCESSFUL` and a green report at
  `app/build/reports/androidTests/connected/`. This lane now installs on
  `com.sinura.personaltrainer.debug`, so it can sit beside release. **Still
  never point it at the gym-floor phone** — use the emulator. Do not run it
  against the release `applicationId`.

Migration tests must pass in both lanes before a schema change ships.

`WorkoutRepository` and `ScheduleRepository` have instrumented tests on real SQLite
(`app/src/androidTest/.../data/repository/`). ViewModels and Compose screens remain
unverified by automation — see [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md) Phase 1.

## On Windows

The phone lane is Obtainium. This section is only for a Windows host that
still runs Gradle. It is not how Temper reaches the device.

The owner's machine is Windows/PowerShell, so the commands in this file need translating —
and one lane is not available at all.

**Gradle.** From the repository root (not your home directory):

```powershell
cd path\to\PersonalTrainer
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
```

`./gradlew` is the POSIX script and will not run in PowerShell; `.\gradlew.bat` is the
Windows wrapper. Both are committed.

**The `tools/` scripts** (`preflight.sh`, `run-domain-tests.sh`, `syntax-check.sh`) are
`#!/bin/sh` and cannot run in PowerShell. Use Git Bash, which ships with Git for Windows:

```powershell
& "C:\Program Files\Git\bin\bash.exe" tools/preflight.sh
```

or open Git Bash in the repo folder and run `tools/preflight.sh` directly. They also need
`python3` on PATH. These scripts are the *executor's* pre-push gate; the owner's gate is the
Gradle commands above plus the on-device checklist, so a missing Git Bash never blocks a
phase — it only means preflight is run by the executor rather than re-run locally.

**The JVM/Robolectric migration lane does not work here, and that is not a bug.**
Robolectric defaults to NATIVE SQLite everywhere except Windows, where it falls back to
LEGACY (SQLite 3.7.10). LEGACY's `PRAGMA table_info` cannot express composite primary keys,
so Room schema validation fails *falsely* for entities that have one — and Phase 3 adds
exactly such an entity (`exercise_muscles`, `PRIMARY KEY(exerciseId, muscleKey)`). A red
`SchemaV1BaselineTest` on this machine therefore proves nothing about the schema.

Consequences, in order of cost:

1. **The emulator lane is the migration lane** (`.\gradlew.bat connectedDebugAndroidTest`).
   It runs real Android SQLite, it already works on Windows, and it is the truth check
   regardless of host. Phase 3's migration suite is gated on it.
2. **Optional: WSL2** restores the JVM lane — clone into the Linux filesystem and run
   `./gradlew testDebugUnitTest` there. Worth it only if the fast lane is missed.
3. **Do not wait on GitHub Actions.** We do not use hosted runners.
   Cursor on Linux already has the JVM lane.

Nothing in the plan depends on the JVM lane existing on this machine; the packets name the
emulator lane as the truth check precisely so this substitution is legal.

## Things that will bite you

**Database schema changes.** The foundation database is `TemperDatabase`
(`temper.db`), version 4, frozen. Changing any `@Entity` means: bump
`FoundationGeneration.VERSION`, write a `TemperMigrations` migration, and
commit `app/schemas/com.sinura.personaltrainer.data.local.TemperDatabase/<n>.json`.
Never add `fallbackToDestructiveMigration` — it silently erases the
training history this app exists to accumulate.

> **Legacy `2.json` is still committed.**
> `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/2.json`
> is the Room-generated v2 baseline (`identityHash` `3eedd530…`) for the
> leftover file. Do not hand-edit it. A later Temper version still
> requires a real `./gradlew :app:assembleDebug` so Room can write
> `<n>.json` — inventing an identityHash is a crash loop on a phone with
> no destructive fallback. See `docs/MIGRATION_REHEARSAL.md`.

**The rest timer cannot be tested with the screen on.** Its whole job is firing while the
phone sleeps. Verify with the screen off and the phone untouched; force Doze with
`adb shell dumpsys deviceidle force-idle` to make it deterministic.

**Backups are destructive on restore.** Restore replaces everything. The validator refuses
malformed and empty documents, and a pre-restore snapshot is written to app-private storage
first, but test restores on the emulator, not on the phone holding your real history.

**Release builds are not what you run day to day.** `assembleRelease` needs
`keystore.properties`; without it the APK is unsigned and cannot update your install. See
[SETUP.md](../SETUP.md).

## Useful adb

```bash
adb shell dumpsys deviceidle force-idle          # force Doze, to test the rest timer
adb shell dumpsys deviceidle unforce             # back to normal
adb shell am kill com.sinura.personaltrainer     # simulate process death (keeps saved state)
adb shell am force-stop com.sinura.personaltrainer  # harsher: clears saved state too
adb logcat --pid=$(adb shell pidof com.sinura.personaltrainer)
```

`am kill` is the honest test for "phone sat in my pocket and the OS reclaimed the app".
`force-stop` is a user-initiated kill and legitimately discards saved state.

## Owner loop

This is how the project actually moves. Cursor on the web writes the packet.
Obtainium on the phone is the check. They are not the same evening.

- **`trunk` is shipping.** There is no `main`. After a merge, cut a
  `debug-live-*` pre-release with `PersonalTrainer-*-debug.apk`. The owner
  taps Obtainium. Do not tell them to open Android Studio.
- **A packet may sit.** Green JVM (`./gradlew testDebugUnitTest` and
  `assembleDebug`) is enough to open the PR and start the next packet.
  The phone is what merges it, not what unblocks the next branch.
- **No two open PRs edit the same Kotlin file.** If the next packet needs a
  file an open PR already owns, it is stacked on that branch. Independent
  packets cut from current `trunk`. A stack merges at the tip only.
- **Never run `connectedDebugAndroidTest` on the release `applicationId`.**
  The connected suite targets `.debug`, which is the point. Phone judging
  happens on Temper Debug from Obtainium, not gym-floor Temper.

Agents load the same rules from `.cursor/rules/owner-loop.mdc`. The current
program those rules point at is [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

## Committing

One packet, one `cursor/<slug>-b87f` branch, one PR into `trunk`. Write
commit messages that explain **why**, not what — the diff already says what.
The existing history is the model to follow.
