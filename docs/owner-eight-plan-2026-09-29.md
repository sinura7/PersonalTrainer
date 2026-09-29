# Plan — the owner's eight asks of 29 September 2026

> Status: **proposed**, not yet adopted. Written 29 September 2026 from a
> read of the code and the ADRs; no code changed. Once the owner says yes,
> the packet rows below go into the table in
> [FRONTEND_REDESIGN.md](FRONTEND_REDESIGN.md) and this file becomes their
> record. Decisions live in [architecture/](architecture/README.md).

## What was asked, in the owner's words

1. Images on the toolbar drop-down items.
2. The app asks for every permission it needs, and they sit under a
   **Permissions** tab in Settings.
3. A **Notifications** page in Settings: what to be told about, alarm type,
   countdown haptic, when it starts, loudness, whether it pauses music, with
   the required permissions listed at the foot of the page.
4. The coach adjusts weight, reps and RPE from **all** previous workouts,
   above all the last 12 weeks of that lift, grounded in research.
5. Rest timers between finished sets.
6. Borders around sections.
7. A countdown before a timed exercise starts.
8. RPE is required on every logged set.
9. A name for the in-house coach.

## What the code already does (the digest)

| Ask | Today | Gap |
|---|---|---|
| Menu images | Every menu is `InstrumentMenu` (`ui/components/InstrumentMenu.kt`). No item anywhere has a `leadingIcon`. `ForeignControlsTest` forbids raw `DropdownMenu`, so the change lands once, in the wrapper. | Add an icon slot and use it. |
| Permissions | Manifest: POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, VIBRATE, FOREGROUND_SERVICE(+SPECIAL_USE), WAKE_LOCK, RECEIVE_BOOT_COMPLETED, INTERNET, ACCESS_NETWORK_STATE. Asked in four scattered places: `LaunchPermissionsHost` (first launch), `RestNotificationGate` (floor and rest page), `ReminderPrefsSection` banner, `RestTimerPrefsSection` banner. Full-screen intent is checked, never requested. | One page that shows every capability's state and offers the fix. |
| Notifications settings | `RestPrefsStore`: sound, vibration, tick, default rest. `ReminderPrefsStore`: on/off, per-day times, quiet hours. Hard-coded: cue file, vibration pattern, tone volume 80, channel names, notification text, haptics. No audio focus code, so music is never paused or ducked. | A page over both stores plus new prefs (see packet P4). |
| Coach history | `WorkoutRepository.progressionFor` reads the top set of the **last one** finished session; RPE from the **last two** (`RpeModifier.RPE_HOLD_SESSIONS = 2`). Double progression in `ProgressionCalculator`; in-set codes in `SetMicroRec.kt`. Epley e1RM in `PersonalRecords.kt`, used for records and stalls only. `DeloadSignal` 14 days, `StallSignal` 3 sessions. Evidence catalog: 5 papers, 3 heuristics. | A 12-week window, a trend model, an RPE table, and an ADR. |
| Rest between sets | Done. `logSet` → `RestTimer.shouldStartAfterLog` → `startRestAfterSet`, coach length first (ADR-012 decision 18). | Nothing to build; verify on the phone. |
| Borders | Tokens exist: `Hairline` (white 8%), `HairlineStrong`, `OutlineSolid`, `Metrics.hairline` 1 dp, `emphasisBorder` 2 dp, `GymCard` with hairline border. Home separates by a 28 dp gap; the floor is unboxed rows with rules. | Stronger token, and box the floor's sections. |
| Timed-exercise countdown | `startHoldSet` starts the hold clock on the first tap. No lead-in. `FloorWorkClocks` owns the ticker and cues. | A lead-in phase in the clocks helper. |
| Forced RPE | `rpe: Int?` nullable end to end; `RpeSelector` 6–10 plus Clear; W1b labelled it "Effort · optional" (owner decision then). Missing RPE reads as "no evidence" in the coach. | A gate on Log; no schema change. |
| Name | None. Copy says "coach". Backup text still says "Personal Trainer". ADR-008: never "AI trainer". | Pick one, sweep the copy objects. |

## Rules this plan must obey

- One packet, one throwaway branch off `trunk`, one PR, JVM gate green
  (`./gradlew testDebugUnitTest` + `assembleDebug`), squash-merge, branch
  deleted (`owner-loop.mdc`).
- Settings is the fifth tab; new surfaces are sub-pages of it, never a sixth
  tab (ADR-014).
- Special-access permissions are asked after rest is used or configured,
  never as onboarding (ADR-012 decision 4). Notification permission and
  exact-alarm access stay separate capabilities (decision 5).
- Every coach decision is local, deterministic, and carries a `RuleTrace`
  (ADR-008). Every rule cites a real paper with a DOI or is marked
  `heuristic: true` (ADR-029). No LLM authors gym math.
- Copy lives in `domain/*Copy.kt` objects, not literals.
- Colour is never the only signal (ADR-023).

## The packets, in order

Visible packets ship to Temper Debug through Obtainium; the next drop is
`debug-live-2026-09-29` at live code 107 (`tools/debug-drop-plan.py`; re-run
before each drop, another packet may take it first).

### P1 — Section borders (Visible, small)

- New token `Metrics.sectionBorder` (1 dp) and colour `SectionEdge`, brighter
  than `Hairline` but under `OutlineSolid` so fields still read as fields.
  `ForeignControlsTest` and the token ceilings in
  `tools/checker-baselines.toml` must still pass.
- `GymCard` takes the new edge. Home's `DayBlock` and week board follow for
  free.
- The floor: wrap the identity block, the stats row, the entry panel, the
  set history and the rest dock in `GymCard`s (or a lighter `FloorSection`
  built on it), replacing the bare `HairlineDivider` rules at
  `ActiveWorkoutScreen.kt` lines 665, 672, 762, 790.
- Tests: `WorkoutFloorRenderTest` reachability unchanged; a new render check
  that each floor section has a bordered parent; W1d's large-text cases still
  hold (font 1.6 and 2.0).
- Phone checklist: open a workout; each block has an edge in dark and light;
  nothing clips at font 2.0.

### P2 — RPE required, and a lead-in before a timed set (Visible, small)

Two owner decisions are needed first (see *Decisions owed*).

- **RPE gate.** `ActiveWorkoutViewModel.logSet` refuses a working set with
  `rpe == null` and shows a one-line reason on the primary action ("Pick
  your effort first"). Warm-ups stay exempt (they never coached). Holds stay
  exempt unless the owner says otherwise (R2-4 took holds out of rep
  coaching). Rename W1b's "Effort · optional" to "Effort" in
  `SetRowCopy`/`RpeSelector` copy. The history editor `SetEditSheet` keeps
  Clear for old rows; a correction of a *working* set also requires one.
- **Lead-in.** A `LEAD_IN` phase in `FloorWorkClocks.startHold`: a short
  countdown (owner picks 3, 5 or 10 s; default 5) with a tick each second
  and a distinct cue at zero, then the hold clock runs as today. New
  `FloorTimerCue.LeadInTick` and `LeadInDone`; `FloorTimedMode` gains
  `HOLD_LEAD_IN` so the instrument bar shows "GET READY 3" in the kicker
  slot. Cancel on any tap. Saved across process death like the hold itself
  (`SavedStateFloorTimer`).
- Tests: `FloorWorkClocksCharacterisationTest` gains lead-in cases;
  `ActiveWorkoutViewModelTest` gains the refusal and the warm-up exemption;
  `RpeSelectorRenderTest` the new label.
- Phone checklist: log a set without effort (refused, reason shown); a
  plank shows GET READY then HOLD; the lead-in survives locking the phone.

### P3 — Menu icons (Visible, small)

- `InstrumentMenuItem(text, icon: ImageVector?, …)` inside
  `InstrumentMenu.kt`; icons from `TemperIcons`/`OutlinedMarks` at 20 dp in
  `TextSecondary`, Danger items in Danger.
- Sweep the eight menus: `WorkoutOverflowMenu`, `SessionDetailScreen`,
  `ActivityDetailScreen`, `LiveSessionBar`, `GymSurfaces` session card,
  `BackupRestoreSection`, `SetHistoryStrip`, `WorkoutSavedSets`.
- Tests: a render test per menu that the icon and the text are both present;
  TalkBack reads the text once (no double announcement, W1a's rule).

### P4 — Settings → Permissions, then Settings → Notifications (Visible, medium, two PRs)

**P4a Permissions page.** New `SettingsPage.PERMISSIONS`, row in
`SettingsHome` under "training" ("Permissions — what Temper may do on this
phone"). One `PermissionsSection` listing each capability with a live state
and one action:

| Capability | Check | Action |
|---|---|---|
| Notifications | `areNotificationsEnabled` / POST_NOTIFICATIONS | system prompt, else app notification settings |
| Precise rest alerts | `ExactAlarmCapability.canScheduleExactAlarms` | `REQUEST_SCHEDULE_EXACT_ALARM` |
| Rest alert over the lock screen | `canUseFullScreenIntent` | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (API 34) |
| Battery | `isIgnoringBatteryOptimizations` | per-app `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (replaces the general list screen used today) |
| Vibration | always granted | shown as "Granted" for honesty |

Re-checks on resume. The existing four ask-points stay, and each gains a
"Manage in Settings → Permissions" link so there is one place to go. Copy in
a new `PermissionsCopy`. ADR-012 decision 4 is kept: the page never opens on
its own.

**P4b Notifications page.** New `SettingsPage.NOTIFICATIONS`. Top to
bottom, mirroring the owner's list:

1. *What Temper tells you about*: rest done, workout reminders, missed-day
   check-in (each on/off; reminders link to the existing Reminders page).
2. *Rest alarm*: type (sound + vibrate / sound / vibrate / silent), sound
   choice (bundled cue or system alarm tone), loudness (uses alarm stream;
   a 25/50/75/100 % slider stored as `restCueVolume`), the last-five-second
   tick (moves here from Rest timer), tick haptic on/off.
3. *When it starts*: rest starts on Log (today's rule, read-only line) and
   the lead-in length for timed sets (3/5/10 s, from P2).
4. *Music*: "Pause music for the alert" (`AudioFocusRequest`
   GAIN_TRANSIENT_EXCLUSIVE) / "Turn music down" (MAY_DUCK) / "Leave music
   alone" (today). New code in `RestTimerAlerts`.
5. *Permissions this needs*: the P4a section embedded, read-only, with a link
   to the full page.

New prefs keys in `RestPrefsStore` (`restCueVolume`, `restAlarmType`,
`restAudioFocus`, `leadInSeconds`, `tickHaptic`); `user_settings` stays one
store. The Rest timer page keeps default rest and the presets only. Tests:
`RestTimerAlertsTest` for focus and volume; render tests for both pages;
`SettingsHome` row counts.

Phone checklist: turn everything off and on from one page; start music, let
a rest end with each of the three music choices.

### P5 — The coach reads twelve weeks (Visible, large, ADR first)

**Step 0, ADR-033 "Long-window progression".** Written and shown to the
owner before any code. It decides:

- **Window.** `progressionFor` reads every finished working set of the lift
  in the last 84 days (12 weeks), most recent first, capped at 24 sessions.
  Nothing older is read; nothing in the window is ignored.
- **Trend model.** For each session, the top set's Epley e1RM
  (`PersonalRecords`, already there) and its RPE. A slope over the window
  says whether the lift is rising, flat or falling. A flat or falling slope
  over 3+ sessions is a stall (`StallSignal` folds in); a falling slope with
  rising RPE is a deload call (`DeloadSignal` folds in).
- **RPE table.** Zourdos 2016 (DOI 10.1519/JSC.0000000000001049) and Helms
  2016 (10.1519/SSC.0000000000000218), both already in the catalog: RPE →
  reps in reserve → % of e1RM. The next load is the % of the trend e1RM that
  matches the target reps at the target RPE (goal-dependent: strength
  RPE 8, hypertrophy RPE 7–8, per `CoachPreferences`), snapped to the plate
  step (`IncrementTable`, unchanged), and never more than one step from the
  last top set unless the trend has risen 3 sessions running.
- **Rep target.** Double progression stays the default; when the trend e1RM
  says a heavier load would land at the target RPE, load moves first.
- **Recent bias.** The last 2 sessions keep a veto (today's `RpeModifier`):
  two grinds in a row hold the weight whatever the slope says. The clock is
  still not a rule input (ADR-029, W2c).
- **Honesty.** Fewer than 3 sessions in the window → today's one-session
  rule, labelled "Not much history yet" in the trace. Any rule that leans
  on a claim not in a cited paper is `heuristic: true`. The trace gains the
  window (`evidence window` is already a `RuleTrace` field) and the slope.

**Step 1, domain.** New `domain/progression/LongWindow.kt`
(`WindowedHistory`, `TrendModel`, `RpeLoadTable`), pure Kotlin, no imports
outside `domain` (`tools/check-domain-seams.py`). `ProgressionCalculator.adjusted`
gains a `window: WindowedHistory?` input and a new rung between the hint and
`RpeModifier`. `SetMicroRec.firstSet` takes the windowed hint.

**Step 2, data.** `WorkoutRepository.lastFinishedWork` gets a
`sinceEpochDay` bound and a DAO query with an index on
`(exerciseId, sessionFinishedAt)` if `EXPLAIN` shows a scan (Room stays v7;
an index is a migration, so only if needed, and then as v8 with its schema,
asset copy and migration test per ADR-032).

**Step 3, evidence.** `EvidenceCatalog` gains the new rule ids mapped to the
two RPE papers and Schoenfeld 2017; new heuristic entries for the cap and the
recent-veto. `docs/coach/evidence-seed.json` mirrors it.

**Step 4, UI.** The Next card's "Why?" shows the window ("12 weeks, 9
sessions"), the trend word, and the citation chip as today. No new screen.

**Tests.** `LongWindowTest` (slope, table, cap, fewer-than-three), extended
`ProgressionCalculatorTest` and `CoachRulesTest`, `CoachRecomputeTest`
(inputs-only recompute holds), and a replay test that today's 96
`ActiveWorkoutViewModelTest` cases give the same answer when the window has
one session.

Phone checklist: a lift with 10+ sessions shows the window in Why; a lift
done twice shows "Not much history yet"; a heavy week marked lighter still
holds.

### P6 — The coach's name (Quiet, small, after the owner picks)

- Sweep "coach" in `OnboardingScreen`, `SyncCopy`, `ProgressionCopy`,
  `RuleTraceCopy`, `MastheadCopy`, `CoachEvidenceCopy` into one
  `CoachIdentity.NAME` constant so the name can change once.
- Fix the stale "Personal Trainer" in `BackupJson.kt:22` and
  `BackupValidator.kt:427` to "Temper".
- Never "AI" in user copy (ADR-008).

**Candidates.** Pick: **Tempo** — sits beside Temper, means pacing, and is
a real gym word for set speed and rest. Others: **Spot** (the spotter behind
you), **Rack**, **Cue**. Recommendation: Tempo.

## Decisions owed by the owner

1. Adopt this order (P1 → P2 → P3 → P4a → P4b → P5 → P6), or reorder.
2. P2: is RPE required on holds (planks, hangs) too? Recommend **no**.
3. P2: lead-in length default. Recommend **5 s**.
4. P4b: default music behaviour. Recommend **Turn music down** (duck).
5. P5: window length 12 weeks and cap 24 sessions. Recommend **yes**.
6. P6: the name. Recommend **Tempo**.

## Side issues found on the way, not in this plan

- "Personal Trainer" survives in backup error text and Drive folder names.
- `ForeignControlsTest` may need a line for the new `InstrumentMenuItem`.
- `LaunchPermissionsHost` opens the general battery list, not the per-app
  request; P4a replaces it, but the first-launch walk keeps the old intent
  until then.
