# ADR-027 — Active workout logging screen redesign

- **Status:** Accepted
- **Date:** 18 September 2026
- **Amended:** 23 September 2026 — decision 8, with the consequence and review
  question that repeat it, by [ADR-032](ADR-032-jvm-evidence-lanes.md): the
  floor goldens are retired, not re-recorded, and packet X3 removed them; JVM
  renders with reachability assertions are the evidence
- **Amended:** 23 September 2026 — decision 7 and the density pass's item 5,
  by packet W1d on the owner's decisions of 23 September 2026: a value wider
  than the numeral's sample steps its size down on the sample's line, and the
  − / + glyph is drawn at a fixed size inside its plate
- **Clarified:** 8 October 2026 — the density-budget paragraph records the
  later adopted CoachEngine and P1 changes; the current ratchet remains 852 dp
- **Amended:** 8 October 2026 — native Quiet composition, authorized under
  [O12](../ux-context/README.md#owner-decisions-for-this-project); implementation
  and agent verification proceed on the reviewed Debug-124 stack
- **Amends:** [ADR-026](ADR-026-frontend-redesign.md) decision 3 (the compact
  64 dp identity and its order) and the *Workout contracts* order in
  [FRONTEND_REDESIGN.md](../FRONTEND_REDESIGN.md). ADR-026 decisions 2, 4 and 8
  are untouched: business rules, no automatic advancement, native evidence.
- **Does not supersede:** [ADR-005](ADR-005-instrument-identity.md) (one filled
  Volt, tokens, tabular numerals), [ADR-008](ADR-008-deterministic-rules.md)
  (the recommendation is the deterministic coach's), [ADR-012](ADR-012-rest-and-reminders.md)
  (one service-owned rest clock, including its later exterior presentations),
  [ADR-023](ADR-023-palette-and-reduced-motion.md).
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
   *Amended 23 September 2026 (packet W1d):* the numeral's size still comes
   from the sample, and only a value wider than the sample steps down the
   ramp (`numeralLg`, then `numeralMd`; `numeralMd` scaled to the room only
   past that), so every digit and the unit are laid out whole. The field
   keeps the sample's line, so the plates beneath do not move. Before this,
   99,999.99 kg at 360 dp and font 2.0, or 1102.5 lb at 412 dp, took the
   whole field and left the unit no width (`LogLoopScale.fittedNumeral`).
8. **Native evidence is re-recorded, not skipped.** The nine floor goldens under
   `app/src/androidTest/assets/goldens` describe the F3 composition and must be
   re-recorded on `temper-tests-api29` before the emulator lane is read as green
   again. JVM renders (`WorkoutFloorRenderTest`) are review artifacts, not goldens.
   *Amended 23 September 2026 by [ADR-032](ADR-032-jvm-evidence-lanes.md):* the
   goldens are retired rather than re-recorded, and the JVM render set is the
   evidence. Packet X3 removed them.
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
   *Amended 23 September 2026 (packet W1d):* the glyph is drawn at a fixed
   size in the 36 dp circle, its style's design size read as dp
   (`LogLoopScale.fixedGlyph`), in the circle's whole height rather than a
   padded 20 dp slice. Text that grew with the system font was cut to "_" or
   "." at font 1.6 and 2.0, and at 2.0 the − drew nothing. The 48 dp target
   and the inset are unchanged.

`WorkoutFloorRenderTest.theEntryLoopStaysWithinItsHeightBudget` measures the
loop end to end at 360 dp — identity top to set-history bottom. The original
density pass held it at 840 dp (920.5 dp before, 837.0 dp after). CoachEngine v1,
adopted in commit `cc3609b1` (#370), added its evidence chip and changed the
ceiling to 868 dp. The owner's section-frame packet P1, adopted in commit
`89d94b41` (#444) and described in [the 29 September plan](../owner-eight-plan-2026-09-29.md),
lowered the ceiling to its measured 852 dp. **852 dp is the current test
ratchet**, not additional space available to a new design. The number may be
lowered; it may not be raised to make a change fit. This clarification records
those integrated decisions and changes no layout or test threshold.

## Amendment — 8 October 2026, native Quiet workout composition

The owner approved proceeding from the updated workout proposals and directed
development through completion without owner testing during development (O12).
The native packet adapts [Quiet](../ux-context/studies/q01/README.md#connected-proposals-following-o10)
to Compose's existing recording and timer contracts. Browser approval does not
establish native usability or physical-phone acceptance; those evidence limits
remain explicit while the agent implements and verifies the packet.

1. **Recording comes first.** Amend decision 1's floor order to a compact 64 dp
   keyed exercise illustration beside the complete name and existing identity
   controls; actual entry values; effort and visible readiness; saved-set strip;
   inline coaching; then Last set · Best set · Volume. Names wrap without losing
   the matching artwork. The image adds no redundant spoken name or separate
   focus stop. Existing exercise details, Working | Warm-up, Plan / Last fills,
   set correction and explicit Add set remain reachable.
2. **Keep one anchored primary action of at least 72 dp.** Values, effort,
   readiness and the primary form the recording sequence. The native adaptation
   retains the dock for keyboard, small-window and timing reachability rather
   than copying the browser's inline Log. The existing derived action supplies
   its verb, explicit payload and readiness; visible feedback accompanies the
   actual blocker. Missing effort, saving, correction, failed writes and exact
   Retry must remain understandable and actionable. There is no duplicate Log.
3. **Secondary content scrolls.** Coaching follows the saved-set strip and
   always participates in the floor's scroll order, with no floating coach or
   reserved overlay height. Why, Apply and existing coaching actions are retained;
   Pending saves keep the existing advice card in place with its controls
   disabled. Removing it during a write can clamp the shorter lazy list and
   move the entry; disabling it preserves the viewport and write lock.
   Apply still fills the draft without writing a set. Saved statistics move
   below coaching without changing their calculations. Saved work stays
   inspectable and correctable; this native adaptation retains the strip instead
   of adopting the browser proposal's hidden saved-row presentation.
4. **Retain the existing behavior and accessibility floors.** Instrument's
   semantic colors, one filled Volt action, tabular numerals and non-color state
   cues remain. Manual values, required working-repetition effort and its reset,
   suggested-versus-selected effort, explicit progression/extra sets, logging
   during rest, service-owned timers and recovery identities are unchanged.
   Touch targets remain at least 48 dp. The **852 dp ratchet** now measures the
   complete floor content through the relocated statistics, including saved sets
   and coaching, with the dock outside that measurement. Moving history earlier
   must not shorten the measurement or create permission to raise its threshold.
5. **Verify the native packet independently.** Resume reveals the identity/start
   of the floor; correction reveals its entry rather than the relocated stats.
   Check long names, all entry modes, font 1.0/1.6/2.0, small and adaptive windows,
   landscape, IME, full-control reachability, failure/retry and the connected
   Start → Log → rest/return → correction → Finish → Summary/History journey.
   Run the applicable local gate and independent/adversarial reviews on the
   implementation. Earlier browser and Debug-124 baseline passes are retained
   separately; they are not fresh verification of Quiet.

This amendment changes composition on the reviewed Debug-124 stack. It adds no
coaching algorithm, persistence format, navigation destination or distribution
change. Reconcile the overlapping Tempo/coaching stack before trunk integration.

## Amendment — 10 October 2026, compact Focus workout

The owner reviewed the connected Focus concept, requested a more compact and
complete presentation, and authorized implementation (O13). This amends the
8 October composition while retaining its recording, recovery and timer rules.

- Keep the matching 64 dp exercise image beside its complete name and set
  context. Use smaller Instrument workout title and numeral tokens. The native
  adaptation retains the fixed session overflow so it is reachable after scrolling.
- Keep actual entry, effort and visible readiness on the common floor. Group
  Working/Warm-up, weight fills, effort help/Clear, saved statistics and secondary
  coaching controls in a scrollable Set options sheet with a fixed Done action.
  Weight fills retain their existing weight-only behavior and say so explicitly.
- Show one durable receipt with direct Edit last and All. During correction,
  identify the actual selected saved set and show its saved record, rather than
  presenting a different latest set as the correction target. All retains the
  complete saved list and its existing correction and deletion actions.
- Put the Tempo shortcut alongside saved work. It opens the existing explanation,
  Apply and Keep my numbers actions. Apply still only changes the draft. Compact
  rest retains the service-owned clock and its existing controls. Preserve the
  anchored 72 dp primary action and applicable 48 dp targets.
- Retain the 852 dp ceiling for the complete common floor through its saved/Tempo
  region. Statistics now occupy a disclosed sheet, so this is a different
  composition from the previous inline measurement, not a claimed like-for-like
  height improvement. Verify every disclosed control and essential value across
  the full native matrix, separately from the floor measurement.

Native source, renders, interactions, connected journey and independent/adversarial
reviews must verify this packet. Browser approval does not establish native or
physical-phone acceptance. No schema, backup format, navigation destination,
coaching algorithm, signing or distribution change is authorized.

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
  *Amended 23 September 2026 by [ADR-032](ADR-032-jvm-evidence-lanes.md):* the
  floor goldens are retired rather than re-recorded, and packet X3 removed
  them.
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
  *Amended 23 September 2026 by [ADR-032](ADR-032-jvm-evidence-lanes.md):* they
  are retired, not re-recorded, and packet X3 removed them; the JVM render
  set, with reachability assertions, is the evidence.
