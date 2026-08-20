# Phase 6b — Home "Today" rework

1. **Mission**

Home becomes the answer to one question — "what do I do right now?" (DESIGN_AUDIT §6.1) — instead of a menu of duplicates. The masthead states today's session, the hero starts it in one tap, the week strip mirrors Plan's persisted week, one next-session module replaces the two recommendation slots, and the `StartWorkout` interstitial dies in favor of a start-options sheet. Everything Home loses, Body (6a) already owns. Separate landing, separate device pass from 6a — by protocol, this phase starts only after the 6a PR merges.

Execution protocol: `docs/gameplan/PROTOCOL.md` binds. Branch `claude/phase-6b-home-today`, one PR, owner sign-off closes.

> **Line-number caveat.** Citations verified at commit `2212628`, before Phases 1a–6a landed. Phase 1a already deleted `RestRemainingStrip` and the hero's Resume relabel; Phase 4 already extracted `ThisWeekHomeCard` to its own file and retargeted its tap to the Plan tab. Re-anchor by symbol; a missing symbol means an earlier phase already acted — verify, don't re-do.

2. **Read first**

1. `docs/gameplan/PROTOCOL.md` — protocol.
2. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` — in full. Masthead + `todayHeadline` (:214-244, :489-496), stat row (:254-286), hero item (:146-168), heat card (:169-185, :387-451, :460-487), ReadyToProgress (:186-194, :320-346), Recent (:195-202, :348-385).
3. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeViewModel.kt` — in full: the combine (:44-68; `insights.routines` at :52, the progression-ready filter at :56-60), `startSuggestedDay` + `navigateToSession` (:79-97).
4. The extracted `ThisWeekHomeCard` (Phase 4 moved it out of ScheduleScreen.kt:405-465; grep for it) and Plan's week-strip composable (Phase 4) — you extract/reuse the strip.
5. `app/src/main/java/com/sinura/personaltrainer/ui/workout/StartWorkoutScreen.kt` + `StartWorkoutViewModel.kt` — the interstitial being demolished; ResumeBlock (:97-104, :170-183), routine rows + free workout (:106-135), `LIFTS_PREVIEWED = 3` (:247).
6. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt` — post-6a state; every remaining `Route.StartWorkout` reference (at 2212628: :95, :234, :268, :316, :319-329).
7. `app/src/main/java/com/sinura/personaltrainer/domain/ScheduleModels.kt` — `SessionFocusKind` (:45-56), `SuggestedTrainingDay` (:64-75), `WeeklySchedulePlan.dayOn/nextTrainingOnOrAfter` (:88-91) — plus Phase 4's ScheduleRepository/derived-week types (grep).
8. `app/src/main/java/com/sinura/personaltrainer/workout/StartTrainingDay.kt` — the start contract incl. Phase 4's pinned-error and resume-or-discard outcomes.
9. `app/src/main/java/com/sinura/personaltrainer/ui/progress/RecommendationCards.kt` (:68-83) and the Phase 5 engine — the next-session module's inputs and the sheet's pinned suggestion.
10. 6a's merged `ProgressScreen.kt` — the `content: List<BodyItem>` list and its `Calendar` entry (the anchor target).
11. `docs/DESIGN_AUDIT.md` §6.1 (H-01…H-12, target layout at :272-278); `docs/UI_REDESIGN.md` §6 (:143, :150); `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` §8 Home + Schedule (:176, :178).

3. **Binding doctrine**

- **UI_REDESIGN §6** (:143): "Masthead: date kicker + today's answer ('PUSH DAY · 4 LIFTS') — never the app's own name. One hero card = today's plan with the screen's only filled button." And (:150): "One start spine — the hero starts today's plan directly; 'other options' opens a sheet…; `Route.StartWorkout` as an interstitial disappears." This phase dispositions that §6 item — recorded, now owned.
- **DESIGN_AUDIT H-01/H-02** (one resume path), **H-03** (masthead), **H-05/H-10** (Home duplicating tabs/History — the demolitions), **H-07** (ready-to-progress hidden without history — preserved), **S-01** (interstitial's third resume path — dies with the interstitial).
- **ONE-LIVE-AFFORDANCE RULE** (settled, Phase 1a): when a session is in progress the LiveSessionBar is the ONLY live-session affordance anywhere, counting docked chrome. Home's hero never shows Resume. Gate wording binds.
- **RECOMMENDATION SURFACE MAP** (settled): Home = ONE next-session module (plan + top recommendation composed, one-line reason); start-options sheet = action surface with one pinned suggestion; Body = explanation cards. Home never shows two recommendation slots.
- **Accent budget** (UI_REDESIGN §8): the hero holds Home's only filled/volt control.
- **REVISED_STRUCTURE Phase 6b** — scope and the 6a/6b split; **item 18** (StartTrainingDay pinned contract: explicit error for dead pins; explicit resume-or-discard when another session is live).

4. **Settled decisions** (do not reopen)

- **Masthead string table — all seven states.** Headline is `InstrumentType.display`, uppercase (the doctrine literal is caps). Replace `todayHeadline` (HomeScreen.kt:489-496) with a pure domain function `mastheadHeadline(day: SuggestedTrainingDay?, loggedToday: Boolean, liftCount: Int?): String` in a new `domain/MastheadCopy.kt`:

| # | State | Literal |
|---|---|---|
| 1 | Session in progress | **Not a masthead state.** The bar owns live; the masthead renders whichever of states 2–7 describes the day. `"Workout in progress"` is deleted. |
| 2 | Rest day (`day.isRest`) | `"REST DAY"` |
| 3 | Recovery day (`focusKind == RECOVERY`) | `"RECOVERY DAY"` |
| 4 | Focus day, routine resolves | `"<NOUN> · N LIFTS"` (`"· 1 LIFT"` singular), e.g. `"PUSH DAY · 4 LIFTS"` |
| 5 | Focus day, no routine / unresolvable | `"<NOUN>"` alone — the count is dropped, never guessed |
| 6 | No plan (`day == null` / empty week) | `"READY TO TRAIN"` |
| 7 | Already logged today (finished session today, none in progress) | `"TRAINED TODAY"` |

  Nouns per `SessionFocusKind`: UPPER `"UPPER DAY"`, LOWER `"LOWER BODY DAY"`, PUSH `"PUSH DAY"`, PULL `"PULL DAY"`, LEGS `"LEG DAY"`, FULL_BODY `"FULL BODY DAY"` (RECOVERY is state 3). State 7 outranks 2–5; state 1's rule outranks everything.
- **Lift-count source — decided: existing flows, no new query.** `insights.routines` already reaches HomeViewModel (HomeViewModel.kt:52); count = `routines.firstOrNull { it.id == day.routineId }?.exercises?.size` (`Routine.exercises`, Models.kt:22-28). Unresolvable → state 5.
- **Week strip**: Home renders the SAME composable as Plan's strip, extracted to `ui/components/WeekStrip.kt` (if Phase 4 didn't already share it), fed from the Phase-4 derived week (`insights.weekPlan`, rewired to ScheduleRepository in Phase 4). Cells mirror Plan's exactly — no Home-only variant. Any tap on the strip → Plan tab. No per-cell navigation on Home.
- **ONE next-session module** replaces both the heat card's recommendation slot and any second slot: composition = focus title + up to 3 named lifts + one-line reason. Lifts from the slot's routine (`exercises.take(3)`, mirroring StartWorkoutScreen.kt:202-204); reason = the Phase-5 engine's top recommendation reason when one names this session, else `day.reason` (SuggestedTrainingDay, ScheduleModels.kt:72). Card tap = same action as the hero.
- **Calendar-jump anchor mechanism — the route carries it.** `Route.Progress` keeps `path = "progress"` — every `goToTab(Route.Progress.path)` call and plain tab navigation stays a paramless navigate — and gains `val pattern = "progress?section={section}"` plus `fun create(section: String? = null)`; the Body composable re-registers on `Route.Progress.pattern` with `navArgument("section")` (nullable, default null) and passes `initialSection` into ProgressScreen; a one-shot `LaunchedEffect(initialSection)` runs `listState.animateScrollToItem(content.indexOfFirst { it is BodyItem.Calendar })` **only when `initialSection == "calendar"`** (6a's 1:1 content list makes the index exact) — never on null, so a plain tab tap never scrolls. The chip navigates like the old Library jump (the deliberate-reset contract formerly at AppNav.kt:244-252): `navigate(Route.Progress.create("calendar")) { popUpTo(start){saveState=true}; launchSingleTop=true; restoreState=false }`. Tab highlighting keeps working because 6a matches tabs on the destination's registered pattern — set the Body tab's `matchPattern = Route.Progress.pattern`. No `isTabRoute` resurrection.
- **`Route.StartWorkout` is deleted.** Its behavior moves into `StartOptionsSheet`, a `ModalBottomSheet` (Surface3) hosted by Home AND Body, driven by `StartWorkoutViewModel` renamed `StartOptionsViewModel` (start/resume logic at StartWorkoutScreen.kt:63-71 and the ViewModel survive intact). Sheet content order: today's slot pinned on top with a `TODAY` kicker (starts via `StartTrainingDay`) → the Phase-5 pinned suggestion row (moves in from the interstitial) → routines (existing rows, StartWorkoutScreen.kt:117-128) → "Free workout" last (:129-134). In-progress state: the ResumeBlock contract (:97-104, :170-183) transfers — the sheet hides all starts and shows "Go to session" + a guarded Discard (item 18's explicit resume-or-discard; routed through the Phase-1a use cases). This modal, user-invoked surface is the recorded exception to ambient-affordance counting; nothing persistent on Home may resume.
- **Hero final form**: the whole card is ONE action — tap starts today's slot via `startSuggestedDay` (HomeViewModel.kt:86-97 → StartTrainingDay); on rest/no-slot/logged days the tap opens the StartOptionsSheet instead. The card never relabels to Resume (Phase 1a already removed it; assert). Below the card, a chevron row `"This week ›"` → Plan tab — the card body itself no longer navigates (the mis-tap trap at HomeScreen.kt:158 / ThisWeekHomeCard's `GymCard(onClick=onOpenSchedule)` dies).
- **Demolitions**: `TrainingBalanceCard` + `MuscleHeatTile` (HomeScreen.kt:169-185, :387-451, :460-487, incl. the `"Last 7 days"` fallback at :415 — already rewritten in Phase 5; delete whatever remains), `RecentSection` (:195-202, :348-385) — Body owns both. `RestRemainingStrip` (:138-145, :288-317): Phase 1a deleted it; verify by grep, do not re-add. `HomeStatRow` (:254-286) stays.
- **Ready-to-progress rows deep-link the named lift**: row tap → `ExerciseDetail(hint.exerciseId)` (`ProgressionHint` is declared at Models.kt:110-118; ProgressionCalculator.kt:39-55 is only the `hint()` factory) replacing `onClick = onStartWorkout` (HomeScreen.kt:333).
- **Home final LazyColumn order**: masthead + stat row → error banner → hero (+ "This week ›" chevron row) → week strip → next-session module → ready-to-progress (when non-empty) → calendar-jump chip (tertiary row, `"Training calendar ›"`) — nothing else.
- **FOUR-TAB FALLBACK deltas** (only if the Phase-0 record chose four tabs and 6a executed its fallback): the calendar-jump chip targets the History tab — plain `goToTab(Route.History.path)`, NO anchor param, no `Route.Progress` pattern change (History's calendar is already at the top of that screen, HistoryScreen.kt:88-101). Everything else in this phase is identical: Recent and the heat card still leave Home (History/Body own them), masthead/hero/strip/sheet unchanged.

5. **Work items**

**WI-1 — Masthead.** Add `domain/MastheadCopy.kt` with the table above; delete `todayHeadline` (HomeScreen.kt:489-496); HomeViewModel supplies `loggedToday` (the :154-156 logic moves into the ViewModel state) and `liftCount`. Test: `MastheadCopyTest` in the domain suite — one assert per row of the table (all seven states), plus the singular-lift and unresolvable-routine cases, runnable via `tools/run-domain-tests.sh`.

**WI-2 — Week strip.** Extract Plan's strip to `ui/components/WeekStrip.kt` (composable signature: `WeekStrip(week: <Phase-4 derived-week type>, todayEpochDay: Long, onTap: () -> Unit)`); Plan and Home both call it; Home's `onTap` = `goToTab(Plan)`. No new state; it reads the same `insights.weekPlan` Home already holds (HomeViewModel.kt:61). Test: `check-screen-wiring` (onTap wired), existing planner/schedule domain tests untouched.

**WI-3 — Next-session module.** New `NextSessionCard` in `ui/home/` per the settled composition; delete the recommendation slot inside `TrainingBalanceCard` (:432-449) as part of WI-6. The HomeViewModel filter at :56-60 simplifies: Home consumes exactly one recommendation (the engine's top item) — no list. Test: a small pure selector (`nextSessionReason(day, recommendations)`) in `domain/MastheadCopy.kt` or alongside, covered in `MastheadCopyTest`/`NextSessionReasonTest`.

**WI-4 — Calendar-jump anchor.** Give `Route.Progress` the settled `pattern` property (its `path` stays `"progress"`); re-register the Body composable on the pattern and add `navArgument("section")` (nullable, default null); thread `initialSection` into ProgressScreen; implement the one-shot scroll (gated on `initialSection == "calendar"`); set the Body tab's `matchPattern = Route.Progress.pattern`. Add the chip row to Home. FALLBACK: chip = `goToTab(History)`, skip the pattern change entirely. Test: `check-when-exhaustive`/`check-screen-wiring` green; device step 7.

**WI-5 — StartOptionsSheet + interstitial demolition.** Build the sheet per settled content; rename `StartWorkoutViewModel` → `StartOptionsViewModel`; move the Phase-5 suggestion row in; wire in-progress state to the Phase-1a Finish/Discard use cases. Delete `StartWorkoutScreen.kt`, `Route.StartWorkout` (:95), and its composable block (:319-329). Retarget every call site (complete enumeration at 2212628 — re-grep after 1a-6a):
- AppNav.kt:234 Home `onStartWorkout` → delete the param; HomeScreen owns sheet visibility (`rememberSaveable`).
- AppNav.kt:268 Body `onStartWorkout` (empty-state + `RecommendationAction.START_WORKOUT` via dispatch, RecommendationCards.kt:78; produced at RecommendationEngine.kt:225) → Body hosts the shared sheet; the callback becomes "show start options".
- 6a's Body session-section empty-state start (was HistoryScreen.kt:71-72) → same sheet.
- `dispatchRecommendation`'s `onStartWorkout` parameter (RecommendationCards.kt:71, :78) → semantics change to "open start options"; rename the param `onStartOptions`.
- Phase-5 in-workout/add-sheet surfaces: verify none navigate to `Route.StartWorkout` (grep); they shouldn't.

Gate grep: `grep -rn "StartWorkout" app/src/main/java` → only `StartOptions*` names remain.

**WI-6 — Hero final form + demolitions.** Rework `ThisWeekHomeCard` (extracted in Phase 4) to the settled one-action form + chevron row; delete `TrainingBalanceCard`/`MuscleHeatTile`/`RecentSection` and the now-unused Home params (`onOpenHistory` went in 6a; `onOpenLibraryMuscle` leaves Home with the heat card — recommendation dispatch on Home dies with the second slot; `onOpenSession` leaves with Recent). `check-screen-wiring` + `check-unused-imports` prove the cleanup.

**WI-7 — Device pass.** §7 gate then §8 — separate from 6a's, per the split-landing rule (attacks: scope-MINOR on 5.5 stacking).

6. **Out of scope**

- Anything on Body except the `initialSection` param and hosting the shared sheet (its content/order is 6a's, frozen).
- Recommendation engine logic, band thresholds, coach copy (Phase 5, done); per-loadType increments (Phase 7).
- Library, catalog, muscle-key contract (Phase 7); imagery (Phase 8).
- LiveSessionBar behavior, finish/discard internals, stale-session policy (Phase 1a, done).
- Tab list, tab matching beyond the Body `matchPattern` line (6a, merged).
- Deleting or moving `HomeStatRow`; Settings surfaces; schema/backup anything.

7. **Acceptance gate**

```bash
tools/preflight.sh                                       # green
python3 tools/check-screen-wiring.py app/src/main/java   # cite: hero/sheet/chip/strip callbacks wired
python3 tools/check-when-exhaustive.py app/src/main/java # cite: SessionFocusKind when in MastheadCopy is exhaustive
grep -rn "Route.StartWorkout" app/src/main/java          # expect: no output
grep -rn "TrainingBalanceCard\\|MuscleHeatTile\\|RecentSection\\|RestRemainingStrip" app/src/main/java  # expect: no output
grep -rn "Workout in progress" app/src/main/java         # expect: no output (masthead state 1 rule)
./gradlew testDebugUnitTest assembleDebug                # owner machine / CI
```

Domain tests by name: `MastheadCopyTest` (all seven states + edge cases), `NextSessionReasonTest`; full pre-existing suite green in both lanes. CI green on the PR.

8. **Owner device checklist**

1. Install over 6a's build. Home shows: date kicker, then today's answer in caps (e.g. `PUSH DAY · 4 LIFTS` if today's slot has a routine) — never the app name, never "Workout in progress".
2. On a rest day (or after Tune makes today rest): masthead reads `REST DAY`; the hero offers options rather than starting a session — tapping it opens the start sheet.
3. On a training day, tap the hero card once → today's session starts and the workout screen opens. No intermediate "Start workout" screen exists anywhere in the app anymore.
4. Back out. The LiveSessionBar appears; Home's masthead shows the day (not the session), the hero does NOT say Resume, and nothing on Home except the bar returns to the session. Tap the hero → the sheet states a session is in progress and offers "Go to session" and Discard; cancel it.
5. The week strip under the hero matches Plan's strip cell-for-cell (open Plan and compare). Tapping the strip lands on Plan.
6. The next-session module shows one card: focus, up to three lift names, one reason line. There is no second suggestion card and no heat tiles or Recent list anywhere on Home.
7. Tap "Training calendar ›" → Body opens already scrolled to the calendar (fallback build: History opens with the calendar at top).
8. If "Ready to progress" shows: tapping a row opens that exercise's detail, not a start screen.
9. Finish the live session (from the bar): summary → Done → Home. Masthead now reads `TRAINED TODAY`.
10. Process-death check (Developer options → "Don't keep activities"): open the start sheet, background, return — the app restores sanely (sheet may close; nothing crashes; masthead correct). Turn the setting off.

9. **Estimates**

Executor: 2-3 days (masthead + strip + module ~1, sheet + interstitial demolition ~1, hero + demolitions + proofs ~0.5-1). Owner: 0.5-1 day (device pass + review; the diff is almost entirely `ui/home/` + `ui/workout/`).

10. **Hand-back**

- PR link, CI link, preflight output.
- The masthead table reproduced with a device screenshot per reachable state (minimum: focus-with-count, rest, trained-today).
- The WI-5 call-site enumeration with each retarget's final file:line; the `grep -rn "Route.StartWorkout"` empty result.
- Screenshots: final Home top-to-bottom, the start sheet (idle and in-progress states), the calendar jump landing.
- Which IA branch this build runs (three/four tab) and confirmation the calendar-jump target matches it.
- Named test results; `check-screen-wiring`/`check-when-exhaustive` outputs.
- Doc deltas in the PR: ROADMAP 6b marked done; DESIGN_AUDIT H-01/H-02/H-03/H-05/H-10 and UI_REDESIGN §6 "one start spine" recorded as delivered; §9's "not delivered" list amended.
- §8 checklist echoed with observed results; the statement that exactly one live-session affordance exists anywhere, counting docked chrome — verified in step 4.
