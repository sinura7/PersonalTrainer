# Roadmap

> **Superseded as current law on 24 August 2026.** This file is the historical
> record of how the strength logger was built (audits, game plan, Jobs 1–6).
> The current program is [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).
> Signed decisions live in [architecture/](architecture/README.md).
>
> Historical “Room v3 won’t” and “no backdated session creation” are
> **superseded** by [ADR-010](architecture/ADR-010-schema-reset-migrations.md)
> and [ADR-007](architecture/ADR-007-activity-model.md).
> `fallbackToDestructiveMigration`, a sixth tab without a new ADR, and an
> LLM-as-author remain forbidden. Settings is a tab
> ([ADR-014](architecture/ADR-014-settings-tab.md)).
>
> Executors verify current decisions in `docs/architecture/`, not by grepping
> `Signed:` in this file.
>
> 10 Sep 2026 — H: the app offers, it does not retype. Choosing an RPE
> filled the entry wells from the recommendation it unlocks, so a load and
> a rep count the lifter had just typed were replaced by numbers they had
> not asked for — the app editing their entry in the act of being told
> about it. Asking for an extra set did the same. Both raise the
> recommendation exactly as before: it sits above Log with its own **Use**,
> and only that tap moves it into the wells (`applyIntentRecToDraft` is
> gone; `applyMicroRec` was always the consented path). Live test 35
> (`debugLiveCode` 35), drop `debug-live/2026-09-10-6`.
>
> 10 Sep 2026 — G: the entry wells belong to the next set. Logging one took
> a snapshot of weight and reps at the tap and wrote it back over the wells
> when Room returned, so a load nudged or a rep count typed in the tens of
> milliseconds the write takes was taken back by the log's own tail — the
> owner's "sometimes it resets one or the other". The tail now clears the
> two per-set flags (warm-up, RPE) on the draft as it stands; the row that
> was written keeps the tapped values. Typed reps stop being a delta
> measured against a well that may have moved: `setReps` takes the number.
> Live test 34 (`debugLiveCode` 34), drop `debug-live/2026-09-10-5`.
>
> 10 Sep 2026 — A tick with no preferences yet stays silent. The service
> seeded `tickPreferences` with the defaults — everything on — until
> DataStore's first emission, so a boundary that fell before that read
> landed ticked against the defaults rather than the owner's choice; the
> way to see it is a rest with seconds left when the process is killed,
> the sticky restart posting the next boundary while the container is
> cold. The field is null until the first emission, and a tick that finds
> it null makes no sound and still schedules the next one. Live test 35
> (`debugLiveCode` 35), drop `debug-live/2026-09-10-6`.
>
> 10 Sep 2026 — Day board follow-ups, from the same six-reviewer pass over
> #205: a tappable block reads as a button again (`Role.Button`, which
> `Card(onClick)` does not set and `InstrumentRow` did); Skip and Up / Down
> are drawn inside the block they act on, through a `controls` slot; an
> auxiliary pack's meta line is its own caption, as the confirm already
> shows, not a second estimate; quiet ink follows
> `DailyAgenda.canOpenStart`, so a session skipped on an earlier day reads
> settled; the order line may take two lines at 360 dp. Live test 33
> (`debugLiveCode` 33), drop `debug-live/2026-09-10-4`.
>
> 10 Sep 2026 — Tick follow-ups, from a six-reviewer pass over #206: the
> ticks re-anchor from every running snapshot the service collects, not
> only from the `ACTION_SYNC` that trails the disk write, so a -15 s that
> lands the countdown on five ticks five at once (`RestTick.nextTick`
> counts a boundary at exactly now, with a `ticked` guard against a
> runnable asking for itself). Preflight proves `rest_tick.wav` is byte-
> identical to its generator; the backup threat model names `REST_TICK`
> among the exclusions and a round-trip test shows a restore leaves the
> toggle alone; the Settings caption claims only what the code gives.
> Live test 32 (`debugLiveCode` 32), drop `debug-live/2026-09-10-3`.
>
> 10 Sep 2026 — Lane fixes: the hosted emulator pass is meant to be green.
> Four failures, one pull request each, none skipped or loosened: the
> exact-alarm test read `lastAlarmSchedule` before the IO-scope arm had
> run (it waits, bounded, now — #207); the History pass looked for
> `Records` where the kicker draws `RECORDS` (#208); the workout journey
> asserted the summary's lift breakdown without scrolling to it (#209);
> and the golden's 7,091-pixel diff, printed from the lane as base64
> (#210), so the baseline is re-recorded from the lane's own capture with
> the comparator still exact
> (`docs/foundation-program/evidence/golden-rerecord-2026-09-10.md`).
> The lane is now the reference renderer.
>
> 10 Sep 2026 — Correction to that entry, from the review pass: the
> golden's diff was **not** renderer anti-aliasing. 7,074 of the 7,091
> pixels are the two cards' `PRIMARY` / `SECONDARY` labels, which packet
> F3 (`6787b17`, 3 Sep) recoloured from `#5F6B73` to `#7F8B93` for
> contrast — 3,934 of them exactly that pair, the rest its blends — and
> the golden, committed 2 Sep, was never re-recorded for it. The lane had
> been comparing shipped ink against a stale screenshot for a week. Only
> the last 17 pixels, on the Volt button's corners, are the renderer.
> VISUAL_TESTING now says a colour-token change is a golden change.
>
> **Open, needs the owner:** those 17 pixels are not stable run to run.
> Two runs of the same commit (#212) differed by exactly them, each by one
> level in one channel, and the golden passed once and failed once. With
> an exact comparator the lane cannot reach the ten consecutive green runs
> the CI header sets as the bar for making the job blocking. The options
> are to keep the comparator exact and accept a coin-flip golden, or to
> treat a difference of at most one level per channel as equal under a
> tight cap on how many pixels may differ, documented as a rounding
> allowance rather than a tolerance. Nothing is loosened until it is
> decided.
>
> 10 Sep 2026 — Tick: the last five seconds of rest tick. `RestTick` says
> where the boundaries fall; `RestTimerService` posts one runnable per
> boundary and re-asks on every sync, so a ±15 s moves the ticks with the
> deadline and an old tick is never sounded against a new one (`isDue`).
> Each tick is a click (`res/raw/rest_tick.wav`, written by
> `tools/build-rest-tick.py`, kept loaded in a `SoundPool` on the alarm
> stream) and a 40 ms pulse, under the existing Sound and Vibration
> toggles and a new **Last five seconds** toggle. The toggle is
> device-local — not in the backup document, not restored — like the last
> preset. Reach is the countdown's: process alive, CPU awake; in doze the
> alarm path's completion cue is the whole alert, and the Settings caption
> says so. Closes R-04, T-02, T-05, T-17, N-02 and G-10. Live test 31
> (`debugLiveCode` 31), drop `debug-live/2026-09-10-2`.
>
> 10 Sep 2026 — F: Home's day board. Each of today's sessions is its own
> bordered block — the title, the first four catalog stills, the numbered
> order, `2 lifts · about 13 min` — with Start (leftover: Do it today) in
> Volt ink on the foot. The whole block is the tap, into the same ADR-021
> confirm, under the same test tag; Skip and reorder sit under it as
> before, and Add keeps its own row under Today. Still open and the
> empty-agenda leftover card draw the same head (`DayBlockHead`), so a
> session looks the same on every Home surface. Aux packs needed no new
> plumbing: `AuxiliaryBlocks` already mints them as routines, so their
> stills resolve like any routine's. Done and moved blocks go quiet in
> ink; the stills stay (ADR-022: identity, not state). The words are
> `DayBlockCopy`, pure and tested; the confirm's count line reads the same
> function. No ADR: the behaviour is ADR-021 as it stands, only the
> drawing changed. Live test 30 (`debugLiveCode` 30), drop
> `debug-live/2026-09-10`.
>
> 10 Sep 2026 — Lane: the emulator job says why it failed. Its script is
> `tools/ci-instrumented.sh`, which dumps the device log before the runner
> tears the emulator down (the runner executes each `script:` line as its
> own `sh -c`, so the fallback could not be inline), and a
> `Print instrumented failures` step prints every failure body from the
> JUnit XML — the semantics trees, the golden diff figures, the caught
> exception — into the job log, where they can be read from any network.
> Still non-blocking; the bar for the gate is unchanged. CI only.
>
> 9 Sep 2026 — R18 step two: exercise detail bests and history read
> `CompletedTrainingRepository.observeExerciseSets` (both stores). A
> backdated strength day counts as a PR on that lift, not only in
> Records. `#217`–`#219` are on `trunk` (`#217`/`#218` both live 35;
> `#219` is docs). Open `#220` bumps to 36 / `debug-live/2026-09-10-8`
> and owns the drop tools; overlap with this packet is ROADMAP only.
> This packet inherits 35 and does not bump it.
>
> 9 Sep 2026 — F: the multi-add picker writes as it goes. A tap in Add lifts
> puts the lift on the routine (or on the custom week's day) immediately and a
> second tap takes it back out; the numbers are the session's own order, and the
> footer button is **Done**, not Add. Closing the sheet — scrim, back, a stray
> tap — no longer empties a cart the owner built by hand. `LiftCart` keeps the
> in-flight taps (`picked`/`settle`), `planConfirm` and `ExercisePickerEvent.Confirmed`
> are gone. With it: a target typed into a card while that card's previous
> commit is still in Room is no longer dropped by the commit's tail
> (`stagedTargets` is a `ConcurrentHashMap`, removed by compare-and-remove) —
> the lost update that made `stagedTargetsCommitWhenTheyDifferAndRejectZeroSets`
> time out on a two-core runner. No schema change. Live test 29
> (`debugLiveCode` 29), drop `debug-live/2026-09-09-8`.
>
> 9 Sep 2026 — `#175` (Robolectric 4.16.1) and `#180` (AGP 8.9.3)
> closed unmerged. Robolectric stays pinned at `4.16`; 8.9.3 waits for
> aapt2 ledger + checker in one packet. `#173`, `#199` and `#200` are
> on `trunk`. `#201` is the live-29 drop. `#181` does not bump 28.
>
> 9 Sep 2026 — W3: `SettingsViewModelTest`'s four waits go through
> `awaitFirst` now that `#188` has landed; `unbounded_waits` 4 → 0. Every
> ViewModel wait in the suite has a ceiling and names what it last saw.
>
> 9 Sep 2026 — J: `DESIGN_AUDIT` re-read against the code. 42 of the 64
> rows still marked P1 were closed by shipped work (keyed stills on every
> picker, chip and header; the bundled rest cue on the alarm stream; the
> nine-tenths picker with the create row only on no match; equipment on
> the lift; staged targets and persist-on-exit; Room v4 with the catalog
> versioned; the overlay superseded) and now say so with file evidence;
> two are partly closed. What is genuinely open: the last-5-second tick
> (R-04, T-02, T-05, T-17, N-02, G-10), Body's first-launch emptiness
> (B-02), routine-card and recommendation pictures (S-02, B-03, I-01),
> editor target steppers and a load-type control (E-04, E-12), a cue
> preview in Settings (N-01), the battery-restriction copy (T-16), and
> the walkthrough rows (W-02, W-11, G-02, G-05, T-12, I-04) and the
> chip's set progress and rest badge (W-06). Docs only.
>
> 9 Sep 2026 — I: the hosted emulator lane boots the Nexus 5X profile the
> goldens were recorded on (411 dp at 420 dpi, the `temper-tests-api29`
> device); it had been booting a 320 px default. Still red, and now
> honestly so — five of eighty need an emulator in front of someone:
> `FoundationGoldenTest` (0.43% of pixels in `[84,664..858,1321]`, the
> figure region, SwiftShader vs the recording GPU), `ExactAlarmCapability`
> `apiBelow31SchedulesExact` (`FAILED` where API 29 must give `EXACT`),
> `ProductionScreensPass.historyAt360Font2` (`Records` unreachable), and
> both `ActiveWorkoutJourney` journeys (`Top set 202.5 kg × 5`, `Set 1`
> not displayed). The lane stays non-blocking until it is green ten runs
> in a row on `trunk`. No app code; no drop.
>
> 9 Sep 2026 — C: `tools/check-cancellation.py` fails preflight when a
> `catch (Exception)` that can see a suspension has no `CancellationException`
> clause ahead of it. 32 such sites (every ViewModel `launch`, the app's
> start-up imports, two receivers, Drive sign-out, the foundation reset)
> now rethrow cancellation; `cancellation_swallow` 32 → 0. No drop.
>
> 9 Sep 2026 — D: Body says what the figure was built from — a facts line
> under the map ("3 sessions this week · last finished yesterday"), **Show
> this month** in one tap when Day or Week is empty, and the Front / Back
> chips in their own strip under the figure (`DESIGN_AUDIT` B-05 closed).
> Live test 28 (`debugLiveCode` 28), drop `debug-live/2026-09-09-7`.
> `#181` does not bump it.
>
> 9 Sep 2026 — E: Golf cool-down pack (`golf-cooldown`, Mobility: couch
> stretch, incline pigeon, calf stretch, elephant walk, dead bug). After a
> round, where the Golf warm-up is before one. Live test 27
> (`debugLiveCode` 27), drop `debug-live/2026-09-09-6` — the `-5` cut died
> on the `#188` / `#190` compile break that `#196` fixed.
>
> 9 Sep 2026 — `#194` / `#188`: verified backup drop. A finished
> backup can be opened, a silent Drive account switch is refused, and
> the sealed password can be shown. `debugLiveCode` 27.
>
> 9 Sep 2026 — W2: every ViewModel test wait goes through
> `Flow.awaitFirst` (sharedTest `TestWaits.kt`): `withTimeout(FLOW_MS)`
> and, on giving up, the last value the flow showed. 203 sites in 16
> classes; `unbounded_waits` 207 → 4 (`SettingsViewModelTest`, owned by
> `#188`, follows). No app code; no drop.
>
> 9 Sep 2026 — W: `tools/check-unbounded-waits.py` fails preflight when a
> `*ViewModelTest.kt` waits on a ViewModel flow with no `withTimeout`
> around it — the shape that wedged CI twice on 9 Sep. Ratcheted at 207
> (`unbounded_waits`); `test_unbounded_waits.py` is its fixture proof.
> No app code; no drop.
>
> 9 Sep 2026 — `#190`: ErrorSlot in the eight remaining ViewModels
> (StartOptions, ExerciseLibrary, ActivityComposer, CustomWeek,
> Settings, Onboarding, LiveCardio, SessionDetail). History's three
> sites ride `#181`, which owns that file. No drop.
>
> 9 Sep 2026 — `#189`: the 31-minute CI hang was a ViewModel error race
> (every action wrote null into one shared error flow on success), not a
> deadlock. `util/ErrorSlot`: a success clears only its own family, and
> only refusals older than its own start. `tools/hang-watchdog.sh` wraps the
> CI unit-test step and thread-dumps a wedged worker from outside the JVM.
> B2 follows: the same slot in the eight remaining ViewModels
> (`HistoryViewModel` waits for `#181`). No app-visible change; no drop.
> `DESIGN_AUDIT` W-14/W-15 were already closed in code and are marked so.
>
> 8 Sep 2026 — R18 step one: History horizon and block reviews read
> `CompletedTraining` from both stores. A backdated strength day counts
> as a PR in the readout, not only in Records. `#184` (flow waits are
> `TestWaits.FLOW_MS`) is on `trunk`.
>
> 8 Sep 2026 — Dependabot `#174` (coroutines 1.11.0) and `#176`
> (android-all-instrumented 17) closed unmerged. `#178` named
> `kotlinx-coroutines-android` and the J3 API-35 jar on the ignore
> list. Live test 22 is still the phone APK; Obtainium is still 20.
>
> 5 Sep 2026 — Live test 21 (`debugLiveCode` 21) on `trunk`: C–J4
> plus lint publisher. Obtainium attach waits on a hosted runner;
> sideload Temper Debug until a `debug-live-*` pre-release exists.
> Gym-floor Temper unchanged.
>
> 3 Sep 2026 — H1: History horizon names PRs and the lift that moved
> most; calendar opens on this week; Home last session is a signed
> delta versus the previous visit of the same routine.
>
> 3 Sep 2026 — G6: reduced motion finishes the gate; Volt/PrGold/Warn
> collisions stay with a mandatory non-colour channel (ADR-023).
>
> 3 Sep 2026 — G5: Body figure 45% (300–440), legend above the
> silhouette, calendar cells 48 dp, Plan-day Remove confirms in
> danger ink, idle Start rest is Volt.
>
> 1 Sep 2026 — Obtainium live test 17 (`debugLiveCode` 17, tag
> `debug-live-2026-09-01-2`): keyed catalog stills on the phone
> (Library thumbs are the Brokenout WebPs). Pull Obtainium on
> Temper Debug. [ADR-022](architecture/ADR-022-keyed-catalog-stills.md).
>
> 1 Sep 2026 — Keyed catalog stills: one WebP per built-in lift,
> `imageKey` written at catalog v7. Family stills stay the fallback
> for customs. Body unlit/heat unchanged. Gym-station photo
> portfolios wait. [ADR-022](architecture/ADR-022-keyed-catalog-stills.md).
> Live test 17 is the Obtainium drop.
>
> 1 Sep 2026 — Obtainium live test 16 (`debugLiveCode` 16): Home row
> starts the planned session (confirm); filled Volt is Start a workout;
> Add under Today with just-today vs weekly; Skip on Still open; Plan
> routines collapsed; routine editor Save. Gym-station photo portfolios
> wait. [ADR-021](architecture/ADR-021-home-start-and-day-add.md).
>
> 31 Aug 2026 — Obtainium live test 15 (`debugLiveCode` 15, tag
> `debug-live-2026-08-31`): warm-up extras (golf, lower-body,
> upper-body, shoulder), Home Add extra once, untimed day board,
> Up/Down reorder. [ADR-020](architecture/ADR-020-warmup-extras.md).
>
> 31 Aug 2026 — Warm-up extras (golf, lower-body, upper-body, shoulder)
> plus the existing mobility packs. Plan Add session is weekly. Home
> **Add extra** is once for that day. Home and Plan hide session clocks;
> Up / Down rearranges the day's blocks. [ADR-020](architecture/ADR-020-warmup-extras.md).
> Live test 15 is the Obtainium drop.
>
> 30 Aug 2026 — Obtainium live test 14 (`debugLiveCode` 14, tag
> `debug-live-2026-08-30`): Task 2 queue (dead code, StartOccurrence,
> onboarding retry, insights perf, silent-defect tests, build fat,
> small UX) plus the audit/emulator-lane work from live test 13.
>
> 30 Aug 2026 — Small UX: bodyweight wheel unit toggle, cardio catalog
> banner, Move-to-today MOVED ids, previous-week Still open. Live test
> 14 is the Obtainium drop.
>
> 30 Aug 2026 — Debug APK fat: Gson toolchain pins, vendored outlined
> marks, no kotlinx.serialization or material-icons-extended. Live test
> 13 is still the Obtainium drop.
>
> 30 Aug 2026 — Silent-defect tests: mapper wire names, ExerciseRepository,
> reminder receivers/worker. Live test 13 is still the Obtainium drop.
>
> 30 Aug 2026 — Insights perf: one coach-hint query per pass, Body's
> window chip retargets the shared snapshot, PR/summaries stop
> rescanning history on every live set, rest seconds share one clock.
> Live test 13 is still the Obtainium drop (`debugLiveCode` 13).
>
> 29 Aug 2026 — Full-tree adversarial audit: eight independent passes,
> every finding source-verified. Worst: occurrences generated on the
> wrong weekday for non-Monday week starts; REPLACE+CASCADE wiping a
> rule’s history on an hour change; discard deleting finished
> sessions; the plan-row binding marking the wrong day DONE; the
> reminder Start button dead on Android 12+; restore recovery
> silently dropping preferences. All fixed on this branch, with the
> deferred queue and owner actions in
> [FD-full-audit.md](foundation-program/evidence/FD-full-audit.md).
> These fixes shipped as live test 13 (`debugLiveCode` 13, tag
> `debug-live-13`).
>
> 29 Aug 2026 — The empty-agenda leftover card’s Volt (Start this
> session) opens the same session-summary confirm as the agenda card
> before starting — it was the one Home start left that jumped straight
> into the log. No clock line: a slot day is untimed. Live test 13 is
> this drop (`debugLiveCode` 13)
> ([ADR-018](architecture/ADR-018-home-start-confirm.md), amended).
>
> 29 Aug 2026 — A leftover Home session (yesterday’s Friday, a missed
> block) moves onto today when you confirm **Do it today**. Today lists
> **Still open**. Recurrence does not change. Live test 12 is this drop
> (`debugLiveCode` 12)
> ([ADR-019](architecture/ADR-019-move-to-today.md)).
>
> 29 Aug 2026 — Home planned rows and the Volt open a start confirm
> with the session summary. Confirm starts it. The tag prefers a
> workout over Stretch. Live test 11 is this drop (`debugLiveCode` 11)
> ([ADR-018](architecture/ADR-018-home-start-confirm.md)).
>
> 29 Aug 2026 — Home is the day’s board: a selectable week bound to
> occurrences, not leftover Friday-on-Saturday titles. Completion is
> rest / none / some / all on planned blocks. Plan is fill-the-day;
> Add session is the Volt; Tune and header New are gone. Reminder
> prefs live on Settings; hours live on the day. Live test 10 is this
> drop (`debugLiveCode` 10)
> ([ADR-017](architecture/ADR-017-home-week-board.md)).
>
> 29 Aug 2026 — Settings opens on lbs/kg and Regular/Military hours.
> Schedule and coaching are the same store as the questionnaire.
> Weekly weigh-in follows the first training day. Equipment is
> grouped and filters generated weeks. Reminders and session hours
> live on Plan. Goals UI is gone. Home is Start, not a second tab
> bar ([ADR-016](architecture/ADR-016-settings-home-trim.md)).
>
> 29 Aug 2026 — Plan is a schedule workshop. A weekday is a pushed
> page of untimed blocks (workout, cardio, auxiliary). Start is Home.
> No Start Cardio, Swap, or Unpin. Add session is on Plan and the day
> page. Live test 8 is this drop ([ADR-015](architecture/ADR-015-plan-day-blocks.md)).
>
> 29 Aug 2026 — Settings is the fifth tab. The gear is gone from Home
> and Plan. Library and Goals stay pushed.
>
> 29 Aug 2026 — Body is a readout. Day / Week / Month chips wash the
> silhouette from logged sets, reps, and RPE. Empty still shows the
> figure. No Start on this tab. Start lives on Home.
>
> 28 Aug 2026 — A weekday can hold more than two sessions: morning
> cardio, the pinned workout, and a later accessory / Hyper Pro
> session. Plan’s day sheet adds the later row; Home lists the stack.
> One live activity at a time. Finish one, then start the next.
>
> 28 Aug 2026 — Prescribed sets done turns Log into Next. A + after the
> last logged set asks for an extra. Selected RPE retargets reps/weight
> from this session and last time. Rest countdown sits on the lock
> screen (chronometer, not overlay rest on the log).
>
> 29 Aug 2026 — Temper Debug Obtainium drops bump `debugLiveCode`.
> Live test 9 is Settings / Home trim (lbs/kg, Regular/Military,
> weekly weigh-in, Plan reminders, Goals UI gone).
> Same versionCode is why a check for updates can show nothing.
>
> 28 Aug 2026 — Phone lane is Obtainium, not Android Studio. Cursor
> lands on `trunk`. Temper Debug is a GitHub pre-release
> (`debug-live-*`, `PersonalTrainer-*-debug.apk`). Gym-floor Temper
> stays on the signed APK. Do not mix the two Obtainium entries.
>
> 28 Aug 2026 — Builder and live log stack lifts as full-width
> vertical cards. Tap expands in place. Rest floor wraps the remaining
> clock in a countdown ring. Log bar stays linear. Overlay rest and a
> 240 dp ring on the log stay won’ts.
>
> 28 Aug 2026 — Cross-tab layout fit. Instrument stays. Overflow,
> hit-target splits, Volt outside grouped windows, Why in a dialog
> so the log dock does not grow. Not a restyle, not DIRECTION_A.
>
> 28 Aug 2026 — In-set next load is a compact `Next:` line above Log.
> Use fills the draft only. Why is a local `RuleTrace`. The rest floor
> shows the same line with no Use. ProgressionStrip stays session-grain.
> Home `RecommendationEngine` is unchanged.
>
> 28 Aug 2026 — Live rest on the log is a condensed bar. Tap opens
> `session/{id}/rest` with a huge remaining clock. One RestTimerGateway.
> Skip is never Volt. Overlay rest and a 240 dp ring stay won’ts.
>
> 28 Aug 2026 — Hyper Pro is its own kit. Catalog v6 adds the official
> 28-movement laundry list as `EquipmentType.HYPER_PRO`. Questionnaire
> place mixes gym / Hyper Pro / home / bodyweight. Resilience goal
> opens with nordic, reverse hyper, and reverse nordic. Gym-only weeks
> do not assign Hyper Pro lifts.
>
> 27 Aug 2026 — First visit opens Home. Generate a schedule, build a
> week, or start a workout. The questionnaire is a pushed route, not a
> launch gate. Generated weeks size sets/reps from age, goal, and days
> (ACSM / Schoenfeld landmarks), emit a `RuleTrace`, and keep strength
> sessions on compounds.
>
> 27 Aug 2026 — Live testing is Temper Debug beside gym-floor Temper.
> Obtainium watches signed GitHub Releases. Cursor lands on `trunk`.
>
> 27 Aug 2026 — Body tab is the unlit ChatGPT still with a live heat
> wash. This week and Last 30 days stay the two windows. Rest stays
> the photograph. Overlay plates will not pixel-match every still
> seam. Library thumbs stay the posed stills.
>
> 27 Aug 2026 — Library thumbs are the locked 18-still pack (WebP),
> keyed by family. Not a second drawing of those stills. Not 101
> catalog keys. Customs stand on the unlit front/back still. Body
> live heat stays the standing map.
>
> 27 Aug 2026 — Library poses retarget to the locked 18-still bible
> (front/back unlit, front/back demo heat, 14 family poses). Same
> Temper plates on a skeleton. Not a PNG pack. Customs still stand.
>
> 26 Aug 2026 — Library thumbs are the Temper figure posed: the same
> polygonal plates and hairline seams as Body, Heat3 on the working
> plates, kit in the hands. Not a second pictogram, not a PNG pack.
> Customs with no family still stand.
>
> 26 Aug 2026 — Audit leftovers on the further-design vehicle: lighter-week
> and "moved most" captions are not Volt; Tune Done and a met goal are
> status, not the act; a noon-stamped receipt omits duration; rest
> controls have no emphasised fill. One-live confirm shares the start
> lock; History/Goals/insights read activity summaries; named-args
> ignores Compose `path(fill)`; bodyweight backup keeps the ADR-011
> four-tuple. Restore refuses live cardio as well as live strength.
> ADR-005 stays closed. Evidence:
> [AUDIT-hygiene.md](foundation-program/evidence/AUDIT-hygiene.md).
>
> 26 Aug 2026 — Further-design of gym-floor chrome: composer Save dock,
> kind-aware live bar, one Volt on workout/plan/summary, honest Why and
> History totals, reminder quiet hours, rest gold-finish, PR glow, set
> GroupedList, chart PR line, un-nested Home agenda, week-strip two-a-day
> mark. Not a restyle. ADR-005 / ADR-006 stay closed.
>
> 26 Aug 2026 — Studio sync may request `-sources.jar` / javadoc / the
> Gradle `-src.zip`. Those stay trusted artifacts. The checksum ledger
> stays on; do not disable verification to install debug. Host `aapt2`
> for linux / windows / osx is checksummed so a Windows Studio install
> is not a Linux-only ledger miss.
>
> 26 Aug 2026 — Activity composer Save is the one Volt. Type chips speak
> Run / Ride / Walk via CardioCopy, not `RUN`. Weight fields follow
> LocalWeightUnit. Add set / Add cardio are secondary controls. Remove is
> a named Danger control — the row itself does not delete. Live cardio
> Finish is the one Volt and sits above the system navigation inset, same
> job as strength Log. Leave running and Discard are stacked secondary
> controls (Danger ink on Discard), not footnotes; Discard confirms.
> System back opens a leave dialog whose Volt is Leave running. After
> finish, activity-summary Done is pinned with the same inset.
>
> 25 Aug 2026 — Home agenda speaks the session you built. Strength rows
> show the same 1 · 2 · 3 order as Plan and the editor. A two-a-day keeps
> one Volt Start (strength preferred); other planned rows stay tappable.
> The masthead never prints CARDIO DAY · N LIFTS. Editor empty copy says
> Add, matching the picker. Custom-week Add lifts is disabled while Confirm
> writes. Home does not host the start-options sheet — Body, History, and
> Plan still do. The sheet now starts today’s plan itself (same occurrence
> binding Home uses). Plan free opens that sheet. Agenda/Plan rows never
> dump `Planned`. Missed-work Keep the dates is the one Volt. Gym-floor
> errors say lift, not exercise. Active-workout Log and summary Done sit
> above the system navigation inset (three-button / gesture pill).
> Leave-workout Keep and exit is the filled Volt; Stay is a real control;
> Discard stays Danger ink and still confirms.

Derived from the full audit of 19 Aug 2026 ([AUDIT.md](AUDIT.md)). Phases land in order;
each one ends at a gate that must be verified before the next begins.

Status legend: **done** · **next** · *later* · **superseded**

---

## Phase 0 — Foundation · **done**

Config-cache-safe release build, Room schema export wired, adaptive launcher icon,
day/night window background, volume thousands separators, week-start consistency, leave
dialog anatomy.

> **Closed 20 Aug 2026.** `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/1.json`
> was generated by a real build and committed — six entities, `identityHash`
> `6d58ad40d5c03785ab29aaf61157f369`. Phase 4 is no longer gated. Never hand-edit it.

## Phase 1a — Rest timer trust · **done**

`AlarmManager.setAlarmClock` wakeup, persisted timer state with boot rebasing, single
idempotent completion path, silent done-channel so the sound preference is real,
notification-permission recovery banner, ceiling countdown, phantom-timer guard.

## Phase 1b — Data trust · **done**

Transactional snapshot excluding unfinished sessions, `BackupValidator` (rejects malformed,
foreign, dangling-reference and empty-destructive documents), SAF export/import with no
Google dependency, restore blocked during an active workout, pre-restore safety snapshot,
`lastBackup` vs `lastRestore` separation.

## Phase 1c — Coaching trust · **done**

Progression judged on the **top set** of the last finished session (not the last set
logged), planner recovery override no longer downgrades lower-body days, week start
verified as a single source of truth.

## Phase 2 — Lifecycle hygiene · **done**

Consume-once notification intent, explicit `SessionLoadState` so a missing session is
terminal rather than an infinite spinner, workout draft surviving process death via
`SavedStateHandle`, system-back parity with the top-bar exit.

Then the state-graph work the audit called for:

- **A5/A7** — one analytics pipeline (`TrainingInsightsCalculator` +
  `TrainingInsightsSource`) behind Home, Schedule and Progress, computed off the main
  thread. The three copies had drifted: only Progress passed the exercise catalog and
  honoured the week-start preference, so the same history produced a different body map on
  Progress than the weekly plan was built from.
- **A6** — navigation is one-shot state with an explicit ack, not a lambda captured into a
  coroutine that outlives its `NavController`.
- **A8** — session notes debounce to a typing pause and write in order. They were one
  unordered write per keystroke, so a shorter earlier string could land after a longer
  later one.
- **A9** — the routine editor's four load flags became one tested `RoutineEditorLoad`.
- **A3** — prefill left the `uiState` chain, which it was both an output of and an input
  to; actions read hot `StateFlow`s instead of a `WhileSubscribed` projection.

> **A1 closed 22 Aug 2026.** ViewModels take `AppDependencies` in the constructor.
> Production still resolves the graph from `AppContainer` via `@JvmOverloads` so the
> default `viewModel()` factory is unchanged. Tests construct a ViewModel with a fake
> graph instead of reaching through `Application`.

---

## Phase 3 — The Mirror · **done**

The app recorded years of sets and showed almost none of it back.

- **Exercise detail screen** — lifetime totals, standing records, weekly volume and
  estimated-1RM trends, every session the lift appears in. Reached from the library or from
  an exercise block in a past session.
- **Personal records** — weight, reps-at-weight and estimated 1RM, computed from history
  rather than stored. Announced in-workout as a dismissible banner, never a dialog.
- **Workout finish summary** — replaces the silent pop back to Home.
- **Previous-session values** — every working set of the last session for the current lift,
  above the steppers.
- **Training calendar** — the month at a glance, shaded by how hard each day was relative
  to that month's own hardest day.

Not built: **editable finished sessions**. Editing history means relaxing the guards that
protect finished sessions from writes, and that deserves its own change rather than being
folded in at the end of a feature phase.

Everything here is a read over the existing schema, so none of it waited on the v1 baseline.

## Phase 4 — Schema v2 · **done** (superseded; game-plan Phase 3 shipped it)

> Superseded 20 Aug 2026. The monolithic Phase 4 bundled the migration with behaviour
> changes this roadmap itself said deserved their own change (see the Phase 3 note above on
> editable sessions). It is split and re-ordered value-first across the game plan below: the
> migration core is game-plan Phase 3; the test substrate and session hygiene ship first
> (Phases 2 then 1); the catalog and imagery ship last (Phases 7/8). Execution rules:
> `docs/archive/gameplan/PROTOCOL.md`.

## Phase 5 — The physical product · **done, verified on device**

The visual redesign. Full critique and the reasoning behind every decision are in
`docs/UI_REDESIGN.md`; the design language itself is `docs/ui-redesign/DIRECTION_B_INSTRUMENT.md`.

- **One dark theme, mapped completely.** The half-mapped scheme let Material's baseline
  neutrals through into the nav bar, every dialog, every sheet and every default card —
  invisible in review, unmissable on screen. `surfaceTint` is now transparent, so the
  elevation overlay can never tint a surface with the accent again.
- **Two bundled faces with real tabular figures**, replacing the system monospace that was
  carrying the largest numerals in the product. `tools/build-fonts.py` instances and subsets
  them and asserts the tabular widths rather than trusting them.
- **Token layer** — colour, type, shape, spacing, motion and haptics — enforced by
  `tools/check-design-tokens.py` so it cannot fragment again.
- **The workout screen staged around resting and lifting**: the rest clock pinned outside
  the scroll, live session telemetry in the header, entry compacted to one panel, state
  drawn rather than narrated.
- **Haptics on the logging loop**, including press-and-hold repeat with a detent per step.
- **One intensity ramp**, colourblind-safe, shared by the body map, the calendar and Home.
- **Charts split by kind** — bars for additive volume, a focused-domain line for levels, so
  an estimated 1RM can finally show a few percent of progress.

Built, installed and used on a real phone on 20 Aug 2026 — the first time any of this code
was compiled, which had been the single largest caveat on it. Two defects surfaced that no
static check here had caught, both since fixed: a missing `Surface1` import in
`ExerciseDetailScreen`, and the routine editor discarding an unsaved rename on back.
`tools/check-missing-imports.py` was written to close the first class permanently.

Not done here: **exercise imagery** and the equipment field it needs, which belong with the
Phase 4 schema change; **rest-as-instrument sound design**; the **plate calculator**; and a
full **accessibility pass at font scale 2.0**.

*Amended 21 Aug:* the equipment field shipped in Phase 3 and the imagery in Phase 8 — composed
in Compose rather than commissioned, so the line-art budget was never spent. The other three
are still not done and are still unowned by any phase.

## Phase 6 — Platform *later*

Glance rest-timer widget, Health Connect, bodyweight log, CSV import from Strong/Hevy.
Recorded decisions **not** to build: overlay bubble, Wear OS app.

## The game plan — 20 Aug 2026

Phase numbers below are game-plan numbers, independent of the historical phases above.
Full packets live in `docs/archive/gameplan/`; the execution protocol is
`docs/archive/gameplan/PROTOCOL.md`. Phases land in order; each closes only on owner sign-off.

**Phase numbers are identifiers, not sequence** — the execution order is 0, 2, 1, 3, 4, 5,
6a, 6b, 7, 8 (ten PRs), and the table is in that order.

| Order | Phase | One line | Executor-days | Owner-days |
|---|---|---|---|---|
| 1st | **0 — Decisions & doctrine** | Docs only: ROADMAP/DESIGN_AUDIT restructure, IA adjudication, schedule-semantics sign-off, surface map, cut list, branch ground truth. **BLOCKING checkpoint.** | 0.5–1 | 0.5–1 |
| 2nd | **2 — Test substrate** | androidTest scaffold + deps, Robolectric JVM lane hosting MigrationTestHelper, smoke migration test vs `schemas/1.json`, `tools/preflight.sh`, connectedAndroidTest runbook. | 1–2 | 0.5 |
| 3rd | **1 — Session hygiene (packets 1A + 1B)** | One branch `claude/phase-1-session-hygiene`, one PR, one owner evening. **1A:** FinishWorkout/DiscardWorkout use cases, LiveSessionBar, zero-set discard-only policy, 4-hour stale nudge (in-app), RestRemainingStrip deleted, one-live-affordance gate. **1B (on top of a green 1A gate):** editable finished sessions (completedAt preserved), guarded session delete, repeat-last-session, delete-set undo. | 5–7 | 1–1.5 |
| 4th | **3 — Schema v2 migration** | ONE additive migration (equipment/loadType/movementKey/imageKey/nameKey, exercise_muscles, seed_meta, schedule_slots from D2), versioned seeding behind the maintenance mutex, batch-1 catalog (the 37, keyed on the Phase 7 family vocabulary from the start) + review artifact, Backup v2 + round trip, pre-open raw DB copy, rehearsal runbook, junction-first heat. | 3–5 | 1–2 |
| 5th | **4 — Plan tab & the pinned week** | Reconciliation rules as pure Kotlin first (from D2), ScheduleRepository owns the persisted week (sixth insights source), planner demoted to proposing fills, `insights.weekPlan` rewired, Plan tab built inside whichever tab bar D1 settles on, pushed ScheduleScreen deleted, ThisWeekHomeCard extracted, planner fixes. | 4–6 | 0.5–1 |
| 6th | **5 — Honest heat & coach** | Absolute weekly-set bands, windows → THIS_WEEK + LAST_30_DAYS, coach on trailing 14 days, RPE, imbalance by weighted sets, per surface map. | 4–5 | 0.5–1 |
| 7th | **6a — Tab consolidation** ✅ 21 Aug | Ran **Branch A** per signed D1: four tabs (Home · Body · Plan · History), Library demoted to a pushed route with a back arrow, `isTabRoute` and the `restoreState = false` hacks deleted. The history work landed in History in place — month grouping with pinned headers, a sheet for multi-session days, a Records section. Body was not restructured. | 4–5 | 1–1.5 |
| 8th | **6b — Home "Today" rework** ✅ 21 Aug | Masthead states today rather than the app's name (`MastheadCopy`, pure and tested). The week strip is now one composable shared with Plan, not a Home-only copy. ONE next-session module: the balance card, the heat tile and the recent-activity list are gone. The StartWorkout interstitial is deleted — `StartOptionsSheet` is a modal every entry point opens, so the live bar stays the only live-session affordance. | 2–3 | 0.5–1 |
| 9th | **7 — Catalog to ~98 + Library UX** ✅ 21 Aug | Catalog at **98** built-ins across two seed bumps (v3, v4), every per-bucket count matching plan. Library groups into 39 movement families with equipment chips; the muscle filter reads junction credits, so it finally includes secondary-credit lifts, and the route carries a canonical enum rather than display text. Collisions surface as rename-or-keep-both — no merge tool, no FK rewriting. One increment table replaced three disagreeing ones: the "+5.5 lbs" defect is dead and bodyweight lifts are told to add a rep. Per-loadType add defaults replaced the universal 3×5/90s. Swap-equipment in both the routine editor and the live session. Bodyweight leftover is closed: Settings log + `SetWork` split (reps vs added kg), not a 40 kg fiction. | 4–5 | staged review |
| 10th | **8 — Imagery** ✅ 21 Aug *(was optional; run anyway)* | Compose-drawn thumbnails: a body figure with the trained muscles lit from a **fixed** Heat3 (identity, not the owner's live band) plus an equipment badge, on picker rows, library rows, the 56dp detail header and — glyph only — the in-workout chips. The anatomy moved to `ui/components/FigureArt.kt` as a verified pure move; all 233 coordinates are character-identical, so the Body tab is untouched. Zero assets, zero `res/` additions. **Outstanding, owner-side: the release-APK measurement and the glyph verdicts.** | 2–4 | 0.5–1 |

| 11th | **9 — The guided setup** ✅ 21 Aug *(not in the original plan)* | Came out of a UX audit of the finished ten phases, which found every screen assumed a lifter who already had routines and a pinned week — and a fresh install had neither. Six questions, one per screen, then a preview of the real week with the real lifts, then one button. The split is derived, never asked. Nothing is written until "Use this plan", and re-running it from Settings deletes nothing. Also closed four defects on the cold-start path, including a **compile break that had been on the branch for three phases**. | 2–3 | 0.5 |

**A1 (the DI seam) landed 22 Aug 2026** as the opportunistic refactor this note always
was — not a phase. ViewModels are constructor-injected with `AppDependencies`.
`WorkoutRepository` and `ScheduleRepository` now have `androidTest` coverage on real
SQLite. ViewModel and screen instrumented tests are still unscheduled.

**UX page pass (22 Aug 2026).** The screens already exist. The work was making each
one tell the truth and offer one act. Living plan: [UX_PAGE_PASS.md](UX_PAGE_PASS.md).

**Job 2 · code-done on `trunk` (22 Aug 2026).** Daily logging is mature. No
workout in mind → short path → the app builds the week. P0–P4 are on `trunk`
(shared-structure figure, emphasis, Athletic, preview copy, later fills /
Suggest honours emphasis). Phone gates remain the owner's.
[JOB2_ACTION_PLAN.md](JOB2_ACTION_PLAN.md). P5 sex waits for a real sentence.
P6 catalog is won't — families already exist.

**Job 3 · code-done on `trunk` (22 Aug 2026).** Replay stored answers
without creating routines; lighter-week marker (HOLD, not scaled sets);
Home / Plan / setup ViewModel JVM tests; debug is
`com.sinura.personaltrainer.debug`. Phone gates remain the owner's
(unpin+replay, Tune+HOLD, two icons). Packets, gates, won'ts:
[JOB3_ACTION_PLAN.md](JOB3_ACTION_PLAN.md).

**Job 4 · code-done on `trunk` (22 Aug 2026).** Deload card marks this
week; Home replay when routines exist; Settings / History / Progress
JVM contracts; Backup caption and Finish helper tell the truth before
the tap. Phone gates remain the owner's (card+HOLD, Home replay,
Backup/Finish copy). Packets, gates, won'ts:
[JOB4_ACTION_PLAN.md](JOB4_ACTION_PLAN.md).

**Job 5 · code-done on `trunk`.** Rest cue, plates, type-in, pounds
default, font-scale 2.0, prompted backup, and `ci.yml` listing
`trunk` are on `trunk`. Phone gates remain the owner's. GitHub
runners are not a test lane. Room v3, fifth tab, LLM, rename, sex,
and catalog seed stay signed won't. Packets, gates, won'ts:
[JOB5_ACTION_PLAN.md](JOB5_ACTION_PLAN.md).

**Job 6 (superseded as current program, 24 Aug 2026).** Regroup for the
four-tab logger. Leftover P0–P8 are on `trunk`. Phone-week floor finding
(RPE explainer and sticky rest dock) landed in `2484396`. Job 6 is no
longer the current program. [JOB6_REGROUP.md](JOB6_REGROUP.md) is
historical leftover paper. Current work:
[FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md).

**Review baseline (22 Aug 2026).** Full-tree audit after Job 2 landed on `trunk`.
Closed 22–23 Aug. Do not reopen as live work. Q4 onboarding ANR on the
cloud emulator is environment. Debug is `.debug` (Job 3 / P4).

| Item | Severity | Packet |
|---|---|---|
| ~~Snapshot retry can copy v2 into the v1 rollback folder~~ | ~~P0~~ | merged [#17](https://github.com/sinura7/PersonalTrainer/pull/17) |
| ~~Snapshot treats any dest directory as success~~ | ~~P1~~ | same |
| ~~Rest timer `apply()` can lose disk state before the alarm~~ | ~~P1~~ | merged [#18](https://github.com/sinura7/PersonalTrainer/pull/18) |
| ~~`preflight.sh` links Robolectric annotations / junit~~ | ~~P1~~ | merged [#19](https://github.com/sinura7/PersonalTrainer/pull/19) |
| ~~CI `on.push` still lists `main`, not `trunk`~~ | ~~P1~~ | done 23 Aug — `db787f5`. Hosted runners are not a test lane. |
| ~~Suggest stays up on a fully pinned week~~ | ~~P2~~ | merged [#20](https://github.com/sinura7/PersonalTrainer/pull/20) |
| ~~Planner still listens for deleted `recovery-upper`~~ | ~~P2~~ | merged [#21](https://github.com/sinura7/PersonalTrainer/pull/21) |
| ~~Setup preview has no catalog-empty error~~ | ~~P3~~ | merged [#22](https://github.com/sinura7/PersonalTrainer/pull/22) |
| ~~`DEVELOPMENT.md` still says `2.json` is uncommitted~~ | ~~P2~~ | merged [#23](https://github.com/sinura7/PersonalTrainer/pull/23) |

---

## Known open items

Carried forward deliberately, with the phase that will address them.
**Phase numbers refer to the game plan.**

| Item | Phase |
|---|---|
| Instrumented tests cover migrations plus `WorkoutRepository` / `ScheduleRepository`; remaining ViewModels and screens still untested (Home / Plan / setup / Settings / History / Progress have JVM tests) | later — opportunistic |
| ~~ViewModels untestable by construction (service-locator `AppViewModel`) — A1~~ | ~~opportunistic~~ done 22 Aug 2026 — constructor-injected `AppDependencies` |
| Finished sessions cannot be edited | ~~1~~ fixed 21 Aug — sets, notes, session delete, repeat |
| Routine editor loses an unsaved rename on back | ~~4~~ fixed 20 Aug |
| Imbalance advice compares tonnage, not working-set counts | ~~5~~ fixed 21 Aug — weighted weekly sets |
| ~~Progression increment is a global 2.5 kg; LBS users see "+5.5 lbs"~~ | ~~7~~ done 21 Aug — `IncrementTable.STEP_LBS` is 5 lbs; storage is `STEP_LBS_IN_KG` |
| RPE is stored and backed up but read by nothing | ~~5~~ fixed 21 Aug — two top sets at RPE 9+ hold the load |
| Planner assigns focus to days already in the past | ~~4~~ fixed 21 Aug — proposals only for open days ≥ today |
| `arrangeKinds` can still produce back-to-back same-family days | ~~4~~ fixed 21 Aug — guarded rotation replaces the swap |
| ~~Toolchain ~20 months stale; release unminified~~ | ~~later (platform)~~ · **done** — Phase 4 took AGP 8.9.2 / Kotlin 2.0.21 (`gradle/libs.versions.toml`) and release is minified (`app/build.gradle.kts`) |
| Exercise imagery and the equipment field it needs | ~~3 (field)~~ / ~~7 (catalog)~~ / ~~8 (imagery)~~ — all done 21 Aug |
| ~~Rest-timer sound design; plate calculator; font-scale-2.0 pass~~ | ~~Job 5 / P2–P4~~ done 22 Aug — cue, plates, type-in, 2.0 layout |
| ~~No scheduled auto-backup (manual + prompted only)~~ | ~~Job 5 / P5~~ done 22 Aug — 14-day caption nag, not WorkManager |

---

## Decisions

**These D1–D6 decisions built the strength logger.** They remain historically
true. Current binding decisions are the ADRs. D7 records the supersession.

A historical decision is marked **Signed** below. Executors starting a
*foundation-program* packet verify [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md)
and `docs/architecture/`, not this grep.

### D7 — Foundation program supersession

**Chosen:** [FOUNDATION_PROGRAM.md](FOUNDATION_PROGRAM.md) and
[docs/architecture/](architecture/README.md) are current law. Room v3
won’t is superseded for the one authorized Phase 5 cutover. Backdated
activity creation is required. Drive is backup, not sync. One live
activity; many completed and scheduled activities per day. FND-037 is
not a finding.

**Signed: foundation program P0.1, 24 August 2026.**

### D1 — Information architecture

The app ships **four tabs** (Home · Body · Plan · History). Library is a
pushed route. DESIGN_AUDIT NAV-01 called five a lot; that sentence is
stale — Routines folded into Plan and Library left the bar. UI_REDESIGN
§6 recorded a three-tab target that Phase 5 deferred. This decision
closes the contradiction. **Circle one option and sign.**

**What both options give you, whichever you circle.** Library stops being a tab and
lives on as a pushed screen — entered from Plan, from recommendation cards, and from the
muscle detail sheet; every pushed route survives. Routines folds into the new **Plan**
tab, so the week and the routines that fill it are one place instead of a tab plus an
orphaned screen in Settings. The **LiveSessionBar** (a docked strip whenever a session is
live — elapsed, sets, rest countdown, tap to resume) lands as chrome, not a tab, and
becomes the **only** live-session affordance anywhere. And the history work lands either
way: sessions grouped by month, a sheet when a day holds more than one session, and a
personal-records row.

**Option A (recommended): four tabs — Home · Body · Plan · History.** History keeps its
tab. The calendar and the session log stay exactly one tap away, Body stays
silhouette-first, and Phase 6a is the tab-bar rewrite plus the nav retargets — Body does
not absorb History. What it costs you: a fourth tab in the bar, and "what has my training
done" is answered in two related places rather than one.

**Option B: three tabs — Home · Body · Plan — plus the LiveSessionBar.** This is the
recorded target IA of UI_REDESIGN §6: the reflection tab keeps the name **Body**, leads
with the silhouette, and *absorbs* History — the calendar and session log move under the
body map, making Body the single "what has my training done" surface instead of two thin
ones. What it costs you: the largest single piece of nav work in the plan, and the reach
described below.

**Why the recommendation flipped to four tabs (new evidence, 21 Aug 2026).** When the
merged Body screen was specced in full, the session log came out **five sections deep** —
window picker, silhouette, muscle rows, calendar, coach cards, and only then the session
list, with personal records under it. At the same time the Home rebuild deletes Home's
"Recent" list in **both** branches, and the only scroll anchor built into the merged
screen targets the **calendar**, not the session list. Net effect if you circle B: "what
did I do last session" goes from one tap today to a tab change plus a long scroll. Four
tabs keeps every win listed above and drops only the large merge — and it is closer to
what you originally asked for.

**Three tabs is still a legitimate choice.** If you circle B, two mitigations become
mandatory and are built in the same phases, not deferred: (i) a `section=sessions` scroll
anchor on Body alongside the `section=calendar` one, so anything that means "show me my
log" lands on the log; and (ii) a single **"Last session"** link row on Home. That row is
a link, not a recommendation surface, so the D3 surface map is untouched by it.

Either choice closes NAV-01. There is no keep-five option: leaving the contradiction open
means doing the nav work twice.

**Chosen option: A — four tabs (Home · Body · Plan · History).**
**Signed: repo owner, 21 Aug 2026.**

Consequence for execution: Phase 6a runs **Branch A** — History keeps its tab, the
month grouping / multi-session-day sheet / PR row land in `HistoryScreen` and
`HistoryViewModel` in place, and Body is not restructured. Phase 6b's calendar chip is a
plain tab jump to History; the `Route.Progress` section-anchor work and the "Last
session" row on Home are **not built** (they were Option B's mitigations). Executors of
6a/6b take Branch A and never blend branches.

### D2 — Schedule semantics

Spec: `docs/SCHEDULE_SEMANTICS.md`. Phase 3 derives the `schedule_slots` DDL
from this signed spec; Phase 4 implements the derivation. Read the five worked examples
and ask of each: "is this what I'd expect my week to do?" Rule 6 (a missed day does *not*
carry into next week) is the one deliberately open question — strike it and initial the
margin if you want carry-over instead; the DDL is unaffected either way.

**Signed: repo owner, 21 Aug 2026. Rule 6 KEPT as written** — a missed day does not
carry into the next week; the cycle restarts at the week boundary and nothing is owed.
Phase 4's `weekRolloverResetsSatisfaction` test pins exactly this behaviour.

### D3 — Recommendation surfaces

Recommendations appear on exactly four surfaces, and nowhere else. **Home:** one "next
session" module — today's plan and the top recommendation composed into a single card
with a one-line reason; never two recommendation slots. **Body:** the full explanation
cards. **The start-options sheet** and **the in-workout add-exercise sheet:** action
surfaces, one pinned suggestion each. Phases 5 and 6b both conform to this map; any
executor adding a fifth surface is wrong.

### D4 — Cut list

Cut, recorded 20 Aug 2026. None of these may reappear in a packet without a new signed
decision here.

- **LLM / chat coach** — breaches DESIGN_AUDIT §15 and offline-first; the rule engine is
  the coach.
- **Muscle-head-level granularity** — not derivable from set logs; weighted
  primary/secondary credit is the ceiling.
- **Day and year heat windows** — windows are This week + Last 30 days (Phase 5).
- **Per-routine equipment override** — a variant is its own catalog row (`movementKey`
  family); DESIGN_AUDIT §7's row is superseded.
- **The FK re-pointing custom-merge tool** — collisions are skip-and-surface: the seeder
  always inserts the built-in and flags the collision; a "needs attention" row in Library
  lets the owner rename their custom or keep both. History FKs are never rewritten.
- **The line-art commission ($1.5–4k)** — imagery is Compose-drawn composed thumbnails
  (Phase 8); the commission survives only as a non-committal appendix there.
- **A1 as a phase** — opportunistic, 2-day timebox, gates nothing.
- **The rest overlay bubble** — reaffirming Phase 6 above; DESIGN_AUDIT §10.2 now carries
  the superseding banner.

### D5 — Branch ground truth

**Corrected 22 Aug 2026.** This decision was recorded on a false premise. It said `main`
held only the initial commit; `git rev-list --count main` was 52, and 50 of those are the
shared ancestry this branch is built on. The sessions that wrote D5 could see their own
branch and inferred the rest. Since branch-as-trunk is the conclusion this whole plan
rests on, it is worth being exact about what was actually true.

What was true: the two lineages forked at `319c5f5` on 20 Aug. `main` then took two
commits of its own — a schema v2 adding `equipment`, `load_type` and `secondary_muscles`
as nullable snake_case columns, and a committed Room-generated `2.json` baseline for
them. This branch took 52, including its own schema v2: camelCase columns with SQL
defaults, plus `exercise_muscles`, `schedule_slots` and `seed_meta`. Both declare
`version = 2`, and they are not the same schema.

**Chosen: branch-as-trunk — and for a reason the original entry did not have.** Not
because `main` is empty. Because the two cannot be merged: `main`'s
`domain/ExerciseTaxonomy.kt` and this branch's `domain/ExerciseTraits.kt` each declare
`enum class LoadType` in the same package, and both are one-sided additions, so git
reports no conflict and Kotlin fails with `Redeclaration: LoadType`. A merge produces a
tree that looks clean and cannot compile. Recovery cost decides the direction: everything
unique to `main` is ~2,060 lines and its one irreplaceable artifact regenerates from a
single Gradle task; this branch is ~34,800 lines that no command reproduces.

`main`'s tip is preserved before it is overwritten, and the five things worth keeping from
it are grafted individually rather than merged — see the consolidation commits following
`36ac45a`. Thereafter: branch-per-phase `claude/phase-<n>-<slug>`, one PR per phase, owner
merges, no phase starts before the previous PR lands
(`docs/archive/gameplan/PROTOCOL.md` §2–§3).

### D6 — Second-pass amendments *(informational)*

Recorded 21 Aug 2026, after the second-pass adversarial audit (six independent auditors,
every finding evidence-verified against the repo). These amend **how** the game plan
executes; they do not change what it builds, and nothing here reopens D1–D5.

- **Execution order changes; phase numbers do not.** Phase numbers are identifiers, not
  sequence. The order is **0 (Decisions) → 2 (Test substrate) → 1 (Session hygiene) →
  3 (Schema v2) → 4 (Plan tab) → 5 (Heat & coach) → 6a (Tabs + Body) → 6b (Home) →
  7 (Catalog) → 8 (Imagery, optional)**. Phase 2 is the phase that ships
  `tools/preflight.sh` and the Robolectric lane, so running it first (a) keeps Phase 2's
  verified gate literals true rather than stale, (b) gives Phase 1's gates a working
  domain-test lane instead of a command that exits non-zero on a cold clone, and (c)
  gives Phase 1's repository writes — restore-set, repeat-session,
  delete-finished-session — a Robolectric lane they otherwise lack. The cost is that
  session hygiene reaches your phone roughly one to two executor-days later.
- **Phases 1a and 1b merge into one phase: "Phase 1 — Session hygiene."** One branch
  `claude/phase-1-session-hygiene`, one PR, one combined owner evening. Both packets go
  to the same executor session and are executed in order — 1a in full with its gate
  green, then 1b on top. 1b's gate greps re-assert 1a's invariants, so the sequence
  self-verifies. Saves one owner evening; no safety is lost.
- **The full second-pass findings and the rest of this reconciliation:**
  `docs/archive/gameplan/SECOND_PASS.md`.

**No signature required; recorded for the record.**

---

## ~~Open follow-up — the deload advice has no affordance~~ · **done** (Job 3 / P2)

**Raised:** 21 August 2026, by the coach rework (Phase 5). **Closed:** Job 3 / P2
shipped the lighter-week marker (HOLD, not scaled sets). The Plan Tune
control marks this week; progression HOLDs load. Do not rediscover this
as an open hole.

**Chosen: A** (historical). Named as Job 3 / P2 in
[JOB3_ACTION_PLAN.md](JOB3_ACTION_PLAN.md).
