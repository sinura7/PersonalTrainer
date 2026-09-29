# ADR-029 — In-workout CoachEngine and evidence-backed suggestions

- **Status:** Accepted
- **Date:** 21 September 2026
- **Amended:** 25 September 2026 — W2c: a call's trace time does not make
  the coach run again (see Consequences); 27 September 2026 — W2e: the
  coach follows the unit and a week marked lighter while a lift is open,
  and an entry nobody touched follows it (owner decision of 25 September;
  see Consequences)
- **Related:** [ADR-004](ADR-004-offline-core-and-entitlements.md),
  [ADR-008](ADR-008-deterministic-rules.md), [ADR-020](ADR-020-warmup-extras.md),
  [ADR-025](ADR-025-goal-thresholds.md), [ADR-027](ADR-027-workout-logging-redesign.md)

## Context

Temper already ships a local in-set rule ladder ([`Coach` / `SetMicroRecCalculator`](../../app/src/main/java/com/sinura/personaltrainer/domain/Coach.kt))
with a structured [`RuleTrace`](../../app/src/main/java/com/sinura/personaltrainer/domain/RuleTrace.kt) and a **Next set** card on the active
strength workout ([ADR-027](ADR-027-workout-logging-redesign.md)). The product
direction is a constant gym-floor coach: set-to-set load and rep guidance,
session-open warm-up ladders, and explanations grounded in peer-reviewed
literature — not an LLM author and not a network prerequisite ([ADR-008](ADR-008-deterministic-rules.md)).

Recording, history, and live logging stay free and offline ([ADR-004](ADR-004-offline-core-and-entitlements.md)).
Commercial upgrade may later gate cloud sync or narrative API over an existing
trace; it must not gate Home, Plan, History, or the logger.

## Decision

1. **`CoachEngine` is the named policy façade** for in-workout strength coaching
   v1. It delegates load/rep/rest math to the existing [`Coach.decide`](../../app/src/main/java/com/sinura/personaltrainer/domain/Coach.kt)
   ladder (no parallel progression engine). It returns a **`CoachSuggestion`**
   value: target weight (kg), reps, optional RPE, short explanation, literature
   evidence ids, and the same [`RuleTrace`](../../app/src/main/java/com/sinura/personaltrainer/domain/RuleTrace.kt) as today.

2. **Evidence is curated on-device.** A versioned seed catalog
   ([`EvidenceCatalog`](../../app/src/main/java/com/sinura/personaltrainer/domain/coach/EvidenceCatalog.kt),
   human mirror [`docs/coach/evidence-seed.json`](../coach/evidence-seed.json))
   holds real peer-reviewed entries with DOI where one exists, year, short claim,
   and policy rule ids that may cite them. Rules that cannot be tied to a real
   source are marked **`heuristic: true`** with **no DOI**. The app must never
   invent papers or fake DOIs.

3. **Within-session policy stays RPE-reactive** on the existing reason codes
   (`IN_TANK`, `RPE_HOLD`, `FAILED_DROP`, …). Session-open warm-up ladders
   continue to use [`WarmupRamp`](../../app/src/main/java/com/sinura/personaltrainer/domain/WarmupRamp.kt)
   off the last completed working load. [`CoachPreferences`](../../app/src/main/java/com/sinura/personaltrainer/domain/CoachPreferences.kt)
   (goal, emphasis, equipment) remain inputs to generators and weekly rules;
   v1 next-set math is unchanged, with evidence copy that can reflect goal where
   already wired.

4. **UI:** The active strength workout keeps a persistent **Next set** card.
   It shows numbers, a one-line why, **Apply**, **Why?**, and a **Based on …**
   citation chip when literature ids are present. Tapping the chip or Why opens
   detail including DOI or an honest heuristic label. Apply copies into the draft
   only; it never logs silently ([ADR-008](ADR-008-deterministic-rules.md)).

5. **Offline-first:** Catalog lookup and policy run in `domain/` with no network.
   No LLM and no remote feature flag for gym math.

6. **Entitlements (future):** Optional paid surfaces may include multi-device sync,
   crowd learning, or an API that narrates an existing `RuleTrace`. Coach
   suggestions on the personal/debug build are visible without login. A product
   flag may hide suggestions in a store build later; core logging stays free.

## Consequences

- New literature is added by extending the seed catalog and rule→evidence map,
  not by editing locked micro-rec numbers in the same packet unless a separate
  ADR amends thresholds.
- v2 follow-ups (explicitly out of this ADR): ingestion UI for new papers,
  cardio coach, session-level macro pacing, commercial gate, multi-user learning.
- Privacy: the evidence library is static assets; no new telemetry is required
  ([`docs/PRIVACY.md`](../PRIVACY.md)).
- The clock is not a rule input (*amended 25 September 2026, W2c, audit C-2*).
  `nowMs` and `todayEpochDay` reach only the `RuleTrace` (`generatedAtMs` and
  the evidence day), so two asks that differ only in the clock get the same
  call. The Log's Next card keys its ask on the inputs with the clock at zero
  plus `CoachPreferences` (`CoachKey`); the rest page's Next line on that key
  and the rest of what its floor is drawn from (the session, the lift it
  shows, the entry's warm-up flag, a save the Log holds, the unit). Neither
  asks again on a weight step without RPE or on a second of rest. The ask
  itself uses the clock as it is then, so the trace still carries a real time
  ([ADR-008](ADR-008-deterministic-rules.md) decision 2): the time the inputs
  last changed. Held by `CoachRecomputeTest`. `LogNextCardInputsTest` changes
  the coach goal, a lighter week, the hint, the unit and the lift's targets
  one at a time, and `RestFloorInputsTest` the session, the lift, a warm-up,
  a held save and the unit; each holds its screen to the change.
- The unit and the week's mark reach the coach while a lift is open
  (*amended 27 September 2026, W2e, owner decision of 25 September 2026*).
  A lift's hint, its first-set suggestion from last session
  (`WorkoutRepository.progressionFor`, still the only path to a suggested
  weight), is read under a unit and a lighter-week flag. The Log and the
  rest page read both once, when a lift was opened, so a unit pulled in by
  sync or a week marked lighter reached a lift's first set only when the
  lift was opened again, and its in-set call only through the Log's next
  load. Both screens now watch the two (`ProgressionHintLoader.settings`)
  and, when either changes, read the open lift's hint again: one history
  read, and nothing else. The lighter flag reaches the in-set call at once.
  An answer for a lift the screen has left is dropped (the Log's per-lift
  check; the rest page's `readsFor`). A failed re-read keeps the old hint
  and is logged. Last session, history, the planned rest of the dock and of
  the rest page (seeded once, [ADR-012](ADR-012-rest-and-reminders.md)), a
  set open for correction, a held save, a warm-up, a running set clock and a
  set just saved stay as they were.
  The entry follows too, only while nobody has touched it: "untouched
  pre-filled numbers follow the new suggestion; typed numbers stay".
  Untouched means: not changed by hand (`draftDirty`: a typed or stepped
  number, reps, Warm-up, a ramp chip, Last time, Use and a
  correction saved all count; an effort does not, since 29 September 2026,
  P2a: every working set carries one, and a follow moves only the weight), no working set of that lift logged in the
  session (a set just saved, of any lift and a warm-up too, holds every
  follow back until the workout shows its row), not a warm-up, no set open
  for correction, no save in flight or waiting for Retry, no set clock
  running or stopped with its time not yet logged (the numbers just lifted),
  and not a hold (a hold's hint counts reps, which the coach does not coach,
  audit DM-1). A follow refused at that moment waits for the next change or
  the next time the lift is opened. A lift whose suggestion could not be read
  when it was opened is not read again in a live Log; the next reopen reads
  it. Such an entry holds what the load filled in, the hint or,
  with none, the routine's weight, so it takes the hint's weight whenever a
  new one is read: in a live Log when the unit or the mark changes, and
  whenever the Log opens a lift whose entry it recovered (Back and the live
  bar, a lift switched back to, a process death), whatever changed the
  suggestion meanwhile. Only the weight moves, and only to a new number: a
  lift with no history keeps its routine weight exactly, and a stored
  weight is never re-rounded for the new unit. Held by
  `LogFollowsSettingsTest`, `ReopenFollowsSettingsTest`,
  `RestPageFollowsSettingsTest` and
  `LogNextCardInputsTest.aWeekMarkedLighterReachesTheLogsCallWithoutOpeningItsLiftAgain`.
  The week turning over while a lift is open is the clock, not a write, and
  is not watched.

## Review questions

- May v1 call an LLM for “why”? No. Narration may only wrap `RuleTrace` + cited
  evidence after opt-in ([ADR-008](ADR-008-deterministic-rules.md)).
- May coach suggestions require sign-in? No ([ADR-004](ADR-004-offline-core-and-entitlements.md)).
- What if a rule has no paper? Mark heuristic; do not attach a DOI.
