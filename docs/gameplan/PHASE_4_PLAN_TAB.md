# Phase 4 — Plan tab & the pinned week

## 1. Mission

Give the week an owner. Today the week is a pure function re-invented on every flow emission (`TrainingInsightsCalculator.compute` calls `WeeklySchedulePlanner.plan` with a fresh clock, `domain/TrainingInsights.kt:112-126`), so the plan reshuffles whenever history changes and nothing the user decides survives. This phase builds the pure derivation engine over the `schedule_slots` table Phase 3 froze, puts a `ScheduleRepository` behind it as the sixth insights source, demotes the planner to proposing fills the user explicitly accepts, hardens `StartTrainingDay`'s pinned contract, and rebuilds the Routines tab as **Plan** — week strip on top, routines below, Tune and Settings in the header — deleting the pushed Schedule route in the same landing so Home, Plan, and the hero card can never disagree about what this week is.

## 2. Read first

Line numbers throughout this packet are from audit commit `2212628` on `claude/app-hierarchy-navigation-cjzigo`. Phases 0-3 (and 1a/1b/2) have merged since; lines will have drifted — especially in `AppNav.kt`, which Phase 1a's LiveSessionBar edits. Re-anchor every citation by content (grep the quoted identifier) before editing.

1. `docs/gameplan/PROTOCOL.md` — branch/PR/sign-off rules this phase runs under. The only other gameplan file you need.
2. `docs/gameplan/SCHEDULE_SEMANTICS.md` (Phase 0 deliverable; if the name differs, PROTOCOL.md's index names it) — the owner-signed slot semantics and five worked examples. §4 of this packet restates them; **the signed doc wins on any conflict.**
3. `app/src/main/java/com/sinura/personaltrainer/domain/WeeklySchedulePlanner.kt` — `plan()` (:13-71), `trainingDayIndices` (:98-105), `classifyRoutine` (:138-162), `classifySession` (:357-389), `compatible` (:406-416), `arrangeKinds` (:329-344), `recoveryOverrideSlot` (:90-96), dead `matchesLoggedSession` (:443-446). You will modify most of these.
4. `app/src/main/java/com/sinura/personaltrainer/domain/ScheduleModels.kt` — `SuggestedTrainingDay` (:64-75), `WeeklySchedulePlan` (:77-92), `SessionFocusKind` (:45-56), `SchedulePreferences` (:23-43).
5. `app/src/main/java/com/sinura/personaltrainer/insights/TrainingInsightsSource.kt` — the inner five-flow combine (:60-68, inside the outer combine :59-71) you extend to six; `includeWeekPlan` (:58, :93).
6. `app/src/main/java/com/sinura/personaltrainer/domain/TrainingInsights.kt` — `weekPlan` field (:48), the compute chain (:82-137) you rewire.
7. `app/src/main/java/com/sinura/personaltrainer/workout/StartTrainingDay.kt` — the whole file (57 lines): the outcome contract you change.
8. `app/src/main/java/com/sinura/personaltrainer/ui/schedule/ScheduleScreen.kt` — everything here moves or dies: header (:211-247), day row (:311-403), `PreferenceBlock` (:249-299), `ThisWeekHomeCard` (:412-465), `todayEpochDay` (:467-468).
9. `app/src/main/java/com/sinura/personaltrainer/ui/schedule/ScheduleViewModel.kt` — `loggedEpochDays` derivation (:44-48), `startDay` (:104-115), the nav-StateFlow pattern (:88-102) PlanViewModel must reuse.
10. `app/src/main/java/com/sinura/personaltrainer/ui/routines/RoutinesScreen.kt` + `RoutinesViewModel.kt` — the list, header, delete flow that move into PlanScreen verbatim.
11. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt` — routes (:91-119), tabs (:157-163), `goToTab` (:181-189), every callsite in §5's demolition table.
12. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` — hero wiring (:146-168), imports of `ThisWeekHomeCard`/`todayEpochDay` (:57-58), `onOpenSchedule` param (:82).
13. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeViewModel.kt` — `startSuggestedDay` (:86-97): a `when` over `StartDayOutcome` that your new outcome breaks (by design).
14. `app/src/main/java/com/sinura/personaltrainer/ui/settings/SettingsScreen.kt` — `PreferenceBlock` import (:65), `onOpenSchedule` (:82, :141), the row to delete (:298-311).
15. `app/src/main/java/com/sinura/personaltrainer/data/repository/PreferencesRepository.kt` — schedule prefs stay here (:53-78); slots do NOT live in DataStore.
16. `app/src/main/java/com/sinura/personaltrainer/data/repository/WorkoutRepository.kt` — `startFreeWorkout` stores the focus as `routineName` (:82-98); `insertSessionIfIdle` (:100-112); `observeInProgress` (:44-46).
17. Phase 3's additions: `ScheduleSlotEntity`, `ScheduleDao`, migration 1→2, backup v2 — read them as merged; this phase extends the DAO but never touches schema.
18. `app/src/main/java/com/sinura/personaltrainer/AppContainer.kt` — where `ScheduleRepository` gets wired (:22-70).
19. Doctrine: `docs/ROADMAP.md` (Known open items table, :138 onward; the two planner rows this phase closes are :151-152), `docs/DESIGN_AUDIT.md` §6.9 + NAV-02, `docs/UI_REDESIGN.md` §5.1/§6/§8, `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md`, `docs/DEVELOPMENT.md` (:59-66 static checks, :81-86 domain-test runner). Note ROADMAP.md's own phase numbering is unrelated to the gameplan's — its "Phase 4" is the schema migration.

## 3. Binding doctrine

- **DESIGN_AUDIT.md §6.9**: C-02 (P1 — suggested days pointing at a deleted routine must not crash or empty-start) is closed by work item 3's explicit `Failed` contract. C-04 (prefs in Settings, week on a stack screen, easy to miss) is closed by the Plan tab. C-01 (generated-feeling copy) constrains the derived summary strings. §6.12 NAV-02 (Settings and Schedule stack-only from Home) is closed: Schedule dissolves into a tab; Settings gains its named second home (Plan header gear).
- **UI_REDESIGN.md §6**: "Plan — Routines (start-first)" is this tab. Library-as-segment inside Plan is **not** this phase (Phase 6a/7). "One live surface": the LiveSessionBar (Phase 1a) is the only live-session affordance anywhere — the Plan tab and day sheet add none, and the old Schedule "Resume" item (`ScheduleScreen.kt:162-169`) dies with the screen.
- **UI_REDESIGN.md §5.1 + §8, DIRECTION_B_INSTRUMENT**: token-only colors/radii/spacing, volt accent budget once-or-twice per screen (on the strip: today's marker only), kicker grammar, grouped lists, hand-rolled patterns. `check-design-tokens.py` polices mechanically.
- **REVISED_STRUCTURE**: Phase 4 definition (lines 132-142), SCHEDULE SEMANTICS block (186-200), accepted findings 5 (ScheduleRepository owns the week; fills persist; `insights.weekPlan` rewired here), 9 (nav move happens ONCE, here), 18 (StartTrainingDay pinned contract). These are settled; do not reopen.
- **ROADMAP.md known items :151-152**: "Planner assigns focus to days already in the past" and "`arrangeKinds` can still produce back-to-back same-family days" are fixed here, once, with tests.
- **Mechanical proof** (PROTOCOL.md): `tools/preflight.sh` green before every push; `check-when-exhaustive.py` cited for the `StartDayOutcome` change; `check-screen-wiring.py` cited for the new Plan callbacks.

## 4. Settled decisions

**Ground truth & preconditions.** Work branches from `main` (post-Phase-3 merge) as `claude/phase-4-plan-tab`. Before writing code, verify these exist; if any is missing, stop and report per PROTOCOL.md:
- `schedule_slots` table + `ScheduleSlotEntity` + `ScheduleDao` + migration 1→2 (Phase 3), backup v2 carrying slots, `hasLocalData` counting slots.
- `FinishWorkout`/`DiscardWorkout` use cases + LiveSessionBar (Phase 1a) — the discard path work item 3 wires through.
- `tools/preflight.sh` + the Robolectric JVM lane (Phase 2).

**Slot model (from the Phase-0 signed semantics — restated, signed doc wins).**
- `schedule_slots(id PK, position INT, routineId TEXT? FK→routines ON DELETE CASCADE, focusKind TEXT?, anchorDay INT? 0-6, createdAt, updatedAt)`. A slot is a **routine slot** (`routineId != null`) or a **focus-only slot** (`routineId == null && focusKind != null`). No slot = rest. `anchorDay` maps 0=Monday…6=Sunday = `DayOfWeek.ordinal`; `focusKind` stores the `SessionFocusKind` enum name. If the Phase-3 entity recorded either mapping differently, the entity wins and the domain mapper adapts — never the schema.
- The week is an ordered cycle of slots (by `position`). **Next up** = the first slot not yet satisfied this week. Satisfaction resets at week rollover (week start from `SchedulePreferences.weekStart`). One pass per week: when all slots are satisfied, remaining days are rest; the cycle does not restart mid-week.
- **Satisfaction is by matching, not by an origin column** (schema is frozen; no v3). Finished sessions of the current week (day of `session.date`, consistent with `loggedEpochDays` at `ScheduleViewModel.kt:44-48`), in chronological order, each satisfy at most one slot: the lowest-position unsatisfied slot where (routine slot) `session.routineId == slot.routineId`, or (focus-only slot) `compatible(classifySession(session), slot.focusKind)` (`WeeklySchedulePlanner.kt:406-416`, `:357-389` — promote `compatible` from private to internal). Sessions matching nothing satisfy nothing. Focus-only pins started through `StartTrainingDay` are traceable because `startFreeWorkout` stores the focus title as `routineName` (`WorkoutRepository.kt:82-98`) and `classifySession` reads it.
- **Placement (the effective week)** — a pure function of (slots, history, preferences, today); stored slots are never rewritten by derivation. The rules run in numbered order; within each rule, slots are processed in `position` order. A day is **open** when it holds no slot placed by an earlier rule and no satisfied slot displays on it:
  1. Satisfied slots display on the day of their satisfying session (if two land on one day, the second is satisfied but not displayed — the cell shows the first).
  2. Anchored, unsatisfied, reachable (`anchorDay` ≥ today and that day open): placed on the anchor day. Two unsatisfied slots anchored to the same day: the lower position takes it; the other falls to rule 3.
  3. Anchored but missed/blocked, in position order: **shift forward to the next open day from today — never skip.**
  4. Unanchored, in position order: even-spread over the remaining open days — slot k of n goes to `openDays[(k * openDays.size) / n]`.
  5. More unsatisfied slots than open days: place what fits (position order); the rest are simply not shown this week.
  - Unsatisfied slots are never placed before today — this is the past-day fix at the derivation level.
  - A routine slot whose `routineId` resolves to no routine in the current routines list (e.g. mid-flow before the CASCADE emission lands) is treated as deleted: not placed, never crashes.
- **Regeneration/planner proposals never touch existing slots.** The planner proposes fills for empty (rest) days only; accepted fills are persisted as ordinary slots (anchored to their proposed day) and are thereafter indistinguishable from user pins — the only way any slot ever leaves is explicit unpin. Deleting a routine CASCADEs its slots; the derived week heals (tested).
- Pinnable focus kinds: `PUSH, PULL, LEGS, UPPER, LOWER, FULL_BODY`. `RECOVERY` is planner-only, never pinnable.

**Ownership & rewiring.**
- `ScheduleRepository` (new) owns slot CRUD; it joins `TrainingInsightsSource` as the **sixth** source flow. Slots live in Room, not DataStore; `PreferencesRepository` keeps only days/split/weekStart (`:53-78`).
- `insights.weekPlan` stays type `WeeklySchedulePlan?` but is built from the derived week — **it contains pinned truth only, no invented ghost week**. Consumers that change meaning, all in this phase: Home's `ThisWeekHomeCard` (`HomeScreen.kt:150-167` via `HomeViewModel.kt:61`), the Schedule surface (`ScheduleViewModel.kt:52` — replaced by PlanViewModel), and planner emphasis inputs (the planner now runs only on demand for proposals). `ProgressViewModel` passes `includeWeekPlan = false` (`ProgressViewModel.kt:46`) and is untouched.
- `SuggestedTrainingDay` gains `slotId: String? = null`. In `insights.weekPlan`, every non-rest day has `slotId != null`. Planner proposals have `slotId == null` and live only in PlanViewModel state until accepted — never in insights.
- Owner-visible consequence, stated deliberately: after this phase, until the owner pins or accepts fills, the week is empty. Home's hero shows the "No plan yet" state; Plan shows an empty strip with "Suggest a week" one tap away. The plan is persisted, never invented.

**StartTrainingDay contract (REVISED item 18, extended to all routine days).**
- A day whose `routineId` resolves to a deleted or empty routine returns `Failed(reason)` — never a silent free-workout fallback (replaces `StartTrainingDay.kt:44-51`). Focus-only days (`routineId == null`) legitimately start free workouts named after the focus.
- An in-progress session returns a new outcome `Blocked(inProgressSessionId)` — never silent `Open(current.id)` (replaces `:37-42`). The caller shows an explicit resume-or-discard dialog; discard routes through the Phase-1a `DiscardWorkout` use case, then re-invokes the start.
- Literal strings: deleted → `"That day's routine no longer exists. Swap or unpin it in Plan."`; empty → `"{name} has no lifts yet. Add lifts or swap the day's routine."`

**Plan tab.**
- The Routines tab is renamed **Plan**; the route string stays `"routines"` (`Route.Routines`, `AppNav.kt:93`) — saved-state, back-stack identity, and the tab-selection matching in `isTabRoute` all key on the path, and churning the string buys nothing. Icon unchanged (`FitnessCenter`). Only the `Tab` label at `AppNav.kt:160` changes.
- Screen layout top-to-bottom: header (title "Plan" · Tune · New · gear) → week strip (7 cells) → [Tune card when toggled] → [suggestion controls when proposing] → routines list exactly as today → the existing long-press-delete hint.
- **All pin management lives in the day sheet (tap a strip cell). No long-press anywhere on the strip** — that is the chosen interaction; routines-list rows keep their existing tap=edit / long-press=delete and gain no pin action.
- Header gear opens Settings — the named second home (`navController.navigate(Route.Settings.path)`).
- Old "New week" regenerate dies; its replacement is "Suggest a week" → inline proposals → explicit "Accept fills" persist / "Dismiss" clears. Nothing persists without Accept.

**Demolition.** `Route.Schedule` and the pushed ScheduleScreen are deleted; `ThisWeekHomeCard` is extracted to `ui/home/ThisWeekCard.kt` (composable renamed `ThisWeekCard`); the Home hero-card **body** tap retargets from the pushed Schedule to the Plan tab via `goToTab`; Settings' "This week's plan" row is removed (Settings keeps its `PreferenceBlock` card); `todayEpochDay` moves to `domain/DayLabel.kt`; `PreferenceBlock` moves to `ui/plan/PreferenceBlock.kt`; dead `matchesLoggedSession` (`WeeklySchedulePlanner.kt:443-446`, zero callers — verify with grep) is deleted.

## 5. Work items

### WI-1 — Pure-domain schedule engine (FIRST; no UI, no Room)

**Create `app/src/main/java/com/sinura/personaltrainer/domain/WeekDerivation.kt`:**

```kotlin
data class ScheduleSlot(
    val id: String,
    val position: Int,
    val routineId: String?,
    val focusKind: SessionFocusKind?,
    val anchorDay: DayOfWeek?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class DerivedDay(
    val epochDay: Long,
    val dayOfWeek: DayOfWeek,
    val slot: ScheduleSlot?,          // null = rest/open
    val satisfiedBySessionId: String?,
)

data class DerivedWeek(
    val weekStartEpochDay: Long,
    val days: List<DerivedDay>,       // always size 7
    val nextUp: DerivedDay?,          // day holding the lowest-position unsatisfied slot, or null
)

object WeekDerivation {
    fun derive(
        slots: List<ScheduleSlot>,
        history: List<WorkoutSession>,
        preferences: SchedulePreferences,
        nowMs: Long,
        zone: ZoneId,
    ): DerivedWeek

    fun toWeeklySchedulePlan(
        week: DerivedWeek,
        routines: List<Routine>,
        preferences: SchedulePreferences,
        nowMs: Long,
    ): WeeklySchedulePlan
}
```

`derive` implements §4's satisfaction + placement rules exactly. `toWeeklySchedulePlan` maps each `DerivedDay` to a `SuggestedTrainingDay` (`slotId` set; routine slot → `routineName`/`focusTitle` from the resolved routine and `focusKind = WeeklySchedulePlanner.classifyRoutine(routine)` — the field is non-nullable (`ScheduleModels.kt:68`); focus-only slot → `focusKind = kind`, `focusTitle = kind.label`; rest days as `restDay`-style entries; `confidence = HIGH`; `reason` for a pinned day: `"Pinned to your week."`, for a satisfied day: `"Logged."`). Plan `summary` literals: zero slots → `"No sessions pinned yet."`; else `"{p} pinned · {l} logged this week"`. `thinHistory = false` always. `resolvedSplit = preferences.splitStyle`.

**Modify `ScheduleModels.kt`:** add `slotId: String? = null` to `SuggestedTrainingDay` (default keeps existing tests compiling).

**Planner demotion + fixes, `WeeklySchedulePlanner.kt`:**
- `plan()` gains `pinnedSlots: List<ScheduleSlot> = emptyList()` (last parameter). With pins: derive the week internally, echo pinned/satisfied days (carrying `slotId`), and produce planner-suggested days **only for open days ≥ today** — the past-day fix (`trainingDayIndices` offsets from weekStart, `:98-105`, currently ignore today). Proposals have `slotId = null`. With `pinnedSlots` empty and no history this week, behavior degrades to today's full-week suggestion — but note `TrainingInsightsCalculator` no longer calls it (below); the only production caller is PlanViewModel's `suggestFills`.
- `arrangeKinds` fix (`:329-344`): the current single swap can create the adjacency it exists to prevent (`[UPPER, LOWER, UPPER]` + lastFocus UPPER → `[LOWER, UPPER, UPPER]`). After the first-position fix, run a greedy pass: for each i where `sameStressFamily(kinds[i-1], kinds[i])`, swap `kinds[i]` with the first j > i that breaks the adjacency without creating a new one; if no such j exists, leave it. Change `arrangeKinds` and `compatible` from `private` to `internal` for tests.
- Delete `matchesLoggedSession` (`:443-446`).

**Tests — `app/src/test/java/com/sinura/personaltrainer/domain/WeekDerivationTest.kt`** (the five signed worked examples, by name, plus satisfaction rules). Baseline fixture: weekStart Monday; slots `[0: Push routine @Mon, 1: Pull routine @Wed, 2: Legs routine @Fri]` unless stated:
1. `missedAnchoredDayShiftsForwardNeverSkips` — today Tuesday, no sessions: Push places Tuesday, Pull Wednesday, Legs Friday; Monday is rest; `nextUp` = Push/Tuesday.
2. `missedUnanchoredDayKeepsCycleOrder` — anchorless `[Push, Pull, Legs]`, today Wednesday, Push logged Monday: Push satisfied on Monday; Pull and Legs even-spread over `[Wed..Sun]` → Pull Wednesday, Legs Friday; `nextUp` = Pull.
3. `weekRolloverResetsSatisfaction` — fully-trained week, `nowMs` advanced to next Monday: all slots unsatisfied, anchored slots back on Mon/Wed/Fri, `nextUp` = Push/Monday.
4. `suggestedFillsNeverTouchUserSlots` — `plan(pinnedSlots = [Push @Mon])`, today Monday, 3-day prefs: Monday's day carries the pin's `slotId`; every `slotId == null` non-rest day falls on an open day ≥ today; no proposal on Monday.
5. `routineDeletionHealsDerivedWeek` — derive after removing the Wednesday slot (simulating CASCADE): Wednesday is open, no dangling day, remaining slots placed per rules.
Plus: `slotSatisfiedByMatchingRoutineSession`, `sessionSatisfiesOnlyOneSlot`, `focusSlotSatisfiedByCompatibleSession`, `satisfiedSlotDisplaysOnItsSessionDay`, `unsatisfiedSlotsNeverPlacedBeforeToday`.

**Tests — additions to `WeeklySchedulePlannerTest.kt`:** `plannerNeverProposesPastDays`, `plannerProposalsOnlyOnOpenDays`, `arrangeKindsAvoidsSameFamilyAdjacency` (the `[U,L,U]` case above), `arrangeKindsLeavesUnfixableWeeksAlone` (all-same-family input unchanged).

### WI-2 — ScheduleRepository + sixth source + weekPlan rewire

**Extend Phase 3's `ScheduleDao`** (queries only — no schema change): `observeAll(): Flow<List<ScheduleSlotEntity>>` ordered by `position`, `upsert`, `deleteById`, `maxPosition(): Int?`, and a `@Transaction suspend fun insertAll(slots: List<ScheduleSlotEntity>)` for accepting fills atomically.

**Create `app/src/main/java/com/sinura/personaltrainer/data/repository/ScheduleRepository.kt`:**

```kotlin
class ScheduleRepository(private val scheduleDao: ScheduleDao) {
    fun observeSlots(): Flow<List<ScheduleSlot>>   // .orLogAndFallback("schedule slots", emptyList())
    suspend fun pin(routineId: String?, focusKind: SessionFocusKind?, anchorDay: DayOfWeek?): ScheduleSlot
    suspend fun unpin(slotId: String)
    suspend fun swapRoutine(slotId: String, routineId: String)   // updates routineId + updatedAt
    suspend fun acceptFills(fills: List<SuggestedTrainingDay>)   // one @Transaction DAO call; positions appended in day order; anchorDay = fill's dayOfWeek
}
```

`pin` requires exactly one of routineId/focusKind non-null and appends `position = (maxPosition ?: -1) + 1`. Entity↔domain mapping in `data/mapper/Mappers.kt` (unknown `focusKind` string → drop the slot with a log, never crash).

**Wire:** `AppContainer` (`:22-70`) constructs `ScheduleRepository(database.scheduleDao())` and passes it to `TrainingInsightsSource`. In `TrainingInsightsSource.kt`, the inner five-flow combine (`:60-68`) is at the typed-overload limit — wrap it: `combine(inner, scheduleRepository.observeSlots()) { sources, slots -> sources.copy(slots = slots) }` with `Sources` gaining `slots: List<ScheduleSlot> = emptyList()` (the default keeps the inner combine's `Sources(...)` constructor call (`:67`) compiling unchanged).

**Rewire compute:** `TrainingInsightsInput` gains `slots: List<ScheduleSlot>`. In `TrainingInsightsCalculator.compute` (`TrainingInsights.kt:112-126`), replace the `WeeklySchedulePlanner.plan` call with `WeekDerivation.derive(...)` + `toWeeklySchedulePlan(...)` inside the same `recoverWith`/`InsightFailure.PLAN` guard. The planner is no longer called from insights at all. The old `refresh` param stays (harmless recompute signal) but no surface calls it for regeneration anymore.

**Tests:** `TrainingInsightsCalculatorTest` additions: `weekPlanComesFromPinnedSlotsNotThePlanner` (with slots → days match derivation; with zero slots → seven rest days, summary `"No sessions pinned yet."`). Robolectric lane (Phase 2): `scheduleSlotCascadeOnRoutineDelete` — insert routine + slot, delete routine via DAO, assert slot row gone (proves the FK the healing test assumes).

### WI-3 — StartTrainingDay contract

**Create `app/src/main/java/com/sinura/personaltrainer/workout/StartDayDecision.kt`** — pure, JVM-testable:

```kotlin
sealed interface StartDayDecision {
    data object Rest : StartDayDecision
    data class Blocked(val inProgressSessionId: String) : StartDayDecision
    data class StartRoutine(val routine: Routine) : StartDayDecision
    data class StartFree(val focusTitle: String) : StartDayDecision
    data class RoutineGone(val message: String) : StartDayDecision
}

internal fun decideStart(
    day: SuggestedTrainingDay,
    routine: Routine?,          // resolved from day.routineId, null if missing
    inProgress: WorkoutSession?,
): StartDayDecision
```

Rules: rest → `Rest`; `inProgress != null` → `Blocked(inProgress.id)`; `day.routineId != null` and routine missing/empty → `RoutineGone` with the §4 literal strings; else `StartRoutine`/`StartFree(day.focusTitle)`.

**Modify `StartTrainingDay.kt`:** add `data class Blocked(val inProgressSessionId: String) : StartDayOutcome` to the sealed interface (`:11-19`); `invoke` resolves routine + in-progress, maps `decideStart` to IO (`RoutineGone` → `Failed(message)`, `Blocked` → `Blocked`; starts keep flowing through `startRoutine`/`startFreeWorkout`, whose private `insertSessionIfIdle` transaction (`WorkoutRepository.kt:100-112`) still resolves the start race). The `:37-42` silent `Open(current.id)` and the `:44-51` fallback chain are deleted.

**Callers:** every `when (outcome)` breaks — by design; `check-when-exhaustive.py` is the proof no caller is missed. `HomeViewModel.startSuggestedDay` (`:86-97`) and PlanViewModel handle `Blocked` by setting `blockedByInProgress: String?` state that drives a `ResumeOrDiscardDialog` (full-width stacked buttons per UI_REDESIGN §5.1): title `"A workout is already in progress"`, buttons `"Resume that workout"` (navigates to it) and `"Discard it and start this"` (destructive; calls the Phase-1a `DiscardWorkout` use case, then re-invokes `startTrainingDay(day)`), tap-outside cancels. Place the dialog composable in `ui/components/` so Home and Plan share it.

**Tests — `app/src/test/java/com/sinura/personaltrainer/workout/StartDayDecisionTest.kt`:** `pinnedDayWithDeletedRoutineFailsExplicitly`, `pinnedDayWithEmptyRoutineFailsExplicitly`, `inProgressSessionBlocksInsteadOfSilentResume`, `restDayIgnored`, `focusOnlyDayStartsFreeWorkoutNamedAfterFocus`.

### WI-4 — Plan tab construction (inside the five-tab bar)

**Create `app/src/main/java/com/sinura/personaltrainer/ui/plan/`** — `PlanScreen.kt`, `PlanViewModel.kt`, `PlanDaySheet.kt`, `PreferenceBlock.kt` (moved verbatim from `ScheduleScreen.kt:249-299`).

```kotlin
@Composable
fun PlanScreen(
    onCreateRoutine: () -> Unit,
    onOpenRoutine: (String) -> Unit,
    onWorkoutStarted: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PlanViewModel = viewModel(),
)
```

**Header** (pattern of `RoutinesScreen.kt:147-173`): `Text("Plan", InstrumentType.display)` weighted; `TextButton "Tune"` toggling a `rememberSaveable` tune section hosting `PreferenceBlock` (label flips to "Done"/Volt when open, per `ScheduleScreen.kt:236-241`); `TextButton "New"` (create routine, Volt, shown when routines non-empty, as today); `IconButton(Icons.Outlined.Settings)` → `onOpenSettings`. Accent budget: Volt on at most "New" + today's strip marker; Tune is Volt only while open.

**Week strip:** a `Row` of 7 equal-weight tappable cells from `state.week` (never scrolls). Cell, top-to-bottom:
1. Today marker: 16×3dp bar, Volt if today else transparent (mirrors the nav tick, `AppNav.kt:487-491`).
2. `Kicker(dayOfWeek.shortLabel().take(1))` — Volt when today, else `TextTertiary`.
3. Date numeral: `Text(dayOfMonth, InstrumentType.numeralSm)` — `TextPrimary` on slot days, `TextTertiary` on rest.
4. Name line: `Text(routineName ?: focusTitle, InstrumentType.caption, TextSecondary, maxLines = 1, ellipsis)`; rest days `"Rest"` in `TextTertiary`; proposal days (during suggestion preview) show the proposed name in `TextTertiary`.
5. Logged tick: `Icon(Icons.Outlined.Check, 12.dp, TextSecondary)` when `epochDay in loggedEpochDays`, else a 12dp spacer.

**Suggestion flow:** when the week has ≥1 open day, a `TextButton "Suggest a week"` sits under the strip. Tapping runs `WeeklySchedulePlanner.plan(pinnedSlots = current slots, ...)` on `Dispatchers.Default` from the latest insights value; proposals (`slotId == null`, non-rest) render into their cells plus a row: `Kicker("Suggested")` + `PrimaryGymButton("Accept fills")` (→ `scheduleRepository.acceptFills`, clears preview) + `TextButton("Dismiss")`. Explicit-confirm satisfied: nothing persists before Accept.

**Day sheet** (`PlanDaySheet.kt`, `ModalBottomSheet` per `ExercisePickerSheet` conventions), opened by cell tap; content by state:
- Always: date kicker (`"Monday · 24 Aug"` style — reuse `HomeScreen.kt:499`'s `"EEEE '·' d MMM"` pattern), title = routineName/focusTitle or `"Rest day"` (open future day: `"Open day"`), caption = pinned reason / `"Logged"` when satisfied / `"No session logged."` for a past open day.
- Pinned future/today day, not logged: `PrimaryGymButton("Start {name}")` → `viewModel.startDay(day)`; rows `"Swap routine…"` (inline routine picker list) and `"Unpin this day"` (immediate, no dialog — recoverable by re-pinning).
- Pinned + logged today: primary label `"Train again"` (labels per `ScheduleDayRow`, `ScheduleScreen.kt:382-388`); same manage rows.
- Open future/today day: rows `"Pin a routine…"` (routine picker; creates slot anchored to this day) and `"Pin a focus…"` (`InstrumentChip` row of the six pinnable kinds; anchored to this day).
- Past days: informational only, no actions.
- Start is always visible where defined even during an in-progress session; `Blocked` surfaces the shared `ResumeOrDiscardDialog` (WI-3). The sheet itself adds no live-session affordance.

**PlanViewModel** (pattern of `ScheduleViewModel`, including the nav-StateFlow at `:88-102`):

```kotlin
data class PlanUiState(
    val isLoading: Boolean = true,
    val week: WeeklySchedulePlan? = null,
    val routines: List<Routine> = emptyList(),
    val preferences: SchedulePreferences = SchedulePreferences.DEFAULT,
    val inProgress: WorkoutSession? = null,
    val loggedEpochDays: Set<Long> = emptySet(),
    val proposals: List<SuggestedTrainingDay> = emptyList(),
    val blockedByInProgress: String? = null,
    val error: String? = null,
)
```

Combines `container.trainingInsights.observe()`, `observeInProgress()`, `schedulePreferences`, action-error; `loggedEpochDays` derived exactly as `ScheduleViewModel.kt:44-48`. Actions: `startDay`, `pinRoutine(epochDay, routineId)`, `pinFocus(epochDay, kind)`, `unpin(slotId)`, `swapRoutine(slotId, routineId)`, `suggestFills()`, `acceptFills()`, `dismissFills()`, `deleteRoutine(id)` (moved from `RoutinesViewModel.kt:37-47`), Tune setters (moved from `ScheduleViewModel.kt:71-81`), `resumeBlocked()`/`discardBlockedAndStart(day)`.

**Routines section:** `RoutineRow`, delete-confirm dialog, empty state, and the long-press hint move verbatim from `RoutinesScreen.kt` into `PlanScreen.kt`. When routines are empty but the strip exists, the strip still renders (an empty week) above the routines empty-state.

**Per-item tests:** UI is device-verified (owner checklist); the ViewModel's derivable logic (logged-days set, proposal filtering `slotId == null && !isRest`) lives in the domain layer already covered by WI-1/WI-2 tests. `check-screen-wiring.py` proves every new callback is called.

### WI-5 — Demolition and retargets

Every `AppNav.kt` change (audit-commit lines; re-anchor by content — Phase 1a will have shifted them):
1. `:75` `import ...ui.schedule.ScheduleScreen` — delete.
2. `:76` `import ...ui.routines.RoutinesScreen` — replace with `import ...ui.plan.PlanScreen`.
3. `:112` `data object Schedule : Route("schedule")` — delete.
4. `:160` `Tab(Route.Routines, "Routines", ...)` — label → `"Plan"`; route and icons unchanged.
5. `:243` Home callsite `onOpenSchedule = { navController.navigate(Route.Schedule.path) }` → `onOpenPlan = { goToTab(Route.Routines.path) }`.
6. `:288-299` the `composable(Route.Schedule.path) { ScheduleScreen(...) }` block — delete entirely.
7. `:303` Settings callsite `onOpenSchedule = { navController.navigate(Route.Schedule.path) }` — delete (param removed from `SettingsScreen`).
8. `:307-312` Routines composable → `PlanScreen(onCreateRoutine = ..., onOpenRoutine = ..., onWorkoutStarted = { navController.navigate(Route.ActiveWorkout.create(it)) { launchSingleTop = true } }, onOpenSettings = { navController.navigate(Route.Settings.path) })` — no `popUpTo` on workout start (the tab stays underneath, matching Home's resume at `:235-238`).
Unchanged by design, recorded: `goToTab` (`:181-189`), `isTabRoute` (`:504-508`), Home as start destination and the `popUpTo(Route.Home.path)` finish paths (`:345-347`, `:367`).

Other files:
- **Delete** `ui/schedule/ScheduleScreen.kt`, `ui/schedule/ScheduleViewModel.kt`, `ui/routines/RoutinesScreen.kt`, `ui/routines/RoutinesViewModel.kt` (RoutineEditor files stay).
- **Create `ui/home/ThisWeekCard.kt`:** extract `ThisWeekHomeCard` (`ScheduleScreen.kt:412-465`) as `ThisWeekCard(day, nextDay, loggedToday, onOpenPlan, onPrimary)`. Drop the `thinHistory` param and its caption (derived weeks set it false). Phase 1a already removed the in-progress branches (`:435-440` era); if any survive, delete them — the card must carry no live-session affordance. Add the empty-week state: when `day == null && nextDay == null`, headline `"No plan yet"`, caption `"Pin your week in Plan."`, action `"Start a workout"`. Card body tap = `onOpenPlan`.
- **`HomeScreen.kt`:** param `onOpenSchedule` → `onOpenPlan` (`:82`); imports `ui.schedule.ThisWeekHomeCard`/`ui.schedule.todayEpochDay` (`:57-58`) → `ui.home.ThisWeekCard`/`domain.todayEpochDay`; callsite (`:150-167`) updated.
- **`domain/DayLabel.kt`:** gains `fun todayEpochDay(nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long` (moved from `ScheduleScreen.kt:467-468`).
- **`SettingsScreen.kt`:** remove `onOpenSchedule` param (`:82`) and its pass-through (`:141`, `:284`); delete the `"This week's plan"` `GroupedList` row (`:298-311`); update the `PreferenceBlock` import (`:65`) to `ui.plan.PreferenceBlock`; the section caption (`:288`) becomes `"Days, split and week start. Pin the week itself on the Plan tab."`; `KeyboardArrowRight` import dies with the row (`check-unused-imports.py` will confirm).
- **Docs in the same PR:** ROADMAP.md known-items rows `:151-152` marked fixed (this phase); DESIGN_AUDIT C-02, C-04, NAV-02 annotated closed with a pointer to this phase.

Proof greps (all five must return nothing): `grep -rn "Route.Schedule" app/src`, `grep -rn "ui.schedule" app/src`, `grep -rn "matchesLoggedSession" app/src`, `grep -rn "ThisWeekHomeCard" app/src`, `grep -rn "onOpenSchedule" app/src`.

## 6. Out of scope

- Tab-bar consolidation: the bar stays five tabs; Library and History are untouched (Phase 6a).
- Home content rework beyond the hero-card body-tap retarget and the `ThisWeekCard` extraction — no masthead strings, no week strip on Home, no recommendation-slot changes (Phase 6b).
- Heat/coach changes: bands, `HeatWindow` edits, RPE, imbalance, recommendation surfaces (Phase 5).
- Any schema change. `schedule_slots` DDL is frozen from Phase 3; no new columns, no origin-tracking column, no v3.
- Backup format — v2 already carries slots (Phase 3). Do not touch `BackupDocument`/`BackupValidator`.
- LiveSessionBar, Finish/Discard use-case internals (Phase 1a owns them; you only call `DiscardWorkout`).
- `StartWorkoutScreen` interstitial and start-options sheet (Phase 6b). Library picker/catalog work (Phase 7). Imagery (Phase 8). A1 DI refactor.
- Routine editor changes of any kind.

## 7. Acceptance gate

```
git checkout claude/phase-4-plan-tab
tools/preflight.sh
```
Expected: all eight static checks report zero findings and the domain suite passes. If running checks individually (`docs/DEVELOPMENT.md:59-66`): each `python3 tools/check-*.py app/src/main/java` exits 0 with no findings; `tools/syntax-check.sh app/src/main/java` clean; `PT_JARS=build/test-jars tools/run-domain-tests.sh` green including the new tests.

The five proof greps in WI-5 return nothing.

`check-when-exhaustive.py` output cited in the PR as proof every `when (StartDayOutcome)` handles `Blocked`; `check-screen-wiring.py` cited as proof every PlanScreen callback is invoked.

On a machine with the Android SDK (owner's, or CI once green): `./gradlew testDebugUnitTest` passes including the Robolectric `scheduleSlotCascadeOnRoutineDelete`; `./gradlew assembleDebug` builds.

Domain tests by name: `missedAnchoredDayShiftsForwardNeverSkips`, `missedUnanchoredDayKeepsCycleOrder`, `weekRolloverResetsSatisfaction`, `suggestedFillsNeverTouchUserSlots`, `routineDeletionHealsDerivedWeek`, `slotSatisfiedByMatchingRoutineSession`, `sessionSatisfiesOnlyOneSlot`, `focusSlotSatisfiedByCompatibleSession`, `satisfiedSlotDisplaysOnItsSessionDay`, `unsatisfiedSlotsNeverPlacedBeforeToday`, `plannerNeverProposesPastDays`, `plannerProposalsOnlyOnOpenDays`, `arrangeKindsAvoidsSameFamilyAdjacency`, `arrangeKindsLeavesUnfixableWeeksAlone`, `weekPlanComesFromPinnedSlotsNotThePlanner`, `pinnedDayWithDeletedRoutineFailsExplicitly`, `pinnedDayWithEmptyRoutineFailsExplicitly`, `inProgressSessionBlocksInsteadOfSilentResume`, `restDayIgnored`, `focusOnlyDayStartsFreeWorkoutNamedAfterFocus`.

The phase closes only on owner sign-off of the device checklist (PROTOCOL.md).

## 8. Owner device checklist

Install the phase APK over your current install (your data stays).

1. The third tab now reads **Plan**. Open it: a 7-day strip on top (all "Rest" if you have not pinned anything), your routines below, and Tune / New / a gear in the header.
2. Tap the gear — Settings opens. Go back. In Settings, confirm the schedule section still has the days/split/week-start card but **no** "This week's plan" row.
3. Tap tomorrow's cell → sheet opens → "Pin a routine…" → pick one. The cell now shows that routine's name.
4. Go Home. The hero card names the same session for the same day as the Plan strip. Tap the card **body** (not the button) — you land on the Plan tab, not a pushed screen (the bottom bar stays visible).
5. On Plan, tap "Suggest a week", then "Accept fills". Force-stop the app (or reboot) and reopen: the week is exactly as accepted — nothing reshuffled.
6. Tap today's cell → Start. Log one set, finish the workout. Back on Plan, today's cell shows the check mark; Home's hero has moved on to the next pinned day.
7. Long-press-delete a routine that is pinned to a day. The Plan strip heals — that day shows the suggestion-free "Rest"/open state, no crash, no phantom name. Tap the day: it offers pin actions again.
8. Pin a routine, then edit it and remove all its lifts. Tap that day's Start: you get a clear error naming the problem ("…has no lifts yet…"), **not** an empty free workout.
9. Start a workout and leave it running. From Plan, tap a different day's Start: a dialog offers "Resume that workout" / "Discard it and start this" — it must never silently drop you into the old session. Try both paths (discard one you don't mind losing).
10. Confirm nothing anywhere except the LiveSessionBar offers to resume while a session runs — Plan strip, day sheet, and Home hero included.
11. Settings → export a backup file; then import it. Your pinned week survives the round trip.
12. Reply on the PR with pass/fail per step. The phase closes on your sign-off.

## 9. Estimates

- **Executor:** 4-6 days. WI-1 engine + planner fixes + tests ≈ 1.5-2; WI-2 repository + rewire ≈ 1; WI-3 contract + callers ≈ 0.5-1; WI-4 Plan tab ≈ 1.5-2; WI-5 demolition + docs ≈ 0.5. The wide `when-exhaustive` ripple from `Blocked` and the six-flow combine are the likely overrun points.
- **Owner:** 0.5-1 day — the 12-step device pass plus PR review. The PR is large but mechanically partitioned: review WI-1/WI-3 (behavior) carefully; WI-4/WI-5 (UI/moves) can be reviewed by screenshot and checklist.

## 10. Hand-back

The completion report to the owner must contain:
1. PR link (`claude/phase-4-plan-tab` → main) with the WI-1…WI-5 commits separable.
2. Preflight output pasted (eight checks + domain-test count, all green) and the five empty proof-greps.
3. The `check-when-exhaustive` and `check-screen-wiring` outputs cited as the mechanical proof for the `StartDayOutcome.Blocked` ripple and the new Plan callbacks.
4. The named domain-test list from §7 with pass status, and confirmation the five worked-example tests match `docs/gameplan/SCHEDULE_SEMANTICS.md` (or an explicit diff if the signed doc disagreed and won).
5. A plain-language note of the one behavior change the owner will feel immediately: **the auto-generated ghost week is gone** — Home and Plan show only pinned/accepted sessions, and the first thing to do after updating is pin a week or tap "Suggest a week → Accept".
6. The debug APK (or build instructions) plus the §8 checklist, and the statement that the phase stays open until the owner reports the checklist results.
7. Doc edits included: ROADMAP known-items rows :151-152 closed; DESIGN_AUDIT C-02/C-04/NAV-02 annotated.
8. One open owner question, asked on the PR (answer does not block merge): Settings keeps its days/split/week-start `PreferenceBlock` card — only the "This week's plan" navigation row is removed, per REVISED_STRUCTURE's "Settings' schedule row → removed" — so the same three preferences are editable in both Settings and Plan's Tune. Confirm you want the duplicate surface kept; if not, removing Settings' card is a small follow-up.
9. Any deviations from this packet, each with one line of why.
