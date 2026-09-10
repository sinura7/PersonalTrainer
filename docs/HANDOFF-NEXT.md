# Start here

The first thing a new session on this repository should read. Rewritten
2026-09-10, at the end of the session that shipped the Home day board and
the rest timer's last five seconds.

## Where the code stands

`debugLiveCode` is **35**; `appVersionCode` is **1** and stays there until
a real public artifact is cut (FOUNDATION_PROGRAM P12.3). Room is frozen at
v4, the backup document and envelope formats are untouched, and no
identifier is ever rewritten. Those three hold for every future packet.

Landed on 10 September, in order: the emulator lane learned to print its
own failures (#204, #210); the **Home day board** — every session today
drawn as its own bordered block with its stills, the numbered order and
Start on the foot (#205, #213); the **last five seconds tick** (#206,
#212, #217); the four instrumented failures that had made the lane red
since it was first pointed at the right profile (#207, #208, #209, #211);
the golden's record corrected (#215); and the lane's own tests hardened
(#216). #214, from another session, moved the entry wells onto the next
set.

**The hosted emulator lane is green: 80 tests, 0 failed.** It has never
been green before. It is still `continue-on-error` and must stay that way —
see the CI note below.

## What is verified, and how

Every packet above went through the same gate, run in this container:

```bash
PT_STATIC_ONLY=1 sh tools/preflight.sh        # 30 steps: 26 checks, 4 fixture proofs
sh tools/hang-watchdog.sh ./gradlew testDebugUnitTest assembleDebug
#  -> ~1900 tests, 0 failures; PersonalTrainer-1.0.0-debug.apk
```

then CI on the pull request, then a squash merge. The emulator lane is read
directly on each pull request rather than through its check.

Not verified, and it matters: **nothing here has been on a phone.** Six
drops are waiting — `debug-live/2026-09-10` (30) through
`debug-live/2026-09-10-6` (35). Install **35 only**: every earlier one is
the same two features with fewer of the review's fixes folded in.

## What the phone check is

One install, four things:

1. Home shows one bordered block per session, up to four lift pictures,
   the numbered order, and Start (or **Do it today**) on the foot. Tapping
   the block opens the same confirm as before.
2. A Golf warm-up or cool-down block shows the pack's own sentence, not a
   second time estimate.
3. Start a rest, press **-15 s** so the countdown lands on five: a tick and
   a pulse on 5, 4, 3, 2, 1, then the cue. Turn **Last five seconds** off
   in Settings and the last five seconds go quiet while the cue still plays.
4. With TalkBack on, a planned block announces as a **button**.
5. Turn **Last five seconds** off, start a rest, swipe the app away with a
   few seconds left: the last five seconds stay quiet and the cue still
   plays at zero.

## The open question

**Branch protection.** The owner asked for *Tests, lint, debug build* as a
required check on `trunk`. That makes a GitHub-hosted runner able to block a
merge, which [ADR-002](architecture/ADR-002-execution-protocol.md) §6 refuses
permanently. It needs a signed amendment, not a settings toggle. Same
question, same shape, as the emulator lane's `continue-on-error`: see
[DEVELOPMENT.md](DEVELOPMENT.md)'s CI section.

## What is actually left

Biggest first, and the first two are the owner's, not a session's:

- **The physical TalkBack pass** — 0 of 20 pages signed. It is the only
  thing holding the Android Public Candidate milestone
  (FOUNDATION_PROGRAM P9.7). A phone session with the screen reader on,
  walking `AccessibilityMatrix`.
- **The whole-app phase audit** owed at the close of Phase 9. Same eleven
  screens, same phone; do the two in one sitting.
- **Twenty-one DESIGN_AUDIT P1 rows** still genuinely open. Cheapest that
  pays: N-01, a cue preview button in Settings. Biggest felt: B-02, Body's
  first-launch emptiness.
- **R18 convergence steps 3 and 4** — one shared detail-ViewModel shape, and
  the activity-edit capability split — plus the five use-case extractions
  and the seven-row parity table in
  `architecture/completed-training-convergence.md`. Step 3 first: two detail
  screens that can disagree about *missing* versus *failed* is the bug that
  record exists to prevent. The log-time PR badge is **not** on this list:
  it is a signed product fact, because activities are never logged live.
- **R17 measurement** is blocked on a fixture generator and a benchmark
  module nobody has built, not on the owner's history growing. About a day.
- **The 600 dp screen passes never run at 600 dp**: `mount` sizes a Box
  inside a `fillMaxSize` parent, so the width is coerced to the 411 dp
  screen. `Modifier.requiredWidth` fixes it and may surface real tablet
  bugs, which is why it is its own packet.
- **`required_args_mixed = 180`** is the largest debt family in
  `tools/checker-baselines.toml`. Take `required_args_lambda = 46` first as
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
([ADR-002](architecture/ADR-002-execution-protocol.md) decision 1). On
10 September three pull requests were open at once and two of them bumped
`debugLiveCode` to 33 independently — git merges that silently and the
second build is never offered by Obtainium. That is the predicted cost of
breaking the rule, not bad luck.

