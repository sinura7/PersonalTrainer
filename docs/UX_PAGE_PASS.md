# UX page pass — living plan

> **Banner (24 Aug 2026).** Binding product rules below remain in force except
> the Room v2 freeze. A new database generation is authorized once, in
> foundation-program Phase 5 ([ADR-010](architecture/ADR-010-schema-reset-migrations.md)).
> Current program: [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md). This file is
> the gym-floor page-pass method, not the feature roadmap.

How we make Temper the easiest thing to use on the gym floor.

This file is the plan we follow for page passes. **It is not the law.** If a clearer move shows up
while building or on the phone, take it, write it under *Floor findings*, and
leave the old line struck through with why it lost. Trial and error is the method.

Status: **done** · **next** · *later* · **won't**

---

## How to use this file

1. One page at a time. Do not restyle the stack in one pass.
2. A page is done when its **gate** is true on a phone, not when the code looks finished.
3. Architecture already exists. Prefer a targeted fix over a rewrite.
4. After every page: `./gradlew testDebugUnitTest`, `assembleDebug`, commit, push, update the PR.
5. Do not run `connectedDebugAndroidTest` against the gym-floor phone. That
   lane targets `com.sinura.personaltrainer.debug` (Job 3 / P4). Use the emulator.

When you find a better idea, record it here in the same turn you ship it. The next
agent (or the same one tomorrow) should be able to read this file and know both
the plan and every time we left it.

---

## Deviation rules

The written plan loses when any of these is true:

| Prefer the floor when… | Example already shipped |
|---|---|
| A control would **lie** (it cannot do what it says) | Home said Start while a session was live |
| Two controls answer **the same tap** | Volt Start + volt Free workout on one sheet |
| A button produces **nothing** | Suggest a week on a fully pinned week |
| The user opened a surface **on purpose** and we sent them hunting | Start sheet → “dismiss and find the bar” |
| Copy uses **planner jargon** the gym does not | “Accept fills” |
| A label **disagrees with the code** | “Lands at 3 × 5” after per-lift defaults shipped |

The written plan **wins** when the floor idea:

- adds a fifth tab, a 3-tab collapse, an LLM trainer, or head-level anatomy
- invents a new route
- renames the package, Room version, or Drive folder
- uses `fallbackToDestructiveMigration`
- adds Start/Resume on a screen that is not Home, the start sheet, a Plan day, or Repeat

If you deviate, do not silently overwrite the gate. Strike the old line, add the
finding, keep the gate honest.

---

## Binding product rules

These are signed. A page pass may not weaken them.

- **Four tabs:** Home · Body · Plan · History. Library is pushed.
- **Volt = live / act only.** One filled control per screen.
- **Home / Plan / Start sheet / Body never say Resume.** The live bar and the rest
  notification are the return. Dialogs that fork (“this start, or the one already
  running”) say **Go to session** / **Go to that session**.
- **Today’s plan starts in one tap** from Home. Everything else is `StartOptionsSheet`
  over the current screen.
- **Finish / Discard** go through `FinishWorkout` / `DiscardWorkout`.
- Same week object (`TrainingInsightsSource`). Same `WeekStrip`. Same `ExercisePickerSheet`.
- Weights stored in kg. Display via `LocalWeightUnit`.
- Production stays on Room v2 until the signed Phase 5 cutover. Do not invent
  a `TrainerDatabase` v3 identityHash during a page pass.
  `fallbackToDestructiveMigration` stays forbidden. The one authorized new
  database generation is [ADR-010](architecture/ADR-010-schema-reset-migrations.md).

---

## Pages

### 0 — Setup · **done**

**Job.** Get a week on the phone.

**Gate.** Guided path → Home can start today’s session. Own path → editor `new`.
Nothing is written until “Use this plan”.

No further work unless a question is found that does not change the plan.

### 1 — Active workout · **done**

**Job.** Log this session.

**Gate.** Start → log → lock → rest completes → finish → Summary. Process death
keeps the draft.

**Floor findings**

- Rest dock stays **outside the scroll at the top**. Mid-rest you scroll sets;
  the clock must not leave the screen. *(beat: bury rest near the log)*
- Finish stays enabled on **any logged set**, including warmup-only.
- In-set empties are `compact` — the Temper mark does not sit mid-set.
- Leave copy points at the **bar and the rest notification**, never Home.
- Leave actions are stacked buttons. **Keep and exit** is the one Volt.
  Stay is a quiet control. Discard stays Danger ink and still confirms.
- **Log sits above the system nav.** Scaffold's bottomBar is edge-to-edge.
  This route hides the tab bar, so the log dock owns `navigationBarsPadding`
  the same way the tab bar and live bar already do.
- **Why is a dialog**, not an expand in the log dock. Next line is one
  line with ellipsis. Use stays Volt text. Lift name ellipsizes.

### 2 — Summary · **done**

**Job.** Name the session once.

**Gate.** Done → Home. Back cannot reopen a dead session.

**Floor findings**

- Pinned Done sits above the system nav (`navigationBarsPadding`), not
  under the three-button bar / gesture pill.

### 3 — Home · **done**

**Job.** State today; start it.

**Gate.** Four states, one volt: planned / rest / empty week / live.

**Floor findings**

- Agenda exists → `DailyAgendaCard` is the only today-surface. Volt is
  `Start {title}` on the next planned row (strength preferred). That row
  also shows the numbered lift order. Free workout stays quiet.
- Two-a-day: one Volt. The other planned row stays tappable, not a second
  filled Start. A three-session day is the same rule: one Volt on the
  first planned strength; later accessory stays tappable.
- Empty agenda leftover → `ThisWeekCard`. Planned + not logged → volt
  “Start this session”. Rest / already trained → quiet “Start a free workout”.
  ~~Rest → quiet “Start anyway”. Already trained → quiet “Start another”~~
  *(those labels never shipped on the leftover card; free is the honest second path)*.
- **Live → no Start at all.** The card names the plan. The bar is the way back.
  ~~Card never knows about live, so it still says Start.~~ That Start would lie.
- `LinkRow` is a full-width 48 dp row. Label and trailing ellipsize.
  Rec Why is a dialog; the card tap does not wrap it.

### 4 — Start sheet + live bar · **done**

**Job.** Shared start / return.

**Gate.** Bar: resume / finish / discard. Sheet does not become a third Resume.

**Floor findings**

- Live sheet primary is **“Go to session”**, not “dismiss and find the bar”.
- Discard from the sheet uses the **same confirm** as the bar.
- Today’s plan is a **filled button**, not a list row. Body, History, and Plan
  pass no extra Today args — the sheet reads the same agenda Home uses and
  starts it (strength preferred, leftover slot week if the agenda is empty).
  Home itself does not host this sheet.
- Free workout is **quiet** so it does not compete with that button. One string:
  `SessionOrderCopy.FREE_WORKOUT`.
- Status lines never dump a schema enum (`Planned`). Empty strength is
  “No lifts yet”; cardio/mixed planned is “Ready”.
- Live bar identity is the resume target. The ⋮ is not inside that tap.
  Title ellipsizes. Height is `rowMin`, not a clipped 56 dp.

### 5 — Plan · **done** (this pass)

**Job.** A pinned week.

**Gate.** Suggest → confirm → Home names today. Missed days do not roll.

**Floor findings**

- Empty week, **no routines**: volt “Suggest a week” (same words as Home).
- Empty week, **routines exist**: volt “Use my answers again” (Job 3 / P1).
  Quiet Suggest stays the heat/planner path. One filled button.
- Week with pins: quiet Suggest. Fully pinned: **hidden**
  *(a button that produces an empty preview is a dead control)*.
- Confirm is **“Use this week”**. ~~Accept fills~~ — fills is planner jargon.
- Header New is quiet so it does not compete with that confirm.
- Free on a Plan day opens `StartOptionsSheet`, not an empty free session.
- Two-a-day: one Volt Start (strength preferred); other planned rows stay
  tappable. Same rule as Home. A later accessory session is another
  tappable row, not a second Volt.
- Missed-work: one Volt **Keep the dates**. Move / Adapt / Skip are
  secondary gym buttons. Copy never says “recurrence”.
- Plan day Start sits **under** the grouped occurrence list, not inside
  the window. Day sheets skip the half-expanded detent.

### 6 — Editor · **done** (this pass)

**Job.** One program.

**Gate.** Targets persist after leave and process death. **No Start on this screen.**

**Look for**

- Empty state that tells you to add a lift but has no action (the button is a
  quieter control further down).
- “Add exercise” vs “Add a lift” — the workout and the picker already say lift.
- A Save that does not write targets (already removed; do not bring it back).

**Won't.** Start / Resume. A second Save. A plate *screen* — Job 5 / P3
is a caption under the numeral, not a new route.

### 7 — Library / Exercise detail · **done** (this pass)

**Job.** Catalog question + lift life.

**Gate.** Body muscle filter **survives the hop** to detail and back, including
after the user changes chips. A custom add uses that lift’s defaults, not a
universal 3 × 5.

**Look for**

- `LaunchedEffect(initialMuscle)` re-applying the Body filter when Library
  recomposes after a pop — wiping a chip the user already cleared.
- Copy that still says “3 × 5” after `AddDefaults` became per-lift.
- FAB + empty-state both creating a lift (one volt is enough).

**Won't.** Library as a fifth tab. Merging custom history into a built-in id.

### 8 — Body · **done** (this pass)

**Job.** Heat + hole.

**Gate.** Changing Day / Week / Month **does not change recommendations**.
(Already true in `TrainingInsightsCalculator` — coach uses a fixed 14-day basis.
The pass is to keep it that way and to make the map actionable.)

**Look for**

- Contributor rows that name a lift and do not open it.
- Empty / error offering Start when the remedy is retry (error already says retry).
- Window chips that look like they change the advice. If they do, that is a defect.

**Won't.** Year / all-time heat windows. Head-level anatomy. LLM coach.

- Muscle sheet metrics share width. Name and recency ellipsize.

### 9 — History / Session detail · **done** (this pass)

**Job.** Memory + typo fix.

**Gate.** Edit a finished set → heat and volume move. Repeat = a **new** live
session. Repeat while live → “Go to session”, not a silent resume.

**Floor findings**

- History is a readout. Day / Week / Month / Year / All chips retotal.
  Empty still shows the calendar. There is no Start Volt on this tab.
- Repeat copy that says Resume.
- A calendar day with two sessions opening only the first (already a sheet).
  The day sheet skips the half-expanded detent and scrolls.
- Session-missing empty with no way back.
- Four-up metric clusters use `weight(1f)` so 360 dp does not clip a column.
  The horizon readout now leads with sessions, then days / sets / min.

**Won't.** Recomputing duration on a repair. Changing `completedAt`. A Start
button on History. Restyling Instrument.

### 10 — Settings · **done** (this pass)

**Job.** Prefs + survival.

**Gate.** Unit change re-renders the stack. Restore is **blocked while live**,
and the screen says so *before* the tap, not only after the repository refuses.

**Look for**

- Restore / import still tappable during a live session (repo already throws).
- Backup copy that implies today’s unfinished session is in the file.
- Guided setup re-run that deletes history (it must not).

**Won't.** Silent WorkManager Drive upload. Renaming the Drive folder.
Inventing a casual `TrainerDatabase` v3 during a page pass. Job 5 / P5 is a
stale-backup prompt, still a tap. The foundation cutover is not this file.

---

## Do not open

Casual `TrainerDatabase` v3 · emulator instrumented tests on the real
applicationId · package rename · Drive folder rename · 5th tab · LLM trainer.

Plate calculator, rest *sound* design, font-scale 2.0, and prompted
backup shipped as Job 5. Job 6 leftover polish is historical. Current
program: [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

Deload decision UI was deferred here. The Tune chip is Job 3 / P2
(ROADMAP option A). The Body card tap that actually marks the week is
Job 4 / P1. Home empty-week volt becomes replay when routines exist
(Job 4 / P2).

---

## Floor findings (newest first)

Record every deviation here. Oldest stay; do not delete.

Owner asked for a **Body readout** (29 Aug 2026): the tab is the
silhouette. Day / Week / Month chips. No Start on this page. Empty
still shows the figure. Heat follows sets, reps, and RPE. Instrument
stays.

Owner asked for a **History readout** (29 Aug 2026): the tab is
information. Day / Week / Month / Year / All chips. No Start on this
page. Empty still shows the calendar. Instrument stays.

Owner asked for a **day stack** (28 Aug 2026): more than two sessions
on one weekday — morning cardio, the pinned workout, and later
accessory / Hyper Pro work. Plan adds the later occurrence; Home
lists them in time order. One live activity. Finish one, then start
the next. Overlay rest, a fifth tab, and LLM-as-author stay won’ts.

Owner asked for a **cross-tab layout fit** (28 Aug 2026): windows, buttons,
and text that fit on every tab and in the workout. Instrument / ADR-005 stay.
Not a restyle.

Owner asked for **vertical lift cards** (28 Aug 2026): selected workouts
were a left-to-right strip. Each lift now occupies one full-width row,
the next under it, scrolled up and down. Tap expands that card — builder
targets, or the live log. Live does not collapse the open lift on a
second tap. Rest floor is a ticking ring around the remaining clock;
the log bar stays linear. Overlay rest and a 240 dp ring on the log
stay won’ts.

Owner asked for **Next / extra set / RPE targets / lock-screen rest**
(28 Aug 2026): when prescribed sets are in, Log becomes Next. A +
after the last recorded set logs extra. Selected RPE retargets the
wells and the set-progress line from this session and last time.
Rest countdown is on the lock screen (HIGH public chronometer +
`RestLockActivity` when tapped or when rest completes). Overlay rest
and a 240 dp ring on the log stay won’ts.

1. **Rest dock stays at the top, outside the scroll.** Mid-rest you scroll sets.
2. **Start sheet while live is “Go to session.”** They opened Start on purpose.
3. **Finish on any logged set**, including warmup-only.
4. **Home Start disappears while live.** A Start it cannot honor is a lie.
5. **“Use this week,” not “Accept fills.”**
6. **Don’t show Suggest when every remaining day is already pinned.**
7. **Editor empty state’s action *is* Add a lift.** A caption with the button
   further down is a treasure hunt on a one-purpose screen.
8. **Library seeds the Body muscle once per ViewModel**, not on every recomposition.
   Surviving the hop means the filter you arrived with *and* the chips you changed.
9. **“Lands at 3 × 5” is a lie.** Defaults are per lift. Say that.
10. **Body contributor rows open the lift.** A name you cannot tap is a dead end.
11. **Settings restore is disabled while live**, with a sentence, not only a
    thrown error after the tap.
12. **The start sheet starts today.** Body, History, and Plan host it. Home
    does not. Dismissing before `startToday` would cancel the write.
13. **Plan free is the sheet**, not `startFreeWorkout()` from the day sheet.
14. **Never print `Planned`.** Numbered lift order, “No lifts yet”, or “Ready”.
15. **Missed-work Keep the dates is the Volt.** Recurrence is not a gym word.
16. **Lift, not exercise**, on gym-floor errors and the empty session body.
17. **Pinned Log / Done sit above the system nav.** The tab bar is gone on
    those routes; the dock owns the inset. A Log under the three-button
    bar is a miss.
18. **Leave-workout Keep and exit is the Volt.** Stay is a real control.
    Discard stays Danger ink, not a second filled button, and still confirms.
19. **Live cardio Finish is the Volt and sits above the system nav.** Leave
    running and Discard are stacked secondary controls, not footnotes.
    Discard confirms. Types speak Run / Ride / Walk, not `RUN`. After
    finish, activity-summary Done is pinned with the same inset. System
    back opens a leave dialog whose Volt is Leave running.
20. **Activity composer Save is the Volt.** Type chips speak Run / Ride /
    Walk via `CardioCopy`, not `RUN`. Weight follows `LocalWeightUnit`.
    Add set / Add cardio are secondary. Remove is a named Danger control;
    tapping the row does not delete the line.
21. **Session lifts stack vertically.** A left-to-right strip made the next
    lift a hunt. Each lift is one full-width card under the last. Tap
    expands sets/reps/rest/load (builder) or the log (live). Live does
    not collapse the open lift on a second tap.
22. **Rest floor is a ticking ring around the remaining clock.** The log
    keeps the condensed bar and linear track. Overlay rest and a 240 dp
    ring on the log stay won’ts.
23. **Log becomes Next when the lift is done.** Extra sets are a +
    after the last recorded set, never a second Volt. Finish stays in
    the header on the last lift.
24. **Selected RPE tells you what to lift next.** Wells fill from last
    working / last session, not a preview of the draft. Auto-apply
    after a log stays a won’t.
25. **Lock-screen rest is a chronometer, not overlay rest.** Screen
    off, turn on, the countdown is on the lock screen. The live log
    keeps the condensed bar.
26. **A weekday can hold more than two sessions.** Morning cardio,
    the pinned workout, and a later accessory session are independent
    rows. One Volt. Finish one before starting the next.
27. **History has no Start.** Day / Week / Month / Year / All retotal a
    hero sessions numeral. Empty still shows the calendar. Start lives
    on Home.

---

## Verification (every page)

- [ ] `./gradlew testDebugUnitTest` — 0 failures
- [ ] `./gradlew assembleDebug`
- [ ] Phone: the page gate, plus any floor finding shipped this turn
- [ ] This file updated if we left the written plan

Last JVM gate: 1324 tests, 0 failures (composer chrome packet, 26 Aug 2026). Phone gates still need the owner. Job 5 is code-done except CI `trunk`. Job 6 regroup: [JOB6_REGROUP.md](JOB6_REGROUP.md).
