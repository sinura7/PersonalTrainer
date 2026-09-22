# Live workout clarity — research notes (2026-09-22)

**Scope:** Read-only audit of `trunk` active strength logging (`ActiveWorkoutScreen`, ViewModel, domain, ADRs). No UI changes. Draft PR #372 (Packet A logging diamond) explicitly out of scope.

**Law:** [ADR-027](architecture/ADR-027-workout-logging-redesign.md), [ADR-029](architecture/ADR-029-coach-engine.md), [FRONTEND_REDESIGN.md](FRONTEND_REDESIGN.md) workout contracts, [FloorCompactChrome.kt](../app/src/main/java/com/sinura/personaltrainer/domain/FloorCompactChrome.kt). **No ADR-030** in repo.

**Stale doc warning:** [workout-entry-experience-report.md](workout-entry-experience-report.md) predates ADR-027 (wheels, 64 dp identity, RPE-on-rest-only). Treat as historical; verify against current Kotlin.

---

## Canonical scroll order (strength, lift selected)

1. Session header — back, routine name (portrait) or plan headline (landscape), progress line + segmented bar, Finish, overflow (Switch, Skip, Swap, Remove, notes, summary).
2. Exercise identity — 88 dp still, equipment kicker, name, set context line, working-set count, Details, Working | Warm-up toggle.
3. Stats row — Last set · Best set · Volume (always composed when a lift is selected).
4. Entry — hero weight/reps (or hold time), Plan/Last quick fills, optional warm-up ramp chips.
5. RPE — full 6–10 track (working draft) or warm-up explanation caption (warm-up draft).
6. Next set coach card — only if `microRec` passes gates (see below).
7. Set history strip — saved chips, current-set ring (except while editing or planned complete), Add set when plan met.
8. Dock — rest card / hold or set clock / context row + 72 dp primary.

Overlays (not in scroll): lift switcher, saved-sets sheet, picker, end/discard dialogs, PR banner item, snackbar error/undo when log bar hidden.

---

## Visibility gates (code truth)

| Element | Shown when |
|--------|------------|
| Stats row | Lift selected, session has lifts |
| RPE track | `!draft.isWarmup` (row always composed; warm-up shows caption only) |
| Warm-up ramp | `draft.isWarmup && workingLogged == 0` (ramp list may be empty) |
| Next set card | `entryEnabled && !draft.isWarmup && microRec != null && visibleOnEntry(rec)` |
| Coach null | `firstSet()` returns null if no hint/target weight; `editing`; calculator null |
| `visibleOnEntry` false | `reasonCode` is `LIFT_DONE` or `EDITING` |
| Set history strip | Hidden entirely if no sets, no current mark, no Add set |
| Current-set ring | Hidden while `editingSetId != null` or `plannedComplete` |
| Rest card (idle) | Dock + `showRest`; landscape hides idle card (`hideIdleRest`) → “Timer controls ›” |
| Auto rest after log | Working set, not last prescribed set (`RestTimer.shouldStartAfterLog`) |
| PR banner | Brief moment after log breaks record |
| Log bar / dock | `logBarVisible` or empty-session Add lift or rest-only dock |

**Post-log draft reset:** RPE cleared, warm-up flag cleared to Working (`acknowledgeSave`).

**`rpeIntent`:** `workoutCoachSuggestion` sets `rpeIntent = true`, so draft RPE drives next-set math, not preview-only rows.

---

## Height / accessibility contracts

- Entry loop budget (identity → set history): **868 dp** max at 360 dp width (`WorkoutFloorRenderTest.LOOP_BUDGET_DP`, raised for ADR-029 evidence chip).
- Landscape 640×360: compact header 56 dp; idle rest hidden; `logBudgetDp` must leave ≥96 dp for log (`LandscapeChrome`).
- Nine committed floor goldens: working, warmup, rest, hold, success, error, completion, font20, reduced-motion (`GoldenPageCatalog`).
- Large font (≥1.6): stacked stats, stacked entry wells, dock context wraps.

---

## External pattern anchors (cited in full report)

- **Strong / Hevy:** auto rest after set complete; previous performance prefill; slim rest bar with ±15; monospaced digits ([RepReturn Strong review](https://repreturn.com/strong-app-review/), [Hevy rest timer docs](https://www.hevyapp.com/features/workout-rest-timer/)).
- **Boostcamp:** tap/log triggers bottom countdown; RPE in flow ([boostcamp.app/workout-tracker](https://www.boostcamp.app/workout-tracker)).
- **JuggernautAI:** RPE captured per set; adjusts within session ([JTS help — RPE/RIR](https://help.jtsstrength.com/en/articles/2-all-about-rpe-and-rir)).
- **ScreensDesign synthesis:** rest attached to set that completed; progressive disclosure for history ([screensdesign.com article](https://screensdesign.com/articles/workout-tracker-app-design-examples/)).

---

## Product calls still for owner

1. Is **always-visible idle rest card** worth ~72 dp before first set, vs Strong-style “rest only after log”?
2. Should **Next set** be collapsed by default on first working set when entry already matches plan?
3. Should **stats row** demote on set 1 when Last = “First set” (three cells vs one Last-time tap target)?
4. **Mode-gate** coach + RPE until after set 1 logged, or keep ADR-027/029 persistent card?
5. Approve any future **Packet A** correctness fixes separately from layout/scarcity work.

See agent final report in thread for scorecard, workflow map, self-review, and phased hypotheses.
