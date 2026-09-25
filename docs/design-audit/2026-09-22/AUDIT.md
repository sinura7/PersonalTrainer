# Temper: whole-app audit, visual and backend

Review date: 22 September 2026. Source baseline: `6558564` on `trunk`. This is
the phase audit owed after a week of major work: Temper Account sync
(21 September), CoachEngine (21 September) and five rounds of live-workout
redesign. Main code grew from 70,506 lines (11 September) to about 94,000, and
Room went from v4 to v7.

It is a findings record and a delivery order, not an ADR. Decisions it needed
were taken by the owner on 22 September and are recorded below. Where the code
has moved on, the status column says so.

## Method and limits

- Three read-only explorers: every tab, the live workout and design system,
  and the backend. Then a draft. Then two independent reviewers: a plan critic,
  and an adversarial fact-checker who confirmed 12 claims, found 3 partly true,
  and refuted 1, which was dropped (History's bodyweight showing zero).
  The main claims were re-checked by hand, and the first sync claim by a
  running test.
- No phone, emulator, TalkBack or physical touch was used. Visual claims come
  from code, the 16 September design audit
  ([2026-09-16/AUDIT.md](../2026-09-16/AUDIT.md), D01–D17) and, from packet Q1
  on, JVM renders written by the test run.
- Severity: **P1** = protect data or a first-run blocker; **P2** = next
  correctness or polish work; **P3** = worth doing, not urgent.

## Owner decisions (22 September)

| Topic | Decision |
|---|---|
| Order | Visual and backend work interleaved per area |
| Existing redesign plan | Update and re-order F4–F11, do not replace it |
| Delivery | Small packets; JVM gate; an Obtainium drop per visible packet; code-only packets ride along with the next visible drop |
| Signed in | Yes, on Temper Debug. Gym-floor Temper has no sync configuration and is unaffected |
| Sync | Make it safe, then finish it (ADR-028's full cloud target, including live strength) |
| Server | Temper's Supabase project is connected; every server change is shown to the owner first |
| Delete account | Hidden until it truly works; the app says how to request deletion |
| Screen proof | JVM/Robolectric renders count as evidence; emulator goldens retire as a gate |
| Body figure | Redrawn as vector muscles: one geometry for the art, the heat and the taps |
| Phone text size | Default (1.6 and 2.0 are still tested) |
| Conflict rule | Proposed in ADR-031 §4: when two phones change the same row, the later save wins. **The owner confirms it before sync resumes.** |

## Findings

### Sync (P1: data safety)

| # | Finding | Status |
|---|---|---|
| S-1 | Pull destroyed local rows. Activity sessions were deleted and re-inserted; blocks, templates and routines used `REPLACE`. SQLite deletes the old row, and the foreign keys cascade: activity sets gone, routine lifts gone, finished `workout_sessions.routineId` set to NULL. Sets used `ABORT`, so a row already present threw and stalled every later table on every pass. Proven by a running test before the fix. | Sync **paused** (S0a, #380). Pull writes in place (S0b, #383). |
| S-2 | The pull cursor trusts phone clocks: `gt` on `updated_at_ms` with no tiebreaker skips tied rows; tombstones keep the old `updated_at_ms`, so deletes never reach other devices; the server upsert is unconditional (last arrival wins). | Open: S1 (tombstone stamp), S2a/S2b (server change time, composite cursor, conditional upsert). |
| S-3 | The outbox is written after the save commits, not in the same transaction (ADR-009 §15). If queuing throws, the user sees "Could not save" and a retry duplicates the session. Changes made while the auth session is loading or offline are never queued. | Open: S1. |
| S-4 | Four delete enqueuers have no callers (`enqueueRuleDelete`, `enqueueOccurrenceDelete`, `enqueueTemplateDelete`, `enqueueBodyweightDelete`), so deleting a plan rule, a plan day, a template or a weigh-in never reaches the server. | Open: S1. |
| S-5 | One bad upload blocks the whole queue forever. WorkManager `APPEND_OR_REPLACE` retries without limit; HTTP calls have no timeouts. | Open: S1 (quarantine), S4 (WorkManager hygiene, timeouts). |
| S-6 | Live strength workouts (`workout_sessions`, `session_exercises`, `set_logs`) are never synced, yet PRIVACY.md said they were. | Copy made honest (S0a). Sync of strength history: S3a/S3b. |
| S-7 | Account deletion always failed: blocking `HttpURLConnection` on the main thread, and after wiping the cloud tables it called `DELETE /auth/v1/user`, which Supabase does not offer to a signed-in user. | Button hidden, deletion request route shown (S0a). Server-side delete: S2a/S2b. |
| S-8 | Server DDL and RLS are missing from the repository for 9 of 17 tables, and pulls send no `user_id` filter. | Open: S2a. |
| S-9 | Sign-out keeps the pull cursors; sync is not serialised with restore; a second phone's default settings overwrite real ones; tokens sit in plaintext SharedPreferences. 33 sync tests, none covering these paths. | Open: S1, S3b, S4. |
| S-10 | Governance: shipped sync violates ADR-009 §14–15, which was never amended; FOUNDATION_PROGRAM still said Phase 11 was "correctly not started". | ADR-031 (X1). |

Found while fixing S-1 and left for S1: a pulled occurrence whose rule is
missing, or a routine lift whose exercise is missing, still fails its foreign
key and stops the tables after it; a table's cursor is saved only after the
whole table; a custom lift kept on this phone can be re-uploaded and bring a
server tombstone back; set timestamps use `completedAtMs`, so a set uploaded
late can fall behind another phone's cursor; nothing serialises two passes.

### First launch (P1)

| # | Finding | Status |
|---|---|---|
| L-1 | The save-posture chooser let taps, TalkBack and Back through to Home; in landscape or at large text two of its three choices were unreachable. | Fixed (Q1). |
| L-2 | Choosing Account or Drive started the permission walk at once, so its dialogs covered the sign-in form. | Fixed (Q1): the walk waits until Settings is left. |

### Coach (P2)

| # | Finding | Status |
|---|---|---|
| C-1 | The goal set in Settings never reaches the workout (`workoutCoachSuggestion(coachPrefs = DEFAULT)`). | Open: W1b. |
| C-2 | The engine runs three times per tap, and `RuleTrace.generatedAtMs` defeats equality, so the flow re-emits on every tap. | Fixed (W2c, 25 September 2026). What W2c found: the Log asked once per entry change (a weight step included) and re-issued its call; the rest page asked twice a second while a rest ran. Now the Log asks only when what the coach reads changes (`CoachKey`: the inputs with the clock at zero, plus the coach settings), the page only when that or the rest of its floor changes, and the page's second, undrawn call is gone. |

### Tabs

| Tab | Findings | Status |
|---|---|---|
| Home | D01 "Trained today" on past dates. Finished blocks not tappable (D14). Week-strip cells about 43 dp wide at 360 dp. No read-error state. | D01 fixed (Q1). The rest: F4. |
| Body | D05 figure and heat don't line up. D06 sizing ignores the live bar. The legend doesn't wrap. | F5a–c. |
| History | D07 two period models. The period is lost on process death. Mixed sessions titled "Cardio" in one view and "Workout" in another. The set editor uses a different effort control. | F6a–b. |
| Plan | Routines hidden behind a toggle, delete only by long-press. "Programs" vs "routines". Dead UI (`PreferenceBlock`, `PlanLighterChip`, `PlanRecoveryCommands`). `RoutineEditorViewModel` is 1,438 lines. | F7a–b. |
| Library | "No matches" offers only "Create lift". | F8b. |
| Cardio | The composer has no header or back; backdating moves one day per tap. | F9. |
| Settings | 11 rows in unnamed groups; three save rows; static subtitles; reused icons; two ways to regenerate a week; three heading styles; no loading state; `BackupCoordinator` is 985 lines. | F10a–c. |
| Cross-tab | Four hand-built tab headers; two overlapping start sheets; 10 of 21 ViewModels lose screen state on process death. | Spread across F4–F10. |

### Live workout and design system (P2)

- Visible: no visible switch-exercise control (D09); the numbers don't invite
  typing (D10); two "Add set" buttons; the evidence chip is under 48 dp; chips
  are announced twice to TalkBack. W1a–b.
- Churn debris: about 320 dead lines in `SetEntryPanel`, dead branches in
  `FloorInstrumentBar`, 6 unused components, about 10 unused ViewModel
  functions, `FloorCompactChrome` constants only tests read. W2a.
- `ActiveWorkoutViewModel` is 2,541 lines with a 23-field state and a 13-stage
  `combine`; rest logic duplicated with `RestTimerViewModel`. W2b, W2d.
- About 1,100 `ui/workout` test assertions check source text rather than
  rendered screens. T1.
- Every design-token ceiling is at 100%. W2a lowers them.
- The rest-timer architecture is sound (P3: non-atomic `adjust`, W2b).

### System (P2)

- Docs contradicted the code: HANDOFF-NEXT said live code 46 and Room frozen at
  v4 (then 98 and v7); README said "Backup, not sync"; CURRENT_STRUCTURE counts
  were from 11 September; six or more overlapping plan docs. X1.
- No test ran `MIGRATION_TEMPER_6_7`; the device test stopped at v6. Fixed
  (X2a, #382): JVM tests for 5→6 and 6→7, the debug-asset schemas completed,
  and a guard that each version has its schema, asset and migration test.
- The container gate skipped `lintDebug` (hosted CI and release ran it). The
  container gate now runs it on every packet.
- `SyncWorker` casts the Application. S4.

## Delivery order

One packet is one PR into `trunk`. **V** = visible, gets its own drop and phone
check. **Q** = quiet, rides along with the next V drop. The live order, with
progress, is kept in [FRONTEND_REDESIGN.md](../../FRONTEND_REDESIGN.md).

| Wave | Packets |
|---|---|
| 0 · protect data, fix the record | S0a (V, done) · X2 (Q; X2a done, X2b before v8) · S0b (Q, done) · Q1 (V, done) · X1 (Q, this record) |
| 1 · finish the workout floor → Milestone A | T1 · W1a · W1b · W2a–d · W3 |
| 2 · tabs, interleaved with sync | S1 (unpause) · F8a · F4 · F6a · F6b (Milestone B) · S2a · S2b · F10a–c · S3a · S3b · F7a–b · S4 · F5a–c · F8b · F8c · F9 · F11 (Milestone C) |

Every packet: static preflight, `testDebugUnitTest assembleDebug`, a test that
fails on `trunk` and passes on the branch for each defect, two review passes
(independent and adversarial), and renders at 360×640, 412 dp, landscape and
font 1.0 / 1.6 / 2.0 for visible work.

## Deliberately not doing

Localisation; Gradle modules; KMP; end-to-end encryption (unless the owner
revisits ADR-031); hiding the coach strip when it reads "Applied" (conflicts
with ADR-030 §3); the refuted History bodyweight item.
