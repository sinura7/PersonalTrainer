# Start here

The first thing a new session on this repository should read. Rewritten
2026-09-12, after the floor phone-check packet (RPE from history, next-lift
box, Start next, rest time before first log, X goes Home, Finish owns
save/discard).

## Where the code stands

`debugLiveCode` is **45**; `appVersionCode` is **1** and stays there until
a real public artifact is cut (FOUNDATION_PROGRAM P12.3). Room is frozen at
v4, the backup document and envelope formats are untouched, and no
identifier is ever rewritten. Those three hold for every future packet.

**This packet (floor phone-check UX) is on `trunk`.** It does not bump 45.
The phone still offers live 45 until the next drop. Do not start Home
packets G or H — they have no written scope. Do not invent a sixth tab.

What landed:

1. RPE / Next use that lift's logged history, not a generic 6–9. RPE stays optional.
2. Selected lift is a pinned box (picture + name + planned work + rest) above the rest dock.
3. Idle rest dock: **Start next** (primary, does not start rest) and **Start** (rest only).
4. Planned rest is visible before the first logged set of a lift. Idle still says **Not running**.
5. Duplicate rest/next chrome is collapsed so a normal phone can see the session lift list.
6. X / back goes Home. Session stays live (in-progress bar + rest notification).
7. Finish is the explicit end: **Save as is** / **Leave without saving**.

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

Not verified, and it matters: **this floor packet has not been on a phone.**

**Install the newest `debug-live-2026-09-11-8` pre-release, version 45.**
That is Temper Debug (`com.sinura.personaltrainer.debug`) from Obtainium,
pre-releases on, `PersonalTrainer-*-debug.apk`. Gym-floor Temper stays on
the signed `PersonalTrainer-<version>.apk`. 45 does not yet include this
floor packet; those land on the next drop after this packet merges.

## What the phone check is

One install, after the next drop that carries this packet:

1. Start a session. The selected lift's picture, name, planned work, and
   rest time stay visible before the first logged set. Idle rest says
   **Not running**.
2. RPE chips name last time's effort for that lift when history exists.
   Next includes that RPE. RPE stays optional.
3. Idle dock: **Start next** is the large control; **Start** only starts rest.
4. The session lift list is visible in one view with the log plates.
5. X goes Home with no popup. The in-progress bar is there. Finish offers
   **Save as is** and **Leave without saving**.

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
