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

## Cleared from the queue (second pass, same day)

`96c2c6d` follow-up on this branch: MOVED rows vacate their day
(board fill, summary, two-a-day); wall minutes come from the zone
offset at the instant so DST days stop shifting the overdue threshold;
ADAPT_WEEK genuinely re-derives the remaining week from current rules
(hour changes land, removed rules retire, no past-day minting);
`BackupValidator` refuses everything the mapper would explode on
(unknown enums, Gson-null fields, duplicate block/set/interval ids,
orphan FK rows regardless of list emptiness); week rollover runs on
every resume; restored reminders are handed to the scheduler at
commit; the exact-alarm grant-change broadcast re-arms a persisted
rest; the full-screen intent attaches only when the API 34 gate is
open; assisted lifts are excluded from best-weight goals; bodyweight
backups round-trip at full precision; History says when it is showing
a stale read; Plan-day's cardio row is a readout when the day already
has cardio.

## Round two (same day): re-audit with fresh lenses

Five independent passes over the finished tree: an adversarial review of
the day's own diff, a light-surfaces audit (onboarding, Body, Library,
Exercise Detail, Goals, repair flows), a code-health review, a
product-gap analysis, and a verification pass over this ledger (verdict:
every Fixed and Sound row above HOLDS). Fixed in the follow-up commit on
this branch:

- **Reminder "Start" left the notification in the shade (P1).** The
  activity-PendingIntent rewrite fixed the launch but dropped the cancel
  the old receiver path performed — actions never auto-cancel, so the
  started session's reminder kept live Snooze/Move/Skip buttons; a later
  Skip tap marked the running plan row SKIPPED. MainActivity now cancels
  by occurrence id when it consumes a Start launch.
- **Same-boot heuristic misread a double reboot (P2).** "elapsedRealtime
  has not gone backwards" is true after a genuine reboot whenever the new
  boot's uptime passes the old start. Both timer persistences now stamp
  `Settings.Global.BOOT_COUNT` at save; when both sides carry it, the
  counter alone decides (rest and cardio), and the clock heuristics
  remain only for rows written by older builds.
- **Body's Month window under-fetched (P2).** The SQL lower bound was a
  rolling 30 days but "this month" starts at civil midnight on the 1st —
  on the 31st of a 31-day month, day-1 sessions silently vanished from
  the heat while `windowStartMs` still claimed them. Fetch width is now
  32 days; every consumer re-filters against its own window.
- **Backup exports silently eaten while busy (P2).** Both export paths
  consumed the held password and the one-shot plaintext approval before
  `runBackupAction`'s busy check; a concurrent Drive action at
  picker-return time dropped the export with no file and no message. The
  busy case now refuses aloud before consuming anything.
- **Composer arm race (P2).** A fast save could read `heldOccurrenceId`
  before the init transfer out of PendingOccurrence landed, saving the
  activity with no plan link after the store was already cleared.
  `confirmDraft` now joins the transfer job first.
- **Start sheet started every scheduled cardio as a generic Run (P2).**
  StartOptions hardcoded `CardioType.RUN`/"Cardio" where Home and Plan
  resolve `ScheduleKind.cardioTypeOrRun(rule.templateId)`.
- **Live-bar polish (P3).** The bar's action error was sticky (nothing
  called `onActionErrorShown`); it now auto-dismisses after a dwell. The
  cardio discard branch was an unguarded suspend call — an exception
  crashed the process and the failure path cleared the timer baseline of
  a session that still existed; it is now guarded like the workout branch.
- **Release-log redaction armed late (P3).** `AppLog.redactMessages` was
  set after container construction — after the database-open/migration
  logging it exists to redact. It is now the first line of `onCreate`.
- **debug-live workflow (P3).** `workflow_dispatch` from a `debug-live/*`
  branch would have published a release despite the artifact-only
  comment; publish is now gated on push events. The branch glob is
  single-level so a nested branch cannot mint a slash-carrying tag.
- **Library status banner never cleared (P3).** The ViewModel's message
  survived its banner's dwell, so repeating the same action ("Added X to
  Y." twice) deduped in the StateFlow and showed nothing.
- **Week-shrink trimmed in Monday order (P3).** `withDaysPerWeek` now
  orders from the lifter's stored week start — a Sunday-week lifter
  dropping to 3 days no longer loses Sunday first.

Ledger corrections from the verification pass: the deliveries-REPLACE
note in the Fixed table is scoped to rows without children (correct as
shipped), and the "30-day insight window" row's remaining over-fetch is
superseded by the 32-day month fix above.

## Deferred, with recommendation (the queue)

- **Coach hint fan-out (perf P1)** — **done 30 August 2026.**
  `readyForProgression` reads every requested lift's finished working
  sets in one query and picks last-session top sets / the RPE window in
  Kotlin. Insights caches the hint list on (routines, unit, lighter week,
  finished summaries), so an in-progress log does not repeat the pass.
- **Second full pipeline per tab (perf P2)** — **done 30 August 2026.**
  Progress collects the shared without-plan assembly and retargets only
  `MuscleLoadCalculator.snapshot` when the window chip moves. History
  still has its own graph reads (not this packet).
- **Per-set write amplification (perf P2)** — **done 30 August 2026.**
  `recordsBrokenBy` uses one MAX aggregate (plus in-session earlier
  sets) instead of the lift's lifetime graph. Finished summaries and the
  32-day session graph gate on `FinishedWorkGeneration`, so logging into
  an in-progress session does not re-aggregate history.
- **Rest poll sharing (perf P3)** — **done 30 August 2026.**
  `remainingSeconds` is `shareIn`'d and wakes on the next whole-second
  boundary. The live bar unsubscribes the rest poll and the 1 Hz elapsed
  ticker on routes that already own the session.
- **MoveToToday id collision with a MOVED row; previous-week PLANNED
  rows stuck invisible** — **done 30 August 2026.** Relocate mints a
  distinct id when the canonical row is already `MOVED`. Still open
  includes the previous week, so Monday still lists Sunday.
- **Bodyweight/blocks restore is two stores without a transaction
  (data P2); ACTIVE activities counted in the confirm but filtered on
  restore (P3).**
- **ProGuard keeps whole logic packages un-obfuscated** where Gson needs
  only DTO field names (P3 hardening).
- **Coverage ratchet is unreachable** outside the owner's machine; add
  `jacocoTestReport` + `check-coverage.py` to CI once the runner exists,
  and floor `data.repository`/`reminder`/`insights` (P2).
- **Zero-test packages:** `data.mapper`, `ExerciseRepository`, and the
  reminder receivers/worker — **done 30 August 2026.** Mapper tests pin
  enum wire names and round-trips. Exercise create/rename/delete/search
  and reminder Start/Snooze/Move/Skip plus the worker's reread path now
  fail the JVM lane instead of the phone. Still untested:
  `data.local.entity/relation`, `ui.reminders`, `ui.units`.

Added by round two (all need a compiler or a UX decision):

- **Onboarding "Use this plan" is not idempotent (P2/P3)** — **done 30
  August 2026.** Routine/pin writes run in one Room transaction. A
  failed attempt drops the routines it created so the invited retry
  cannot mint a second copy. A finished program is still never deleted.
- **`ui/goals` is a dead package** — **done 30 August 2026.** Package,
  `GoalCopy`, Goals tests and preview deleted. `goalDao` /
  `GoalRepository` stay for the backup format.
- **Dead code inventory** — **done 30 August 2026.** PlanViewModel start
  spine, `beginGuided()`, `LinkRow`, dead `HomeUiState` fields, and the
  unused `CompactLiftRow` composable are gone. `CompactTargetFields`
  stayed with the live session-lift strip.
- **Start-spine triplication** — **done 30 August 2026.** `StartOccurrence`
  sits beside `StartTrainingDay`. Home relocates leftovers then maps the
  sealed outcome; the start sheet maps it directly. Plan's start spine
  was already deleted. `StartLiveCardio` is the cardio-start ritual.
- **BodyweightWheel unit toggle drifts the value** — **done 30 August
  2026.** Nearest whole numeral from the raw conversion; the parked
  page after a unit change is not a commit.
- **Cardio onboarding can show a lift-catalog error over a valid cardio
  plan** — **done 30 August 2026.** Cardio preview does not retry the
  lift catalog. Trend charts are still index-spaced so layoffs vanish
  from the x-axis (P3, arguably deliberate).
- **Build fat** — **done 30 August 2026.** Gson encodes the three
  toolchain pin files. kotlinx.serialization is not shipped; 1.8.1 is
  still the Room 2.8 ceiling. Five outlined marks are vendored so
  `material-icons-extended` is not on the debug APK.
- **SettingsViewModel (~900 lines):** extract the backup/Drive/restore
  state machine (~475 lines) into its own coordinator next time it is
  touched. Common.kt's rest-dock family (~500 lines) is a mechanical
  file split.
- **Accepted, documented risks:** the restore-journal fingerprint format
  change means a journal left open by the *previous* build reads as
  rolled back after an update mid-restore (one-in-a-million ordering;
  recovery is honest, prefs re-apply is not); stricter FK validation can
  refuse a historical backup file with dangling references the old
  validator tolerated (Room FKs make genuine files safe).

## Owner-side actions (nothing on this branch can close these)

1. **Assign a GitHub Actions runner** (account billing). Every guard
   above then runs on every push; today all 542 runs die in ~5 s before
   checkout. This is the single highest-leverage fix in the repo.
2. **Cut the pending drop** — **done 1 September 2026.** Live test 16
   was `debug-live-2026-09-01`. Live test 17 is the current Obtainium drop:
   pre-release `debug-live-2026-09-01-2` carries
   `PersonalTrainer-1.0.0-debug.apk` (versionCode 17,
   `1.0.0+debug.17`). `tools/released-version-code.txt` stays at 1
   (gym-floor `appVersionCode` floor). Hosted Actions still have no
   runner; the drop was hand-published.
3. **Physical TalkBack pass** (blocks Android Public Candidate) and the
   **Play rehearsal** (blocks Commercial RC), per P9.7/P12.4.

## Commands

- `tools/preflight.sh` — PASS. 1066 domain tests (JVM lane now also
  compiles `CardioTimerPersistence`, `BootSession` + their tests). 0
  findings from all static checkers, including the repaired
  `check-state-members`.
- `./gradlew testDebugUnitTest` / `assembleDebug` — PASS on 1 September
  2026. Live test 17 is `debug-live-2026-09-01-2` (versionCode 17).
  Live test 16 remains `debug-live-2026-09-01`.
