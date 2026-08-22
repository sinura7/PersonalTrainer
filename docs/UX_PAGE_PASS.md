# UX page pass — living plan

How we make Temper the easiest thing to use on the gym floor.

This file is the plan we follow. **It is not the law.** If a clearer move shows up
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
- Room stays at **version 2**. Never invent a schema v3 or an identityHash.

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

### 2 — Summary · **done**

**Job.** Name the session once.

**Gate.** Done → Home. Back cannot reopen a dead session.

No change this pass. The screen already leads with volume, gold for records only,
and pinned Done.

### 3 — Home · **done**

**Job.** State today; start it.

**Gate.** Four states, one volt: planned / rest / empty week / live.

**Floor findings**

- Planned + not logged → volt “Start this session”.
- Rest → quiet “Start anyway”. Already trained → quiet “Start another”
  *(a second filled Start after TRAINED TODAY reads as “it didn’t save”)*.
- **Live → no Start at all.** The card names the plan. The bar is the way back.
  ~~Card never knows about live, so it still says Start.~~ That Start would lie.

### 4 — Start sheet + live bar · **done**

**Job.** Shared start / return.

**Gate.** Bar: resume / finish / discard. Sheet does not become a third Resume.

**Floor findings**

- Live sheet primary is **“Go to session”**, not “dismiss and find the bar”.
- Discard from the sheet uses the **same confirm** as the bar.
- Today’s plan is a **filled button**, not a list row.
- Free workout is **quiet** so it does not compete with that button.

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

### 6 — Editor · **done** (this pass)

**Job.** One program.

**Gate.** Targets persist after leave and process death. **No Start on this screen.**

**Look for**

- Empty state that tells you to add a lift but has no action (the button is a
  quieter control further down).
- “Add exercise” vs “Add a lift” — the workout and the picker already say lift.
- A Save that does not write targets (already removed; do not bring it back).

**Won't.** Start / Resume. A second Save. A plate calculator.

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

**Gate.** Changing THIS WEEK / 30 DAYS **does not change recommendations**.
(Already true in `TrainingInsightsCalculator` — coach uses a fixed 14-day basis.
The pass is to keep it that way and to make the map actionable.)

**Look for**

- Contributor rows that name a lift and do not open it.
- Empty / error offering Start when the remedy is retry (error already says retry).
- Window chips that look like they change the advice. If they do, that is a defect.

**Won't.** Day / year windows. Head-level anatomy. LLM coach.

### 9 — History / Session detail · **done** (this pass)

**Job.** Memory + typo fix.

**Gate.** Edit a finished set → heat and volume move. Repeat = a **new** live
session. Repeat while live → “Go to session”, not a silent resume.

**Look for**

- Repeat copy that says Resume.
- A calendar day with two sessions opening only the first (already a sheet).
- Session-missing empty with no way back.

**Won't.** Recomputing duration on a repair. Changing `completedAt`.

### 10 — Settings · **done** (this pass)

**Job.** Prefs + survival.

**Gate.** Unit change re-renders the stack. Restore is **blocked while live**,
and the screen says so *before* the tap, not only after the repository refuses.

**Look for**

- Restore / import still tappable during a live session (repo already throws).
- Backup copy that implies today’s unfinished session is in the file.
- Guided setup re-run that deletes history (it must not).

**Won't.** Scheduled auto-backup. Renaming the Drive folder. Room v3.

---

## Do not open

Plate calculator · rest *sound* design · font-scale 2.0 · scheduled backup ·
Room v3 · emulator instrumented tests on the real applicationId ·
package rename · Drive folder rename · 5th tab · LLM trainer.

Deload decision UI was deferred here. The Tune chip is Job 3 / P2
(ROADMAP option A). The Body card tap that actually marks the week is
Job 4 / P1. Home empty-week volt becomes replay when routines exist
(Job 4 / P2).

---

## Floor findings (newest first)

Record every deviation here. Oldest stay; do not delete.

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

---

## Verification (every page)

- [ ] `./gradlew testDebugUnitTest` — 0 failures
- [ ] `./gradlew assembleDebug`
- [ ] Phone: the page gate, plus any floor finding shipped this turn
- [ ] This file updated if we left the written plan

Last JVM gate: 700 tests, 0 failures (merged `trunk`, 22 Aug 2026). Phone gates still need the owner. Job 4 living plan: [JOB4_ACTION_PLAN.md](JOB4_ACTION_PLAN.md).
