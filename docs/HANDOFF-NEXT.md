# Start here

The first thing a new session on this repository should read. Rewritten
2026-09-11, after R18 step 4 (activity-edit split, five use-case
extractions, seven-row parity table).

## Where the code stands

`debugLiveCode` is **38**; `appVersionCode` is **1** and stays there until
a real public artifact is cut (FOUNDATION_PROGRAM P12.3). Room is frozen at
v4, the backup document and envelope formats are untouched, and no
identifier is ever rewritten. Those three hold for every future packet.

R18 steps 1–4 are on `trunk` (or in this packet, landing next):

1. History horizon and block reviews read both stores.
2. The lift page reads both stores.
3. Both completed-training detail screens share `CompletedTrainingDetailLoad`
   (load / missing / failed; `retry()`). A thrown session read is
   unavailable, not "no longer on this phone".
4. **This packet.** Edits are per capability, not by store: notes and
   delete on completed activities; set repair and repeat stay refused
   (activity blocks are snapshots; repeat-as-live stays strength only).
   Five extractions: `CompleteTraining` façade (`Written` / `RuledOut` /
   `Failed` — the plan's Accepted / Rejected / Failed),
   `RecordsCalculator` over `RecordSet`, `ProtectBackup` /
   `OpenBackup`, `DraftStore<T>` with clear-on-accepted-save, live-session
   bar finish through the façade. Seven-row parity table:
   `CompletedTrainingParityTest`. Does **not** bump 38. Do not start a
   numbered R18 step 5 — there isn't one.

Live **38** shipped as `debug-live-2026-09-11` from the architecture stack
`#232`–`#238`. Obtainium still offers 38 until the number rises. This
packet rides the next drop; it is not on the phone yet.

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

Not verified, and it matters: **R18 step 4 has not been on a phone.**

**Install the newest `debug-live-2026-09-11*` pre-release, version 38.**
That is Temper Debug (`com.sinura.personaltrainer.debug`) from Obtainium,
pre-releases on, `PersonalTrainer-*-debug.apk`. Gym-floor Temper stays on
the signed `PersonalTrainer-<version>.apk`. 38 does not yet include
activity notes and delete; those land on the next drop after this packet
merges.

## What the phone check is

One install, the live-38 checks plus, after the next drop that carries
this packet:

1. Open a finished cardio or mixed activity from History. Add a note;
   leave; come back — the note is still there. Overflow offers **Delete
   session…** only, never Repeat, never a set editor.
2. A finished strength session still offers Repeat, set edit, and undo
   as before.
3. Finish from the live-session bar still writes one completed row.
   A thrown finish is a retry, not a crash.

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
- **Four DESIGN_AUDIT P1 rows** still genuinely open. D-04 is closed:
  empty screens draw a rack / plan / catalog / log / gone / retry picture
  that teaches the next tap; the Temper mark stays identity, not a shrug.
  Remaining: D-08 inventory, and D-01/D-02/D-03 which are already Instrument
  in `Type`/`Color`/`Shape`/`Metrics` (confirm in the table, do not redraw).
  D-06 is closed:
  last three seconds of rest hit harder in the hand, and a refused tap
  buzzes twice. L-05 is closed:
  add-to-routine shows the lift still and Work/Rest it will land as, and
  destination routines as pictured cards. W-11 is closed:
  the log button says **Log warm-up** vs **Log set**. W-06 is closed:
  live lift chips show set progress (`2/5`) and a rest badge. I-01 is closed:
  History list cards picture the first three lifts. E-12 is closed:
  a lift can be marked plates / stack / bodyweight / added / assisted. E-04 is closed:
  editor targets are the workout's large steppers, not tiny text boxes. S-02 is closed:
  Start Options routine cards show the first three lift stills and the kit mix.
  I-04 is closed: session detail is a filled program sheet (same cards as the
  floor / program).
  T-16 is closed: first rest names unrestricted battery. G-05 / W-02 / T-12
  are closed: idle rest says **Not running**, and a warm-up names that rest
  did not start. G-02 is closed: rest Start/Skip and Log set share the lower
  dock. B-02 is closed: first-launch Body names catalog lifts and a muscle opens
  the lifts that train it. N-01 is closed: Settings Rest timer **Play complete
  cue** samples the same rest-done tone that fires at 0:00.
- **R18 numbered steps 1–4 are done.** There is no step 5. Set repair on
  activity blocks needs an ADR, not another convergence step. The log-time
  PR badge stays strength-only: activities are never logged live.
- **R17 measurement** is blocked on a fixture generator and a benchmark
  module nobody has built, not on the owner's history growing.
- **The 600 dp screen passes** are on `trunk` (`#231`). Instrumented only.
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
([ADR-002](architecture/ADR-002-execution-protocol.md) decision 1).
`trunk` is the only sitting line. Branch names: `cursor/<short-slug>-b87f`.
JVM (`./gradlew testDebugUnitTest` + `assembleDebug`) is the push gate.
After merge, delete the remote branch. Do not recreate `main`.
