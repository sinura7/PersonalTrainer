# Phase 6b — Home "Today" rework

1. **Mission**

Home becomes the answer to one question — "what do I do right now?" (DESIGN_AUDIT §6.1) — instead of a menu of duplicates. The masthead states today's session, the hero starts it in one tap, the week strip mirrors Plan's persisted week, one next-session module replaces the two recommendation slots, and the `StartWorkout` interstitial dies in favor of a start-options sheet. Everything Home loses, 6a already re-homed — onto History under **Branch A (four tabs)**, onto the merged Body under **Branch B (three tabs)**. This phase inherits 6a's branch; it never re-decides it. Separate landing, separate device pass from 6a — by protocol, this phase starts only after the 6a PR merges.

Execution protocol: `docs/gameplan/PROTOCOL.md` binds. Branch `claude/phase-6b-home-today`, one PR, owner sign-off closes. **Execution order (D-A): phase numbers are identifiers, not sequence.** The plan runs 0 → 2 → 1 → 3 → 4 → 5 → 6a → **6b** → 7 → 8, so this packet executes eighth, immediately after 6a merges.

> **Line-number caveat.** Citations verified at commit `2212628`, before Phases 1a–6a landed, and every one of them was *exact* at that commit; the only diff since has been docs-only, so drift will come from Phases 0–6a merging and from nothing else. Phase 1a already deleted `RestRemainingStrip` and the hero's Resume relabel; Phase 4 already extracted the Home hero card to `ui/home/ThisWeekCard.kt` **and renamed the composable `ThisWeekCard`** (`grep -rn "ThisWeekHomeCard" app/src` is one of Phase 4's proof greps and must return nothing by the time you start). Re-anchor by symbol; a missing symbol means an earlier phase already acted — verify, don't re-do.
>
> **Mandatory phase-start re-baseline (D-G, PROTOCOL §6).** Your FIRST commit on this branch is a re-baseline report — docs/PR-body only, before any work item. It states the current trunk tip, which phases merged since `2212628` (6a included, with the branch it executed), the measured domain-test count and test-class count, and every packet literal below that has drifted, each with its verified current value. Drift fully explained by a merged phase is EXPECTED — adopt the new value and continue. Stop only for a mismatch nothing in the merge history accounts for.

2. **Read first**

1. `docs/gameplan/PROTOCOL.md` — protocol.
2. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt` — in full. Masthead + `todayHeadline` (:214-244, :489-496), stat row (:254-286), hero item (:146-168), heat card (:169-185, :387-451, :460-487), ReadyToProgress (:186-194, :320-346), Recent (:195-202, :348-385).
3. `app/src/main/java/com/sinura/personaltrainer/ui/home/HomeViewModel.kt` — in full: the combine (:44-68; `insights.routines` at :52, the progression-ready filter at :56-60), `startSuggestedDay` + `navigateToSession` (:79-97).
4. `ui/home/ThisWeekCard.kt` — the Home hero card, extracted out of ScheduleScreen.kt:405-465 and renamed `ThisWeekCard` by Phase 4 (grep for it) — and Plan's week-strip composable (Phase 4); you extract/reuse the strip.
5. `app/src/main/java/com/sinura/personaltrainer/ui/workout/StartWorkoutScreen.kt` + `StartWorkoutViewModel.kt` — the interstitial being demolished; ResumeBlock (:97-104, :170-183), routine rows + free workout (:106-135), `LIFTS_PREVIEWED = 3` (:247).
6. `app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt` — post-6a state; every remaining `Route.StartWorkout` reference (at 2212628: :95, :234, :268, :316, :319-329) **and Phase 1a's `LIVE_BAR_HIDDEN_ROUTES` set, which names `Route.StartWorkout.path` and therefore stops compiling when you delete the route (WI-5 owns that edit)**.
7. `app/src/main/java/com/sinura/personaltrainer/domain/ScheduleModels.kt` — `SessionFocusKind` (:45-56), `SuggestedTrainingDay` (:64-75), `WeeklySchedulePlan.dayOn/nextTrainingOnOrAfter` (:88-91) — plus Phase 4's ScheduleRepository/derived-week types (grep).
8. `app/src/main/java/com/sinura/personaltrainer/workout/StartTrainingDay.kt` — the start contract incl. Phase 4's pinned-error and resume-or-discard outcomes.
9. `app/src/main/java/com/sinura/personaltrainer/ui/progress/RecommendationCards.kt` (:68-83) and the Phase 5 engine — the next-session module's inputs and the sheet's pinned suggestion.
10. **Branch B only** — 6a's merged `ProgressScreen.kt`: the `content: List<BodyItem>` list and its two anchor targets, `BodyItem.Calendar` and the first `BodyItem.MonthHeader`, plus 6a's comment recording that both indices are exact and may be `-1`. **Branch A** has no such list: `ProgressScreen.kt` was left alone by 6a and `HistoryScreen.kt` hosts the calendar at the top of its own LazyColumn, so there is nothing to anchor to.
11. `docs/ROADMAP.md` § Decisions **D1** and 6a's merged PR body — they state which branch is live. `grep -A5 "### D1" docs/ROADMAP.md` and `grep -n "Chosen option" docs/ROADMAP.md`: Option A = four tabs (Branch A), Option B = three tabs (Branch B). Confirm the app agrees before writing code: `grep -rn "Route.History" app/src/main/java` — hits mean Branch A, silence means Branch B.
12. `docs/DESIGN_AUDIT.md` §6.1 (H-01…H-12, target layout at :272-278); `docs/UI_REDESIGN.md` §6 (:143, :150); `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md` §8 Home + Schedule (:176, :178).

3. **Binding doctrine**

- **UI_REDESIGN §6** (:143): "Masthead: date kicker + today's answer ('PUSH DAY · 4 LIFTS') — never the app's own name. One hero card = today's plan with the screen's only filled button." And (:150): "One start spine — the hero starts today's plan directly; 'other options' opens a sheet…; `Route.StartWorkout` as an interstitial disappears." This phase dispositions that §6 item — recorded, now owned.
- **DESIGN_AUDIT H-01/H-02** (one resume path), **H-03** (masthead), **H-05/H-10** (Home duplicating tabs/History — the demolitions), **H-07** (ready-to-progress hidden without history — preserved), **S-01** (interstitial's third resume path — dies with the interstitial).
- **ONE-LIVE-AFFORDANCE RULE** (settled, Phase 1a): when a session is in progress the LiveSessionBar is the ONLY live-session affordance anywhere, counting docked chrome. Home's hero never shows Resume. Gate wording binds.
- **RECOMMENDATION SURFACE MAP** (settled, D3): Home = ONE next-session module (plan + top recommendation composed, one-line reason); start-options sheet = action surface with one pinned suggestion; Body = explanation cards. Home never shows two recommendation slots. Branch B's mandatory "Last session" row (§4) is a **link row, not a recommendation surface** — it recommends nothing, ranks nothing, and consumes no engine output — so the D3 map is untouched by it.
- **Accent budget** (UI_REDESIGN §8): the hero holds Home's only filled/volt control.
- **REVISED_STRUCTURE Phase 6b** — scope and the 6a/6b split; **item 18** (StartTrainingDay pinned contract: explicit error for dead pins; explicit resume-or-discard when another session is live).

4. **Settled decisions** (do not reopen)

- **BRANCH INHERITANCE — determined, not decided.** 6a executed either **Branch A (four tabs: Home · Body · Plan · History)** or **Branch B (three tabs: Home · Body · Plan)** per D1's signed option; this phase inherits that and never re-opens it. Establish which one you are in before writing code, using §2 item 11's greps, and state it in the first line of your re-baseline commit. Exactly three things in this phase are branch-dependent — the calendar-jump mechanism (WI-4), the "Last session" row (Branch B only), and the device steps that name a screen. Everything else — masthead, week strip, next-session module, StartOptionsSheet, interstitial demolition, hero, demolitions — is **identical in both branches**. Never blend them.
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
- **Calendar-jump mechanism — BRANCH B (three tabs): the route carries it.** `Route.Progress` keeps `path = "progress"` — every `goToTab(Route.Progress.path)` call and plain tab navigation stays a paramless navigate — and gains `val pattern = "progress?section={section}"` plus `fun create(section: String? = null)`; the Body composable re-registers on `Route.Progress.pattern` with `navArgument("section")` (nullable, default null) and passes `initialSection` into ProgressScreen; a one-shot `LaunchedEffect(initialSection)` resolves the target index from 6a's content list and runs `listState.animateScrollToItem(index)` **only when `initialSection` is one of the two known values and the resolved index is `>= 0`** — never on null, so a plain tab tap never scrolls, and never on `-1`, which is what 6a's list returns when the section is absent (no sessions logged yet). The vocabulary is exactly two values, both built by 6a and both consumed here: `"calendar"` → `content.indexOfFirst { it is BodyItem.Calendar }`, `"sessions"` → `content.indexOfFirst { it is BodyItem.MonthHeader }`. Any other value is ignored (no scroll, no crash). The chip navigates like the old Library jump (the deliberate-reset contract formerly at AppNav.kt:244-252): `navigate(Route.Progress.create("calendar")) { popUpTo(start){saveState=true}; launchSingleTop=true; restoreState=false }`. Tab highlighting keeps working because 6a matches tabs on the destination's registered pattern — set the Body tab's `matchPattern = Route.Progress.pattern`. No `isTabRoute` resurrection.
- **Calendar-jump mechanism — BRANCH A (four tabs): a plain tab jump, no route surgery.** The chip calls `goToTab(Route.History.path)` and nothing else. `Route.Progress` is **not** touched: no `pattern` property, no `create(section)`, no `navArgument("section")`, no `initialSection` param on ProgressScreen, no `LaunchedEffect`, no `matchPattern` change, no anchor vocabulary. History's calendar is already the first item of its LazyColumn (HistoryScreen.kt:88-101), so arriving on the tab *is* arriving at the calendar. The copy on the chip is the same in both branches (`"Training calendar ›"`); only the destination differs.
- **BRANCH B — the "Last session" link row on Home is MANDATORY** (D1's mitigation (ii)). One row, tertiary weight, no card: label `"Last session"` with the most recent finished session's title and date as its caption (reuse the `HomeStatRow`'s `state.recentSessions.firstOrNull()` value — no new query); tapping it opens `Route.SessionDetail.create(session.id)` directly, exactly as `RecentSection`'s rows do today (HomeScreen.kt:348-385), which is why `onOpenSession` stays on HomeScreen under this branch (WI-6's param cleanup drops it only under Branch A). It exists because this branch deletes `RecentSection` while the session log sits five sections deep on the merged Body: without it "what did I do last session" costs a tab change plus a long scroll. When the log is empty the row still renders, reading `"All sessions ›"` and targeting `Route.Progress.create("sessions")` — the log's own empty state is the honest destination for a user with nothing logged, and it is what consumes 6a's second anchor. It is a link, not a recommendation surface: no reason line, no engine input, no ranking — the D3 surface map is untouched (§3).
- **BRANCH A — the "Last session" row is NOT built.** History is one tap away and its session list is the second thing on that screen, so the row would be a fourth path to the same place. Do not add it, and do not add any other Home log surface; `onOpenSession` therefore leaves HomeScreen with `RecentSection` (WI-6).
- **`Route.StartWorkout` is deleted** — including its membership in Phase 1a's `LIVE_BAR_HIDDEN_ROUTES`, which WI-5 owns as a recorded amendment to that phase's settled decision 2 (see WI-5; the amendment is legal because the sheet replacing the interstitial is a modal, user-invoked surface, which 6b already records as the exception to ambient-affordance counting). Its behavior moves into `StartOptionsSheet`, a `ModalBottomSheet` (Surface3) hosted by Home AND Body (and, under Branch A, also by History for its session-log empty state — WI-5), driven by `StartWorkoutViewModel` renamed `StartOptionsViewModel` (start/resume logic at StartWorkoutScreen.kt:63-71 and the ViewModel survive intact). Sheet content order: today's slot pinned on top with a `TODAY` kicker (starts via `StartTrainingDay`) → the Phase-5 pinned suggestion row (moves in from the interstitial) → routines (existing rows, StartWorkoutScreen.kt:117-128) → "Free workout" last (:129-134). In-progress state: the ResumeBlock contract (:97-104, :170-183) transfers — the sheet hides all starts and shows "Go to session" + a guarded Discard (item 18's explicit resume-or-discard; routed through the Phase-1a use cases). This modal, user-invoked surface is the recorded exception to ambient-affordance counting; nothing persistent on Home may resume.
- **Hero final form**: the whole card is ONE action — tap starts today's slot via `startSuggestedDay` (HomeViewModel.kt:86-97 → StartTrainingDay); on rest/no-slot/logged days the tap opens the StartOptionsSheet instead. The card never relabels to Resume (Phase 1a already removed it; assert). Below the card, a chevron row `"This week ›"` → Plan tab — the card body itself no longer navigates (the mis-tap trap at HomeScreen.kt:158 / the hero card's whole-card `GymCard(onClick = …)` dies — Phase 4 renamed that composable `ThisWeekCard` and its param `onOpenPlan`).
- **Demolitions**: `TrainingBalanceCard` + `MuscleHeatTile` (HomeScreen.kt:169-185, :387-451, :460-487, incl. the `"Last 7 days"` fallback at :415 — already rewritten in Phase 5; delete whatever remains), `RecentSection` (:195-202, :348-385) — the reflection surface owns both now (Body under Branch B, Body + History under Branch A; the heat card's content is Body's in either branch and the session log is History's under Branch A). Branch B replaces `RecentSection` with the single "Last session" link row, not with a list. `RestRemainingStrip` (:138-145, :288-317): Phase 1a deleted it; verify by grep, do not re-add. `HomeStatRow` (:254-286) stays.
- **Ready-to-progress rows deep-link the named lift**: row tap → `ExerciseDetail(hint.exerciseId)` (`ProgressionHint` is declared at Models.kt:110-118; ProgressionCalculator.kt:39-55 is only the `hint()` factory) replacing `onClick = onStartWorkout` (HomeScreen.kt:333).
- **Home final LazyColumn order**: masthead + stat row → error banner → hero (+ "This week ›" chevron row) → week strip → next-session module → ready-to-progress (when non-empty) → **[Branch B only] "Last session" link row** → calendar-jump chip (tertiary row, `"Training calendar ›"`) — nothing else. The two tertiary link rows sit adjacent at the bottom, in that order, and are the only rows below ready-to-progress. Under Branch A the calendar chip is the sole tertiary row.
- **Branch delta summary** (the complete list — if something is not here, it is identical in both branches): (1) the calendar-jump chip targets History via plain `goToTab` under Branch A and Body via `Route.Progress.create("calendar")` under Branch B; (2) the `Route.Progress` pattern/`navArgument`/`initialSection`/scroll effect/`matchPattern` work exists **only** under Branch B; (3) the "Last session" row exists **only** under Branch B, and with it `onOpenSession` survives on HomeScreen; (4) device steps 7 and 11 name a different screen. Everything else is the same in both branches: `RecentSection` and the heat card leave Home either way (History/Body own that content), and masthead, week strip, next-session module, hero, StartOptionsSheet, interstitial demolition and the `LIVE_BAR_HIDDEN_ROUTES` amendment are unchanged by the branch.

5. **Work items**

**WI-1 — Masthead.** Add `domain/MastheadCopy.kt` with the table above; delete `todayHeadline` (HomeScreen.kt:489-496); HomeViewModel supplies `loggedToday` (the :154-156 logic moves into the ViewModel state) and `liftCount`. Test: `MastheadCopyTest` in the domain suite — one assert per row of the table (all seven states), plus the singular-lift and unresolvable-routine cases, runnable via `tools/run-domain-tests.sh`.

**WI-2 — Week strip.** Extract Plan's strip to `ui/components/WeekStrip.kt` (composable signature: `WeekStrip(week: <Phase-4 derived-week type>, todayEpochDay: Long, onTap: () -> Unit)`); Plan and Home both call it; Home's `onTap` = `goToTab(Plan)`. No new state; it reads the same `insights.weekPlan` Home already holds (HomeViewModel.kt:61). Test: `check-screen-wiring` (onTap wired), existing planner/schedule domain tests untouched.

**WI-3 — Next-session module.** New `NextSessionCard` in `ui/home/` per the settled composition; delete the recommendation slot inside `TrainingBalanceCard` (:432-449) as part of WI-6. The HomeViewModel filter at :56-60 simplifies: Home consumes exactly one recommendation (the engine's top item) — no list. Test: a small pure selector (`nextSessionReason(day, recommendations)`) in `domain/MastheadCopy.kt` or alongside, covered in `MastheadCopyTest`/`NextSessionReasonTest`.

**WI-4 — Calendar jump (and, under Branch B, the log jump).**

*Branch B (three tabs).* Give `Route.Progress` the settled `pattern` property (its `path` stays `"progress"`) and `fun create(section: String? = null)`; re-register the Body composable on the pattern and add `navArgument("section")` (nullable, default null); thread `initialSection` into ProgressScreen; implement the one-shot scroll for the two-value vocabulary (`"calendar"` → `BodyItem.Calendar`, `"sessions"` → first `BodyItem.MonthHeader`), gated on a known value **and** a resolved index `>= 0`, no-op otherwise; set the Body tab's `matchPattern = Route.Progress.pattern`. Add both tertiary rows to Home in the settled order: the "Last session" row (→ `SessionDetail`, or `create("sessions")` in its empty-log state) then the `"Training calendar ›"` chip (→ `create("calendar")`, with the deliberate-reset nav options). Both of 6a's anchors are consumed here; leaving `"sessions"` unwired is an incomplete Branch B.

*Branch A (four tabs).* Add the `"Training calendar ›"` chip to Home wired to `goToTab(Route.History.path)`. Do not touch `Route.Progress`, `ProgressScreen`, the Body tab's `matchPattern`, or anything else in `ui/progress/`; there is no anchor param, no scroll effect, and no "Last session" row in this branch. This work item is one chip and one callback.

Test (both branches): `check-when-exhaustive`/`check-screen-wiring` green; device step 7 in its branch's wording.

**WI-5 — StartOptionsSheet + interstitial demolition.** Build the sheet per settled content; rename `StartWorkoutViewModel` → `StartOptionsViewModel`; move the Phase-5 suggestion row in; wire in-progress state to the Phase-1a Finish/Discard use cases. Delete `StartWorkoutScreen.kt`, `Route.StartWorkout` (:95), and its composable block (:319-329). Retarget every call site (complete enumeration at 2212628 — re-grep after 1a-6a):
- AppNav.kt:234 Home `onStartWorkout` → delete the param; HomeScreen owns sheet visibility (`rememberSaveable`).
- AppNav.kt:268 Body `onStartWorkout` (empty-state + `RecommendationAction.START_WORKOUT` via dispatch, RecommendationCards.kt:78; produced at RecommendationEngine.kt:225) → Body hosts the shared sheet; the callback becomes "show start options".
- The session-log empty-state start (HistoryScreen.kt:71-72 originally) → same sheet, hosted by whichever screen holds that empty state after 6a: **Branch A** — `HistoryScreen`'s own empty state (:68-72), so History becomes a third host of the shared sheet; **Branch B** — the merged Body's session-section empty state, already covered by the Body host above.
- `dispatchRecommendation`'s `onStartWorkout` parameter (RecommendationCards.kt:71, :78) → semantics change to "open start options"; rename the param `onStartOptions`.
- Phase-5 in-workout/add-sheet surfaces: verify none navigate to `Route.StartWorkout` (grep); they shouldn't.
- **`LIVE_BAR_HIDDEN_ROUTES` (AppNav.kt, added by Phase 1a) — this phase owns the edit.** Phase 1a settled the set as `setOf(Route.ActiveWorkout.path, Route.WorkoutSummary.path, Route.StartWorkout.path)` (PHASE_1A settled decision 2). Deleting `Route.StartWorkout` breaks that expression's compile, and no other packet owns it, so **remove `Route.StartWorkout.path` from the set here**, leaving `setOf(Route.ActiveWorkout.path, Route.WorkoutSummary.path)`. Record it in the PR body as a **deliberate amendment to PHASE_1A settled decision 2**, in one line, with this justification: the interstitial that the hidden-route entry protected no longer exists, and its replacement — the `StartOptionsSheet` — is a modal, **user-invoked** surface, which this phase's own settled decisions already record as the exception to ambient-affordance counting. The bar staying visible on the route that hosts the sheet therefore does not violate the one-live-affordance rule: the sheet is not ambient chrome, and while it is open it shows "Go to session" instead of any start. Do not delete or weaken the set itself, and do not touch the bar's rendering, insets, copy or behavior in any other way.

Gate grep: `grep -rn "StartWorkout" app/src/main/java` → only `StartOptions*` names remain.

**WI-6 — Hero final form + demolitions.** Rework `ThisWeekCard` (`ui/home/ThisWeekCard.kt`, extracted and renamed in Phase 4 — the old name `ThisWeekHomeCard` matches nothing by now) to the settled one-action form + chevron row; delete `TrainingBalanceCard`/`MuscleHeatTile`/`RecentSection` and the now-unused Home params (`onOpenLibraryMuscle` leaves Home with the heat card — recommendation dispatch on Home dies with the second slot). Param branch delta: under **Branch A**, `onOpenSession` leaves HomeScreen with `RecentSection`, and `onOpenHistory` is already gone only if 6a removed it — under Branch A 6a kept it, so delete it here along with `RecentSection` (it has no other caller once the section is gone). Under **Branch B**, `onOpenHistory` went in 6a and `onOpenSession` **stays**: the "Last session" row uses it. `check-screen-wiring` + `check-unused-imports` prove the cleanup.

**WI-7 — Device pass.** §7 gate then §8 — separate from 6a's, per the split-landing rule (attacks: scope-MINOR on 5.5 stacking).

6. **Out of scope**

- Anything on Body except (Branch B) the `initialSection` param and its scroll effect, and (both branches) hosting the shared sheet — Body's content and section order are 6a's, frozen. Under Branch A, Body and History are both frozen except for the sheet hosting and the `onStartWorkout` retarget of WI-5.
- Recommendation engine logic, band thresholds, coach copy (Phase 5, done); per-loadType increments (Phase 7).
- Library, catalog, muscle-key contract (Phase 7); imagery (Phase 8).
- LiveSessionBar behavior, finish/discard internals, stale-session policy (Phase 1a, done) — **with one named exception: WI-5's removal of `Route.StartWorkout.path` from `LIVE_BAR_HIDDEN_ROUTES`**, which this phase owns because it deletes the route. That one line is in scope; the bar's rendering, insets, copy, ticker and use-case wiring remain out of it.
- Tab list, tab matching beyond the Body `matchPattern` line (6a, merged).
- Deleting or moving `HomeStatRow`; Settings surfaces; schema/backup anything.

7. **Acceptance gate**

Both branches:

```bash
tools/preflight.sh                                       # green
python3 tools/check-screen-wiring.py app/src/main/java   # cite: hero/sheet/chip/strip callbacks wired
python3 tools/check-when-exhaustive.py app/src/main/java # cite: SessionFocusKind when in MastheadCopy is exhaustive
grep -rn "Route.StartWorkout" app/src/main/java          # expect: no output
grep -rn "LIVE_BAR_HIDDEN_ROUTES" -A2 app/src/main/java  # expect: exactly ActiveWorkout + WorkoutSummary (WI-5 amendment)
grep -rn "TrainingBalanceCard\\|MuscleHeatTile\\|RecentSection\\|RestRemainingStrip" app/src/main/java  # expect: no output
grep -rn "Workout in progress" app/src/main/java         # expect: no output (masthead state 1 rule)
grep -rn "ThisWeekHomeCard" app/src/main/java            # expect: no output (Phase 4 renamed it ThisWeekCard)
```

Branch B only, additionally: `grep -rn "section=\\|initialSection" app/src/main/java` shows the pattern, the `navArgument`, the threaded param and **both** anchor values (`"calendar"` and `"sessions"`) wired — a build with only `calendar` fails this gate (D1 mitigation (i)); `grep -rn "Last session" app/src/main/java` returns the Home link row (mitigation (ii)).

Branch A only, additionally: `grep -rn "initialSection\\|Route.Progress.pattern" app/src/main/java` returns no output (no route surgery in this branch), and `grep -rn "goToTab(Route.History.path)" app/src/main/java` returns the chip.

**Owner-machine gate (D-F).** CI has never executed in this repo (billing block, PROTOCOL §3); no gate line here depends on it. The owner runs, on their machine, and pastes the output into the PR:

```bash
./gradlew testDebugUnitTest assembleDebug                # owner machine; paste the tail into the PR
```

Domain tests by name: `MastheadCopyTest` (all seven states + edge cases), `NextSessionReasonTest`; full pre-existing suite green in both lanes. Once the owner's standing (non-gating) billing errand is done, CI green on the PR is an **additional** check — never a substitute for the pasted owner-machine output.

8. **Owner device checklist**

1. Install over 6a's build. Home shows: date kicker, then today's answer in caps (e.g. `PUSH DAY · 4 LIFTS` if today's slot has a routine) — never the app name, never "Workout in progress".
2. On a rest day (or after Tune makes today rest): masthead reads `REST DAY`; the hero offers options rather than starting a session — tapping it opens the start sheet.
3. On a training day, tap the hero card once → today's session starts and the workout screen opens. No intermediate "Start workout" screen exists anywhere in the app anymore.
4. Back out. The LiveSessionBar appears; Home's masthead shows the day (not the session), the hero does NOT say Resume, and nothing on Home except the bar returns to the session. Tap the hero → the sheet states a session is in progress and offers "Go to session" and Discard; cancel it.
5. The week strip under the hero matches Plan's strip cell-for-cell (open Plan and compare). Tapping the strip lands on Plan.
6. The next-session module shows one card: focus, up to three lift names, one reason line. There is no second suggestion card and no heat tiles or Recent list anywhere on Home (Branch B's single "Last session" link row is not that list — step 11).
7. Tap "Training calendar ›" → **Branch B:** Body opens already scrolled to the calendar. **Branch A:** the History tab opens with the calendar at the top (no scrolling involved).
8. If "Ready to progress" shows: tapping a row opens that exercise's detail, not a start screen.
9. Finish the live session (from the bar): summary → Done → Home. Masthead now reads `TRAINED TODAY`.
10. Process-death check (Developer options → "Don't keep activities"): open the start sheet, background, return — the app restores sanely (sheet may close; nothing crashes; masthead correct). Turn the setting off.
11. **Branch B:** at the bottom of Home, a single "Last session" row names your most recent workout; tapping it opens that session's detail. (With nothing logged it reads "All sessions ›" and lands on Body's session list.) **Branch A:** there is no such row on Home — your log is the History tab, one tap away, with the session list directly under the calendar.
12. With a session live, open the start sheet from Home: the LiveSessionBar stays visible behind the sheet, and the sheet offers "Go to session"/Discard rather than any start — that is the recorded modal exception, and it is still exactly one live-session affordance in ambient chrome.

9. **Estimates**

The branch barely moves this phase — the delta is one chip target, one route pattern and one link row.

- **Branch A (four tabs — D1's recommended default): executor 2-2.5 days** — masthead + strip + module ~1, sheet + interstitial demolition (incl. the `LIVE_BAR_HIDDEN_ROUTES` amendment) ~1, hero + demolitions + proofs ~0.5. **Owner: 0.5-1 day.**
- **Branch B (three tabs): executor 2-3 days** — the same, plus ~0.25-0.5 for the `Route.Progress` pattern work, the two-value anchor vocabulary and the "Last session" row. **Owner: 0.5-1 day.**

Either way the diff is almost entirely `ui/home/` + `ui/workout/` (Branch B adds a few lines of `ui/navigation/` + `ui/progress/`). Estimates are informational (PROTOCOL §4); they gate nothing.

10. **Hand-back**

- PR link; preflight output; the owner-machine `./gradlew testDebugUnitTest assembleDebug` output pasted (CI link only if CI has actually run — additional check, not a gate, D-F).
- The re-baseline report from the first commit (D-G): trunk tip, phases merged since `2212628` (including which branch 6a executed), measured domain-test and test-class counts, and every drifted packet literal with its verified current value.
- The masthead table reproduced with a device screenshot per reachable state (minimum: focus-with-count, rest, trained-today).
- The WI-5 call-site enumeration with each retarget's final file:line; the `grep -rn "Route.StartWorkout"` empty result; and, on its own line, the **recorded amendment to PHASE_1A settled decision 2** — `Route.StartWorkout.path` removed from `LIVE_BAR_HIDDEN_ROUTES`, with the modal-surface justification and the set's final contents pasted.
- Screenshots: final Home top-to-bottom, the start sheet (idle and in-progress states), the calendar jump landing.
- **Which branch this build runs, stated in one line: "Branch A (four tabs)" or "Branch B (three tabs)"**, and confirmation that the calendar-jump target matches it. Branch B additionally confirms that **both** D1 mitigations shipped: (i) the `section=sessions` anchor is wired end-to-end alongside `section=calendar` (name the two `indexOfFirst` targets and the guard), and (ii) the single "Last session" link row is on Home in the settled position, opening SessionDetail — with the explicit statement that it is a link row, not a recommendation surface, so the D3 surface map is unchanged. Branch A states that neither was built, and why (no route surgery; History is one tap away).
- Named test results; `check-screen-wiring`/`check-when-exhaustive` outputs.
- Doc deltas in the PR: ROADMAP 6b marked done; DESIGN_AUDIT H-01/H-02/H-03/H-05/H-10 and UI_REDESIGN §6 "one start spine" recorded as delivered; §9's "not delivered" list amended.
- §8 checklist echoed with observed results; the statement that exactly one live-session affordance exists anywhere, counting docked chrome — verified in step 4.
