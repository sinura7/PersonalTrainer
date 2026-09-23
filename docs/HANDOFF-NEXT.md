# Start here

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
  still queue. The pull now updates rows in place (packet S0b). Packet S1
  resumes sync, and only when
  [ADR-031](architecture/ADR-031-trusted-server-sync-lane.md) decision 3 is
  met. In-app account deletion is off; Settings → Account says how to request
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

Next: T1 (workout test triage), then W1a.

## What is verified, and how

Every packet goes through the same gate, run in this container:

```bash
PT_STATIC_ONLY=1 sh tools/preflight.sh
sh tools/hang-watchdog.sh ./gradlew testDebugUnitTest assembleDebug
./gradlew lintDebug   # at least on every visible packet
```

Then a squash merge into `trunk`. For every defect, a test that fails on
`trunk` and passes on the branch. Two review passes per packet: one
independent, one adversarial.

- **Visible work** is proven by JVM renders written by the test run, at
  360×640, 412 dp, landscape, and font 1.0 / 1.6 / 2.0
  ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)).
- **The hosted "Instrumented smoke" job** is non-blocking. Its 17 September
  emulator goldens are retired and fail on `trunk` too.
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

## Known tooling gaps

- `tools/debug_drop.py` treats a suffix as free when no *tag* exists. A failed
  drop leaves a `debug-live/<suffix>` branch with no tag, and the planner names
  it again. Until that is fixed, pick the next free suffix by hand.
- A cold container silently downgrades the static gate, and still exits OK,
  when no compiler jar is present. Make the skip non-zero unless
  `PT_ALLOW_NO_COMPILER=1`.
- `required_args_mixed` is the largest debt family in
  `tools/checker-baselines.toml`.

## Never

- Gradle modules, localisation, KMP, a sixth tab, or LLM-as-author.
- A schema change without its migration test.
- A GitHub-hosted runner as the test lane.
- Bumping `debugLiveCode` outside a drop.
- Touching a Supabase project other than Temper's.

## Process

- One packet open at a time, on a branch, squash-merged
  ([ADR-002](architecture/ADR-002-execution-protocol.md) decision 1).
- `trunk` is the only sitting line. After a merge, delete the remote branch.
  Do not recreate `main`.
- How replies to the owner are written is in [CLAUDE.md](../CLAUDE.md). The
  owner loop is `.cursor/rules/owner-loop.mdc`.
