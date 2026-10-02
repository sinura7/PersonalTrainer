# Action plan — the owner's asks of 29 September 2026

> Status: **adopted 29 September 2026** (owner: "Please proceed"), **revised the
> same day** after an independent audit of the plan and of its first packet
> (33 agents over the P1 diff, thirty fact-checks of this document against the
> code, and a critique through five lenses, every finding adversarially
> verified). The owner answered eight questions that day; their answers are
> the decision log in §7, and the questions still open are §8. Each packet
> gets a row in [FRONTEND_REDESIGN.md](FRONTEND_REDESIGN.md) when it is cut,
> a bullet in [HANDOFF-NEXT.md](HANDOFF-NEXT.md) and an entry in
> [ROADMAP.md](ROADMAP.md) when it lands, and an *As built* note here. Signed
> decisions live in [architecture/](architecture/README.md).

**In plain terms:** you asked for eight things. This is the order they get
built in, what each one will look like on your phone, what the code already
does, where the design law makes us do something differently from your
words, and the questions only you can answer.

## 0. What the owner asked, verbatim

> Add images to the toolbar drop down to indicate.
> [ ] the app needs to ask for all the permissions it requires to function
> from the user and these required settings should be nexted under a tab
> called permissions in the app settings.
> [ ] We need to add a notifications under the settings tab where the user
> can also see the required permission at the end of thr page and everything
> above had to do with what kind of notifications the user wants and doesn't
> wants. Along with things like alarm settings. Type of alarm. Count down
> haptic. When does it start. Sounds loudness should it stop current music?
> Etc etc
> [ ] We need to make sure the personal trainer itself is adjusting its
> recommended weight, reps and rpe based on all previous workouts and most
> importantly the last 12 week of that particular excerices and base its next
> recommendation based on research papers and studies and it being applied
> to the users real data to determine the next best rep. The next best set
> and the next best recommendation.
> [ ] Rwst timers should be set between finished sets
> [ ] We need borders for the section to make it more Obvious to the user and
> easier to follow.
> [ ] There should be a timer countdown for excerise that require a timed
> workout. This gives the user a chance to start
> [ ] Force the user to input the rpe so it can force an accurate history
> for the personal trainer to recommend.
> [ ] We should come up with a name for an Inhouse, almost like, ai. We
> should give this little helper/personal trainer a name. Help me come up
> with an idea.

Nine lines. Every packet below opens with the line it answers and says
plainly where it narrows or widens it.

## 1. Goal

Do the nine things above, each as the owner said it, and say plainly where
the design law or the code makes us do it differently. When this plan is
done:

1. The Next set card's weight, reps and RPE come from the last twelve weeks
   of that lift, not the last workout alone; *Why?* shows the window, the
   trend and the papers behind the call; the numbers set to set still react
   to the effort just logged, anchored on that history.
2. Every working set has an effort recorded, so that history is worth
   reading.
3. A timed hold gives the lifter a moment to get into position before the
   clock runs; a finished set starts the rest by itself (already true; the
   owner confirms it on the phone).
4. Settings has one page for Permissions and one for Notifications, so
   nothing the phone needs is a surprise and every alert is the owner's to
   shape.
5. Menus show a picture beside every action; sections read as panels.
6. The coach has a name.

### Success criteria

| # | Criterion | How it is proved |
|---|---|---|
| G1 | On a lift with 5+ usable sessions in 84 days, *Why?* names the window, the trend and a DOI | `LongWindowTest`, phone check |
| G2 | A lift with fewer than 5 usable sessions falls back to today's rule and *Why?* says "Not much history yet" | `LongWindowTest`, phone check |
| G3 | Every existing coach test gives the same answer when the window is absent (`window = null`) | the existing suites stay green unchanged |
| G4 | A working set cannot be logged, on the floor or corrected in History, without an RPE; warm-ups and holds still can | `ActiveWorkoutViewModelTest`, `SessionDetailViewModelTest` |
| G5 | A hold shows GET READY 5 → 1 with Cancel, then HOLD; a tap on the commit during GET READY logs nothing; it survives the screen locking | `HoldWorkTest`, `FloorWorkClocksCharacterisationTest`, phone check |
| G6 | Settings → Permissions shows each capability's live state with one working fix each; it never opens by itself | pure table test over `PhoneCapabilities.states`, one render, phone check |
| G7 | Settings → Notifications ends with the permissions list; music ducks, pauses or is left alone as chosen | `RestTimerAlertsTest` (new), phone check with music playing |
| G8 | Every menu item has an icon and TalkBack reads its text once | per-menu render tests |
| G9 | Every floor block is a drawn panel (stroke and fill read off the window) at font 1.0, 1.6 and 2.0 at 360 dp; Home's cards carry the same edge | `FloorSectionEdgesRenderTest`; one Home render |
| G10 | One `CoachIdentity.NAME` constant; "Personal Trainer" gone from user copy | a listed checker in `tools/preflight.sh` with its paired test |
| G11 | The local gate is green on every PR and each visible packet ships as a Temper Debug drop | `./gradlew testDebugUnitTest assembleDebug lintDebug`; Obtainium |
| G12 | After a working set that is not the lift's last planned one, rest starts on its own | already held by `ActiveWorkoutViewModelTest`; the owner sees it on the P1 phone check |

## 2. Rules this plan obeys

- One packet, one branch off `trunk`, one PR, gate green, squash-merge,
  branch deleted (`owner-loop.mdc`). The gate is `tools/preflight.sh` then
  `./gradlew testDebugUnitTest assembleDebug lintDebug`. Each packet gets an
  independent review and an adversarial audit before merge (ADR-002 d.8);
  this revision is P1's.
- Drop numbers and tags are taken at cut time from
  `python3 tools/debug-drop-plan.py`, never planned ahead. Trunk already
  shipped `debugLiveCode` 107 (`debug-live-2026-10-02`, packet P3, 2 Oct
  2026). P1 (#444) does not bump in the PR; after squash-merge, run the
  script again and bump `debugLiveCode` in its own commit before the
  Obtainium drop (108 and `debug-live/2026-10-02-2` as of 2 Oct 2026).
- This session's branch is `ccr-1cb8377d-ekl6ii`, assigned by the tool the
  owner works in; the `cursor/<slug>-b87f` rule in `owner-loop.mdc` names the
  other tool's vehicles. Both are vehicles: squash-merged and deleted. The
  plan's two docs commits and P1 ride one PR, as X1's docs pass rode with its
  neighbours; the PR body says so.
- Settings is the fifth tab; new pages are `SettingsPage` values, never a
  sixth tab (ADR-014).
- Special-access permissions are asked after rest is used or configured,
  never as onboarding (ADR-012 d.4), with one signed exception this plan
  makes explicit (P4a): the first-open walk that Q1 (#384) shipped.
- Every coach decision is local, deterministic and carries a `RuleTrace`
  (ADR-008); every rule cites a real DOI or is `heuristic: true` (ADR-029);
  a paper is cited only for the claim it makes; the clock is not a rule
  input (W2c); rules do not branch on goal, thresholds may (ADR-025). No LLM
  authors gym math.
- Copy lives in `*Copy.kt` objects (65 in `domain/`, 6 outside), the build
  ships English only (`localeFilters += "en"`); `domain` imports nothing
  outside itself (`tools/check-domain-seams.py`); colour is never the only
  signal (ADR-023); token ceilings in `tools/checker-baselines.toml` hold.
- Room stays v7. Room v8 is S2b's and v9 S3a's (the schema ledger); a packet
  here that needs a column or index takes the next free number with schema
  export, debug asset copy, migration test and the pre-migration copy
  (ADR-032, ADR-010), and says so in the ledger.
- New preference keys are classified: archived (in `BackupDocument`,
  `BackupJson`, the restore write and `BackupPreferences`) or device-local
  (with the KDoc pattern `restAlarmEligible` uses and a
  `BackupV2RoundTripTest` line). Sync is paused (ADR-031); no new pref
  travels until S1 says which do.

## 3. What exists today (digest, corrected)

| Ask | Today | Gap |
|---|---|---|
| Menu images | Eight menu sites, all through `InstrumentMenu` (`ui/components/InstrumentMenu.kt`); no `DropdownMenuItem` has a `leadingIcon`; no Material `TopAppBar`. `ForeignControlsTest` guards seven of the eight sites by name (`ActivityDetailScreen` is not on its list). | Item slot with icon; the eighth site on the guard. |
| Permissions | Manifest: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, VIBRATE, FOREGROUND_SERVICE(+SPECIAL_USE), WAKE_LOCK, SCHEDULE_EXACT_ALARM, USE_FULL_SCREEN_INTENT, RECEIVE_BOOT_COMPLETED. Two runtime prompts for notifications (`LaunchPermissionsHost` at first open; `RestNotificationGate` on the floor and rest page, asked once per phone) and two banners that open system settings (`ReminderPrefsSection` → app notification settings; `RestTimerPrefsSection` → exact-alarm request). The first-open walk also asks exact alarms and the general battery list. Full-screen intent is checked, never requested; on a sideloaded build it is granted unless the owner turns it off. | One page; one probe; the battery request done right. |
| Notifications | `RestPrefsStore`: sound, vibration, tick, default rest (Settings) and the last preset (floor). `ReminderPrefsStore`: opt-out, per-day times, quiet hours. Hard-coded: cue file, vibration pattern, tone volume 80, channel names and importance, text. No audio focus. The "Rest complete" channel asks to bypass Do Not Disturb, which Android grants only with notification-policy access the app does not hold. | One page, new prefs, audio focus done to the API's contract. |
| Coach history | `WorkoutRepository.progressionFor` reads the top set of the **last one** finished session and RPE from the **last two** (`RpeModifier.RPE_HOLD_SESSIONS = 2`). Double progression (`ProgressionCalculator`); in-set codes (`SetMicroRec.kt`). Epley e1RM (`PersonalRecords`, 1–12 reps, null above) feeds records, `StallSignal` (3 sessions), `DeloadSignal` (a **volume** signal over three calendar weeks, comparing strength over 14 days; it reads no RPE) and the detail chart, never the next load. Catalog: 4 papers with DOIs (all resolve to the named papers), 1 without, 3 heuristics; `citedByRules` in the JSON and `CoachPolicyEvidence` in code already disagree on 10 of 17 codes (audit C-3). | Window, per-load-class trend, RPE table for the next-session hint, ADR. |
| Rest between sets | Done: after a saved working set `FloorSetSaves` calls back, `RestTimer.shouldStartAfterLog` decides, `startRestAfterSet` runs the coach's length first (ADR-012 d.18). None after a warm-up; none after the last planned set unless an extra set is asked for. | Phone check only (G12). |
| Borders | Built (P1, below). | Home render still owed. |
| Countdown | `startHoldSet` starts the hold clock at once; the dock hides Stop during a hold; a running hold ends only by a lift switch, a log, an edit or a rest. `FloorWorkClocks` owns ticker and cues; `HoldWork` the arithmetic. | Lead-in phase modelled in domain. |
| Forced RPE | `rpe: Int?` nullable end to end; `RpeSelector` 6–10 plus Clear; the label is `RpeCopy.LABEL = "Effort · optional"` (W1b, #393), with `OPTIONAL`, `LABEL_SPOKEN`, `HELP_INTRO` and `AccessibilityMatrix` saying the same; the coach reads a missing RPE as "no evidence". Production logs go through `performPrimary` → `logSetWithDuration`; `logSet()` is a test seam. | Gate in `logSetWithDuration`; History's correction path; copy and ADR-026 d.3. |
| Name | None; copy says lowercase "coach"; "This file is not a Personal Trainer backup." survives in `BackupJson.kt` and `BackupValidator.kt`. | Pick, sweep, checker. |

## 4. Milestones and drops

| Milestone | Packets | Drop | What the owner checks |
|---|---|---|---|
| **M1 Floor feel** | P1 (built), P2a, P2b | P1 on the next free after merge (108 today); P2a/P2b ride later drops | Panels; Log refused without effort; GET READY before a plank; rest starts after a set |
| **M2 Menus** | P3 (on trunk as 107) | shipped | Icons in every ⋮ menu |
| **M3 Settings** | P4a, P4b (three PRs) | next free per V packet | Permissions page; Notifications page; music ducks |
| **M4 Coach** | P5 (ADR, then four PRs) | next free | *Why?* shows window, trend, DOI |
| **M5 Name** | P6 | rides M4's drop or the next | The name in copy |

Order: P1 → P2a → P2b → P3 → P4a → P4b → P5 → P6. The P5 ADR is drafted
during M1–M3 and shown to the owner before M4 starts. No open PR against
`trunk` touches these files today (GitHub, 29 September); origin still
carries nine leftover vehicle heads that `owner-loop.mdc` says to delete.

## 5. The packets

### P1 — Section borders — **built 29 September, revised after audit**, PR #444 phone checklist (Obtainium 108 after merge)

**Owner's words.** "We need borders for the section to make it more Obvious
to the user and easier to follow."

**Where this narrows or widens it.** Widens: every card and grouped list in
the app takes the same edge, not only the workout screen, so one edge
system exists. Narrows: the exercise header (image, name, Lift n of N) and
the Working / Warm-up switch stay unframed; they are the hero and its
control, not sections (a question for the owner, §8 Q4).

**In plain terms.** Each part of the workout screen now sits in its own
slightly lighter box with a clear outline, like taped-off zones on a gym
floor, and the boxes on Home, History and Settings got the same outline.

**As built.**
- `SectionEdge` (white at 22%, `Color.kt`) is the edge of every panel:
  `GymCard`, `GroupedList`, `LiftCard`, the rest card and the new
  `FloorSection`. The recessed `NumeralWell` (history's edit sheet, the
  routine editor) keeps the 8% `Hairline`: a bright rim on a well reads as
  raised. `TokenIdentityTest` pins the alpha.
- `FloorSection` (`ui/components/GymSurfaces.kt`) is a filled panel:
  `Surface1` on `Pit` plus the edge, the surface ladder every panel in the
  app is built on. The first cut had no fill, on a premise the audit showed
  false (the `Surface1` wells it cited are not on the floor); a line alone
  on Pit read as a form's field group. 4 dp above and below the block,
  8 dp at the sides, 4 dp for the stats row and the entry, which need the
  width. The floor's block gap came from 12 to 8 dp.
- The frame took width, and three things broke in the first cut and are
  fixed: `Last set · RPE 9` wrapped (stats inset 4 dp); `40% · Suggested`
  on the warm-up ramp wrapped (entry inset 4 dp); the five effort chips
  split 3 + 2 at font 2.0 (the row estimate in `RpeSelector` carried 8 dp
  of slack per chip over the chip's real padding; now 4 dp; a 320 dp phone
  still wraps as its test holds). A fourth, pre-existing and made worse:
  the compact Next-set line's numbers would be cut at font 2.0; it now
  stacks numbers above Why? and Apply at large text, as the tall card does.
- The entry loop measures 852 dp at 360 wide against a ceiling that stood
  at 868 (the ceiling is a bound, not the previous reading); it is now 852,
  and the test's history comment carries the line.
- Tests: `FloorSectionEdgesRenderTest` reads each frame's stroke and fill
  off the window at font 1.0, 1.6 and 2.0 (a reverted token or a lost
  stroke fails it), and holds the stats label, the ramp caption, the five
  chips on one row and the compact strip's numbers whole. The shared
  fixture `twoWorkingSetsLogged()` lives in `FloorTestKit`. Still owed
  (§8): one Home render asserting `DayBlock`'s card, and the 600 dp width.
- Docs: row 12h, ROADMAP, HANDOFF-NEXT, this note.

**Phone checklist (PR #444; not merged until the owner OKs this on the phone).** Open a workout under bright light with the
screen at about half brightness: each block reads as its own panel; the
last-set label and the warm-up captions are one line. At the largest text
nothing clips and the five effort chips are one row. Home's cards and
Settings' lists have the same edge. Log a working set that is not the
lift's last: rest starts by itself (G12).

### P2a — Effort required on working sets (Visible, small, one day)

**Owner's words.** "Force the user to input the rpe so it can force an
accurate history for the personal trainer to recommend."

**Where this narrows it.** Owner decision D2: working sets only; warm-ups
and timed holds still log without one (the coach never reads effort on
those). This reverses W1b's "Effort · optional" (#393, drop 102), which the
owner accepted then; ADR-026 d.3 is amended to say so.

**In plain terms.** Before you can tap Log on a real set, you pick how hard
it felt. Warm-ups and planks do not ask.

**Files.** `ui/workout/ActiveWorkoutViewModel.kt` (`logSetWithDuration`,
the path every Log, warm-up log, hold log and Save changes takes),
`domain/LogCommitCopy.kt` (the reason joins `disabledReason`),
`ui/workout/WorkoutDock.kt` (already feeds that slot), `domain/RpeCopy.kt`
(`LABEL`, `LABEL_SPOKEN`, `OPTIONAL`, `HELP_INTRO`, `blurb`),
`ui/workout/RpeSelector.kt` (KDoc), `domain/AccessibilityMatrix.kt`,
`ui/history/SetEditSheet.kt`, `ui/history/SessionDetailViewModel.kt`,
`docs/architecture/ADR-026-frontend-redesign.md`.

**Steps.**
1. In `logSetWithDuration`, after the readiness guards, a working set on a
   non-hold lift with `rpe == null` is refused through the existing
   rejection path (`LogCommitFeedback.REJECT`, haptic reject); the reason
   `LogCommitCopy.EFFORT_MISSING` ("Pick your effort first") shows in the
   commit's disabled-reason slot, and the effort track pulses once
   (reduced motion: no pulse, ADR-023). The rule itself (`requiresEffort(
   isWarmup, isHold)`) sits in `domain`, beside `RestTimer.shouldStartAfterLog`.
2. Copy: `LABEL` "Effort"; `LABEL_SPOKEN` "Effort, required for working
   sets"; `HELP_INTRO` "How hard did that working set feel? A working set
   needs an effort before it can be logged. Warm-ups and holds don't.";
   `OPTIONAL` leaves `blurb()`. On a hold the track's heading still reads
   "Effort" but its helper says holds do not need one.
3. Clear and tap-to-deselect stay: they make the set unloggable until a
   value is picked, which is the point; the outlined recommended chip is
   the one-tap shortcut (no default value is ever written for the lifter).
4. History: `SessionDetailViewModel.updateSet` / `addSet` refuse a working
   set with no RPE the same way; `SetEditSheet` keeps Clear for reading old
   rows but cannot save a working set without one. Old rows with no RPE
   stay as they are (an invented effort inside the window P5 reads is worse
   than a gap; the coach already treats a gap as "no evidence").
5. ADR-026 d.3 amended: "effort required on working sets; warm-ups and holds
   optional (owner decision 29 September 2026, supersedes W1b)".

**Consequence to name.** With effort on every working set, "You have
another in you" and the shorter in-tank rests fire more often than today,
because they fired only when every set had an RPE. That is the rules
working as written, not a change to them.

**Tests.** `ActiveWorkoutViewModelTest`: refusal, warm-up exempt, hold
exempt, refusal through `performPrimary`; `WorkoutPrimaryActionsTest`: the
action's caption; `FloorSetSaveCharacterisationTest`: no row written on
refusal; `SessionDetailViewModelTest`: correction refusal;
`RpeCopyTest`, `RpeSelectorRenderTest`, `AccessibilityMatrixTest`: the
copy.

**Phone checklist.** Log a set without effort (refused, reason shown,
track pulses); a warm-up logs without one; a plank logs without one; in
History, save a correction of a working set with effort cleared (refused).

### P2b — GET READY before a timed hold (Visible, small, one day)

**Owner's words.** "There should be a timer countdown for excerise that
require a timed workout. This gives the user a chance to start."

**Where this narrows it.** Owner decision D3: holds only (planks, hangs),
5 s by default, 3 / 5 / 10 offered. The SET stopwatch (a timed set the
lifter starts and stops) gets no lead-in: the lifter starts it when they
are ready. Off is not offered; 3 s is the shortest.

**In plain terms.** Tap to start a plank and the screen counts GET READY
5, 4, 3, 2, 1 with a tick each second, then the hold clock runs. Cancel is
one button. You cannot log a hold by mistake during the countdown.

**Files.** `domain/HoldWork.kt` (a `leadInDeadline` beside the hold
deadline and `phase(now) → LEAD_IN | HOLD | DONE`),
`domain/FloorTimedMode.kt` (`HOLD_LEAD_IN` ahead of `HOLD_RUNNING` in the
resolver, active, refuses `offerSetClock`; cues `LeadInTick(secondsLeft)`,
`LeadInDone`), `domain/FloorTimerSurface.kt` (`durationToLog` returns null
in LEAD_IN), `ui/workout/FloorWorkClocks.kt` (ticks only),
`ui/workout/WorkoutPrimaryAction.kt` (`CANCEL_LEAD_IN` kind, "Cancel";
the commit reads "Get ready…" and is disabled), `ui/workout/WorkoutDock.kt`,
`ui/components/RestTimerUi.kt` (kicker `GET READY`, the countdown numeral,
no `Warn` colour), `workout/SavedStateFloorTimer.kt` (the lead-in deadline
saved beside the hold's; the reboot guard that clears a hold whose start
lies in the future must learn the lead-in, or it would clear every lead-in
as "clock went backwards"), `data/repository/prefs/RestPrefsStore.kt`
(`leadInSeconds`, archived in the backup), `ui/settings/RestTimerPrefsSection.kt`
(a row until P4b moves it), `domain/*Copy.kt` for the words.

**Steps.**
1. Model the phase in `HoldWork` from two deadlines; `leadInSeconds == 0`
   never occurs (3 is the floor).
2. The resolver ranks `HOLD_LEAD_IN` above `HOLD_RUNNING`; the rest card
   stays hidden; the set clock is not offered.
3. The bar shows `GET READY` and the numeral; the tick cue each second
   goes where the hold's cues go (foreground only; the phone locked
   mid-lead-in shows the right phase when unlocked, no ticks through the
   lock screen); the done cue is distinct from the hold's target cue.
4. Cancel sits in the bar's Stop slot (`onStop`); it ends the lead-in and no
   hold starts. Nothing else cancels it except what already ends a hold.
5. The commit is `CANCEL_LEAD_IN` during the phase; `durationToLog` is null,
   so a tap can never log a one-second plank.
6. Persistence: both deadlines saved; restore picks the phase from them.
7. Pref and its Settings row; the backup carries it (with `restTickEnabled`,
   which the backup had left behind).

**Tests.** `HoldWorkTest` (phase from deadlines);
`FloorTimedModeTest` (priority); `FloorWorkClocksCharacterisationTest`
(ticks, cancel, resume after death, reboot guard); `WorkoutPrimaryActionsTest`
(`CANCEL_LEAD_IN`); a render of the bar's GET READY kicker; `BackupJsonTest`
for the new field.

**Phone checklist.** A plank shows GET READY 5…1 then HOLD; Cancel during
the countdown starts nothing; a tap on the big button during the countdown
logs nothing; lock the phone mid-countdown and unlock (still right); set
the lead-in to 3 s.

### P3 — Menu icons (Visible, small, one day)

**Owner's words.** "Add images to the toolbar drop down to indicate."

**Where this widens it.** Owner decision D8: every ⋮ menu in the app, not
only the workout screen's. "Images" is read as line icons in the app's
stroke, not pictures (a question for the owner, §8 Q8).

**In plain terms.** Every menu row gets a small picture beside its words,
so a glance tells you which row is which.

**Files.** `ui/components/InstrumentMenu.kt`; the eight sites:
`ui/workout/WorkoutOverflowMenu.kt`, `ui/history/SessionDetailScreen.kt`,
`ui/activity/ActivityDetailScreen.kt`, `ui/navigation/LiveSessionBar.kt`,
`ui/components/GymSurfaces.kt` (session card), `ui/settings/BackupRestoreSection.kt`,
`ui/workout/SetHistoryStrip.kt`, `ui/workout/WorkoutSavedSets.kt`;
`ui/components/OutlinedMarks.kt` for missing glyphs;
`app/src/test/.../ui/theme/ForeignControlsTest.kt`.

**Steps.**
1. `InstrumentMenuItem(text, icon, onClick, enabled, danger, caption)` in
   `InstrumentMenu.kt`: icon 20 dp, `TextSecondary`, Danger tint for danger
   rows, `contentDescription = null` (the text is the label).
2. Missing glyphs drawn in `OutlinedMarks`'s stroke weight.
3. Replace each `DropdownMenuItem` at the eight sites.
4. `ForeignControlsTest`: add `ActivityDetailScreen` to `menuSites` (eight,
   not seven), and allow `DropdownMenuItem` only inside `InstrumentMenu.kt`.

**Tests.** One render per site (icon node and text node present); the
TalkBack pass reads each row once (`InformationArchitecture`'s spoken
checks, not `AccessibilityMatrix`, which is a table of notes).

**Phone checklist.** Open every ⋮ menu; each row has a picture; TalkBack
reads once.

### P4a — Settings → Permissions (Visible, medium, one to two days)

**Owner's words.** "The app needs to ask for all the permissions it
requires to function from the user and these required settings should be
nexted under a tab called permissions in the app settings."

**Where this narrows it.** Owner decision D1: the app keeps asking as it
does today (three at first open, notifications again on the floor, the
lock-screen alert the first time a rest runs) and gets the page; it does
not ask everything cold. ADR-012 d.4 says special access is never asked in
onboarding, and the first-open walk (Q1, #384) already asks exact alarms
and battery: this packet writes that walk into ADR-012 as the signed
exception, so law and code agree. "All the permissions" becomes the five
the owner can grant or see; the five install-time ones (internet, network
state, foreground service, wake lock, boot) are listed on the page in one
line as "granted at install, nothing to do".

**In plain terms.** One page in Settings shows everything the phone lets
Temper do, green or red, with one button per red row that fixes it.

**Files.** `domain/PhoneCapability.kt` (identity only: NOTIFICATIONS,
EXACT_REST_ALARM, LOCK_SCREEN_ALERT, BATTERY, VIBRATION; `CapabilityState`
GRANTED / MISSING / NOT_ON_THIS_PHONE; pure `PhoneCapabilities.states(
sdkInt, notificationsEnabled, restDoneChannelEnabled, canScheduleExactAlarms,
canUseFullScreenIntent, batteryUnrestricted, hasVibrator)`),
`domain/PhoneCapabilityPort.kt` (the port; on `AppDependencies`),
`timer/AndroidPhoneCapabilities.kt` (the Android side, re-read on resume),
`domain/LaunchPermissions.kt` (`nextStep` rewritten over the same states,
so the walk and the page cannot drift), `domain/PermissionsCopy.kt`,
`ui/settings/SettingsPage.kt` (`PERMISSIONS`), `SettingsHome.kt`,
`SettingsScreen.kt`, `ui/settings/PermissionsSection.kt`,
`ui/permissions/LaunchPermissionsHost.kt`, `ui/workout/RestNotificationGate.kt`,
`ui/reminders/ReminderPrefsSection.kt`, `ui/settings/RestTimerPrefsSection.kt`,
`app/src/main/AndroidManifest.xml` (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`),
`docs/architecture/ADR-012-rest-and-reminders.md`, `docs/COMMERCIAL_BOUNDARY.md`.

| Capability | Check | Fix | Copy note |
|---|---|---|---|
| Notifications | `areNotificationsEnabled` and the "Rest complete" channel not blocked (POST_NOTIFICATIONS on 33+) | system prompt once, then app notification settings | a blocked channel is Missing too |
| Precise rest alerts | `canScheduleExactAlarms` | `REQUEST_SCHEDULE_EXACT_ALARM` | "Android 14 phones start with this off for new installs" |
| Rest alert over the lock screen | `canUseFullScreenIntent` (34+) | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` | below 34: Granted, no action; sideloaded builds start granted |
| Battery | `isIgnoringBatteryOptimizations` | per-app `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:` data, needs the manifest permission; falls back to the general list if it throws | the honest why: the exemption lifts the once-per-nine-minutes throttle on the rest wakeup; exact alarms already fire in Doze |
| Vibration | `hasVibrator`; Android 13+ alarm-vibration toggle | none; NotOnThisPhone if no vibrator | "your phone's Alarm vibration setting also applies" |

**Steps.**
1. Manifest: declare `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with a comment
   (personal build; Play-sensitive; `COMMERCIAL_BOUNDARY.md` gets the line
   that the store build drops or justifies it).
2. Domain states and port; `LaunchPermissions.nextStep` over them.
3. Android probe on `AppDependencies`; the walk's battery step uses the
   per-app intent.
4. The page: one `GymCard` per capability, state chip (words plus icon),
   the why, one button. Row in `SettingsHome` under "training":
   "Permissions — What Temper may do on this phone", summary "n to fix".
5. The four ask-points gain "Manage in Settings → Permissions".
6. ADR-012 d.4 amended with the first-open walk as the signed exception;
   the page never opens by itself.

**Tests.** `PhoneCapabilitiesTest` (pure table over the inputs, every
state); one `PermissionsSectionRenderTest` (five rows, chips and buttons
present); new `LaunchPermissionsHostTest` (step order unchanged; battery
step fires the per-app intent) — no test exists for the host today;
`SettingsHome` row test.

**Phone checklist.** Turn notifications off in Android, open the page
(Missing; the fix works); revoke exact alarms (Missing); block the "Rest
complete" channel alone (Missing); five rows on Android 14, the lock-screen
row reads Granted with no button on Android 12.

### P4b — Settings → Notifications (Visible, medium, three PRs)

**Owner's words.** "A notifications under the settings tab where the user
can also see the required permission at the end of the page and everything
above had to do with what kind of notifications the user wants and doesn't
wants. Along with things like alarm settings. Type of alarm. Count down
haptic. When does it start. Sounds loudness should it stop current music?"

**Where this narrows or reinterprets it.** The page follows the owner's
list in the owner's order. "Count down haptic" is read as a vibration on
each of the last five seconds of rest (today only a sound ticks) and the
lead-in's ticks; if the owner meant something else, §8 Q9. "Loudness" is
the cue's level under the phone's own alarm volume, and the page says so.
A "missed-day check-in" does not exist in the app today and is not added
here (§8 Q3). Reminders' own sound and vibration are Android's channel
settings, fixed at creation; the page links to them.

**In plain terms.** One page: what Temper tells you about, how the rest
alarm sounds and shakes, when it starts, what happens to your music, and at
the bottom the permissions all of that needs.

**Files.** `ui/settings/SettingsPage.kt` (`NOTIFICATIONS`), `SettingsHome.kt`,
`SettingsScreen.kt`, `ui/settings/NotificationsSection.kt`,
`domain/NotificationsCopy.kt`, `data/repository/prefs/RestPrefsStore.kt`,
`domain/RestTimer.kt` (`RestTimerPreferences`), `timer/RestTimerAlerts.kt`,
`timer/RestTickPlayer.kt`, `timer/RestTimerService.kt` (the tick haptic runs
where the tick plays, in the service, through `Vibrator`, not
`performHapticFeedback`, which needs a view), `timer/RestTimerNotifications.kt`
(drop `setBypassDnd`, which Android strips without policy access, and say
so in the copy), `data/backup/BackupDocument.kt`, `BackupJson.kt`,
`LocalBackupRepository.kt`, `ui/settings/RestTimerPrefsSection.kt`,
`ui/reminders/ReminderPrefsSection.kt`.

**PR 1 — the alarm itself.**
- `restAlarmType` (SOUND_AND_VIBRATE | SOUND | VIBRATE | SILENT), derived
  statelessly from today's two booleans when unset (`type ?:
  RestAlarmType.from(sound, vibrate)`); every write of the type writes the
  booleans too; a restore writes the booleans and removes the type, so the
  restored settings win. Archived through the existing two fields; no
  backup version bump (every preference reads with a default).
- `restCueSound` (TEMPER_CUE | SYSTEM_ALARM; the system tone is played
  through `MediaPlayer` from its URI so completion is known and focus
  released), `restCueVolume` (25 / 50 / 75 / 100 %, a `MediaPlayer` scalar
  under the alarm stream; the row reads "Cue volume, relative to your
  phone's alarm volume"), `tickHaptic` (on/off, in the service).
  Device-local, with the KDoc pattern and a round-trip test line.
- Audio focus: `restAudioFocus` (DUCK | PAUSE | NONE; default DUCK, owner
  decision D4). `AudioFocusRequest` with the cue's alarm attributes:
  `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` makes Android 8+ duck music apps
  itself; `AUDIOFOCUS_GAIN_TRANSIENT` pauses them (and some do not resume;
  the row says so); NONE requests nothing. Focus abandoned on the cue's
  completion. Device-local.
- Tests: new `RestTimerAlertsTest` (focus type per pref, none for SILENT,
  release on completion, volume scalar, cue source); `RestPrefsStoreTest`
  (stateless derivation, restore removes the type);
  `BackupV2RoundTripTest` (device-local lines).

**PR 2 — the page.**
1. *What Temper tells you about*: Rest done (this is `restAlarmType`;
   SILENT is "off", one control, not two), Workout reminders (link to the
   Reminders page and Android's channel).
2. *Rest alarm*: type (single-choice radio roles), sound, cue volume with
   spoken value, last-five-second tick, tick haptic, Play preview (the
   preview always sounds; the checklist says so).
3. *When it starts*: "Rest starts when you log a set" (read-only; ADR-012
   d.18); the lead-in 3 / 5 / 10 s (from P2b).
4. *Music*: Turn music down / Pause music / Leave music alone.
5. *Permissions this needs*: P4a's cards read-only, "Manage" link.
The Rest timer page keeps default rest and its presets only; its moved rows
link here. `SettingsHome` row under "training": "Notifications — Alerts,
sounds, music". Tests: `NotificationsSectionRenderTest`; `SettingsHome`
rows; `RestTimerPrefsSection` render.

**PR 3 — sync and backup bookkeeping.** One line in `data/sync/SyncAccountPrefs.kt`'s
KDoc and in ADR-031's list: which of the new keys travel (none until S1
decides); the risk row in §6.

**Phone checklist.** Play music; end a rest under each of the three music
choices; cue volume 25 % is quieter than 100 % at the same phone alarm
volume; SILENT sounds nothing and shakes nothing (the preview still
sounds); the permissions list at the foot matches the Permissions page.

### P5 — The coach reads twelve weeks (Visible, large, ADR + four PRs, two to three weeks)

**Owner's words.** "Make sure the personal trainer itself is adjusting its
recommended weight, reps and rpe based on all previous workouts and most
importantly the last 12 week of that particular excerices and base its
next recommendation based on research papers and studies and it being
applied to the users real data to determine the next best rep. The next
best set and the next best recommendation."

**Where this narrows it.** Owner decision D5: the coach reads the last
84 days of a lift; it does not read older sessions at all. The owner said
"all previous workouts, most importantly the last 12 weeks"; whether older
work should count for less rather than not at all is §8 Q2, and a yes adds
a decay term to the ADR. Owner decision D7: the history reaches both the
opening numbers and the set-to-set nudges; it does so through the first
set's hint (the in-set rules already start from it), so the in-set rules
themselves do not change (ADR-029 d.3). If the owner later wants the RPE
table inside the set-to-set rules, that is an ADR-029 amendment named as
such. The owner trains a lift about once a week (D6), so 84 days is about
12 sessions; the cap of 24 sessions is a safety bound that never bites for
them.

**In plain terms.** Today the coach looks at your last workout of a lift
and asks "did you hit your reps, and how hard was it?". After this it
looks at three months of that lift, works out whether you are getting
stronger, flat or slipping, and picks the next weight from what you can
lift at the effort your goal calls for, using published tables, and it
tells you which paper says so. With less than five workouts of a lift it
says it does not know enough yet and does what it does today.

**Step 0 — ADR-033 "Long-window progression"** (docs only, shown to the
owner before code). It decides:

- **Window.** Every finished working set of the lift whose session date is
  within 84 days of the session being coached (the caller supplies the
  bound from the session's date, never from the clock; W2c), most recent
  first, capped at 24 sessions; the trace shows the effective window when
  the cap bites.
- **Per-lift metric**, one shared function grown from
  `StallSignal.measure`: e1RM for `LOADED` and `BODYWEIGHT_ADDED` at 1–12
  reps; the top-set load with reps as tiebreak above 12 reps; reps for
  `BODYWEIGHT`; less assistance, then reps, for `BODYWEIGHT_ASSISTED`
  (Epley on assistance inverts, the bug `DeloadSignal` was repaired for).
  A session counts as usable when it yields a metric.
- **Two scales.** The long window gives the count, the best metric and the
  rep bands covered, for *Why?*. Direction comes from a trend window: the
  newest sessions whose top-set reps are within ±2 of the current target,
  at most 8. Slope is Theil–Sen (median of pairwise slopes, deterministic)
  of the metric over session index, as a percentage of the trend window's
  mean per session. Rising if the projected change across the trend
  window is at least +2.5 % and at least one plate step; falling at or
  below −2.5 %; else flat. Direction needs 5 usable points; 3–4 read "flat
  (few points)"; a gap over 21 days between sessions starts the trend
  window again. Every number here is a heuristic (`LW_TREND`, `heuristic:
  true`) and is offered to the owner as a candidate in the ADR, not as a
  decision already made. Direction never contradicts `StallSignal`: a
  stall it reports for the lift reads as flat here.
- **Load rule, next-session hint only.** When the lift is `LOADED` or
  `BODYWEIGHT_ADDED`, target reps ≤ 10, direction is not falling and the
  newest top-set RPE is at least two below the goal's target RPE, the hint
  moves one plate step up with reps unchanged (`LW_TABLE`), before
  `RpeModifier` gets its veto. The table (RPE → reps in reserve → % of the
  metric) is used as a **relative** step, never as an absolute load: the
  step is still `IncrementTable`'s, and never more than one step from the
  last top set unless the trend has been rising for three usable sessions.
  Otherwise double progression as today. The target RPE is a threshold
  supplied to the rule from a goal → threshold table in `domain` beside
  `AddDefaults` (ADR-025 d.1), one integer per goal, all five named
  (STRENGTH 8, HYPERTROPHY 8, ATHLETIC 7, RESILIENCE 7, GENERAL 8, owner
  to confirm); the rule itself is identical for every goal (ADR-025 d.2).
  ADR-033 lists that table beside the three RPE numbers already in code
  (`ANOTHER_SET_RPE_CEILING` 7, `VolumeRamp.RPE_CEILING` 8,
  `RPE_HOLD_THRESHOLD` 9) with one sentence each. A single top-set RPE 10
  holds the load on its own (today two sessions averaging 9 are needed).
- **Honesty.** Fewer than 5 usable sessions → today's rule, trace says
  "Not much history yet" (G2). Pounds and kilograms give the same *step*;
  the snapped number lands on each unit's own grid, and the ADR says so.
- **Evidence.** `LW_TABLE` → `helms-2016-rpe-application` (the table),
  `zourdos-2016-rpe-rir` (the scale's validity), `hackett-2017-rtf-accuracy`
  (why RPE ≥ 8 is trusted more than 6–7); `LW_VETO` → helms-2016,
  hackett-2017 as `RPE_HOLD` already does; `LW_TREND` and `LW_CAP`
  heuristic, no DOI: no paper models a 12-week slope. Schoenfeld 2017 stays
  on `CLIMB_REPS` / `BW_ADD_REP` only. The exact table cells used (reps
  1–10 × RPE 6–10) are reproduced in the ADR with the paper's table number;
  any interpolated cell is heuristic. Candidate additions the sports-science
  review named, each to be DOI-verified before it enters the catalog:
  Helms et al. 2018 (RPE- versus percentage-based load prescription),
  Graham & Cleather 2021 (autoregulation), Refalo et al. 2023 and Robinson
  et al. 2024 (proximity to failure), Grgic et al. 2018 (rest intervals),
  and a RIR-first wording of the scale.
- **Out of scope.** Cardio, holds (R2-4), session-level pacing, an LLM, and
  the in-set rules.

**PR 1 — domain.** `domain/LongWindow.kt` beside `ProgressionCalculator`
(one progression family, one package): `WindowedHistory`, `TrendMetric`
(shared with `StallSignal`), `TrendModel`, `RpeLoadTable`, `TargetRpe`
table. `progressionFor` / `ProgressionHintLoader` compute the window and
pass it to `ProgressionCalculator.adjusted(window = …)`; `adjusted` defaults
`window = null`, so `SetMicroRec`'s in-set call passes nothing and every
in-set test holds unchanged (G3). The window rides inside `ProgressionHint`
(new fields), not as a new coach input, so the rest page and
`NextSetInputsTest` are untouched and no second history read happens.
`RuleTrace` needs no new fields: the evidence window and the typed facts
carry the window and slope. Tests: `LongWindowTest` (metric per load class;
Theil–Sen on flat, rising, falling, noisy, three-point and gapped series;
the table as a relative step; the cap; under-five fallback; RPE 10 veto),
extended `ProgressionCalculatorTest`, the existing coach suites green.

**PR 2 — data.** `WorkoutRepository.lastFinishedWork` takes `sinceEpochDay`
and `limit = 24`, computed by `progressionFor` from the coached session's
date; the DAO query is already indexed for this read (audit A1: no index,
no Room bump). Test: a repository test with 30 sessions over 100 days
returns the 24 within 84 (the repository is where the bound is applied).

**PR 3 — evidence.** `EvidenceCatalog` and `docs/coach/evidence-seed.json`
gain the four rule ids; `CoachPolicyEvidence` maps them. A new test holds
that `citedByRules` in the JSON equals `CoachPolicyEvidence` for every
code (the two already disagree on 10 of 17; this PR reconciles them
first), that every non-heuristic id has a DOI, and that no heuristic has
one.

**PR 4 — UI.** *Why?* shows "12 weeks · 9 sessions · rising" and the
citation chip as today; no new screen. `CoachEvidenceCopy` and
`RuleTraceCopy` carry the words. `LogNextCardInputsTest` holds that the
window reaches the card through the hint; `CoachRecomputeTest` holds that
a second of rest still does not recompute.

**Phone checklist.** A lift with 10+ sessions shows the window in *Why?*; a
lift done twice says "Not much history yet"; a week marked lighter still
holds the weight; switching to pounds moves the number to the pound grid
and keeps the step.

### P6 — The coach's name (Quiet, small, half a day, after the owner picks)

**Owner's words.** "We should come up with a name for an Inhouse, almost
like, ai. We should give this little helper/personal trainer a name."

**Where this narrows it.** Never "AI" in user copy (ADR-008). Owner
decision D9: a gym word. The first pick, Tempo, is withdrawn: it is a
lifting term this codebase may need as a feature (rep tempo), it sits too
close to the brand Temper, and a fitness product already carries it.

**Shortlist, checked for clashes on 29 September (app stores, web).**
Every gym word has neighbours; none of these is a trademark question for a
personal build, and the check is about a future store listing and about
confusion. **Tempo** collides outright: Tempo (tempo.fit) is a smart home
gym whose app recommends weights and asks how many reps you had left, the
very thing our coach does. **Spot** — the spotter behind you, the word for
exactly what a coach on the floor does; reads as a person ("Spot suggests
100 kg"). Neighbours: Spotr, Spottr, Spot Me, Spotter (coaching apps); no
fitness app called plainly Spot was found. **Rack** — where the bar lives;
neighbours Rack Strength, Re-rack, Rack (CrossFit), GymRack; and a thing,
not a helper. **Chalk** — Chalk Performance Training, Chalk Gyms, Chalk
(tracker): crowded. **Collar** — the clip that holds the plates on the bar;
keeps the lift honest; no fitness app found under it, but it says less on
its own. Recommendation: **Spot**; **Collar** if the owner wants a word
nobody else uses.

**Files.** `domain/CoachIdentity.kt`; the six copy sites
(`OnboardingScreen.kt:147`, `SyncCopy.kt:23`, `ProgressionCopy.kt`,
`RuleTraceCopy.kt`, `MastheadCopy.kt`, `coach/CoachEvidenceCopy.kt`; code
comments are not copy and are left); `data/backup/BackupJson.kt:22` and
`BackupValidator.kt:427` ("Personal Trainer" → "Temper"; the backup format's
own constants are exempt); a new checker `tools/check-coach-name.py`
scoped to string literals in `app/src/main`, with its paired test, listed
in `tools/preflight.sh` and counted in `DEVELOPMENT.md`.

**Done when** G10 holds.

## 6. Dependencies and risks

| Risk | Where | Mitigation |
|---|---|---|
| Forced effort slows the floor | P2a | one tap; the outlined recommended chip is the shortcut; warm-ups and holds exempt |
| The lead-in collides with the hold's reboot guard | P2b | the guard learns both deadlines; a test for a hold restored mid-lead-in |
| Audio focus behaves oddly on some phones | P4b | default DUCK; NONE one tap away; the focus result is logged |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is Play-restricted | P4a | personal build only; `COMMERCIAL_BOUNDARY.md` line for the store build |
| DND bypass silently stripped | P4b | drop `setBypassDnd`; copy says the alarm follows Do Not Disturb like any alarm |
| New prefs land while sync is paused | P4b | classified archived or device-local; none travels until S1 |
| Twelve-week read is slow on a long history | P5 PR 2 | bounded query, cap 24, one read per lift open (W2e) |
| Trend flips on a noisy lift | P5 | Theil–Sen, rep-banded trend window, 5-point minimum, one-step cap, RPE veto, G3 |
| A rule cites a paper for a claim it does not make | P5 PR 3 | the reconciliation test; heuristic marking; the ADR reproduces the table cells |
| Settings packets collide with F10 | P4 | P4 lands first; F10c rebases on it |
| The height budget refuses a floor change | P2b | GET READY is the bar's own kicker slot; nothing new below the fold |

## 7. Decision log

| # | Decision | Owner said (29 September) |
|---|---|---|
| D1 | How Temper asks for permissions | **Keep the rule, add the page.** First launch asks the three it asks today; the lock-screen alert is asked the first time a rest runs; the Permissions page shows all five with a fix each. ADR-012 d.4 gains the first-open walk as its signed exception. |
| D2 | Which sets need an effort | **Working sets only.** Warm-ups and timed holds still log without one. Reverses W1b's "Effort · optional". |
| D3 | The get-ready countdown | **Holds only, 5 seconds** (3 / 5 / 10 offered). Not the set stopwatch. |
| D4 | Music during the rest alert | **Turn the music down briefly** (duck) by default; pause and leave-alone as choices. |
| D5 | Where the twelve weeks show up | **Both**: the opening numbers and, through them, the set-to-set nudges. The in-set rules themselves do not change. |
| D6 | Training rhythm | **About once a week** per lift; 84 days is about 12 sessions; the 24-session cap never bites. |
| D7 | Packet order | P1 → P2a → P2b → P3 → P4a → P4b → P5 → P6 ("Please proceed"). |
| D8 | Which menus get images | **Every ⋮ menu in the app.** |
| D9 | The name's style | **A gym word.** Shortlist in P6; Tempo withdrawn. |

## 8. Questions still open for the owner

Plain questions; one answer each. Nothing below blocks P1's drop.

- **Q1 (P2a, P5).** With effort required, an easy working set has nothing to
  say: the scale starts at 6, "four reps left". Should the scale gain a
  **5 = "five or more left", labelled Easy**? Saying yes changes one locked
  rule (a 5 reads as "had more in you", like 6 and 7) and needs a line in
  the coach's ADR. Recommend **yes**.
- **Q2 (P5).** Should workouts older than twelve weeks be **forgotten**, or
  **count for less** rather than not at all? Recommend **forgotten** for the
  first version; "count for less" is a later ADR line.
- **Q3 (P4b).** Do you want a **nudge on a day you planned to train and did
  not**? Nothing like that exists today; it would be its own small packet.
  Recommend **yes, later**, after M3.
- **Q4 (P1).** The Working / Warm-up switch and the exercise header have
  no frame. On the P1 phone check, does that look right, or should the switch get
  its own box?
- **Q5 (P6).** From the gym-word shortlist, checked for clashes, **Spot**
  is the pick (near neighbours Spotr, Spottr, Spot Me; none plainly Spot);
  **Collar** is the uncrowded alternative. Which one, or neither?
- **Q6 (process).** May each packet after P1 go on **its own branch off
  `trunk`** with its own PR, as the repo's rule says? This session was told
  to use one branch, so it needs your word.
- **Q7 (G12).** On the P1 phone check, log a working set that is not the lift's last
  planned one. Did the rest start by itself? If not: was it a warm-up, the
  last set, a correction, or were rest alerts switched off?
- **Q8 (P3).** By "images" on the menu rows, do you mean **line icons** in
  the app's stroke (the recommendation) or **pictures**?
- **Q9 (P4b).** By "count down haptic", do you mean a **vibration on each of
  the last five seconds of rest**, and on the GET READY ticks? That is what
  the plan builds.

## 9. Phone check record

One row per drop; the owner's words go in the last column.

| Drop | Packets | What to check | What the owner saw |
|---|---|---|---|
| 107 | P3 | Open a live workout, tap ⋮, confirm a mark beside each row | — |
| 108 | P1 | P1's checklist above, plus G12 and Q4 | — |

## 10. Side issues found, not in this plan

- Nine leftover vehicle heads on origin (`claude/*`, `cursor/*`, `codex/*`,
  `probe/*`); `owner-loop.mdc` says to delete them.
- "PersonalTrainer Backups" Drive folder and `personal-trainer-backup-*`
  file names: a backup-format decision, not copy.
- `citedByRules` in `docs/coach/evidence-seed.json` disagrees with
  `CoachPolicyEvidence` on 10 of 17 codes today (audit C-3); P5 PR 3
  reconciles them.
- The backup leaves `restTickEnabled` behind; P2b takes it along with
  `leadInSeconds`.
- The "Rest complete" channel's `setBypassDnd(true)` is stripped by Android
  for an app without notification-policy access; P4b removes it and says so.
- `AccessibilityMatrix.kt` and `InformationArchitecture` ship in `main` as
  test scaffolding (CURRENT_STRUCTURE's known debt).
- A permanent Home render (the `AuditRenderTest` harness made a gate) is
  W3's; until then P1's Home edge is checked by eye on the phone.
