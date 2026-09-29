# Action plan — the owner's eight asks of 29 September 2026

> Status: **adopted 29 September 2026** (owner: "Please proceed"). The
> defaults in *Decision log* stand until the owner says otherwise. Each
> packet row goes into the table in [FRONTEND_REDESIGN.md](FRONTEND_REDESIGN.md)
> as it is cut; this file is the plan and its record. Signed decisions live
> in [architecture/](architecture/README.md). Written from a read of the code
> and the ADRs; the digest of what exists today is in §3.

## 1. Goal

**Temper's coach becomes a trainer you can trust set to set, and the floor
and Settings make that trust visible.** Concretely, when this plan is done:

1. The Next set card's weight, reps and RPE come from the last twelve weeks
   of that lift, not the last workout alone, and *Why?* shows the window and
   the papers behind the call.
2. Every working set has an effort recorded, so that history is worth
   reading.
3. A timed set gives you a moment to get into position before the clock
   runs; a finished set starts your rest by itself (already true).
4. Settings has one page for Permissions and one for Notifications, so
   nothing the phone needs is a surprise and every alert is yours to shape.
5. Menus show a picture beside every action; sections have edges.
6. The coach has a name.

### Success criteria

| # | Criterion | How it is proved |
|---|---|---|
| G1 | On a lift with 9+ sessions in 84 days, *Why?* names the window, the trend and a DOI | `LongWindowTest`, phone check |
| G2 | A lift with fewer than 3 sessions falls back to today's rule and says "Not much history yet" | `LongWindowTest`, phone check |
| G3 | Every one of today's coach tests gives the same answer when the window holds one session | replay test in `CoachRulesTest` |
| G4 | A working set cannot be logged without an RPE; warm-ups and holds still can | `ActiveWorkoutViewModelTest` |
| G5 | A hold shows GET READY 5 → 1, then HOLD, and survives the screen locking | `FloorWorkClocksCharacterisationTest`, phone check |
| G6 | Settings → Permissions shows each capability's live state with one fix each; nothing opens on its own | render test, phone check |
| G7 | Settings → Notifications ends with the permissions list; music ducks, pauses or is left alone as chosen | `RestTimerAlertsTest`, phone check with music playing |
| G8 | Every menu item has an icon and TalkBack reads its text once | per-menu render tests |
| G9 | Every floor block and every Home block has a visible edge at font 1.0 and 2.0 | render tests, phone check |
| G10 | One `CoachIdentity.NAME` constant; "Personal Trainer" gone from user copy | grep in `tools/preflight.sh` |
| G11 | The JVM gate is green on every PR; each visible packet ships as a Temper Debug drop | CI, Obtainium |

## 2. Rules this plan obeys

- One packet, one branch off `trunk`, one PR, JVM gate green
  (`./gradlew testDebugUnitTest` + `assembleDebug`), squash-merge, branch
  deleted (`owner-loop.mdc`). `tools/preflight.sh` first for the fast loop.
- Before a drop: `python3 tools/debug-drop-plan.py` names the code and tag.
  Today it says 107 and `debug-live-2026-09-29`.
- Settings is the fifth tab; new pages are `SettingsPage` values, never a
  sixth tab (ADR-014).
- Special-access permissions are asked after rest is used or configured,
  never as onboarding (ADR-012 decision 4); notification permission and
  exact-alarm access are separate capabilities (decision 5).
- Every coach decision is local, deterministic, carries a `RuleTrace`
  (ADR-008); every rule cites a real DOI or is `heuristic: true` (ADR-029);
  the clock is not a rule input (W2c). No LLM authors gym math.
- Copy lives in `domain/*Copy.kt`; `domain` imports nothing outside itself
  (`tools/check-domain-seams.py`); colour is never the only signal (ADR-023);
  token ceilings in `tools/checker-baselines.toml` hold.
- Room stays v7 unless a packet needs a column or index, and then v8 with
  schema export, debug asset copy and migration test (ADR-032, ADR-010).

## 3. What exists today (digest)

| Ask | Today | Gap |
|---|---|---|
| Menu images | Every menu is `InstrumentMenu` (`ui/components/InstrumentMenu.kt`). No item has a `leadingIcon`. `ForeignControlsTest` forbids raw `DropdownMenu`, so the change lands once in the wrapper. | Item slot with icon. |
| Permissions | Manifest: POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, VIBRATE, FOREGROUND_SERVICE(+SPECIAL_USE), WAKE_LOCK, RECEIVE_BOOT_COMPLETED, INTERNET, ACCESS_NETWORK_STATE. Asked in four places: `LaunchPermissionsHost` (first launch), `RestNotificationGate` (floor, rest page), `ReminderPrefsSection` banner, `RestTimerPrefsSection` banner. Full-screen intent checked, never requested. | One page. |
| Notifications | `RestPrefsStore`: sound, vibration, tick, default rest. `ReminderPrefsStore`: on/off, per-day times, quiet hours. Hard-coded: cue file, vibration pattern, tone volume 80, channel names, text, haptics. No audio focus. | One page, new prefs, audio focus. |
| Coach history | `WorkoutRepository.progressionFor` reads the top set of the **last one** session; RPE from the **last two** (`RpeModifier.RPE_HOLD_SESSIONS = 2`). Double progression (`ProgressionCalculator`), in-set codes (`SetMicroRec.kt`). Epley e1RM in `PersonalRecords.kt` for records and stalls only. `DeloadSignal` 14 days; `StallSignal` 3 sessions. Catalog: 5 papers, 3 heuristics. | Window, trend, RPE table, ADR. |
| Rest between sets | Done: `logSet` → `RestTimer.shouldStartAfterLog` → `startRestAfterSet`, coach length first (ADR-012 d.18). | Phone check only. |
| Borders | `Hairline` (white 8%), `HairlineStrong` (14%), `OutlineSolid`, `Metrics.hairline` 1 dp, `emphasisBorder` 2 dp, `GymCard` bordered. Home separates by 28 dp gap; the floor is unboxed rows with rules at `ActiveWorkoutScreen.kt` 665, 672, 762, 790. | Stronger edge; box the floor. |
| Countdown | `startHoldSet` starts the hold clock at once. `FloorWorkClocks` owns ticker and cues. | Lead-in phase. |
| Forced RPE | `rpe: Int?` nullable; `RpeSelector` 6–10 plus Clear; W1b labelled "Effort · optional". | Gate on Log. |
| Name | None; copy says "coach"; backup text says "Personal Trainer". | Pick, sweep. |

## 4. Milestones and drops

| Milestone | Packets | Drop | What the owner checks |
|---|---|---|---|
| **M1 Floor feel** | P1 (built), P2 | 107, `debug-live-2026-09-29` (or the next free) | Edges on every block; Log refused without effort; GET READY before a plank |
| **M2 Menus** | P3 | 108 | Icons in every ⋮ menu |
| **M3 Settings** | P4a, P4b | 109, 110 | Permissions page; Notifications page; music ducks |
| **M4 Coach** | P5 (ADR, then 4 PRs) | 111 | *Why?* shows window, trend, DOI |
| **M5 Name** | P6 | rides M4's drop or the next | The name in copy |

Order of work: P1 → P2 → P3 → P4a → P4b → P5 → P6. The P5 ADR is drafted
during M1–M3 and shown to the owner before M4 starts. Packets share no
files with open PRs against `trunk` as of 29 September (last merged: #443).

## 5. The packets

Each packet lists: branch and PR, files, steps, tests, gate, drop, phone
checklist, done-when. Steps are in the order they are done.

### P1 — Section borders (Visible, small, half a day) — **built 29 September**, on the drop 107 checklist

**Why.** Blocks on the floor are separated by 1 dp lines at 8% white; on a
gym floor in daylight they vanish. The owner wants each section to read as
its own panel.

**Files.** `ui/theme/Color.kt`, `ui/theme/Metrics.kt`,
`ui/components/GymSurfaces.kt`, `ui/workout/ActiveWorkoutScreen.kt`,
`ui/workout/ExerciseStatsRow.kt`, `ui/home/HomeScreen.kt` (verify only),
tests under `ui/workout/`, `ui/theme/`.

**Steps.**
1. Add `SectionEdge = Color(0x33FFFFFF)` (white at 20%) in `Color.kt`
   between `HairlineStrong` and `OutlineSolid`, with a doc line saying what it
   is for. Keep `Hairline` for dividers and ring tracks.
2. `GymCard` takes `BorderStroke(Metrics.hairline, SectionEdge)`. Home's
   `DayBlock`, week board and Settings cards follow at once.
3. Add `FloorSection(title: String?, content)` in `GymSurfaces.kt`: a
   `GymCard` with `Metrics.space3` padding (tighter than `cardPadding`) and an
   optional `Kicker`. Wrap the floor's identity block, stats row, entry panel,
   set history and rest dock; delete the four full-bleed `HairlineDivider`
   rules that separated them. `ExerciseStatsRow`'s inner cell rules stay.
4. Run `tools/check-design-tokens.py`; if the new colour trips a ceiling,
   raise it by exactly the new uses and say so in the PR.
5. Check `ForeignControlsTest` still passes (it enforces 3:1 on `OutlineSolid`
   only; `SectionEdge` is decorative and needs no ratio, say so in its doc).

**Tests.** New `FloorSectionEdgesRenderTest`: each of the five floor blocks
has a bordered ancestor at font 1.0, 1.6 and 2.0 in 360 dp and 600 dp
widths. Existing `WorkoutFloorRenderTest`, W1d large-text cases and
`FloorScreenWiringRenderTest` unchanged.

**Gate.** `tools/preflight.sh`, then `./gradlew testDebugUnitTest assembleDebug`.

**Phone checklist.** Open a workout in dark and light; each block has an
edge; at font 2.0 nothing clips; Home cards have the same edge.

**Done when** G9 holds and the drop is on the phone.

*As built (29 September):* `SectionEdge` is white at 22%; `FloorSection` has
no fill (the entry wells inside it are `Surface1` on `Pit`, and a fill would
have taken that contrast away) and 4 dp inside the edge, not 8; the block
gap came down 12 → 8 dp so the frames fit the height budget, which the loop
now beats at 852 dp (budget lowered from 868 as the test's rule asks). The
exercise header stays unframed: it is the image-led hero, not a block. The
stats row's inner cell rules stay. `FloorSectionEdgesRenderTest` holds the
five frames at font 1.0 and 2.0.

### P2 — RPE required, and a lead-in before a timed set (Visible, small, one day)

**Why.** The coach reads RPE as evidence; a missing one is a hole in the
twelve weeks P5 will read. A hold that starts on the tap gives no time to
get into position.

**Files.** `ui/workout/ActiveWorkoutViewModel.kt` (`logSet`, ~1468),
`ui/workout/RpeSelector.kt`, `domain/SetRowCopy.kt` (or wherever "Effort ·
optional" lives; W1b), `ui/workout/FloorWorkClocks.kt`,
`domain/FloorTimedMode.kt`, `domain/HoldWork.kt`,
`workout/SavedStateFloorTimer.kt`, `ui/components/RestTimerUi.kt`
(`FloorInstrumentBar` kicker), `data/repository/prefs/RestPrefsStore.kt`
(`leadInSeconds`).

**Steps, RPE.**
1. `LogRefusal.EFFORT_MISSING` in `domain`; `logSet` returns it for a working
   set with `rpe == null` on a non-hold lift. Warm-ups and holds exempt
   (decision D2).
2. The primary action shows the refusal as its caption ("Pick your effort
   first") and pulses the RPE track once (reduced motion: no pulse, ADR-023).
   Haptic REJECT.
3. Copy: "Effort · optional" → "Effort". `SetEditSheet` keeps Clear for old
   rows; saving a correction of a working set with no effort is refused the
   same way.

**Steps, lead-in.**
4. `FloorTimedMode.HOLD_LEAD_IN`; `FloorTimerCue.LeadInTick(secondsLeft)`
   and `LeadInDone`. `FloorWorkClocks.startHold` runs `leadInSeconds` first
   (default 5, D3), one tick per second, then the hold clock as today. Any
   tap cancels the lead-in and the hold. `timedGeneration.bump()` on start.
5. The instrument bar's kicker shows `GET READY` and the numeral counts
   down; `Warn` colour is not used (it means last ten seconds of rest).
6. Persist the lead-in start in `SavedStateFloorTimer` beside the hold so a
   process death mid-lead-in resumes correctly.
7. Pref `leadInSeconds` in `RestPrefsStore` (3, 5, 10; default 5); the
   Rest timer settings page gets a row for it until P4b moves it.

**Tests.** `ActiveWorkoutViewModelTest`: refusal, warm-up exempt, hold
exempt, correction refusal. `FloorWorkClocksCharacterisationTest`: lead-in
ticks, cancel, resume after death, zero lead-in skips the phase.
`RpeSelectorRenderTest`: label. `FloorTimedModeTest`: resolver priority with
the new mode.

**Phone checklist.** Log a set without effort (refused, reason shown, track
pulses); warm-up logs without one; a plank shows GET READY 5…1 then HOLD;
lock the phone mid-lead-in and unlock (still correct); set lead-in to 3 s.

**Done when** G4 and G5 hold.

### P3 — Menu icons (Visible, small, half a day)

**Files.** `ui/components/InstrumentMenu.kt`, the eight menu sites:
`ui/workout/WorkoutOverflowMenu.kt`, `ui/history/SessionDetailScreen.kt`,
`ui/activity/ActivityDetailScreen.kt`, `ui/navigation/LiveSessionBar.kt`,
`ui/components/GymSurfaces.kt` (session card), `ui/settings/BackupRestoreSection.kt`,
`ui/workout/SetHistoryStrip.kt`, `ui/workout/WorkoutSavedSets.kt`;
`ui/theme/TemperIcons.kt` / `OutlinedMarks.kt` for any missing glyph.

**Steps.**
1. `InstrumentMenuItem(text, icon: ImageVector, onClick, enabled, danger,
   caption)` in `InstrumentMenu.kt`. Icon 20 dp, `TextSecondary`, Danger
   tint for danger items, `contentDescription = null` (the text is the label).
2. Add the missing glyphs to `OutlinedMarks` in the same stroke weight as
   `MoreVert`.
3. Replace each `DropdownMenuItem` at the eight sites.
4. `ForeignControlsTest`: allow `DropdownMenuItem` only inside
   `InstrumentMenu.kt`.

**Tests.** One render test per site: icon node and text node present; a
TalkBack pass (`AccessibilityMatrix`) reads each item once.

**Phone checklist.** Open every ⋮ menu; each row has a picture; TalkBack
reads once.

**Done when** G8 holds.

### P4a — Settings → Permissions (Visible, medium, one day)

**Files.** `ui/settings/SettingsPage.kt` (`PERMISSIONS`),
`ui/settings/SettingsHome.kt`, `ui/settings/SettingsScreen.kt`,
new `ui/settings/PermissionsSection.kt`, new `domain/PermissionsCopy.kt`,
new `domain/PhoneCapability.kt`, `timer/ExactAlarmCapability.kt`,
`timer/RestTimerNotifications.kt` (`canUseFullScreenIntent` made public),
`ui/permissions/LaunchPermissionsHost.kt`, `ui/workout/RestNotificationGate.kt`,
`ui/reminders/ReminderPrefsSection.kt`, `ui/settings/RestTimerPrefsSection.kt`.

**Steps.**
1. `domain/PhoneCapability` enum: NOTIFICATIONS, EXACT_REST_ALARM,
   LOCK_SCREEN_ALERT, BATTERY, VIBRATION; each with `title`, `why`
   (one sentence in the owner's terms) and `state: Granted | Missing |
   NotOnThisPhone`.
2. `ui/settings/CapabilityProbe` (Android side) reads the five states;
   re-read on resume.
3. `PermissionsSection`: one `GymCard` per capability with state chip
   (text plus icon, never colour alone), the *why*, and one action button:

   | Capability | Check | Action |
   |---|---|---|
   | Notifications | `areNotificationsEnabled`, POST_NOTIFICATIONS on 33+ | system prompt, else app notification settings |
   | Precise rest alerts | `ExactAlarmCapability.canScheduleExactAlarms` | `REQUEST_SCHEDULE_EXACT_ALARM` |
   | Rest alert over the lock screen | `canUseFullScreenIntent` | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (34+), else "Granted" |
   | Battery | `isIgnoringBatteryOptimizations` | per-app `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
   | Vibration | always granted | "Granted", no action |

4. Row in `SettingsHome` under "training": "Permissions — What Temper may do
   on this phone". `SettingsHomeCopy.PERMISSIONS`, `permissionsSummary(n
   missing)`.
5. The four existing ask-points keep their behaviour and gain a
   "Manage in Settings → Permissions" link. `LaunchPermissionsHost`'s battery
   step moves to the per-app request intent.
6. The page never opens itself (ADR-012 d.4).

**Tests.** `PermissionsSectionRenderTest` over all 3^5 state combinations
that matter (granted, missing, not-on-this-phone per row); `SettingsHome`
row test; `LaunchPermissionsHost` step-order test unchanged.

**Phone checklist.** Turn notifications off in Android, open the page
(Missing, fix works); revoke exact alarms (Missing); page lists five rows on
Android 14, four on 12.

**Done when** G6 holds.

### P4b — Settings → Notifications (Visible, medium, one to two days)

**Files.** `ui/settings/SettingsPage.kt` (`NOTIFICATIONS`), `SettingsHome.kt`,
`SettingsScreen.kt`, new `ui/settings/NotificationsSection.kt`, new
`domain/NotificationsCopy.kt`, `data/repository/prefs/RestPrefsStore.kt`,
`domain/RestTimerPreferences.kt`, `timer/RestTimerAlerts.kt`,
`timer/RestTickPlayer.kt`, `ui/settings/RestTimerPrefsSection.kt`,
`ui/reminders/ReminderPrefsSection.kt`.

**Steps.**
1. New prefs in `RestPrefsStore` (one `user_settings` store; keys added, no
   migration): `restAlarmType` (SOUND_AND_VIBRATE | SOUND | VIBRATE |
   SILENT; default SOUND_AND_VIBRATE), `restCueSound` (TEMPER_CUE |
   SYSTEM_ALARM), `restCueVolume` (25/50/75/100; default 100),
   `tickHaptic` (default on), `restAudioFocus` (DUCK | PAUSE | NONE; default
   DUCK, D4), `alertRestDone`, `alertMissedDay`. `leadInSeconds` from P2.
   `soundEnabled` and `vibrationEnabled` become derived from `restAlarmType`
   with a one-time read-through so nobody loses a setting.
2. `RestTimerAlerts`: request audio focus per `restAudioFocus`
   (`AudioFocusRequest` with `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` or
   `_TRANSIENT_EXCLUSIVE`), release on completion; set `MediaPlayer` volume
   from `restCueVolume`; pick the cue source from `restCueSound`. Tick
   haptic through `Haptics.CLOCK_TICK` when `tickHaptic`.
3. `NotificationsSection`, top to bottom:
   1. *What Temper tells you about*: Rest done, Workout reminders (link to
      Reminders), Missed-day check-in. Switches.
   2. *Rest alarm*: type (single-choice radio roles, F8a's rule), sound,
      loudness slider with spoken value, last-five-second tick, tick haptic,
      Play preview.
   3. *When it starts*: read-only line "Rest starts when you log a set";
      lead-in 3 / 5 / 10 s.
   4. *Music*: Turn music down / Pause music / Leave music alone.
   5. *Permissions this needs*: P4a's cards, read-only, with "Manage" link.
4. `RestTimerPrefsSection` keeps default rest and presets only; the moved
   rows link here.
5. `SettingsHome` row under "training": "Notifications — Alerts, sounds,
   music".

**Tests.** `RestTimerAlertsTest` (focus type per pref, volume, cue source,
release on finish, no focus request when SILENT); `NotificationsSectionRenderTest`;
`RestPrefsStoreTest` for the derived read-through; `SettingsHome` rows.

**Phone checklist.** Play music, end a rest under each music choice; set
loudness 25% (quieter); SILENT plays nothing and vibrates nothing; the
permissions list at the foot matches the Permissions page.

**Done when** G7 holds.

### P5 — The coach reads twelve weeks (Visible, large, ADR + four PRs, one to two weeks)

**Step 0 — ADR-033 "Long-window progression"** (docs only, shown to the
owner before code). It decides:

- **Window.** Every finished working set of the lift in the last 84 days,
  most recent first, capped at 24 sessions (D5). Nothing older; nothing in
  the window ignored. The window is measured from the session being
  coached, not the clock (W2c).
- **Trend.** Per session, the top set's Epley e1RM (`PersonalRecords`,
  exact below 12 reps) and its RPE. Least-squares slope of e1RM over
  session index: rising / flat / falling, with thresholds named in the ADR.
  Flat or falling over 3+ sessions is the stall `StallSignal` already
  reports; falling with rising RPE is the deload `DeloadSignal` already
  reports; the ADR folds both into one trace.
- **RPE table.** Zourdos 2016 (10.1519/JSC.0000000000001049) and Helms 2016
  (10.1519/SSC.0000000000000218), both already in `EvidenceCatalog`: RPE →
  reps in reserve → % of e1RM. Next load = that % of the trend e1RM for the
  target reps at the goal's RPE (strength 8, hypertrophy 7–8, from
  `CoachPreferences`), snapped by `IncrementTable`, and never more than one
  plate step from the last top set unless the trend rose three sessions
  running.
- **Rep rule.** Double progression stays the default; load moves first only
  when the table says the heavier load lands at the target RPE.
- **Recent veto.** `RpeModifier` stays: two grinds in a row hold the weight
  whatever the slope says.
- **Honesty.** Fewer than 3 sessions → today's rule, trace says "Not much
  history yet" (G2). Any claim not in a cited paper is `heuristic: true`.
  Trace gains `window` and `slope`.
- **Out of scope.** Cardio, holds (R2-4), session-level pacing, an LLM.

**PR 1 — domain.** New `domain/progression/LongWindow.kt`: `WindowedHistory`
(sessions, oldest and newest day, count), `TrendModel` (slope, direction,
e1RM at the newest point), `RpeLoadTable`. `ProgressionCalculator.adjusted`
gains `window: WindowedHistory?` and a rung between `hint` and
`RpeModifier`. `SetMicroRec.firstSet` takes the windowed hint. `RuleTrace`
gains the two fields. Tests: `LongWindowTest` (slope on flat, rising,
falling, noisy; table round-trips; cap; under-three fallback), extended
`ProgressionCalculatorTest`, `CoachRulesTest` replay (G3).

**PR 2 — data.** `WorkoutRepository.lastFinishedWork` gains
`sinceEpochDay` and `limit = 24`; `workoutDao` gets a bounded query. Run
`EXPLAIN QUERY PLAN`; if it scans, add an index on
`(exerciseId, sessionFinishedAt)` as Room v8 with schema export, asset copy
and `Migration7To8Test` (ADR-032). `ProgressionHintLoader` passes the
window. Tests: DAO test with 30 sessions over 100 days returns 24 within 84.

**PR 3 — evidence.** `EvidenceCatalog` and `docs/coach/evidence-seed.json`
gain rule ids `LW_TREND`, `LW_TABLE`, `LW_CAP` (heuristic), `LW_VETO`
mapped to the two RPE papers, Schoenfeld 2017 and Hackett 2017.
`CoachPolicyEvidence` maps them. Test: every rule id in code has an entry;
every DOI is well-formed; no heuristic has a DOI.

**PR 4 — UI.** *Why?* sheet shows "12 weeks · 9 sessions · rising" and the
citation chip as today; no new screen. `CoachEvidenceCopy` and
`RuleTraceCopy` carry the words. `LogNextCardInputsTest` gains the window
as an input that recomputes the card; `CoachRecomputeTest` holds that a
second of rest does not.

**Phone checklist.** A lift with 10+ sessions shows the window in *Why?*;
a lift done twice says "Not much history yet"; a week marked lighter still
holds the weight; pounds and kilograms give the same call.

**Done when** G1, G2, G3 hold.

### P6 — The coach's name (Quiet, small, half a day)

**Name: Tempo** (D6). Sits beside Temper, means pacing, and is a gym word
for set speed and rest. Never "AI" (ADR-008).

**Files.** New `domain/CoachIdentity.kt`; `ui/onboarding/OnboardingScreen.kt:147`,
`domain/SyncCopy.kt:23`, `domain/ProgressionCopy.kt`, `domain/RuleTraceCopy.kt:28`,
`domain/MastheadCopy.kt:186`, `domain/coach/CoachEvidenceCopy.kt`;
`data/backup/BackupJson.kt:22`, `data/backup/BackupValidator.kt:427`.

**Steps.** One constant, swept through the six copy objects ("Tempo
suggests…", "Based on…" unchanged); the two "Personal Trainer" strings become
"Temper"; a preflight grep fails on "Personal Trainer" or "AI trainer" in
`main`. Tests: copy tests updated; `BackupValidatorTest` message.

**Done when** G10 holds.

## 6. Dependencies and risks

| Risk | Where | Mitigation |
|---|---|---|
| Token ceiling trips on the new edge colour | P1 | Raise by exactly the new uses; note in PR |
| Forced RPE slows logging on the floor | P2 | The track is one tap; the refusal pulses it; holds and warm-ups exempt |
| Lead-in and rest alarm collide (rest ends as GET READY runs) | P2 | `startHoldSet` already stops rest; lead-in inherits that |
| Audio focus on OEM phones behaves oddly | P4b | Default DUCK; NONE is one tap away; log the focus result |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is Play-sensitive | P4a | Personal build only; note for the store build in `COMMERCIAL_BOUNDARY.md` |
| Twelve-week read is slow on a long history | P5 PR 2 | Bounded query, cap 24, index if needed, one read per lift open (W2e) |
| Trend flips the suggestion on a noisy lift | P5 | One-step cap, recent veto, under-three fallback, replay test G3 |
| Room v8 if an index is needed | P5 PR 2 | ADR-032 lane already exists; pre-migration copy (X2b) |
| A packet's files collide with S1 / F10 when they start | P4 | P4 lands before F10; `SettingsScreen` split (F10c) rebases on it |

## 7. Decision log

| # | Decision | Default adopted | Owner said |
|---|---|---|---|
| D1 | Packet order P1 → P6 | as above | "Please proceed" (29 Sep) |
| D2 | RPE required on holds | **No** (warm-ups and holds exempt) | default stands |
| D3 | Lead-in default | **5 s** (3 / 5 / 10 offered) | default stands |
| D4 | Music during a rest alert | **Turn music down** (duck) | default stands |
| D5 | Coach window | **84 days, cap 24 sessions** | default stands |
| D6 | Coach's name | **Tempo** | default stands; alternatives Spot, Rack, Cue |

A default the owner overturns later is changed here and in the packet that
carries it; nothing else moves.

## 8. Side issues found, not in this plan

- "PersonalTrainer Backups" Drive folder and `personal-trainer-backup-*` file
  names (docs/DRIVE_SIGNIN_CHECK.md); renaming them is a backup-format
  decision, not copy.
- `LaunchPermissionsHost` opens the general battery list; P4a replaces it.
- `ForeignControlsTest` needs a line for `InstrumentMenuItem` (P3 carries it).
- `DeloadSignal` and `StallSignal` each keep their own window today; P5's
  ADR folds them but does not delete them.
