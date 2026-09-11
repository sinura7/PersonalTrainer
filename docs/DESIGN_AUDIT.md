# Personal Trainer — design and quality audit

**Status:** living product spec. This is the list of everything that must be true before the app feels like a high-class gym product, not a functional prototype.

**Bar:** every screen and control is simple, but designed with intent. The user should feel they are holding a piece of gym equipment, not filling out a form. Quality of use on the floor — one-handed, sweaty, noisy, phone in a pocket or on a rack — is the highest requirement. Text-only lists, generic Material chrome, and “it works if you read carefully” are not enough.

**Honest current state:** the data model, rest-timer service, kg/lbs display, progression math, and local-first stack are a solid skeleton. Visually and as a gym-floor workflow, the app is still a first-pass Material 3 shell. Treat everything below as required work, including items that look small.

Reference apps for the bar (not to copy layouts): Strong, Hevy, Future, Apple Fitness. Those products feel physical: lift images, large clocks, decisive haptics, cards that look like plates and machines.

---

## 0. How to use this document

- Items are tagged **P0** (broken / ship-blocker), **P1** (quality of use — do next), **P2** (polish that still matters), **P3** (later, but do not forget).
- IDs are stable (`A-01`, `T-04`, …). Use them in commits and PR notes.
- “Looks fine in a screenshot” is not done. Done means it works with a bar loaded, rest running, and the phone locked or on the home screen.
- Do not add features that compete with the floor workflow until P0s are closed.

---

## 1. Product thesis

This is a **personal training app for strength work**, not a generic habit tracker.

A session on the floor is:

1. See the lift (picture + name + machine/equipment).
2. Load the bar / sit on the machine.
3. Log the set with huge, miss-proof controls.
4. Rest with a clock you can hear and see without opening the app.
5. Get a clear “back to the bar” cue.
6. Move to the next lift without hunting through text.

If any of those six steps feel like admin, the design failed.

### What “simple but heavily designed” means here

- One primary action per moment. No competing CTAs.
- Large type, large hit targets, high contrast. Gym lighting is bad.
- Every lift has a **picture**. Text is the caption, not the product.
- Equipment is first-class: barbell, dumbbell, cable, **machine**, smith, bodyweight. Routines should look like a gym floor, not a grocery list.
- Motion is short and physical (clock ticks, rest ring, card settle). No decorative animation.
- Sound and haptics are part of the UI, not an afterthought.
- Empty states teach the next tap. They are not apologies.

---

## 2. Confirmed product requirements (from the owner)

These are not optional polish. They are part of the product.

| ID | Requirement | Current state | Priority |
|---|---|---|---|
| **R-01** | Adding a lift shows the **exercise image beside the name** so the picker looks physical, not like a contacts list | `ExercisePickerSheet` is `ListItem` + text only. `Exercise` has no image field | ✅ 9 Sep 2026 — picker rows carry `ExerciseThumb` (`ExercisePickerSheet.kt`), the 40 dp keyed still of ADR-022 |
| **R-02** | **Library contains images** for every built-in lift; custom lifts can attach or inherit a placeholder | Library cards are title + muscle chip + buttons | ✅ 9 Sep 2026 — 129 keyed WebP stills in `drawable-nodpi`, held by `tools/check-still-pack.py`; customs inherit the family pose (ADR-022) |
| **R-03** | Rest timer plays a **completion sound** that means “next set starts now” | Generic system notification ringtone via `RestTimerAlerts`, skipped if ringer is silent | ✅ 9 Sep 2026 — bundled `res/raw/rest_done.ogg` through `RestSound`, played on the alarm stream (`RestTimerAlerts.CUE_ATTRIBUTES`, `USAGE_ALARM`) |
| **R-04** | **Last 5 seconds tick** as a warning | Not implemented. No in-app or service tick | ✅ 10 Sep 2026 — `RestTick` (5, 4, 3, 2, 1) posted by `RestTimerService` per boundary; a click (`res/raw/rest_tick.wav` through `RestTickPlayer`, alarm stream) and a 40 ms pulse (`RestTimerAlerts.tick`); Settings **Last five seconds** toggle. Phone awake only; doze keeps the completion cue |
| **R-05** | Rest timer **hovering popup on the phone home screen** so remaining time is visible without opening the app | Foreground notification + chronometer only. No overlay / bubble / widget **— Superseded — see §10.2 banner; the notification + last-5s ticks are the home-screen presence.** | ✅ 9 Sep 2026 — superseded — no launcher overlay; see §10.2 banner |
| **R-06** | Rest timer **must not go negative** | Domain clock clamps to `0`. System notification `Chronometer` countdown can overshoot after `setWhen` is in the past. Users see negatives | **P0** |
| **R-07** | **Delete a routine, then create one, must not crash** | Editor auto-inserts `"Untitled routine"` on `routine/new`, `leave()` deletes the empty stub, ViewModel can keep a deleted id. Reported crash on recreate | **P0** |
| **R-08** | Routines include **machines** as a real format (not just free-weight names in a text list) | Catalog mixes “Leg Press” / “Lat Pulldown” as names only. No equipment type, no machine grouping, no machine card design | ✅ 9 Sep 2026 — `EquipmentType` (`BARBELL`…`MACHINE`…`BODYWEIGHT`) on `Exercise.equipment` (`Models.kt`), shown as the row tag in `RoutineEditorScreen`, carried in `BackupJson` |

---

## 3. P0 defects — fix before more features

### A-01 — Resume workout can show “No lifts yet” after a logged set  [P0]

**Seen on device:** Home → Start → Free workout → add Barbell Back Squat → log warm-up → Keep and exit → Resume. Home still showed **WORKOUT IN PROGRESS**. Active workout showed **No lifts yet**.

**Why this is fatal:** the core loop is “leave the phone, come back, keep logging.” If the lift and set vanish from the screen, the app is untrustworthy.

**Two failure modes, both real in code:**

1. **UI lie.** `ActiveWorkoutScreen` treats `selected == null` as an empty session:

   ```
   selected = session.exercises.firstOrNull { it.exercise.id == state.selectedExerciseId }
   if (selected == null) → “No lifts yet”
   ```

   That empty state is also shown when the session **has** exercises but `selectedExerciseId` does not match (stale draft cache, id mismatch, race before `sessionFlow` assigns the first lift).

2. **Data / restore.** `WorkoutDraftCache` is in-process only. A new `ActiveWorkoutViewModel` on resume reads the cache, then only auto-selects the first lift when `selectedExerciseId == null`. A cached id that is not in `session.exercises` blocks that fallback forever for that VM.

**Must do:**

- Never show “No lifts yet” if `session.exercises` is not empty. Show the chip row + first lift.
- On every session emission, if the selected id is missing from the session, snap to `exercises.first()`.
- Persist selected exercise id with the session (Room or DataStore), not only an in-memory cache.
- Add a resume integration test: log set → leave → new ViewModel → same session id → lift and set visible.
- Confirm Room `observeSession` actually returns `session_exercises` + sets after process death (cache will be empty).

### A-02 — Delete routine then create crashes  [P0]

**Owner report.** Highest-trust recreate path:

1. Create routine (`navigate("routine/new")`).
2. `RoutineEditorViewModel` **immediately inserts** `"Untitled routine"` before the user has typed anything.
3. Back / leave with zero lifts **deletes that row**.
4. Create again, or delete a real routine from the list then tap **+**.

**Likely mechanisms:**

- ViewModel / `SavedStateHandle` still holds the **deleted** UUID. `observeById` goes null. `addExercise` / `saveDetails` / `updateExercise` still fire SQL against a missing parent. FK on `routine_exercises.routineId` can throw. Some paths catch; not all (reorder, `touch`, init races) are guaranteed.
- Navigation reuses the `routine/new` back-stack entry / ViewModel. `init` does **not** run again, so no new row is created. The editor is attached to a ghost id.
- System / predictive back **does not call** `leave()`. Only the top-bar back button does. Stub routines pile up; delete-from-list while an editor instance is still alive is a crash lane.
- `isLoading` is `routineId.value == null`, not “row exists.” After a delete, the screen leaves the spinner and edits a null routine.

**Must do:**

- Stop inserting a row on open. Create the routine on first real save (name + ≥1 lift), or create only after the first lift is added.
- `BackHandler` and top-bar back share one `leave()` path.
- If `observeById` emits null, treat as **gone**: reset id, or pop with a calm message. Never keep writing.
- Creating after a delete must always allocate a **new** id. Do not reuse ViewModels across `routine/new` sessions (`key` the VM on a nonce, or `popUpTo` + fresh navigate).
- Cover with a unit/instrumented test: create → leave empty → create again → add lift → save. And: save routine → delete from list → create → add lift → save.

### A-03 — Rest timer goes negative  [P0]

**Domain is safe.** `RestTimer.remainingSeconds` and `formatClock` clamp to `0`. In-app `-15` cannot go below zero (`RestTimerStore.adjust`).

**What the user sees is the notification chronometer.** `RestTimerService.runningNotification` uses:

```
setUsesChronometer(true)
setChronometerCountDown(true)
setWhen(endAtWall)
```

When `endAtWall` is in the past, the system Chronometer keeps counting **through zero** (`-0:01`, `-0:02`, …) until `onComplete()` cancels the notification. That is a known Android behavior. Samsung’s shade makes it worse because the row stays on screen a beat longer.

**Must do:**

- Do not use countdown Chronometer for a gym rest clock, **or** replace the notification the instant remaining ≤ 0 (same handler, no “until we get around to it”).
- Prefer a custom `contentText` (`1:24 remaining`) updated every second from the service, always `coerceAtLeast(0)`.
- Never show a minus in-app, in the overlay, or in the shade. Overtime, if we add it later, is a **separate** “+0:12 overdue” state, never `-0:12`.

### A-04 — Notification permission can be denied with no recovery  [P0 for gym use]

`RequestRestNotificationPermission` fires once on Active Workout. If the user denies, rest still “runs” in process but the home-screen clock and completion alert are gone. There is no Settings deep-link, no in-workout explanation, no retry.

Without a visible/audible rest cue, the timer product is broken on a locked phone.

### A-05 — 0 kg is a legal working set  [P0 quality]

Warm-up at 0 kg was used in the device walkthrough and looked like a dummy set. The stepper allows `0.0`. Logging 0 × N as a **working** set poisons volume, heat map, and progression.

**Must do:** allow 0 only for warm-up or marked bodyweight/assisted lifts. Block 0 kg working sets on loaded lifts. Distinct bodyweight path (see E-12).

---

## 4. Design system — the app does not have one yet

The theme is a green Material 3 scheme (`Forest` / `Leaf` / `Lime` / `Sand` / `Ink` / `CardDark`) and system `SansSerif`. That is a tint, not a design system.

### D-01 — Type is incomplete and generic  [P1]

`Type.kt` only overrides `headlineLarge/Medium`, `titleLarge/Medium`, `bodyLarge/Medium`, `labelLarge`. Screens use `headlineSmall` and `displayMedium` / `displaySmall`, which fall through to stock Material defaults. No display face for the rest clock. No tabular numbers, so `1:08` and `1:11` jitter. No tracked labels (`REST`, `WEIGHT`, `REPS` should be a dedicated `labelSmall` + letter-spacing token).

**Need:** a real scale — Display (clock), Numeric (weight/reps, tabular), Title, Body, Label, Overline. Prefer one distinctive family for display (e.g. a condensed grotesque) and a readable grotesque for UI. Do not ship system SansSerif as the brand.

### D-02 — Color is “gym green,” not a language  [P1]

Dark mode is the gym mode and should be designed first. Current dark surface (`#0B1A14` / `CardDark`) is fine as a start but:

- Cards and background are too close; hierarchy collapses.
- Lime-on-forest is used for everything primary. Rest-done, rest-running, error, and progression all need **distinct** roles.
- No token for heat, PR, warm-up, machine vs free weight, rest warning (last 5s).
- Light theme exists but is unloved. If we keep it, it must match the same components, not look like a different app.

### D-03 — Shape, elevation, and spacing are default Material  [P1]

`PrimaryGymButton` is 64dp / 16dp radius. Cards are stock. Screen padding is a repeated `20.dp` magic number. No radius scale, no card treatment that feels like a plate or a machine plate. No consistent section gap.

**Need:** 4/8/12/16/24/32 spacing tokens. Card radius 20–24 for lift cards, 28+ for the rest clock. Hairline borders on dark cards instead of relying on elevation (elevation disappears on AMOLED).

### D-04 — No image or illustration layer  [P1]

There are no exercise assets, no empty-state illustrations, no branded mark beyond `ic_launcher_foreground`. A strength app without lift pictures will always feel like a spreadsheet.

**Partly closed, Phase 8 (21 Aug).** Every lift now has a picture: a body figure with its
trained muscles lit and an equipment badge, on picker rows, library rows, the detail header and
the in-workout chips. It is *drawn*, not shipped — Compose `DrawScope` against the Heat and
outline tokens — so there are still no exercise **assets**, and there never need to be. What
this row asked for beyond that is still open: **empty-state illustrations** and a **branded
mark** are untouched and unowned by any phase.

### D-05 — Motion language is missing  [P2]

No rest-ring animation, no tick pulse on last 5s, no settle on log-set, no chip selection motion. `BodyMap` color-lerps; that is the only motion. Add a short spec: 120–180ms standard, 80ms tick, 240ms rest-complete. Never block input on animation.

### D-06 — Haptics are one waveform  [P1]

`RestTimerAlerts` uses a single complete pattern. Need a small palette: tick (light), last-3s (medium), complete (strong), log-set (click), error (double). Respect system haptic intensity.

### D-07 — Iconography is stock outlined Material  [P2]

Home / Body / Routines / Library / History are generic. Fine as placeholders. Replace with a tight custom set once the tab model is locked. Do not mix filled and outlined at random.

### D-08 — Component inventory is too thin  [P1]

Shared pieces today: `EmptyState`, `ScreenLoading`, `ConfirmActionDialog`, `WeightStepper`, `RepsStepper`, `RestTimerBar`, `ExercisePickerSheet`, `PrimaryGymButton`.

Missing, all required for a designed app:

- `ExerciseThumb` (image + equipment badge)
- `ExerciseRow` (thumb + name + muscle + equipment)
- `LiftCard` (workout + routine + library)
- `MachineCard` / equipment chip
- `SetTable` (one history, not two)
- `GymDialog` (same confirm language everywhere)
- `SectionHeader`
- `NumericKeypad` or tap-to-type on the big numbers (steppers alone are slow for 87.5)
- Overlay / bubble rest clock
- Skeleton loaders that match card layout (not a lone spinner)

### D-09 — Loading, error, empty are inconsistent  [P2]

Some screens use `ScreenLoading`, others inline `CircularProgressIndicator`. Errors are often a red `Text` stuffed in the list. Empty states are copy-heavy and unillustrated. Standardize: skeleton → content; inline error banner with retry; empty with one action and a picture.

### D-10 — Typography tokens vs usage  [P2]

Weight/reps use ad-hoc `56.sp`. Rest uses `displayMedium`. Home greeting uses `titleMedium`. Pick tokens and delete one-off sizes.

### D-11 — No dark-specific assets  [P2]

When images land, they need dark-friendly plates (not white-box PNGs on `#0B1A14`).

### D-12 — App icon and splash  [P2]

Launcher is the foreground drawable reused as icon. Needs a real mark that reads at 24dp and on Samsung’s theme icons.

---

## 5. Cross-cutting gym-floor rules

These apply to every screen.

| ID | Rule | Now | Priority |
|---|---|---|---|
| G-01 | Primary tap targets ≥ 48×48, gym primaries ≥ 56dp | Steppers are large (good). Chips, delete icons, RPE chips are finger-fussy | ✅ 9 Sep 2026 — `Metrics.touchMin` 48 dp, `rowMin` / `control` 56 dp, `commit` 72 dp are the only sizes the design system offers (ADR-005, FND-023) |
| G-02 | One-handed: primary actions in the lower half during a session | Rest card is **first** in the scroll; log set is mid-list; finish is at the bottom. On a tall phone the clock eats the fold | ✅ 11 Sep 2026 — `RestDock` sits in `Scaffold.bottomBar` immediately above `LogBar` (`ActiveWorkoutScreen`, tag `workout-rest-idle` / `workout-log-set`). Finish stays in the header: it is not a mid-set act. Landscape still hides idle rest (`LandscapeChrome.hideIdleRest`) |
| G-03 | Keyboard never covers the thing you opened the sheet to pick | Add-lift focuses the search field immediately; IME covers the catalog | ✅ 9 Sep 2026 — `ExercisePickerSheet` no longer auto-focuses the search (no `FocusRequester`); the sheet is nine-tenths of the screen with the catalog under a pinned search |
| G-04 | No duplicate information competing for the same decision | Last set card **and** “This exercise” both show the same set with Edit/Delete | ✅ 9 Sep 2026 — one `Last set ·` line (`RestFloorCopy`) and no second history block in `ActiveWorkoutScreen` |
| G-05 | Never surprise-start rest | Rest card is always mounted, so tapping `1:00` after a warm-up feels like auto-start | ✅ 11 Sep 2026 — idle rest is `Not running` + planned duration in body type, not a live `numeralMd` clock (`RestIdleCopy`, `RestIdleRow`). Start is the only start. After a warm-up the dock says `Warm-up · 1:00` |
| G-06 | Chips show a picture + short name, not a paragraph | Workout lift chips are full names only (`Barbell Back Squat`) and truncate badly | ✅ 9 Sep 2026 — `SessionLiftStrip` chips are a still plus a short name |
| G-07 | Destructive actions confirm with the object name and consequence | Mostly done. Finish vs discard vs keep is still easy to mis-tap (discard is the confirm button) | ✅ 9 Sep 2026 — `LeaveWorkoutDialog` / `LeaveCardioDialog` keep Keep primary and Discard in danger ink; Library asks `Delete ${exercise.name}?` |
| G-08 | Offline is the default; network is backup only | True. Do not regress | — |
| G-09 | Units are a setting, not a fake toggle in the header | Home “Units · kg” looks like it flips units; it opens Settings | ✅ 9 Sep 2026 — weight unit is a Settings row (`SettingsScreen`, `viewModel.weightUnit`); nothing in a header toggles it |
| G-10 | Sounds have meaning: tick = hurry up, tone = stand up | One generic notification sound | ✅ 10 Sep 2026 — tick = `rest_tick.wav` on 5–1 (`RestTick`), tone = `rest_done.ogg` at zero (`RestSound`); two sounds, two meanings |
| G-11 | Keep-screen-on during an active session is correct | Done. Do not extend it to Home | — |
| G-12 | Bottom bar hides on Active Workout / Editor / Settings — good. Returning to a tab must restore scroll | `saveState` is on; verify Library search/filter survive | P2 |

---

## 6. Screen-by-screen

### 6.1 Home

**Job:** “What do I do right now?” One answer.

| ID | Issue | Priority | Status |
|---|---|---|---|
| H-01 | Three ways to resume: primary button, rest card, **In progress** card. Same action three times | P1 | ✅ 1A deleted the rest strip; 6b deleted the in-progress card. `LiveSessionBar` is the only one left |
| H-02 | In-progress + rest card + “Resume workout” + weekly card start = CTA pile-up on a training day | P1 | ✅ 6b — Home carries one filled button, in the hero, and it never says Resume |
| H-03 | Header title is the app name. Should be today: time of day, in-progress lift, or “Upper · 4 lifts” | P1 | ✅ 6b — `MastheadCopy.headline`, pure and tested. It states the **day**, never the live session: the bar owns live |
| H-04 | “Units · kg” is Settings. Relabel to a gear or “Settings.” Unit change stays inside Settings | P1 | ✅ 6b — a bare gear in the masthead; the unit line is gone |
| H-05 | Quick actions (Routines / Library / History) duplicate the tab bar | P2 | ⚠️ 6b — the grid is gone. Two tertiary links remain (This week → Plan, Training calendar → History); they are contextual handoffs from the section above them, not a menu, but they *are* still tab destinations |
| H-06 | Training balance is six unlabeled color dots. Not a body. Not convincing | P1 | ✅ 6b — `TrainingBalanceCard` and the heat tile deleted. The body map is the Body tab's job |
| H-07 | “Ready to progress” empty state on a brand-new install is noise. Hide the section until there is history | P1 | ✅ the section is composed only when it has rows |
| H-08 | Progress cards say “Tap to start a workout” and go to Start Workout, not a prefilled session for that lift | P2 | ⚠️ 6b — a row now opens **that lift**, not a start screen. It still does not prefill a session for it |
| H-09 | Weekly card copy can read like a placeholder (“starter week” / generate copy) | P2 | ✅ 4 — `ThisWeekCard` rewritten; the empty state makes “Suggest a week” the primary action instead of narrating one |
| H-10 | Recent activity duplicates History | P2 | ✅ 6b — `RecentSection` deleted. The last session survives as two numerals in the stat row |
| H-11 | Rest remaining card copy explains the notification. Once overlay exists, copy must match the real surface | P2 | ✅ 1A — `RestRemainingStrip` deleted outright |
| H-12 | Greeting + “Personal Trainer” + unit line is three lines of chrome before the workout | P2 | ✅ 6b — replaced by a date kicker and the headline |

Two rows above are marked ⚠️ rather than ✅ on purpose. H-05 and H-08 were both improved by
6b without being closed, and recording them as delivered would hide the remaining half from
whoever reads this table next.

**Home target layout (designed, still simple):**

1. If rest running → full-width rest strip (clock + lift name). Tap = session.
2. If session in progress → one Resume card (lift thumbnails + set count). No second button.
3. Else → one Start button + today’s planned session (picture row).
4. Optional: one recommendation, one next-progression lift.
5. Settings in the top bar as an icon.

### 6.2 Start workout

| ID | Issue | Priority |
|---|---|---|
| S-01 | When a session is in progress the screen correctly hides new starts. Good. The resume button is then a **third** resume path from Home | P2 |
| S-02 | Routine cards are text. Need first three lift thumbs + machine mix **— Closed 11 Sep 2026: Start Options `RoutineRow` is a `GymCard` with the first three `ExerciseThumb` stills (`RoutineCardCopy.STILL_LIMIT`) and the kit mix (`RoutineCardCopy.mix`)** | P1 |
| S-03 | Free workout has no personality. It should still feel like walking onto the floor (empty rack + add lift) | P2 |
| S-04 | Empty routines CTA should create a routine, not only send you away | P2 |

### 6.3 Active workout (the product)

This screen **is** the app. It is currently a vertical form: rest card, chip row, titles, suggestion card, steppers, warm-up switch, RPE chips, log button, last-set card, set list, notes, finish.

| ID | Issue | Priority |
|---|---|---|
| W-01 | Rest card always occupies the top, even when idle. It should collapse to a slim “Rest 1:30” control until running or just finished **— Closed 9 Sep 2026: idle rest is a 56 dp control (`LandscapeChrome.REST_IDLE_DP`, tag `workout-rest-idle`), hidden outright in landscape** | P1 |
| W-02 | Warm-up + visible rest + preset chips feels like rest already started (device walkthrough) | ✅ 11 Sep 2026 — idle dock and floor say `Not running`; the floor ring is empty until Start; chips stay duration pickers under that kicker (`RestIdleCopy`) |
| W-03 | Add-lift sheet: search field auto-focuses; keyboard covers the default catalog. **Show the pictured grid first.** Search is explicit (icon), not auto-IME **— Closed 9 Sep 2026: `ExercisePickerSheet`: nine-tenths height, search pinned and not auto-focused, catalog with stills beneath; the create row appears only when nothing matches** | P1 |
| W-04 | Picker rows are name + muscle. **Image required** (R-01) **— Closed 9 Sep 2026: `ExerciseThumb` on every picker row (`ExercisePickerSheet.kt`)** | P1 |
| W-05 | Picker always shows “Muscle group for new exercise” — create-mode chrome on every search **— Closed 9 Sep 2026: the permanent muscle-group field is gone; muscle chips sit on the create row, which exists only when the query matches nothing** | P1 |
| W-06 | Lift chips have no thumb, no set progress (`2/5`), no rest badge **— Closed 11 Sep 2026: still (`ExerciseThumb`), `2/5` (`LiftChipCopy.marks`), and a rest badge (`LiftChipCopy`) on `WorkoutLiftCard`; live rest is remaining time in RestCyan** | P1 |
| W-07 | “No lifts yet” vs missing selection — see A-01 | P0 |
| W-08 | Duplicate set history: **Last set this lift** and **This exercise** list the same latest set with two Edit/Delete pairs **— Closed 9 Sep 2026: one `Last set ·` line (`RestFloorCopy`); the second block is gone** | P1 |
| W-09 | Only the latest set is editable in the list; the last-set card also edits it. One place | P1 | ✅ Any logged set is selectable now, and selection reveals the two actions. The gate was `if (isLatest)` in `SetRow` alone — `editSet(setId)`/`deleteSet(setId)` already resolved a row by id, and `updateSet` already preserved `completedAt` and `setNumber` so a revision cannot re-date a record. `isLatest` still earns its keep: it draws the Volt rail |
| W-10 | Suggestion card is large and pushes the steppers down. Collapse to one line: `Last 100 × 5 → 102.5` + Use **— Closed 9 Sep 2026: the suggestion is one in-set line (`SetMicroRecCopy.line`, `RestFloorCopy`)** | P1 |
| W-11 | Warm-up is a switch with no visual change to the log button (`Log warm-up` vs `Log set`) **— Closed 11 Sep 2026: `LogBarCopy.commit` is `Log set ·` vs `Log warm-up ·` plus the draft (`WorkoutLogBar`)** | P1 |
| W-12 | RPE 6–10 as chips is fine; it sits between reps and Log, adding scroll before the primary tap | P2 |
| W-13 | Session notes on the live logging screen are in the way. Move to finish or a overflow | P2 |
| W-14 | Finish is disabled until a set is logged (good). Discard is the **confirm** button on the leave dialog; Keep and exit is dismiss. Invert: Keep is default, Discard is the destructive text action | P1 | ✅ `LeaveWorkoutDialog`: **Keep and exit** is the `PrimaryGymButton`, "Discard this workout instead" is a `DangerGymButton` beneath it, stacked full-width; `LeaveCardioDialog` matches. Row was stale when re-read 9 Sep 2026 |
| W-15 | Close icon means leave, not discard — but it opens a dialog whose primary is Discard. Easy to kill a session | P1 | ✅ Same dialog: Close opens Keep-first; Discard is the danger act, never the default |
| W-16 | No “next lift” preview. After last set of a lift, the UI should offer the next routine lift with picture **— Closed 9 Sep 2026: finishing a prescribed lift offers the next unfinished one (`advanceToNextLift`, `#185`)** | P1 |
| W-17 | No plate math, no bar + plates graphic | P2 |
| W-18 | No rest-per-set history (how long they actually rested) | P3 |
| W-19 | No supersets / circuits / alternating | P3 |
| W-20 | Weight stepper cannot jump to 87.5 quickly. Need tap-number → keypad **— Closed 9 Sep 2026: tapping the number opens a decimal keypad (`KeyboardType.Decimal` field in `Common.kt`; `ActiveWorkoutJourneyInstrumentedTest.seedSession_typeWeight_…`)** | P1 |
| W-21 | No exercise image on the logging header. Name only **— Closed 9 Sep 2026: `ExerciseThumb` in the logging header (`ActiveWorkoutScreen.kt`)** | P1 |
| W-22 | Permission prompt has no copy. Android’s naked dialog is the first gym-session impression **— Closed 9 Sep 2026: rationale copy precedes the `POST_NOTIFICATIONS` request and reports what rest can do afterwards (`ActiveWorkoutScreen.kt`)** | P1 |
| W-23 | `keepScreenOn` is correct; pair it with a dimmable rest clock for rack-mounted phones | P2 |

**Active workout target (simple, designed):**

```
[ Lift thumb | Barbell Back Squat          2 / 5 ]
[ Set 3 · target 5 @ 100 kg                      ]
[          100.0 kg          ]   huge, tap to type
[            5 reps          ]
[ Log set ]                      primary, always visible
[ ○ ○ ● ○ ○ ]                    sets this lift
[ Rest 1:30 ▸ ]                  collapsed; expands when running
```

Images on the lift switcher. Rest becomes a full-screen-feeling card only while counting.

### 6.4 Rest timer (in-app + system)

| ID | Issue | Priority |
|---|---|---|
| T-01 | Completion sound is the **default notification ringtone** — not a gym cue **— Closed 9 Sep 2026: bundled `rest_done.ogg` cue (`RestSound`), not the ringtone** | P1 |
| T-02 | No last-5-second tick (R-04) **— Closed 10 Sep 2026: `RestTick` + `RestTimerService.scheduleTick`, one runnable per boundary, re-asked on ±15 s** | P1 |
| T-03 | No distinct “stand up” stinger separate from the shade notification **— Closed 9 Sep 2026: the cue is its own `MediaPlayer` on `USAGE_ALARM` (`RestTimerAlerts`), separate from the shade notification** | P1 |
| T-04 | Silent ringer skips sound entirely. Offer a workout override (media/alarm stream) with an explicit setting, default off **— Closed 9 Sep 2026: by decision: the cue rides the alarm stream, which a silent ringer does not mute; sound has its own toggle (`setRestSoundEnabled`) rather than a media override** | P1 |
| T-05 | Vibration is only on complete. Add tick pulses on 5–1 **— Closed 10 Sep 2026: `RestTimerAlerts.tick` — a 40 ms `createOneShot` pulse per tick under the Vibration toggle** | P1 |
| T-06 | Notification Chronometer can go negative (A-03) | P0 |
| T-07 | No overlay / bubble on the launcher (R-05). See §8 **— Superseded — see §10.2 banner.** **— Closed 9 Sep 2026: superseded — see §10.2 banner** | P1 |
| T-08 | Home rest card and in-workout card can disagree for a frame (different collectors) | P2 |
| T-09 | Presets are only 60/90/120. Need 30s (accessories) and 180s (heavy compounds) | P2 |
| T-10 | Custom parse is good (`90` / `1:30`). Dialog chrome is generic | P2 |
| T-11 | Skipping rest should feel immediate (haptic + card collapse). | P2 |
| T-12 | Rest does not auto-start after warm-up (correct). UI does not make that obvious | ✅ 11 Sep 2026 — `RestTimer.shouldStartAfterLog` still refuses warm-ups; idle copy names it (`RestIdleCopy.afterWarmupHint`, dock `Warm-up · 1:00`) |
| T-13 | Per-lift rest from the routine is in `secondsToStart` but the big card always looks like a global timer | P2 |
| T-14 | Done notification copy “Back to the bar.” is good. Channel still uses the generic sound | P2 |
| T-15 | `-15` / `+15` / Skip on the notification are unlabeled icon-less text. Fine. Keep them  | — |
| T-16 | Samsung battery Unrestricted is documented in SETUP. In-app, first rest should mention “Allow unrestricted battery or the clock dies” | ✅ 11 Sep 2026 — first running rest shows `RestBatteryCopy.SENTENCE` on the dock and floor until Got it (`REST_BATTERY_HINT`, device-local). No overlay permission |
| T-17 | No in-app tick audio while the activity is visible (service only alerts on complete) **— Closed 10 Sep 2026: the service ticks whenever rest runs with the process alive, in-app and in the shade alike (`RestTickPlayer`, alarm stream)** | P1 |

### 6.5 Routines list

| ID | Issue | Priority |
|---|---|---|
| U-01 | Cards are a title, a count, and a truncated text list. No images, no equipment mix **— Closed 9 Sep 2026: `CustomWeekScreen` cards carry `SessionLiftStrip` stills and the equipment tag** | P1 |
| U-02 | Delete icon fights the card tap. Easy to delete | P2 |
| U-03 | Empty untitled stubs can appear if system back skipped `leave()` **— Closed 9 Sep 2026: `RoutineEditorViewModel.discardEmptyStub()` removes an untitled, liftless stub on exit** | P1 |
| U-04 | No last-performed date, duration estimate, or muscle tags | P2 |
| U-05 | “Add at least one lift before starting” is correct; still looks like a broken card | P2 |
| U-06 | Library icon in the app bar duplicates the Library tab | P3 |

### 6.6 Routine editor

This is the second most important design surface after Active Workout. Today it is a **form**: name, notes, save details, add exercise, then cards of number fields.

| ID | Issue | Priority |
|---|---|---|
| E-01 | Auto-create untitled + delete on leave (A-02) | P0 |
| E-02 | Title is always “Edit routine,” including on create | P2 |
| E-03 | Save is a separate button from adding lifts. Users expect autosave **— Closed 9 Sep 2026: targets are staged and flushed (`stageTargets` / `commitTargets` / `flushStagedTargets`) and details persist on exit (`persistDetailsOnExit`); there is no Save gate on adding lifts** | P1 |
| E-04 | Targets are tiny `OutlinedTextField`s. Use the same large steppers as the workout, or a compact stepper row **— Closed 11 Sep 2026: expanded editor cards use a 2×2 of `NumeralWell` plates (`CompactTargetFields`), same tap-to-type `NumberEntryDialog` as the workout; no `OutlinedTextField` on the card** | P1 |
| E-05 | No lift image on the row **— Closed 9 Sep 2026: `SessionLiftStrip` still on every editor row** | P1 |
| E-06 | No equipment / machine field (R-08) **— Closed 9 Sep 2026: `Exercise.equipment` shown as the row tag (`RoutineEditorScreen`)** | P1 |
| E-07 | Reorder by two arrow buttons. Need drag handle | P2 |
| E-08 | “Update targets” is an extra tap. Changing a field should persist **— Closed 9 Sep 2026: a changed field persists through `commitTargets`; no Update tap** | P1 |
| E-09 | Add-exercise picker is the same text sheet as the workout (W-03–W-05) **— Closed 9 Sep 2026: one `ExercisePickerSheet` serves the workout, the editor and the Library (`ExerciseRow` is shared by design)** | P1 |
| E-10 | Pending default targets (3×5, 90s) are invisible until after add | P2 |
| E-11 | Notes field is a second text box on a gym-programming screen. Collapse | P2 |
| E-12 | No way to mark a lift bodyweight / assisted / machine stack vs plates **— Closed 11 Sep 2026: custom create/edit carries `LoadType` chips (`LoadTypeChipRow`); picker create writes the chosen load (`ExercisePickerEvent.Created`); editor/library/swap rows name plates vs stack (`LoadTypeCopy.rowTag`); a bodyweight card has no kilogram well** | P1 |
| E-13 | No supersets | P3 |
| E-14 | Cannot preview “how this routine will look on the floor” | P2 |

**Routine editor target:** a vertical stack of **lift cards** (thumb, name, equipment, 3 × 5 @ 100, rest 1:30). Tap a card to edit targets in a designed sheet. Name is an inline title. Autosave.

### 6.7 Library

| ID | Issue | Priority | Status |
|---|---|---|---|
| L-01 | **No images** (R-02). This is the catalog. It must look like a gym wall chart | P1 | ⏳ Phase 8, optional. The 40dp slot is reserved and `imageKey` ships null |
| L-02 | Cards are dense admin (Custom/Built-in, Add to routine, edit, delete) | P1 | ✅ the row carries one action; edit and delete moved behind an overflow |
| L-03 | Muscle chips only. Need **equipment chips**: Barbell, Dumbbell, Cable, Machine, Smith, Bodyweight | P1 | ✅ 7 — a second chip row, AND-combining with the muscle row |
| L-04 | Built-in notes are empty. No setup cues, no machine instructions | P2 | ❌ not done. The packet made cues explicitly optional and never blocking; 98 rows of authored coaching text is its own piece of work |
| L-05 | Add-to-routine dialog is a list of text buttons, defaults 3×5 with no preview | P1 | ✅ 11 Sep 2026 — `AddToRoutineSheet` is a landing `GymCard` (still, Work/Rest from `AddDefaults`) plus destination `GymCard`s with the first three stills (`AddToRoutineCopy`); Library and the lift page share it |
| L-06 | Creating a routine from this dialog dumps you into the broken `routine/new` path (A-02) | P0 | ✅ the path itself was repaired earlier — `RoutineEditorPolicy` drops the `new` sentinel instead of inserting eagerly, and the editor has an explicit MISSING phase. Not re-verified on device in Phase 7 |
| L-07 | Search + chips + FAB is standard. Grid of pictured tiles would match R-01/R-02 | P1 | ⚠️ 7 — search and filtering now scale to 98 (escaped LIKE, nicknames, family grouping, equipment chips). The pictured **grid** is Phase 8 |
| L-08 | Custom-only edit/delete is correct. Built-ins still need a detail page (image, muscles, equipment, last weight) | P1 | ✅ every lift opens its detail page; the image is Phase 8 |
| L-09 | “Other” muscle on blank create is a junk bucket | P2 | ❌ not done. Picker-created customs still default to a blank group |
| L-10 | Catalog is ~38 lifts and missing common machines (leg press variants, hack squat, pec deck, seated row machine, hip abduction, preacher, etc.) | P1 | ✅ 7 — 98 lifts. Every machine named here ships: hack squat, leg press calf raise, pec deck, machine seated row, hip abduction, preacher curl |

### 6.8 Body map

| ID | Issue | Priority |
|---|---|---|
| B-01 | Schematic hotspots, not an anatomical illustration. Fine as v1, not “high class” | P2 |
| B-02 | Empty until history — the tab is a dead end on first launch **— Closed 11 Sep 2026: first-launch Body names catalog lifts (`BodyExplorer.coverage`) under the figure; a muscle with no logged work opens the lifts that train it (`BodyExplorer.forMuscle`) instead of an empty sheet. No Start Volt. Library stays pushed.** | P1 |
| B-03 | Recommendations can still read generic. They should name **lifts you already have** with pictures **— Naming closed 21 Aug 2026 (Phase 5): every muscle-targeted card resolves one lift the owner already has, preferring routines over recent history and filtered by the equipment they say they own, and taps through to that lift. Pictures remain open (Phase 8).** | P1 |
| B-04 | Home dots + this tab tell the same story twice | P2 |
| B-05 | Front/back toggle is easy to miss **— Closed 9 Sep 2026: the Front / Back chips sit in their own strip under the figure, beside a facts line that says what the figure was built from ("3 sessions this week · last finished yesterday"); an empty Day or Week offers **Show this month** in one tap.** | P2 |

### 6.9 Schedule

| ID | Issue | Priority |
|---|---|---|
| C-01 | Planner copy can feel generated / placeholder | P2 |
| C-02 | Suggested days that point at a **deleted routine** need a rebuild, not a crash or empty start **— Closed 21 Aug 2026 (Phase 4). Deleting a routine cascades its schedule slots away and the derived week heals; a pin whose routine survives but is empty now returns an explicit `Failed` naming the routine, instead of silently starting a free workout under its name.** | P1 |
| C-03 | Starting a day should show the lift picture row | P2 |
| C-04 | Prefs live in Settings; the week lives on a stack screen. Easy to miss **— Closed 21 Aug 2026 (Phase 4). The week is a tab (Plan) with the preferences behind its own Tune toggle; Settings keeps the preference card and loses the navigation row.** | P2 |

### 6.10 History and session detail

| ID | Issue | Priority |
|---|---|---|
| I-01 | List of text cards. No lift thumbs, no weekly chart, no PRs **— PRs closed 21 Aug 2026 (Phase 6a): a Records section at the foot of History shows the standing bests, one per lift, newest first, each tapping through to that lift. The weekly chart is not planned.** **— Calendar and horizon readout closed (H1, 3 Sep 2026).** **— Closed 11 Sep 2026: History list cards picture the first three lifts (`HistoryCardCopy.STILL_LIMIT`, `SessionLogRow`)** | P1 |
| I-02 | Volume as a single number is opaque without a sparkline | P2 |
| I-03 | No calendar heat, no compare-to-last **— Calendar half closed 21 Aug 2026 (Phase 6a): the training calendar sits above the log, days are grouped by month with a pinned month header, and a day holding more than one session opens a sheet instead of silently picking the first. Compare-to-last remains open.** | P2 |
| I-04 | Session detail is grouped text. Should look like a filled program sheet **— Closed 11 Sep 2026: `FilledLiftCard` is the program/floor card filled in (CountBadge, still, Work/Rest/Load, `SetCopy.setLine` table); Edit still opens the repair sheet** | P1 |
| I-05 | Cannot favorite a session into a routine | P2 |
| I-06 | No photos / gym notes media | P3 |
| I-07 | Duration is minutes stored on finish — confirm it is real elapsed time, not a stub | P2 |

### 6.11 Settings

| ID | Issue | Priority |
|---|---|---|
| N-01 | Rest sound/vibrate toggles exist; no preview button (“play complete cue”) **— Closed 11 Sep 2026: Settings Rest timer row `Play complete cue` (`RestCompleteCue.TITLE`, tag `settings-play-complete-cue`) calls `RestTimerAlerts.preview` — the same bundled `rest_done.ogg` on the alarm stream as 0:00. Sound is forced on for the sample so the row is never silent; vibration follows the switch** | P1 |
| N-02 | No last-5s tick toggle (will need one) **— Closed 10 Sep 2026: the Last five seconds row under Rest timer (`RestTick.TITLE`, `setRestTickEnabled`), device-local, not in the backup document** | P1 |
| N-03 | No overlay permission row (will need one) **— Superseded — no overlay permission row will be added; see §10.2 banner.** **— Closed 9 Sep 2026: superseded — see §10.2 banner** | P1 |
| N-04 | Backup is solid conceptually. Restore needs a brutal confirm (it already should; verify copy) **— Closed 9 Sep 2026: restore previews (`RestorePreview`), confirms (`confirmRestore`) and refuses while a session is live (`BackupRepository.refuseIfLive`)** | P1 |
| N-05 | Units explanation is clear. Good. Keep it | — |
| N-06 | About / version is enough. No gym profile (bodyweight, plates available) yet — needed for plate math and bodyweight lifts | P2 |

### 6.12 Navigation and information architecture

| ID | Issue | Priority |
|---|---|---|
| NAV-01 | Five tabs (Home, Body, Routines, Library, History) is a lot for a logging app. Body and History are secondary. Consider Home / Workout / Program (Routines+Library) / You (History+Body+Settings) **— Closed 20 Aug 2026 — adjudicated by the decision recorded in ROADMAP.md § Decisions D1, which the owner signs by circling one option (four tabs Home · Body · Plan · History, recommended; or three tabs Home · Body · Plan with Body absorbing History). Either option demotes Library to a pushed screen and lands the LiveSessionBar as chrome. D1 is the single record of the chosen option. The Home/Workout/Program/You grouping suggested in this row is superseded either way.** | P2 |
| NAV-02 | Settings and Schedule are stack-only from Home. Fine if Home header is obvious **— Closed 21 Aug 2026 (Phase 4). Schedule dissolved into the Plan tab; Settings gained a second named home in the Plan header's gear.** | P2 |
| NAV-03 | Library `?muscle=` navigation with `restoreState = false` can surprise scroll/filter **— Closed 21 Aug 2026 (Phase 6a). Library stopped being a tab, so a filtered jump is an ordinary push and the restoreState hack is deleted along with the `isTabRoute` prefix-matching shim.** | P2 |
| NAV-04 | Active workout `launchSingleTop` is correct. Routine editor does **not** use it and shares the `"new"` argument — related to A-02 | P0 |
| NAV-05 | Deep link from the rest notification is implemented. Overlay tap must use the same `sessionId` extra **— Closed 9 Sep 2026: superseded — no overlay; the notification deep link carries `sessionId`** | P1 |

---

## 7. Data model gaps (design cannot fake these)

`Exercise` today:

```
id, name, muscleGroup, notes, isCustom
```

That is why everything looks like text.

**Required fields (migration — DB is still version 1, `exportSchema = false`):**

| Field | Why |
|---|---|
| `imageKey` | Built-in drawable / asset name. Library, picker, chips, routine cards, history |
| `equipment` | `BARBELL`, `DUMBBELL`, `CABLE`, `MACHINE`, `SMITH`, `KETTLEBELL`, `BAND`, `BODYWEIGHT`, `OTHER` |
| `machineKind` | Optional: `LEG_PRESS`, `CHEST_PRESS`, `LAT_PULLDOWN`, `SEATED_ROW`, `HACK_SQUAT`, `PEC_DECK`, `LEG_EXTENSION`, `LEG_CURL`, `CALF_RAISE`, `SMITH_GENERIC`, … |
| `primaryMuscle` / `secondaryMuscles` | Replace free-string `muscleGroup` for heat + filters (`CanonicalMuscle` already exists) |
| `loadType` | `EXTERNAL` / `BODYWEIGHT` / `ASSISTED` / `STACK` — drives 0 kg rules and UI |
| `defaultRestSeconds` | Per lift, not only per routine line |

~~`RoutineExercise` should store equipment override~~ **Superseded 20 Aug 2026 (D4 cut list):** an equipment variant is its own catalog row with its own history and PRs, grouped by `movementKey`; no per-routine override column will exist.

Backup JSON must version these fields. Old backups stay valid.

`exportSchema = false` should flip to true before the first migration.

---

## 8. Exercise images — how this should work

**Goal:** every time a lift is listed, a picture sits to the left (row) or above (tile). No exceptions in picker, library, routine, workout chips, or history.

### Visual spec

- Thumb: 56×56 in rows, 72×72 in workout header, 96×96 in library grid.
- Dark background, subject centered, no tiny white padding.
- Equipment badge (machine / barbell / dumbbell) on the corner.
- Missing image: stylized silhouette by equipment, **never** an empty gray box, never a broken-image icon.

### Content

- Ship drawings or photos for the entire default catalog (~38 now, more once machines are added).
- Style: one consistent illustrated set (preferred — readable at 56dp, dark-mode safe) **or** one photo set. Do not mix.
- Custom lifts: pick from the silhouette set, or reuse a built-in image (“this is a variant of Seated Cable Row”).
- Do not block logging on a missing bitmap.

### Implementation notes

- Key images by `imageKey`, not by display name (names change).
- WebP in `res/drawable-*` or `assets/exercises/`.
- Coil/Glide only if we later load user photos; built-ins should be resources (offline, instant).
- Picker: **2-column pictured grid** of the catalog when query is empty. Filter chips for muscle **and** equipment. Keyboard closed until the user taps search.

---

## 9. Machines in routines — format, not a footnote

“Add machines” does not mean extra rows of text. It means the routine **reads like a floor plan**.

### Design

- Group or badge by equipment: **Free weight** / **Cables** / **Machines**.
- Machine cards use a stack/selector visual (not a barbell illustration).
- Name format: `Leg Press · Machine` not only `Leg Press`.
- Targets on stack machines may be **plate + stack** later; v1 can stay kg if we label “stack.”

### Catalog work

Expand `DefaultExercises` with first-class machine entries (and images):

- Leg press, hack squat, smith squat
- Chest press, pec deck / seated fly
- Lat pulldown, seated row (machine), assisted pull-up
- Shoulder press machine, lateral raise machine
- Leg extension, sitting/lying curl, seated calf, hip abduction/adduction
- Cable variants already in the list should be tagged `CABLE`, not generic “Back”

### Routine builder

- Add-lift sheet sections: Machines / Free weight / Cable / Bodyweight.
- A routine that is “machine day” should be creatable in a few taps and **look** like one.

---

## 10. Rest audio, ticks, and home-screen presence

### 10.1 Sound design

| Moment | Sound | Haptic |
|---|---|---|
| Rest starts | Optional soft click | Light |
| Last 5s | Short tick each second (dry, not a ringtone) | Light tick |
| Last 1s | Higher tick | Medium |
| 0:00 | Distinct two-note “up” cue — **not** the SMS ringtone | Strong waveform (already close) |

Ship short raw/ogg assets in `res/raw/`. Do not rely on `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)`.

Settings:

- Sound on/off
- Tick on/off
- Vibration on/off
- Preview buttons
- “Play over silent” (alarm/media stream) — **off by default**, explained

Play ticks from the **foreground service** so they continue when the activity is stopped. In-app clock can mirror for when the screen is on.

### 10.2 Hovering popup on the home screen (R-05)

> **Superseded 20 Aug 2026.** ROADMAP Phase 6 records the decision **not** to build the
> overlay bubble, and D4 reaffirms it. The FGS notification (non-negative, fixed under
> A-03) plus the last-5s ticks are the glanceable rest surface. This section is retained
> as the analysis that informed the decision; do not implement it.

**Wanted:** glanceable rest while the launcher is showing.

**Android reality:**

| Approach | Reliability | Notes |
|---|---|---|
| Ongoing notification + chronometer | High | Already there. Fix negatives. This stays the **fallback** |
| `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY` bubble | Medium | Needs a special permission screen. Samsung / Xiaomi / ColorOS often bury or reset it. Battery savers kill overlays |
| Android 11+ bubbles | Medium-low | Conversation-oriented; awkward for a timer |
| App widget + exact updates | Medium | Good glance, poor 1Hz updates without FGS |
| Full-screen rest Activity (lock screen) | High for “on the rack” | Use for last 5s optional |
| Picture-in-picture | Wrong product | Video-shaped |

**Spec to implement:**

1. Keep the FGS notification as the source of truth (always).
2. Add an **optional** compact overlay: circular or pill clock, ~72–88dp, remaining `m:ss`, lift name optional, tap → session. Draggable. Last 5s visual pulse.
3. Settings row: “Show rest over other apps” → system overlay permission. If denied, explain that the notification is the clock.
4. Never make overlay the only cue. If Samsung kills it, ticks + notification still work.
5. Do not use accessibility overlays.

Document Samsung: overlay + Unrestricted battery + notification channel importance.

---

## 11. Workflow critique (happy path vs what we have)

**Designed happy path**

1. Open app → one obvious Start for today’s session.
2. Session opens on lift 1 with **picture**, last weight loaded, target visible.
3. Log set (huge controls) → rest clock takes over, overlay + notification + ticks.
4. At 0:00, sound + haptic + “Set 2.”
5. After last set of the lift, next lift card (picture) is already there.
6. Finish → history card that looks like the session you lived.

**Today’s path (device + code)**

1. Home is a dashboard of sections.
2. Start is a second screen.
3. Free workout is an empty form.
4. Add lift is a keyboard-first text search.
5. Log is solid (steppers are the best-designed piece in the app).
6. Rest card is always on; warm-up makes it confusing.
7. Leave dialog’s primary action is Discard.
8. Resume can show an empty session (A-01).
9. Rest in the shade can read negative (A-03).
10. History is a receipt, not a story.

The steppers, keep-screen-on, single in-progress session, and FGS rest are the parts that already understand the gym. Everything around them still understands a CRUD app.

---

## 12. Copy and microcopy (small, still required)

| ID | Location | Problem |
|---|---|---|
| X-01 | Leave dialog | Confirm = Discard. Default must be Keep |
| X-02 | Rest idle | “Starts after a working set. Or tap a preset.” — too long; also easy to miss |
| X-03 | Home units button | Reads as a toggle |
| X-04 | “Personal Trainer” as Home H1 | Brand, not context |
| X-05 | Library “Built-in” | Users do not care. Show equipment |
| X-06 | “Untitled routine” | Should never persist |
| X-07 | Progression “Tap to start a workout” | Weak |
| X-08 | Done notification | Keep “Back to the bar.” |
| X-09 | Permission | Need one sentence before the system dialog |
| X-10 | Routine save “Saved.” | Autosave should make this unnecessary |
| X-11 | Empty Home sections | Too many empty sermons on first launch |

Tone: short, physical, second person. No feature-explainers on the logging screen.

---

## 13. Accessibility and internationalization

| ID | Issue | Priority |
|---|---|---|
| ACC-01 | Many icons have no content description (`contentDescription = null`) | P2 |
| ACC-02 | Clock must announce remaining time to TalkBack on change, not every tick if that spams | P2 |
| ACC-03 | Color-only heat dots fail contrast and color-blind users — need labels **— Closed 9 Sep 2026: `HeatLegend` names every band in a kicker and the rows carry the numeral; colour is never the only channel (ADR-023)** | P1 |
| ACC-04 | Hard-coded English everywhere — acceptable for v1, do not bake sentences into images | P3 |
| ACC-05 | Weight labels must keep unit audible (“100 kilograms”) | P2 |

---

## 14. Engineering quality that affects feel

| ID | Issue | Priority |
|---|---|---|
| Q-01 | Room v1, `exportSchema = false` — migrate before images/equipment **— Closed 9 Sep 2026: Room is v4 with `exportSchema = true` (ADR-010); images and equipment shipped on it** | P1 |
| Q-02 | Draft cache is process-scoped — resume after death depends entirely on Room | P0 |
| Q-03 | Routine editor side effects in `init` | P0 |
| Q-04 | `headlineSmall` used but not in the type theme | P2 |
| Q-05 | Exercise picker empty-query **does** load the full catalog (`search("")` → `observeAll`). The UI still *feels* empty because the keyboard covers it **— Closed 9 Sep 2026: `ExercisePickerSheet` shows the catalog under the search from the first frame** | P1 |
| Q-06 | Seed only if table count is 0 — adding new default machines later will not appear for existing installs. Need a versioned catalog upsert **— Closed 9 Sep 2026: seeding is versioned: `catalogVersion` in seed meta against `DefaultExercises.CATALOG_VERSION` (`DbMaintenance.seedCatalog`)** | P1 |
| Q-07 | Backup must include new exercise fields and not drop images keys **— Closed 9 Sep 2026: `BackupJson` writes `equipment`, `loadType` and `imageKey`** | P1 |
| Q-08 | Domain tests exist (good). Missing: editor lifecycle, resume selection, timer display clamp, overlay permission denied **— Closed 9 Sep 2026: `RoutineEditorViewModelTest` (lifecycle, resume, stubs), ten `RestTimer*Test` classes; overlay permission is superseded** | P1 |
| Q-09 | No Android SDK in some CI environments — keep domain tests host-runnable; add instrumented tests for A-01/A-02 **— Closed 9 Sep 2026: CI runs the SDK; `connectedDebugAndroidTest` is the instrumented lane (`ci.yml`)** | P1 |
| Q-10 | Notification permission result ignored | P0 |
| Q-11 | `OnConflictStrategy.REPLACE` on routines can orphan child rows if ids are reused carelessly | P2 |

---

## 15. Best-in-class gaps (explicit non-goals until P0/P1)

Do not start these until the floor loop is beautiful and reliable:

- Social / shares / following
- Video coaching / AI chat
- Store / Play listing work
- Wear OS (after overlay + ticks are right; then it becomes the right clock)
- Nutrition
- Full periodization planner
- Photo body scans
- Supersets, circuits, EMOM
- Plate calculator (P2 — high value, after images)
- Gym-level machine brand models
- LLM / chat "AI trainer" — reaffirmed 20 Aug 2026 (D4). The coach is the rule engine,
  made honest and specific; it works offline.
- Muscle-head-level granularity ("front head of the triceps"). Weighted primary/secondary
  credit is the honest ceiling of set-log data; a later sub-group split for delts and back
  only is the recorded maybe.
- Day and year heat windows. The decision-relevant horizons are This week and Last 30
  days, on absolute weekly-set bands (game-plan Phase 5).

Wear and a plate calculator will matter. They are not the current hole. Images, machines, timer sound/tick/overlay, resume, and “delete routine / create” are.

---

## 16. Priority backlog (do in this order)

### Wave 0 — trust

1. **A-01** Resume always shows the real lifts and sets.
2. **A-02** Routine create/delete lifecycle; no untitled stubs; no crash.
3. **A-03** Timer never displays negative anywhere.
4. **A-04 / Q-10** Notification permission with recovery.
5. **A-05** 0 kg working-set rule.

### Wave 1 — physical catalog

6. Exercise model + migration: `imageKey`, `equipment`, `machineKind`, `loadType`.
7. Illustrated assets for the full catalog (R-01, R-02).
8. `ExerciseRow` / library grid / picker grid — keyboard not auto-shown.
9. Machine section in picker and routine cards (R-08).
10. Versioned catalog seed so existing installs get new machines.

### Wave 2 — rest as a gym instrument

11. Custom complete sound + last-5s ticks + haptics (R-03, R-04).
12. Settings: preview, tick toggle, play-over-silent.
13. ~~Optional overlay bubble + permission row (R-05)~~ **Cut 20 Aug 2026 (D4); see §10.2 banner. Nothing replaces it in this wave.**
14. Collapse idle rest card; invert leave-dialog defaults.
15. Samsung / battery copy in-app.

### Wave 3 — design system pass

16. Type scale (tabular clock), dark-first color roles, spacing/radius tokens.
17. Rebuild Home around one CTA.
18. Rebuild Active Workout around log-set + picture + collapsed rest.
19. One set table; kill duplicate last-set card.
20. Routine editor as lift cards with autosave and large targets.
21. History cards with thumbs; session detail as a program sheet.

### Wave 4 — depth

22. Tap-to-type weight keypad.
23. Next-lift preview.
24. Plate calculator.
25. Per-lift rest clarity.
26. Body map illustration pass.
27. IA trim (tabs) if Home is doing its job.

---

## 17. Acceptance — “best possible” for this stage

The app is not done when features exist. It is done when:

- A new user can start a free workout, **see squat as a picture**, log a set, pocket the phone, **hear ticks at 5**, **see the clock in the notification shade** (non-negative, always), and come back to the **same lift and sets**.
- They can delete a routine and immediately create another without a crash or an “Untitled routine” leftover.
- A machine-day routine looks like machines, not a paragraph of names.
- Every component is simple: few actions, large type, one picture, one primary button.
- Nothing important is only text.

Until then, this remains a strong prototype. That is a good starting point. It is not yet the product.
