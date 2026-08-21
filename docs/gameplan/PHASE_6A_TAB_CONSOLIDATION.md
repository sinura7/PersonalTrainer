# Phase 6a — Tab consolidation + Body absorbs History

1. **Mission**

Collapse the five-tab bar to its final shape and land the history work the IA has been waiting on: sessions grouped by month with sticky headers, a sheet when a day holds more than one session, and a personal-records row. **Which screen hosts that work is the one branch decision in this phase** (§4): under **four tabs** it lands in `HistoryScreen`/`HistoryViewModel` in place and Body is left alone; under **three tabs** Body absorbs History and becomes the single training-record surface — silhouette, per-muscle loads, training calendar, coach explanations, the full session log, and PRs on one screen. This is the IA landing that Phases 1a–5 were sequenced to make safe: the LiveSessionBar (1a) already owns resume, the Plan tab (4) already owns the week, and honest heat (5) already owns the windows — so tab demolition now breaks no live behavior it hasn't already been handed. The two branches are **not** the same size: four tabs is a nav rewrite plus in-place list work; three tabs additionally constructs the largest screen in the app. §9 gives both numbers.

Execution protocol: `docs/gameplan/PROTOCOL.md` binds. Branch `claude/phase-6a-tab-consolidation` off trunk, one PR, phase closes only on owner sign-off of §8. Phase 6b must not start until this PR merges. **Execution order (D-A): phase numbers are identifiers, not sequence.** The plan runs 0 → 2 → 1 → 3 → 4 → 5 → **6a** → 6b → 7 → 8, so this packet executes seventh, with Phases 0, 2, 1, 3, 4 and 5 already merged into trunk when you start.

> **Line-number caveat.** Every `file:line` below was verified at commit `2212628` on `claude/app-hierarchy-navigation-cjzigo`, and every one of them was *exact* at that commit; the only diff since has been docs-only, so drift will come from Phases 0–5 merging and from nothing else. Re-anchor each citation by its quoted symbol (grep), not the number. A symbol that is already gone means an earlier phase did that piece — verify and move on; never re-do it.
>
> **Mandatory phase-start re-baseline (D-G, PROTOCOL §6).** Your FIRST commit on this branch is a re-baseline report — docs/PR-body only, before any work item. It states the current trunk tip, which phases merged since `2212628`, the measured domain-test count and test-class count, and every packet literal below that has drifted, each with its verified current value. A mismatch fully explained by a merged prior phase (or by the game plan's own commits) is EXPECTED: record it, adopt the new value, continue. Stop and ask the owner only for a mismatch nothing in the merge history accounts for.

2. **Read first**

1. `docs/gameplan/PROTOCOL.md` — branch/PR/gate rules; not repeated here.
2. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt` — the whole file: Route sealed class (:91-119), tab list (:157-163), `showBottomBar` (:169-171), `goToTab` (:181-189), every composable block (:232-391), `isTabRoute` shim (:504-508). You are rewriting its top half.
3. `app/src/main/java/com/sinura/personaltrainer/ui/progress/ProgressScreen.kt` + `ProgressViewModel.kt` — the Body tab; note the header column outside the LazyColumn (:67-75) and item keys `map`/`muscles` (:140-184). Under **Branch B (three tabs)** you are extending these two files into the merged screen; under **Branch A (four tabs)** they are read-only in this phase — Body is not restructured at all.
4. `app/src/main/java/com/sinura/personaltrainer/ui/history/HistoryScreen.kt` + `HistoryViewModel.kt` + `TrainingCalendarCard.kt` — the calendar item (:88-101), `groupedRowShape` (:140-145), the ViewModel's `visibleMonth` + combine (:32-52). Under **Branch A** these are the files you edit in place (and `TrainingCalendarCard.kt` does not move); under **Branch B** this is everything being absorbed into Body and then deleted.
5. `app/src/main/java/com/sinura/personaltrainer/ui/progress/RecommendationCards.kt` — `dispatchRecommendation` (:68-83) is the cross-tab dispatcher you retarget.
6. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` — only the cross-links: `onOpenHistory`/`onOpenRoutines`/`onOpenLibraryMuscle` params (:76-86) and the Recent header action (:355-362). Do NOT rework Home content — that is 6b.
7. `app/src/main/java/com/sinura/personaltrainer/ui/library/ExerciseLibraryScreen.kt` — signature + `initialMuscle` (:69-81); it becomes pushed-only and needs a back affordance.
8. `app/src/main/java/com/sinura/personaltrainer/MainActivity.kt` — deep-link consume-once (:27, :46-61); no change, but the device pass retests it.
9. `app/src/main/java/com/sinura/personaltrainer/domain/TrainingCalendar.kt` (`CalendarDay.sessionIds`, :10-14) and `domain/PersonalRecords.kt` (`bests`, :66-91).
10. `docs/ROADMAP.md` — the Phase-0-recorded IA adjudication (§ Decisions **D1**), which selects your branch, and the phase table. Read it before anything else in §4:

   ```bash
   grep -A5 "### D1" docs/ROADMAP.md        # the D1 record
   grep -n "Chosen option" docs/ROADMAP.md  # the signed selection: Option A = four tabs, Option B = three tabs
   grep -c "Signed:" docs/ROADMAP.md        # 0 means Phase 0 has not merged — stop (PROTOCOL §4)
   ```

11. `docs/DESIGN_AUDIT.md` §6.12 (NAV-01, NAV-03), §6.10 (I-01, I-03), §6.1 (H-05, H-10); `docs/UI_REDESIGN.md` §6 + §8; `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` §8 ("History + calendar + session", "Progress + body map").

3. **Binding doctrine**

- **UI_REDESIGN §6** (docs/UI_REDESIGN.md:139-152): "Progress — History + training calendar + body map + PRs unified; the heat map becomes a module, not a tab." Under **Branch B** this phase is that sentence. Under **Branch A** the D1 record *adjudicates* that sentence rather than executing it: the unification half is deliberately not built, the heat map still stops being a tab, and the PR/calendar/grouping wins land on History instead. D1 outranks §6 — it is the later, signed decision.
- **DESIGN_AUDIT NAV-01** (§6.12): five tabs is too many — closed by this phase per the Phase-0 recorded decision, in **either** branch (Library and Routines both stop being tabs regardless). **NAV-03**: the `?muscle=` `restoreState=false` surprise — dies here, in either branch.
- **DESIGN_AUDIT I-03** (no calendar heat on History — the calendar exists now; the work keeps it one concept) and **I-01** (History has no PRs — the PR summary row answers it). Both close in either branch; only the hosting screen differs.
- **DIRECTION_B_INSTRUMENT §8**: calendar card cells, grouped session list ("title / date caption / trailing volume numeral/sm"), hand-rolled nav bar with volt tick — all already built; reuse, never restyle.
- **UI_REDESIGN §8 guardrails**: no raw colors/radii; accent budget; every new gap a token.
- **REVISED_STRUCTURE Phase 6a** (the binding brief): scope, the nine-site checklist, shim deletion, Home-stays-start-destination. Its "size L" applies to Branch B; Branch A is smaller and §9 sizes it separately.
- **LIVESESSIONBAR CONTRACT** (settled in Phase 1a): the bar is visible on all tab routes and pushed routes except ActiveWorkout/WorkoutSummary/StartWorkout. Your tab-bar rewrite must not disturb it; while it is visible no other surface may show a live-session affordance.
- **Mechanical proof**: `tools/preflight.sh` green before every push; `check-when-exhaustive` + `check-screen-wiring` cited in the PR wherever enums/callbacks change (docs/DEVELOPMENT.md:55-73).

4. **Settled decisions** (do not reopen)

- **BRANCH SELECTION — do this first, once, and write the answer at the top of your re-baseline commit.** The IA is settled in Phase 0's D1 record, not here. Read `docs/ROADMAP.md` § Decisions D1 (greps in §2 item 10) and map the signed option onto this packet's two branches:

  | D1 record | This packet | Tabs, in order |
  |---|---|---|
  | **Option A** (D1's recommended default) | **Branch A — four tabs** | Home · Body · Plan · History |
  | **Option B** | **Branch B — three tabs** | Home · Body · Plan |

  Both branches are specified to executable depth below; every work item states its behavior under both, or is labelled as belonging to one. **Never blend them.** If the signature line is blank, or `grep -c "Signed:" docs/ROADMAP.md` returns 0, Phase 0 has not merged — stop and tell the owner (PROTOCOL §4, blocking checkpoint). In both branches: Body keeps route path `progress`, label "Body", `AccessibilityNew` icons; Plan is Phase 4's tab (formerly Routines), `FitnessCenter` icons; Library and Routines both stop being tabs.
- **What is identical in both branches** (the bulk of the phase): the tab-bar rewrite itself (WI-1), Library's demotion to a pushed route with a back affordance (WI-3), `isTabRoute` + `restoreState=false` deletion, `OPEN_ROUTINES` → Plan retargeting, Home stays the NavHost start destination, the deep-link device retest, and the whole history feature set — month grouping with sticky headers, the multi-session-day sheet, the PR summary row, and the two pure domain helpers (`groupSessionsByMonth`, `prSummary`) with their named tests. What differs is only **which screen hosts** that feature set, and therefore whether `HistoryScreen`/`HistoryViewModel` and `Route.History` survive.
- **Home remains the NavHost start destination.** The finish paths `popUpTo(Route.Home.path)` (AppNav.kt:345-347) and `popBackStack(Route.Home.path,…)` (:367) depend on it. Recorded constraint; never change `startDestination`.
- **Route.Library survives as a pushed route only**, pattern `"library?muscle={muscle}"` (AppNav.kt:272-287), param stays the free-text catalog label. The canonical-muscle-key flip is Phase 7's, end-to-end — the `catalogLabel` hop at RecommendationCards.kt:77 stays this phase.
- **`isTabRoute` (AppNav.kt:504-508) is deleted**, along with the `popUpTo/saveState/restoreState=false` Library special cases (:244-252, :259-267). After this phase no tab route carries a query param, so tab matching is plain equality on the destination's registered route pattern.
- **Branch B — the merged Body screen is ONE LazyColumn.** Never nested scrolling, never a child LazyColumn, session rows as individual `items()` (the HistoryScreen.kt:132-139 rationale binds). Section order (top to bottom): window picker (chrome header, outside the list, as today) → silhouette card → per-muscle rows → month calendar → recommendation explanation cards → month-grouped session list with sticky month headers → PR summary. Note this moves the muscle rows ABOVE recommendations relative to current ProgressScreen (:149-184) — settled.
- **Branch B — BOTH scroll anchors are mandatory** (D1's mitigation (i); the merged screen puts the session log five sections deep, so "show me my log" must be reachable without a hunt). The `content` list exposes **two** true scroll indices, built and commented the same way: `content.indexOfFirst { it is BodyItem.Calendar }` and `content.indexOfFirst { it is BodyItem.MonthHeader }` — the first entry of the month-grouped session list. Both are exact only because the list is 1:1 with rendered items; the comment saying so covers both. 6b consumes **both**: `section=calendar` from Home's calendar chip and `section=sessions` from Home's log link. Shipping the calendar anchor alone is an incomplete Branch B.
- **Branch A — Body is not restructured, History hosts the work.** `ProgressScreen.kt`/`ProgressViewModel.kt` get **no** content list, no `BodyItem` sealed interface, no calendar, no session list, no PRs, no merge — their only diff in this phase is whatever WI-4's retarget rows force (nothing beyond the `onOpenRoutines`/`onOpenLibrary` call sites). `HistoryScreen`/`HistoryViewModel` keep their structure and gain the feature set in place: the existing single LazyColumn (HistoryScreen.kt:79-126) keeps `item(key = "calendar")` at the top, the flat `itemsIndexed(state.sessions)` block becomes month groups with `stickyHeader` month kickers, and a PR summary section is appended below the list. No scroll anchors are built (the calendar is already the first item on the History tab, and 6b's chip is a plain tab jump). `TrainingCalendarCard.kt` stays in `ui/history/`.
- **Month grouping is presentation-side, in both branches.** The query is `workoutRepository.observeHistory()` (HistoryViewModel.kt:35) and does not change shape; grouping is a pure function, tested on the JVM. Branch A: the query stays in `HistoryViewModel` and the grouping is applied there. Branch B: the query moves into `ProgressViewModel` unchanged and the grouping is applied there.
- **Multi-session days get a sheet, in both branches.** The first-session-only calendar tap (HistoryScreen.kt:95-97: `day.sessionIds.firstOrNull()?.let(onOpenSession)`) is replaced: one session opens directly, two or more open a day sheet. The sheet is hosted by whichever screen owns the calendar — History under Branch A, Body under Branch B.
- **File plan.** Both branches: `SessionDetailScreen/ViewModel` stay in `ui/history/`, the `"history/{sessionId}"` route string is unchanged, and the two new domain helpers live in `domain/` regardless of host. Branch A: `HistoryScreen.kt`, `HistoryViewModel.kt` and `TrainingCalendarCard.kt` are all edited in place, none moved, none deleted; `ProgressScreen.kt`/`ProgressViewModel.kt` are effectively untouched. Branch B: `ProgressScreen.kt`/`ProgressViewModel.kt` stay the Body tab's files (route `progress`, no renames), `TrainingCalendarCard.kt` moves to `ui/progress/` (package line updated), and `HistoryScreen.kt` + `HistoryViewModel.kt` are deleted.
- **Recommendation surface map** (settled Phase 0/5): Body = full explanation cards. Home keeps exactly its current slots until 6b. Do not add or remove Home recommendation surfaces here.

5. **Work items**

**WI-1 — The tab bar.** Common to both branches: in AppNav.kt rewrite the `tabs` list (:157-163); extend the private `Tab` class (`private data class Tab`, :121-126) with `val matchPattern: String = route.path`; `showBottomBar` (:169-171) and `isSelected` (:207-211) compare `destination.route == tab.matchPattern` — then **delete `isTabRoute` (:504-508)**. Keep `showBottomBar`'s `navBackStackEntry == null` first-frame guard (the :166-171 comment explains it): before the back-stack flow emits, the frame must still count as a tab, or the bar slides up from nothing on every cold start. `goToTab` (:181-189) is unchanged in both branches. The LiveSessionBar hosting in the Scaffold is untouched in both branches. Library and Routines leave the list in both branches (Routines is Phase 4's Plan tab by now — grep for the actual route constant, do not assume `Route.Routines`).

BRANCH A (four tabs): the list is Home, Body (`Route.Progress`), Plan, History (`Route.History`) — in that order, History last. `Route.History` **survives** in the sealed class (:94) and its composable block (:313-318) survives with it; only its `onStartWorkout` wiring is re-verified (WI-4 row 6). The History tab keeps `Icons.Outlined.History`/`Icons.Filled.History` and the label "History"; Body keeps `AccessibilityNew`. Nothing about `HistoryScreen`'s hosting changes — it is still `composable(Route.History.path) { HistoryScreen(...) }`.

BRANCH B (three tabs): the list is Home, Body (`Route.Progress`), Plan. **Delete** `Route.History` from the sealed class (:94) and the History composable block (:313-318). `Route.SessionDetail` (`"history/{sessionId}"`, :107-109) is a different route and stays — do not delete it by string association.

Tests (both branches): `check-when-exhaustive` + `check-screen-wiring` green. Branch B only: `grep -rn "Route.History" app/src/main/java` returns zero hits. Branch A: that grep returns exactly the sealed-class declaration, the tab-list entry, the composable block, and the retarget sites of WI-4 rows 1/6 — enumerate them in the PR instead of expecting silence.

**WI-2 — The history work: month grouping, the day sheet, the PR row.** The feature set is the same in both branches; only its host differs. Read the shared half first, then execute exactly one of the two branch halves.

*Shared — new domain code (pure, in `domain/`, covered by the JVM suite, identical in both branches):*
- `SessionMonthGroup(month: YearMonth, sessions: List<WorkoutSession>)` and `fun groupSessionsByMonth(sessions: List<WorkoutSession>, zone: ZoneId): List<SessionMonthGroup>` — newest month first, sessions within a month keep the repository's order.
- `PrSummaryRow(exerciseId: String, exerciseName: String, kind: PersonalRecordKind, valueKg: Double, reps: Int, achievedAt: Long)` and `fun prSummary(sessions: List<WorkoutSession>, limit: Int = 3): List<PrSummaryRow>` — flatten each exercise's working sets to `ExerciseSetRecord`s, run `PersonalRecords.bests` (PersonalRecords.kt:66) per exercise, keep each exercise's e1RM best (fall back to WEIGHT when e1RM is null), sort by `achievedAt` descending, take `limit`, no two rows for one exercise.

*Shared — tests (same names, same cases, whichever branch runs):* `SessionMonthGroupingTest` (ordering, month boundaries across a year rollover, empty input) and `PrSummaryTest` (e1RM-first selection, WEIGHT fallback, one-row-per-exercise, recency order, limit) in `app/src/test/java/com/sinura/personaltrainer/domain/`, runnable by `tools/run-domain-tests.sh`.

*Shared — the multi-session-day sheet:* the day-tap handler replaces HistoryScreen.kt:95-97 — `sessionIds.size == 1` → `onOpenSession` directly; `> 1` → set `selectedDayEpoch` (rememberSaveable), render a `ModalBottomSheet` (containerColor `Surface3`, mirroring `MuscleDetailSheet` at ProgressScreen.kt:244-248) listing that day's sessions as `SessionLogRow`s; row tap dismisses and opens SessionDetail. It is hosted by the screen that owns the calendar.

**WI-2 · BRANCH A (four tabs) — the work lands in History, in place.**

`HistoryViewModel.kt`: keep the file, the `visibleMonth` StateFlow (:32), the three-way `combine` (:34-52) and the paging functions (:60-68) exactly as they are. `HistoryUiState` (:20-25) gains two fields computed inside the existing combine block: `monthGroups: List<SessionMonthGroup> = groupSessionsByMonth(sessions, ZoneId.systemDefault())` and `records: List<PrSummaryRow> = prSummary(sessions)`. `sessions` stays (the empty check at HistoryScreen.kt:68 uses it). No new query, no new flow, no repository change.

`HistoryScreen.kt`: the screen keeps its shape — the `"History"` display title in the outer `Column` (:54-61), the loading branch, and the empty state (:68-72) are unchanged. Inside the single LazyColumn (:79-126): `item(key = "calendar")` stays first, with the new day-tap handler. Replace the `item(key = "sessions-header")` + flat `itemsIndexed(state.sessions)` pair with one pass over `state.monthGroups`: per group a `stickyHeader(key = "month-${group.month}")` rendering the month as a kicker on a `Pit` background strip (`@OptIn(ExperimentalFoundationApi::class)` on the composable), then `itemsIndexed(group.sessions, key = { _, session -> session.id })` rendering today's row body verbatim (:109-125) with `groupedRowShape(index, group.sessions.size)` computed **within the group**, so each month reads as its own grouped panel with rounded ends. Below the last group, when `records` is non-empty, a `GymSectionHeader("Records")` item and one row per `PrSummaryRow`. New param `onOpenExercise: (String) -> Unit` on `HistoryScreen`, wired in AppNav's History block to `Route.ExerciseDetail.create` — `check-screen-wiring` proves it.

Not built in Branch A: no `BodyItem` list, no scroll anchors, no `content.indexOfFirst`, no changes to `ProgressScreen.kt`/`ProgressViewModel.kt` beyond WI-4's call sites, no file moves, no deletions. The `"All sessions"` section header disappears because the month headers replace it.

**WI-2 · BRANCH B (three tabs) — Body absorbs History.**

`ProgressViewModel.kt`: today `uiState` is a single `.map` over `container.trainingInsights.observe(...)` (:45-65) — there is no combine yet. Rebuild it as a `combine` of that insights flow with `container.workoutRepository.observeHistory()`, `container.preferencesRepository.schedulePreferences`, and a `visibleMonth = MutableStateFlow(YearMonth.now())`, with the paging functions copied verbatim from HistoryViewModel.kt:60-68 (including the never-future clamp). `ProgressUiState` gains: `sessions: List<WorkoutSession>`, `monthGroups: List<SessionMonthGroup>`, `calendar: TrainingMonth`, `weekStart: DayOfWeek`, `records: List<PrSummaryRow>`. Calendar built via `TrainingCalendarBuilder.build` exactly as HistoryViewModel.kt:42-49 (same weekStart comment applies). Keep `includeWeekPlan = false` (:46).

`ProgressScreen.kt`: build the list content as `val content: List<BodyItem>` (a private sealed interface: `Map`, `Muscles`, `Calendar`, `RecommendationItem(rec)`, `MonthHeader(group)`, `SessionRow(session)`, `RecordsHeader`, `RecordRow(row)` — plus the existing notice/window-empty items), then emit it in one LazyColumn: `stickyHeader` for `MonthHeader` (month kicker on a `Pit` background strip; `@OptIn(ExperimentalFoundationApi::class)`), `item`/`items` for the rest, session rows keyed by `session.id` with the `groupedRowShape` treatment lifted from HistoryScreen.kt:108-125/140-145. The current Progress LazyColumn spaces every item with `verticalArrangement = Arrangement.spacedBy(Metrics.cardGap)` (:114); that list-level spacing must go — space the sections with per-item padding instead, so the grouped session rows sit contiguous (hairline-separated, gap-free) exactly as they do on History. One item ↔ one `content` entry, so both `content.indexOfFirst { it is BodyItem.Calendar }` and `content.indexOfFirst { it is BodyItem.MonthHeader }` are true scroll indices — 6b's two anchors depend on this; leave a comment saying so, naming both. Either index is `-1` when its section is absent (no sessions logged yet), so 6b's scroll effect must guard on `index >= 0` — say so in the same comment. The calendar is `TrainingCalendarCard` (moved file), month paging wired to the ViewModel. New `onOpenSession: (String) -> Unit` and `onOpenExercise: (String) -> Unit` params on ProgressScreen, wired in AppNav's Progress block to `Route.SessionDetail.create` / `Route.ExerciseDetail.create` pushes.

Empty states: the merged screen keeps Body's existing no-work empty state (ProgressScreen.kt:95-103); when heat has work but `sessions` is empty the session section shows the History empty copy ("No sessions yet" / "Finish a workout and it lands here", HistoryScreen.kt:68-72) with action "Start workout" → the existing `onStartWorkout` (still `Route.StartWorkout` this phase; 6b retargets).

Delete `HistoryScreen.kt`, `HistoryViewModel.kt`.

**WI-3 — Library stops being a tab.** (Identical in both branches.)

Remove Library from the tab list (done in WI-1). The composable block (:272-287) survives as the pushed destination. `ExerciseLibraryScreen` gains `onBack: () -> Unit` and a back-arrow header (copy the `StartWorkoutHeader` grammar, StartWorkoutScreen.kt:143-167), wired to `popBackStack`. Both `Library.create(muscle)` call sites become plain pushes:

```kotlin
onOpenLibraryMuscle = { muscle -> navController.navigate(Route.Library.create(muscle)) }
```

deleting the `popUpTo/saveState/restoreState=false` blocks at AppNav.kt:244-252 and :259-267. Entry points that remain (the complete set): (a) a "Library" text action added to the Plan screen's header row (next to Phase 4's gear), pushing `Route.Library.create(null)`; (b) recommendation cards via `dispatchRecommendation` OPEN_LIBRARY_MUSCLE (RecommendationCards.kt:76-77); (c) the muscle sheet's "Find lifts" (ProgressScreen.kt:197-199, :316-319). `check-screen-wiring` proves the new callbacks are called.

**WI-4 — The nine-site retarget checklist.** Execute every row; the PR description reproduces this table with each row checked, and each row's "Required end state" cell names the branch you ran. **Seven of the nine rows are identical in both branches** — only rows 1 and 6 differ, and in Branch A both of those are verify-only.

| # | Site (verified at 2212628) | Current behavior | Required end state |
|---|---|---|---|
| 1 | AppNav.kt:241 `onOpenHistory` ← Recent header action, HomeScreen.kt:355-362 | `goToTab(Route.History.path)` | **Branch A:** no change — the History tab still exists, so `onOpenHistory` keeps its param, its wiring and its `goToTab(Route.History.path)` body. Verify by reading and note it in the PR. **Branch B:** delete the `onOpenHistory` param from HomeScreen; `RecentSection`'s header action calls the existing `onOpenProgress` (Body). Label stays "History"-free: use "All sessions". (6b deletes the whole section in either branch; this keeps 6a shippable alone.) |
| 2 | AppNav.kt:240 Home `onOpenRoutines` ← `RecommendationAction.OPEN_ROUTINES` (RecommendationCards.kt:79; produced at RecommendationEngine.kt:179) | `goToTab(Route.Routines.path)` | `goToTab` to the Plan tab's route (Phase 4's rename — grep for the actual path constant). Mechanism unchanged; verify only. |
| 3 | AppNav.kt:269 Progress `onOpenRoutines` | same | same as row 2. |
| 4 | AppNav.kt:244-252 Home `onOpenLibraryMuscle` | tab navigation + `restoreState=false` hack | plain push (WI-3). |
| 5 | AppNav.kt:259-267 Progress `onOpenLibrary` | same hack | plain push (WI-3). |
| 6 | AppNav.kt:313-318 History block; empty-state "Start workout" at HistoryScreen.kt:71-72 | History tab hosts the empty-state start | **Branch A:** block survives; the empty-state start stays exactly as it is (`navigate(Route.StartWorkout.path)`), and the block gains only WI-2's `onOpenExercise` wiring. Verify, don't rewrite. **Branch B:** block deleted; the start action lives in the merged Body session-section empty state (WI-2), still `navigate(Route.StartWorkout.path)` this phase. Either way 6b owns the retarget away from `Route.StartWorkout`. |
| 7 | Settings: AppNav.kt:303 `onOpenSchedule` → `Route.Schedule`; SettingsScreen.kt:302 schedule row | Phase 4 deleted ScheduleScreen and the Settings schedule row | Verify both are gone (grep `Route.Schedule`, `onOpenSchedule`); if any survivor remains, delete it now. Settings' two doors stay: Home gear (AppNav.kt:254) + Plan header gear (Phase 4). |
| 8 | AppNav.kt:345-347 `popUpTo(Route.Home.path)`; :367 `popBackStack(Route.Home.path)` | assume Home is start destination | No change. Recorded constraint: Home stays `startDestination` (:219). Assert by reading, note in PR. |
| 9 | MainActivity.kt:27, :46-61 notification deep link | tab-independent, consume-once | No code change. MUST be re-proven on device (§8 steps 8-9) — the tab rewrite touches the NavHost the deep link lands in, and the LiveSessionBar is a second always-visible resume path. |

**WI-5 — Mechanical proof + scripted device pass.** Run the §7 gate for your branch; fix until green. Then hand the owner §8, using the branch's variant of steps 1-2. The phase does not close on static evidence. The PR body opens with one line naming the branch you executed — "Branch A (four tabs)" or "Branch B (three tabs)" — followed by the re-baseline report from your first commit.

6. **Out of scope** (tempting, forbidden)

- Any Home content change beyond checklist rows 1/4 — masthead, hero, week strip, heat-card/Recent removal are ALL 6b.
- Deleting `Route.StartWorkout` or building the start-options sheet (6b).
- The Library muscle-param canonical-key flip, family grouping, equipment chips, search work (Phase 7); the RecommendationCards.kt:77 `catalogLabel` hop stays.
- Heat semantics, windows, band thresholds (Phase 5, done), imagery (Phase 8).
- LiveSessionBar, finish/discard use cases, editable sessions, repeat-last (Phases 1a/1b, done).
- The A1 DI refactor. Any schema or backup change.
- Restyling existing components (calendar card, session rows, nav bar) beyond what the move forces.
- **Blending branches.** Under Branch A: do not delete `HistoryScreen.kt`/`HistoryViewModel.kt`, do not delete `Route.History`, do not move `TrainingCalendarCard.kt`, do not build `BodyItem` or any scroll anchor, do not restructure `ProgressScreen`. Under Branch B: do not keep a History tab "just in case" and do not leave `HistoryScreen` in the tree unreferenced.

7. **Acceptance gate**

Run, in order, all exit 0 / green:

Both branches:

```bash
tools/preflight.sh                                   # all eight static checks + domain tests
python3 tools/check-screen-wiring.py app/src/main/java   # cite in PR: the new callbacks wired
python3 tools/check-when-exhaustive.py app/src/main/java # cite in PR: no when survivor after the tab-list rewrite
grep -rn "isTabRoute" app/src/main/java              # expect: no output
grep -rn "restoreState = false" app/src/main/java    # expect: no output
grep -rn "Route.Library" app/src/main/java           # expect: pushes only, no tab-list hit
```

Branch B only, additionally:

```bash
grep -rn "HistoryScreen\\|HistoryViewModel" app/src/main/java  # expect: no output
grep -rn "Route.History" app/src/main/java                     # expect: no output
grep -rn "BodyItem.MonthHeader" app/src/main/java               # expect: the sessions anchor exists (D1 mitigation i)
```

Branch A only, additionally: `grep -rn "Route.History" app/src/main/java` returns the declaration, the tab entry, the composable block and nothing else — paste the hits; `grep -rn "BodyItem" app/src/main/java` returns no output (Body was not restructured).

**Owner-machine gate (D-F).** CI has never executed in this repo (billing block, PROTOCOL §3), so no gate line here depends on it. The owner runs, on their machine, and pastes the output into the PR:

```bash
./gradlew testDebugUnitTest assembleDebug            # owner machine; paste the tail into the PR
```

Domain tests by name, green in both lanes (`tools/run-domain-tests.sh` and gradle): `SessionMonthGroupingTest`, `PrSummaryTest`, plus the whole pre-existing suite unchanged. Once the owner's standing (non-gating) billing errand is done, CI green on the PR is an **additional** check — never a substitute for the pasted owner-machine output.

8. **Owner device checklist** (phase closes only when every step is reported)

1. Install the branch build over your current install (no data reset). App opens on Home; the bottom bar shows exactly **Home · Body · Plan · History** (Branch A) or **Home · Body · Plan** (Branch B), volt tick on Home.
2. **Branch A:** tap Body — it looks exactly as it did before this build (window picker, silhouette, coach cards, muscle rows), nothing added. Then tap History: this month's calendar on top, then your sessions grouped by month — the month label stays pinned while its sessions scroll — then a Records section. **Branch B:** tap Body. Top to bottom: window picker, silhouette, muscle rows, this month's calendar, any coach cards, your sessions grouped by month — month label stays pinned while its sessions scroll — then a Records section. Scrolling is one continuous list, no inner scroll traps.
3. On the screen that shows the calendar (History under Branch A, Body under Branch B): tap a day with one session → that session opens. Back returns to that screen where you left it.
4. If any day has two sessions (log two short ones if needed): tapping it opens a sheet listing both; each opens correctly.
5. Page the calendar back several months and forward; it refuses to pass the current month. Rotate the phone; the shown month survives.
6. Tap a Records row (bottom of History under Branch A, bottom of Body under Branch B) → that exercise's detail opens.
7. From a coach card that says "Find … lifts", and from a muscle sheet's "Find lifts": Library opens filtered, with a back arrow that returns to Body. From Plan's header, open Library unfiltered.
8. Start a workout, start a rest timer, background the app, tap the rest notification → the workout screen opens once, back does NOT re-open it. LiveSessionBar shows on Home, Body, Plan, pushed Library — and on History under Branch A.
9. With the session still live: enable Developer options → "Don't keep activities", switch away and back. The app restores to the right tab and the bar is still there. Turn the setting off.
10. Finish the workout → summary → Done lands on Home. Nothing anywhere except the bar ever offered Resume.

9. **Estimates**

Branch-dependent; execute one, quote one.

- **Branch A (four tabs — D1's recommended default): executor 2-2.5 days** — tab-bar rewrite + shim deletion ~0.75, the history work in place (grouping, sticky headers, day sheet, PR rows, two domain helpers + tests) ~1, Library demotion + the seven identical retarget rows + proofs ~0.75. **Owner: 0.5-1 day** (device pass + PR review; the diff is the AppNav top half plus `ui/history/`).
- **Branch B (three tabs): executor 4-5 days** — nav rewrite ~1, Body merge ~2-2.5, retargets + proofs ~1, plus the second scroll anchor. **Owner: 1-1.5 days** (device pass + PR review; the review is mostly the AppNav diff and the merged screen).

Branch A is materially smaller — roughly **half** the executor time — because it drops the size-L merge and keeps the same feature set. Estimates are informational (PROTOCOL §4); they gate nothing.

10. **Hand-back** (completion report to the owner)

- PR link, branch name; preflight output pasted; the owner-machine `./gradlew testDebugUnitTest assembleDebug` output pasted (CI link only if CI has actually run — it is an additional check, not a gate, D-F).
- The re-baseline report from the first commit (D-G): trunk tip, phases merged since `2212628`, measured domain-test and test-class counts, and every drifted packet literal with its verified current value.
- The WI-4 table reproduced with every row's final file:line and a checkmark.
- **Which branch executed, stated in one line: "Branch A (four tabs)" or "Branch B (three tabs)"**, mapped back to the signed D1 option. Branch B additionally states that **both** scroll anchors shipped — `calendar` and `sessions` (D1 mitigation (i)) — naming the two `indexOfFirst` expressions and the file:line of the comment that documents them. Branch A states explicitly that `ProgressScreen`/`ProgressViewModel` were not restructured and that `Route.History`, `HistoryScreen` and `HistoryViewModel` all survive.
- Screenshots: the new tab bar; the screen hosting the calendar and the month-grouped session list with a pinned month header (History under Branch A, Body under Branch B); the multi-session day sheet; Library with back arrow.
- Named test list with results; `check-screen-wiring`/`check-when-exhaustive` outputs.
- Doc deltas included in the PR: ROADMAP phase entry marked done; note closing DESIGN_AUDIT NAV-01/NAV-03/I-03 and updating H-05/H-10 status.
- §8 checklist echoed with the owner's observed results; any deviation listed as an open defect with a file:line hypothesis.
- Explicit statement: Route.StartWorkout and all Home content are untouched, awaiting 6b.
