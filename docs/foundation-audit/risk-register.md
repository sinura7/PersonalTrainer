# Ranked findings and acceptance criteria

This register ranks impact, not implementation order. The phase-two roadmap should group
accepted findings by dependency, risk, and user value.

## 1. Severity and confidence

| Severity | Meaning |
|---|---|
| Critical | Core trust promise fails, data is endangered, or agreed product cannot exist |
| High | Primary journey, scalability, privacy, or quality is materially compromised |
| Medium | Recurring friction, architectural drag, or meaningful consistency gap |
| Low | Bounded polish or documentation debt |

| Confidence | Meaning |
|---|---|
| Verified | Reproduced at runtime or by executed tool |
| High | Direct code/config/document evidence |
| Medium | Strong inference requiring device/user validation |
| Low | Hypothesis only |

## 2. Critical findings

### FND-001 — Modern Android cannot rely on the exact rest alarm

- **Type:** Defect
- **Severity:** Critical
- **Confidence:** High
- **Evidence:**
  - [`RestTimerAlarmScheduler.kt`](../../app/src/main/java/com/sinura/personaltrainer/timer/RestTimerAlarmScheduler.kt)
    calls `setAlarmClock()`, then `setExactAndAllowWhileIdle()`, and swallows both exceptions.
  - [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) declares neither
    exact-alarm permission, and the app does not check for a system exemption.
  - On Android 12–12L, apps targeting API 31+ require `SCHEDULE_EXACT_ALARM` or a power-save
    exemption. On Android 13+, eligible apps targeting API 33+ may instead use
    `USE_EXACT_ALARM`:
    [official API reference](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent)).
  - Lint reports two `MissingPermission` warnings.
  - `RestTimerAlarmReceiver` does not verify that the persisted elapsed-realtime deadline is
    due before calling completion, despite the scheduler comment claiming that it does.
  - Android clears alarms on reboot and no boot receiver reschedules the persisted timer.
- **Impact:** The feature specifically built to ring with the screen off can fall back to an
  in-process service tick that is not guaranteed through sleep/Doze.
- **Acceptance criteria:**
  - chosen strategy is valid under Android and Play policy;
  - fresh-install API 31+ behavior is explicit when exact access is absent;
  - a 60-second rest completes on time with screen off and forced Doze;
  - stale/in-flight alarms cannot finish an extended or newly started rest early;
  - reboot behavior is implemented and tested or documented as unsupported;
  - automated lint no longer reports an unhandled exact-alarm permission path;
  - documentation stops claiming `setAlarmClock()` is exempt.

### FND-002 — The data model cannot represent the agreed fitness product

- **Type:** Target gap
- **Severity:** Critical
- **Confidence:** High
- **Evidence:** `WorkoutSession` and `SetLog` express strength sets only; no activity modality,
  cardio metric, schedule occurrence, or typed activity block exists.
- **Impact:** Cardio, mixed typed blocks, and independent timed schedule occurrences cannot be
  added honestly as isolated screens. Multiple finished strength sessions can already share a
  date, but they do not satisfy that target.
- **Acceptance criteria:**
  - one activity envelope represents strength-only, cardio-only, and mixed sessions;
  - cardio supports typed duration/distance/effort fields without fake exercise rows;
  - a day supports at least two independent occurrences;
  - records retain source, performed time, time zone, and live/backdated/imported origin;
  - all core queries and export/import understand the new types.

## 3. High findings

### FND-003 — The core workout orchestration is untested

- **Type:** Validation gap
- **Severity:** High
- **Confidence:** High
- **Evidence:** No `ActiveWorkoutViewModelTest`; no Compose UI tests; the ViewModel and screen
  are the largest primary-flow units.
- **Impact:** Domain tests cannot prove state transitions, one-shot effects, timer
  coordination, error clearing, or navigation.
- **Acceptance criteria:** Behavioral tests cover load, set validation/write, PR, rest start,
  edit/delete/undo, leave, finish, discard, process restoration, and one-shot navigation;
  at least one Compose/device smoke covers type weight → log → rest → finish.

### FND-005 — Summary volume contradicts durable history

- **Type:** Defect
- **Severity:** High
- **Confidence:** Verified
- **Evidence:** Runtime 100 lb × 5 produced 501 lb in Summary and 500 lb in Summary subtotal,
  Session Detail, and Exercise Detail. Summary uses `roundToInt`; shared formatting uses
  ties-to-even `round`.
- **Impact:** The save-confirmation screen disagrees with the record it claims to summarize.
- **Acceptance criteria:** One shared formatter serves every aggregate; property tests cover
  unit conversion and `.5` boundaries; all session surfaces agree byte-for-byte.

### FND-006 — History erases workout identity at a supported phone width

- **Type:** Defect
- **Severity:** High
- **Confidence:** Verified
- **Evidence:** At 360 dp, populated History rendered title/date as `…`; fixed metric columns
  and menu remained visible.
- **Impact:** Users cannot identify a row before opening or repeating it.
- **Acceptance criteria:** At 360 dp and font scales through 2.0, session title remains
  meaningful; metrics wrap/stack/collapse first.

### FND-007 — Scheduled activities and reminders are absent

- **Type:** Target gap
- **Severity:** High
- **Confidence:** High
- **Evidence:** Current alarms/notifications are rest-only; schedule slots have no time,
  occurrence, duration, reminder, or action state.
- **Impact:** The agreed timed-occurrence and actionable-reminder product direction cannot be
  delivered by the current schedule model.
- **Acceptance criteria:** Users can create timed recurring or one-off occurrences, including
  two/day; reminders expose Start, Snooze, Move, and Skip/Rest; quiet hours and opt-out exist;
  only one missed-session check-in fires.

### FND-008 — Backdated activity creation is absent

- **Type:** Target gap
- **Severity:** High
- **Confidence:** High
- **Evidence:** Sessions are created “now”; completed sets can be repaired but session
  performed date cannot be authored.
- **Impact:** Users cannot recover omitted workouts or migrate history manually.
- **Acceptance criteria:** Create/edit a past activity with local date/time/time zone; heat,
  calendar, goals, aggregates, and export attribute it consistently.

### FND-009 — Measurable goals and annual progress are absent

- **Type:** Target gap
- **Severity:** High
- **Confidence:** High
- **Evidence:** `TrainingGoal` is a generation/ranking preference, not a target record. UI
  horizons top out at month/12-week block plus per-lift lifetime.
- **Impact:** The app cannot keep a user accountable to explicit outcomes across the requested
  day/week/month/year model.
- **Acceptance criteria:** Initial goal types cover schedule adherence, sessions/active
  minutes, lift target, cardio duration/distance, optional bodyweight; year and all-time
  progress use comparable windows.

### FND-011 — Android Auto Backup and plaintext export have no settled privacy posture

- **Type:** Architecture debt
- **Severity:** High
- **Confidence:** High
- **Evidence:** `android:allowBackup="true"`; `backup_rules.xml` is unreferenced; Room and
  DataStore have no app-layer encryption; user-exported and Drive JSON have no app-layer
  encryption. Android’s standard backup transport is OS-encrypted, but the app has not
  explicitly chosen what it should include. App-private safety and pre-migration snapshots
  are eligible by default and can retain older/deleted history.
- **Impact:** Data is eligible for a channel the product says it does not rely on, while
  user-controlled JSON files expose complete history/bodyweight if copied or shared.
- **Acceptance criteria:** Threat model covers device theft, Auto Backup, file leak, and Drive;
  manifest/rules match the decision; export clearly warns or offers encryption; privacy
  documentation is accurate.

### FND-012 — Backup is not optional account sync

- **Type:** Target gap
- **Severity:** High
- **Confidence:** High
- **Evidence:** Drive uploads/downloads one whole JSON document with destructive replacement;
  entities lack sync revisions/tombstones/conflict policy.
- **Impact:** Running backup more often cannot safely produce multi-device continuity.
- **Acceptance criteria:** Local-first outbox, incremental protocol, per-entity conflict
  policy, tombstones, encryption, opt-in identity, offline queue, and explicit sync status;
  core use remains available signed out.

### FND-013 — Database read faults can masquerade as empty history

- **Type:** Defect
- **Severity:** High
- **Confidence:** High
- **Evidence:** `FlowGuards.orLogAndFallback` converts a query failure into an empty list.
- **Impact:** Corruption or disk failure can render “No sessions yet,” encouraging new writes
  while hiding a serious recovery state.
- **Acceptance criteria:** Data-read failures produce explicit degraded/error state with
  retry/export/recovery guidance; no primary screen presents a fault as legitimate emptiness.

### FND-014 — Field failures are invisible off-device

- **Type:** Validation/operations gap
- **Severity:** High
- **Scope:** Commercialization and field operations
- **Confidence:** High
- **Evidence:** `AppLog` is local only; no crash reporting or user diagnostic export exists.
- **Impact:** Distributed failures cannot be correlated or diagnosed remotely.
- **Acceptance criteria:** Before public distribution, choose privacy-preserving opt-in crash
  reporting or a user-triggered diagnostic bundle; audit logs for fitness/bodyweight content.

### FND-014A — Restore promises a safety copy that is best-effort and unreachable

- **Type:** Defect/data risk
- **Severity:** High
- **Confidence:** High
- **Evidence:**
  - Settings says a copy “is saved on this phone first.”
  - `writeSafetySnapshot()` catches snapshot exceptions and returns null.
  - Restore proceeds with wholesale deletion/replacement when that path is null.
  - `safetySnapshotPath` is propagated in result objects but never displayed or consumed by a
    recovery flow.
- **Impact:** A valid but wrong/stale restore can replace newer history without the promised
  fallback, especially when disk pressure is the reason the snapshot failed.
- **Acceptance criteria:** Either a verified snapshot is a restore precondition, or the
  confirmation copy is honest about best effort; users can list, inspect, and restore retained
  snapshots; snapshot failure is explicit before deletion.

### FND-014B — Catalog-only backups bypass the empty-destructive guard

- **Type:** Defect/data risk
- **Severity:** High
- **Confidence:** High
- **Evidence:** `BackupSummary.isEmpty` includes exercise count. Every normal export includes
  the seeded built-in catalog, so a backup with no routines, sessions, or sets is still
  “non-empty” and can replace a phone with authored history. `hasLocalData()` also ignores
  DataStore-only bodyweight and completed-block history.
- **Impact:** The guard described as the last defense against an empty wipe does not measure
  user-authored data on either side.
- **Acceptance criteria:** Destructive validation compares authored-data counts separately
  from built-ins; the final confirmation names incoming and local sessions/sets/routines,
  bodyweight entries, and blocks; catalog-only replacement of authored history requires an
  unmistakable second confirmation or is refused.

### FND-014C — Overall restore is neither atomic nor serialized with workout start

- **Type:** Architecture/data risk
- **Severity:** High
- **Confidence:** High
- **Evidence:** Room deletion/replacement commits before DataStore preference restoration;
  catalog reconciliation runs afterward in another transaction. The live-session check also
  occurs before the maintenance lock, while workout start does not share that lock.
- **Impact:** The UI can report restore failure while the phone already contains replaced
  Room data paired with unchanged preferences, fully restored preferences with an
  unreconciled catalog, or a just-started live session deleted by the replacement.
- **Acceptance criteria:** Restore has an explicit staged/commit protocol or recoverable
  journal across Room, preferences, and reconciliation; start/restore share serialization;
  every failure result states the actual committed state and offers deterministic recovery.

## 4. Medium findings

| ID | Type | Finding | Confidence | Acceptance criteria |
|---|---|---|---|---|
| FND-004 | Test defect | Device lane runs 14 tests but its smoke assertion hardcodes the release package; 13 substantive tests pass | Verified | Assertion derives the debug target package and the lane is green |
| FND-010 | Performance risk | Complete nested history is materialized and repeatedly processed; scale impact is unmeasured | High | Benchmark 500 sessions/15,000 sets; then bound queries/projections and paginate if budgets fail |
| FND-015 | UX defect | Notification-denial banner dominates the first workout viewport | Verified | Set entry remains visible or banner collapses after first explanation |
| FND-016 | UX defect | Routine names and target-load labels compress at 360 dp | Verified | Identity remains readable; field says Target weight with separate unit |
| FND-017 | Product mismatch | Missed schedule items silently shift; target asks once before adapting | High | Explicit Move all / Adapt week / Keep / Skip decision with recurrence unchanged |
| FND-018 | Architecture debt | One global live session conflicts with independent morning/evening activities | High | Product explicitly keeps one live session or supports independent active timers/states |
| FND-019 | Architecture debt | Historical bodyweight and blocks are encoded DataStore strings | High | Queryable historical records move to structured storage; DataStore keeps preferences |
| FND-020 | Portability debt | JVM-pure domain imports `java.time`, `java.text`, and `Locale` | High | Platform-neutral clock/date/format ports or multiplatform libraries; shared tests compile for iOS |
| FND-021 | Accessibility gap | No TalkBack/UI automation across 13 screens | High | Full manual matrix plus semantics tests for Home and Active Workout |
| FND-022 | Accessibility gap | Large-text adaptation is limited to selected paths | High | All primary screens pass 360 dp × font 2.0 without lost identity/action |
| FND-023 | Accessibility defect | Body-map hotspots can be below 48 dp | High | Reliable rows are announced and map targets meet policy or are explicitly secondary |
| FND-024 | Contrast risk | `TextTertiary` is 3.23:1 at 11–12 sp | Verified | No essential state/instruction uses it as sole text; use stronger token where load-bearing |
| FND-025 | Test gap | 10+ ViewModels lack dedicated behavior tests | High | Risk-ranked coverage starts with workout, session repair, routines, library, summary, live bar |
| FND-026 | CI debt | Lint has 64 warnings and is non-gating | Verified | Classify warnings; resolve correctness/security items; baseline/waive remainder; define gate |
| FND-027 | Dependency debt | 2024 Android stack is materially behind 2026 stable versions | Verified by lint | Upgrade in tested increments; record compatibility/migration checks |
| FND-028 | Dependency debt | Google Sign-In API use is deprecated | Verified compiler warning | Move Drive auth to supported credential/authorization API before commercialization |
| FND-029 | Release debt | Release is unminified/unshrunk and versionCode remains 1 | High | Record release size; enable/test hardening or document rationale; enforce version bump |
| FND-030 | Privacy/compliance | No privacy policy, data-safety narrative, or commercial support posture | High | Required before sharing beyond trusted personal use |
| FND-031 | Product clarity | Plan command vocabulary can stack Suggest/Replay/Tune/Lighter/Use | Medium | New-user test completes recovery without glossary; progressive disclosure/copy if not |
| FND-032 | Discoverability | Library is intentionally off-tab and can be hard to find initially | Medium | Test first-week discovery; strengthen contextual link, not a fifth tab |
| FND-033 | UX architecture | Body’s read-only figure pushes recommendations below fold | Verified | Test comprehension/action discovery; compact summary if evidence supports it |
| FND-034 | Component debt | Duplicate notes/group/row/numeric patterns can drift | High | Define shared contracts while preserving legitimate live/history variants |
| FND-035 | Component debt | `ExercisePickerSheet` has 11+ mode parameters | High | Cohesive state/config plus event API; mode tests |
| FND-036 | UI coupling | Recommendation navigation dispatch lives in UI helper | High | UI emits intents; route mapping becomes behavior-tested |
| FND-038 | Performance risk | Backup reads and encodes the entire database at once | High | Large-data timing/memory budget; streaming or staged snapshot if exceeded |
| FND-039 | Time semantics | Everything uses `ZoneId.systemDefault()` | High | Capture activity/schedule time zone and define travel/backdating policy |
| FND-040 | Observability | Central insights source has no direct runtime-source test package | Medium | Tests cover sharing, failure isolation, cancellation, and recompute triggers |

## 5. Low findings and explicit decisions

| ID | Type | Finding or decision | Acceptance |
|---|---|---|---|
| FND-041 | Documentation | Root `README.md` has stale heat windows/package paths, calls Drive backup “sync,” and ignores enabled Android Auto Backup | Current behavior and channel distinctions documented |
| FND-042 | Documentation | Operational docs contain `main`/historical branch references | Active runbooks consistently use `trunk`; archives labeled |
| FND-043 | Visual regression | Only seven previews, all token/thumb galleries | Critical page/state previews or screenshot tests |
| FND-044 | Design consistency | Stock Switch/FAB/menus remain visible outliers | Verify theme resolution; skin only where evidence shows mismatch |
| FND-045 | Decision | Dark-only theme | Keep documented; do not treat light theme as a defect |
| FND-046 | Decision | Library remains pushed, four tabs remain | Preserve unless new user evidence invalidates IA |
| FND-047 | Decision | Core rule engine remains local and deterministic | API may explain; it does not silently control plans |
| FND-048 | Decision | Local recording and export remain available without account/subscription | Enforce at use-case and entitlement boundaries |

## 6. Preserved strengths

These are foundation assets, not work to reopen casually:

- no destructive Room fallback;
- intentional foreign-key semantics;
- session target snapshots;
- structurally validated, transactional Room table replacement with a best-effort, currently
  non-user-recoverable safety snapshot;
- local file backup independent of Google;
- pure deterministic progression/coach/schedule rules;
- 758 passing Gradle tests and 647 passing plain-JVM tests;
- one shared insights pipeline off the main dispatcher;
- constructor-injected ViewModel dependency seam;
- one live-session chrome concept;
- one dominant action color;
- coherent semantic design tokens;
- weight stored in kilograms with explicit display unit;
- editable finished sets that preserve attribution time;
- generated exercise imagery with one anatomy source.

## 7. Audit sign-off criteria

The audit phase is complete when:

- every surface and major state is mapped;
- current facts, target gaps, and hypotheses are separated;
- runtime contradictions are evidenced;
- automated baseline results are recorded honestly;
- architecture flows and future boundaries are explicit;
- each ranked finding has acceptance criteria;
- a later roadmap can accept, reject, group, and order findings without reopening discovery.

This package satisfies those documentation criteria. Physical-phone, TalkBack, Doze,
production Drive, and synthetic multi-year performance gates remain deliberately open
validation items for the roadmap.
