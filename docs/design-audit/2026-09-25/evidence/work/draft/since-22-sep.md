## What changed since 22 September

Every row of the [22 September record](../2026-09-22/AUDIT.md), re-checked
against the code at `1ad3b11` by the pass that owns it. **Closed** means the
code now does what the fix promised and a named test holds it; **Open** means
the record still describes the code; **Worse** means the pass found a
consequence the record did not name.

Counts: 22 closed · 27 open as recorded · 2 worse · 1 narrowed · 1 refuted item stands refuted.

### Sync (S-1 … S-10)

| # | 22 Sep finding | Now | Evidence |
|---|---|---|---|
| S-1 | Pull destroyed local rows | **Closed** (#383) | every parent write on the pull path is `@Upsert`; the remaining `REPLACE` writes are leaf tables (`SyncPullInPlaceTest`). The local, non-sync `REPLACE` callers are provably safe today but unpinned (`DB-` P3). |
| S-2 | Cursor trusts phone clocks; tombstones keep the old stamp; server upsert unconditional | Open (S1, S2a, S2b) | `SupabaseRestClient.kt:33-34`, `SyncEngine.kt:173-188`; a tie at a 500-row page boundary is skipped too. New: one future-stamped row can freeze a table's pulls on every other phone (S-13, B3). |
| S-3 | Outbox written after the commit; edits while loading or offline never queue | Open (S1), and uneven | of 26 hook sites, 6 sit inside the save's transaction, 13 after it, 3 *before* the delete they describe; four planner writes have no hook at all (B4b). `NetworkError` also reads as signed out (PV-3, B2). |
| S-4 | Four delete enqueuers have no callers | Open (S1) | `enqueueRuleDelete`, `enqueueOccurrenceDelete`, `enqueueTemplateDelete`, `enqueueBodyweightDelete`: zero callers. |
| S-5 | One bad upload blocks the queue; unlimited retry; no timeouts | Open (S1, S4) | `SyncEngine.kt:94-115` (`valueOf`/`error()` outside the per-row try), `SupabaseRestClient.kt` (no timeouts, no `disconnect()`), `WorkManagerSyncScheduler.kt:16-27` (no backoff cap). |
| S-6 | Live strength workouts not synced; copy said they were | Open as recorded; copy honest (S0a) | `SyncEntityType.kt` has no strength type; `SyncCopy.kt:21-25` says so. Strength history sync stays S3a/S3b. |
| S-7 | Account deletion always failed | Open, gated (S2a, S2b) | `IN_APP_DELETE_AVAILABLE = false`; the code behind the gate is unchanged, and its `deleteAllRows` filter is `user_id=not.is.null` (S-12, B2/B3). |
| S-8 | DDL and RLS missing for 9 of 17 tables; no `user_id` filter on pulls | Open (S2a) | `docs/supabase/` defines 8 tables. The live row-security check ADR-031 §3 asks for is still owed: Temper's project was not reachable from this session (see Limits). |
| S-9 | Sign-out keeps cursors; sync not serialised with restore; default prefs overwrite real ones; plaintext tokens | Open (S1, S3b, S4) | cursors: `SyncCoordinator.kt:41-50`; restore: `BK-2`; prefs: `SyncAccountPrefs.kt:62-114`; tokens: supabase-kt's default session manager writes the JSON session to `shared_prefs/<applicationId>_preferences.xml`, excluded from OS backup by the domain-wide rule, plaintext at rest like Room. New upload-side twin: a second account inherits the first's rows (S-14, B1). |
| S-10 | ADR-009 §14–15 never amended | **Closed** (ADR-031) | |
| (a) | A pulled child with a missing parent stops later tables | Open (S1) | `SyncEngine.kt:287-301`; and the skipped child is never re-pulled once the parent arrives (S-11, B1). |
| (b) | A table's cursor is saved only after the whole table | Open (S1) | `SyncEngine.kt:184-188`. |
| (c) | A kept custom lift can be re-uploaded and revive a server tombstone | Open on the client; server effect narrower than recorded | Gson omits a null `deleted_at_ms`, so the upload cannot clear the tombstone either way (R). |
| (d) | Set change time is `completedAtMs` | Open (S1) | `SyncOutboxWriter.kt:46,208`. |
| (e) | Nothing serialises two passes | Narrowed | pass-vs-pass is serialised by the unique WorkManager chain; nothing serialises a pass against restore, sign-out or the sign-in bootstrap. |

### First launch and coach

| # | 22 Sep finding | Now | Evidence |
|---|---|---|---|
| L-1 | Chooser let taps, TalkBack and Back through | **Closed** (Q1) | `SavePostureChooser.kt:76-93`, `SavePostureChooserTest` (7). No regression. |
| L-2 | Permission walk covered the sign-in form | **Closed** (Q1) | `LaunchPermissions.kt:42-47`, `AppNav.kt:352-365`. |
| C-1 | Goal set in Settings never reached the workout | **Closed** (W1b) | chain traced `CoachingPrefsStore` → `ActiveWorkoutViewModel.kt:544-574` → `CoachEngine.kt:63-65`; `FloorRestAndCoachWiringRenderTest`. |
| C-2 | Engine runs three times per tap; `generatedAtMs` defeats equality | Open, reduced (W2c) | one engine run per draft change on the Log now (plus the rest page's own two); `RuleTrace.generatedAtMs` still stamped from `nowMs` (`SetMicroRec.kt:425-451`), so `microRec` never dedupes. |

### Tabs

| Tab | 22 Sep finding | Now |
|---|---|---|
| Home | D01 "Trained today" on other dates | **Closed** (Q1); every acceptance case pinned by `MastheadCopyTest`. |
| Home | D14 finished blocks not tappable | Open (F4): `DailyAgendaCard.kt:234-256`. |
| Home | Week-strip cells ≈ 43 dp | Open (F4): 43.4 dp by arithmetic (`WeekStrip.kt:71`). |
| Home | No read-error state | Open (F4), mechanism now exact: every Home flow drops `Unavailable`, so an unreadable table leaves `ScreenLoading` up for ever with no retry. |
| Body | D05 figure and heat misaligned; D06 sizing ignores the live bar; legend does not wrap | Open (F5a–c): `FigureArt.kt:131-169`, `BodyViewport.kt:20,61`, `BodyMap.kt:196-208`. |
| History | D07 two period models; period lost on process death; mixed sessions titled two ways; set editor's effort control | Open (F6a, F6b). The title split is latent (three fallback rules disagree, reachable only for a blank title). A read-error state does exist (retry page, stale caption, banner). |
| Plan | Routines behind a toggle; delete by long-press; "Programs"; dead UI; `RoutineEditorViewModel` 1,438 lines | Open (F7a, F7b). Dead UI is **wider than listed**: `PreferenceBlock`, `PlanLighterChip`, `PlanRecoveryCommands` plus 15 test-only `PlanViewModel` functions. The editor is 1,490 lines. ADR-021 §6 says the routines list is collapsed *by decision*; F7a reverses it without an amendment. |
| Library | "No matches" offers only "Create lift" | Open (F8b); the picker has the same dead end with a muscle chip set. |
| Cardio | Composer has no header or back; backdating one day per tap | Open (F9). |
| Settings | 11 unnamed rows; three save rows; static subtitles; reused icons; two regenerate paths; three heading styles; no loading state; `BackupCoordinator` 985 lines | Open (F10a–c). **Worse:** the Settings "Generate a week" path silently deletes the current training block and duplicates every routine (UI-3, B10b). |
| Cross-tab | Four hand-built tab headers | Open (History, Body). |
| Cross-tab | Two overlapping start sheets | Open by decision (ADR-021 §2 keeps both). |
| Cross-tab | 10 of 21 ViewModels lose state on process death | Mostly benign (the state that matters is `rememberSaveable`), with three real losses: History's period, Library's query/filters/editor draft, and the custom-week questionnaire answers (new, UI-3). |

### Live workout and design system

| 22 Sep finding | Now |
|---|---|
| D09 switch, D10 typing, two "Add set", evidence chip < 48 dp, chips announced twice | **Closed** (W1a #389), each with its render test. |
| Churn debris (~1,600 dead lines) | **Closed** (W2a #409). Remnants elsewhere: 8 ViewModel functions, 8 composables and a handful of helpers with no caller in `main` (AR-6). |
| `ActiveWorkoutViewModel` 2,541 lines, 23-field state, 13-stage combine; rest logic duplicated | Open, reduced (W2b-4, W2c, W2d): 2,362 lines; 18 `combine`s over 24 inputs; the rest commands are shared (W2b-2). W2b-4 is pinned precisely: the Log passes `wantAnotherSet`, `historySets` and `editingSetId` to the coach, the rest page passes defaults. |
| ~1,100 source-text assertions | **Closed** on the floor (T1a–T1c-2); ~400 remain in 23 files outside `ui/workout` (TS-4). |
| Every token ceiling at 100 % | Open by construction: all 15 families sit exactly on their ceiling (0 blocking, 59 advisory). |
| Non-atomic `adjust` (P3) | **Closed** (#403): compare-and-set loop, `RestTimerStoreTest`. |

### System

| 22 Sep finding | Now |
|---|---|
| Docs contradicted the code | **Closed** (X1) with leftovers: `DEVELOPMENT.md` still says Room "version 4, frozen" in the schema-change instructions (DC-1), plus eight smaller drifts (DC-2 … DC-9). |
| No test ran `MIGRATION_TEMPER_6_7` | **Closed** (X2a #382); X2b's pre-migration copy also holds (#402). |
| Container gate skipped `lintDebug` | Closed by process (HANDOFF lists it; baseline 0/0), not by mechanism: nothing in Gradle ties lint to the push gate (BR-8). |
| `SyncWorker` casts the Application | Open (S4); the same cast in 12 other files, matching the six bypass groups `CURRENT_STRUCTURE` names. |
| The two rest-alarm quirks (HANDOFF) | Open, scheduled; now located: `RestTimerController.kt:296-310` (arm before the row lands; reschedule ignoring `_persistenceHealthy`). |
| Dock Skip (ADR-012) | Open, owner decision pending: still ends whatever runs and clears "rest done". |
| Refuted item (History bodyweight zero) | Stands refuted: the column prints "—". |
