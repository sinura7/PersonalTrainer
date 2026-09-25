# Temper: whole-app audit, 25 September 2026

Review date: 25 September 2026. Source baseline: `1ad3b11` on `trunk` (PR
#412, audit W2b-3); `trunk` moved to `7b077e0` (W2b-4, PR #413) and then
`54c8587` (W2c, PR #414) while the audit ran, the branch merges both, and
nothing below was re-run on them. Counts are as at `1ad3b11`. Packet
**X6**: a findings record and a proposed order, not an ADR. It follows the [22 September record](../2026-09-22/AUDIT.md)
after thirty merged packets, and it is written for the owner first: the
verdict, the grades, what changed, what is new, what needs a decision. The
detail for engineers is in [backend.md](backend.md),
[frontend.md](frontend.md),
[architecture-and-quality.md](architecture-and-quality.md),
[verification.md](verification.md) and [baseline.md](baseline.md).

## Verdict

Temper is in better shape than any earlier record describes. The gate is
green end to end (3,149 tests, no lint warnings, every coverage floor held),
the database and its seven migrations are sound and tested, the rest timer's
September fixes all hold, the workout floor is finished and drawn by tests,
and the September "pull deletes your rows" fix is real while sync stays
airtight behind its pause. Nothing found can lose training history on the
phone today. What the audit found instead is a ring of smaller truths around
that core: the signed gym-floor release cannot be built at all right now, one
tap in Settings quietly restarts a training block, four things the privacy
page promises are not what the code does, and for someone using the phone by
voice four screens are unusable or misleading. Sixty-two P2 rows from the
passes, 51 distinct findings once nine duplicates are merged and two are
argued down to P3, none P1, every one confirmed or trimmed by a second
reviewer, and a proposed order that puts the release lane and the
data-safety fixes before the remaining floor work.

## Grades

The 19 August audit graded nine dimensions; the same scale, with three added.

| Dimension | 19 Aug → now | Why |
|---|---|---|
| Data layer (Room) | B− → **A−** | seven migrations tested on the JVM, a rollback copy before each, no data-losing path found; only unpinned `REPLACE` callers and a few missing indices |
| Backup and Drive | C+ → **B** | sound crypto and a journaled restore; four P2s around Drive sign-out, the guard's blind spots and the cancelled after-workout copy |
| Sync (Temper Account) | new: **C** | paused and airtight; every 22 September item still open, plus five new ways a second phone or account gets it wrong |
| Rest timer | C− → **B+** | the W2b fixes hold with tests; the light-theme notification and a repeating permission box are the gaps |
| Domain rules | C− → **B** | deterministic, one rounding path, the pure-Kotlin seam held; hold lifts coached in reps, no time zone on strength sessions |
| Architecture | C+ → **B** | layering held by checkers; the big classes and thirteen application casts remain; one crash path at startup and one on Home |
| UI and UX | C− → **B−** | the floor is done; Settings, Plan and onboarding carry the most open items; large text clips in a dozen places |
| Accessibility | new: **C+** | 48 dp and contrast mostly held; four screens unusable or misleading by voice |
| Build and release | C+ → **C** | the debug lane is exemplary; the signed release cannot be built and its workflow has a second break |
| Testing | C+ → **B** | 3,149 green tests and honest coverage; 20 screens never drawn on the gate, coverage checked by no lane |
| Docs and governance | new: **B−** | the ADRs decide and are mostly followed; nine drifts and three rule-versus-code conflicts |
| Privacy | new: **B** | honest diagnostics, no analytics, secrets clean; four promises the code does not keep |

## What changed since 22 September

Every row of the [22 September record](../2026-09-22/AUDIT.md), re-checked
against the code at `1ad3b11` by the pass that owns it. **Closed** means the
code does what the fix promised and a named test holds it; **Open** means the
record still describes the code; **Worse** means the pass found a
consequence the record did not name. Of 47 rows: **13 closed, 32 open (two
worse than recorded), 1 narrowed, 1 refuted item stands.**

### Sync (S-1 … S-10)

| # | 22 Sep finding | Now | Evidence |
|---|---|---|---|
| S-1 | Pull destroyed local rows | **Closed** (#383) | every parent write on the pull path is `@Upsert`; the remaining `REPLACE` writes are leaf tables (`SyncPullInPlaceTest`). The local, non-sync `REPLACE` callers are provably safe today but unpinned (P3). |
| S-2 | Cursor trusts phone clocks; tombstones keep the old stamp; server upsert unconditional | Open (S1, S2a, S2b) | `SupabaseRestClient.kt:33-34`, `SyncEngine.kt:173-188`; a tie at a 500-row page boundary is skipped too. New: one future-stamped row can freeze a table's pulls on every other phone (S-13, B3). |
| S-3 | Outbox written after the commit; edits while loading or offline never queue | Open (S1), and uneven | of 26 hook sites, 6 sit inside the save's transaction, 13 after it, 3 *before* the delete they describe; four planner writes have no hook (B4b). `NetworkError` also reads as signed out (PV-3, B2). |
| S-4 | Four delete enqueuers have no callers | Open (S1) | zero callers for all four. |
| S-5 | One bad upload blocks the queue; unlimited retry; no timeouts | Open (S1, S4) | `SyncEngine.kt:94-115`, `SupabaseRestClient.kt` (no timeouts, no `disconnect()`), `WorkManagerSyncScheduler.kt:16-27`. |
| S-6 | Live strength workouts not synced; copy said they were | Open as recorded; copy honest (S0a) | strength history sync stays S3a/S3b. |
| S-7 | Account deletion always failed | Open, gated (S2a, S2b) | the code behind the gate is unchanged, and its `deleteAllRows` filter is `user_id=not.is.null` (S-12, B2). |
| S-8 | DDL and RLS missing for 9 of 17 tables; no `user_id` filter on pulls | Open (S2a) | `docs/supabase/` defines 8 tables. The live row-security check ADR-031 §3 asks for is still owed: Temper's project was not reachable from this session. |
| S-9 | Sign-out keeps cursors; sync not serialised with restore; default prefs overwrite real ones; plaintext tokens | Open (S1, S3b, S4) | tokens located exactly: supabase-kt's default session manager writes the JSON session to `shared_prefs/<applicationId>_preferences.xml`, excluded from OS backup, plaintext at rest like Room. New upload-side twin: a second account inherits the first's rows (S-14, B1). |
| S-10 | ADR-009 §14–15 never amended | **Closed** (ADR-031) | |
| (a) | A pulled child with a missing parent stops later tables | Open (S1) | and the skipped child is never re-pulled once the parent arrives (S-11, B1). |
| (b) | A table's cursor is saved only after the whole table | Open (S1) | `SyncEngine.kt:184-188`. |
| (c) | A kept custom lift can be re-uploaded and revive a server tombstone | Open on the client; server effect narrower | Gson omits a null `deleted_at_ms`, so the upload cannot clear the tombstone either way (R). |
| (d) | Set change time is `completedAtMs` | Open (S1) | `SyncOutboxWriter.kt:46,208`. |
| (e) | Nothing serialises two passes | Narrowed | pass-vs-pass is serialised by the unique WorkManager chain; nothing serialises a pass against restore, sign-out or the sign-in bootstrap. |

### First launch and coach

| # | 22 Sep finding | Now | Evidence |
|---|---|---|---|
| L-1 | Chooser let taps, TalkBack and Back through | **Closed** (Q1) | `SavePostureChooser.kt:76-93`, `SavePostureChooserTest` (7). |
| L-2 | Permission walk covered the sign-in form | **Closed** (Q1) | `LaunchPermissions.kt:42-47`, `AppNav.kt:352-365`. |
| C-1 | Goal set in Settings never reached the workout | **Closed** (W1b) | chain traced end to end; `FloorRestAndCoachWiringRenderTest`. |
| C-2 | Engine runs three times per tap; `generatedAtMs` defeats equality | Open, reduced, at the baseline; closed by W2c (#414) after it | one engine run per draft change on the Log now (the rest page runs its own two); `RuleTrace.generatedAtMs` is still stamped from `nowMs`, so `microRec` never dedupes. |

### Tabs

| Tab | 22 Sep finding | Now |
|---|---|---|
| Home | D01 "Trained today" on other dates | **Closed** (Q1); every acceptance case pinned by `MastheadCopyTest`. |
| Home | D14 finished blocks not tappable | Open (F4). |
| Home | Week-strip cells ≈ 43 dp | Open (F4): 43–44 dp measured on the rendered frame; at font 1.6 and above the captions collapse to "R…", so colour is the only channel left. |
| Home | No read-error state | Open (F4), mechanism now exact: every Home flow drops `Unavailable`, so an unreadable table leaves the loading screen up for ever. |
| Body | D05 misaligned heat; D06 sizing ignores the live bar; legend does not wrap | Open (F5a–c); the legend ellipsises at font 1.6 and the title breaks mid-word at 2.0 on the frames. |
| History | D07 two period models; period lost on process death; mixed titles; set editor's effort control | Open (F6a, F6b). The title split is latent. A read-error state exists for the strength side; the activity side crashes (UI-17, new). |
| Plan | Routines behind a toggle; delete by long-press; "Programs"; dead UI; `RoutineEditorViewModel` 1,438 lines | Open (F7a, F7b). Dead UI is **wider than listed**: the three named pieces plus 15 test-only `PlanViewModel` functions. The editor is 1,490 lines. ADR-021 §6 says the list is collapsed *by decision*; F7a reverses it without an amendment. |
| Library | "No matches" offers only "Create lift" | Open (F8b); the picker has the same dead end. |
| Cardio | Composer has no header or back; backdating one day per tap | Open (F9); confirmed on the frame. |
| Settings | 11 unnamed rows; three save rows; static subtitles; reused icons; two regenerate paths; three heading styles; no loading state; `BackupCoordinator` 985 lines | Open (F10a–c). **Worse:** the Settings "Generate a week" path silently deletes the current training block and duplicates every routine (UI-3, B10b). |
| Cross-tab | Four hand-built tab headers | Open (History, Body). |
| Cross-tab | Two overlapping start sheets | Open by decision (ADR-021 §2). |
| Cross-tab | 10 of 21 ViewModels lose state on process death | Mostly benign (`rememberSaveable` covers it), with three real losses: History's period, Library's query, filters and editor draft, and the custom-week questionnaire answers (UI-3, new). |

### Live workout and design system

| 22 Sep finding | Now |
|---|---|
| D09 switch, D10 typing, two "Add set", evidence chip < 48 dp, chips announced twice | **Closed** (W1a), each with its render test. |
| Churn debris (~1,600 dead lines) | **Closed** (W2a). Remnants elsewhere: 8 ViewModel functions and 8 composables with no caller in `main` (AR-6). |
| `ActiveWorkoutViewModel` 2,541 lines, 23-field state, 13-stage combine; rest logic duplicated | Open, reduced (W2d; W2c closed by #414 after the baseline): 2,362 lines at `1ad3b11`; 18 `combine`s over 24 inputs; the rest commands are shared. W2b-4 was pinned precisely (the Log passed `wantAnotherSet`, `historySets` and `editingSetId` to the coach, the rest page passed defaults) and closed by #413 while this audit ran. |
| ~1,100 source-text assertions | **Closed** on the floor (T1a–T1c-2); ~445 remain in 40 files outside `ui/workout` (TS-4). |
| Every token ceiling at 100 % | Open by construction: all 15 families sit exactly on their ceiling. |
| Non-atomic `adjust` (P3) | **Closed** (#403). |

### System

| 22 Sep finding | Now |
|---|---|
| Docs contradicted the code | **Closed** (X1) with leftovers: `DEVELOPMENT.md` still says Room "version 4, frozen" in the schema-change instructions (DC-1), plus eight smaller drifts. |
| No test ran `MIGRATION_TEMPER_6_7` | **Closed** (X2a); X2b's pre-migration copy also holds. |
| Container gate skipped `lintDebug` | Closed by process, not by mechanism: nothing in Gradle ties lint to the push gate (BR-8). |
| `SyncWorker` casts the Application | Open (S4); the same cast in 12 other files. |
| The two rest-alarm quirks (HANDOFF) | Open, scheduled; located at `RestTimerController.kt:296-310`. |
| Dock Skip (ADR-012) | Open, owner decision pending: it still ends whatever runs and clears "rest done". |
| Refuted item (History bodyweight zero) | Stands refuted: the column prints "—". |

## What is new that matters

No finding is P1. Five came close and each stays P2 for a stated reason
(details in [verification.md](verification.md)): the Settings tap that
restarts a block deletes one date and a week count that every backup carries,
not history; the restore guard's blind spot is covered by the safety copy
written before every restore; a corrupt settings file is a rare trigger that
atomic writes make rarer; one account's rows reaching a second account needs
that person to already hold the unlocked phone; and the login headers reach
the log only on a reply shape the server does not normally send. "You" below
means the person training with the app.

### Release and lanes (build)

| ID | What it means for you | Where it goes |
|---|---|---|
| BR-1 (B14) | The signed gym-floor version cannot be built at all: a library added on 21 September trips the code shrinker (`org.slf4j.impl.StaticLoggerBinder`), and nobody has built the release flavour since. Temper Debug is unshrunk and unaffected. One line fixes it. | X7: fixed 25 September; the gate now builds the release |
| BR-2 (B14) | The release robot has a second, independent break in its very first step (the retired `tools` SDK package), already fixed in the other two workflows. | X7: fixed 25 September |
| TS-2 (B13b) | The "quick test run without Android" cannot start: four test files sit in folders the lane compiles wholesale. `tools/verify.sh` therefore cannot pass. | X8 |
| TS-1 (B13b) | The coverage floors are checked by no automatic step: the one script that checks them dies first, and the cloud build never runs the report. | X8 |
| TS-3 (B13b) | Only the workout floor is ever drawn by the merge gate; twenty of 25 screens are checked by reading code, not by showing them (Home 16 %, Plan 14 %, Settings 17 %, navigation 9 % covered). | W3 |
| TS-4 (B13b) | About 445 tests pin the spelling of code, not behaviour, outside the floor; renaming a variable breaks them while a real bug that keeps the words passes. | F-packets, as each tab is touched |

### Data safety and crashes

| ID | What it means for you | Where it goes |
|---|---|---|
| UI-3 (B10b) | One tap on Settings → Week generator → "Generate a week" silently restarts your 12-week block from this week and adds a second copy of every routine; the other door to the same code warns and previews first. | R1 |
| BK-3 (B5) | The check that stops an empty backup from wiping your data does not know about goals, saved cardio templates or the planned week, so a catalog-only file can wipe those without the warning. The safety copy taken just before still holds them. | R1 |
| BK-1 (B5) | Signing out of Google Drive is supposed to forget your backup password and switch automatic backup off; it does neither, it just hides the switch, and backups resume by themselves on the next sign-in. The privacy page says otherwise. | R1 |
| BK-4 (B5) | The after-workout Drive copy runs only while the summary is open; tapping Done a few seconds after finishing cancels it, and nothing retries or says so. | R1 |
| DB-2 (B4b) | If the small settings file is ever damaged, no setting saves again, the front door shows a Retry that cannot work, and the first-launch question is drawn over it; the only exit wipes history. | R1 |
| DB-1 (B4b) | If storage hiccups while Home reads your history, the app closes instead of showing the last numbers; four of Home's twelve feeds skip the rule the other eight follow. | R2 |
| UI-17 (B10a) | The same on History: an unreadable activity log leaves the screen blank and closes the app instead of offering Retry. Proven by the render harness. | R2 |
| UI-12 (B9a) | Finishing or discarding from the bottom bar saves the workout, but if the small follow-up write fails the app crashes, the plan still shows the workout not done and no summary opens. | R2 |
| AR-2 (B13a) | The one error the rollback copy exists to warn about is never captured in diagnostics, because the capture is installed a line too late. | R2 |

### The floor, the coach and the rest timer

| ID | What it means for you | Where it goes |
|---|---|---|
| DM-1 (B8) | For planks, dead hangs and stretches the coach talks in reps instead of seconds, and after your first hold it says "Hold 0 reps". | R2 |
| UI-2 (B11) | Correcting a logged set and tapping Finish at the top ends the workout and throws the correction away without a word; the old number stays and can still be fixed from History. | R2 |
| RT-2 (B6) | Once you have said no to notifications, the "Rest alerts" box comes back every time you open a workout or the rest page, and after two refusals its Continue does nothing. | rest-alarm packet |
| RT-1 (B6) | On a phone set to the light system theme the rest countdown in the shade and on the lock screen is near-white on white. | rest-alarm packet |
| RT-3 (B6) | A brand-new user is asked for alarms, notifications and battery access right after choosing how to save, before any rest, which ADR-012 and the privacy page say never happens. Decision 2. | X9 |
| DM-2 (B8) | Train in one time zone and open the app in another, and a strength workout can file under a different day; the body-heat map and "this week" shift with it. The activity model already stores the zone; strength sessions do not. | S3a |
| RM-1 (B7) | Temper Debug's "Update" button downloads the build and brings the app forward, but the install screen Android hands back is never opened, so the phone stays on the old build. Obtainium is unaffected. | R3 |

### Sync, when it resumes

All dormant while `SYNC_PAUSED` holds; all inputs to S1 and S2a.

| ID | What it means for you | Where it goes |
|---|---|---|
| S-11 (B1) | A workout can reach your second phone with its sets missing for good, if the sets arrived at the server a moment before the workout did. | S1 |
| S-12 (B1) | One odd row in the cloud freezes downloads for every table behind it, on every attempt; Settings only says sync could not finish. | S1 |
| S-14 (B1), S-11 (B3) | If someone else signs in on your phone, your lifts, weigh-ins, goals and settings are queued into their account and their own data never fully downloads; and the same lift under two accounts jams the upload for good. | S1, S2a |
| S-12 (B3) | Clearing a setting or unlinking something on one phone never reaches the other: the app never sends "this field is now empty". | S2a |
| S-13 (B3) | One phone with a wrong clock can quietly switch off downloads of a whole kind of data on every other phone. | S2a |
| S-12 (B2) | The switched-off "delete account" code asks the server to delete every row that has an owner, not just yours; only the server's per-user locks stand in the way, unconfirmed on nine of seventeen tables. | S2a |
| PV-3 (B2) | After about an hour without signal the Account page says you are signed out and your edits stop being queued, although the login is fine. | S1 |
| S-11 (B2), S-11 (B7) | One request that never gets an answer jams the sync job for ten minutes at a time, and a failing upload is retried for ever with everything else waiting behind it. | S1 (moved forward from S4) |
| PV-2 (B2) | A server error is shown and logged exactly as the library built it; for one unusual reply shape that text includes the login headers. | S1 |
| BK-2 (B5) | Restore does not reset the cloud-sync bookkeeping: old queued uploads survive, restored cardio sessions and the planned week are never queued, and restored custom lifts are stamped "changed at time zero". | S1 |

### Screens and voice

| ID | What it means for you | Where it goes |
|---|---|---|
| UI-1 (B9a) | Tapping a reminder while you are already in a workout throws you out to Home, and the reminder is used up even when the start is then refused. | R2 |
| UI-1 (B9b) | On a Wednesday, tap Monday and press Add session: you land on a page that says the day is a record and lets you add nothing. | F7a |
| UI-3 (B9b) | If Android closes the app while you build your first week, the week still saves but your goal, kit, bodyweight, kg/lb and any plank time are quietly dropped. | F8c |
| UI-1 (B10c) | On a short phone, at large text, or held sideways, the "Where will you train?" step's Continue is below the fold and the page will not scroll. Guided setup is a re-run from Settings, not the forced first run. | F8c |
| UI-2 (B10c) | Switch the run to a walk or type a distance, tap "Leave running", and those inputs are gone; only the clock survives. | F9 |
| UI-4 (B10c) | A saved run or a backdated workout never says which day it happened on. | F9 |
| UI-5 (B10c) | Answering "Both" produces exactly the Strength plan; "Cardio" asks four questions for nothing and previews seven Rest rows under a garbled sentence. | F8c |
| UI-1 (B10a) | A pull-up's weekly rep chart is labelled and spoken in kilograms. | F6a |
| UI-2 (B10a) | Switch History to Year and, until the log re-reads, "This year" sits over last month's record count and best mover. | F6a |
| UI-4 (B10b) | "Save on this phone" does not sign you out, so Settings says your data stays local while changes still queue for upload. Decision 1. | F10a |
| UI-2 (B10b) | With the screen reader on, opening any Settings page never announces a new page or its name. | F10a |
| UI-23 (B10b) | At large text the Account page's fixed-height card cuts its explanation off mid-sentence. | F10a |
| AX-15 (B12) | A screen-reader user cannot set a reminder time: the wheels say nothing and cannot be typed into; the bodyweight wheel already solved this. | F10a |
| AX-14 (B12) | History's totals card reads "This month, 12 sessions" by voice and skips days, sets, minutes, records and "moved most". | F6a |
| AX-1 (B12) | Every History workout is read twice by voice, the second time as bare numbers, and never as a button. | F8a |
| AX-2 (B12) | The custom-week day buttons say only "Mo", "Tu"… by voice, are 43 dp wide, and "We" breaks in two at large text. | F8a |
| DC-1 (B14) | The database-change instructions say "version 4, frozen" and omit the test that guards migrations now. | X9 |

P3 findings, 200 of them (plus the two argued down from P2), are in the detail files, grouped by pass; the
render harness added a dozen more about large text, listed there under each
screen's §7. The frames that show the clearest of them are in
[evidence/frames/](evidence/frames/).

## To confirm on the phone

Nothing on the JVM can settle these; each is a short check after the next drop.

- Light theme: start a rest, pull the shade and lock the phone; is the countdown readable? (RT-1)
- TalkBack: open Settings → Reminders and try to set a time by voice; open Settings → Backup and listen for a page announcement (AX-15, UI-2 B10b).
- Airplane mode for over an hour while signed in, then Settings → Account: does it say signed out? (PV-3)
- Temper Debug: tap the banner's Update and watch whether Android's install sheet ever opens (RM-1).
- Guided setup from Settings on a 640 dp phone at large text: can the Place step be finished? (UI-1 B10c)
- After a flight: does yesterday's workout still sit on yesterday? (DM-2)
- The hosted release workflow, run by hand as an artifact-only build, to see BR-2 fail before BR-1 (owner only).

## Decisions needed

Each is one question. The record proposes; the owner decides.

1. **"Save on this phone" and Temper Account.** Should choosing the local posture sign the account out and clear the queue, or should the caption say you are still signed in? (UI-4 B10b)
2. **The first-open permission walk** asks for alarms, notifications and battery before any rest, while ADR-012 §4/§13 and PRIVACY.md say that never happens. Amend the rule and the page to match ADR-028 §2, or gate the walk on the first rest and reminder? (RT-3)
3. **Weekday reminder alarms are exact when allowed**, while ADR-012 §9 and PRIVACY.md say reminders are never exact. Amend the rule, or drop the exact branch? (RM-10, P3)
4. **The dock's Skip** still ends whatever rest runs and clears "rest done". Name its rest like the other three Skips, or keep it?
5. **The routines list on Plan** is collapsed by decision (ADR-021 §6) and F7a plans to open it. Amend ADR-021 before F7a?
6. **Token ceilings.** All 15 families sit exactly on their ceiling, so the next raw dp anywhere fails the gate. Re-base each with a written note, or keep them tight and pay per packet?
7. **Order.** The proposal below puts the release lane (X7) and a small data-safety packet (R1) ahead of the remaining floor packets. Yes, or keep the floor first? **Decided 25 September: yes.** The order below is the live one; `FRONTEND_REDESIGN.md` carries it.
8. **Owner-only actions carried forward:** delete the eight stale branches (all still present, plus this audit's branch once merged); switch branch protection on (ADR-024); connect Temper's own Supabase project to a session so the read-only row-security check can run before S1.

## Proposed order (adopted by the owner on 25 September)

Codes continue the existing families (`X` record and tooling, `R` small
repair, `S` sync, `F` frontend, `W` floor). **V** gets a drop; **Q** rides
along. Rows in *italics* are the 22 September order unchanged, with scope
notes. Adopted the day the record was written (decision 7);
`FRONTEND_REDESIGN.md` carries it as the live table.

| # | Packet | Scope | Kind |
|---|---|---|---|
| 1 | **X7** release lane (done 25 September) | `-dontwarn org.slf4j.impl.StaticLoggerBinder` (BR-1); `packages: ''` in `release.yml` (BR-2); the swallowed upload failure (BR-7); an unsigned `assembleRelease` in the local gate so the release cannot rot silently again | Q |
| 2 | **R1** data safety | "Generate a week" confirms and keeps the current block (UI-3); the restore guard counts goals, templates and planner rules (BK-3); Drive sign-out disarms auto-backup and forgets the sealed password (BK-1); the after-workout copy survives leaving the summary (BK-4); a `corruptionHandler` on `user_settings` and the chooser drawn under, not over, the retry screen (DB-2, L-3) | V |
| 3 | *W2b-4* | done while this audit ran (#413): the rest page's Next line is the Log's | Q |
| 4 | **R2** crash and coach | the four Home feeds through `observeHealth` and a handler on the insights scope (DB-1, AR-1); History's activity read guarded (UI-17); the bar's finish/discard epilogue guarded (UI-12); diagnostics capture installed before the pre-migration copy (AR-2); hold lifts excluded from rep coaching (DM-1); Finish blocked or warned while a set edit is open (UI-2 B11); a reminder tap that neither ejects a live workout nor consumes a blocked start (UI-1 B9a) | V |
| 5 | *W2c* | done while this audit ran (#414): `generatedAtMs` out of equality and one coach call per real change (C-2); the eager `stateIn` on the floor (AR-4) and the invalidation fan-out per logged set (AR-3) move to W2d-1–3 | Q |
| 6 | *W2d-1–3* | as planned, plus AR-4 and AR-3 from W2c | Q |
| 7 | *rest-alarm packet* | the two quirks (RT-4, RT-5), the rest-alerts gate that asks once (RT-2), the light-theme notification colours (RT-1) | V |
| 8 | **R3** Temper Debug updater | `MainActivity` handles `STATUS_PENDING_USER_ACTION` (RM-1); staged APKs deleted from cache (RM-6) | Q |
| 9 | *check-only drop* | as planned | V |
| 10 | *W3* | as planned; start from the audit's harness (`evidence/AuditRenderTest.kt.txt`) and make it the permanent render matrix; every tab drawn at least once on the gate (TS-3) | V |
| 11 | *S1* | as planned, plus: a skipped child re-pulled when its parent lands (S-11 B1); per-row isolation on pull (S-12 B1); cursors, metadata and "last synced" reset on sign-out and account switch (S-14); `NetworkError` as signed-in-offline (PV-3); timeouts, `disconnect()` and the IO dispatcher on the REST client, moved forward from S4 (S-11 B2, S-11 B7); restore resets the sync tables and re-queues what it rewrote (BK-2); the outbox hook inventory made uniform (S-3); error text redacted before the screen and the log (PV-2) | V |
| 12 | **X8** test lanes | the plain-JVM lane compiles again (TS-2); coverage checked in the deterministic hosted job (TS-1); lint tied to the local gate (BR-8); the timer floor raised to its reading | Q |
| 13 | **X9** docs truth | `DEVELOPMENT.md`'s schema section (DC-1) and the eight smaller drifts (DC-2 … DC-9); PRIVACY.md and ADR-012 amended per decisions 2 and 3; the seven soft keys; the lift count (143, not ~98) | Q |
| 14 | *F8a* | as planned, plus the custom-week day strip (AX-2), `SessionLogRow` as one button (AX-1), Settings' radio roles | Q |
| 15 | *F4* | as planned; Home's "loading forever" becomes a read-error state; the strip captions at large text | V |
| 16 | *F6a* | as planned, plus the stale record count under a new period (UI-2 B10a), the readout's spoken values (AX-14), the reps chart's unit (UI-1 B10a), the title at large text | V |
| 17 | *F6b* (Milestone B) | as planned | V |
| 18 | *S2a* | as planned, plus composite `(user_id, id)` keys (S-11 B3), explicit nulls through an RPC (S-12 B3), server change time and a cursor on it (S-13 B3), a server-side delete function (S-12 B2), the nine missing tables from the audit's reconstructed DDL (backend.md, B3 appendix) | Q |
| 19 | *S2b* | as planned; the v8 bump also carries the local `REPLACE`-path hardening and the missing indices (B4a) | V |
| 20–22 | *F10a–c* | as planned, plus pane titles on sub-pages (UI-2 B10b), the typed path for the reminder wheel (AX-15), the Account card that clips (UI-23), decision 1 | V, V, Q |
| 23–24 | *S3a–b* | as planned; S3a's schema also stores the captured zone on strength sessions (DM-2) | Q, V |
| 25–26 | *F7a–b* | as planned, plus planning a past weekday (UI-1 B9b), the wider dead-UI list, decision 5 | V, Q |
| 27 | *S4* | as planned, minus the items moved into S1 | Q |
| 28–30 | *F5a–c* | as planned; the legend and title at large text | V |
| 31 | *F8b* | as planned | V |
| 32 | *F8c* | as planned, plus the non-scrolling setup steps (UI-1 B10c), the answers surviving process death (UI-3 B9b), the Focus question doing something (UI-5 B10c), the clipped option blurbs | V |
| 33 | *F9* | as planned, plus the date on the receipt (UI-4 B10c), the live-cardio inputs surviving "Leave running" (UI-2 B10c), the landscape dock | V |
| 34 | *F11* (Milestone C) | as planned | V |

## How this was done, and its limits

- Nineteen read-only specialist passes covered every package and resource,
  each re-verifying the 22 September and 16 September findings in its scope
  before adding new ones, with a read ledger per pass. Six adversarial
  verifiers then tried to refute every P1/P2 claim; runnable claims became
  34 JUnit probes whose assertion states the defect, run once on the JVM:
  33 green (the defect is there), one red on a badly chosen assertion whose
  first two held. The first run wedged on the probe for the sync
  transport's missing timeouts, which is that defect in one line. Nothing
  was refuted; three claims were trimmed. The tally and every argument are
  in [verification.md](verification.md).
- The repository's own gate was run in the audit's container on JDK 17
  ([baseline.md](baseline.md)): 3,149 tests, lint, coverage, the device-lane
  compile, plus two probes (the full preflight and an unsigned release
  build). A temporary Robolectric harness drew every screen at 360×640,
  412×915, 800×360 and 600×960 at font 1.0, 1.6 and 2.0 (510 frames); the
  curated 40 are under [evidence/frames/](evidence/frames/); the harness
  source is kept there for W3, with the 34 probe sources and the nineteen
  pass reports and their read ledgers (`evidence/probes/`, `evidence/passes/`).
- No phone, emulator, TalkBack or physical touch was used; every "R" item
  is under "To confirm on the phone". No Supabase call was made: the project
  this session could reach is not Temper's. Frames are reviewed, not
  pixel-pinned; the font scale is set through `LocalDensity`, the gate's own
  method, which does not change `Configuration.fontScale`.
- The audit branch carries two checkpoint commits made when the session's
  usage limit interrupted the run; they preserved the pass reports and are
  squashed away by the merge.

## Deliberately not doing

- Fixing anything under `app/` in this packet; bumping `debugLiveCode`;
  cutting a drop.
- Re-raising the refuted 22 September item (History bodyweight showing
  zero; it prints "—").
- Localisation, Gradle modules, KMP, end-to-end encryption, a sixth tab, an
  LLM author: the permanent refusals stand.
- Extending FND numbering; writing a new ADR: the decisions above are the
  owner's.
