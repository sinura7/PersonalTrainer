# ADR-027 — Active workout logging screen redesign

- **Status:** Accepted
- **Date:** 18 September 2026
- **Amends:** [ADR-026](ADR-026-frontend-redesign.md) decision 3 (the compact
  64 dp identity and its order) and the *Workout contracts* order in
  [FRONTEND_REDESIGN.md](../FRONTEND_REDESIGN.md). ADR-026 decisions 2, 4 and 8
  are untouched: business rules, no automatic advancement, native evidence.
- **Does not supersede:** [ADR-005](ADR-005-instrument-identity.md) (one filled
  Volt, tokens, tabular numerals), [ADR-008](ADR-008-deterministic-rules.md)
  (the recommendation is the deterministic coach's), [ADR-012](ADR-012-rest-and-reminders.md)
  (the rest clock is the service's; no overlay clock), [ADR-023](ADR-023-palette-and-reduced-motion.md).
- **Related:** owner decision and reference image, 18 September 2026;
  [workout-entry-experience-report.md](../workout-entry-experience-report.md) §11
  (bones to retain).

## Context

The owner supplied a reference image of the target logging screen and asked for
a production-quality re-engineering of the workout floor rather than a reskin: a
precision-instrument layout with the two entry numerals as the loudest elements,
a compact image-led exercise identity, the session's progress in the header, a
Last set · Best set · Volume row, a segmented RPE track, a next-set card with Why
and Apply, today's sets as a strip of chips, the rest clock as its own quiet card,
and a two-line commit that names its payload — with every business rule, timer
identity, persistence path and recommendation engine left exactly where it is.

F0–F3 had already rebuilt the floor around a 64 dp identity, stacked plate
steppers, a latest-saved receipt row and a 56 dp rest bar. The reference keeps
that packet's state model and derived primary action and changes the composition.

## Decision

1. **The screen's order is:** session header (back, routine name, `Exercise n of N ·
   x of y sets`, one progress segment per lift); exercise identity (112 dp keyed
   still, equipment, name, set ordinal, working count, Details, Working | Warm-up);
   Last set · Best set · Volume (this exercise); weight and reps (or hold time) as
   two hero numerals with round − / + plates, the unit riding the weight numeral's
   baseline; RPE 6–10 with Easy / Max effort ends and help; Next set (numbers,
   change, one-line reason, Why, Apply); set history chips with the current set
   ringed and Add set once the plan is met; the dock with the rest card or
   hold/set clock and the 72 dp commit.
2. **Every number is derived from saved rows.** Progress is
   `WorkoutProgressCalculator`: only a prescribed lift has a plan, so a free lift's
   sets count as done but never as planned, and a free lift fills its segment once
   worked but never counts as complete. The stats row is
   `ExerciseFloorStatsCalculator`, whose Best set is the standing-records rule
   (`PersonalRecords.bests`, most reps where reps are the measure, else estimated
   1RM, else heaviest) over finished sets from other sessions plus today's working
   sets, holds excluded on both sides, and says `Today` only when a standing record
   was beaten this session; volume follows `WorkoutSession.work` (working sets
   only, holds are time). No hardcoded values.
3. **The ViewModel's contract is unchanged** except for one read-only flow,
   `exerciseHistory`, loaded with the prefill. `WorkoutPrimaryActions.derive`,
   `performPrimary`, logging, editing, undo, `WorkoutAdvance`, the rest gateway,
   `Coach.decide` and `IncrementTable` are called, not copied.
4. **Apply copies the next set into the entry and never logs.** Once the entry
   matches (`SetMicroRec.isApplied`: the same coercions Apply writes, the weight
   compared at display precision, the effort matched exactly), the control reads
   Applied and stands down. Why opens the rule trace. A suggestion is never
   rendered as a selection, and its change line is plain ink, not Volt.
5. **The rest card is the service's clock at three moods** — running (cyan,
   draining ring, −15 / +15 / Skip), done (gold flash, `Back to the bar`), and at
   rest (dim, planned length, Start rest). Duration editing stays in the sheet;
   holds and the set stopwatch keep the 56 dp instrument bar; modes never stack.
6. **Log set stays the one filled Volt** and gains a second line naming its
   payload (`70 lbs × 10 · RPE 9`). Selected states use a Volt outline on a dim
   Volt tint. Coral stays on the muscle stills. Cyan stays on timing.
7. **Numerals never move their plates.** Whether the − / + plates sit beside a
   hero numeral or beneath it is decided from a fixed widest sample, not the
   live value; large system text stacks the two columns
   (`LogLoopScale.stackEntryWells`).
8. **Native evidence is re-recorded, not skipped.** The nine floor goldens under
   `app/src/androidTest/assets/goldens` describe the F3 composition and must be
   re-recorded on `temper-tests-api29` before the emulator lane is read as green
   again. JVM renders (`WorkoutFloorRenderTest`) are review artifacts, not goldens.
9. **Last time is reachable, but no longer a strip.** The F2 floor listed every set
   of the previous session as tappable chips. The reference has no such row, so:
   the Last set cell shows last time's final set and applies it on tap until the
   first set of today lands; the routine's planned load and last time's load
   return as one-tap `Plan` / `Last` fills under the weight numeral only while the
   entry holds something else (`FloorWeightPresets.quickFills`); the rest of last
   session lives behind Details. In landscape the commit's verb stays short and the
   next lift's name rides its second line.

## Consequences

- `CurrentLiftCard`, `WorkoutLiftCard`, `LogBar`, `SelectedLiftDock` and
  `LoggedSetsPanel` are gone; their behaviour lives in `WorkoutHeader`,
  `ExerciseHeader`, `ExerciseStatsRow`, `WeightRepsEditor`, `RpeSelector`,
  `NextSetRecommendation`, `SetHistoryStrip`, `RestTimerCard` and `WorkoutDock`.
- Presentation tests assert the new order and copy; ViewModel and domain tests
  are unchanged in intent. Source-text tests point at the new files.
- Large system text (1.6 and above) stacks the hero numerals, the stats row, the
  next-set card's numbers over its change, drops Details under the identity and
  the rest controls under the clock; the header's progress line may wrap once.
- Milestone A's phone checklist gains the reference screen; the floor goldens
  are owed a re-record on the emulator profile before they gate anything.
- No schema, backup, signer, tab or navigation change is authorised by this record.

## Review questions

- Does Apply log the set? No. It fills the entry; Log set is still the only write.
- Does the screen count on its own? No. The rest card reads the timer service and
  sends it commands; the hold and set clocks are unchanged.
- May a lift auto-advance after its last planned set? No (ADR-026 §4). The
  primary becomes Next exercise and waits for a tap; Add set stays explicit.
- Is Best set a new definition? No. It is the standing-records headline rule
  applied to this lift, with today's sets in the running.
- Are the old F3 goldens still valid evidence? No. They are stale until
  re-recorded; the JVM render lane is for review, not pixel gating.
