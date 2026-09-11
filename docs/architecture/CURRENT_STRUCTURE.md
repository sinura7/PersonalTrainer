# Current structure

What the code is, as of 11 September 2026. Read this before changing anything
structural.

This is a description, not a decision. The decisions are the ADRs beside it,
and where this file and an ADR disagree the ADR wins and this file is wrong —
say so. It exists because there was no such description: the closest thing was
[`docs/foundation-audit/architecture.md`](../foundation-audit/architecture.md),
written before the Phase 5 cutover, and it still described Room v2, nine
entities, four tabs and no cardio.

## Shape

One Gradle module, `:app`. No sub-modules — see the note at the end on why not
yet.

| Source set | Files | Lines | Tests |
|---|---|---|---|
| `app/src/main` | 416 | 70,506 | — |
| `app/src/test` | 282 | — | 2,027 |
| `app/src/androidTest` | 23 | — | 88 |
| `app/src/debug` | 12 | — | Compose previews and the golden-capture substrate |
| `app/src/sharedTest` | 4 | — | `FakeClock`, `SequentialIds`, `ControllableElapsedRealtime`, `TestWaits`, compiled into both test sets |

## The layers

Everything is under `com.sinura.personaltrainer`.

| Package | Files | Lines | What it is |
|---|---|---|---|
| `domain` | 142 | 16,528 | Models, rules, calculators, policies, ports, and ~27 `*Copy.kt` text objects |
| `ui` | 126 | 35,220 | 18 screens, 22 ViewModels, `ui/components`, `ui/theme`, `ui/navigation` |
| `data` | 92 | 13,242 | `local/{dao,entity,relation}`, `mapper`, `repository`, `repository/prefs`, `backup` |
| `timer` | 18 | 2,535 | Rest foreground service, alarm scheduler, notifications, persistence |
| `reminder` | 9 | 480 | WorkManager scheduling, receivers, worker |
| `workout` | 9 | 634 | Use cases: start, finish, discard, `CompleteTraining` façade, draft cache and recovery |
| `diagnostics` | 4 | 332 | Redacted diagnostic bundle, crash store, event ring |
| `util` | 6 | 297 | `JvmTime`, `IdFactory`, quantity formatting, coroutine error helpers |
| `insights` | 2 | 345 | `TrainingInsightsPublisher` and the one source behind it |
| `activity` | 1 | 64 | Activity use cases (confirm, start live, discard, finish) |
| `logging` | 1 | 74 | `AppLog`, the swappable sink |

### `domain` depends on nothing

Four imports across 142 files: `kotlin.math.abs`, `max`, `round`, and
`kotlinx.coroutines.CancellationException`. No app package, no `java.time`, no
Android. `tools/check-domain-seams.py` holds that at zero and rejects an import
of any internal package other than `domain` itself.

This is recent. Until 11 September 2026 fifteen domain files carried
`time: TimePort = JvmTime` defaults, and `util/JvmTimePort.kt` calls
`android.os.SystemClock` while `util` imports `TimePort`, `CivilDate` and
`IdPort` straight back — two packages neither of which could compile without
the other, and a ratchet that could not see it because the Android hop was one
import further out than anything it banned.

### The rest of the direction rules, all currently held

- `data` never imports `ui`.
- `ui` never imports a Room `@Entity` or `@Dao`. Every ViewModel goes through
  a repository.
- No package cycles anywhere in `ui`. The three that existed —
  `ui/components`, `ui/history` and `ui/workout` each depending on
  `ui/navigation` which depends on all three back — were all caused by
  `LiveBarCopy` and `LiveBarKind`, which now live in `domain`.

## How it is wired

```mermaid
flowchart TB
    PTA["PersonalTrainerApp<br/>(manifest android:name)"]
    AC["AppContainer(context) : AppDependencies<br/>36 typed ports"]
    DB[("TemperDatabase v4<br/>21 entities · 10 DAOs")]
    PREFS[("user_settings DataStore<br/>46 keys · 6 prefs stores")]
    REPOS["18 repositories<br/>+ BackupService"]
    UC["workout/ + activity/ use cases"]
    VMS["22 ViewModels<br/>AppViewModel : AndroidViewModel"]
    NAV["AppNav.kt<br/>sealed Route · 5 tabs"]
    SCR["18 Compose screens"]
    TIM["timer/<br/>RestTimerStore + FGS + alarm"]
    REM["reminder/<br/>WorkManager"]

    PTA -->|onCreate| AC
    AC --> DB
    AC --> PREFS
    DB --> REPOS
    PREFS --> REPOS
    REPOS --> UC
    AC --> UC
    AC --> TIM
    AC --> REM
    AC -->|"container: AppDependencies = application.appContainer()"| VMS
    VMS -->|"uiState: StateFlow"| SCR
    VMS -->|"nav StateFlow + LaunchedEffect ack"| NAV
    NAV --> SCR
```

**Dependency injection is a hand-rolled composition root.** No Hilt, no
Dagger, no Koin. `PersonalTrainerApp.onCreate` builds one `AppContainer`, which
implements `AppDependencies` — an interface of 36 typed ports. Every ViewModel
is `@JvmOverloads constructor(application, container: AppDependencies =
application.appContainer())`, so production gets the real graph through the
default and tests pass `FakeAppDependencies`, which is the same repositories
over an in-memory Room database. There is no mocking library and there should
not be one; `AppViewModelSeamTest` locks the constructor shape.

`timer/` and `reminder/` reach the graph by casting
`(application as PersonalTrainerApp).container` rather than through
`AppDependencies`. That is the one place the seam is bypassed, and it is
service-locator shaped.

**Navigation** is a sealed `Route` hierarchy in
[`ui/navigation/AppNav.kt`](../../app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt)
with typed `create()`/`parse()` factories over string paths. ViewModels never
touch `NavController`: they emit a navigation `StateFlow` and the screen
bridges it with a `LaunchedEffect` that acknowledges what it handled.

**Persistence** is `TemperDatabase` version 4 (`temper.db`), 21 entities, 10
DAOs, hand-written migrations 1→2→3→4, schemas exported to `app/schemas/`.
`fallbackToDestructiveMigration` appears nowhere and is banned in comments in
both database classes. Legacy `TrainerDatabase` v2 (`personal_trainer.db`)
survives only as migration-test substrate.

**Preferences** are one DataStore named `user_settings` holding 46 keys. The
keys are package-level in `data/repository/prefs/`, and six areas —
`DisplayPrefs`, `CoachingPrefs`, `PlanningPrefs`, `RestPrefs`, `ReminderPrefs`,
`BackupPrefs` — sit behind interfaces that `PreferencesRepository` mixes in by
delegation. One store on disk, six in code: the keys share a file, so
splitting them across stores would be a data migration.

**The rest timer** has one source of truth: `RestTimerStore`'s in-memory
`StateFlow`, owned by the container. SharedPreferences holds a row for process
death, the foreground service draws the notification, and AlarmManager wakes
the completion. `RestTimerCompletion.completeOnce` claims by timer id so
rehydration, the alarm and the service cannot each fire the same finish.

## Verification

`./gradlew testDebugUnitTest` runs the unit tests **and** the whole
static gate: every `Test` task depends on `:app:staticChecks`, which runs
`tools/preflight.sh` with `PT_STATIC_ONLY=1`. That is 26 checkers plus their
fixture proofs — domain seams, design-token ceilings, unbounded waits,
swallowed cancellation, supply-chain ledger, version floor. Until 11 September
2026 none of it was wired into Gradle, so the push gate could go green on a
branch that broke all of it.

The counts the ratchets hold live in `tools/checker-baselines.toml`. Growth
past a number fails closed; a count that comes in under it prints a note asking
you to tighten the file.

Lanes, and which gate each one is, are in
[`docs/DEVELOPMENT.md`](../DEVELOPMENT.md). The short version: JVM is the merge
gate, the emulator and the phone gate the signed release.

## Known debt, named

Not a to-do list — a list of things a reader will notice and should not have to
rediscover.

- **`ActiveWorkoutViewModel` is 1,344 lines** with 17 `MutableStateFlow`
  fields, and still holds rule decisions that belong in `domain` — prefill,
  lift selection, the log-set sequence.
- **`RoutineEditorViewModel` is 1,208 lines**, mostly the staged-targets
  commit and refusal logic.
- **`WorkoutRepository` is 1,075 lines** and covers two things: the session
  aggregate and the progression / personal-record analytics over it.
- **`PlannerRepository` fuses schedule rules with reminder delivery.** They are
  separate subjects sharing a class.
- **No repository interfaces.** `AppDependencies` is the only abstraction
  between a ViewModel and a concrete repository, and 13 ViewModels import
  repository outcome types (`SaveExerciseResult`, `StartSessionOutcome`,
  `WorkoutRepository.DeletedSet`) directly. `data/repository/prefs` is the
  first place in the data layer with interfaces.
- **Six soft foreign keys.** `schedule_rules.routineId` and `.templateId`,
  `measurable_goals.exerciseId`, `activity_blocks.exerciseId`,
  `activity_sessions.templateId` and `.occurrenceId`,
  `schedule_occurrences.completedActivityId` have no FK constraint, so orphan
  references are possible.
- **`strings.xml` holds two strings and no UI text.** User-facing copy is
  `domain/*Copy.kt` objects and inline literals; `stringResource` is used
  nowhere in `ui`. There is no localisation path today, and that is a decision
  nobody has written down.
- **One of eleven required goldens is committed.**
  `domain/GoldenPageCatalog.missingPageGoldens` names the rest. Page-level
  visual regression is defined and unenforced.
- **`PlanDayViewModel` has no tests**, and `ui/components` sits at roughly
  0.15 test lines per production line against `domain`'s 1.16.
- **Test scaffolding ships in `main`**: `GoldenPageCatalog`,
  `InformationArchitecture`, `AccessibilityMatrix`, `CatalogReviewRenderer`,
  `PlanReviewRenderer`, `DiagnosticRedaction.DiagnosticCanaries`. Each has one
  reference in `main` — its own declaration — and the rest in tests.

## Why not multiple modules yet

The obvious split is `:domain` (pure Kotlin), `:data`, `:ui`, `:timer`,
`:reminder`, and it would buy compile-time enforcement of the direction rules
above rather than a Python checker enforcing them.

It was impossible until this week: `domain` and `util` each needed the other to
compile, so neither could be a module. That is fixed, and `domain` is now
genuinely standalone. What remains is cost, not blockage — roughly 274 test
files plus the `sharedTest` wiring would move, `AppContainer` would need
splitting, and `app/schemas/` belongs with `:data`. At 69k lines the
incremental-build win is modest, so the case rests on enforcement.

If that case gets made, do `:domain` first and alone. It is the only module
with no inbound dependency to unpick.
