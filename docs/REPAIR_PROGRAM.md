# Repair program — the 1 September audit, packet by packet

**Status:** proposed — not started  
**Derived from:** [foundation-program/evidence/FD-audit-2026-09-01.md](foundation-program/evidence/FD-audit-2026-09-01.md)  
**Authority it obeys:** [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md), [architecture/](architecture/README.md) ADR-001…022, [UX_PAGE_PASS.md](UX_PAGE_PASS.md)

Living plan, same method as the Job plans: if a clearer move shows up while
building, take it, write it under *Floor findings*, and strike the old line
with the reason. The plan loses to the floor; it does not lose to
convenience.

## How this plan works

One packet is one branch, one PR into `trunk`, one evening unless marked
otherwise. A packet is **done** when its named test fails on `trunk`,
passes on the branch, `./gradlew testDebugUnitTest assembleDebug` is green,
`tools/preflight.sh` is clean, and — where a phone gate is named — the owner
has seen it on Temper Debug.

Every packet below carries five things so it can be picked up cold:

- **Symptom** — what you see on the phone today.
- **Cause** — the code, with file and line.
- **Change** — what to do, in order.
- **Proof** — the test that must go red before the fix and green after. A
  packet with no red-first test is not finished; it is hoped.
- **Owns** — the files it edits. Two open PRs may not own the same file
  (owner-loop rule), so this column is the merge order.

Phases are ordered by what they protect: first the numbers Temper reports
about your training, then the timer, then the week, then the paths that do
not exist, then speed, then the surface. Within a phase the order is a
dependency order, not a taste order.

## What this program will not do

No sixth tab. No Library or Goals as a tab. No LLM coach. No package or
Drive-folder rename. No `fallbackToDestructiveMigration`. No light theme.
No Start outside Home, the start sheet, or Repeat. Two rows in the audit
need a database column and are held for one signed v5 packet at the end;
nothing else here touches the schema.

## Decisions needed before the packets that carry them

Six calls are yours. Each has my recommendation and what it costs to go the
other way. Packets that need one are marked; everything else can start now.

| # | Decision | My recommendation | If you choose otherwise |
|---|---|---|---|
| 1 | The start sheet has no host. Restore it, or delete it and put its two actions on Home? | **Restore it**, hosted on Body, History and Plan as UX_PAGE_PASS §4 already says. It is written, tested and matches two ADRs. | Deleting means removing ~390 lines plus tests, then building "log a past workout" and "start cardio" into Home's Add picker — the same evening's work, and ADR-006 T2 and ADR-021 §2 both need amending. |
| 2 | How late is "missed"? | **End of the civil day.** A session is missed when the day is over, which is how a person thinks about it; the prompt becomes a morning event, and no new preference is needed. | A fixed grace (say two hours) keeps the evening prompt but needs a number nobody can defend, and still surprises anyone who trains late. |
| 3 | Rest cue channel. | **Alarm.** The rest timer exists to interrupt you; a gym phone on Do Not Disturb that stays silent for rest is the feature failing. Clock apps do exactly this. The Settings sound toggle stays the off switch. | Staying on the notification channel keeps DND fully silent and leaves rest inaudible in the mode most people use in a gym. |
| 4 | Settings Export: the code makes it a quiet button, ADR-014 §4 and `AccessibilityMatrix` say it is the page's Volt. | **Make the code match the ADR.** It is the only guard against losing your history, and the 14-day stale caption already nags about it. | Amending the ADR and the matrix line is fine too — but then Settings is the one tab with no act, and the nag has no button to point at. |
| 5 | Deleting or unpinning a pinned session currently also erases the record that you *did* it on earlier days. | **History survives.** Retire the rule (`enabled = false`) instead of deleting it; delete only the future planned rows. | Keeping the cascade means a tidy rules table and a week board that forgets completed work. |
| 6 | The signed release build has never run on a phone. | **One evening to prove it**, before the next `v*` tag: install it, export, restore, share diagnostics. | Skipping it means the first time R8, the ProGuard rules and the crash bundle ever execute is on the build you hand out. |

## Packet index

Forty-one packets, plus two held. "Evenings" is a working evening at this
repo's test bar, not an optimistic hour. The total is about fifty-five;
phases A–C, which cover everything that misreports your training or breaks
the gym floor, are fifteen of them.

| # | Packet | Evenings | Needs | Phase |
|---|---|---|---|---|
| A1 | Pound progression lands on real plates | 1 | — | Truth |
| A2 | An edited set refreshes every screen | 1 | — | Truth |
| A3 | A finished session cannot be un-finished | 1 | — | Truth |
| A4 | Restore reports what actually happened | 1 | — | Truth |
| A5 | The coach reads all of your history | 1 | — | Truth |
| A6 | Records and deload read assisted lifts correctly | 1 | — | Truth |
| B1 | The rest service stops when the rest does | 2 | — | Timer |
| B2 | The cue plays where you can hear it | 1 | 3 | Timer |
| B3 | A late rest still announces itself | 1 | — | Timer |
| B4 | Timer surfaces stop lying | 1 | — | Timer |
| C1 | Nothing is born overdue | 1 | — | Week |
| C2 | Rebuild keeps what you added; rules retire | 1 | 5 | Week |
| C3 | Tonight is startable, and today knows the time | 1 | 2 | Week |
| C4 | Planner and session writes are atomic | 1 | — | Week |
| D1 | The start sheet gets a home (or a grave) | 1 | 1 | Paths |
| D2 | Reminder Start works from anywhere | 1 | — | Paths |
| D3 | Live cardio is visible; errors dismiss; drafts survive | 2 | — | Paths |
| E1 | Stop recomputing everything | 1 | — | Speed |
| E2 | Thumbnails stop decoding at full size | 2 | — | Speed |
| E3 | The shell stops recomposing every second | 1 | — | Speed |
| E4 | Query and recompute hygiene | 2 | — | Speed |
| F1 | The logging loop keeps the wells on screen | 1 | — | Design I |
| F2 | One green button per screen | 1 | 4 | Design I |
| F3 | Text you can read in a gym | 1 | — | Design I |
| F4 | Big text does not break the screen | 2 | — | Design I |
| F5 | One word per thing | 1 | — | Design I |
| F6 | Today is not buried by the missed-work card | 1 | — | Design I |
| G1 | A screen reader can use Temper | 3 | — | Design II |
| G2 | One numeric-entry grammar | 1 | — | Design II |
| G3 | Shared headers and docks | 2 | — | Design II |
| G4 | Skin the four foreign controls | 2 | — | Design II |
| G5 | Body's first viewport; small targets; destructive confirms | 1 | — | Design II |
| G6 | Reduced motion, and the palette question | 1 | — | Design II |
| H1 | History shows that you got stronger | 2 | — | Design III |
| H2 | Units and clocks finish what Display started | 2 | — | Design III |
| H3 | Row and card vocabulary; landscape; a regression net | 3 | — | Design III |
| J1 | The release build is real | 1 | 6 | House |
| J2 | The release ratchet and CI pinning | 1 | — | House |
| J3 | App size | 1 | — | House |
| J4 | Tests stop sleeping | 2 | — | House |
| J5 | The checkers report what they skip | 1 | — | House |
| K1 | *(held)* One signed v5: session time zone and the index census | 2 | — | Schema |
| K2 | *(held)* The compiler train: Kotlin, AGP, Compose, Room | 4+ | — | Toolchain |

---

# Phase A — Truth

Six packets. Everything here changes a number Temper reports about your
training, or lets something else change it behind your back. Nothing in a
later phase matters if these are wrong.

## A1 — Pound progression lands on real plates

**Symptom.** Training in pounds, hit your target every session from 100 lb:
the app suggests 105, 110, then **115.5** — half a pound you cannot load —
and by the tenth session says 151 where it should say 150. The prefilled
weight, the in-workout strip, the coach card, the plate caption ("and 0.5
leftover") and your history all carry the drift. It also splits records:
typing "115" and accepting the app's "115" store two different numbers, so
"most reps at this weight" never accumulates.

**Cause.** `domain/ProgressionCalculator.kt:53` adds a kilogram step
(`STEP_LBS_IN_KG` = 2.26796) to a value already on the 0.1 kg grid and
re-quantises, so the real step is 2.3 kg = 5.07 lb and the error compounds.
`WeightConverter.incrementKg` (`domain/WeightFormat.kt:70-73`) — what the
+/− plates use — already does it correctly by stepping in display units
first. The comment above line 53 claims the quantisation prevents this.

**Change.**
1. Give `ProgressionCalculator.hint` and `suggestWeightKg` the display
   `unit`. All three call sites already hold it —
   `WorkoutRepository.kt:730` and `:926`, and `SetMicroRec.kt:208`/`:300`
   via `inputs.unit` (`SetMicroRec.kt:19`).
2. Compute the next weight as
   `WeightConverter.toKg(toDisplayValue(lastKg, unit) ± displayStep, unit)`
   using `IncrementTable.displayStep`, keeping the assistance inversion and
   the floor at zero exactly as they are.
3. Leave `stepKg` for the copy layer, which only needs a size to name.

**Proof.** `ProgressionCalculatorTest` gains a twelve-session walk in each
unit asserting the *displayed* weight is exactly `100 + 5n` (lb) and
`50 + 2.5n` (kg); plus a test that a suggestion accepted and the same
number typed store identical kilograms. Both fail on `trunk` today.

**Owns.** `domain/ProgressionCalculator.kt`, `domain/SetMicroRec.kt`,
`data/repository/WorkoutRepository.kt`, their tests.

## A2 — An edited set refreshes every screen

**Symptom.** Correct a mistyped weight on a past session. That screen
updates; Home's "Last session", History's volume and totals, and the Body
heat map keep the old number until you happen to finish an unrelated
workout. It looks random because deleting or adding a set *does* refresh.

**Cause.** `WorkoutDao.observeFinishedWorkGeneration` (`:52-71`)
fingerprints finished work as session count, duration sum, last finish,
working-set count and last set time. `observeSessionSummaries` and
`observeFinishedSince` (`WorkoutRepository.kt:117-142`) only re-query when
that changes; `updateSet` (`:489-518`) deliberately touches neither
`completedAt` nor `setNumber`, so nothing in the fingerprint moves. This is
a regression from the 30 August write-amplification fix, and it breaks
UX_PAGE_PASS page 9's gate ("edit a finished set → heat and volume move").

**Change.** Add `COALESCE(SUM(sl.weightKg * sl.reps), 0)` and
`COALESCE(SUM(sl.reps), 0)` to the fingerprint query and the matching
fields on `FinishedWorkGeneration`. Both are cheap aggregates over the join
the query already performs. No schema change — this is a query, not a
table.

**Proof.** A repository test that collects `observeSessionSummaries`, edits
a finished set's weight, and asserts the flow re-emits with the new volume.
Red on `trunk`.

**Owns.** `data/local/dao/WorkoutDao.kt`,
`data/local/entity/FinishedWorkGeneration.kt`, its test.

## A3 — A finished session cannot be un-finished

**Symptom.** You did the session. Its reminder is still in the shade. Hours
later you tidy the notification and press **Skip** — your completed workout
is now marked skipped, the day loses its credit, and the week says you did
nothing. **Move** is worse: it marks the finished row as moved and mints a
duplicate for tomorrow.

**Cause.** Three gaps that compound. `cancelReminders`
(`PlannerRepository.kt:541-553`) and
`WorkManagerReminderScheduler.cancelForOccurrence` (`:34-36`) cancel the
scheduled *job* but never dismiss a notification already posted — only
`MainActivity` does, and only on a Start launch. `skipOccurrence`
(`:310-316`) and `moveOccurrenceForward` (`:400-434`) accept any status.
`ActivityRepository.confirm` and `completeLive` mark DONE without touching
reminders at all.

**Change.**
1. Guard `skipOccurrence` to `PLANNED | MISSED` and `moveOccurrenceForward`
   to `PLANNED`; a refused call returns quietly, as the other guards do.
2. Have `cancelForOccurrence` also call
   `ReminderNotifications.cancel(appContext, occurrenceId)`.
3. ~~Route `ActivityRepository`'s two DONE writes through
   `PlannerRepository.markOccurrenceDone`~~ **Keep the DONE write inside the
   activity transaction and hand the settled occurrence to a callback after
   it commits**, which cancels reminders — removing the third hand-coded
   `"DONE"` literal at the same time. (See *Floor findings*, 2026-09-01.)

**Proof.** Three tests: Skip on a DONE row leaves it DONE; Move on a DONE
row mints nothing; finishing a session cancels its posted reminder. All red
today.

**Owns.** `data/repository/PlannerRepository.kt`,
`data/repository/ActivityRepository.kt`,
`reminder/WorkManagerReminderScheduler.kt`, `reminder/ReminderNotifications.kt`.

## A4 — Restore reports what actually happened

**Symptom.** A restore that fails before it touches anything tells you
Temper "is finishing it from the copy already on this phone" — and then
refuses to start any workout until you relaunch the app.

**Cause.** `BackupRepository.kt:201` (`mark(WIPING)`) throws on a disk
failure; `namedCommitFailure` (`:311-324`) reads phase `STAGED` and falls
through to the interrupted-restore message. The `STAGED` journal stays open,
so `WorkoutRepository.kt:82-87` refuses every start until the next recovery
pass runs. Two smaller things ride along: `RestoreJournalStore.clear()`
(`:45-48`) deletes the state file before the incoming file, so a crash
between them can leave the decrypted backup on disk with nothing pointing at
it; and the unlock passphrase (`SettingsViewModel.kt:742-755`) is the one
`CharArray` never wiped.

**Change.**
1. Wrap the `mark(WIPING)` call: on failure `clear()` the journal and throw
   "Restore could not start. Nothing was changed."
2. In `namedCommitFailure`, treat `STAGED` and null as the nothing-changed
   branch.
3. Delete the incoming file before the state file in `clear()`, and sweep an
   orphan incoming file on `read()`.
4. Wipe the unlock passphrase in a `finally`.
5. While here: bind the envelope's authentication tag to the *parsed*
   version rather than the compile-time constant
   (`BackupEnvelope.kt:195-196`), and commit a v1 fixture with a known
   password so a future version bump cannot silently orphan every existing
   backup file.

**Proof.** ~~A stage-failure test~~ **A `commitFailureMessage` test** asserting
the message is honest for every phase, plus an orphan-sweep test; a fixture
test decrypting a committed v1 envelope. (See *Floor findings*, 2026-09-01.)

**Owns.** `data/repository/BackupRepository.kt`,
`data/backup/RestoreJournalStore.kt`, `data/backup/BackupEnvelope.kt`,
`ui/settings/SettingsViewModel.kt`.

## A5 — The coach reads all of your history

**Symptom.** A muscle you trained five weeks ago gets a high-priority card
saying "Back has no logged work — nothing in history maps to Back", sitting
directly beneath a Body map that correctly says "35 days since".

**Cause.** `TrainingInsights.kt:138-145` builds the coach's basis from the
32-day windowed graph and never overlays lifetime recency; the display
snapshot does exactly that at `:118-126`. Past the window
`daysSinceLastTrained` is null, and `RecommendationEngine.kt:235-247`
renders null as "no logged work". `CoachBasisTest` passes because it feeds
`coachBasis` an unwindowed list directly.

**Change.** Pass `lastLoggedAtByExerciseId` and the catalog into
`coachBasis` and overlay `MuscleRecency.byMuscle` onto the basis, mirroring
`rememberLifetimeRecency`. While in the file, ~~drop CORE from
`neglectedMuscles`~~ **invert the `coreCoverageGap` guard** so it stops
producing two Coverage cards for the same gap (see *Floor findings*,
2026-09-01).

**Proof.** A `TrainingInsightsCalculator` test with a 40-day-old session
absent from the window but present in the recency map: the card must read
"35 days since", not "no logged work".

**Owns.** `domain/TrainingInsights.kt`, `domain/MuscleLoadCalculator.kt`,
`domain/RecommendationEngine.kt`, their tests.

## A6 — Records and deload read assisted lifts correctly

**Symptom.** Three quiet wrong answers. A 110 kg set in week one of a block
is celebrated as two personal records although your standing best is 150 kg.
An assisted pull-up that improved — 20 kg of help down to 10 kg — reads as a
regression, so the "sets up three weeks, strength flat, schedule a lighter
week" card fires at someone who is progressing. And adding *more* assistance
while doing one more rep earns "Most reps ever".

**Cause.** `BlockReview.countRecords` (`:131-152`) seeds its comparison list
with in-block sets only, contradicting its own documentation; the existing
test passes because its single set is the first one. `DeloadSignal.kt:113`
computes estimated one-rep max without the load class, so assistance
kilograms read as bar load. `PersonalRecords.kt:206-208` compares reps for
every reps-are-the-measure class without comparing the assistance.

**Change.**
1. Seed `countRecords` from the sessions it already receives, filtered to
   before the block start.
2. Resolve the load class in `DeloadSignal` and use negated assistance as
   the strength proxy (or exclude assisted lifts from the top-three).
3. For `BODYWEIGHT_ASSISTED`, ~~count a rep record only at equal-or-less
   assistance~~ **keep the all-time rep count as the bar and require the
   standing rep record to have been set at no less assistance**; extend
   `recordPriorsBefore` with the matching aggregate. (See *Floor findings*,
   2026-09-01.)

**Proof.** Three tests, each red today: a pre-block best suppresses the
in-block "record"; an improving assisted lift vetoes the deload card; more
assistance with more reps is not a record.

**Owns.** `domain/BlockReview.kt`, `domain/DeloadSignal.kt`,
`domain/PersonalRecords.kt`, `data/local/dao/WorkoutDao.kt` *(after A2)*,
their tests.

---

# Phase B — The rest timer keeps its promise

Four packets. This is the feature built specifically to work while the phone
is asleep in your pocket, and it is the one with the most ways to fail
silently. Every packet here carries a phone gate; none of them is finished
on a green test alone.

## B1 — The rest service stops when the rest does · 2 evenings

**Symptom.** Screen off, rest ends. The alarm fires, the cue plays — and the
ongoing "Rest" card never leaves the shade. It counts backwards, "−0:42",
with live +15s and Skip buttons, for roughly as long as the rest was.
Tapping it opens the lock-screen page, which immediately closes.

**Cause.** On the alarm path `RestTimerCompletion.kt:58-67` stops the store
with `stopIfCurrent(id, fromService = true)`, and `RestTimerController.stop`
(`:100-111`) deliberately skips `ACTION_STOP` when the call came from the
service. The follow-up `NotificationManager.cancel(RUNNING_ID)` cannot
remove a foreground-service notification — its own comment says the service
teardown does that. The teardown is
`handler.postDelayed(completeRunnable, delayMs)`
(`RestTimerService.kt:103-104`), an uptime-clock timer that does not advance
while the CPU is suspended, so it fires only after the phone has been awake
as long as it slept.

**Change.**
1. In `RestTimerService.onCreate`, collect `controller.snapshot` and call
   `stopNow()` the moment it reports not running. The service then follows
   its source of truth instead of racing a handler.
2. Drop the ineffective `cancel(RUNNING_ID)` from `completeOnce`.
3. Keep the last `startId` and use `stopSelf(startId)`, and in `onComplete`
   only tear down when nothing is running — today a "+15s" tapped as the
   clock hits zero leaves the extension running with no card and no
   controls (`RestTimerService.kt:159-188`).

**Proof.** A Robolectric service test: completion through the alarm path
stops the service and removes the notification; a +15 s extension claimed
mid-completion leaves the service alive with a running card.

**Phone gate.** 60-second rest, screen off, `adb shell dumpsys deviceidle
force-idle`. The cue fires on time *and* the shade is clean afterwards.

**Owns.** `timer/RestTimerService.kt`, `timer/RestTimerCompletion.kt`,
`timer/RestTimerController.kt`.

## B2 — The cue plays where you can hear it · needs decision 3

**Symptom.** Sound is on in Settings, the rest ends, and nothing plays —
because your media volume is down or a pair of earbuds is paused. On Do Not
Disturb, nothing plays and nothing buzzes at all.

**Cause.** `RestTimerAlerts.kt:63-64` calls
`MediaPlayer.create(context, R.raw.rest_done)`, which prepares the player,
and only *then* sets the audio attributes — which the platform documents as
having no effect after `prepare()`. The cue therefore routes to the media
stream while `ringerIsSilent` models the notification stream. Separately,
both the tone and the vibration declare `USAGE_NOTIFICATION`
(`:17-20, 91-96`) with `setBypassDnd(false)`, and Do Not Disturb suppresses
both.

**Change.**
1. Use the four-argument `MediaPlayer.create(context, res, attributes,
   AUDIO_SESSION_ID_GENERATE)` so the attributes apply before preparation.
2. Per decision 3, move tone and vibration to `USAGE_ALARM`
   (`VibrationAttributes.USAGE_ALARM` on API 33+) and align or retire the
   ringer-silent gate accordingly.

**Proof.** A test asserting the attributes reach `create` rather than a
post-`prepare` setter. Audio routing itself is a phone check.

**Phone gate.** Media volume at zero, ringer up, rest completes audibly.
Then with Do Not Disturb on, per the decision.

**Owns.** `timer/RestTimerAlerts.kt`, `timer/RestTimerNotifications.kt`.

## B3 — A late rest still announces itself

**Symptom.** Android killed the app during a long rest. The alarm arrives
more than a minute late — normal, because Android 14 denies precise alarms
by default and inexact ones batch — and the rest completes in total silence.

**Cause.** `RestTimerRehydrator` classifies a same-boot expiry older than
`LATE_ALERT_GRACE_MS` (60 s) as `None`
(`RestTimerStatePersistence.kt:126, 173-179`) and the controller clears the
disk row (`RestTimerController.kt:156-159`). `rehydrate()` runs in
`Application.onCreate`, before the receiver, which then finds nothing and
treats the rest as already completed
(`RestTimerAlarmReceiver.kt:63-72`).

**Change.** Return `Expired(late = true)` for any same-boot expiry; announce
with the cue inside the grace and post a silent "Rest done" beyond it. Do
not clear the disk row during `onCreate` — let the claim path clear it, so
the receiver still has something to claim.

**Proof.** A rehydration test for a five-minute-late row: the outcome is
`Expired`, the notification is posted, the cue is suppressed. Red today.

**Owns.** `timer/RestTimerStatePersistence.kt`,
`timer/RestTimerController.kt`, `timer/RestTimerAlarmReceiver.kt`.

## B4 — Timer surfaces stop lying

**Symptom.** Four small ones. The gold "Back to the bar" flash appears only
sometimes. The lock-screen glance shows "Back to the bar" after you *skipped*
a rest or finished the workout. A phantom "Rest 0:00" card can appear after
a process restart. And returning to the app can flash a stale second before
the real clock arrives.

**Cause.** The finish flash is a race between the shared poll emitting zero
and the store being cleared (`Common.kt:726-730`,
`RestTimerScreen.kt:162-166`). `RestLockActivity` is `singleInstance` but
does not override `onNewIntent`, and derives "finished" from running going
false (`:70-105, 164-168`). `ensureForegroundClaimed` runs before the null
and STOP branches, so a sticky restart claims foreground with an idle
snapshot (`RestTimerService.kt:48, 107-112`). The shared rest poll keeps its
replay cache after its subscribers leave
(`RestTimerController.kt:66-70`).

**Change.** Publish a `lastCompletedTimerId` from `completeOnce` and key the
gold flash on it. Override `onNewIntent` in `RestLockActivity`, and finish
when the timer stops without completing. Rehydrate before claiming
foreground on a null intent. Set `replayExpirationMillis = 0`. Use
`FLAG_NO_CREATE` when cancelling the alarm, and log the two swallowed
foreground-service failures instead of discarding them.

**Proof.** A completion-signal test for the flash; a lock-activity test that
a skip does not render the finished state.

**Owns.** `timer/RestLockActivity.kt`, `timer/RestTimerService.kt`,
`timer/RestTimerController.kt`, `timer/RestTimerAlarmScheduler.kt`.

---

# Phase C — The week is honest

Four packets. The planner is the newest large subsystem and carries the
highest density of P1s; three of the four rows below are in code the last
two audit rounds wrote.

## C1 — Nothing is born overdue

**Symptom.** Finish the setup wizard on a Thursday evening with Monday,
Wednesday and Friday chosen. Home opens saying "2 planned sessions were not
done", with red cells on Monday and Wednesday, "3 planned · 0 done", and two
leftovers in Still open that were never scheduled — and the one scheduling
decision you get per week is already spent. Add a cardio block at 9am and it
lands at 07:00, overdue at birth.

**Cause.** `OccurrenceGenerator.generateWeek` (`:36-73`) has no notion of
now: every enabled rule is placed on `weekStart.nextOrSame(rule.weekday)`.
`ensureWeek` (`PlannerRepository.kt:232-253`) passes neither today nor the
time; onboarding and the custom-week wizard call `publishPinnedWeek`
immediately after setup. `DayBlocks.addCardio` defaults to 07:00 and
`nextLaterHour` ignores the clock. Only ADAPT guards this, and its own
comment (`MissedWorkPolicy.kt:94-98`) names the rule the generator does not
follow.

**Change.**
1. Give `generateWeek` `todayEpochDay` and `nowMinutes`; skip a
   `(rule, date)` pair that sits behind now **when the rule was created
   after that day began**. Rules that predate the day must still mint it, or
   week rollover stops recording history.
2. Clamp same-day hours to `max(default, current hour + 1)` in
   `DayBlocks`, `AuxiliaryBlocks` and `PlanViewModel.addCardio`.
3. Give `ExistingLayoutMatcher.match` a `todayEpochDay` and drop days behind
   it, as `WeeklySchedulePlanner.plan` already does.

**Proof.** A generator test: a rule created this evening mints nothing for
earlier days of the same week, while a rule created last month still does.
A `DayBlocks` test that a 21:00 add does not land at 18:00. Both red today.

**Owns.** `domain/OccurrenceGenerator.kt`, `domain/ExistingLayoutMatcher.kt`,
`data/repository/PlannerRepository.kt`, `data/repository/DayBlocks.kt`,
`data/repository/AuxiliaryBlocks.kt`, `ui/plan/PlanViewModel.kt`.

## C2 — Rebuild keeps what you added; rules retire · needs decision 5

**Symptom.** Two ways to lose work. Choose "Rebuild the rest of the week"
after a missed day and every one-off you added — a "just today" stretch, a
session you dragged onto today — silently disappears, while its original day
stays marked as moved, so the session is gone from the strip, from Still
open and from the summary. Separately, deleting or unpinning a session also
erases the record that you *did* it on earlier days.

**Cause.** ADAPT treats every planned row from today onward as regenerable
(`MissedWorkPolicy.kt:81-105`) and regenerates only from *enabled* rules on
*their own* weekday; a one-off belongs to a rule that `DayBlocks` disabled
after minting (`:108, 178`), and a relocated row sits on a day that is not
its rule's weekday. Neither regenerates, so both land in `removed` and
`PlannerRepository.kt:283-285` deletes them. The second half is the foreign
key: `schedule_occurrences.ruleId` cascades
(`PlannerEntities.kt:29-38`), so `removeTimedRule` (`:213-230`) and the
unpin path in `syncSlotsToRules` (`:85-91`) take the DONE and SKIPPED rows
with the rule, contradicting `removeTimedRule`'s own documentation.

**Change.**
1. In ADAPT, treat a row as regenerable only when its id equals
   `occurrenceId(ruleId, day)` *and* its rule is enabled; everything else is
   kept, untouched.
2. Per decision 5, retire rather than delete: a rule with any non-planned
   occurrence gets `enabled = false` and loses only its future planned rows.
   Correct the documentation either way.
3. While here, `RoutineRepository.delete` gains a
   `PlannerRepository.onRoutineDeleted(id)` call in the same transaction, so
   deleting a routine stops leaving rules that mint sessions which then
   refuse to start with "Swap or unpin it in Plan" and nothing to unpin.

**Proof.** ADAPT with a disabled once-rule and a relocated id keeps both
rows; `removeTimedRule` keeps a DONE row; deleting a routine leaves no rule
behind. All three red today.

**Owns.** `domain/MissedWorkPolicy.kt`,
`data/repository/PlannerRepository.kt`,
`data/repository/RoutineRepository.kt`.

## C3 — Tonight is startable, and today knows the time · needs decision 2

**Symptom.** Session set for 18:00. At 18:01 Home says one session was not
done. Tap "Keep the dates" — the row loses its Start. You are in the gym and
the only offer is a free workout that will not tick the plan off. Choosing
Move instead pushes tonight's session to tomorrow. Separately, leave the app
open overnight and Plan still marks yesterday as today; its Add session
button opens yesterday and refuses to add.

**Cause.** `MissedWorkPolicy.kt:19-20` counts a today row overdue one minute
past its hour, and Keep marks it MISSED (`:50-54`), while
`DailyAgendaCard.canOpenStart` (`:339-344`) only starts a MISSED row that is
a leftover from an earlier day. The stale date is `remember { }` around the
clock: `PlanScreen.kt:270`, `HistoryScreen.kt:99`, and Home's unmemoised
read with no resume trigger (`HomeScreen.kt:173`).

**Change.**
1. Per decision 2, overdue becomes day-based: only rows before today count,
   so the prompt is a morning event.
2. Let `canOpenStart` accept a MISSED row dated today — `MoveToToday.decide`
   already resolves it to "already there".
3. Add one `TodayTicker` that re-reads on `ON_RESUME` and at the next local
   midnight, provide it through a composition local, and replace every
   `todayEpochDay()` / `LocalDate.now()` read in composition with it.

**Proof.** A policy test that a 19:00 row is not overdue at 19:01 but is
tomorrow morning; an agenda test that a today-MISSED row is startable; a
ticker test across a midnight boundary with a frozen clock.

**Owns.** `domain/MissedWorkPolicy.kt` *(after C2)*,
`ui/home/DailyAgendaCard.kt`, new `ui/units/TodayTicker.kt`,
`ui/home/HomeScreen.kt`, `ui/plan/PlanScreen.kt`, `ui/history/HistoryScreen.kt`.

## C4 — Planner and session writes are atomic

**Symptom.** Rare but total: a session that vanishes from the week, or a
finished workout deleted by a stale screen.

**Cause.** `moveOccurrenceForward` and `moveOccurrenceToDay`
(`PlannerRepository.kt:400-464`) mark the old row moved, call WorkManager,
then write the replacement — none of it in a transaction, and reachable from
a notification action. `discardSession` (`WorkoutRepository.kt:705-709`)
reads `finishedAt` and then deletes, so a live-bar Finish racing a stale
screen's Discard can still remove a finished session;
`ActivityRepository.discard` already does this check inside its transaction.
`addExerciseToSession`, `RoutineRepository.addExercise` and `moveExercise`,
and `ExerciseRepository.createCustom`/`updateCustom` have the same
check-then-act shape, so a double tap can duplicate.

**Change.** Wrap each in `withTransaction`; move the WorkManager calls after
the commit so a rollback cannot leave the scheduler diverged. Replace the
discard guard with a conditional statement —
`DELETE FROM workout_sessions WHERE id = :id AND finishedAt IS NULL` — and
the mirror for delete-finished.

**Proof.** An instrumented test that a scheduler failure mid-move leaves a
consistent week; a repository test that discarding a finished session is a
no-op.

**Owns.** `data/repository/PlannerRepository.kt` *(after C2)*,
`data/repository/WorkoutRepository.kt` *(after A1)*,
`data/repository/RoutineRepository.kt` *(after C2)*,
`data/repository/ExerciseRepository.kt`, `data/local/dao/WorkoutDao.kt`.

---

# Phase D — Paths that do not exist

Three packets. Nothing here is broken code; these are journeys the product
promises and cannot currently perform.

## D1 — The start sheet gets a home (or a grave) · needs decision 1

**Symptom.** There is no way to log a workout you did yesterday, and no way
to start a run that was not planned.

**Cause.** `StartOptionsSheet` has zero call sites in the app. The route to
the composer is reached only from Home with `"mixed"` for a planned mixed
occurrence (`HomeViewModel.kt:313`), and live cardio only from a planned
cardio row. The sheet, its 391-line view model and its tests are maintained
for a surface nobody can open — while ADR-006 T2, ADR-021 §2 and
UX_PAGE_PASS §4 all describe it as shipping.

**Change (restore path).** Host the sheet on Body, History and Plan as
UX_PAGE_PASS §4 states — never on Home, which owns the day's board. Guard
the cardio-discard branch (`StartOptionsViewModel.kt:290-294`), which is the
unprotected suspend call whose twin on the live bar was fixed in August.
Delete the sheet's private `estimatedMinutes` in favour of the identical
`HomeToday.estimatedSessionMinutes`.

**Change (delete path).** Remove the sheet, its view model and its tests;
add "Log a past activity" and "Start cardio" to Home's Add picker; amend
ADR-006, ADR-021 and UX_PAGE_PASS in the same commit.

**Proof.** Either way, an instrumented test that reaches the composer in
`past` mode and live cardio from a cold start with nothing planned.

**Owns.** `ui/workout/StartOptionsSheet.kt`,
`ui/workout/StartOptionsViewModel.kt`, `ui/navigation/AppNav.kt`,
plus whichever hosts are chosen.

## D2 — Reminder Start works from anywhere

**Symptom.** Tap **Start** on a workout reminder while the app happens to be
open on Settings: the notification vanishes, Temper records that you started
— and nothing begins. Whenever you next open Home, a workout silently starts
with no confirm, possibly hours later. Tapping the notification's *body*,
which should just show you the day, also starts the workout.

**Cause.** `openOccurrenceId` is handed only to the Home destination
(`AppNav.kt:408-409`) and consumed by a `LaunchedEffect` inside `HomeScreen`
(`:105-109`) that calls `startOccurrence` directly, while `MainActivity`
(`:68-88`) has already cancelled the notification and marked the delivery
started. The content intent (`ReminderNotifications.kt:119-129`) carries the
same occurrence extra as the Start action, so both do the same thing.

**Change.** Consume the occurrence at the navigation root: switch to the
Home tab first, then hand it on. Give the content intent a distinct extra
that selects the day and opens the ADR-018 confirm rather than starting.
Surface a message when the id no longer resolves, instead of returning
silently after the notification has already been dismissed.

**Proof.** A navigation test that a Start launch received while Settings is
foreground lands on Home and starts; a test that the body tap opens the
confirm and starts nothing.

**Owns.** `ui/navigation/AppNav.kt`, `MainActivity.kt`,
`reminder/ReminderNotifications.kt`, `ui/home/HomeViewModel.kt`.

## D3 — Live cardio is visible; errors dismiss; drafts survive · 2 evenings

**Symptom.** Three unrelated gaps with one shape — the app forgetting
something it knows. During a live run, Home still shows the filled "Start a
workout" button and tappable planned rows; tapping one gives a red "One live
activity at a time". Error banners on seven screens cannot be dismissed and
sit until some later action happens to succeed. And process death while
naming a new routine or building a custom week loses the name, the notes and
the whole week draft — the next add then mints a second "Untitled routine".

**Cause.** Home and Plan derive `sessionLive` from
`workoutRepository.observeInProgress()` alone (`HomeViewModel.kt:89`), while
the live bar and the start sheet also combine `activityRepository.observeLive()`.
The banners are `GymErrorBanner(message)` without the `onDismiss` the
component already supports. `RoutineEditorViewModel` (`:83-92, 284`) and
`CustomWeekViewModel` (`:62-75`) hold their in-flight authoring in plain
state flows, and `editingSetId` is the one workout-draft field not mirrored
(`ActiveWorkoutViewModel.kt:180`).

**Change.** Combine `observeLive()` into Home's and Plan's live state. Pass
`onDismiss` on all seven banners and add the missing `dismissError`
functions. Mirror the routine editor's id, name, notes and created-this-session
flag, the custom week's days and selected day, and `editingSetId` into saved
state. Add the missing in-flight guards on `logSet`, `addSet`,
`deleteSession` and `repeatSession` while in these files.

**Proof.** A Home test that a live cardio hides the Volt; a saved-state test
for each of the three authoring surfaces; a double-tap test on `logSet`
(which also closes the double-logging half of F1).

**Owns.** `ui/home/HomeViewModel.kt` *(after D2)*, `ui/plan/PlanViewModel.kt`
*(after C1)*, `ui/routines/RoutineEditorViewModel.kt`,
`ui/routines/CustomWeekViewModel.kt`, `ui/workout/ActiveWorkoutViewModel.kt`,
seven screen files.

---

# Phase E — Speed and battery

Four packets. Two of the three big rows undo a fix the August ledger records
as done, which is the argument for the regression tests each packet carries.

## E1 — Stop recomputing everything

**Symptom.** The phone runs warm during a workout and the battery goes
faster than the screen time explains.

**Cause.** Two leaks feeding one expensive pipeline. `observeLastLogged`
(`WorkoutDao.kt:388`) is an ungated `GROUP BY` across every set ever logged,
including the in-progress session; it sits inside the insights assembly
(`TrainingInsightsSource.kt:167-171`), so **every logged set** — warm-ups
included — re-runs the whole analytics pass: heat snapshot, coach basis,
recommendation engine, week derivation. And `PreferencesRepository` has 26
mapped flows and zero `distinctUntilChanged`, so **any** settings write
re-emits about thirty equal-but-new values; four of them are inside the same
assembly, which is why the Body window chip — a persisted preference — runs
the full pass instead of the cheap retarget the ledger describes, as do a
weigh-in, a rest-preset change, and the pending-occurrence write at every
workout start and finish.

**Change.**
1. Make the recency query finished-only (`JOIN workout_sessions … finishedAt
   IS NOT NULL`) and gate it on `observeFinishedWorkGeneration()` like its
   siblings. Keep the ungated variant solely for the picker's recency order,
   which legitimately wants the live session.
2. Add a `pref()` helper in `PreferencesRepository` that appends
   `distinctUntilChanged()`, and route all 26 flows through it.
3. Add `distinctUntilChanged()` on `Sources` before the compute step.

**Proof.** Two counting tests: logging a set triggers zero recomputes;
moving the Body window chip triggers one retarget and zero full computes.
Both red today.

**Owns.** `data/local/dao/WorkoutDao.kt` *(after A2, A6)*,
`data/repository/WorkoutRepository.kt` *(after C4)*,
`data/repository/PreferencesRepository.kt`,
`insights/TrainingInsightsSource.kt`.

## E2 — Thumbnails stop decoding at full size · 2 evenings

**Symptom.** The Library stutters when you scroll it, and the app is about
2 MB larger than it needs to be.

**Cause.** All 145 catalog stills are 768×768. `ExerciseThumb.kt:159` draws
them with `painterResource`, which decodes the full bitmap — 2.36 MB of
pixels — synchronously during composition, on the main thread, for a
40-point square, remembered only per composable instance behind a weak
framework cache. Scrolling the Library decodes about ninety of them.
`TemperMark` does the same for an 80-point mark in every empty state, and
`TemperStillCache` pins 4.7 MB of mutable bitmap for the app's life for a
panel that never draws above roughly 230×440.

**Change.** Ship a 256-pixel `ex_*` pack (enough for the 56-point header at
the highest density) and keep the 768 originals only where they are drawn
large; decode through one process-wide LRU on the IO dispatcher, with the
equipment badge as the placeholder; sample the body stills at 2. The Body
panel currently *upscales* its 768 source, so raise that one pair instead of
lowering it.

**Proof.** An APK-size assertion in the size checker; a test that the thumb
composable requests a sampled decode. The scroll itself is a phone check.

**Phone gate.** Scroll the Library end to end; no stutter, no blank frames.

**Owns.** new `ui/components/ThumbCache.kt`,
`ui/components/ExerciseThumb.kt`, `ui/components/TemperMark.kt`,
`ui/components/PoseArtwork.kt`, `res/drawable-nodpi/**`, `tools/`.

## E3 — The shell stops recomposing every second

**Symptom.** Nothing visible — this is battery and heat while a session is
live, on every screen except the workout itself.

**Cause.** `PersonalTrainerNav` reads the live-bar state in its own body
(`AppNav.kt:269-280`); the one-second elapsed label produces a new value
every second, invalidating the navigation root and the scaffold's bottom
slot. Because `goToTab` is a local function captured by un-memoised lambdas,
the `NavHost` builder is very likely rebuilt with it — nineteen destinations
re-parsed once a second. Alongside it, `RestTimerStatePersistence`
(`:55-63`) calls `commit()` — a synchronous disk flush — on the main thread
at every rest start, adjust and stop, which means on the frame of every Log
tap and every Finish.

**Change.** Move the bar into a `LiveSessionBarHost` that collects its own
state; expose a `hasLiveSession` boolean flow for visibility and insets;
memoise `goToTab`. In the timer controller, make persistence and alarm
arming one ordered IO job — publish the in-memory snapshot immediately, then
persist, then arm — which keeps the receiver's invariant (never arm before
the row is durable) without blocking the frame.

**Proof.** A recomposition-count test on the root while a session ticks; an
ordering test that the alarm is never armed before the row is written.

**Owns.** `ui/navigation/AppNav.kt` *(after D2)*,
`ui/navigation/LiveSessionBarViewModel.kt`,
`timer/RestTimerController.kt` *(after B4)*,
`timer/RestTimerStatePersistence.kt` *(after B3)*.

## E4 — Query and recompute hygiene · 2 evenings

**Symptom.** Cumulative sluggishness: every app resume rewrites the week,
opening a Plan day runs the Plan pipeline twice, a month arrow in History
re-walks your entire history, and a weigh-in re-reviews every finished
block.

**Cause and change**, in one packet because they are all the same mistake:
- `ensureWeek` upserts every row including the unchanged ones
  (`PlannerRepository.kt:249`), so Room's triggers fire and Home, Plan and
  the start sheet all recompute on every foreground → upsert only what was
  generated, and skip the reminder pass when nothing was.
- `PlanDayScreen.kt:68` builds a second `PlanViewModel` → a thin
  `PlanDayViewModel`, or scope to the tab entry.
- `HistoryViewModel.kt:127-189` puts the visible month and horizon chip in
  the same combine as the projections, records and grouping → a base state
  plus a cheap derived layer; key block reviews on the block, unit and last
  weigh-in.
- `historyBefore` (`WorkoutRepository.kt:858-865`) issues one lifetime query
  per lift when the batched query already exists; the progression path runs
  about ten small queries per lift switch, half of them repeats → one
  `lastFinishedWork` read shared by hint, RPE window and last performance.
- The four `DateFormat` instances built per recomposition, the unmemoised
  Home and Plan day derivations, and the eleven eagerly composed Settings
  sections → `remember`, move into the view model, `LazyColumn`.

**Proof.** A test that a resume with no schedule change produces no
occurrence writes; a query-count test on the summary screen.

**Owns.** `data/repository/PlannerRepository.kt` *(after C4)*,
`ui/plan/PlanDayScreen.kt`, `ui/history/HistoryViewModel.kt`,
`data/repository/WorkoutRepository.kt` *(after E1)*,
`ui/settings/SettingsScreen.kt`, `ui/home/HomeScreen.kt` *(after C3)*.

---

# Phase F — Design, first pass

Six packets, all inside the signed rules. These are the ones a person feels
within a minute of using the app.

## F1 — The logging loop keeps the wells on screen

**Symptom.** Log a set and the weight and reps entry scrolls off the top, so
you scroll back for the next one — every set, with chalk on your hands. Two
fast taps on Log record two sets. And the "last time" chips showing last
session's sets are not tappable, so matching last week costs about three
stepper taps per set.

**Cause.** After a log, `setsRequester.bringIntoView()`
(`ActiveWorkoutScreen.kt:828-837`) scrolls the *logged sets* panel into
view — and that panel sits below the entry wells, so the wells leave the
screen. `logSet` (`ActiveWorkoutViewModel.kt:769`) validates then launches
with no in-flight flag and `PrimaryGymButton` has no debounce.
`LastTimeStrip` (`:1097-1113`) renders its chips as plain boxes.

**Change.** Bring the entry panel into view instead. Add the `logging` flag
and drive `enabled` on the log button from it (shared with D3). Make each
last-time chip clickable with `Role.Button` and a tick haptic, setting the
draft weight and reps from that set.

**Proof.** A Compose test that the entry panel is displayed after a log; a
double-tap test asserting one set. Phone gate: log five sets without
scrolling once.

**Owns.** `ui/workout/ActiveWorkoutScreen.kt`,
`ui/workout/ActiveWorkoutViewModel.kt` *(after D3)*.

## F2 — One green button per screen · needs decision 4

**Symptom.** Four screens show two or three filled green buttons at once —
including an empty Plan, which is the first thing a new user sees, and
Home's fallback card, which can show three.

**Cause.** `ThisWeekCard`'s no-plan branch renders a recovery Volt *and* a
filled "Start a workout", and ignores the `quietStart` flag that exists for
exactly this. `PlanScreen.kt:405-413` shows the Add session Volt beside a
non-compact `EmptyState`, whose action is a filled button
(`Common.kt:158-165`). `CustomWeekScreen`'s day strip paints a filled Volt
dot on every filled day under a Volt confirm, where `WeekStrip` solved the
same problem with check marks. Settings has none at all, against ADR-014 §4.

**Change.** Secondary Start whenever a recovery Volt is present, and honour
`quietStart` in that branch; `compact = true` on Plan's empty state; check
marks in the custom-week strip; per decision 4, Export becomes the Settings
Volt (or the ADR and the accessibility matrix are amended in the same
commit).

**Proof.** A test per screen counting filled buttons in each state — the
kind of assertion the matrix already implies but nothing enforces.

**Owns.** `ui/home/ThisWeekCard.kt`, `ui/plan/PlanScreen.kt`,
`ui/routines/CustomWeekScreen.kt`, `ui/settings/SettingsScreen.kt`,
`domain/AccessibilityMatrix.kt`.

## F3 — Text you can read in a gym

**Symptom.** The faint grey under every number — "LBS", "REPS", "MIN",
"SETS" at 11 point — plus every Settings caption, the heat-map legend, the
chart axes and the picker's "Selected" state. Under gym lighting, "500"
without a readable unit is a number without a meaning.

**Cause.** `TextTertiary` (#5F6B73) measures 3.23:1 on the card surface,
2.97:1 on sheets and 2.65:1 pressed — below the 4.5:1 needed for normal text
and below even the 3:1 large-text floor on two surfaces — and it is used at
93 sites, many load-bearing. `Color.kt:73` already says it is for
decorative and disabled use only; nothing enforces that.

**Change.** Retune to #7F8B93 (4.8:1 on sheets, 5.1:1 on cards) and add a
separate `TextDisabled` token so "quiet" and "disabled" stop sharing one
value; route `MetricCluster` labels and the instruction captions to it.
Fix the two disabled states that read as enabled: the confirm button that
keeps its green ink, and the disabled primary that is indistinguishable
from a secondary.

**Proof.** A JVM contrast test asserting every text-on-surface pair used in
the app clears 4.5:1, computed from the token values — a permanent guard,
not a one-off fix.

**Owns.** `ui/theme/Color.kt`, `ui/components/GymSurfaces.kt`,
`ui/components/Common.kt`, `ui/settings/SettingsScreen.kt`, ~15 caption sites.

## F4 — Big text does not break the screen · 2 evenings

**Symptom.** At the largest system font, hero numbers are chopped to "1,2…",
the live session bar loses the workout's name, and the Body window chips run
off the edge.

**Cause.** `StatTile` has no font-scale awareness, so a 36-point numeral
becomes 72 in a fixed-width tile. The live bar's trailing cluster (elapsed,
rest clock, sets, menu) eats roughly 290 of 360 points at that scale,
leaving 40 for the title. `BodyWindowPicker` uses a non-wrapping row where
History's equivalent uses a flow row.

**Change.** Extend `LogLoopScale` with a `tileNumeral(fontScale)` step and
apply it in `StatTile`; stack the tile rows on Home, Summary and Activity
detail at 1.6 and above; hide the bar's rest clock and sets cluster at that
scale (the rest clock is on the lock screen anyway); flow rows for the Body
and onboarding pickers; add a 1.6 preview profile, which is the threshold
the code already uses and the previews do not cover.

**Proof.** Instrumented page passes at font 2.0 asserting the hero numerals
and the bar title are fully displayed.

**Owns.** `ui/theme/LogLoopScale.kt`, `ui/components/GymSurfaces.kt`,
`ui/navigation/LiveSessionBar.kt` *(after E3)*, `ui/progress/ProgressScreen.kt`,
`ui/home/HomeScreen.kt` *(after E4)*, `ui/summary/WorkoutSummaryScreen.kt`,
`ui/activity/ActivityDetailScreen.kt`, `ui/onboarding/OnboardingScreen.kt`.

## F5 — One word per thing

**Symptom.** The Library route says "exercise" six times where the rest of
the app says "lift". Returning to a live session is called four different
things — "Back to the workout", "Go to session", "Go to that session",
"Back to the bar". Discarding has three phrasings. And a caption on the
cardio receipt reads "Cardio. No invented lift rows.", which is a note about
how the app is built, on a screen about your run.

**Cause.** Copy added at different times, with `LiveBarCopy`,
`SessionOrderCopy` and `ActivityDetailCopy` never reconciled.

**Change.** Rename the six Library strings to "lift"; make "Go to session"
the single resume verb everywhere ("Back to the bar" stays — it names a
different act, returning from the rest page); take every discard label from
`LiveBarCopy.discard(kind)`; rewrite the two cardio captions in the user's
language. Update the accessibility matrix line in the same commit.

**Proof.** A copy test asserting no user-facing string outside the catalog
contains "exercise", and that the resume label has one source.

**Owns.** `ui/library/**`, `domain/LiveBarCopy.kt`,
`domain/ActivityDetailCopy.kt`, `ui/workout/StartOptionsSheet.kt`
*(after D1)*, `domain/AccessibilityMatrix.kt` *(after F2)*.

## F6 — Today is not buried by the missed-work card

**Symptom.** On the morning after a missed day, the card appears and pushes
the day's plan and its Start roughly 900 points down — off the bottom of any
phone — on both Home and Plan. That is the one morning you most need to see
today.

**Cause.** `MissedWorkCard` renders four 56-point buttons stacked
(`:29-59`) and is inserted above the day's surface.

**Change.** "Keep the dates" stays the visible Volt; Move, Adapt and Skip
collapse behind a quiet "Other choices" disclosure — still named, still
secondary, about 180 points shorter. Put Home's Add row inside the Today
list after a hairline rather than in a second box, which is what ADR-021 §3
describes.

**Proof.** A layout test at 360×640 asserting the day's Start is within the
first viewport with the prompt showing.

**Owns.** `ui/plan/MissedWorkCard.kt`, `ui/home/DailyAgendaCard.kt`
*(after C3)*.

---

# Phase G — Design, second pass

Six packets. Structure and accessibility. G1 is what stands between Temper
and the Public Candidate gate.

## G1 — A screen reader can use Temper · 3 evenings

**Symptom.** With TalkBack on: there are no headings anywhere, so there is
no way to jump between sections of a long screen; the end of a rest is never
announced; a personal record is never announced; bodyweight cannot be
entered at all, because the wheel is a pager with no semantics and no typed
alternative; list rows read as plain text rather than buttons; and switch
rows respond only on the switch itself, not the row.

**Cause.** Zero `heading()`, zero `liveRegion`, zero `stateDescription` in
the whole UI tree. `Role` is set at six sites; twenty clickables have none,
including `InstrumentRow` (`GymSurfaces.kt:329`), which is the app's most
common tappable.

**Change.** `heading()` on tab titles, the Home masthead, sheet titles and
section kickers; `liveRegion = Polite` on the rest kicker at the finished
transition and on the record banner; `Role.Button` in `InstrumentRow`,
`SecondaryGymButton`, `RestControl`, `StepperButton` and `ExerciseRow`;
`selected` with the right role on week cells and onboarding choices;
`toggleable` on switch rows; and a typed fallback on the bodyweight wheel —
tapping the numeral opens the number dialog that already exists.

**Proof.** Semantics tests for Home and the active workout asserting
headings exist and rows expose a role; a test that the rest kicker is a live
region.

**Phone gate.** The physical TalkBack pass the matrix has been waiting for.

**Owns.** `ui/components/GymSurfaces.kt` *(after F3)*,
`ui/components/Common.kt` *(after F3)*, `ui/components/GymStatus.kt`,
`ui/onboarding/**`, `ui/components/WeekStrip.kt`,
`ui/settings/SettingsScreen.kt` *(after F2)*, `ui/reminders/**`.

## G2 — One numeric-entry grammar

**Symptom.** Typing a decimal weight on a European keyboard gives you 1025
in the routine editor and 0 in the composer. No field advances to the next
with the keyboard's Next key. The custom-rest dialog offers a full QWERTY
keyboard for "1:30".

**Cause.** Three parsers disagree about the comma: `NumericEntry.parseDecimal`
accepts it, `WeightConverter.parseDisplayToKg` rejects it, and
`SessionLiftStrip.decimalDigits` strips it. `imeAction` is set in exactly two
places out of seventeen.

**Change.** Route every typed number through `NumericEntry`; add
Next → Done chains with focus requesters in the routine editor, composer,
cardio and password dialogs; a number keyboard for custom rest; give the
numeric fields the numeral type style they currently lack.

**Proof.** A parser test with a comma decimal for all three paths; a
Compose test that Next moves focus from sets to reps to rest.

**Owns.** `ui/routines/SessionLiftStrip.kt`, `domain/WeightFormat.kt`
*(after A1)*, `ui/activity/ActivityComposerScreen.kt`,
`ui/activity/LiveCardioScreen.kt`, `ui/components/Common.kt` *(after G1)*,
`ui/settings/SettingsScreen.kt` *(after G1)*.

## G3 — Shared headers and docks · 2 evenings

**Symptom.** Nothing, yet — this is the packet that stops the next drift.
Nine screens hand-roll the same back header and six hand-roll the same
pinned bottom dock, and they have already diverged (one dock has a hairline
above it, another does not).

**Change.** One `ScreenHeader(title, onBack?, trailing?)` and one
`PinnedDock(volt, secondary?, tertiary?)`, then migrate all fifteen sites.
The dock becomes the single place the landscape fix and the Library's
floating button change later land.

**Proof.** The existing page-pass instrumented tests must stay green
unchanged — this is a refactor, and a test that needed editing would mean it
was not.

**Owns.** `ui/components/` (two new files), fifteen screen files.

## G4 — Skin the four foreign controls · 2 evenings

**Symptom.** Four places where a stock Material control shows through: three
filled green switch tracks on Settings, a snackbar drawn on the only light
surface in the app, five dropdown menus rendered on the window colour with an
invisible shadow and no edge, and sixteen text fields whose unfocused border
sits at 1.75:1 — below the 3:1 that a control boundary needs.

**Cause.** `Theme.kt` maps `surfaceContainer` to the window colour with a
comment explaining it is for the navigation bar — which is hand-rolled and
never uses it — while the actual consumer is `DropdownMenu`. The switch,
snackbar and field colours are Material defaults resolving through the
scheme.

**Change.** An `InstrumentSwitch`; snackbars routed through the existing
status banner; menus on the sheet surface with a hairline; a field border at
or above 3:1. Correct the stale scheme mapping and its comment.

**Proof.** This is the runtime evidence ADR-005 §6 requires before skinning
anything, and the audit's computed ratios are it — record them in the packet
and add the token gallery goldens from H3 so a scheme change cannot pass
unseen again.

**Owns.** `ui/theme/Theme.kt`, `ui/components/` (new switch),
`ui/settings/SettingsScreen.kt` *(after G2)*, `ui/reminders/**` *(after G1)*,
three snackbar hosts, five menu sites.

## G5 — Body's first viewport; small targets; destructive confirms

**Symptom.** The Body tab's figure is fixed at 440 points, so the legend sits
on the fold and every muscle row and recommendation is below it — and on a
smaller phone the figure itself is cut. Calendar day cells are 40 points, under
the 48 the rest of the app holds. Removing a session from a Plan day is one
grey tap with no confirm and no undo, while the same act elsewhere is red and
confirms.

**Change.** Derive the panel height from the screen (45%, clamped 300–440);
put the legend above the figure. Raise the calendar cell to 48. Give Plan
day's Remove the danger ink and a confirm — or an undo snackbar, which is
better on that screen. Give the rest floor's idle "Start rest" the Volt it
should have (today the *finished* state has one and the only act does not).

**Proof.** A layout test that the first muscle row is within the first
viewport at 360×640; a touch-target test on the calendar.

**Owns.** `ui/progress/BodyMap.kt`, `ui/progress/ProgressScreen.kt`
*(after F4)*, `ui/history/TrainingCalendarCard.kt`,
`ui/plan/PlanDayScreen.kt` *(after E4)*, `ui/workout/RestTimerScreen.kt`
*(after B4)*.

## G6 — Reduced motion, and the palette question

**Symptom.** With animations turned off system-wide, the summary still
counts up, staggers its records and scales in; the status banner still
animates; and eight list-placement animations still run.

**Cause.** Reduced motion is honoured in six of thirteen animating files.
ADR-005 §5 says durations collapse to zero.

**Change.** Gate the remaining seven; move the loose dwell and repeat
constants into `Motion` and delete or use its five unused members. Then
answer the palette question the audit raised, in an ADR line rather than in
code: the accent and the record gold collapse to one colour for a
red-green-blind user, the warning amber and the record gold are nearly
identical for everyone, and the top heat stop reads as the danger red.
Either accept all three with a mandatory non-colour channel at every site,
or shift the warning toward orange.

**Owns.** `ui/summary/WorkoutSummaryScreen.kt` *(after F4)*,
`ui/components/GymStatus.kt` *(after G1)*, `ui/theme/Motion.kt`,
`ui/theme/Color.kt` *(after F3)*, eight list sites.

---

# Phase H — Design, third pass

Three packets. Product surface rather than repair; each is a proper packet
with a phone gate.

## H1 — History shows that you got stronger · 2 evenings

**Symptom.** Four weeks in, History shows counts and calendar dots. The only
place the app says "you got stronger" is three taps deep on a single lift,
or twelve weeks away in a block review.

**Change.** Move the horizon chips into the scrolling list so the header
stops eating 306 points; collapse the calendar to the current week with a
Month disclosure; add a personal-records count and a "moved most" line to the
horizon readout by reusing the block review's mover logic over the selected
range; give Home's "Last session" tile a signed delta against the previous
session of the same routine. None of this adds a Start, a tab or a route.

**Owns.** `ui/history/HistoryScreen.kt` *(after C3)*,
`ui/history/HistoryViewModel.kt` *(after E4)*, `domain/BlockReview.kt`
*(after A6)*, `ui/home/HomeScreen.kt` *(after F4)*.

## H2 — Units and clocks finish what Display started · 2 evenings

**Symptom.** The Settings "Regular / Military" chips change four quiet-hours
labels and nothing else. Seven different date formats coexist, and the
activity composer prints US-style dates for everyone. A pounds user is asked
for distance in kilometres.

**Cause.** `LocalClockFormat` is provided at the navigation root and read by
nobody.

**Change.** One `DateCopy` object replacing the seven formats and the
hard-coded locale, wired to the clock preference — or, if the preference is
not worth keeping, relabel the chips "Quiet-hours clock" and say so. Derive a
distance unit from the weight unit.

**Owns.** `ui/units/**`, `ui/history/**` *(after H1)*,
`ui/settings/SettingsScreen.kt` *(after G4)*, `ui/activity/**` *(after G2)*,
`domain/CardioCopy.kt`.

## H3 — Row and card vocabulary; landscape; a regression net · 3 evenings

**Change**, three related things:
1. **Consolidate the twelve near-duplicates** the audit lists — one lift-card
   header, one routine row, one set readout, a public danger button, a count
   badge, a numeral atom, one dialog grammar for the nine dialogs, one sheet
   chrome for the eleven sheets. Give `InstrumentRow` a role and a click
   label. Reduce the 27-parameter workout lift card and the two 17-parameter
   Home cards to state-and-events pairs, which the exercise picker already
   demonstrates.
2. **Landscape**: chrome alone exceeds a landscape phone's height on the
   workout and rest screens, so the log is invisible. Collapse the header,
   hide the idle rest row, fold the micro-recommendation into the card, scale
   the ring.
3. **A regression net**: a component gallery through the golden capture, the
   three theme galleries as goldens, a measure test for tabular numeral
   widths, and six gym-floor page goldens — replacing the current catalogue
   that codifies all 108 page goldens as missing.

**Owns.** `ui/components/**`, `ui/workout/**`, `ui/home/**`,
`app/src/debug/**`, `androidTest/testutil/**`.

---

# Phase J — Housekeeping

Five packets. None of this is visible on the phone; all of it is what keeps
the previous phases from silently regressing.

## J1 — The release build is real · needs decision 6

**Symptom.** Nothing you can see — which is the problem. The signed build's
code shrinking, its keep rules, its Gson round-trip and its crash bundle have
never executed anywhere. The build you install daily is the debug one, which
writes your workout titles and internal file paths into the phone's log.

**Cause.** `appVersionCode` is still 1 and the only signed release predates
the shrinking being switched on (`P12-field-operations.md`). Log redaction
is tied to the build type (`PersonalTrainerApp.kt:80`), so it is off exactly
where you use it. And `release.yml` (`:174-213`) never archives the symbol
map, so a crash report from a shrunken build cannot be read back — the
diagnostics feature keeps only frames whose class name starts with the
package, which shrinking renames away.

**Change.** Redact by default in both build types, with a debug-only toggle
in the Settings block that already exists. Upload the mapping file as a
release asset and keep package names so the diagnostics filter still
matches. Then install one `assembleRelease` build on a phone and walk
export → protected export → import → restore → share diagnostics, and write
the result into the P12 evidence file.

**Owns.** `PersonalTrainerApp.kt`, `app/proguard-rules.pro`,
`.github/workflows/release.yml`, `ui/settings/SettingsScreen.kt` *(after H2)*.

## J2 — The release ratchet and CI pinning

**Symptom.** The safety check meant to stop you shipping a release the phone
will ignore cannot pass as documented, and will stop catching anything after
the first real release.

**Cause.** `release.yml:84-99` requires the version code to be strictly
above a floor file and, in the same breath, tells you to set that file to
the new code in the same commit — those cannot both hold. The local checker
uses a different comparison, so a green preflight does not predict a green
tag. Separately, all six GitHub Actions are pinned to moving major tags,
including in the job that materialises the signing key.

**Change.** Derive the floor from the previous `v*` tag's version code in
git and delete the floor file as a source of truth; make the local checker
agree. Pin every action to a commit hash. Add the static gate and lint to
the drop and release workflows, which currently run unit tests only.

**Owns.** `.github/workflows/**`, `tools/check-version-code.py`,
`tools/released-version-code.txt`, `SETUP.md`.

## J3 — App size

**Change.** English-only resource filtering; the Play-only dependency block
out of the APK; packaging excludes for the debug-probe and metadata files;
release lint switched on. With E2's smaller stills this is roughly 2.5 MB off
a 7–9 MB app. Bring Robolectric's Android jar — the largest artifact the
test suite executes, currently fetched outside the checksum ledger — under
the ledger with offline resolution, and declare the coroutines Android
artifact explicitly instead of inheriting it.

**Owns.** `app/build.gradle.kts`, `gradle/verification-metadata.xml`,
`app/src/test/resources/robolectric.properties`.

**Partly done.** The checksum-ledger gaps that were failing every CI run
were closed on 2 September 2026, ahead of this packet. (See *Floor
findings*, 2026-09-02.) Everything else in J3 is untouched.

## J4 — Tests stop sleeping · 2 evenings

**Symptom.** The suite is slower than it needs to be and will flake on a
loaded machine: ten test files poll with a real 10-millisecond sleep in a
loop, three encode timing assumptions in 50, 80 and 200-millisecond sleeps,
and two read the wall clock, so a week boundary can fail them.

**Change.** `runTest` with a standard test dispatcher and value-based waits
instead of polling; deferred hand-offs instead of sleeps; the injected clock
instead of `LocalDate.now()`. Move the sixteen "policy" tests that read
source text from disk into the checkers, where that assertion belongs — the
JVM suite then measures behaviour only.

**Owns.** `app/src/test/**`, `tools/`, and the dispatcher seams in `app/src/main`
(`AppDependencies.ioDispatcher` / `computeDispatcher`, `AppContainer`,
`BackupRepository`, and the ViewModels that `flowOn` or `withContext` off the
test scheduler). Calendar `TimePort` and moving the sixteen source-reading
policy tests into the checkers remain owed.

## J5 — The checkers report what they skip

**Symptom.** A green gate that is quieter than it looks.

**Cause.** `check-state-members.py` silently skips nine of twenty-seven
screens, including everything on Settings, because it requires an explicitly
typed state declaration. `check-required-args.py` skips mixed-argument calls
without saying so. `check-design-tokens.py` covers four of about twelve token
families — it cannot see a dp literal, an alpha literal, a named colour, a
per-corner shape or a raw animation duration. `syntax-check.sh` prints "no
syntax errors" when the compiler fails to start at all, and preflight's jar
bootstrap picks whichever compiler jars sort last, then deletes a hand-built
jar directory whose annotations jar came from the wrong path.

**Change.** Print skipped counts and fail when they grow past a committed
baseline; add the missing token regexes (listed in the audit); scan the debug
source set and the XML resources too; capture the real exit status in the
syntax check; select jars by the versions in the version catalogue and test
jar *contents* rather than paths.

**Owns.** `tools/**`.

---

# Phase K — Held

Two packets that need a signature of their own, not just a decision.

## K1 — One signed v5: session time zone and the index census · 2 evenings

Legacy strength sessions carry no time zone, so which civil day a workout
belongs to is re-derived from wherever the phone is when you look
(`SessionSummary.kt:39`) — a Sunday-night session in Los Angeles reads as
Monday in Tokyo, moves your logged days and lands on a different calendar
day from a cardio logged the same evening. Activities already carry the full
four-field stamp; the strength tables predate it.

This is the only row in the audit that needs a database column, so it should
travel with the index work rather than alone: four indexes the hot queries
actually want, and six declared indexes no query uses. At one-user volumes
none of the indexes is urgent, which is exactly why they should ride along
with a migration that has to happen anyway.

Gate: both migration lanes green, and the schema JSON committed from a real
build — Room derives a hash the compiler must agree with, so this cannot be
hand-written.

## K2 — The compiler train · 4+ evenings

Kotlin 2.0.21 is the end of its line and is already compiling against a
newer standard library pulled in by coroutines. It works today by the
one-version-ahead rule; the day any dependency arrives built by Kotlin 2.2
the build stops. The train is Kotlin, KSP, the Android plugin, Compose and
Room together, as one signed matrix with its own evidence file — not a
Dependabot pull request. Until it is signed, add a tripwire to the SDK
checker that fails if a 2.2 standard library ever enters the ledger, and add
ignore rules so Dependabot stops proposing the versions the toolchain
reviews already refused.

---

# Definition of done for the program

The program is complete when all of the following hold:

1. Every packet above is merged to `trunk`, or struck with a written reason
   under *Floor findings*.
2. `./gradlew testDebugUnitTest assembleDebug lintDebug` is green, and
   `tools/preflight.sh` reports zero findings with no skipped checkers.
3. The migration suites pass in both lanes (K1 only).
4. The owner has seen every phone gate named above on Temper Debug.
5. A physical TalkBack pass has signed the accessibility matrix (G1), and
   `AccessibilityMatrix.publicCandidateReady()` returns true.
6. One signed release build has been installed and walked through export and
   restore (J1).
7. The audit file's ranked list has no unresolved P1 or P2 row that is not
   either fixed here or struck with a reason.

## Floor findings

*Every deviation from this plan gets a dated line here, with the old line
struck and the reason given.*

**2026-09-02 — J4, second half: the Owns line now includes the production
dispatcher seams.** The first-half entry below said J4 could not finish
inside `app/src/test/**` and `tools/`. This packet amends **Owns** rather
than drifting: `AppDependencies` now carries `ioDispatcher` and
`computeDispatcher` (production `Dispatchers.IO` / `Dispatchers.Default`);
`BackupRepository` and the five `flowOn(Dispatchers.Default)` ViewModels
plus `SettingsViewModel`'s file hops and `WorkoutSummaryViewModel`'s
summary build use those; `FakeAppDependencies` takes a `scheduler` that
also drives DataStore, IO hops, and compute hops. Room's query and
transaction executors stay real thread pools: `UnconfinedTestDispatcher.dispatch`
throws unless the caller is `yield`, so it cannot be an `Executor`, and a
`StandardTestDispatcher` executor queues work that `runBlocking` never
pumps. Tests wait on Room with `first { }` on the Flow. The transaction
pool is a separate single thread — putting it on the test dispatcher
deadlocks `withTransaction`, and setting only the query executor would
have assigned both to the same pool.

The remaining `delay(10)` poll helpers are gone. Three bounded waits stay,
each naming the boundary that forces them: `TrainingInsightsSourceTest`
(share grace + `assertSame` pass because the polls never advance virtual
time), `RestorePrepareTest.startWaitsForTheMaintenanceLock` (a negative —
start has not completed — with no waiter seam on `DbMaintenance`), and
`WorkoutRepositoryInsightsQueriesTest.loggingAnInProgressSetDoesNotRescanFinishedSummaries`
(an absence of a further emission). Calendar `TimePort` on
`ProgressViewModel` / `HomeViewModel` and moving the sixteen source-reading
policy tests into `tools/` stay owed. Count stays 1650.

**2026-09-02 — a fourth way to wait on the wrong thing, found by trunk.**
J4's first half passed twice on the exact tree that merged — a push run
and a pull-request run on `5f27e8c` — and then turned trunk red on the
merge commit: 1650 tests, 2 failed. Both are races those runs did not
happen to fire, and neither is new — both are the same disease, at sites
the packet did not reach.

`CustomWeekViewModelTest`'s missing-lift test read `uiState.value`
synchronously after three writes and asserted the picker was still open.
That is a class the entry below does not name: **a synchronous `.value` read
of a shared `stateIn` flow whose upstream crosses a real thread.** `uiState`
shares through `stateIn` at `:114`, and its `:91` branch folds in
`resultsFlow` (`:77`), which collects `exerciseRepository.search` and
`observeLastLogged` — Room flows answering on Room's own query executor —
so the sharing coroutine resumes off the test thread and the cached value
can be an emission behind. This is not the partial-combine latch below: a
`combine` never regresses a field, and the read simply happened before the
emission existed. Now waits for the error the assertions describe.

`RoutineEditorViewModelTest.confirmPendingAddSkipsLiftsAlreadyOnTheRoutine`
failed at the same line as before, for two reasons both still open. Its
catalog barrier was `isNotEmpty()` where the test selects two lifts — the
very site class tightened to `size >= 2` in two other tests in this file,
missed here. And the barrier added the day before waits only for the routine
to carry one exercise, while `:460` returns immediately whenever
`confirmInFlight` is set and the `finally` at `:516` clears it only after
the write returns; Room can emit the saved routine first, so the second
confirm was a no-op against a view model still refusing confirms. Both are
closed, and the wait is now a conjunction including `!addingLifts`.

The lesson is in how it was proved. Two green runs on this tree preceded
the merge that went red. A green run does not retire a race, it records
that the race did not fire — and nothing here is deterministic until the
`app/src/main` seams the entry below names actually exist.

**2026-09-02 — J4, delivered as half a packet, and a correction.** J4's
stated change is `runTest` with a standard test dispatcher and value-based
waits. It cannot be done inside its own **Owns** line. Four real-thread
boundaries separate a test from the work it waits on — Room's query
executor, DataStore's `Dispatchers.IO` scope, `flowOn(Dispatchers.Default)`
at five production sites, and `withContext(Dispatchers.IO)` at seventeen —
and the last two are in `app/src/main/`, which J4 does not own. The same is
true of "the injected clock instead of `LocalDate.now()`":
`ProgressViewModel` and `HomeViewModel` read the clock directly and take no
`TimePort`, so the calendar half needs a production seam too. This slice
takes the flakiness and leaves the rest owed: full determinism, and moving
the sixteen source-reading policy tests into the checkers.

What the work actually found is that polling was never the disease. Three
distinct defects hide behind it, and each one is a wait on the wrong thing.
**Wrong object:** the test waits on a repository while the code under test
reads the view model's own flow, or the reverse —
`ActiveWorkoutViewModelTest`'s swap-and-remove test could hang outright this
way, and `RestTimerViewModelTest` was losing the race today, starting a rest
on the 90-second default instead of the fixture's 75. **Partial-combine
latch:** `first { one field }` on a state assembled by `combine` can catch
an emission where a sibling field has not landed, which is how
`CustomWeekViewModelTest` failed asserting a picker was closed when it was
still open. **Insufficient barrier:** waiting for `catalog.isNotEmpty()`
before an action that needs two lifts, where `LiftCart.planConfirm` blocks
and writes nothing if one is missing. Polling masked all three by re-reading
until things happened to line up.

The correction. The 2 September entry for
`confirmPendingAddSkipsLiftsAlreadyOnTheRoutine` says a stale dedup let
squat be added twice and the routine land on three. `RoutineRepository`
dedups at `:66`, so that cannot happen, and the narrative is wrong. The
likelier mechanism is the catalog barrier described above. The fix committed
for it is correct and passing; the reason given for it was not.

One structural finding worth keeping. The poll loops were not only waiting —
each iteration called `advanceUntilIdle()` or `runCurrent()`, so they were
also pumping the test scheduler. Delete one and a view model coroutine
parked on a `Dispatchers.IO` preferences read never resumes, so the write
never happens and no wait can succeed. `FakeAppDependencies` now takes a
`prefsDispatcher`, defaulting to `Dispatchers.IO` so nothing changes unless
a test opts in; passing the test's own dispatcher puts DataStore on the
scheduler and lets `advanceUntilIdle` drive a whole read-compute-write
chain, after which no wait is needed at all. `TrainingInsightsSourceTest`
must never be converted this way: its share grace runs on the test scheduler
and its `assertSame` tests pass precisely because the polls never advance
virtual time.

**2026-09-02 — outside the packets, the instrumented lane did not compile.**
With the blocking job finally green, the non-blocking emulator job ran far
enough to fail, and it failed at `compileDebugAndroidTestKotlin`:
`HomePassInstrumentedTest.kt` uses `OccurrenceStatus.PLANNED` at five sites
with no import. `3a2a46f` replaced the import rather than adding one —
`-import ...OccurrenceStatus` / `+import ...PlanDayCopy`, alphabetically
adjacent. The emulator itself booted in 31 s of a 600 s budget and nothing
ran. Import restored.

The gap is the finding, not the typo. `ci.yml`'s blocking job runs
`testDebugUnitTest`, `lintDebug` and `assembleDebug`, none of which compile
`src/androidTest`, and `tools/preflight.sh` never mentions it. So the lane
`docs/DEVELOPMENT.md` calls the truth check — the migration lane, the one
every Room change is supposed to be gated on — was uncompilable for a day
with nothing anywhere able to say so. A blocking `assembleDebugAndroidTest`
step now compiles those sources without a device. That is
`.github/workflows/ci.yml`, packet J2's file, so it is recorded here with
the rest.

`debugLiveCode` goes 17 → 18 in the same change, so the drop carrying the
backup-restore crash fix is one Obtainium refresh away. `appVersionCode`,
`appVersionName` and `tools/released-version-code.txt` stay at 1 / 1.0.0 /
1: that file is the gym-floor ratchet and writing a live-test number into it
fails `check-version-code.py`, as `be38e55` already records.

**2026-09-02 — outside the packets, a crash lint had never been run to
find.** `lintDebug` ran for the first time in this repository and found four
errors. The first: `DriveRestClient.kt:143`, *"Call requires API level 33
(current min is 26): java.io.InputStream#readNBytes"*. `minSdk` is 26, so on
Android 8 through 12L that call does not exist and the import dies with
`NoSuchMethodError`. Both bounded reads used it — `DriveRestClient` for a
Drive restore and `SettingsViewModel.requestFileRestore` for a picked file —
so restoring a backup crashed on most of the supported range.

It came from `96bceff`, on trunk: the read was bounded deliberately, so a
planted multi-hundred-MB file could not OOM-kill the app, and the method
chosen to bound it was API 33. A hardening change that shipped a crash, and
nothing ran the checker that says so. Replaced with `readAtMost`, an
API-26-safe loop next to the budget it enforces, keeping `readNBytes`
semantics: read until the limit or the stream ends, never trusting one
`read` to fill the buffer. Seven tests pin it (`BoundedStreamReadTest`),
proved red-first — a single-read implementation fails
`assemblesAcrossShortReads` and `aStreamThatDribblesStillStopsAtTheLimit`,
which is the property the callers' budget-plus-one probe rests on.

Lint now prints its whole report rather than the first finding. It named one
error of four and pointed at a build intermediate no artifact carries, which
costs a full round trip per issue from an environment that cannot reach the
report host. Three of the four are still unseen; the next run names them.

**2026-09-02 — outside the packets, a trunk test the lane had never run.**
With the Robolectric lane working, 1643 tests ran and one failed:
`HomeViewModelTest.addDaySessionWeeklyKeepsTheRuleEnabled`, from `3a2a46f`
(#108), which had never executed. The product is not at fault and the fix is
in the test, so this is recorded rather than opened as a packet.
`publishPinnedWeek` (`PlannerRepository.kt:255-258`) is two writes and not
one transaction: `syncSlotsToRules` commits the rule outside any transaction
— waking `observeRules()` — and only then does `ensureWeek` write the week.
The test waited for an *enabled* rule, which is true inside that gap, then
read the occurrences that `ensureWeek` had not yet written. Its `once`
sibling passes on the identical path because its barrier is the rule being
*disabled*, which `mintTimed` does only after `publish` returns. The barrier
now waits on the occurrence itself.

The product was checked before the test was blamed. `OccurrenceGenerator`
(`:36-77`) has no notion of now — C1's own Cause says so — and places every
enabled rule at `weekStart.nextOrSame(rule.weekday)`, which for a rule
created today resolves to today under any week-start preference. Three
passing tests depend on it, `StartOccurrenceTest:46-55` on this very
pin-to-rule-to-generate path; none anywhere asserts the opposite. The real
seam is that `publishPinnedWeek` is not atomic, so a live Home collector can
also observe a rule whose week has not landed. Making it one transaction is
the better repair and is left for C1, which owns that file, rather than
taken here on a test-timing finding that cannot be exercised against a
device from this environment.

**2026-09-02 — J3, the Robolectric SDK pin.** With the checksum ledger
fixed the build reached its tests for the first time, and all 59 Robolectric
classes failed identically at sandbox creation:
`UnsupportedOperationException` from `DefaultSdkProvider:170`, which is
`verifySupportedSdk` throwing when an SDK is known but unsupported —
"Android SDK 36 requires Java 21 (have Java 17)". `robolectric.properties`
pinned `sdk=36` above the comment "Robolectric 4.16 ships Android SDK jars
through API 36 (Baklava)". That comment is true and beside the point:
4.16's own table maps Baklava to a required Java of 21
(`knownSdks.put(Baklava.SDK_INT, new DefaultSdk(36, "16", "13921718",
"REL", 21))`), while this project is Java 17 — `jvmTarget`,
`sourceCompatibility` and `targetCompatibility` all 17, and every workflow
pins JDK 17. The pin was unsatisfiable the day it was written.

It survived because nothing had ever run it. A guard
(`check-sdk-target.py`), a test (`SdkTargetTest`) and three documents all
asserted `sdk=36`, and all four of them were checking each
other rather than the lane: CI never reached the tests, and the owner is on
Windows, where `docs/DEVELOPMENT.md` records the JVM lane as unusable. Four
agreeing sources, zero executions. The pin is now `sdk=35`, the newest jar
4.16 supports on Java 17, and the guard, the test and the three documents
say why and say to raise it only together with the JDK. The cost is real
and accepted: the JVM lane emulates API 35 while the app targets 36, so
anything genuinely API-36-specific is covered only by the device lane,
which ADR-002 already names as the truth check. Bumping to JDK 21 was the
alternative; it was declined as a larger, unverifiable-from-here toolchain
change touching J2's workflow files.

**2026-09-02 — J3, the checksum ledger, opened out of order.** J3 owns
`gradle/verification-metadata.xml` and sits in Phase J, last. It is opened
here, before Phase A has merged, because that file is what stopped every
other packet from ever being compiled. `.github/workflows/ci.yml` is the
only thing reachable from the executor's environment that has an Android
SDK and a route to Google's Maven, and its own comment says the branch
build "is the only thing that ever compiles that code". Every run on this
branch and on `trunk` failed 23-45 s in, at `./gradlew testDebugUnitTest`,
on Gradle dependency verification — after checkout, JDK 17, the SDK,
Gradle and the full 17-checker static gate had all passed. The ledger was
missing `guava-parent` 33.4.8-jre, `kotlinx-coroutines-bom` 1.6.4, and the
`.module` files for `junit-bom` 5.9.2 and 5.10.2. `guava-parent` 33.2.1-jre
went in with them: its child is in the ledger and its parent was not, so it
fails the moment 33.4.8-jre stops being first. The gap set is closed rather
than open-ended — walking every `<parent>` and imported-BOM reference
across all 616 components found three absent coordinates and exactly two
components lacking a `.module` that exists upstream, and the three new
entries have no parent of their own and no upstream `.module`, so the
closure terminates. Each checksum was cross-checked against Maven
Central's own published `.sha1`. Nothing was loosened: `verify-metadata`
stays true, no `<trust>` rule was added, and
`tools/check-supply-chain.py` still reports zero findings.

This is not the CI-billing packet that ADR-002 §6 and `owner-loop.mdc`
forbid, and hosted runners are still not the gate. The same missing
checksums fail `./gradlew testDebugUnitTest assembleDebug` — the gate this
program does name — on any machine, the owner's included; CI is only where
it was finally visible. `docs/DEVELOPMENT.md:220` and
`docs/HANDOFF-2026-08-29.md` both claimed the account had no runner and that
every run died in seconds before checkout. Both were false, both are
corrected, and the false version is why nine runs were read as noise. J3
carries no **Proof** line, which by this plan's own rule at `:25-26` makes
it hoped rather than finished; a green run on this branch is the first proof
it has had. The rest of J3 — resource filtering, the Play-only block,
packaging excludes, release lint, Robolectric's Android jar — is untouched
and still owed.

**2026-09-01 — A4, where the failure message is decided.** The plan's proof
was a stage-failure test through `BackupRepository`, which cannot be reached
without either making `RestoreJournalStore` injectable or flipping a
directory's permissions between two statements inside one call — and
`BackupRepository` is Android-coupled, so such a test would be Robolectric
and unrunnable outside Gradle anyway.

The message policy is a rule, not a repository concern, so it moved to
`RestoreJournal.commitFailureMessage(phase, reported)` alongside the strings
it chooses between. `namedCommitFailure` is now a three-line wrapper that
also closes a STAGED journal — the half that unblocks workout starts without
a relaunch. The rule is pure, `RestoreJournal.kt` is already in the
domain-test lane, and the proof therefore *runs in this environment* rather
than being written and hoped over: reverting it to the old
pass-the-message-through behaviour fails with `expected:<Restore failed.
Nothing was changed.> but was:<A restore was interrupted. Temper is finishing
it from the copy already on this phone.>`, which is the symptom quoted back
word for word.

**2026-09-01 — A3, where the DONE write lives.** The plan said to route
`ActivityRepository`'s two DONE writes through
`PlannerRepository.markOccurrenceDone`. Two things are wrong with that. The
status write has to stay inside the activity transaction — a committed
session beside a still-PLANNED day is exactly the inconsistency this packet
exists to close — while `markOccurrenceDone` also cancels reminders, which
reaches WorkManager and the notification manager and must not run inside a
database transaction. And the dependency runs backwards: `activityRepository`
is built before `plannerRepository`.

So the write stays where it is, with the `"DONE"` literal replaced by
`OccurrenceStatus.DONE.name` — the actual fragility was the unchecked string,
not the location — and `ActivityRepository` gains an
`onOccurrenceCompleted: suspend (String) -> Unit` called after the commit.
`AppContainer` and `FakeAppDependencies` both wire it to a new
`PlannerRepository.cancelRemindersFor`, so the fake behaves like production.
It defaults to doing nothing, which keeps the two tests that construct this
repository with a bare database compiling.

**2026-09-01 — A6, the assisted rep rule.** The plan said "count a rep
record only at equal-or-less assistance", which reads as *replacing* the bar
— compare the candidate only against sets done at equal-or-more help. Coded
literally that awards a record to five reps at 30 kg of assistance from
someone who has done ten unassisted, because the only comparable set was a
three-rep one at the same help. So the bar stays the all-time rep count and
the assistance clause becomes a second condition on top of it.

Written first as "at least one earlier set was done at no less help", which
is too weak, and the counter-example is in the tests: eight reps at 10 kg of
help and three at 30, then nine reps at 20. That clears the weaker gate on
the strength of the 30 kg set, while the record it is actually beating was
set with half the help — the symptom, laundered by an unrelated easy day.
The condition is therefore that the standing rep record itself was set at no
*less* help than the candidate: `maxRepsAtEqualOrMoreAssistance == maxReps`.
Four tests pin the shape, two of them the cases the weaker readings got
wrong, and the SQL aggregate is a MAX of reps at equal-or-more assistance.

**2026-09-01 — A6, bodyweight lifts in the deload signal.** Not in the
packet, same cause, so it is fixed here rather than left for a packet that
does not exist. `DeloadSignal` scored every set with an Epley estimate from
`SetLog.weightKg`; on a bare bodyweight lift that column is zero, so the
estimate was null for every push-up and pull-up, `comparable` never reached
one, and the rule returned null before it could fire. The deload card was
silently switched off for anyone training without a bar. `strengthOf` now
answers per load class — kilograms for loaded and vest work, reps for
bodyweight, and least-help-then-most-reps for assisted — so the "at least
one comparable lift" guard means what it says for every lift.

**2026-09-01 — A5.** The plan said "drop CORE from `neglectedMuscles`". The
code disagreed, so the code won. `coreCoverageGap` and `neglectedMuscles`
were duplicates only because they fired on the same condition: the guard
read `if (days != null && days < NEGLECT_DAYS) return null`, so the coverage
card fired for core at or past the neglect threshold and for core with no
history at all — precisely the two cases the neglect card already names, in
its own better words, quoting the actual number of days. Dropping CORE from
the neglect list would have kept the duplication and lost the good sentence.
Inverting the guard to `if (days == null || days >= NEGLECT_DAYS) return
null` makes the two cards complementary instead: neglect covers everything
at or past the threshold, and coverage keeps the one case neglect cannot
see — core trained recently enough to escape the threshold with no *direct*
core work in the basis, which is what a plank logged with no reps, or a
warm-up, produces. Core stays in `neglectedMuscles`, which is where the
honest sentence lives. Three tests pin it
(`RecommendationEngineTest.coreCoverage*`); there were none before.
