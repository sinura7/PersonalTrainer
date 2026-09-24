# Start here

**In plain terms:** Temper works offline on the phone. Its optional cloud sync
(Temper Account) is switched off until it is made safe. The work under way is
the 22 September whole-app audit, done in small packets, each one tested and
shipped to Temper Debug through Obtainium.

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

Done so far:
- **S0a:** sync paused, delete hidden, honest Account copy.
- **X2a:** JVM migration tests for v5→v6→v7.
- **S0b:** the pull updates rows in place.
- **Q1:** the first-launch chooser is a real gate, and Home's headline follows
  the selected day.
- **X1:** this docs pass, plus ADR-031 and ADR-032.
- **T1a, T1b, T1c-1:** workout tests hold rendered behaviour, not source text;
  T1c-1 took the undo, log bar, toolbar, landscape and clock pins, keeping
  every ban and scanning both workout packages, so W2d's moves cannot empty
  them (#404). T1c-2 has the last eleven files.
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
  reaches the shade (ADR-012).
- **Owner decision, 23 September:** the rest a logged set starts is the
  coach's suggested length, not one picked on the dock (ADR-012 decision
  18; already the behaviour, now written down and held by a test).
- **Owner decisions, 24 September:** fix two rare rest-timer glitches the
  W2b-1 review found (a stop that is not for the running rest; "Rest done"
  after a Skip) as W2b-1b, with two more the review found (a Skip on a
  finished card; a skipped rest brought back); a finish that could cancel a
  new rest's save is W2b-1c, straight after; the rest-length sheet honours
  the phone's reduce motion setting (with W2a).
- **Owner confirmation still owed:** ADR-031 decision 4's conflict rule (the
  later save wins), before sync resumes.

Next, in order (owner go-ahead of 23 September, amended 24 September; the
live order is the table in FRONTEND_REDESIGN.md): T1c-2, W2a, W2b-2,
W2c, W2d, then a check-only phone drop, then W3. X2b completed
Wave 0.

## What is verified, and how

Every packet goes through the same gate, run in this container:

```bash
PT_STATIC_ONLY=1 sh tools/preflight.sh
sh tools/hang-watchdog.sh ./gradlew testDebugUnitTest assembleDebug
./gradlew lintDebug   # every packet (FOUNDATION_PROGRAM §4)
```

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
