# Start here

**In plain terms:** Temper works offline on the phone. Its optional cloud sync
(Temper Account) is switched off until it is made safe. The work under way is
the 22 September whole-app audit, done in small packets, each one tested and
shipped to Temper Debug through Obtainium. A second whole-app audit on
25 September ([design-audit/2026-09-25/AUDIT.md](design-audit/2026-09-25/AUDIT.md))
re-checked every finding after thirty packets and set the next order
(owner decision of 25 September).

The first thing a new session on this repository should read. Rewritten
23 September 2026, during the whole-app audit program (packet X1). The one
before it was written on 12 September and still said live code 46 and Room v4.

## Where the code stands

- **`debugLiveCode`** is set in `app/build.gradle.kts`. Never guess it: run
  `python3 tools/debug-drop-plan.py`, which names the next number and the drop
  suffix. `appVersionCode` is **1** and stays there until a real public
  artifact is cut (FOUNDATION_PROGRAM P12.3).
- **Room** is `TemperDatabase` **v7** (`temper.db`), with hand-written
  migrations 1→…→7. Every version has its exported schema, a byte-identical
  copy in `app/src/debug/assets/`, and a JVM migration test;
  `TemperSchemaAssetsTest` fails if any of the three is missing
  ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)). There is no
  `fallbackToDestructiveMigration`, and there never will be.
- **Temper Account sync** (Temper Debug only) is **paused**
  (`AccountSyncGate.SYNC_PAUSED`): no pass uploads or downloads, and edits
  made while signed in, with the session loaded, still queue. The pull now updates rows in place (packet S0b). Packet S1
  resumes sync, and only when
  [ADR-031](architecture/ADR-031-trusted-server-sync-lane.md) decision 3 is
  met and the owner says yes. In-app account deletion is off; Settings → Account says how to request
  it.
- **Structure:** see
  [architecture/CURRENT_STRUCTURE.md](architecture/CURRENT_STRUCTURE.md).

Temper Debug (`com.sinura.personaltrainer.debug`, the Obtainium icon) and
gym-floor Temper are different apps with different databases. Do not mix them.
Uninstall still wipes (`allowBackup=false`, ADR-009); backup is the recovery
path.

## What is being worked on

The **whole-app audit** of 22 September:
[design-audit/2026-09-22/AUDIT.md](design-audit/2026-09-22/AUDIT.md). It
re-ordered the frontend redesign's remaining packets (F4–F11) and interleaved
them with sync-safety packets. The live order, with progress, is the packet
table in [FRONTEND_REDESIGN.md](FRONTEND_REDESIGN.md).

The **25 September whole-app audit** (packet X6,
[design-audit/2026-09-25/AUDIT.md](design-audit/2026-09-25/AUDIT.md)) is the
latest record: it re-verified every 22 September finding, re-ran the gate in
its own container, drew every screen on the JVM, and proposes a new order
with six new packets (X7, R1, R2, R3, X8, X9); the owner adopted it on
25 September, and the table in FRONTEND_REDESIGN.md carries it.

Done so far:
- **S0a:** sync paused, delete hidden, honest Account copy.
- **X2a:** JVM migration tests for v5→v6→v7.
- **S0b:** the pull updates rows in place.
- **Q1:** the first-launch chooser is a real gate, and Home's headline follows
  the selected day.
- **X1:** this docs pass, plus ADR-031 and ADR-032.
- **T1a, T1b, T1c-1, T1c-2:** workout tests hold rendered behaviour, not
  source text; T1c-1 took the undo, log bar, toolbar, landscape and clock
  pins, keeping every ban and scanning both workout packages, so W2d's
  moves cannot empty them (#404). T1c-2 took the last eleven files: the
  floor's chrome, identity, entry, switcher, notes and reduced motion, with
  every ban kept; its dead-code list is W2a's (#408).
- **W1a, W1b, R0:** the lift switch, effort and planned-rest wording, the
  routine picker's second tap (drops 101, 102).
- **X3:** the retired emulator goldens left the hosted lane.
- **W1c:** the entry wells hold still on the first working set (drop 103).
- **X4:** a rest length picked while the rest page or a lift is still loading
  survives; the routine editor's Leave anyway no longer strands the screen
  when a save finishes at that instant (the `RoutineEditorViewModelTest`
  30-second wedge, open since 10 September, was this); the static gate and
  the cloud build were made dependable (see below).
- **W1d:** the workout floor holds still and whole at large text: Last alone
  at font 1.6 and above, with Best and Volume in Details once a working set
  is logged; stats values that shrink to one line where they can (ADR-030
  says when one still wraps); a − / + drawn whole; an outsized weight that
  keeps its unit (drop 104).
- **X2b:** before Room migrates `temper.db`, the app copies it with its WAL
  into `files/pre-migration/temper-v<n>/` (ADR-010 decision 12), so the next
  schema bump (S2b's v8) has a rollback copy (#402).
- **W2b-1:** the rest timer's ±15 s and its finish can no longer undo each
  other across threads (ADR-012 decision 1); a late ±15 on a finished rest
  does nothing, so "rest done" never turns into a skip (#403).
- **W2b-1b:** a stop names the rest it is for, so a late one cannot end the
  next set's rest; while the app that skipped it is still running, a
  skipped rest never says "Rest done" and a recovery never brings it back;
  a Skip on a card left on screen after its rest finished leaves it done
  (ADR-012; #405).
- **W2b-1c:** a rest that finishes on one thread can no longer cancel the
  save of the next set's rest starting on another, which left that rest
  with no row and no wakeup; a rest started as the last one finishes still
  reaches the shade (ADR-012; #406).
- **W2b-1d:** the notification's Skip names the rest its card shows, so a
  tap just as the rest ends keeps "rest done", and a tap on a card that has
  not caught up with the next set's rest leaves that rest running
  (ADR-012; #407).
- **W2a:** about 1,600 lines of app code that nothing used are gone: the
  history set sheet's unused compact entry, unused components, ten
  ViewModel functions and four flows no screen uses, test-only constants
  and the old RPE helper (its stored flag is left unread). Four design-token ceilings are
  lower, each swap proven identical. The rest-length sheet opens in place,
  without its slide, when the phone asks for reduced motion (ADR-023;
  #409).
- **X5:** the local coverage check (`tools/verify.sh`) counts the classes
  the JVM tests load through Robolectric. Until now it left them out, so
  the timer and workout floors failed on numbers that measured the class
  loader, not the tests. The same tests read timer 74.7 %, workout 86.8 %
  and the workout screen's package 85.9 %; the floors are re-based just
  under those readings, and the workout screen's package and the code
  that saves sets get floors of their own (#410).
- **W2b-2:** the Log's dock and the rest page share one set of rest
  commands (`RestCommands`: the rest card's state, saving a pick, starting
  a rest by hand, the battery line, the pick echo) and one hint loader
  (`ProgressionHintLoader`), instead of two copies. Nothing a lifter sees
  changes: six new tests pinned today's behaviour on the old code first.
  ADR-012 decision 18 now says in writing that a length picked on the
  dock holds until a logged set starts a rest or the lift changes.
  Running the rest page against the Log confirmed that its next-set line
  can differ from the Log's; W2b-4 fixes that (#411).
- **W2b-3:** the lock screen's Skip and the rest page's Skip end only the
  rest they show, as the notification's already does (W2b-1d). A tap just
  as the rest runs out keeps "rest done" instead of wiping it, and a tap
  on a screen that has not caught up with a newer rest leaves that rest
  running. The dock's Skip is unchanged; naming it too is a separate
  owner decision (ADR-012; #412).
- **W2b-4:** the rest page's next-set line is the Log's: one shared coach
  question (`NextSetInputs`) with the Log's inputs (Another set, last
  session's effort, the set being corrected), read for the lift the page
  shows, and shown only where the Log shows its card. Its planned rest
  length is unchanged. Whether the page and the dock should both plan the
  extra set's shorter rest after Another set is an owner decision still
  owed (ADR-012; #413).
- **W2c:** the coach is asked again only when something it reads has
  changed. A weight step with no effort picked, a note, or a second of
  rest no longer re-asks it; the rest page used to ask twice a second.
  The lift picker sorts the library only while it is open, and the lift
  it suggests follows edits to that lift. Nothing a lifter sees changes:
  pins written on the old code pass on the new, and every part of the
  two "has anything changed" checks has a test that fails when it is
  left out (ADR-029, ADR-008; audit C-2; #414).
- **X7:** the signed gym-floor Temper builds again. The shrinker had
  stopped on slf4j (brought in by Temper Account's sign-in library on
  21 September) and nobody builds the release day to day, so nobody saw;
  one rule fixes it. `release.yml` no longer asks for the retired SDK
  `tools` package, and a failed upload now fails the run instead of
  saying "already present". The gate's `assembleDebug` builds the
  release as well (unsigned, even where a release key is present), which
  holds the slf4j rule, and `tools/check-release-lane.py` keeps the
  workflow fixes and that gate wiring in place (audit X6, BR-1, BR-2,
  BR-7; #416). The shrunk APK was inspected, not launched: the first
  signed release gets a launch on the phone before anyone relies on it.
- **W2d-1:** the floor's undo queue (the offers, their order, how long
  each shows, and the copy kept for when Android stops the app) moves
  out of the workout ViewModel into a small helper, `FloorUndoOffers`.
  Deleting, removing and undoing work exactly as before; 16 tests
  written on the old code first prove it, among them that an undone or
  expired offer stays gone after the app is stopped, which nothing
  held until now. The ViewModel is 2,377 → 2,327 lines. Built before
  the 25 September order landed; the owner chose to merge it ahead of
  R1 and R2.
- **W2d-2:** the floor's set save (the one set being written, its two
  copies for when Android stops the app, what each outcome says, and the
  check that settles a save whose outcome is unknown) moves out of the
  workout ViewModel into a small helper, `FloorSetSaves`. Saving,
  retrying and editing a failed set work exactly as before; 13 tests
  run on the old code first prove it, and a replay of 22 save
  situations reads the same before and after (#431). Next in W2d are
  W2d-3a (timed work) and W2d-3b (notes); AR-3 and AR-4 wait for a later
  packet (owner decisions of 25 September, below).
- **T2:** the Log test that failed on about one GitHub run in fifteen
  was a real tie, not bad luck: two sets saved in one millisecond made the
  saved-sets sheet call the first "Latest" while the Last set cell called
  the second. The sheet now takes the later set, and the test makes the
  tie every run. The emulator lane's font and keyboard waits went from 10
  to 30 seconds (#418).
- **R1-1:** signing out of Drive switches automatic backup off and
  forgets its password, as the privacy page says; with automatic backup
  on it asks first and points to Show backup password (audit BK-1;
  #419). Found by its review, not fixed: sign-out has never withdrawn
  Drive access (`RevokeAccessRequest` built with no scopes).
- **R1-2:** the restore guard counts goals, activity templates and the
  weekly plan, and the restore question names the plan once (audit BK-3;
  #420).
- **R1-3:** the Drive copy after a finished workout belongs to the app,
  so tapping Done no longer cancels it; one Drive backup runs at a time,
  sign-out and switch-off stop a copy in flight, and a copy gives up
  after ten minutes (audit BK-4; #421).
- **R1-4:** Settings' "Generate a week" asks first, keeps the training
  block you are in (finished or not), never logs an old bodyweight as
  today's weigh-in, and cannot run twice. It still adds its routines
  beside the ones on your week; whether it should replace them is an
  owner question (audit UI-3; #422).
- **R1-5:** a damaged settings file starts again from defaults instead of
  refusing every save forever, and Settings → Backup says so: history is
  safe, settings are back to defaults, Drive is signed out and automatic
  backup is off. The first-launch save question no longer covers the
  retry screen (audit DB-2, L-3; #423). This completes R1. Before sync is
  unpaused: a reset on a signed-in phone queues default account settings
  stamped now, which would win the first push (a condition for S1).
- **R2-1:** Home and History no longer close the app when a database read
  fails. Home keeps its last numbers and goes on updating; History keeps
  the workouts, says it may be behind and offers Retry (audit DB-1, AR-1,
  UI-17; #424). Found by its reviews, not fixed: Plan still reads the
  weigh-ins and the current block raw.
- **R2-2:** a finish, discard or planned start stands when the small
  write after it fails (the link to its planned day): the bar opens the
  summary instead of crashing (audit UI-12; #425). Found by its reviews,
  not fixed: after a restart, a link whose clear had failed comes back,
  and an untagged one completes any later finish.
- **R2-3:** the diagnostics you can send start before the pre-migration
  copy, so a copy that fails while being written is recorded there while
  the app stays open (audit AR-2; #426). Not covered: a copy skipped for
  lack of space, and keeping the record after the app closes.
- **R2-4:** the coach no longer talks in reps about planks, dead hangs
  and stretches: no Next card or rest-page Next line, no Apply, never
  "ready to progress", and the rest page's last set reads its time
  (audit DM-1; #427). Owner question raised: the rest after a hold is a
  flat two minutes; should it follow the plan?
- **R2-5:** Finish warns while a logged set is open for changes and
  offers "Back to my change"; the bottom bar's Finish waits for it and
  says why (audit UI-2 on the floor; #428).
- **R2-6:** a workout reminder tapped while you are in a workout (or
  cardio) leaves you where you are and says why, and a Start's reminder
  stays in your notifications; a reminder for the session you are in
  opens it; a reminder is marked used only once its Start goes through,
  and one whose planned day cannot be read says so instead of closing
  the app (audit UI-1 on Home; #429). This completes R2. Its whole-app
  test found the likely reason JVM tests of the full app never loaded:
  androidx keeps the first test's app for every later one in the
  process; that test resets it before and after each case. Found, not
  fixed: with nothing live, a Start tapped on a screen pushed over Home
  (the workout summary) waits, and the session then starts by itself
  when you leave that screen.
- **T3:** the Log's notes test that failed once in seven runs of its
  class now waits for the lift to load and for the notes' copy to
  settle; new tests hold what it only met by luck (leaving while a lift
  loads, then a restart) and the notes' saves nothing held (a pause into
  a workout with no notes, a phone killed mid-load). Tests only
  (#430).
- **N1:** a workout reopened while its lift was still loading no longer
  brings back an older note (and saves it over the newer one) or shows
  another lift's typed weight: a reopened workout restores only its own
  lift's numbers, and the notes are kept once for the whole workout
  (#432). Found, not fixed: after removing the only lift, Undo (or
  adding it back) leaves it loading; an undone lift comes back at 0 kg;
  Finish from the workout bar can drop the last words typed; the rest
  page can name another lift while the one picked is loading.
- **Rest alarm, RT-2:** with notifications off, the "Rest alerts" sentence
  asked again on every workout opened and every rest page, and after two
  refusals its Continue brought up nothing. It is now answered once on the
  phone (Continue, Not now, or closing it) and stays down; the compact row on the
  workout and the rest page is the way back. The answer is this phone's:
  restore leaves it alone (#433). Android shows its own prompt twice at
  most, and the first-open walk can spend one of those before the
  sentence is ever seen; after a second refusal the row is the only way
  back.
- **Rest alarm, RT-1:** on a phone in the light theme, the rest countdown
  in the shade and on the lock screen was near-white on the white card.
  It now takes the phone's own notification text colours, dark on a
  light card and (from Android 10) light on a dark one, and keeps its
  size and face (#434). A test draws the card's views in each theme and
  measures every line against the least favourable card.
- **Rest alarm, RT-4 and RT-5:** the two rest-alarm quirks. A resume, an
  exact-alarm grant or an early alarm delivery armed the wakeup straight
  from the store, so it could be set before the rest's row was on disk
  (or when it never landed); and a save could arm a newer rest over the
  older rest's row. After a kill in that moment, the rest ended in
  silence. Every re-arm now goes through the same queue as the writes,
  row first, and a save arms only the rest it wrote (#435). This
  completes the rest-alarm packet.
- **Owner decision, 26 September:** two sessions split the order so that
  no two work in the same file at once: one runs W2d-3a, W2d-3b and W2e
  (the workout floor), the other the rest-alarm packet, R3 and R4 (the
  rest timer, the updater and reminders). R4 goes after R3, before the
  check-only drop.
- **Owner decisions, 25 September (later):** this session's packets run
  T3, then N1 (a workout reopened while its lift was still loading
  brought back an older note, then saved it over the newer one, and
  could show another lift's typed numbers; a visible fix), then W2d-2,
  W2d-3a (timed work) and W2d-3b (notes), then W2e: a lighter week or a
  unit switch made while a lift is open reaches its pre-filled numbers
  at once, while numbers you have typed stay. Audit X6's AR-3 and AR-4
  (the invalidation fan-out per logged set, the eager `stateIn` on the
  floor) leave W2d for a later packet that needs owner decisions.
- **Owner decision, 23 September:** the rest a logged set starts is the
  coach's suggested length, not one picked on the dock (ADR-012 decision
  18; already the behaviour, now written down and held by a test).
- **Owner decisions, 24 September:** fix two rare rest-timer glitches the
  W2b-1 review found (a stop that is not for the running rest; "Rest done"
  after a Skip) as W2b-1b, with two more the review found (a Skip on a
  finished card; a skipped rest brought back); a finish that could cancel a
  new rest's save is W2b-1c, straight after; the rest-length sheet honours
  the phone's reduce motion setting (with W2a); the notification's Skip
  names its rest (W2b-1d, straight after W2b-1c); two older rest-alarm
  quirks the W2b-1c review found (an alarm set a moment before its row
  lands; a reschedule that ignores a failed disk write) get one small
  packet after W2d.
- **Owner decisions, 24 September (later):** the local coverage check
  counts what the JVM tests really load, as packet X5 straight after W2a;
  the lock screen's and the rest page's Skip name their rest, as the
  notification's does (W2b-3, after W2b-2); if a test confirms that the
  rest page's next-set line can differ from the Log's (after Another set,
  or a lift's first set), it is fixed in its own packet (W2b-4).
- **Owner confirmation still owed:** ADR-031 decision 4's conflict rule (the
  later save wins), before sync resumes.

Next, in order (owner decisions of 25 September, and of 26 September for
R4; the live order is the table in FRONTEND_REDESIGN.md): W2d-3a and
W2d-3b, W2e, R3 (updater), R4 (reminder follow-ups), then a check-only
phone drop, then W3, S1, X8 and X9. Two sessions run them side by side:
W2d-3a to W2e in one, R3 and R4 in the other (the rest-alarm packet is
done). X2b completed Wave 0.

## What is verified, and how

Every packet goes through the same gate, run in this container:

```bash
PT_STATIC_ONLY=1 sh tools/preflight.sh
sh tools/hang-watchdog.sh ./gradlew testDebugUnitTest assembleDebug
./gradlew lintDebug   # every packet (FOUNDATION_PROGRAM §4)
```

`assembleDebug` also builds the unsigned release (R8, resource
shrinking, lint-vital), since X7, so a shrinker break fails the gate the
day it lands rather than the day a signed Temper is wanted.

Then a squash merge into `trunk`. For every defect, a test that fails on
`trunk` and passes on the branch. Two review passes per packet: one
independent, one adversarial.

- **Visible work** is proven by JVM renders written by the test run, at
  360×640, 412 dp, landscape, and font 1.0 / 1.6 / 2.0
  ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)).
- **The hosted "Instrumented smoke" job** is non-blocking. Its retired
  goldens were removed in X3, so what fails there now is a crash, a journey
  or a reachability check, and is worth reading. The first run after X3
  (#395) reached the 38 entry and 15 completion layout cases' own checks
  for the first time since 17 September and found four checks the goldens
  had hidden. Three were stale expectations, which X4 corrects: the progress
  line is drawn in upper case, an edit scrolls the lift's identity away to
  reveal its entry, and a finished plan's dock leaves the rest-alert row no
  room, all on purpose. The fourth was real: a 99,999.99 kg weight pushed its
  unit out at 360 dp, font 2.0. W1d keeps the unit and makes the test
  measure it the way the JVM gate does. One failure was known and standing:
  `WorkoutEntryJourneyInstrumentedTest`'s entry-position check,
  955 → 997 px on every run since #374. The Last-only stats cell before the
  first working set was one caption line (16 dp) shorter than the full row, so
  the entry wells moved down when that set was saved. W1c fixed it at
  side-by-side sizes, and W1d the two cases left: stacked large text (font
  1.6 and above), and a stats value that wrapped, except one too long even at
  its label's size, which still wraps (ADR-030 lists when).
  `FirstWorkingSetRenderTest` holds all of them on the JVM gate. The lane was
  green on W1d's first hosted runs (#399, both runs).
- **"Tests, lint, debug build"** is the hosted check that must be green
  ([ADR-024](architecture/ADR-024-hosted-jvm-check.md)).
- **GitHub-hosted runners** are not the test lane.

**Drops.** `python3 tools/debug-drop-plan.py` names the build number and the
suffix. Bump `debugLiveCode` in its own PR. Then push the merged `trunk`
commit to `refs/heads/debug-live/<suffix>`; `debug-live.yml` builds, signs and
publishes the Obtainium pre-release. Quiet (code-only) packets ride along with
the next visible drop.

## Waiting on the owner

- **Phone checks** after each visible drop, listed in that drop's PR notes.
- **Branch protection** is decided but not switched on
  ([ADR-024](architecture/ADR-024-hosted-jvm-check.md)): make *Tests, lint,
  debug build* a required check on `trunk`, with "require branches to be up
  to date". Only the owner can change that setting.
- **Supabase changes** (packet S2a) are shown to the owner before they are
  applied ([ADR-031](architecture/ADR-031-trusted-server-sync-lane.md) §5).
- **Eight stale branches** on GitHub are the owner's to delete: sessions may
  not delete branches (the git proxy answers 403). Every one is already on
  `trunk` or was a draft the owner closed on 23 September:
  `claude/app-audit-optimization-xnqf5e`, `claude/ecstatic-galileo-pw9iub`,
  `claude/file-visibility-check-jraqc2`,
  `claude/google-signin-integration-xijk5e`, `codex/home-day-design`,
  `cursor/live-workout-clarity-a-35c4`,
  `cursor/live-workout-clarity-research-3c1f`,
  `probe/delete-permission-test`. The `debug-live/*` branches stay: they are
  the drop record the planner reads.

## Known tooling gaps

- `required_args_mixed` is the largest debt family in
  `tools/checker-baselines.toml`.

## Never

- Gradle modules, localisation, KMP, a sixth tab, or LLM-as-author.
- A schema change without its migration test.
- A GitHub-hosted runner as the test lane.
- Bumping `debugLiveCode` outside a drop.
- Touching a Supabase project other than Temper's.

## Process

- One packet open at a time, one branch per packet
  ([ADR-002](architecture/ADR-002-execution-protocol.md) decisions 1–2),
  squash-merged.
- `trunk` is the only sitting line. After a merge, delete the remote branch.
  Do not recreate `main`.
- How replies to the owner are written is in [CLAUDE.md](../CLAUDE.md). The
  owner loop is `.cursor/rules/owner-loop.mdc`.
