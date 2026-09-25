# Audit X6 render harness (temporary; not to be committed under `app/`)

`AuditRenderTest.kt` draws every user-facing screen with the real composables and the real
ViewModels over `FakeAppDependencies` (an in-memory Room graph) at the ADR-032 matrix and
writes one PNG per frame to

    app/build/screen-renders/audit-2026-09-25/<screen>/<state>-<w>x<h>-font<s>.png

(`build/...` relative to the `app` module directory, exactly as `FirstLaunchRenderTest`
writes `build/screen-renders/q1/`). Sizes are `w360dp-h640dp-mdpi`, `w412dp-h915dp-mdpi`,
`w800dp-h360dp-land-mdpi`, `w600dp-h960dp-mdpi` (per-method `@Config(qualifiers)`), font
scales 1.0 / 1.6 / 2.0 via `LocalDensity provides Density(density, fontScale)` looped inside
each method. Every frame waits up to 20 s for its ViewModel to leave its loading state; a
frame that never does is still written (so the picture shows what happened) and the test
then fails naming it.

## Running it

Copy the file to
`app/src/test/java/com/sinura/personaltrainer/ui/AuditRenderTest.kt` (package
`com.sinura.personaltrainer.ui`), then:

    ./gradlew :app:testDebugUnitTest --tests 'com.sinura.personaltrainer.ui.AuditRenderTest' \
                                     --tests 'com.sinura.personaltrainer.ui.AuditFloorRenderTest' \
                                     --tests 'com.sinura.personaltrainer.ui.AuditShellRenderTest'

Three classes live in the one file because the templates they copy differ in one setting each:

| Class | Precedent | Why separate |
| --- | --- | --- |
| `AuditRenderTest` | `ExerciseDetailThisWorkoutRenderTest` | real main looper, no `Dispatchers.setMain`; ViewModel scopes cancelled on the looper |
| `AuditFloorRenderTest` | `WorkoutFloorRenderTest`, `RestPageSkipTest` | `Dispatchers.setMain(UnconfinedTestDispatcher())` + `clearAndJoinForTest`, as the floor's VM tests need |
| `AuditShellRenderTest` | `RestLockSkipTest` | `@Config(application = PersonalTrainerApp::class)` because `AppNav.kt:293` casts the application |

Test names are `<screen>_<w>x<h>()`. Each screen's states come from a `<screen>States()`
function; the shared loop is `renderAuditMatrix` (top-level, `internal`).

## What renders

| Screen dir | Entry composable / ViewModel | States | Seed (copied from) |
| --- | --- | --- | --- |
| `home` | `HomeScreen` / `HomeViewModel` | empty, populated | setup complete + finished leg-extension session + two timed rules on today and the week generated (`HomeViewModelTest.agendaListsIndependentOccurrences`) |
| `progress` | `ProgressScreen` / `ProgressViewModel` | empty, populated | insights flow with a `BodyHeatSnapshot` holding one trained muscle (CHEST) |
| `plan` | `PlanScreen` / `PlanViewModel` | empty, populated | two routines pinned Mon/Wed/Fri + timed rules (`PlanViewModelTest.aFullyPinnedWeekYieldsNoProposals`) |
| `planDay` | `PlanDayScreen(epochDay = today)` / `PlanDayViewModel` | empty, populated | same planner seed |
| `history` | `HistoryScreen` / `HistoryViewModel` | empty, populated, **error** | finished session + backdated activity; error = `FailingObserveCompletedSummariesDao` |
| `sessionDetail` | `SessionDetailScreen` / `SessionDetailViewModel` | empty (= missing id), populated, **error** | finished session; error = `FailingObserveSessionDao`, gate flipped after seeding |
| `library` | `ExerciseLibraryScreen` / `ExerciseLibraryViewModel` | empty, populated | four lifts + one routine |
| `exerciseDetail` | `ExerciseDetailScreen` / `ExerciseDetailViewModel` | empty (= missing lift), populated, **error** | finished + in-progress leg extension (the template's seed); error = `observeInProgressSession` throwing (the only Details decorator precedent; the screen loads without its session card) |
| `routineEditor` | `RoutineEditorScreen` / `RoutineEditorViewModel` | empty (`routineId = "new"`), populated (`lower-b`) | `seedTestWorkout` routine |
| `customWeek` | `CustomWeekScreen` / `CustomWeekViewModel` | empty, populated | four lifts in the catalog |
| `settings` | `SettingsScreen` / `SettingsViewModel` | home, backup, account, reminders | sub-page via `requestSettingsSubpage`, re-requested before every frame because the screen consumes it |
| `onboarding` | `OnboardingScreen` / `OnboardingViewModel` | empty (first step) | — |
| `intro` | `ColdStartIntro(reduceMotion = false)` | empty | stateless |
| `chooser` | `SavePostureChooser` | empty | stateless; **412×915 only** (q1 already has 360/412/800) |
| `activityComposer` | `ActivityComposerScreen` / `ActivityComposerViewModel` | empty (strength), cardio | `mode` saved-state key |
| `liveCardio` | `LiveCardioScreen` / `LiveCardioViewModel` | empty (= missing id), populated | `startLiveActivity("Easy run", …)` (`LiveCardioViewModelTest`) |
| `activityDetail` | `ActivityDetailScreen` / `ActivityDetailViewModel` | empty (= missing id), populated, **error** | `confirmActivity` backdated 200 lb × 5; error = `FailingGetGraphDao`, gate flipped after seeding |
| `workoutSummary` | `WorkoutSummaryScreen` / `WorkoutSummaryViewModel` | empty (= missing id), populated | finished session with one set |
| `activeWorkout` | `ActiveWorkoutScreen` / `ActiveWorkoutViewModel` | populated | **600×960 only** (the floor lane has the other three); `openLegExtension` + `floorSets(2)` from `FloorTestKit` |
| `restTimer` | `RestTimerScreen` / `RestTimerViewModel` | empty (idle page), running (90 s rest) | `RestPageSkipTest.openThePageOnARunningRest` |
| `shell` | `PersonalTrainerNav()` | first-run, home | real `AppContainer`; `home` = `setOnboardingComplete(true)` on the same container |

"empty" for a detail screen is what a fresh database can show: the screen's own
missing-row answer. Error states exist only where `FakeAppDependencies` offers a DAO
decorator (`activityDaoDecorator`, `workoutDaoDecorator`).

## Expected frame count

- `AuditRenderTest`: 38 states × 4 sizes × 3 font scales = 456, plus the chooser 3 → **459**
- `AuditFloorRenderTest`: floor 1 × 1 × 3 = 3, rest 2 × 4 × 3 = 24 → **27**
- `AuditShellRenderTest`: 2 × 4 × 3 → **24**

Total **510** PNGs, in 21 screen directories.

## Skipped or thin, and why

- **Save-posture chooser** at 360/412(other fonts)/800: already in `q1/`; one frame set here.
- **Active workout** at 360/412/800: already in `build/floor-renders/` from `WorkoutFloorRenderTest`.
- **Exercise detail "error"** is degraded, not failed: the only decorator precedent for Details
  makes the in-progress read fail, and the screen is designed to load anyway. No decorator
  reaches its history read.
- **Sheets and dialogs in their own window** (Home's get-started sheet, the start sheet, keypads)
  are not in a `decorView.draw` capture; the frame shows the screen beneath. This is the same
  limit every existing render test has.
- **Shell risk**: the production `PreferencesRepository` DataStore is a `preferencesDataStore`
  delegate (a process singleton). The four shell methods each boot `PersonalTrainerApp` in a
  fresh Robolectric temp dir; from the second method on, the singleton still points at the
  first method's file. DataStore recreates parent directories on write, so this is expected
  to work, but if the second-to-fourth shell frames show the settings-unavailable retry state,
  that is the cause and the shell should be run one method at a time (`--tests '…AuditShellRenderTest.shell_412x915'`).
- **ColdStartIntro** composes with `reduceMotion = false`; its `delay(AUTO_ADVANCE_MS)` is
  driven by the test clock and `onFinished` is a no-op, so the frame is the intro as first drawn.
- **RestTimer** and the **floor** share `FloorTestKit`'s `internal` helpers; if the lead copies
  the file to a different module than `app`'s test source set they will not resolve.

## Nothing here modifies the repository

The file is written to the scratchpad only; the lead copies it in, runs it, and deletes it.
It does not `./gradlew` anything itself.
