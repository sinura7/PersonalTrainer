# Start here

The first thing a new session on this repository should read. Rewritten
2026-09-12, after the History-on-update packet (Temper Debug Obtainium
updates must keep finished workouts).

## Where the code stands

`debugLiveCode` is **46**; `appVersionCode` is **1** and stays there until
a real public artifact is cut (FOUNDATION_PROGRAM P12.3). Room is frozen at
v4, the backup document and envelope formats are untouched, and no
identifier is ever rewritten. Those three hold for every future packet.

**This packet (History survives an Obtainium update of Temper Debug) is on
`trunk`.** It does not bump 46. Do not start Home packets G or H — they have
no written scope. Do not invent a sixth tab. Do not cut a drop in this
packet.

### What wiped

Allen’s History reset was on **Temper Debug**
(`com.sinura.personaltrainer.debug`) — the Obtainium icon — not gym-floor
Temper. Those are different apps and different databases. Do not mix them.

What it was **not:** a Room schema bump (v4 identity hash is unchanged), not
`fallbackToDestructiveMigration` (still absent), not a gym-floor
`appVersionCode` change, not a debugLiveCode path that deletes `temper.db`.

What *would* have let a wipe ship unnoticed: the upgrade-in-place proof opened
the dead `TrainerDatabase` (`personal_trainer.db`). History lives on
`TemperDatabase` (`temper.db`). Backup round-trip of History cards
(`session_exercises`) was also missing from the fingerprint, so a restore
that dropped the join table could still look green.

Uninstall still wipes (`allowBackup=false`, ADR-009). Backup is the
off-device recovery path and now round-trips finished strength sessions and
completed activities through the queries History actually uses.

### What landed

1. Upgrade-in-place proof uses production `TemperDatabase.create` on
   `temper.db`. Finished History rows and a live session survive close/reopen.
   Catalog seed after reopen must not delete them.
2. A version-code-only drop must not rename the package: suffix stays `.debug`,
   never `.debug.<live number>`. Room stays v4 / `temper.db`.
3. Backup export → restore keeps a finished strength day (session, lifts,
   sets) and a completed activity on History’s queries. An in-progress
   session is still excluded from the file (so Home cannot resume a phantom).
4. The older v2 round-trip now fingerprints `session_exercises`.

Do not open Gradle modules, localisation, a sixth tab, LLM-as-author, a
Room v3 bump, or GitHub-hosted runners as a test lane. Do not bump
`debugLiveCode` unless `python3 tools/debug-drop-plan.py` is cutting a
drop.

## What is verified, and how

Every packet goes through the same gate, run in this container:

```bash
PT_STATIC_ONLY=1 sh tools/preflight.sh        # 30 steps: 26 checks, 4 fixture proofs
sh tools/hang-watchdog.sh ./gradlew testDebugUnitTest assembleDebug
```

then a squash merge into `trunk`. Do **not** use GitHub-hosted runners as
the test lane. The yaml may stay. Ignore it. Cursor JVM + Obtainium are
how we test.

**Phone check (owner), after the next drop that carries this packet:**

1. Open **Temper Debug** (second icon, `com.sinura.personaltrainer.debug`).
   Gym-floor Temper is the other icon — leave it alone.
2. Finish a workout so it shows on History.
3. Pull Obtainium and install the next Temper Debug update
   (`PersonalTrainer-*-debug.apk`, same applicationId, higher live number).
4. Open History. The workout is still there.

Uninstall still empties History. If that happens, restore the backup file
from Settings; it must bring the sessions back.

## One thing waiting on the owner

**Branch protection is decided but not switched on.**
[ADR-024](architecture/ADR-024-hosted-jvm-check.md) amends
[ADR-002](architecture/ADR-002-execution-protocol.md) §6 for one named job:
*Tests, lint, debug build* may be a required check on `trunk`, the emulator
lane may never be, and the local gate is unchanged and still comes first.
What remains is the repository setting, which only the owner can change —
the required check plus "require branches to be up to date". Until then
`trunk` carries no protection. An agent cannot set it and must not ask for
the scope to.

## What is actually left

Biggest first, and the first two are the owner's, not a session's:

- **The physical TalkBack pass** — 0 of 20 pages signed. It is the only
  thing holding the Android Public Candidate milestone
  (FOUNDATION_PROGRAM P9.7). A phone session with the screen reader on,
  walking `AccessibilityMatrix`.
- **The whole-app phase audit** owed at the close of Phase 9. Same eleven
  screens, same phone; do the two in one sitting.
- **Floor phone-check UX is closed in code.** Phone gate stays the owner's,
  on the next Obtainium drop.
- **R18 numbered steps 1–4 are done.** There is no step 5. Set repair on
  activity blocks needs an ADR, not another convergence step.
- **R17 measurement** is blocked on a fixture generator and a benchmark
  module nobody has built, not on the owner's history growing.
- **The 600 dp screen passes** are on `trunk`. Instrumented only.
- **`required_args_mixed`** is the largest debt family in
  `tools/checker-baselines.toml`. Take `required_args_lambda` first as
  the proof that the ratchet-down loop works.
- **A cold container silently downgrades the static gate** and still exits
  OK when no compiler jar is present. Make the skip non-zero unless
  `PT_ALLOW_NO_COMPILER=1`.
- **Packets G and H for Home** are held until the owner has the board on the
  phone, and have no written scope. Do not guess at them.

Deliberately parked, so nobody re-opens them: Phase 10 (iOS/KMP) and Phase
11 (encrypted sync) are gated and correctly not started; AGP 8.9.3 is
refused until a named fix needs it; `versionCode` stays 1.

## Process

One packet open at a time, on a branch, squash-merged
([ADR-002](architecture/ADR-002-execution-protocol.md) decision 1).
`trunk` is the only sitting line. Branch names: `cursor/<short-slug>-b87f`.
JVM (`./gradlew testDebugUnitTest` + `assembleDebug`) is the push gate.
After merge, delete the remote branch. Do not recreate `main`.
