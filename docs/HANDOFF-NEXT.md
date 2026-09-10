# Start here

The first thing a new session on this repository should read. Rewritten
2026-09-10, at the end of the session that shipped the Home day board and
the rest timer's last five seconds.

## Where the code stands

`debugLiveCode` is **36**; `appVersionCode` is **1** and stays there until
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
set. #218 stopped the RPE chip and the extra-set button from retyping
the wells; it landed at live 35, the same number #217 already used, so
Obtainium will not offer it. #219 rewrote this file. `#220` is on
`trunk`: a drop is claimed, not assumed. `debug-live-2026-09-10-8`
shipped 36 from `#220` (`8cf0623`) — the RPE rule and the drop lock,
not the lift page. `#221` is on `trunk`: the drop planner fetches tags
before it answers. `#222` is on `trunk`: the golden comparator
forgives one level of rasteriser rounding, capped at 256 pixels.
`#223` is on `trunk`: ADR-024, the deterministic hosted job may gate
`trunk`; the emulator may not. The setting is the owner's.

Open `#202` is R18 step two (exercise detail reads both stores). It
inherits 36 and does not bump it. After it lands, the next drop is 37.
Do not start step three from `trunk` while `#202` is open. Do not start
a second edit of the drop tools, `debug-live.yml`, `SETUP.md`, the owner
loop, `GoldenImageAssert`, `FoundationGoldenTest`, `DEVELOPMENT.md`,
or the ADRs from `trunk`. Do not delete `claude/android-verify-my59sw`
or `claude/ecstatic-galileo-pw9iub`. An agent does not switch branch
protection on. `#224` is on `trunk`: install 36, not 35.

**The hosted emulator lane is green: 80 tests, 0 failed** on `trunk`
before `#222`; that packet adds three comparator unit tests (expected
83). It is still `continue-on-error` and must stay that way —
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

Not verified, and it matters: **nothing here has been on a phone.**

**Install the `debug-live-2026-09-10-8` pre-release, version 36.** It is the
newest, it carries every packet through #220, and everything merged after it
on `trunk` is docs and tests with no app change. Open `#202` is the
remaining Kotlin: the lift page. After it lands, the next drop is 37.
Every earlier drop is the same features with fewer of the review's fixes
folded in.

An earlier version of this file said "install 35", which was wrong twice
over. Two different builds carry `debugLiveCode` 35 — tag
`debug-live-2026-09-10-6` is #217 (the silent tick) and
`debug-live-2026-09-10-7` is #218 (the RPE entry-well fix) — because two
sessions bumped the counter to 35 independently. Obtainium keys its update
offer on that number, so whichever 35 is installed, the other can never be
offered as an update. **If a version-35 build is already on the phone,
install 36 over it** and the ambiguity is gone; nothing needs uninstalling,
because 36 is a higher number than both. Drop-branch names are not release
names: the branch `debug-live/2026-09-10-6` points at #218, whose tag is
`-7`. Trust the tag, and the version, not the branch.

## What the phone check is

One install, six things:

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
6. Type a weight and reps by hand, then rate the effort: the wells keep what
   was typed and the recommendation waits above **Log** with its own **Use**
   (#218, which is why 36 and not 35).

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
- **Twenty-one DESIGN_AUDIT P1 rows** still genuinely open. Cheapest that
  pays: N-01, a cue preview button in Settings. Biggest felt: B-02, Body's
  first-launch emptiness.
- **R18 step two is open `#202`.** After it lands: **steps 3 and 4** —
  one shared detail-ViewModel shape, and the activity-edit capability
  split — plus the five use-case extractions and the seven-row parity
  table in `architecture/completed-training-convergence.md`. Step 3
  first: two detail screens that can disagree about *missing* versus
  *failed* is the bug that record exists to prevent. The log-time PR
  badge is **not** on this list: it is a signed product fact, because
  activities are never logged live.
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
breaking the rule, not bad luck. It happened again the same day: `#217`
and `#218` both claimed 35; `-6` and `-7` both carry that number.
Obtainium still offers 35 until 36 is installed. `#220` shipped 36 as
`debug-live-2026-09-10-8`. `#221`–`#224` are on `trunk`. The next
drop after `#202` is 37.

