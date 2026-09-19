# Temper: visual and interaction audit

Review date: 16 September 2026. Source baseline: `20789157`, with the existing, uncommitted Windows setup changes left intact. This is a design review and proposal, not a shipped redesign or a new accepted architecture decision.

## Verdict

Temper has a coherent identity and substantial functional engineering. It does **not yet meet the requested exceptional standard for visual finish and interaction clarity**. The main shortfall is composition: individually reasonable components compete for space and attention when assembled into a real screen. The workout flow deserves the first implementation pass; it is the most frequent and consequential interaction, and eight of the reference images show its entry, timer, or switcher states.

Keep the near-black surfaces, restrained lime action, cyan rest state, bundled Inter and Space Grotesk, offline behavior, and five-tab navigation. Improve hierarchy, information grouping, visual state distinctions, anatomical artwork, and transitions between tasks. A palette replacement would not solve the principal problems.

[Open the native baseline review](native.html). The owner's local workspace also
contains `index.html`, an interactive concept using the supplied phone images.
That local concept is illustrative and is not an Android build or a validated layout.

## Evidence and limits

- Reviewed all 12 supplied images. Images 05 and 09 are repeated views of the same logged-set/rest state. Originals are retained locally, unchanged, in `references/01.png` through `references/12.png`; they are excluded from the public repository.
- Inspected the current Compose screen composition, shared tokens and controls, workout input and timer code, Home date copy, Body rendering, History period behavior, live-session bar, Settings grouping, and representative visual/accessibility tests.
- Read the accepted Instrument identity, palette, navigation, and execution decisions. This proposal works within their visual identity. Any proposed behavior change to History scope or the reserved dock contract must be documented and reviewed explicitly.
- No Android virtual device was listed by `emulator -list-avds`; `adb devices -l` listed no connected device. No app runtime, TalkBack, physical touch, transition timing, keyboard, or large-font behavior was tested in this audit.
- The installed build corresponding to the images has not yet been identified. A source-backed finding describes the inspected checkout; a screenshot-only observation is not automatically proof that the same defect exists at HEAD.
- The previously completed local verification had 2,536 passing tests. That is useful functional evidence; it does not establish visual quality or replace rendered, full-screen interaction checks. Those tests were not rerun for this document-only audit.
- Screenshot pixels are not Android dp. Cropped edges, an open menu, a pressed state, or a scrolled card are not by themselves proof of a layout bug.

Severity: **P1** = address in the first redesign work; **P2** = next consistency/polish work. No data-loss or crash claim is made here. Evidence: **S** screenshot observation, **C** confirmed code behavior, **R** requires runtime reproduction. Design judgments are identified as recommendations.

## Prioritized findings

### D01 · P1 · Home uses “today” for a different selected date · S/C

Image 01 shows Tuesday 15 selected, Wednesday 16 marked as today, and the heading “TRAINED TODAY.” `HomeScreen` passes `loggedSelected` to a parameter named `loggedToday`; `MastheadCopy.headline` unconditionally returns that text when true. This is a real mismatch between the selected date and the sentence, not merely a typography issue.

**Change:** supply the selected date/today relationship to the copy function. Use “Training complete” for a historical selection, or a specific date-aware phrase; reserve “Trained today” for the actual current day. Keep today and selected-date indicators visually distinct, and add a clear route back to Today when browsing another date.

**Acceptance:** yesterday completed, today completed, today with another planned activity, future planned day, and empty day each describe the correct date without hiding another scheduled activity.

Evidence: [HomeScreen.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt), [MastheadCopy.kt](../../../app/src/main/java/com/sinura/personaltrainer/domain/MastheadCopy.kt), [WeekStrip.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/WeekStrip.kt).

### D02 · P1 · A recommendation looks too much like a selection · S/C

Images 03–05 show lime-outlined warm-up/RPE choices. `InstrumentChip` uses the same lime border for `selected || recommended`. Selected values additionally get a tinted background and lime text, but the shared, strongest outline makes a suggested value easy to read as already applied. In image 03 a ramp option is emphasized while the working weight remains 20 lbs.

**Change:** give recommendations a quiet “Suggested” annotation or marker; reserve the checked/tinted treatment for a committed selection. Make warm-up mode explicit with a Working/Warm-up choice. Ramp options should clearly describe an action, such as “Use 15 lbs,” with their percentage as supporting text.

**Acceptance:** before any tap, a user can tell the entered weight, the current set type, an uncommitted recommendation, and a selected RPE. Changing a recommendation never changes the draft until applied. Selected and suggested remain distinguishable without color.

Evidence: [InstrumentChip.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/InstrumentChip.kt), `WarmupRampRow` (then in `WorkoutLiftCard.kt`, now in [WeightRepsEditor.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/WeightRepsEditor.kt)), `SecondaryLogOptions` (then in `WorkoutLogBar.kt`, now [RpeSelector.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/RpeSelector.kt)).

### D03 · P1 · The workout footer reserves too much vertical space · S/C

Images 02–03 have a large empty span; images 04–05 become crowded after logging. `LogBar` always reserves a 56 dp timer row and a 56 dp context rail, then uses a 72 dp primary button inside `PinnedDock`, with additional padding, gaps, and navigation inset. The button itself has a good gym-sized target. The combined structure is the problem: empty space is reserved for a message even when there is no message.

**Change:** design one compact action area around the logging button. Keep its bottom edge stable; give timer context and receipt/undo a single, deliberately budgeted companion region. Do not remove useful targets to gain space. Show a receipt as a concise saved-state message rather than another large bordered panel repeating the entire set. Keep errors and undo reachable.

**Acceptance:** at normal font on a 360 × 640 dp viewport, core entry controls and the commit action remain usable without accidental overlap. At larger fonts, allow intentional scrolling and expanded controls. Measure actual coordinates before/after log, receipt expiry, error, and completion. The commit button must not jump because a receipt disappears.

Evidence: `WorkoutLogBar.kt` (now [WorkoutDock.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/WorkoutDock.kt)), [PinnedDock.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/PinnedDock.kt), [Metrics.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/theme/Metrics.kt).

### D04 · P1 · Logging can redirect the viewport away from the next input · S/C/R

Images 04–05 show the exercise card partly scrolled beneath the header. That alone is valid scrolling, not proven overlap. However, `WorkoutLiftCard` explicitly calls `rowRequester.bringIntoView()` for the logged-sets panel whenever the set count increases. It also declares an entry requester but this effect targets history, not the next entry. As the set list grows, the next thing the user needs can move away.

**Change:** define a stable post-log focus rule. During ordinary repetition, retain the next-entry region and show a compact latest-set acknowledgement. Reveal older sets through a deliberate action. During edit/delete/undo, reveal the affected row. At lift completion, intentionally expose the next-lift choice.

**Acceptance:** log sets 1–8 on a short screen; the next input remains predictable. Test with keyboard open, warm-ups, RPE helper, recommendation, receipt, and an active timer. Do not use a screenshot of a partly scrolled card as the sole regression criterion.

Evidence: `WorkoutLiftCard.kt` (now [WeightRepsEditor.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/WeightRepsEditor.kt)), [LogLoopBringIntoViewTest.kt](../../../app/src/test/java/com/sinura/personaltrainer/ui/workout/LogLoopBringIntoViewTest.kt).

### D05 · P1 · The Body illustration is the weakest major visual asset · S/C

Image 10 shows translucent, rounded heat shapes that read as overlays placed over the figure rather than muscles belonging to it. Some shapes visually extend beyond the apparent anatomy. `FigureArt.drawStillWithHeat` draws a cropped raster still, then independently defined smoothed plate paths at 72% opacity. That separation is a plausible mechanism for alignment mismatch; the screenshot is the visual evidence, not proof of a particular coordinate error.

**Change:** use one aligned anatomical coordinate system for the base figure, heat regions, selected-region outlines, and hit testing. Prefer precise vector regions or carefully registered masks. Preserve the established palette. Make the figure feel deliberate at both front and back; do not try to conceal alignment issues with more saturation, glow, or opacity.

**Acceptance:** untrained, one selected muscle, asymmetric heat, fully trained, front/back, and each named muscle all align. Verify both rendered silhouette and hit target. The muscle list remains the accessible alternative to small anatomical regions.

Evidence: [FigureArt.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/FigureArt.kt), [BodyMap.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/progress/BodyMap.kt).

### D06 · P1 · Body’s size budget ignores some of the space the user actually loses · S/C/R

The figure in image 10 occupies most of the screen; the first muscle row is only partly available above the active-session bar. `BodyViewport` derives figure height from the full window height and its arithmetic budget subtracts only a 64 dp tab bar. It does not model the live-session bar, and its simplified first-row calculation is not a substitute for the actual composed controls and insets.

**Change:** size the figure from the measured content area or an explicit compact/expanded layout. Keep the period, front/back selection, and at least a useful first muscle row visible at normal font. An expanded body view can provide detail without making the overview a poster.

**Acceptance:** real full-app captures with and without an active session, at 360/412/600 dp and 1.0/1.6/2.0 font scales. Large type may scroll; it must not overlap or trap the controls. Test front/back controls with long localized labels.

Evidence: [BodyViewport.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/progress/BodyViewport.kt), [BodyMap.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/progress/BodyMap.kt), [AppNav.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt).

### D07 · P1 · History has two different period models on the same screen · S/C

Image 11 selects Month while the calendar says This week. The caption explains that the period changes totals but not the calendar or history list. `HorizonPicker` and `HistoryScreen` deliberately implement this behavior. It is not a broken filter, but it requires the user to learn a non-obvious exception.

**Change:** first make the scope explicit: put period selection inside a “Training totals” region and give the session browser its own visible date scope. Compact the summary so actual sessions appear sooner. A later alternative is one period controlling everything, but that changes the existing product contract and must be decided explicitly.

**Acceptance:** someone selecting Month can predict which content changes. A calendar selection gives obvious feedback and correctly handles multiple sessions. Keep history edit/repair and all-time browsing available.

Evidence: `HorizonPicker` in [HistoryScreen.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/history/HistoryScreen.kt), [TrainingCalendarCard.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/history/TrainingCalendarCard.kt).

### D08 · P1 · Exclusive filters expose a generic checkbox role · C

`InstrumentChip` defaults to `Role.Checkbox`. Body time periods and History horizons do not override that role, even though one value is selected at a time. The workout RPE group already uses an explicit radio role. Visually, unrelated uses also look almost identical: filters, modes, recommendations, and action presets all use the same tile treatment.

**Change:** retain shared styling primitives but add distinct contracts for single selection, independent toggles, and action presets. Use `selectableGroup` and radio/tab semantics where appropriate; action presets should announce an action rather than merely a selected state.

**Acceptance:** TalkBack describes each group and its current choice; selecting one period deselects the prior period. Every control retains a clear visible focus/pressed/selected state and a usable target.

Evidence: [InstrumentChip.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/InstrumentChip.kt), [ProgressScreen.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/progress/ProgressScreen.kt), [HistoryScreen.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/history/HistoryScreen.kt).

### D09 · P2 · Exercise identity is large but its navigation is hidden · S/C

The 112 dp image and 128 dp minimum hero compete with the values being entered in images 02–07. The image/name area opens the lift switcher, but the visible overflow dots suggest that the only action is a menu. The gray rectangular patch in several images could be a pressed/focus/capture state; its cause is unconfirmed and should not be “fixed” by guessing.

**Change:** use a compact exercise identity row with a sharp 56–64 dp image, exercise name, “Lift 1 of 7,” and an explicit switch affordance. Open a larger illustration or details intentionally. Separate session telemetry from exercise progress so “1 set · 518 lbs” cannot be mistaken for the exercise prescription.

**Acceptance:** a new user finds the exercise list without trial taps. Long exercise names remain recognizable. Compare hero rest, press, focus, and TalkBack states on the exact installed build.

Evidence: `CurrentLiftCard.kt` (now [ExerciseHeader.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/ExerciseHeader.kt)), [LiftSwitcherSheet.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/LiftSwitcherSheet.kt).

### D10 · P2 · Direct numeric entry lacks a visible invitation · S/C

The large weight/reps values are clickable and open `NumberEntryDialog`, but compact `FloorNumeralRow` renders them like readouts. The non-compact entry has a more explicit field treatment. A user can spend many taps on ±5 without discovering direct entry.

**Change:** make the numeral visibly editable with a restrained field treatment or a one-time “Tap a number to edit” cue. Keep large, separated increment targets and long-press behavior. Preserve load-type semantics; zero external load, bodyweight, added load, and assisted load are different cases.

**Acceptance:** jump from 40 to 135 lbs with direct input, enter decimals, cancel, change units, and edit an existing set without accidental saves. Show planned vs last-used values as context, not as recommendations the user must follow. Do not automatically assume a 45 lb bar.

Evidence: `FloorNumeralRow` in [SetEntryPanel.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/SetEntryPanel.kt), [StepperButton.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/StepperButton.kt).

### D11 · P2 · Rest and set timing need clearer visual language · S/C

Images 02–03 place a rest duration, a stopwatch icon, and “Start” together; image 07 replaces rest with SET timing. Accessible descriptions exist, but the visual meaning still requires decoding. Image 06 also says “Start at 1:30.” while showing a countdown, which is ambiguous: a clock time, a duration, or an instruction to begin now?

**Change:** explicitly label the idle action “Start rest”; make “Time set” a distinct secondary action. Use “Planned rest · 1:30” for the prescription and separate it from remaining time. Keep the same control ordering in the dock and full timer where possible. Let the rest screen be calm; empty space itself is not a defect. Place the actionable controls within comfortable reach.

**Acceptance:** idle rest, running rest, elapsed rest, stopwatch, hold, paused/editing, and completed states each communicate one meaning. Verify countdown/notification continuity and the effects of Skip and ±15. No new floating overlay timer.

Evidence: [RestTimerUi.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/components/RestTimerUi.kt), [RestTimerScreen.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/RestTimerScreen.kt), [RestFloorCopy.kt](../../../app/src/main/java/com/sinura/personaltrainer/domain/RestFloorCopy.kt).

### D12 · P2 · RPE needs understandable, persistent help · S/C

An optional 6–10 row is compact, but “RPE” alone is unfamiliar. Existing helper/spoken copy is useful; after dismissal, the visible row still needs an easy way to recall the meaning. Suggested and chosen values must remain distinct (D02).

**Change:** label it “Effort · optional” with accessible RPE help; show a short explanation for the selected value. Preserve optionality and allow clearing the choice. Do not make more copy consume another permanent row on every repetition.

**Acceptance:** a new user can understand, skip, select, clear, and edit effort. Screen-reader output announces the chosen effort, not just an isolated number.

### D13 · P2 · The live-session bar looks more tappable than it is · C/R

Only the title column has the resume click handler. The elapsed time and metrics beside it are sibling readouts. The whole strip visually resembles one return-to-session target, so tapping a metric can plausibly feel unresponsive. Compose hit expansion must be checked on device before claiming exact dead-zone dimensions.

**Change:** make the main bar area one clearly labeled “Return to workout” target, with an independent overflow button. Preserve state and the existing single-live-session rule. Keep the quiet strip on other screens; do not add competing Resume buttons everywhere.

**Acceptance:** taps across the non-menu portion resume consistently; TalkBack exposes one understandable main action and a separate menu. Last list items remain reachable above the bar.

Evidence: [LiveSessionBar.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/navigation/LiveSessionBar.kt). `AppNav` already places the bar in the Scaffold bottom area and applies padding; the screenshots do **not** prove that it overlays content incorrectly.

### D14 · P2 · Home gives completed planning detail too much prominence · S/C

Image 01 spends most of its height listing seven exercises from a completed Lower A, then places “Start a workout” below it. The finished status is a quiet word at the bottom. This gives planned exercise detail more emphasis than the day's outcome or next action.

**Change:** make completion visible near the title and summarize a completed block. Allow deliberate expansion to the exercise list or actual session details. Keep planned and performed work clearly identified; don't present a routine's prescription as proof of what was logged. Preserve access to additional planned activities on the same day.

**Acceptance:** completed, skipped, partially completed, future, and multiple-activity days each have a clear hierarchy. Home's generic start action still opens the established options sheet.

Evidence: [DailyAgendaCard.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/home/DailyAgendaCard.kt), [HomeScreen.kt](../../../app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt).

### D15 · P2 · The component system needs stronger distinctions, not more decoration · S/C

Many surfaces use a dark rounded rectangle plus a hairline: cards, steppers, RPE, filters, rest controls, receipts, and sheets. Each is consistent locally, but the whole screen resembles a grid of equally weighted boxes. The color tokens and typography are already centralized; the next layer should define component roles.

**Change:** establish a small set of explicit patterns: page header, compact identity row, editable numeric field, selection group, action preset, readout, grouped navigation list, and bottom action area. Use borders to distinguish an interactive boundary or state, not around every message. Keep existing token values until rendered evidence justifies changes.

**Acceptance:** a component gallery covers rest/press/focus/selected/recommended/disabled/error/loading and long text. Changes propagate through the shared implementation rather than accumulating per-screen padding overrides.

### D16 · P2 · Header actions and Settings labels need a tighter information hierarchy · S/C

“Log or start cardio” competes with Body/History titles while an active workout is already present. Settings is the most orderly screen in the reference set, but “Week generator,” “Your plan,” and the subtitle “Add a new block” overlap conceptually. “Backup” gives features rather than current status.

**Change:** keep secondary header actions concise and make the sheet explain its options. Review the scope of “Log activity” versus “Start” without removing existing capabilities. Give Settings groups visible, useful names; clarify what each planning destination changes. Show truthful backup state when available, with details on its subpage.

**Acceptance:** long labels and large fonts do not crowd the screen title. Settings can be understood without opening every row. Missing backup timestamps are shown as unknown/not yet backed up rather than inferred.

### D17 · P1 · Full-screen visual coverage needs to become a release gate · C/R

The project has meaningful tests and existing screenshot fixtures. However, tests that assert nodes exist, token arithmetic, or source strings cannot establish that the assembled screen feels good. For example, the Body component test mounts the map and a muscle row without the entire navigation/live-bar context in that case. `FloorGoldenTest` records useful seeded workout states, but their existence alone is not evidence that all widths, fonts, and transitions were reviewed.

**Change:** reuse the existing harness and capture deterministic full-app states in a configured emulator. Add bounds/interaction assertions where they protect behavior, and review the screenshots deliberately before approving new baselines. Do not solve visual failures by accepting all new images or loosening checks indiscriminately.

**Acceptance:** the matrix below is captured and reviewed for each affected screen. Physical phone checks remain necessary for one-handed use, haptics, outdoor/gym readability, and accessibility.

Evidence: [FloorGoldenTest.kt](../../../app/src/androidTest/java/com/sinura/personaltrainer/ui/preview/FloorGoldenTest.kt), [BodyPassInstrumentedTest.kt](../../../app/src/androidTest/java/com/sinura/personaltrainer/ui/progress/BodyPassInstrumentedTest.kt), [ContrastPolicyTest.kt](../../../app/src/test/java/com/sinura/personaltrainer/ui/theme/ContrastPolicyTest.kt).

## Items to reproduce, not assert as defects

1. Image 11's 800 minutes for two sessions looks surprising. Inspect session durations, backdated entries, long-open sessions, and summary aggregation before calling it wrong. Duration repair and compact hour/minute formatting may help; do not silently clamp real data.
2. The exercise-card gray rectangle in images 02/03/07 needs a resting/pressed/focused capture on the exact build.
3. The left/right crop in image 08 may come from capture framing. Confirm the full lift sheet before assigning an overflow defect.
4. Image 07 does not show the Log button while SET timing is running. Confirm whether it was captured mid-transition, scrolled/cropped, or deliberately hidden by that build. Do not infer lost logging capability from one image.
5. Runtime text scaling: the heat legend is a non-wrapping row, the numerical entry has fixed side targets, and several important strings are capped at one line. These are test targets, not proven failures at every size.
6. The supplied text looks subdued, but that is not sufficient to declare a contrast violation. Existing token tests cover load-bearing foreground/surface combinations. Measure actual composited selected, pressed, disabled, and heat states as well.

## Recommended workout direction

The first screen should answer, in order: **Which exercise? Which set? What am I recording? What happens when I tap?**

1. Quiet header: return to session navigation, routine title, and Finish. Finishing remains explicit and confirmed where appropriate.
2. Compact identity: one crisp thumbnail, exercise name, lift position, visible switch action. Illustration detail opens deliberately.
3. Set mode and progress: “Working set 2 of 4” or “Warm-up 1,” with an unambiguous mode control.
4. Weight/reps: large editable numerals; obvious units; substantial decrement/increment targets. Show last/per-plan context sparingly.
5. Optional effort: one selection group with accessible help and a clear committed state.
6. Recent work: one useful latest-set line with edit/undo; older sets available intentionally. Avoid repeating the same result in three prominent locations.
7. Bottom action area: clear rest state and a stable primary Log button. On the final set, explicitly present Next lift without silently advancing. Logging one more set remains possible when requested.

The interactive concept demonstrates this hierarchy using the current palette and fonts. It intentionally does not implement persistence, the progression engine, notifications, accessibility services, or Android keyboard/inset behavior. It illustrates an intended interaction contract; engineering still has to prove that contract in Compose.

## Implementation sequence

| Work packet | Scope | Proof before moving on |
|---|---|---|
| A: baseline and state clarity | Configure one emulator; capture the exact current build; date-aware Home copy; selected vs suggested; exclusive filter semantics | Focused copy/semantics tests, baseline screenshots, no changed workout data behavior |
| B: workout reference screen | Compact exercise identity, visible numeric editing, explicit warm-up, consolidated dock and post-log focus | Entire log → rest → next-set → next-lift → finish journey, edit/delete/undo, short screen and large text |
| C: shared controls | Propagate approved headers, chips, state styling, grouped rows, live-bar target | Component states plus Home, Body, History, Settings regression captures |
| D: Body and History | Aligned anatomy; measured viewport; clear totals scope and session browser | Front/back map states, period changes, multi-session days, truthful totals |
| E: complete product pass | Plan, Library, routine editor, dialogs, session detail, onboarding, errors, empty states, cardio | Same visual/interaction bar on unseen flows; physical-phone acceptance |

Do not rewrite all screens at once. Establish one excellent workout screen in Compose, review it on the real phone, and use it as the reference. Keep one implementation packet open at a time. All current data, backup, signing, and Obtainium delivery contracts remain in force.

## Acceptance matrix for exceptional quality

| Dimension | Required evidence |
|---|---|
| Screen sizes | 360 × 640 and a representative 412 dp phone; 600 dp layout; portrait and relevant landscape; gesture and three-button navigation |
| Text and language | Font scales 1.0, 1.6, 2.0; long routine/exercise names; kg/lbs; large/decimal values; relevant RTL |
| Workout states | First use, existing history, no load, bodyweight, added/assisted load, warm-up, working set, hold, stopwatch, running/finished rest, last set, extra set, next lift |
| Reliability | Duplicate tap, disabled/logging state, persistence failure, retry, edit/delete/undo, leave/resume, process recreation, offline |
| Accessibility | At least 48 dp targets where applicable, non-overlapping expanded hit areas, group semantics, focus order, meaningful labels, no color-only meaning, reduced motion, physical TalkBack |
| Visual finish | Shared margins/baselines, coherent type hierarchy, sharp assets, controlled surface levels, readable units and context, no unexplained clipping, stable primary action |
| Performance | Measure entry and scrolling in a representative build/device; timer updates must not unnecessarily recompose the whole screen; no pass claimed from screenshots |
| Human evaluation | Find/switch a lift, enter a large weight directly, identify warm-up vs working, understand suggestion vs saved value, log and undo, return from another tab without coaching |

Android's [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) describe 48 dp minimum target sizing and automatic hit-target expansion. Its [accessibility testing guidance](https://developer.android.com/codelabs/basic-android-kotlin-compose-test-accessibility) supports checking contrast, labels, and usable touch targets. These are baseline requirements, not a claim that following a checklist alone produces excellent design.

## Coverage still needed

No screenshots were supplied for Plan, Library, routine creation/editing, exercise details, numeric-entry dialogs, finish/summary, session repair, cardio, backup/restore, onboarding, loading, errors, or empty states. Their linked shared components were considered where relevant, but these journeys have not received a visual sign-off. The highest-value next images are the Plan screen, an exercise picker, the weight-entry dialog, and the workout finish/summary flow.

No production UI was edited, no APK was installed, and no release was published during this audit. The retained screenshots are local review evidence; sharing or committing them should be an intentional part of a later review.
