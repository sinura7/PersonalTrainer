# ADR-030 — Live workout UI clarity

- **Status:** Accepted
- **Date:** 21 September 2026
- **Related:** [ADR-027](ADR-027-workout-logging-redesign.md) (amends decision 1: main-column order and stats row),
  [ADR-029](ADR-029-coach-engine.md) (Next set card placement), [ADR-004](ADR-004-offline-core-and-entitlements.md)
  (no login gate on logging), [ADR-005](ADR-005-instrument-identity.md)
- **Owner approval:** Allen Hormoz, full-scale plan, 21 September 2026

## Context

The active strength floor (Pull-Up / Upper A style logging) ships honest data and
deterministic coaching, but the composed screen still asks the lifter to read
competing set counts, a three-cell stats strip, coach copy, and set history before
the one job of the moment — enter load and reps, optional effort, and **Log set**.
ADR-027 established the image-led identity and hero numerals; phone checks showed
residual density and duplicate ordinals on the main path.

This ADR records the clarity program (issues I1–I14, target IA, packets A–H).
Implementation proceeds one packet at a time; **Packet A** lands first.

## Goal

On the active strength set logger, each moment has one job: identity and set type,
then the logging diamond (load and reps), then optional RPE, then one primary **Log set**
while resting and advancing stay in the dock. Progressive disclosure moves Last/Best/Volume,
full history, and deep coach material off the logging path without hiding them.

## Non-goals

- Login or entitlement gates on Home, Plan, History, or live logging ([ADR-004](ADR-004-offline-core-and-entitlements.md))
- A sixth tab, Library or Goals as a tab, LLM-authored coaching, schema reset, or
  `debugLiveCode` bumps tied to UX-only packets
- Replacing the ViewModel contract, `WorkoutPrimaryActions`, rest service identity, or
  `Coach.decide` math in clarity packets (presentation and IA only unless a packet says otherwise)
- TalkBack matrix or golden re-record as merge gates (Packet H may collect evidence; not blocking A)

## Principles

1. **One job per moment** — logging, resting, and advancing do not share the same visual band.
2. **One primary CTA while logging** — filled Volt is **Log set** (or edit/save when editing); coach **Apply** is never Volt.
3. **One set-position string** at exercise level — no parallel `Set n of m` and `x/m working sets`.
4. **Progressive disclosure** — stats, full prior session, and long coach traces live behind Details, sheets, or below the diamond path.
5. **Coach serves inputs** — suggestions copy into the draft; they do not log or compete with numerals.
6. **Builder empathy is not the bar** — layout is judged at arm's length on a 360 dp phone, large text, and Obtainium Debug.

## Issues I1–I14

| ID | Problem | Recommendation | Done when |
|---|---|---|---|
| I1 | Multiple regions claim attention before Log set (stats, coach, history, hairlines). | Main column is only identity → type → diamond → RPE → dock commit; supplementary blocks sit below or in Details. | Packet A order matches target IA; no stats row on path; coach after history. |
| I2 | Load and reps are not the obvious spine between identity and commit. | Keep hero numerals (logging diamond) immediately after Working \| Warm-up; no stat row between. | `WeightRepsEditor` follows `SetTypeToggle` with no intervening blocks. |
| I3 | Two set-position strings (`Set 3 of 4` plus `2/4 working sets`). | One exercise-level line: `Working set 2 of 4`, warm-up / extra wording; chip ordinals keep compact `Set n of m`. | Header shows single `SET_CONTEXT`; no `liftSets` progress caption. |
| I4 | Session header progress competes with lift identity. | Defer header/progress redesign to Packet G; do not add second progress readout on identity. | G packet; A does not expand header chrome. |
| I5 | Last / Best / Volume strip clutters the logging path and duplicates history. | Remove three-column stats from main column; Last time may be one quiet tappable line under the title until today’s first set. | No `STATS_ROW` on floor; hint uses `STAT_LAST` when last time applies. |
| I6 | Bodyweight and zero-load columns read like loaded weight hero. | Defer bodyweight hero rewrite to Packet B. | B packet. |
| I7 | Next set / coach card sits in the logging band. | Move coach below set history (above dock); compact card; no scope expansion. | `NextSetRecommendation` composes after `SetHistoryStrip`. |
| I8 | Set history strip always expanded during logging. | Defer collapse / chip budget to Packet E. | E packet. |
| I9 | Rest card and log mode compete for “what happens next”. | Defer rest-vs-log mode policy to Packet F. | F packet. |
| I10 | Primary action hierarchy unclear when recommendation visible. | Log set stays sole filled Volt in dock; Apply stays outline. | Unchanged `WorkoutDock` semantics; A does not Volt Apply. |
| I11 | Warm-up ramp row adds boxes above commit when warm-up selected. | Keep ramp under numerals only in warm-up mode; defer ramp IA to later warm-up packet if needed. | Ramp remains conditional on warm-up draft only. |
| I12 | Too many hairline boxes between identity and commit. | Remove stats hairlines; drop extra dividers around coach/history on main path. | A removes stats dividers; coach/history without leading hairline. |
| I13 | Last-set apply affordance lost without stats row. | Quiet last-time line under title (tap applies) until first set today; full stats in Details / exercise screen. | Header hint wired to `applyLastTimeSet` when `FloorStat.applies` present. |
| I14 | RPE track cramped against numerals and coach. | Add vertical breathing room around RPE (padding and spacing tokens). | `RpeSelector` uses increased vertical spacing (partial in A; sticky policy in D). |

## Target information architecture (strength logging moment)

| Order | Region | Role while logging |
|---:|---|---|
| 1 | Session header | Routine name, session progress line, Finish / overflow — read-only |
| 2 | Exercise identity | Still, equipment, name, **one** set-position line, optional last-time hint, Details |
| 3 | Set type | Working \| Warm-up radio |
| 4 | Logging diamond | Weight and reps (or hold) hero numerals + plates |
| 4a | Warm-up ramp | Only when warm-up selected and plan offers ramp chips |
| 5 | RPE | Optional effort track (hidden with reason for warm-up) |
| 6 | Set history | Today’s chips; current ring; Add set when plan met |
| 7 | Next set coach | Numbers, why, Apply, Why?, evidence chip — supporting |
| — | Dock | Rest / hold / set clock card + **Log set** (72 dp Volt) |

Landscape, editing, and planned-complete states keep the same relative order; copy
 switches to edit / advance verbs without reintroducing the stats strip.

## Ship packets A–H

| Packet | Priority | Issues | Deliverable |
|---|---|---|---|
| **A — Logging diamond** | P0 | I2, I3, I5, I12 (main column), I14 (spacing partial) | Main-column order; stats row removed; unified set line; coach below history; RPE spacing |
| **B — Bodyweight hero** | P1 | I6 | Load-class-aware hero column (reps-first / no false weight hero) |
| **C — Coach strip** | P1 | I7 (full) | Coach layout/copy pass; switcher tap policy if stacked |
| **D — RPE sticky policy** | P2 | I14 (complete) | RPE visibility, scroll, and warm-up reason polish |
| **E — History collapse** | P2 | I8 | Collapsed default, expand to sheet, height budget |
| **F — Rest vs log modes** | P2 | I9 | Clear modes: logging vs resting vs advancing; dock copy |
| **G — Header / progress** | P2 | I4 | Session header and segmented progress without duplicating lift identity |
| **H — Evidence gate** | P3 | I1 (a11y pass) | TalkBack order doc, goldens, accessibility matrix refresh — supplements, does not block A–G merges |

## Decision (Packet A — binding now)

1. **Amend ADR-027 decision 1:** remove Last set · Best set · Volume from the main column;
   stats remain computed (`ExerciseFloorStatsCalculator`) for Details, hints, and tests.
2. **Main-column order** is identity (with `SetOrdinalCopy.exercisePositionLine`), Working \|
   Warm-up, logging diamond, RPE, set history, Next set coach; dock unchanged.
3. **`FloorCompactChrome.statsRowUnderIdentity()` returns false.**
4. Business rules, timers, persistence, and coach math are untouched.

## Consequences

- [FRONTEND_REDESIGN.md](../FRONTEND_REDESIGN.md) workout order must cite ADR-030 where it
  conflicts with ADR-027’s stats-row sentence.
- JVM layout tests and height budgets (`WorkoutFloorRenderTest.LOOP_BUDGET_DP`) must be
  updated when the main column loses height — budgets may ratchet down, never up without cause.
- Packet B–H PRs stack on trunk in table order unless an open PR already owns the same Kotlin paths.

## Review questions

- **Where did Last/Best/Volume go?** Exercise Details and history surfaces; optional last-time hint on identity until first set today.
- **Did chip ordinals change?** No — chips still use `SetOrdinalCopy.draftLine` / `loggedLines`.
- **Is coach gone?** No — it moves below today’s chips so the diamond → RPE → Log path stays clean.
