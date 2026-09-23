# ADR-027 — Active workout logging screen redesign

- **Status:** Accepted
- **Date:** 18 September 2026
- **Amended:** 23 September 2026 — decision 8 by
  [ADR-032](ADR-032-jvm-evidence-lanes.md): the floor goldens are retired, not
  re-recorded; JVM renders with reachability assertions are the evidence
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

1. **The screen's order is:** session header (back, routine name, `n of N exercises ·
   x of y sets`, one progress segment per lift; in landscape one row, the plan's
   words as its title and no bar, so the log keeps `LandscapeChrome`'s budget);
   exercise identity (88 dp keyed
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
   payload (`70 lb × 10 · RPE 9`). Selected states use a Volt outline on a dim
   Volt tint. Coral stays on the muscle stills. Cyan stays on timing.
7. **Numerals never move their plates.** Whether the − / + plates sit beside a
   hero numeral or beneath it is decided from a fixed widest sample, not the
   live value; large system text stacks the two columns
   (`LogLoopScale.stackEntryWells`).
8. **Native evidence is re-recorded, not skipped.** The nine floor goldens under
   `app/src/androidTest/assets/goldens` describe the F3 composition and must be
   re-recorded on `temper-tests-api29` before the emulator lane is read as green
   again. JVM renders (`WorkoutFloorRenderTest`) are review artifacts, not goldens.
   *Amended 23 September 2026 by [ADR-032](ADR-032-jvm-evidence-lanes.md):* the
   goldens are retired rather than re-recorded, and the JVM render set is the
   evidence.
9. **Last time is reachable, but no longer a strip.** The F2 floor listed every set
   of the previous session as tappable chips. The reference has no such row, so:
   the Last set cell shows last time's final set and applies it on tap until the
   first set of today lands; the routine's planned load and last time's load
   return as one-tap `Plan` / `Last` fills under the weight numeral only while the
   entry holds something else (`FloorWeightPresets.quickFills`); the rest of last
   session lives behind Details. The commit's verb stays short in every
   orientation: portrait draws the next lift's name on the commit's capped
   supporting line, landscape only speaks it.

## Amendment — 19–20 September 2026, the density, copy and plate pass

The owner placed the shipped screen beside the reference and asked for it to be
scaled down to match. Measured at equal width, ours ran 1,299 dp of content
against the reference's 698. Five things in this record move; the rest is
unchanged, and the measurements are recorded here so they are not re-derived.

1. **The keyed still is 88 dp, not 112.** Decision 1 above is amended. The words
   beside the still measure 110 dp and are what set that row's height, so the
   larger picture was buying about 2 dp of nothing. At 88 dp the exercise's name
   gets 24 dp more width (`ExerciseHeader` sizes the words as
   `maxWidth - exerciseHeroImage - space3`) and a long name wraps a line less
   often, which is where the height actually comes back.

2. **The `− / +` plates stay at 48 dp, and the entry block stays two rows.**
   The reference's entry block is 63 dp against ours at 176. The whole gap is
   the plate row, and it cannot be closed by rearranging: `WeightRepsEditor`
   puts the plates beside the numeral only when
   `sampleWidth + (stepperRound + space2) * 2 <= availableWidth`, and at 412 dp
   the weight column is 182 dp while the widest sample plus two plates needs
   284 dp. That holds at every size in the numeral ramp — even `numeralMd`
   (24 sp) leaves it 4 dp short. The reference fits because its plates are
   roughly 28–32 dp across. `Metrics` sets 48 dp as the floor and says density
   gains are never taken out of it; the owner chose the floor over the
   proportions, and the height came out of the headings, the source-label
   caption and the gaps instead. **Do not reopen this by shrinking a touch
   target.**

Also settled, and deliberately *not* changed: the `Plan` / `Last` quick fills
stay (decision 9); `Add set` still arrives once the planned sets are done
(decision 1), because a mid-plan tap would have been a no-op beside the
`Current` marker; and the fixed-widest-sample plate rule (decision 7) is
untouched.

3. **The progress line reads `n of N exercises · x of y sets`, set in the
   instrument-label voice.** Decision 1 above is amended. The noun goes last so
   the two counts read as the same shape rather than a heading followed by a
   count, and it is singular for a one-lift session. The line was prose voice at
   12 sp and is now the uppercase `kicker` at 11 sp — the same register as REST
   or LAST 7 DAYS, which is what a meta label over the plan is.

4. **The shown pound unit is `lb`.** `WeightUnit.LBS.suffix` and its
   `displayName` change; **`storageKey` stays `lbs`**, because it is what a
   backup file carries and what `fromStorage` reads back. The two are
   deliberately different strings and must never be reconciled.

5. **The − / + plates are drawn smaller than they are pressed.** Amends item 2
   above, which settled that the plates stay at 48 dp. They still do — but only
   as the *target*. The circle draws 36 dp inside it
   (`Metrics.stepperPlateInset`), with a lighter fill (`Surface3`), a harder
   edge (`HairlineStrong`) and a heavier glyph, because the owner asked for a
   plate that reads as the point of the moment rather than a quiet neighbour.
   The floor `Metrics` states is intact: nothing about where a thumb may land
   changed. `StepperButton` keeps one box when no inset is asked for, so every
   other caller composes exactly as before. **This is not licence to shrink the
   target later** — the arithmetic in item 2 still holds, and the reference's
   proportions still need the numerals to come down, which they have not.

`WorkoutFloorRenderTest.theEntryLoopStaysWithinItsHeightBudget` now measures the
loop end to end at 360 dp — identity top to set-history bottom — and holds it at
or under 840 dp. It was 920.5 dp before this pass and is 837.0 dp after. The
number may be lowered; it may not be raised to make a change fit.

## Consequences

- `CurrentLiftCard`, `WorkoutLiftCard`, `LogBar`, `SelectedLiftDock` and
  `LoggedSetsPanel` are gone; their behaviour lives in `WorkoutHeader`,
  `ExerciseHeader`, `ExerciseStatsRow`, `WeightRepsEditor`, `RpeSelector`,
  `NextSetRecommendation`, `SetHistoryStrip`, `RestTimerCard` and `WorkoutDock`.
- Presentation tests assert the new order and copy; ViewModel and domain tests
  are unchanged in intent. Source-text tests point at the new files.
- Large system text (1.6 and above) stacks the hero numerals, the stats row, the
  next-set card's numbers over its change, drops Details under the identity and
  the rest controls under the clock; the header's title keeps to one line and its
  progress line may wrap once. The commit's verb stays short in every orientation:
  once the planned sets are done it says `Next exercise` and speaks the next lift's
  name in full; portrait also draws the name on the supporting line, capped at two
  lines, and landscape draws only the verb, so a long name can never grow the dock
  into the floor.
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
