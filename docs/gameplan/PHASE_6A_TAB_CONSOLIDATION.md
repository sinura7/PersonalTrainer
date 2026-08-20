# Phase 6a — Tab consolidation + Body absorbs History

1. **Mission**

Collapse the five-tab bar to its final shape and make Body the single training-record surface: silhouette, per-muscle loads, training calendar, coach explanations, the full session log, and PRs on one screen. This is the IA landing that Phases 1a–5 were sequenced to make safe: the LiveSessionBar (1a) already owns resume, the Plan tab (4) already owns the week, and honest heat (5) already owns the windows — so tab demolition now breaks no live behavior it hasn't already been handed. This phase is **size L, honestly**: it rewrites the nav graph and constructs the largest screen in the app.

Execution protocol: `docs/gameplan/PROTOCOL.md` binds. Branch `claude/phase-6a-tab-consolidation` off trunk, one PR, phase closes only on owner sign-off of §8. Phase 6b must not start until this PR merges.

> **Line-number caveat.** Every `file:line` below was verified at commit `2212628` on `claude/app-hierarchy-navigation-cjzigo`, before Phases 1a–5 landed. Re-anchor each citation by its quoted symbol (grep), not the number. A symbol that is already gone means an earlier phase did that piece — verify and move on; never re-do it.

2. **Read first**

1. `docs/gameplan/PROTOCOL.md` — branch/PR/gate rules; not repeated here.
2. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt` — the whole file: Route sealed class (:91-119), tab list (:157-163), `showBottomBar` (:169-171), `goToTab` (:181-189), every composable block (:232-391), `isTabRoute` shim (:504-508). You are rewriting its top half.
3. `app/src/main/java/com/sinura/personaltrainer/ui/progress/ProgressScreen.kt` + `ProgressViewModel.kt` — the Body tab you are extending; note the header column outside the LazyColumn (:67-75) and item keys `map`/`muscles` (:140-184).
4. `app/src/main/java/com/sinura/personaltrainer/ui/history/HistoryScreen.kt` + `HistoryViewModel.kt` + `TrainingCalendarCard.kt` — everything being absorbed; the calendar item (:88-101), `groupedRowShape` (:140-145), the ViewModel's `visibleMonth` + combine (:32-52).
5. `app/src/main/java/com/sinura/personaltrainer/ui/progress/RecommendationCards.kt` — `dispatchRecommendation` (:68-83) is the cross-tab dispatcher you retarget.
6. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` — only the cross-links: `onOpenHistory`/`onOpenRoutines`/`onOpenLibraryMuscle` params (:76-86) and the Recent header action (:355-362). Do NOT rework Home content — that is 6b.
7. `app/src/main/java/com/sinura/personaltrainer/ui/library/ExerciseLibraryScreen.kt` — signature + `initialMuscle` (:69-81); it becomes pushed-only and needs a back affordance.
8. `app/src/main/java/com/sinura/personaltrainer/MainActivity.kt` — deep-link consume-once (:27, :46-61); no change, but the device pass retests it.
9. `app/src/main/java/com/sinura/personaltrainer/domain/TrainingCalendar.kt` (`CalendarDay.sessionIds`, :10-14) and `domain/PersonalRecords.kt` (`bests`, :66-91).
10. `docs/ROADMAP.md` — the Phase-0-recorded IA adjudication (which of PRIMARY/FALLBACK below applies) and the phase table.
11. `docs/DESIGN_AUDIT.md` §6.12 (NAV-01, NAV-03), §6.10 (I-01, I-03), §6.1 (H-05, H-10); `docs/UI_REDESIGN.md` §6 + §8; `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` §8 ("History + calendar + session", "Progress + body map").

3. **Binding doctrine**

- **UI_REDESIGN §6** (docs/UI_REDESIGN.md:139-152): "Progress — History + training calendar + body map + PRs unified; the heat map becomes a module, not a tab." This phase is that sentence.
- **DESIGN_AUDIT NAV-01** (§6.12): five tabs is too many — closed by this phase per the Phase-0 recorded decision. **NAV-03**: the `?muscle=` `restoreState=false` surprise — dies here.
- **DESIGN_AUDIT I-03** (no calendar heat on History — the calendar exists now; the merge keeps it one concept) and **I-01** (History has no PRs — the PR summary row answers it).
- **DIRECTION_B_INSTRUMENT §8**: calendar card cells, grouped session list ("title / date caption / trailing volume numeral/sm"), hand-rolled nav bar with volt tick — all already built; reuse, never restyle.
- **UI_REDESIGN §8 guardrails**: no raw colors/radii; accent budget; every new gap a token.
- **REVISED_STRUCTURE Phase 6a** (the binding brief): scope, the nine-site checklist, shim deletion, Home-stays-start-destination, size L.
- **LIVESESSIONBAR CONTRACT** (settled in Phase 1a): the bar is visible on all tab routes and pushed routes except ActiveWorkout/WorkoutSummary/StartWorkout. Your tab-bar rewrite must not disturb it; while it is visible no other surface may show a live-session affordance.
- **Mechanical proof**: `tools/preflight.sh` green before every push; `check-when-exhaustive` + `check-screen-wiring` cited in the PR wherever enums/callbacks change (docs/DEVELOPMENT.md:55-73).

4. **Settled decisions** (do not reopen)

- **PRIMARY IA: three tabs — Home · Body · Plan**, in that order. Recorded in Phase 0 (read ROADMAP for the record). Body keeps route path `progress`, label "Body", `AccessibilityNew` icons; Plan is Phase 4's tab (formerly Routines), `FitnessCenter` icons. If the Phase-0 record instead says four tabs, execute the FALLBACK section of WI-1 and skip what it lists — do not blend the branches.
- **Home remains the NavHost start destination.** The finish paths `popUpTo(Route.Home.path)` (AppNav.kt:345-347) and `popBackStack(Route.Home.path,…)` (:367) depend on it. Recorded constraint; never change `startDestination`.
- **Route.Library survives as a pushed route only**, pattern `"library?muscle={muscle}"` (AppNav.kt:272-287), param stays the free-text catalog label. The canonical-muscle-key flip is Phase 7's, end-to-end — the `catalogLabel` hop at RecommendationCards.kt:77 stays this phase.
- **`isTabRoute` (AppNav.kt:504-508) is deleted**, along with the `popUpTo/saveState/restoreState=false` Library special cases (:244-252, :259-267). After this phase no tab route carries a query param, so tab matching is plain equality on the destination's registered route pattern.
- **The merged Body screen is ONE LazyColumn.** Never nested scrolling, never a child LazyColumn, session rows as individual `items()` (the HistoryScreen.kt:132-139 rationale binds). Section order (top to bottom): window picker (chrome header, outside the list, as today) → silhouette card → per-muscle rows → month calendar → recommendation explanation cards → month-grouped session list with sticky month headers → PR summary. Note this moves the muscle rows ABOVE recommendations relative to current ProgressScreen (:149-184) — settled.
- **Month grouping is presentation-side.** `HistoryViewModel`'s query (`workoutRepository.observeHistory()`, HistoryViewModel.kt:35) moves into ProgressViewModel unchanged; grouping is a pure function, tested on the JVM.
- **Multi-session days get a sheet.** The first-session-only calendar tap (HistoryScreen.kt:95-97: `day.sessionIds.firstOrNull()?.let(onOpenSession)`) is replaced: one session opens directly, two or more open a day sheet.
- **File plan**: `ProgressScreen.kt`/`ProgressViewModel.kt` stay the Body tab's files (route `progress`, no renames). `TrainingCalendarCard.kt` moves to `ui/progress/` (package line updated). `HistoryScreen.kt` and `HistoryViewModel.kt` are deleted (PRIMARY only). `SessionDetailScreen/ViewModel` stay in `ui/history/`; the `"history/{sessionId}"` route string is unchanged.
- **Recommendation surface map** (settled Phase 0/5): Body = full explanation cards. Home keeps exactly its current slots until 6b. Do not add or remove Home recommendation surfaces here.

5. **Work items**

**WI-1 — The tab bar.**

PRIMARY (three tabs): in AppNav.kt replace the `tabs` list (:157-163) with Home, Body (`Route.Progress`), Plan. Delete `Route.History` from the sealed class (:94) and the History composable block (:313-318). Extend the private `Tab` class with `val matchPattern: String = route.path`; `showBottomBar` (:169-171) and `isSelected` (:207-211) compare `destination.route == tab.matchPattern` — delete `isTabRoute`. Keep `showBottomBar`'s `navBackStackEntry == null` first-frame guard (the :166-171 comment explains it): before the back-stack flow emits, the frame must still count as a tab, or the bar slides up from nothing on every cold start. `goToTab` (:181-189) is unchanged. The LiveSessionBar hosting in the Scaffold is untouched.

FALLBACK (four tabs — only if the Phase-0 record says so): tabs are Home, Body, Plan, History. In that branch DROP from this phase: the Body/History merge (WI-2's screen construction, HistoryScreen/HistoryViewModel deletion), retarget sites 1 and 6 of WI-4 (they keep targeting the History tab). KEEP: month grouping + sticky headers + multi-session-day sheet, applied to `HistoryScreen.kt` in place, and the PR summary row appended to History's list; WI-3 (Library removal) and all remaining WI-4 sites execute identically.

Tests: `check-when-exhaustive` + `check-screen-wiring` green; `grep -rn "Route.History" app/src/main/java` returns zero hits (PRIMARY).

**WI-2 — Body absorbs History** (PRIMARY only; see WI-1 for FALLBACK).

`ProgressViewModel.kt`: today `uiState` is a single `.map` over `container.trainingInsights.observe(...)` (:45-65) — there is no combine yet. Rebuild it as a `combine` of that insights flow with `container.workoutRepository.observeHistory()`, `container.preferencesRepository.schedulePreferences`, and a `visibleMonth = MutableStateFlow(YearMonth.now())`, with the paging functions copied verbatim from HistoryViewModel.kt:60-68 (including the never-future clamp). `ProgressUiState` gains: `sessions: List<WorkoutSession>`, `monthGroups: List<SessionMonthGroup>`, `calendar: TrainingMonth`, `weekStart: DayOfWeek`, `records: List<PrSummaryRow>`. Calendar built via `TrainingCalendarBuilder.build` exactly as HistoryViewModel.kt:42-49 (same weekStart comment applies). Keep `includeWeekPlan = false` (:46).

New domain code (pure, in `domain/`, covered by the JVM suite):
- `SessionMonthGroup(month: YearMonth, sessions: List<WorkoutSession>)` and `fun groupSessionsByMonth(sessions: List<WorkoutSession>, zone: ZoneId): List<SessionMonthGroup>` — newest month first, sessions within a month keep the repository's order.
- `PrSummaryRow(exerciseId: String, exerciseName: String, kind: PersonalRecordKind, valueKg: Double, reps: Int, achievedAt: Long)` and `fun prSummary(sessions: List<WorkoutSession>, limit: Int = 3): List<PrSummaryRow>` — flatten each exercise's working sets to `ExerciseSetRecord`s, run `PersonalRecords.bests` (PersonalRecords.kt:66) per exercise, keep each exercise's e1RM best (fall back to WEIGHT when e1RM is null), sort by `achievedAt` descending, take `limit`, no two rows for one exercise.

`ProgressScreen.kt`: build the list content as `val content: List<BodyItem>` (a private sealed interface: `Map`, `Muscles`, `Calendar`, `RecommendationItem(rec)`, `MonthHeader(group)`, `SessionRow(session)`, `RecordsHeader`, `RecordRow(row)` — plus the existing notice/window-empty items), then emit it in one LazyColumn: `stickyHeader` for `MonthHeader` (month kicker on a `Pit` background strip; `@OptIn(ExperimentalFoundationApi::class)`), `item`/`items` for the rest, session rows keyed by `session.id` with the `groupedRowShape` treatment lifted from HistoryScreen.kt:108-125/140-145. The current Progress LazyColumn spaces every item with `verticalArrangement = Arrangement.spacedBy(Metrics.cardGap)` (:114); that list-level spacing must go — space the sections with per-item padding instead, so the grouped session rows sit contiguous (hairline-separated, gap-free) exactly as they do on History. One item ↔ one `content` entry, so `content.indexOfFirst { it is BodyItem.Calendar }` is a true scroll index — 6b's anchor depends on this; leave a comment saying so. The calendar is `TrainingCalendarCard` (moved file), month paging wired to the ViewModel. New `onOpenSession: (String) -> Unit` and `onOpenExercise: (String) -> Unit` params on ProgressScreen, wired in AppNav's Progress block to `Route.SessionDetail.create` / `Route.ExerciseDetail.create` pushes.

Multi-session-day sheet: day tap handler replaces HistoryScreen.kt:95-97 — `sessionIds.size == 1` → `onOpenSession` directly; `> 1` → set `selectedDayEpoch` (rememberSaveable), render a `ModalBottomSheet` (containerColor `Surface3`, mirroring `MuscleDetailSheet` at ProgressScreen.kt:244-248) listing that day's sessions as `SessionLogRow`s; row tap dismisses and opens SessionDetail.

Empty states: the merged screen keeps Body's existing no-work empty state (ProgressScreen.kt:95-103); when heat has work but `sessions` is empty the session section shows the History empty copy ("No sessions yet" / "Finish a workout and it lands here", HistoryScreen.kt:68-72) with action "Start workout" → the existing `onStartWorkout` (still `Route.StartWorkout` this phase; 6b retargets).

Delete `HistoryScreen.kt`, `HistoryViewModel.kt`.

Tests: `SessionMonthGroupingTest` (ordering, month boundaries across a year rollover, empty input) and `PrSummaryTest` (e1RM-first selection, WEIGHT fallback, one-row-per-exercise, recency order, limit) in `app/src/test/java/com/sinura/personaltrainer/domain/`, runnable by `tools/run-domain-tests.sh`.

**WI-3 — Library stops being a tab.**

Remove Library from the tab list (done in WI-1). The composable block (:272-287) survives as the pushed destination. `ExerciseLibraryScreen` gains `onBack: () -> Unit` and a back-arrow header (copy the `StartWorkoutHeader` grammar, StartWorkoutScreen.kt:143-167), wired to `popBackStack`. Both `Library.create(muscle)` call sites become plain pushes:

```kotlin
onOpenLibraryMuscle = { muscle -> navController.navigate(Route.Library.create(muscle)) }
```

deleting the `popUpTo/saveState/restoreState=false` blocks at AppNav.kt:244-252 and :259-267. Entry points that remain (the complete set): (a) a "Library" text action added to the Plan screen's header row (next to Phase 4's gear), pushing `Route.Library.create(null)`; (b) recommendation cards via `dispatchRecommendation` OPEN_LIBRARY_MUSCLE (RecommendationCards.kt:76-77); (c) the muscle sheet's "Find lifts" (ProgressScreen.kt:197-199, :316-319). `check-screen-wiring` proves the new callbacks are called.

**WI-4 — The nine-site retarget checklist.** Execute every row; the PR description reproduces this table with each row checked.

| # | Site (verified at 2212628) | Current behavior | Required end state |
|---|---|---|---|
| 1 | AppNav.kt:241 `onOpenHistory` ← Recent header action, HomeScreen.kt:355-362 | `goToTab(Route.History.path)` | Delete the `onOpenHistory` param from HomeScreen; `RecentSection`'s header action calls the existing `onOpenProgress` (Body). Label stays "History"-free: use "All sessions". (6b deletes the whole section; this keeps 6a shippable alone.) |
| 2 | AppNav.kt:240 Home `onOpenRoutines` ← `RecommendationAction.OPEN_ROUTINES` (RecommendationCards.kt:79; produced at RecommendationEngine.kt:179) | `goToTab(Route.Routines.path)` | `goToTab` to the Plan tab's route (Phase 4's rename — grep for the actual path constant). Mechanism unchanged; verify only. |
| 3 | AppNav.kt:269 Progress `onOpenRoutines` | same | same as row 2. |
| 4 | AppNav.kt:244-252 Home `onOpenLibraryMuscle` | tab navigation + `restoreState=false` hack | plain push (WI-3). |
| 5 | AppNav.kt:259-267 Progress `onOpenLibrary` | same hack | plain push (WI-3). |
| 6 | AppNav.kt:313-318 History block; empty-state "Start workout" at HistoryScreen.kt:71-72 | History tab hosts the empty-state start | Block deleted; the start action lives in the merged Body session-section empty state (WI-2), still `navigate(Route.StartWorkout.path)` this phase. |
| 7 | Settings: AppNav.kt:303 `onOpenSchedule` → `Route.Schedule`; SettingsScreen.kt:302 schedule row | Phase 4 deleted ScheduleScreen and the Settings schedule row | Verify both are gone (grep `Route.Schedule`, `onOpenSchedule`); if any survivor remains, delete it now. Settings' two doors stay: Home gear (AppNav.kt:254) + Plan header gear (Phase 4). |
| 8 | AppNav.kt:345-347 `popUpTo(Route.Home.path)`; :367 `popBackStack(Route.Home.path)` | assume Home is start destination | No change. Recorded constraint: Home stays `startDestination` (:219). Assert by reading, note in PR. |
| 9 | MainActivity.kt:27, :46-61 notification deep link | tab-independent, consume-once | No code change. MUST be re-proven on device (§8 steps 8-9) — the tab rewrite touches the NavHost the deep link lands in, and the LiveSessionBar is a second always-visible resume path. |

**WI-5 — Mechanical proof + scripted device pass.** Run the §7 gate; fix until green. Then hand the owner §8. The phase does not close on static evidence.

6. **Out of scope** (tempting, forbidden)

- Any Home content change beyond checklist rows 1/4 — masthead, hero, week strip, heat-card/Recent removal are ALL 6b.
- Deleting `Route.StartWorkout` or building the start-options sheet (6b).
- The Library muscle-param canonical-key flip, family grouping, equipment chips, search work (Phase 7); the RecommendationCards.kt:77 `catalogLabel` hop stays.
- Heat semantics, windows, band thresholds (Phase 5, done), imagery (Phase 8).
- LiveSessionBar, finish/discard use cases, editable sessions, repeat-last (Phases 1a/1b, done).
- The A1 DI refactor. Any schema or backup change.
- Restyling existing components (calendar card, session rows, nav bar) beyond what the move forces.

7. **Acceptance gate**

Run, in order, all exit 0 / green:

```bash
tools/preflight.sh                                   # all eight static checks + domain tests
python3 tools/check-screen-wiring.py app/src/main/java   # cite in PR: new Body callbacks wired
python3 tools/check-when-exhaustive.py app/src/main/java # cite in PR: no when survivor after Route.History removal
grep -rn "isTabRoute" app/src/main/java              # expect: no output
grep -rn "restoreState = false" app/src/main/java    # expect: no output
grep -rn "HistoryScreen\\|HistoryViewModel" app/src/main/java  # expect: no output (PRIMARY)
./gradlew testDebugUnitTest assembleDebug            # owner machine / CI (ci.yml triggers on claude/**)
```

Domain tests by name, green in both lanes (`tools/run-domain-tests.sh` and gradle): `SessionMonthGroupingTest`, `PrSummaryTest`, plus the whole pre-existing suite unchanged. CI green on the PR.

8. **Owner device checklist** (phase closes only when every step is reported)

1. Install the branch build over your current install (no data reset). App opens on Home; the bottom bar shows exactly Home · Body · Plan (or · History in the fallback), volt tick on Home.
2. Tap Body. Top to bottom: window picker, silhouette, muscle rows, this month's calendar, any coach cards, your sessions grouped by month — month label stays pinned while its sessions scroll — then a Records section. Scrolling is one continuous list, no inner scroll traps.
3. Tap a calendar day with one session → that session opens. Back returns to Body where you left it.
4. If any day has two sessions (log two short ones if needed): tapping it opens a sheet listing both; each opens correctly.
5. Page the calendar back several months and forward; it refuses to pass the current month. Rotate the phone; the shown month survives.
6. Tap a Records row → that exercise's detail opens.
7. From a coach card that says "Find … lifts", and from a muscle sheet's "Find lifts": Library opens filtered, with a back arrow that returns to Body. From Plan's header, open Library unfiltered.
8. Start a workout, start a rest timer, background the app, tap the rest notification → the workout screen opens once, back does NOT re-open it. LiveSessionBar shows on Home, Body, Plan, and pushed Library.
9. With the session still live: enable Developer options → "Don't keep activities", switch away and back. The app restores to the right tab and the bar is still there. Turn the setting off.
10. Finish the workout → summary → Done lands on Home. Nothing anywhere except the bar ever offered Resume.

9. **Estimates**

Executor: 4-5 days (L — nav rewrite ~1, Body merge ~2-2.5, retargets + proofs ~1, fallback branch adds ~0.5 if taken). Owner: 1-1.5 days (device pass + PR review; the review is mostly the AppNav diff and the merged screen).

10. **Hand-back** (completion report to the owner)

- PR link, branch name, CI run link; preflight output pasted.
- The WI-4 table reproduced with every row's final file:line and a checkmark.
- Which IA branch executed (primary/fallback) and, for fallback, the exact items dropped.
- Screenshots: the new tab bar, merged Body top and session list with a pinned month header, the multi-session day sheet, Library with back arrow.
- Named test list with results; `check-screen-wiring`/`check-when-exhaustive` outputs.
- Doc deltas included in the PR: ROADMAP phase entry marked done; note closing DESIGN_AUDIT NAV-01/NAV-03/I-03 and updating H-05/H-10 status.
- §8 checklist echoed with the owner's observed results; any deviation listed as an open defect with a file:line hypothesis.
- Explicit statement: Route.StartWorkout and all Home content are untouched, awaiting 6b.
