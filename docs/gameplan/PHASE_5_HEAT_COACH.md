# Phase 5 — Honest heat & coach

> Executor spec packet. Standalone: this document plus `docs/gameplan/PROTOCOL.md` are your
> only instructions. Execute on branch `claude/phase-5-heat-coach` cut from trunk after the
> Phase 4 PR merges. Do not start before it merges.
>
> All `file:line` citations below were verified at commit `2212628` on
> `claude/app-hierarchy-navigation-cjzigo` — before Phases 1a–4 landed. Line numbers will
> have drifted; symbols will not. Re-locate every cite by symbol before editing.

## 1. Mission

The body map currently lies: heat is normalized to the window's hardest muscle
(`MuscleLoadCalculator.normalizeHeat`, MuscleLoadCalculator.kt:87-90), so the same training
week changes color when an unrelated muscle changes, and the coach's advice changes when the
user flips a display chip (RecommendationEngine consumes the display-window snapshot,
TrainingInsights.kt:105-107). This phase replaces relative heat with absolute weekly
weighted-set bands, cuts the windows to This week + Last 30 days, computes the coach on a
fixed trailing-14-day basis, upgrades all five rules (sets-based imbalance, RPE consumption,
two symmetric do-less rules, named owned lifts, goal/equipment inputs), codifies the coach's
voice, and lands the two action surfaces plus mid-workout swap/remove. It runs now because
Phase 3's junction weights (exercise_muscles) and Phase 4's Plan tab are its prerequisites,
and Phase 6a/6b build Body/Home on top of the honest numbers, not the old ones.

## 2. Read first

1. `docs/gameplan/PROTOCOL.md` — branch/PR/gate/owner-sign-off protocol; this packet follows it.
2. `app/src/main/java/com/sinura/personaltrainer/domain/MuscleLoad.kt` — HeatWindow enum (7D/14D/week at :12-14), HeatBand cuts (:36-58), MuscleLoadSummary/BodyHeatSnapshot shapes you will change.
3. `app/src/main/java/com/sinura/personaltrainer/domain/MuscleLoadCalculator.kt` — the accumulator, `normalizeHeat` (:87-90) you delete, lifetime-recency recording (:40-45, :122-124), window filtering (:38-57), and the junction-based credit resolution Phase 3 installed in `mappingFor` (read what actually landed there).
4. `app/src/main/java/com/sinura/personaltrainer/domain/RecommendationEngine.kt` — all five rules, every constant (:27-37), window-coupled copy (:150, :177, :190-194).
5. `app/src/main/java/com/sinura/personaltrainer/domain/TrainingInsights.kt` — the compute chain (:82-137) where the coach basis will be added.
6. `app/src/main/java/com/sinura/personaltrainer/insights/TrainingInsightsSource.kt` — the five-source combine (:59-71; six after Phase 4 added ScheduleRepository), the `LAST_7_DAYS` default (:56), `computeDispatcher` (:45, :96).
7. `app/src/main/java/com/sinura/personaltrainer/ui/progress/ProgressScreen.kt` — window chips (:224), `pickerLabel` (:335-340), `sentenceLabel` (:343-346), MuscleDetailSheet band kicker (:260), recommendation dispatch (:153-167).
8. `app/src/main/java/com/sinura/personaltrainer/ui/progress/ProgressViewModel.kt` — in-memory window default (:18, :32). Note: **no window preference is persisted today**; this phase adds persistence.
9. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` — heat card + `"Last 7 days"` kicker fallback (:415), recommendation dispatch (:174-181).
10. `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutScreen.kt` — add-exercise sheet hookup (:415-424), LiftSwitcher chips (:677-703), CurrentLiftHeader (:705-730), LastTimeStrip (:757-788), ProgressionStrip (:790-826), `INCREMENT_KG` copy (:129, :797-800).
11. `app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutViewModel.kt` — `addExerciseInternal` (:473-494), picker state (:442-449).
12. `app/src/main/java/com/sinura/personaltrainer/ui/workout/StartWorkoutScreen.kt` — routine rows list (:106-136) where the pinned suggestion goes; `StartWorkoutViewModel.kt` beside it.
13. `app/src/main/java/com/sinura/personaltrainer/data/repository/WorkoutRepository.kt` — `removeExerciseFromSession` (:140-142, **defined and never called** — verified by grep: definition is the only hit), `progressionFor` (:259-276), `readyForProgression` (:394-415), `topSetOfLastSession` (:425-434), `addExerciseToSession` (:114-138).
14. `app/src/main/java/com/sinura/personaltrainer/domain/ProgressionCalculator.kt` (`INCREMENT_KG` :11), `PersonalRecords.kt` (`estimatedOneRepMaxKg` :58-63), `Models.kt` (ProgressionHint :110-118, SetLog rpe :50).
15. `app/src/main/java/com/sinura/personaltrainer/data/local/entity/SetLogEntity.kt` — `rpe: Int?`, `isWarmup` (:33-34) exist; RPE is stored and read by nothing (ROADMAP.md:150).
16. `app/src/main/java/com/sinura/personaltrainer/data/repository/PreferencesRepository.kt` — DataStore key pattern (:216-231) for the three new keys.
17. `app/src/main/java/com/sinura/personaltrainer/ui/settings/SettingsScreen.kt` — section pattern (WeightUnitsSection :247-270) for the new Coaching section.
18. `app/src/main/java/com/sinura/personaltrainer/domain/WeeklySchedulePlanner.kt` — heat consumers (:248-250) and the relative-heat copy (:285) that becomes false.
19. `app/src/main/java/com/sinura/personaltrainer/ui/theme/Color.kt` — the ramp tokens `HeatEmpty/Heat1..Heat4` and `heatColor` (:109-136).
20. Doctrine: `docs/ROADMAP.md` (:148, :150 known items — verified at `2212628`; the honest-heat/coach decision and the not-to-build list are Phase 0's doc edits and did NOT exist at `2212628` — locate them by content, not line number), `docs/DESIGN_AUDIT.md` B-03 (:409), `docs/UI_REDESIGN.md` §5.1 (:121-135), §6 (:139-152), §8 (:164-170), `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` (kicker label voice, :103), `docs/DEVELOPMENT.md` (:60, :65 static checks; :81-86 domain-test runner).

## 3. Binding doctrine

- **ROADMAP.md, post-Phase-0 sections (honest heat/coach; not-to-build)** — absolute weekly sets-per-muscle bands replace relative-max normalization; coach computes from a fixed window regardless of the display chip; day/year windows are recorded not-to-build; windows become This week + 30 days. LLM/chat coach and muscle-head granularity are recorded non-goals — do not approach them. (These sections do not exist at `2212628` — ROADMAP.md is 156 lines there; Phase 0 adds them. Find them by their headings; if Phase 0 somehow did not land them, the revised game-plan brief below is the authority and the doctrine stands unchanged.)
- **ROADMAP.md :148, :150** — imbalance moves from tonnage to working-set counts; RPE becomes read. Both are owned by THIS phase only (no other phase may touch them).
- **DESIGN_AUDIT B-03 (:409)** — recommendations must name lifts the owner already has. This phase delivers the naming (pictures are Phase 8).
- **DESIGN_AUDIT ACC-03 (:646)** — color is never the only channel: every band is also a word (legendLabel kicker, contentDescription — pattern already at BodyMap.kt:159, :357).
- **UI_REDESIGN §5.1 (:130)** — one heat ramp, one encoding, used identically by body map, calendar, Home tiles. The band mapping below rides `heatColor`; no new color literals (§8 :166: no `Color(0x…)` outside `ui/theme/`).
- **UI_REDESIGN §6 (:151)** — mid-workout swap/remove: "overflow on the current lift header; `WorkoutRepository.removeExerciseFromSession` already exists with no UI caller". Dispositioned HERE.
- **DIRECTION_B_INSTRUMENT (:103)** — the instrument label voice: kickers are ALL-CAPS category labels. The voice spec in §4 below is its application to coach copy.
- **Revised game-plan (binding brief)** — band thresholds, band→ramp mapping, 30-day averaging formula, 14-day coach basis, and the recommendation surface map (Home = ONE next-session module [6b]; Body = full explanation cards; StartWorkout options sheet + in-workout add sheet = action surfaces, one pinned suggestion each; no other surfaces) are pre-decided. Stated as settled in §4; do not reopen.
- **Execution protocol** — `tools/preflight.sh` (Phase 2 deliverable: all eight static checks + domain tests) green before every push; `tools/check-when-exhaustive.py` and `tools/check-screen-wiring.py` (DEVELOPMENT.md:60, :65) cited as the mechanical proof wherever enums/callbacks change. Phase closes only on owner sign-off of §8.

## 4. Settled decisions

**D1 — Windows.** `HeatWindow` becomes exactly two members; 7D and 14D are deleted:

```kotlin
enum class HeatWindow(val label: String, val shortLabel: String) {
    CURRENT_WEEK("This week", "Week"),
    LAST_30_DAYS("Last 30 days", "30 days");
    // startMs: CURRENT_WEEK unchanged (weekStart-anchored, MuscleLoad.kt:27-31);
    // LAST_30_DAYS -> now.minusDays(30) (same shape as the deleted LAST_7_DAYS branch)
    companion object {
        fun fromStorage(raw: String?): HeatWindow =
            entries.firstOrNull { it.name == raw } ?: CURRENT_WEEK
    }
}
```

The brief names this pair "THIS_WEEK + LAST_30_DAYS"; its THIS_WEEK is realized as the
EXISTING `CURRENT_WEEK` member — identical weekStart-anchored semantics — because keeping
the name avoids a pointless rename ripple and `fromStorage` decodes whatever name is stored.
This is not drift; do not rename.

Picker labels: `CURRENT_WEEK → "THIS WEEK"`, `LAST_30_DAYS → "30 DAYS"`. Sentence labels:
`"this week"` / `"in the last 30 days"`. Default window everywhere: `CURRENT_WEEK`.

**D2 — Window preference.** Verified fact: nothing persists the window today
(ProgressViewModel.kt:32 is an in-memory MutableStateFlow) — the brief's "stored window
preference migrated" is satisfied by tolerant decode. This phase ADDS persistence:
DataStore key `stringPreferencesKey("heat_window")` in PreferencesRepository, read/written
by ProgressViewModel, decoded via `HeatWindow.fromStorage` (unknown/legacy → CURRENT_WEEK).

**D3 — Band model.** A working set credits each muscle by its Phase-3 junction weight
(primary 1.0, secondaries by their `weight` column). Per-muscle weekly weighted sets band:

| Band | weeklySets s | legendLabel | ramp token |
|---|---|---|---|
| UNTRAINED | s < 4.0 | "Untrained" | heat0 (`HeatEmpty`) |
| LOW | 4.0 ≤ s < 10.0 | "Low" | heat1 (`Heat1`) |
| PRODUCTIVE | 10.0 ≤ s ≤ 20.0 | "Productive" | heat3 (`Heat3`) |
| HIGH | s > 20.0 | "High" | heat4 (`Heat4`) |

heat2 is reserved for between-band interpolation on the silhouette (D4). Window scaling:
CURRENT_WEEK uses the raw week-to-date total (reads low early in the week — intended:
"this week so far"); LAST_30_DAYS uses the per-week average `total × 7.0 / 30.0`.

**D4 — heat fraction (replaces `normalizeHeat`).** `MuscleLoadSummary.heat` stays a 0..1
Double so `heatColor` (Color.kt:128-136) and every consumer keep working, but becomes
absolute — a piecewise-linear function of weeklySets with these literal anchors.
(Constants live on MuscleLoadCalculator. Ramp geometry, verified: `heatColor` maps
t ≤ 0.02 (`HEAT_EMPTY_THRESHOLD`) to `HeatEmpty` and spreads Heat1..Heat4 evenly over
t = 0.02..1.0, so Heat1 sits at t = 0.02 exactly and Heat3 at t = 0.6733 exactly
((2/3)×0.98+0.02). `FRACTION_LOW = 0.05` deliberately sits just above the empty cutoff so
the LOW floor renders visibly on the ramp rather than at the HeatEmpty boundary — do not
"correct" it to 0.02.)

```
f(s) = 0.0                                   for s < 4.0        // UNTRAINED renders HeatEmpty
     = lerp(0.05, 0.6733, (s-4)/6)           for 4.0 ≤ s < 10.0 // LOW sweeps Heat1→Heat2→Heat3
     = 0.6733                                for 10.0 ≤ s ≤ 20.0 // PRODUCTIVE is flat Heat3
     = lerp(0.6733, 1.0, (s-20)/10)          for 20.0 < s < 30.0 // HIGH ramps to Heat4
     = 1.0                                   for s ≥ 30.0
```

Constants: `FRACTION_LOW = 0.05`, `FRACTION_PRODUCTIVE = 0.6733`, `FRACTION_HIGH = 1.0`,
`LOW_MIN_SETS = 4.0`, `PRODUCTIVE_MIN_SETS = 10.0`, `HIGH_MIN_SETS = 20.0`,
`HIGH_SATURATION_SETS = 30.0`.

**D5 — Model shape.** `MuscleLoadSummary` gains `weeklySets: Double`; `band` becomes
`HeatBand.fromWeeklySets(weeklySets)`; `HeatBand` becomes
`{ UNTRAINED, LOW, PRODUCTIVE, HIGH }` (ordinal order enables `band >= PRODUCTIVE`);
`HeatBand.fromHeat` is deleted. `workingSets: Int` keeps its current counting semantics for
display. `BodyHeatSnapshot.load`'s zero fallback (MuscleLoad.kt:90-101) gains `weeklySets = 0.0`.

**D6 — Coach basis.** Recommendations are computed from a fixed trailing-14-day snapshot,
never from the display window. `COACH_TRAILING_DAYS = 14`; basis weeklySets = 14-day
weighted total × 7 / 14. All coach copy that mentions time says exactly `"the last 14 days"`
(or none); it never quotes the display window. Lifetime recency (daysSinceLastTrained) is
still computed over ALL history, exactly as the snapshot does today (MuscleLoadCalculator.kt:40-45).

**D7 — Engine constants.** Deleted: `MIN_VOLUME_FOR_IMBALANCE_KG` (RecommendationEngine.kt:32),
`HIGH_HEAT`, `LOW_REGION_HEAT`, `HIGH_UPPER_COUNT` (:33-35) and `recoverySignal` (:163-182,
superseded by the rest rule). Kept: `NEGLECT_DAYS = 7`, `HIGH_NEGLECT_DAYS = 10`,
`MAX_NEGLECTED = 2`, `IMBALANCE_RATIO = 2.0`, `STRONG_IMBALANCE_RATIO = 3.0`, `MAX_RESULTS = 5`.
New: `MIN_WEEKLY_SETS_FOR_IMBALANCE = 4.0` (heavier side must be ≥ the low-band floor),
`RPE_HOLD_THRESHOLD = 9.0`, `RPE_HOLD_SESSIONS = 2`, `DELOAD_RISE_RATIO = 1.15`,
`DELOAD_WEEKS = 3`, `DELOAD_TOP_LIFTS = 3`, `OWNED_RECENT_DAYS = 60`.
Rank scores: rest = 72, deload = 75 (both HIGH priority; deload suppresses rest when both fire).
Goal modifiers on rankScore: STRENGTH → progression +10, deload +10; HYPERTROPHY →
imbalance +10, neglect +10, core +10; GENERAL → none.

**D8 — RPE rule.** Fires only on an INCREASE hint. Take the top working set
(ProgressionBasis.topWorkingSet) of each of the last 2 finished sessions containing the
lift; if BOTH have non-null RPE and their average ≥ 9.0, the hint becomes
`action = HOLD, suggestedWeightKg = lastWeightKg, rpeHold = true` (new
`ProgressionHint.rpeHold: Boolean = false` field). ProgressionStrip reason for a held hint
is the literal `"Top set at RPE 9+. Hold {weight}."`. Held lifts drop out of
readyForProgression automatically (it filters INCREASE, WorkoutRepository.kt:408).

**D9 — Do-less rules.** Rest: fires when every muscle in `CanonicalMuscle.bodyMapOrder` has
basis band ≥ PRODUCTIVE. Deload: weekly working-volume buckets w0=[now−7d,now),
w1=[now−14d,now−7d), w2=[now−21d,now−14d); volume rising = `w0 > w1 > w2 && w0 ≥ w2 × 1.15
&& w2 > 0`; e1RM stalling = among the top 3 lifts by working-set count in the last 14 days,
no lift's best `PersonalRecords.estimatedOneRepMaxKg` in [now−14d,now) exceeds its best in
[now−28d,now−14d) (lifts lacking a comparable e1RM in either half are skipped; at least one
comparable lift required). Deload fires when both hold.

**D10 — Named lifts (B-03).** Every muscle-targeted card resolves ONE owned lift:
candidates = catalog exercises whose junction PRIMARY muscle is the target; order =
lifts in routines (routines by `updatedAt` desc, then sortOrder) first, then lifts trained
in the last 60 days (most recent first); filtered by available equipment (D11). Resolved →
card carries `actionExerciseId`/`actionExerciseName`, action `OPEN_EXERCISE`, label
`"Open {lift}"`. Unresolved → falls back to `OPEN_LIBRARY_MUSCLE`, label
`"Find {muscle} lifts"` (existing behavior). `RecommendationAction` gains `OPEN_EXERCISE`;
`Route.ExerciseDetail` exists and is the deep-link target (verified AppNav.kt:105-107;
navigate pattern at AppNav.kt:284). The progression card always deep-links its FIRST ready lift.

**D11 — Preferences.** Three new DataStore keys in PreferencesRepository (pattern :216-231):
`stringPreferencesKey("training_goal")` — values `"STRENGTH" | "HYPERTROPHY" | "GENERAL"`,
default GENERAL, decoded tolerantly; `stringSetPreferencesKey("available_equipment")` —
equipment keys from the Phase 3 catalog vocabulary (read the merged seed for the literal
set; do not invent keys), empty set (default) = no filtering; plus `"heat_window"` (D2).
Domain: `enum class TrainingGoal { STRENGTH, HYPERTROPHY, GENERAL }` and
`data class CoachPreferences(val goal: TrainingGoal, val availableEquipment: Set<String>)`.
These three keys are device-local and deliberately excluded from Backup v2 (single user,
single phone; extending the backup document is not this phase's to do).

**D12 — Voice spec.** Card anatomy: kicker (ALL-CAPS category, rendered via the existing
`Kicker` component), title (the fact — names the lift/muscle and the numbers), reason (the
evidence, ≤2 sentences), action label. Rules: no praise, no first person, no exclamation
marks, no quoting the display window, numerals not number-words. Literal templates
(`{n}` = weighted weekly sets formatted "%.0f" when within 0.05 of an integer else "%.1f";
`{inc}` = `ProgressionCalculator.INCREMENT_KG.toWeightLabel(unit)` — the increment TABLE is
Phase 7, keep quoting the existing constant):

| Card | kicker | title | reason |
|---|---|---|---|
| Imbalance | `BALANCE` | `{Light} is behind {Heavy}` | `{Heavy} {h} weighted sets vs {Light} {l} in the last 14 days ({ratio}×). Add {lift}.` (drop last sentence when unresolved) |
| Imbalance, zero side | `BALANCE` | `No {Light} work against {Heavy}` | `{Heavy} has {h} weighted sets in the last 14 days and {Light} has none. Add {lift}.` |
| Neglect | `COVERAGE` | `{Muscle}: {days} days since a working set` | `Last working set was {days} days ago. {Lift} covers it.` |
| Neglect, never | `COVERAGE` | `{Muscle} has no logged work` | `Nothing in history maps to {Muscle}. {Lift} covers it.` |
| Core gap | `COVERAGE` | `No direct core work in the last 14 days` | `Finished sessions in the last 14 days include no core lift. {Lift} covers it.` |
| Progression | `PROGRESSION` | `{Lift}: ready to progress` (or `{Lift} and {k} more: ready to progress`) | `Top set {w}×{r} hit target. Next session add {inc}.` |
| Rest | `RECOVERY` | `Every muscle is at productive volume` | `All mapped muscles are at 10+ weighted sets per week over the last 14 days. Nothing needs adding.` |
| Deload | `LOAD` | `Volume up 3 weeks, e1RM flat` | `Weekly volume rose {pct}% over three weeks while top-lift e1RMs did not move. Schedule a lighter week.` |

`TrainingRecommendation` gains `kicker: String`, `actionExerciseId: String?`,
`actionExerciseName: String?`.

**D13 — Surfaces (final map; 6b conforms too).** Body keeps the full explanation cards
(kicker added). StartWorkoutScreen gets ONE pinned `SUGGESTED` row above "From routine" —
Phase 6b converts the screen to the start-options sheet and MOVES this row; mark it with the
literal comment `// Phase 6b moves this row into the start-options sheet (surface map).`
The in-workout add-exercise sheet gets ONE pinned `SUGGESTED` row. Only cards with a
resolved `actionExerciseId` pin to action surfaces; do-less cards (rest/deload) appear on
Body only. Home is untouched except the `"Last 7 days"` fallback literal (→ `"This week"`)
and passing the new dispatch callback — the Home module rework is 6b.

**D14 — Swap/remove.** Repository methods (both inside `database.withTransaction`, both
`error(...)` on guard violation): `removeExerciseFromSession(sessionId, itemId)` — session
exists & unfinished, and the item's exercise has ZERO logged sets (warm-ups count as sets)
in this session; `swapExerciseInSession(sessionId, itemId, replacement: Exercise)` — same
guards plus replacement not already in the session; delete + insert a new
SessionExerciseEntity preserving `sortOrder`, `targetSets`, `targetReps`, `restSeconds`,
with `targetWeightKg = null`. UI: an overflow `IconButton` (`Icons.Outlined.MoreVert`) on
`CurrentLiftHeader` (doctrine placement, UI_REDESIGN.md:151), visible only while the
selected lift has zero logged sets, offering `Swap lift…` (reopens ExercisePickerSheet in
swap mode) and `Remove lift` (confirm dialog, `Danger` styling). The chip row itself is
unchanged. After remove/swap, selection re-resolves via the
`resolveSelectedExerciseId` contract (Models.kt:91-100).

## 5. Work items

**W1 — Band model in domain.**
Modify: `domain/MuscleLoad.kt`, `domain/MuscleLoadCalculator.kt`, `domain/WeeklySchedulePlanner.kt`.
- Implement D3/D4/D5: `HeatBand { UNTRAINED, LOW, PRODUCTIVE, HIGH }` with
  `fromWeeklySets(sets: Double)` and legendLabels; delete `fromHeat`; add
  `MuscleLoadCalculator.heatFraction(weeklySets: Double): Double`; delete `normalizeHeat`.
  Accumulator gains `windowWeightedSets: Double` (`+= junction weight` per working set —
  ride whatever per-set credit map Phase 3 installed in `mappingFor`); summary built with
  `weeklySets = scale(windowWeightedSets, window)` where scale is D3, `heat = heatFraction(weeklySets)`.
- Planner ripple: `snapshot.load(it).heat` consumers at WeeklySchedulePlanner.kt:248-250
  keep compiling (heat is still 0..1) — leave thresholds; rewrite the now-false relative
  copy at :285 to the literal `"${muscle.displayName} is below productive weekly volume."`
  and change its condition to `load.band <= HeatBand.LOW`.
- ProgressScreen empty-state copy (:98) becomes `"Weekly working sets light the map for the window you pick."`
Tests: `HeatBandTest` (band edges: 3.99→UNTRAINED, 4.0→LOW, 9.99→LOW, 10.0→PRODUCTIVE,
20.0→PRODUCTIVE, 20.01→HIGH), `HeatFractionTest` (every D4 anchor + monotonicity),
`WeeklySetScalingTest` (30-day total×7/30; CURRENT_WEEK unscaled; primary-1.0 +
secondary-weight set credits sum per junction weights).

**W2 — HeatWindow surgery.**
Modify: `domain/MuscleLoad.kt`, `ui/progress/ProgressScreen.kt`, `ui/progress/ProgressViewModel.kt`,
`insights/TrainingInsightsSource.kt`, `ui/home/HomeScreen.kt`, `data/repository/PreferencesRepository.kt`.
Implement D1/D2. The complete break list (grep-verified at `2212628`; re-grep
`HeatWindow\\.` before you start and reconcile against Phase 1a–4 drift):
- `MuscleLoad.kt:12-14` enum members; `:22-33` `startMs` when.
- `ProgressScreen.kt:224` chips loop (survives — `entries`), `:335-340` `pickerLabel` when,
  `:343-346` `sentenceLabel` when — rewrite per D1.
- `ProgressViewModel.kt:18, :32` defaults → `CURRENT_WEEK`; load persisted window on init,
  persist on `setWindow` (D2).
- `TrainingInsightsSource.kt:56` default `flowOf(HeatWindow.LAST_7_DAYS)` → `CURRENT_WEEK`.
- `HomeScreen.kt:415` kicker fallback `"Last 7 days"` → `"This week"`.
- `RecommendationEngine.kt:150, :177, :190-194` window copy — dies in W4's rewrite.
- Existing tests referencing deleted members: `MuscleLoadCalculatorTest` (:25-153),
  `WeeklySchedulePlannerTest:156, :329`, `TrainingInsightsCalculatorTest:31`,
  `HeatWindowTest:49-54, :84`, `RecommendationEngineTest:15, :159`,
  `WorkingVolumeAgreementTest:22` — retarget to CURRENT_WEEK/LAST_30_DAYS.
Mechanical proof: `python3 tools/check-when-exhaustive.py app/src/main/java` clean plus a
zero-hit grep for `LAST_7_DAYS|LAST_14_DAYS` under `app/src` — the when-checker plus the
compiler are the exhaustiveness guarantee; cite both in the PR.
Tests: `HeatWindowTest` rewritten (LAST_30_DAYS start = 30 days back, weekStart behavior
unchanged for CURRENT_WEEK), `WindowPreferenceTest` (`fromStorage`: "LAST_30_DAYS"→LAST_30_DAYS;
"LAST_7_DAYS"/null/garbage→CURRENT_WEEK).

**W3 — Coach decoupling.**
Modify: `domain/MuscleLoadCalculator.kt`, `domain/TrainingInsights.kt`, `insights/TrainingInsightsSource.kt`.
- New pure types + entry (D6): `CoachMuscleLoad(muscle, weeklySets, daysSinceLastTrained)`
  with `band` derived; `CoachBasis(generatedAtMs, loads: Map<CanonicalMuscle, CoachMuscleLoad>,
  hasAnyWorkingSets, hasBasisWorkingSets)` with a zero-default `load(muscle)`;
  `MuscleLoadCalculator.coachBasis(sessions, nowMs, zone, exerciseCatalog): CoachBasis`.
- Compute-chain placement: in `TrainingInsightsCalculator.compute` (TrainingInsights.kt:82-137),
  after the display snapshot, build the basis under `recoverWith` and feed
  `RecommendationEngine.recommend(CoachInputs(...))`; a basis failure maps to
  `InsightFailure.RECOMMENDATIONS` (the display map must not blank — HEAT stays snapshot-only).
- Cost (state it in the code comment): one extra O(total sets) pass per emission, running on
  `computeDispatcher` = `Dispatchers.Default` (TrainingInsightsSource.kt:45, :96) — same
  order as the existing snapshot pass; no caching, no new queries for the basis itself.
- `TrainingInsightsInput` gains `coachPrefs: CoachPreferences` plus the resolver maps
  (`primaryMuscles: Map<String, CanonicalMuscle>`, `equipmentByExercise: Map<String, String>`)
  built from the junction-backed catalog Phase 3 landed — reuse its accessor; only if none
  exists add `ExerciseRepository.observeMuscleCredits(): Flow<...>`. TrainingInsightsSource's
  Sources combine grows accordingly (nest a combine as the outer one already does, :59-71).
Tests: `CoachBasisTest` (14-day boundary: set at 13d23h counts, 14d1h does not; weekly
average = total×7/14; lifetime recency preserved), `CoachDecouplingTest` (identical history
⇒ byte-identical recommendation list under both display windows).

**W4 — Rule upgrades + preferences.**
Modify: `domain/RecommendationEngine.kt`, `domain/Models.kt`, `domain/ProgressionCalculator.kt` (no change — increments are Phase 7),
new `domain/DeloadSignal.kt`, new `domain/OwnedLiftResolver.kt`, new `domain/RpeModifier.kt`,
`data/repository/WorkoutRepository.kt`, `data/local/dao/WorkoutDao.kt`,
`data/repository/PreferencesRepository.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`.
- Engine signature: `fun recommend(inputs: CoachInputs): List<TrainingRecommendation>` with
  `CoachInputs(basis, history, routines, hints, primaryMuscles, equipmentByExercise, goal,
  availableEquipment, unit, nowMs, zone)`. Rules per D7–D10; imbalance reads
  `basis.load(x).weeklySets` (supersedes tonnage; `MIN_VOLUME_FOR_IMBALANCE_KG` deleted);
  neglect/core keep thresholds but read the basis and name lifts; `recoverySignal` deleted;
  rest + deload added; goal rank modifiers applied at rank time.
- `DeloadSignal.detect(history, nowMs, zone): DeloadFinding?` pure, per D9, volume via the
  same per-set rule as `workingVolumeKg` (Models.kt:77-79) bucketed by the calculator's
  `trainedAtMs` attribution (private at MuscleLoadCalculator.kt:110-113 — make it
  `internal` or replicate its candidate order verbatim; do not invent a different rule).
- `OwnedLiftResolver.resolve(muscle, routines, history, primaryMuscles, equipmentByExercise,
  availableEquipment, exerciseCatalog, nowMs): Exercise?` per D10.
- RPE (D8): `WorkoutDao` gains
  `lastFinishedSessionIdsWithExercise(exerciseId, excludeSessionId, limit): List<String>`
  (same WHERE semantics as the existing single-id query at WorkoutDao.kt:106,
  `ORDER BY finishedAt DESC LIMIT :limit` — the existing query orders by
  `ws.finishedAt DESC`, match it exactly);
  `progressionFor` (:259-276) and `readyForProgression` (:394-415) fetch the last 2 sessions'
  top-set RPEs and pass through `RpeModifier.apply`; `ProgressionHint` gains `rpeHold`.
- Preferences (D11): keys + flows + setters in PreferencesRepository;
  `SettingsScreen` gains a `CoachingSection` between SchedulePrefs and RestTimerPrefs
  (SettingsScreen.kt:136-148): `GymSectionHeader("Coaching")`; three goal rows in the
  WeightUnitsSection selected-row pattern (:247-270) — titles `Strength` / `Muscle` /
  `General`, subtitles `Progression and load first` / `Volume and balance first` /
  `No emphasis`; equipment as a wrap of toggle `InstrumentChip`s, one per catalog equipment
  key. SettingsViewModel exposes the flows and setters.
Tests: `ImbalanceBySetsTest` (ratio on weighted weekly sets; 4.0 floor; zero-side variant),
`RpeModifierTest` (9&9→HOLD+rpeHold; avg 8.75→unchanged; one null→unchanged; never fires on
HOLD/DECREASE), `RestSignalTest` (all bodyMapOrder ≥ PRODUCTIVE fires; one LOW blocks),
`DeloadSignalTest` (rising+flat fires; rising+new-e1RM doesn't; flat volume doesn't;
deload suppresses rest), `OwnedLiftResolverTest` (routine beats history; 60-day cutoff;
equipment filter; null fallback), plus goal-modifier ordering assertions inside
`RecommendationEngineTest`.

**W5 — Voice application.**
Modify: `domain/RecommendationEngine.kt` (copy per D12), `ui/progress/RecommendationCards.kt`
(render `Kicker(recommendation.kicker)` as the card's first child; `actionLabel` gains the
`OPEN_EXERCISE → "Open {actionExerciseName}"` branch), `ui/workout/ActiveWorkoutScreen.kt`
(ProgressionStrip held-hint reason literal, D8).
Tests: `RecommendationVoiceTest` — drive the engine across synthetic scenarios that fire
every card type; assert no output (kicker/title/reason) contains any of
`"!"`, `" I "`, `"we "`, `"great"`, `"nice"`, `"good job"`, `"well done"`, `"amazing"`,
`"keep it up"`; assert kickers are uppercase and drawn from the D12 set.

**W6 — Surfaces + swap/remove.**
Modify: `ui/navigation/AppNav.kt`, `ui/progress/ProgressScreen.kt`, `ui/progress/RecommendationCards.kt`,
`ui/home/HomeScreen.kt`, `ui/workout/StartWorkoutScreen.kt`, `ui/workout/StartWorkoutViewModel.kt`,
`ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/ActiveWorkoutViewModel.kt`,
`ui/components/ExercisePickerSheet.kt`, `data/repository/WorkoutRepository.kt`.
- Deep-link: `dispatchRecommendation` (RecommendationCards.kt:68-83) gains
  `onOpenExercise: (String) -> Unit` and the `OPEN_EXERCISE` branch; both call sites
  (ProgressScreen.kt:153-167, HomeScreen.kt:174-181) and both AppNav screen blocks
  (Progress at :257-271, Home at :232-256) pass
  `{ navController.navigate(Route.ExerciseDetail.create(it)) }`.
  `check-screen-wiring.py` + `check-when-exhaustive.py` are the proof no callback dangles.
- StartWorkout pinned row (D13): new item key `"suggested"` above `"routines-label"`
  (StartWorkoutScreen.kt:117), only when `inProgress == null` and a suggestion resolves:
  `Kicker("SUGGESTED")` + `InstrumentRow(title = lift name, subtitle = card reason,
  onClick = viewModel::startSuggested)` + the D13 six-b comment. StartWorkoutViewModel
  observes `container.trainingInsights.observe(includeWeekPlan = false)`, takes the first
  recommendation with `actionExerciseId`, and `startSuggested()` runs the repository's
  `startFreeWorkout(focusTitle = actionMuscle?.displayName)` (verified signature
  `startFreeWorkout(focusTitle: String? = null)`, WorkoutRepository.kt:82) then
  `addExerciseToSession(sessionId, lift)` then emits the existing navigate event.
- Picker pinned row: `ExercisePickerSheet` gains `suggestion: Exercise? = null,
  suggestionReason: String? = null`; renders one pinned row (kicker `SUGGESTED`) above
  results; tap = `onSelect(suggestion)`. ActiveWorkoutViewModel derives it: first
  recommendation with `actionExerciseId` whose lift is not already in the session.
- Swap/remove per D14: harden `removeExerciseFromSession` (currently unguarded and
  un-called — WorkoutRepository.kt:140-142; its current signature is
  `(itemId: String)` alone — replace it with D14's two-arg signature; zero callers exist,
  so the signature change breaks nothing), add `swapExerciseInSession`; ViewModel gains
  `requestSwap()` (sets a `swapTargetItemId`, opens the picker) and `removeSelectedLift()`;
  `addExerciseInternal` (:473-494) branches to swap when `swapTargetItemId != null`;
  CurrentLiftHeader overflow per D14.
Tests: repository guards are exercised in domain tests where extractable
(`SwapRemoveGuardsTest`: remove refused with logged sets / finished session; swap preserves
sortOrder and targets, nulls targetWeightKg, refuses duplicates) — write the guard logic as
a pure `SessionEditRules` object consumed by the repository so it is JVM-testable.

## 6. Out of scope

- The Home "Today" rework: masthead, week strip, ONE next-session module, removal of the
  duplicate heat card / Recent list, Ready-to-progress row deep-links on Home — all Phase 6b.
- Converting StartWorkout into the start-options sheet — Phase 6b (this phase only pins the
  suggestion row into the existing screen and marks it).
- Tab consolidation / Body-absorbs-History — Phase 6a. Touch no tab, no route removal.
- The per-loadType increment table, `WeightUnit.step` reconciliation, BODYWEIGHT add-weight
  suppression, and any change to `INCREMENT_KG` or the "+2.5" copy source — Phase 7 (keep
  quoting `ProgressionCalculator.INCREMENT_KG`).
- Catalog content, equipment vocabulary changes, Library UX, muscle-filter route flip — Phase 7.
- Imagery — Phase 8. Backup document changes — none (D11). Schema changes — none: this
  phase adds no table, no column, no migration.
- LLM/chat coach, muscle-head granularity, day/year windows — recorded non-goals (binding
  brief cut list; ROADMAP's post-Phase-0 not-to-build section).

## 7. Acceptance gate

Run in-session (all must pass before every push):

```
tools/preflight.sh                                      # eight static checks + domain tests: exit 0
python3 tools/check-when-exhaustive.py app/src/main/java  # clean
python3 tools/check-screen-wiring.py app/src/main/java    # clean
grep -rn "LAST_7_DAYS\\|LAST_14_DAYS" app/src              # expect: NO matches
grep -rn "MIN_VOLUME_FOR_IMBALANCE_KG\\|normalizeHeat\\|fromHeat" app/src   # expect: NO matches
grep -rln "removeExerciseFromSession" app/src/main/java   # expect: WorkoutRepository + ≥1 UI/VM caller
```

Owner machine: `./gradlew testDebugUnitTest` green, `./gradlew assembleDebug` builds, then §8.

Domain tests by name (all green in the JVM suite): HeatBandTest, HeatFractionTest,
WeeklySetScalingTest, HeatWindowTest (rewritten), WindowPreferenceTest, CoachBasisTest,
CoachDecouplingTest, ImbalanceBySetsTest, RpeModifierTest, RestSignalTest, DeloadSignalTest,
OwnedLiftResolverTest, RecommendationVoiceTest, SwapRemoveGuardsTest, plus the retargeted
MuscleLoadCalculatorTest / RecommendationEngineTest / TrainingInsightsCalculatorTest /
WeeklySchedulePlannerTest / WorkingVolumeAgreementTest.

## 8. Owner device checklist

1. Body tab: exactly two window chips — `THIS WEEK` and `30 DAYS`. No 7D/14D anywhere.
2. On `THIS WEEK` early in a week, muscles you haven't trained yet show the dark
   "Untrained" tone — no muscle is max-orange just for being the biggest.
3. Flip between the two chips: the numbers change, the **Recommended cards do not**.
4. This is the before/after heat review: compare the map to what you remember. Bands should
   read as truth (10+ weekly sets = the warm "Productive" tone). If a muscle looks wrong,
   note which — that is a review finding, not necessarily a bug.
5. Tap a muscle: the sheet kicker reads `Untrained/Low/Productive/High`.
6. A Recommended card that names one of your lifts opens that lift's detail page on tap.
7. Settings → Coaching: pick a goal; toggle equipment off (e.g. barbell) — cards stop
   naming barbell lifts.
8. Start workout: one `SUGGESTED` row sits above your routines; tapping it starts a session
   with that lift already in it.
9. In a workout, `+ Add lift`: the sheet shows one pinned `SUGGESTED` row.
10. Select a lift with no sets logged: the lift header shows an overflow with
    `Swap lift…` and `Remove lift`. Log one set: the overflow disappears.
11. Swap a zero-set lift: the replacement appears in the same chip position.
12. After two straight sessions of a lift at RPE 9–10 hitting target reps, the in-workout
    strip reads `Top set at RPE 9+. Hold …` and the lift is absent from Ready to progress.
13. Home's Training card kicker never reads "Last 7 days".

Phase closes only on owner sign-off of this checklist (PROTOCOL.md).

## 9. Estimates

- Executor: 4–5 days (W1+W2 ≈ 1.5; W3 ≈ 0.5; W4 ≈ 1.5; W5 ≈ 0.5; W6 ≈ 1).
- Owner: 0.5–1 day — the 13-step device pass plus the band-truthfulness review (step 4),
  which is a judgment call only the owner can make and may trigger one threshold-tuning
  round trip.

## 10. Hand-back

The completion report to the owner must contain:
1. PR link (`claude/phase-5-heat-coach`), with the §7 command outputs pasted verbatim.
2. The as-built voice table (every literal kicker/title/reason template actually shipped),
   diffed against D12 with any deviation justified.
3. The window-surgery break list as executed: every file:line actually touched, and the
   check-when-exhaustive + zero-hit-grep evidence.
4. Domain test tally: new tests by name, total count before/after, all green.
5. A plain-language note for step 4 of the checklist: "your map will repaint — here is what
   each band means and why the old colors were relative", ≤10 lines.
6. Explicit confirmation of the three surface pins (StartWorkout row + 6b marker comment,
   picker row, Body deep-link) and of swap/remove guards, each with its calling file:line.
7. Anything deferred or discovered (e.g. Phase 3's junction accessor differing from
   assumption), flagged for the Phase 6a/6b packet authors.
