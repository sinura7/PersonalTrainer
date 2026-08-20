# Phase 1a — Session lifecycle

## 1. Mission

Kill session limbo. Extract finish/discard into shared use cases so no surface can ever again finish or discard a workout while leaving a live rest-timer notification or a stale draft behind; then build the LiveSessionBar — the one persistent live-session surface — on top of them, delete the two competing resume affordances (Home's RestRemainingStrip and the hero's Resume relabel), and add the in-app stale-session nudge. This ships first because it is schema-free, fixes daily pain, and is the surface every later limbo policy needs. Execution follows docs/gameplan/PROTOCOL.md: branch `claude/phase-1a-session-lifecycle`, one PR, phase closes only on owner sign-off.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — branch/PR/gate protocol; this packet does not repeat it. (This file is delivered by the protocol packet before this phase starts — `docs/gameplan/` does not exist on the branch today. If it is missing when you start, the protocol facts stated in this packet — branch name, one PR, owner sign-off closes the phase — are sufficient and binding.)
2. `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutViewModel.kt` — `finishWorkout` (:658-676), `discardWorkout` (:678-690), `clearDraft` (:728-731), `WorkoutExit` (:73-79): the exact invariants being extracted.
3. `app/src/main/java/com/sinura/personaltrainer/data/repository/WorkoutRepository.kt` — `finishSession` (:232-247), `discardSession` (:249-251), `observeInProgress` (:44-46), `insertSessionIfIdle` (:100-112).
4. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt` — routes (:91-119), `showBottomBar` (:169-171), deep-link effect (:173-179), Scaffold bottomBar (:192-215), `InstrumentNavBar` insets (:429), ActiveWorkout finish nav (:342-349).
5. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` — `restRemainingSeconds` collection (:95), RestRemainingStrip usage (:138-145) and definition (:288-317), hero wiring (:146-168), `todayHeadline` (:490-496).
6. `app/src/main/java/com/sinura/personaltrainer/ui/schedule/ScheduleScreen.kt` — `ThisWeekHomeCard` (:412-465), READ ONLY: this phase must not edit this file.
7. `app/src/main/java/com/sinura/personaltrainer/workout/WorkoutDraftCache.kt`, `SavedStateWorkoutDraft.kt`, `WorkoutDraftRecovery.kt` — the two draft stores and why the SavedStateHandle half is nav-entry-scoped.
8. `app/src/main/java/com/sinura/personaltrainer/timer/RestTimerController.kt` — `stop` (:72-83), `remainingSeconds` (:34-47).
9. `app/src/main/java/com/sinura/personaltrainer/workout/StartTrainingDay.kt` — the use-case pattern to mirror (style, outcomes, logging).
10. `app/src/main/java/com/sinura/personaltrainer/AppContainer.kt` — where the new use cases register (:22-70; note restore choke point :59-62).
11. `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutScreen.kt` — discard dialog anatomy (:472-503), exit handling (:143-150).
12. `app/src/main/java/com/sinura/personaltrainer/data/local/dao/WorkoutDao.kt` — query style; `observeInProgressSession` (:36-37).
13. `docs/DEVELOPMENT.md` §Running tests — the eight static checks and the JVM domain-test lane.
14. `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` — accent grammar (line 14), bottom-nav spec (~:160), 56dp sticky-bar precedent (~:167).

## 3. Binding doctrine

Note on sources: `REVISED_STRUCTURE.md` and `attacks.md` are planning-phase documents that are NOT in this repository — you cannot and need not read them. Every constraint they impose is restated inline below; this packet's text is authoritative.

- **DESIGN_AUDIT.md** H-01, H-02 (:258-259 — three ways to resume is the defect this phase deletes); NAV-05 (:453 — the notification deep link survives and must keep working); W-14/W-15 (:307-308 — discard is a named destructive text action behind its own confirm).
- **UI_REDESIGN.md** §6 LiveSessionBar bullet (:146-149) — bar above the nav bar, lift/set-count/rest, tap = return, discard in overflow with its existing confirm; §8 guardrails (:164-172) — accent budget, tokens only; appendix IA-V1 (:319) — stale session locks every start surface; §5.1 (:121-135) — type/color/spacing tokens.
- **DIRECTION_B_INSTRUMENT.md** — one volt accent = live/now; bar is `Pit` with a top hairline like the nav bar (~:160); dialogs confirm, they don't host tasks (~:170).
- **REVISED_STRUCTURE.md** — finding 8 (one-live-affordance rule), item 15 (use cases BEFORE any bar/nudge UI; finish-side invariant), LIVESESSIONBAR CONTRACT (lines 219-224), Phase 1a definition (lines 100-106), stale nudge is in-app evaluation only (attacks.md tech finding "Stale-session nudge", integration finding on zero-set limbo).
- **attacks.md** constraints this packet must visibly satisfy: bar must not bypass ViewModel invariants (tech MAJOR "LiveSessionBar finish/discard duplicates the workout state machine"); bar needs its own insets off-tab and a new observed set-count query; zero-set sessions are discard-only; contradictions FATAL on Home's resume surface and its gate wording.

## 4. Settled decisions

Do not reopen any of these.

1. **One-live-affordance rule (gate wording, verbatim):** "exactly one live-session affordance visible anywhere, counting docked chrome." While the LiveSessionBar is visible, no other surface may show a live-session affordance (resume/finish/discard control). Non-tappable status text (e.g. `todayHeadline`'s "Workout in progress", HomeScreen.kt:491) is not an affordance and is left for Phase 6b's masthead work.
2. **Bar visibility:** visible on every route EXCEPT `session/{sessionId}` (ActiveWorkout), `summary/{sessionId}` (WorkoutSummary), and `startWorkout`. Explicit visible list at today's graph: `home`, `progress`, `routines`, `library?muscle={muscle}`, `history`, `schedule`, `settings`, `routine/{routineId}`, `history/{sessionId}`, `exercise/{exerciseId}`. A null `currentDestination` (first frame) counts as visible. StartWorkout keeps its explicit ResumeBlock (StartWorkoutScreen.kt:97-104, :169-183) — legal because the bar is not visible there.
3. **Insets:** when the tab bar is shown, the bar sits directly above `InstrumentNavBar` (which owns `navigationBarsPadding`, AppNav.kt:429) and adds none of its own; when the tab bar is hidden (pushed routes), the bar applies `Modifier.navigationBarsPadding()` itself. Controlled by an `applyNavInsets: Boolean` parameter = `!showBottomBar`.
4. **Bar content:** 56dp row on `Pit` under a `HairlineDivider(startIndent = 0.dp)`: 3dp volt leading rail; kicker + title column; elapsed clock (`InstrumentType.numeralSm`); rest countdown in `RestCyan` only while running; `MetricCluster(value = workingSets.toString(), label = "sets")` (MetricCluster's `value` parameter is `String` — GymSurfaces.kt:199-206); trailing overflow `IconButton` (`Icons.Outlined.MoreVert`). Row tap (onClickLabel "Back to the workout") resumes. Title = `session.routineName ?: "Workout"`.
5. **Bar copy:** live kicker `"In progress"` in `Volt`; stale kicker `"Left open · ${hours}h"` in `Warn` (hours = floor of (now − lastActivity)/1h). Overflow items: `"Finish workout"`, `"Discard workout…"`. Discard confirm reuses the exact ActiveWorkout copy (ActiveWorkoutScreen.kt:476-484): title "Discard this workout?", body "This deletes the session and its N logged sets. This cannot be undone." (zero sets: "This deletes the session. This cannot be undone."), confirm "Discard" in `Danger`, dismiss "Cancel". Implement via `ConfirmActionDialog` (Common.kt:147) with `destructive = true`.
6. **Zero-set policy:** a zero-set session can never be finished, anywhere. The overflow shows "Finish workout" only when `totalSets ≥ 1` (matches the existing guard, which counts all sets including warm-ups — ActiveWorkoutViewModel.kt:660-664, WorkoutHeader `canFinish` ActiveWorkoutScreen.kt:215); "Discard workout…" always shows. Discard clears the `WorkoutDraftCache` entry (use case) and the SavedStateHandle mirror is inert for any other session by the id match in `SavedStateWorkoutDraft.read` (:16-17) / `WorkoutDraftRecovery`.
7. **Stale definition:** last activity = `MAX(completedAt)` over ALL of the session's sets (warm-ups included — a warm-up is activity), falling back to `startedAt` when the session has no sets. Stale when `now − lastActivity ≥ 4h` (constant `LiveSessionRules.STALE_AFTER_MS = 4L * 60 * 60 * 1000`). Evaluated in-app only, on the bar's 1-second ticker — no WorkManager, no alarms, no notification. The nudge IS the bar's state change (kicker → "Left open · Nh" in `Warn`); zero-set stale sessions are discard-only exactly like any zero-set session. `finishSession`'s duration arithmetic (WorkoutRepository.kt:236-239) is NOT changed in this phase.
8. **Elapsed ticker:** elapsed = wall clock minus `startedAt`, re-derived on every tick from a ViewModel-scoped `flow { while(true) { emit(System.currentTimeMillis()); delay(1_000) } }` combined into the state chain and exposed via `stateIn(WhileSubscribed(5_000))` — survives recomposition, backgrounding, and process death (nothing is accumulated). Display via `LiveSessionRules.formatElapsed(seconds)`: under 1h delegate to `RestTimer.formatClock` ("7:42"); ≥1h `"%d:%02d:%02d"` ("1:07:42").
9. **Use-case shape** (mirrors StartTrainingDay.kt): two files, `workout/FinishWorkout.kt` and `workout/DiscardWorkout.kt`. Constructor deps `(workoutRepository, restTimerController, workoutDraftCache)`. `FinishWorkout.invoke(sessionId: String, notes: String? = null)` — `null` notes means "keep the session's stored notes" (the bar passes null; passing "" would wipe them via `finishSession`'s `notes.trim()` write, WorkoutRepository.kt:241-246). The repository signature stays `finishSession(sessionId: String, notes: String)` — non-null (WorkoutRepository.kt:232) — so the use case implements the null contract itself: it passes `notes ?: session.notes` where `session` is the full session it just read (the domain `WorkoutSession` carries `notes`, Models.kt:60). Order inside finish: read full session (`getSession`, not `observeInProgress` — the summary carries empty `sets`, Mappers.kt `toSummary`); missing → `SessionMissing`; already finished → `Finished(sessionId)` (idempotent); `sets.isEmpty()` → `NothingLogged`; else `restTimer.stop()` → `finishSession(sessionId, notes ?: session.notes)` → `draftCache.clear(sessionId)` → `Finished`. Discard order: `restTimer.stop()` first (unconditional, mirrors ActiveWorkoutViewModel.kt:680 and the restore choke point AppContainer.kt:59-62), then `discardSession`, then `draftCache.clear(sessionId)` → `Discarded`; failures → `Failed(message)`. Outcomes:
   ```kotlin
   sealed interface FinishOutcome {
       data class Finished(val sessionId: String) : FinishOutcome
       data object NothingLogged : FinishOutcome
       data object SessionMissing : FinishOutcome
       data class Failed(val message: String) : FinishOutcome
   }
   sealed interface DiscardOutcome {
       data object Discarded : DiscardOutcome
       data class Failed(val message: String) : DiscardOutcome
   }
   ```
10. **Finish-side invariant (mirrors the StartTrainingDay rule):** every finish and every discard in the app routes through these two use cases. After this phase, `workoutRepository.finishSession(` and `workoutRepository.discardSession(` have exactly one caller each — the use case. Enforced by the grep in the acceptance gate.
11. **SavedStateHandle half:** the use case clears the process-wide cache; `SavedStateWorkoutDraft` is nav-entry-scoped and unreachable from AppNav, so `ActiveWorkoutViewModel` keeps calling `savedDraft.clear()` when it consumes a successful outcome, with a comment saying exactly this. A stale saved-state draft for a discarded session can never attach elsewhere (session-id match, SavedStateWorkoutDraft.kt:16-17).
12. **New observed query** (no schema change — `@Query` only):
    ```kotlin
    // data/local/dao/SessionActivityRow.kt
    data class SessionActivityRow(val totalSets: Int, val workingSets: Int, val lastCompletedAt: Long?)
    // WorkoutDao
    @Query("""
        SELECT COUNT(*) AS totalSets,
               COUNT(CASE WHEN isWarmup = 0 THEN 1 END) AS workingSets,
               MAX(completedAt) AS lastCompletedAt
        FROM set_logs WHERE sessionId = :sessionId
    """)
    fun observeSessionActivity(sessionId: String): Flow<SessionActivityRow>
    ```
    Repository wrapper `WorkoutRepository.observeSessionActivity(sessionId): Flow<SessionActivity>` guarded with `orLogAndFallback` (FlowGuards.kt:18), fallback `SessionActivity(0, 0, null)`; domain holder `data class SessionActivity(totalSets: Int, workingSets: Int, lastCompletedAt: Long?)` in `domain/Models.kt`.
13. **Home hero:** HomeScreen passes `inProgress = false` to `ThisWeekHomeCard` unconditionally, so the card can never render "In progress"/"Resume workout" (ScheduleScreen.kt:424-440) — WITHOUT touching ScheduleScreen.kt (the card's extraction and retargeting belong to Phase 4). `onPrimary` drops the resume branch: when `inProgress != null` it calls `onStartWorkout()` (the StartWorkout screen already shows the explicit blocked/resume state, StartWorkoutScreen.kt:97-104); otherwise unchanged (`startSuggestedDay(target)` / `onStartWorkout()`).
14. **RestRemainingStrip is deleted**, not hidden: the item block (HomeScreen.kt:138-145), the composable (:288-317), `HomeViewModel.restRemainingSeconds` (:35-40), and all imports that die with them (`RestTimer`, `RestCyan`, …) — `check-unused-imports.py` polices.
15. **Bar finish navigation:** mirrors ActiveWorkout's (AppNav.kt:342-349): `navigate(Route.WorkoutSummary.create(id)) { popUpTo(Route.Home.path) { inclusive = false }; launchSingleTop = true }`. Bar resume mirrors Home's (AppNav.kt:235-239): `navigate(Route.ActiveWorkout.create(id)) { launchSingleTop = true }`. Discard navigates nowhere (the bar simply disappears; the user stays put — the bar is never visible on ActiveWorkout so no dead screen is possible; the ActiveWorkout MISSING state, ActiveWorkoutScreen.kt:243-256, already covers the notification-deep-link race).
16. **New domain rules file** `domain/LiveSessionRules.kt` (pure Kotlin, JVM-lane testable):
    ```kotlin
    object LiveSessionRules {
        const val STALE_AFTER_MS: Long = 4L * 60 * 60 * 1000
        fun lastActivityMs(startedAt: Long, lastSetCompletedAt: Long?): Long
        fun isStale(lastActivityMs: Long, nowMs: Long): Boolean      // now - last >= STALE_AFTER_MS
        fun staleHours(lastActivityMs: Long, nowMs: Long): Long      // floor hours, coerced >= 0
        fun formatElapsed(elapsedSeconds: Long): String
    }
    ```

## 5. Work items

### 1a-1 · FinishWorkout / DiscardWorkout use cases (land this commit FIRST, before any bar code)

- **Create** `app/src/main/java/com/sinura/personaltrainer/workout/FinishWorkout.kt`, `app/src/main/java/com/sinura/personaltrainer/workout/DiscardWorkout.kt` per settled decision 9. KDoc each with the invariant list (zero-set guard / timer stop / draft clear / idempotence) and the finish-side invariant (decision 10). Use `runCatchingCancellable` + `AppLog` like StartTrainingDay.kt:37-55.
- **Modify** `app/src/main/java/com/sinura/personaltrainer/AppContainer.kt`: register `val finishWorkout: FinishWorkout` and `val discardWorkout: DiscardWorkout` after `workoutDraftCache` (:40).
- **Modify** `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutViewModel.kt`: `finishWorkout()` (:658-676) and `discardWorkout()` (:678-690) become thin dispatchers over `container.finishWorkout(sessionId, notes.value)` / `container.discardWorkout(sessionId)`. Preserve exactly: error strings ("Log at least one set before finishing.", "Could not finish this workout. Try again.", "Could not discard this workout. Try again."), `finished.value = true`, the one-shot `_exitRequested` values (`WorkoutExit.Finished(sessionId)` / `WorkoutExit.Discarded`), and add `savedDraft.clear()` on success (decision 11). Outcome mapping, so the dispatch is deterministic: `NothingLogged` → error "Log at least one set before finishing."; `SessionMissing` and `Failed` → error "Could not finish this workout. Try again."; `Finished` (including the idempotent already-finished case) → clear error, `savedDraft.clear()`, `finished.value = true`, `_exitRequested = WorkoutExit.Finished(sessionId)`. `Discarded` → clear error, `savedDraft.clear()`, `_exitRequested = WorkoutExit.Discarded`; `DiscardOutcome.Failed` → error "Could not discard this workout. Try again.". Delete the now-unused private `clearDraft()` (:728-731) or reduce it to the savedDraft half.
- **Tests:** behavior is pinned by the gate greps plus existing compile-level checks; the pure pieces are covered by 1a-4's domain tests. No Robolectric/instrumented tests in this phase (the test substrate is Phase 2).

### 1a-2 · SessionActivity observed query

- **Create** `app/src/main/java/com/sinura/personaltrainer/data/local/dao/SessionActivityRow.kt`; **modify** `WorkoutDao.kt` and `WorkoutRepository.kt` per settled decision 12; add `SessionActivity` to `domain/Models.kt`.
- **Tests:** none runnable in-lane for DAO SQL (no instrumented lane yet — Phase 2); the SQL is reviewed in PR. Keep the query trivially simple as written.

### 1a-3 · LiveSessionBar + ViewModel + AppNav hosting

- **Create** `app/src/main/java/com/sinura/personaltrainer/ui/navigation/LiveSessionBarViewModel.kt` extending `AppViewModel`:
  ```kotlin
  data class LiveSessionBarUiState(
      val sessionId: String, val title: String,
      val elapsedLabel: String, val workingSets: Int, val totalSets: Int,
      val restRemainingSeconds: Int, val restRunning: Boolean,
      val stale: Boolean, val staleHours: Long,
  )
  val uiState: StateFlow<LiveSessionBarUiState?>   // null = no in-progress session
  ```
  Chain: `container.workoutRepository.observeInProgress().flatMapLatest { session -> if (session == null) flowOf(null) else combine(observeSessionActivity(session.id), container.restTimerController.remainingSeconds, tick) { … } }` with the 1s ticker of decision 8; staleness via `LiveSessionRules` from `lastActivityMs(session.startedAt, activity.lastCompletedAt)`. Actions: `finishFromBar()` → `container.finishWorkout(id, notes = null)`; on `Finished` set one-shot `_finishedNavigation: MutableStateFlow<String?>` (pattern: HomeViewModel.kt:79-84) + `onFinishNavigationHandled()`. `discardFromBar()` → `container.discardWorkout(id)` (no navigation). Failure handling (settled — do not add error UI): the ViewModel exposes NO error state; a `Failed` outcome from either action is logged via `AppLog` and state is left unchanged. The bar reflects reality on the next Room emission — a failed discard leaves the session alive and the bar visible, which is honest.
- **Create** `app/src/main/java/com/sinura/personaltrainer/ui/navigation/LiveSessionBar.kt`: stateless composable
  ```kotlin
  @Composable fun LiveSessionBar(
      state: LiveSessionBarUiState,
      applyNavInsets: Boolean,
      onResume: () -> Unit, onFinish: () -> Unit, onDiscard: () -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  Structure per settled decisions 4-5: Column(background `Pit`) { HairlineDivider; Row(56.dp, clickable=onResume) { volt rail 3×24dp · Column(Kicker, title) · elapsed numeral · rest countdown (RestCyan, only when `restRunning`) · MetricCluster(workingSets.toString(), "sets") · overflow IconButton } } with `navigationBarsPadding()` iff `applyNavInsets`. Overflow = `androidx.compose.material3.DropdownMenu` + `DropdownMenuItem`, anchored in a `Box` wrapping the overflow `IconButton` with a local `remember { mutableStateOf(false) }` expanded flag. (No DropdownMenu exists anywhere in app/src/main/java today — this is its first use; there is no in-repo precedent to copy.) "Finish workout" only when `totalSets >= 1`; "Discard workout…" always, opening the local `rememberSaveable` confirm via `ConfirmActionDialog` (decision 5, `destructive = true`). Volt appears on the rail+kicker cluster only (accent budget, UI_REDESIGN §8).
- **Modify** `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt`:
  - `val liveBarViewModel: LiveSessionBarViewModel = viewModel()` beside `settingsViewModel` (:150); collect `uiState` and `finishedNavigation`.
  - `private val LIVE_BAR_HIDDEN_ROUTES = setOf(Route.ActiveWorkout.path, Route.WorkoutSummary.path, Route.StartWorkout.path)`; `val showLiveBar = liveSession != null && currentDestination?.route !in LIVE_BAR_HIDDEN_ROUTES` (null route → visible, decision 2).
  - Scaffold `bottomBar` (:192-215) becomes `Column { AnimatedVisibility(showLiveBar) { LiveSessionBar(state, applyNavInsets = !showBottomBar, onResume = { navigate ActiveWorkout per decision 15 }, onFinish = liveBarViewModel::finishFromBar, onDiscard = liveBarViewModel::discardFromBar) }; AnimatedVisibility(showBottomBar) { InstrumentNavBar(…) } }` — bar docked ABOVE the tab bar; reuse the existing slide tween pair (:198-203).
  - `LaunchedEffect(finishedNavigation)` → summary navigation per decision 15, then `onFinishNavigationHandled()`.
- **Tests:** `LiveSessionRulesTest` covers the derived labels' inputs (1a-4); route-set correctness is covered by the gate's manual matrix + `check-screen-wiring.py` for the new callbacks.

### 1a-4 · Domain rules + tests

- **Create** `app/src/main/java/com/sinura/personaltrainer/domain/LiveSessionRules.kt` per settled decision 16.
- **Create** `app/src/test/java/com/sinura/personaltrainer/domain/LiveSessionRulesTest.kt`:
  - `notStaleJustUnderFourHours` (3h59m59s → false), `staleAtExactlyFourHours` (true), `lastActivityPrefersLatestSetIncludingWarmups`, `lastActivityFallsBackToStartedAtWithNoSets`, `staleHoursFloorsAndNeverNegative`,
  - `formatElapsedUnderAnHourMatchesRestClock` ("0:00", "0:59", "59:59"), `formatElapsedWithHours` ("1:00:00", "2:02:02"), `formatElapsedClampsNegative` ("0:00").

### 1a-5 · Home cleanup (strip + hero)

- **Modify** `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` and `HomeViewModel.kt` per settled decisions 13-14. Do NOT touch `ScheduleScreen.kt`, `todayHeadline`, the masthead, or `ThisWeekHomeCard`'s signature.
- **Tests:** none (pure deletion + wiring); `check-screen-wiring.py` and `check-unused-imports.py` police the residue.

## 6. Out of scope

- ANY Room schema change: no new entities, tables, columns, or indices. `@Query` additions only (hard constraint for all of Phase 1).
- `ScheduleScreen.kt` — zero edits. `ThisWeekHomeCard` extraction/retargeting is Phase 4.
- Masthead/`todayHeadline` strings, week strip, Home content rework — Phase 6b.
- `StartTrainingDay` behavior (silent `Open(current.id)` at :37-42, pinned contract) — Phase 4, item 18.
- StartWorkout-interstitial deletion / start-options sheet — Phase 6b.
- `finishSession` duration capping at last-set time — not in this plan; duration semantics unchanged (WorkoutRepository.kt:236-239).
- Background scheduling (WorkManager/alarms) or notifications for staleness — the nudge is in-app only, forever in this phase.
- Editable/deletable finished sessions, repeat, delete-set undo — Phase 1b.
- Tab-bar changes, route additions/removals, A1 DI refactor, `tools/preflight.sh` (Phase 2).

## 7. Acceptance gate

Run from the repo root; every command must exit 0 with no findings:

```bash
python3 tools/check-named-args.py app/src/main/java
python3 tools/check-when-exhaustive.py app/src/main/java
python3 tools/check-unused-imports.py app/src/main/java
python3 tools/check-internal-imports.py app/src/main/java
python3 tools/check-missing-imports.py
python3 tools/check-design-tokens.py app/src/main/java
python3 tools/check-screen-wiring.py app/src/main/java
tools/syntax-check.sh app/src/main/java
PT_JARS=build/test-jars tools/run-domain-tests.sh   # all previous tests + LiveSessionRulesTest green
```

(`tools/preflight.sh` does not exist yet — Phase 2 delivers it; run the checks individually.)

Invariant greps (expected output stated literally):

```bash
grep -rn "workoutRepository.finishSession\|container.workoutRepository.finishSession" app/src/main/java
# → exactly ONE matching line, in workout/FinishWorkout.kt. (The definition line in
#   WorkoutRepository.kt — "suspend fun finishSession(" — does NOT match this pattern
#   and must not appear. Before this phase the only match is ActiveWorkoutViewModel.kt:667.)
grep -rn "workoutRepository.discardSession\|container.workoutRepository.discardSession" app/src/main/java
# → exactly ONE matching line, in workout/DiscardWorkout.kt. (Same note: the definition
#   does not match; the pre-phase match is ActiveWorkoutViewModel.kt:682.)
grep -rn "RestRemainingStrip" app/src/main/java
# → no matches
grep -rn "restRemainingSeconds" app/src/main/java/com/sinura/personaltrainer/ui/home
# → no matches
```

Domain tests by name: `LiveSessionRulesTest` (all methods in 1a-4), plus the whole existing suite unchanged and green.

On the owner's machine (executor cannot build): `./gradlew testDebugUnitTest assembleDebug` green. One-live-affordance gate, verbatim: **"exactly one live-session affordance visible anywhere, counting docked chrome"** — verified by owner checklist steps 4 and 11.

## 8. Owner device checklist

1. Install the phase build (`./gradlew installDebug`, or sideload the CI debug APK per docs/DEVELOPMENT.md).
2. From Home, start today's session via the hero; log one set; tap ✕ → "Keep and exit". **Observe:** a bar docked above the tab bar: volt "In progress", session name, elapsed clock ticking every second, "1 sets".
3. Visit all five tabs, then open Schedule (via the hero card body) and Settings (gear). **Observe:** the bar is on every one of these screens; on Schedule/Settings (no tab bar) it sits fully above the gesture-nav area, not under it.
4. On Home: **observe** there is NO rest strip, the hero button does NOT say "Resume workout" (it shows a Start label), and counting everything on screen — including docked chrome — exactly ONE control anywhere resumes/finishes/discards the live session: the bar.
5. Tap the hero's Start while the session runs. **Observe:** it opens the Start-workout screen showing "Session in progress / Resume …" — no new session starts.
6. Re-enter the workout (tap the bar), start a rest timer, leave again. **Observe:** cyan countdown on the bar matching the notification's.
7. Bar overflow → "Finish workout". **Observe:** Summary appears; Done returns Home; bar gone; rest notification gone; session in History with your set.
8. Start a free workout, log nothing, leave. **Observe:** bar overflow shows ONLY "Discard workout…". Discard → confirm dialog ("This deletes the session. This cannot be undone.") → confirm. **Observe:** bar disappears, no notification remains, nothing in History.
9. Start a session, log a set, leave, background the app, kill it from Recents, reopen. **Observe:** bar returns with correct elapsed time.
10. Stale nudge: with a session open, set the phone clock forward 5 hours, reopen the app. **Observe:** the bar kicker reads "Left open · 5h" in amber. Reset the clock; discard the session.
11. Enter the active workout, the summary, and the Start-workout screens. **Observe:** the bar is absent on all three.

## 9. Estimates

- Executor: 3-4 days (1a-1/1a-4 ≈ 1 day; 1a-2 ≈ 0.25; 1a-3 ≈ 1.5-2; 1a-5 ≈ 0.25; gate + PR polish ≈ 0.5).
- Owner: 0.5-1 day (PR review ≈ 2h — review 1a-1 hardest; device checklist ≈ 45 min; sign-off).

## 10. Hand-back

The completion report (PR description + closing comment) must contain: (1) the PR link on `claude/phase-1a-session-lifecycle`; (2) pasted output of every gate command and all four invariant greps; (3) the domain-test count before/after (was 179-class-equivalent lane; name the added test class); (4) a file-by-file change list with one line each; (5) any deviation from a settled decision, flagged loudly with why (target: zero); (6) the owner checklist verbatim with an empty result column for the owner to fill; (7) the statement that the phase stays open until the owner posts checklist results and merges.
