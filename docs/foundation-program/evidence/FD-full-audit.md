# Full-tree adversarial audit — 29 August 2026

Eight independent audit passes over the whole tree (data/backup, timers
and notifications, scheduling domain, workout logging and coaching, every
UI surface, security/privacy, performance, build/test tooling), every
finding re-verified against source before any fix. This file is the
ledger: what was found, what shipped on this branch, and what is
deliberately deferred with a recommendation. Do not rediscover a FIXED
row as live work; the DEFERRED rows are the queue.

## Fixed on this branch

Severity is the audit's, worst first. Commits are on
`claude/full-app-audit-s890pj`.

| Finding | Sev | Commit |
|---|---|---|
| `OccurrenceGenerator` placed every rule on weekStart+ordinal — one civil day early for a Sunday week start, with duplicate rows after a preference change | P0 | `046ae19` |
| Rule/occurrence `REPLACE` upserts + `ON DELETE CASCADE` destroyed the rule's occurrence history (an hour-chip tap wiped the week) and every occurrence rewrite dropped reminder deliveries (snoozes died on restart) | P0/P1 | `046ae19` |
| `discardSession` deleted finished sessions — a stale Active Workout screen could destroy a finished session and all its sets | P0 | `16b153f` |
| Pending-occurrence binding armed before the start outcome and completed without a session check — a Blocked start left the intent live and an unrelated finish marked the wrong plan row DONE; forget-first paths unbound a running planned session; the sheet discard never unbound; a rejected composer save lost the link | P0/P1 | `16b153f` |
| Reminder "Start" was a receiver trampoline — Android 12+ silently drops a receiver's `startActivity`, so the tap dismissed the notification and opened nothing | P1 | `b14c69a` |
| A wall-clock step >2 s mid-rest read as a reboot and silently wiped the running rest while its alarm stayed armed | P1 | `b14c69a` |
| Manifest listened for `android.intent.action.TIME_CHANGED` (the constant's NAME; its value is `TIME_SET`) — clock changes never rebuilt reminders | P2 | `b14c69a` |
| Cardio boot marker compared with exact `==` — a 1 ms skew dropped to the wall clock and a mid-run resync corrupted the recorded duration | P2 | `b14c69a` |
| Rest completion's stop was not id-checked — an alarm claiming timer X could wipe the +15 s extension timer Y | P2 | `b14c69a` |
| WIPING-phase restore recovery fingerprinted stores the Room transaction never writes — cross-device restores read as rolled back, journal cleared, preferences silently dropped; the detected-commit path also skipped preferences on its phase gate | P1 | `96bceff` |
| A `replaceRoom` throw reported "Training data was replaced" over a rolled-back (unchanged) phone and left the journal blocking every start | P2 | `96bceff` |
| Safety-copy export wrote full plaintext history with no password gate or warning | P1 | `96bceff` |
| Protected export silently degraded to plaintext after process death in the file picker | P2 | `96bceff` |
| Unbounded `readBytes`/`readText` of untrusted backup input (SAF pick, Drive download) | P2 | `96bceff` |
| Envelope accepted forged iteration counts (2 B iterations = hours of CPU before auth); KDF at 210k; `String` copies of the passphrase; dialogs held passwords in `rememberSaveable` | P2/P3 | `96bceff` |
| Assisted kilograms summed as tonnage in the summaries SQL — History/Home showed 720 kg where the session summary said 0 | P1 | `2d7a6d2` |
| Coach-suggested lbs weights stored un-quantised (2.2679618… kg step) — false "Heaviest ever" for repeating a weight, reps-at-weight records never accumulated | P1 | `2d7a6d2` |
| Composer's "Create «lift»" row was wired to `Unit` | P1 | `2d7a6d2` |
| Two filled Volts on Home while the missed-work prompt was up; prompt could burn the week's one decision mid-workout (Home and Plan) | P2 | `2d7a6d2` |
| Custom-week back destroyed a multi-day draft with no confirm | P2 | `2d7a6d2` |
| Deliberately cleared notes resurrected mid-debounce (Active Workout and Session Detail) | P2 | `2d7a6d2` |
| Start-sheet discard confirm always said zero logged sets | P2 | `2d7a6d2` |
| Live-bar finish/discard failures were log-only; cardio discard reported "gone" on failure and wiped the timer baseline; a stale cardio route silently rendered whichever session was live | P2/P3 | `2d7a6d2` |
| Home error banner undismissable | P3 | `2d7a6d2` |
| 30-day insight window's lower bound froze at pipeline build — a long-lived process analysed 37+ days | P2 | `296ba8c` |
| Cold start decoded two 768×768 stills on the main thread (~50–150 ms) | P2 | `296ba8c` (bind moved in `b14c69a`'s app-class edit) |
| Rest dock's pulse animation requested a frame every vsync for the whole session while idle | P3 | `296ba8c` |
| Release workflow never checked `appVersionCode` (and the local floor file made the check unfailable) — a forgotten bump ships a release Obtainium ignores | P0 (release lane) | `fb76da6` |
| CI/release pinned action majors that do not exist (`checkout@v7`, `upload-artifact@v7`) — CI stays red the day a runner exists | P1 | `fb76da6` |
| CI ran none of the 17 repo checkers and lint was non-blocking on a stale rationale; the `debug-live` phone lane had no automated producer; the Gradle wrapper was the one unpinned byte-stream | P2 | `fb76da6` |
| `check-state-members` corrupted its index for a file's second state class (verified by execution); three checkers' exit codes wrapped at 256 findings | P1/P3 | `fb76da6` |
| Free-text log messages (user-authored titles, internal paths) reached release logcat through the un-stripped `AppLog` seam | P2 | `bc0f540` |

## Verified sound (do not re-audit as suspicion)

One-live-activity enforcement (transactional, both lanes); top-set
progression basis and e1RM math; warmup exclusion; set delete/undo and
draft recovery; finish path ordering; backup envelope AEAD construction
(AES-256-GCM, AAD-bound parameters, per-wrap salt/nonce); restore
choke-point + validator + verified safety snapshots + journal atomicity;
migrations (no destructive fallback, schemas match); manifest exposure
(nothing exported but the launcher, OS backup fully excluded);
`drive.file` scope with in-memory tokens; diagnostics redaction;
dependency verification ledger (612 pinned components, no bypasses);
one-shot navigation as state+ack everywhere; Start confinement; list
keys; back symmetry; the workout screen's tick isolation and stable keys.

## Deferred, with recommendation (the queue)

- **Coach hint fan-out (perf P1).** `readyForProgression` runs ~200
  sequential DAO round-trips per insights emission — 1–3× per logged
  set. Batch it: one query for last-session top sets across the
  routine's lifts, one for the RPE window. Same pass: cache hints on
  (routines, unit, last-set-id).
- **Second full pipeline per tab (perf P2).** Progress runs a cold copy
  of the whole insights pipeline for its window chip; History re-runs
  its own graphs. Fold the chip into the shared flow's snapshot stage.
- **Per-set write amplification (perf P2).** Summaries/last-logged
  aggregates re-scan all history per logged set; PR detection reloads a
  lift's lifetime sets per log. Aggregate queries (MAX per lift) and a
  windowed summary invalidation are the shape.
- **Rest poll sharing (perf P3).** `remainingSeconds` is a cold 5 Hz
  poll per collector (up to three at once); `shareIn` or align to the
  second boundary. The live-bar pipeline also ticks on routes where the
  bar is hidden.
- **MOVED rows in day math (scheduling P2).** `WeekBoard.summary`,
  `DayFill`, and `twoADayEpochDays` count MOVED rows: moved sessions
  double-count, the vacated day stays red forever, phantom two-a-day
  marks. Decide the fill semantics (vacated = resolved?) and filter.
- **`minutesOfDay` is elapsed-since-midnight (scheduling P2).** DST days
  shift the overdue threshold an hour; derive wall minutes from the
  zone instead.
- **ADAPT_WEEK is behaviourally KEEP_DATES (scheduling P2).** After
  `ensureWeek`, regeneration finds nothing to create. Either make it
  regenerate future PLANNED rows for changed rules or drop the choice.
- **Week rollover only at process start (scheduling P2).** A cached
  Monday process shows an empty board; `ensureWeek` should also run on
  resume/day-change.
- **MoveToToday id collision with a MOVED row (scheduling P3);
  previous-week PLANNED rows stuck invisible (P3).**
- **Validator gaps (data P2).** Unknown enum strings, missing
  `performedStart`, duplicate block/set/interval ids, and empty-list FK
  skips pass `prepareRestore` and explode mid-transaction (rolls back,
  but with finding-2's old message). Harden `BackupValidator`.
- **Bodyweight/blocks restore is two stores without a transaction
  (data P2); bodyweight backup rounds to 0.1 kg through display
  formatting (P3); restored PENDING reminders not scheduled until next
  launch (P3); ACTIVE activities counted in the confirm but filtered on
  restore (P3).**
- **`observeBestWorkingWeights` takes MAX(weightKg) for assisted lifts**
  — the most-assisted set reads as "best" (P3).
- **History `stale` flag has no UI consumer (P3); Plan-day cardio
  "already has cardio" panel keeps its dead hour chips (P3);
  full-screen-intent capability gate exists but is never called (P3);
  no `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` receiver
  (P3).**
- **ProGuard keeps whole logic packages un-obfuscated** where Gson needs
  only DTO field names (P3 hardening).
- **Coverage ratchet is unreachable** outside the owner's machine; add
  `jacocoTestReport` + `check-coverage.py` to CI once the runner exists,
  and floor `data.repository`/`reminder`/`insights` (P2).
- **Zero-test packages:** `data.mapper` (real logic, no tests at any
  level), `data.local.entity/relation`, `ui.reminders`, `ui.units`.

## Owner-side actions (nothing on this branch can close these)

1. **Assign a GitHub Actions runner** (account billing). Every guard
   above then runs on every push; today all 542 runs die in ~5 s before
   checkout. This is the single highest-leverage fix in the repo.
2. **Cut the pending drop**: versionCode 13 is staged; a Gradle-capable
   lane runs `testDebugUnitTest`/`assembleDebug` and publishes the
   `debug-live-*` pre-release — or, once the runner exists, pushing the
   tag does it via the new workflow.
3. **Physical TalkBack pass** (blocks Android Public Candidate) and the
   **Play rehearsal** (blocks Commercial RC), per P9.7/P12.4.

## Commands

- `tools/preflight.sh` — PASS. 1049 domain tests (JVM lane now also
  compiles `CardioTimerPersistence` + its tests). 0 findings from all
  static checkers, including the repaired `check-state-members`.
- `./gradlew testDebugUnitTest` / `assembleDebug` — **not runnable from
  this environment** (network policy blocks `dl.google.com`; no Android
  SDK; hosted CI has no runner). The Gradle gate belongs to the next
  Gradle-capable lane; the Robolectric suites touched here
  (`PendingOccurrenceTest`, `BackupEnvelopeTest` additions,
  `RestorePrepareTest` neighbours) run there.
