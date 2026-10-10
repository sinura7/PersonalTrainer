# Temper UX acceptance matrix — updated 7 September 2026

**Current packet update, 9 October 2026 UTC:** Summary/detail's bounded
F9/W11/V12/Q08/UX23/UX29 work integrated through [PR #467](https://github.com/sinura7/PersonalTrainer/pull/467)
as `755a2df9`, with the exact reviewed tree, final independent integration review
and fresh actual-trunk local (**3,990 app / 1,694 standalone**) and native
(**222 tests / 32 classes**) gates. A first hosted trunk attempt hit the aggregate
30-minute limit and remains failed; one complete unchanged retry passed. Earlier
dated pending statements below retain their original evidence scope. The active
single packet is the named **Set Edit sheet identity and numeric controls**
refinement. It does not close pure-hold Summary representation, Summary's retained
return policy, the wider UX roadmap or physical-phone acceptance.

The named editor cases passed in `full02`, but that complete gate **failed**
one old thumbnail test's IO-worker recomposition. Its scoped Compose v2/cold-decode
repair passed both cases in `targeted04`. The repaired `full03` closed **PASS on
10 October UTC**: **4,049 app / 578 fresh suites**, **1,696 standalone / 255
classes**, all four Windows tasks, zero failures/errors/skips and unchanged
**1,550 runtime inputs**. Current graphics review passes; final commit binding,
reviews, integration and connected Android verification remain pending.
Original counters and failed runs remain archived, never relabelled as passes.

| Existing scope | Set-editor acceptance task | Current evidence |
|---|---|---|
| F9 / W11 / UX29 | Complete exact exercise identity/art and numeric values; expanded default sheet, effort 10 and all 48 dp actions reachable across the native size/font matrix and relevant RTL/reduced motion. | All 25 actual modal cases passed, including fourteen profiles and native glyph/target/identity checks; current-production graphics reviewed. |
| V12 / Q08 / UX29 | Correct an accepted restored timed original through the shipping editor without changing its literal reps, identity, timestamps or unrelated records; preserve positive-rep stopwatch results even under hold metadata. | Room counter01 proved saved reps 8 became 0. Repaired Room, VM and actual restored Edit/Save cases pass with exact row/export and unrelated-record inventories. |
| F9 / Q08 / UX29 | Type or nudge captured seconds; preserve untouched 1–4, 1801 and maximum-integer seconds; refuse invalid/overflowing text without a write. | Parser, Room, VM and actual keypad/nudge/Save cases pass; restored boundary originals and invalid/cancelled drafts retain exact records. |
| F9 / Q08 / UX29 | Correct accepted historical reps without the new-entry guard changing the intended result; keep new-entry limits and refuse invalid text. | Counter03 proved 101 +1 submitted 100. Repaired actual callback and restored durable correction require 102, while new Add retains 100; whole-text/max/invalid cases pass. Counter02's zero-test fixture failure remains distinct. |
| F9 / UX29 | Cancel, dismiss, keypad cancel and set-ID changes preserve the appropriate drafts/records; Delete/Undo restores the exact original; unavailable-read/Retry retains the authored Add draft, while removal clears its owner. | All 26 restored UI cases pass. Counter04's draft reset is repaired: 17.5 kg / 9 reps / RPE 9 / warm-up survive actual Retry without a write; Cancel/removal reopen fresh defaults. Timed Undo reads 45s, meets 48 dp and restores the exact inventory. |

Baseline for this update: `156cc400a0bc7974209e494e4e4cf0525b29bb7d` on `claude/file-visibility-check-jraqc2` (trunk `28f485f` carries R01–R19). Candidate commit: `f528299` on `claude/file-visibility-check-jraqc2`, merged with trunk `d77ca8c` at `76ef74f`. Earlier versions of this line named `8967888`, a commit the same branch documents as not compiling; do not read acceptance against it.

**Status vocabulary.** *Executed (JVM lane)* — a JUnit test ran on this host and passed. *Written, not executed* — a Robolectric or instrumented test exists on the branch but no Android SDK, Gradle, emulator or device was available here. **As of 10 September CI runs `testDebugUnitTest` on this branch and it passes, so a row still marked this way is stale rather than blocked — see handoff §6.2.** *Implemented, device check pending* — behaviour changed; only a device can close it. *Not executed* — untouched. Nothing below is marked passed from source reading alone.

### UX01 — Clarify Home's planned versus freestyle actions

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX01-AC01 | P1 | F02, F11 | Given both a planned workout and freestyle control, a participant can correctly explain which follows the routine and which starts empty. | Not executed |
| UX01-AC02 | P1 | F02, F11 | Given another selected day, starting/logging behavior matches explicit copy and existing date rules; no silent backdating. | Not executed |
| UX01-AC03 | P1 | F02, F11 | At 360 dp and large text, the planned row's start action is discoverable without an unrelated statistics detour; measure rather than assert this has improved. | Not executed |

### UX02 — Separate starting now from recording completed activity

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX02-AC01 | P1 | F01, F06 | With 0, 3 and 50 routines, participants can find past strength, manual cardio, mixed entry and live cardio. | Not executed |
| UX02-AC02 | P1 | F01, F06 | Selecting a manual entry does not start a timer; dismissing the sheet makes no training-data write. | Not executed |
| UX02-AC03 | P1 | F01, F06 | Opening the sheet while a session runs produces one clear answer and no duplicate session. | Not executed |

### UX03 — Refine the existing logging dock and action transitions

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX03-AC01 | P1 | F03, F08, F11 | Rapid repeated activation while a write is pending commits one set. | Not executed |
| UX03-AC02 | P1 | F03, F08, F11 | Warmup, bodyweight, added-weight and assisted movements show the correct labels and units; no universal '0 kg' assumption. | Not executed |
| UX03-AC03 | P1 | F03, F08, F11 | Keyboard open at 360 dp and landscape leaves the active field and commit action reachable. | Not executed |
| UX03-AC04 | P1 | F03, F08, F11 | Undo/edit/next transitions preserve exercise identity and announce accepted results once. | Not executed |

### UX04 — Make routine Save truthful and clarify autosave

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX04-AC01 | P0 | F04, F08 | Inject details-write failure after editing a name: Save remains on screen, preserves text and offers retry. | Written, not executed (Robolectric: RoutineEditorViewModelTest) |
| UX04-AC02 | P0 | F04, F08 | Inject one staged-target failure: no blanket success/exit; retry persists the intended value once. | Written, not executed (Robolectric: RoutineEditorViewModelTest) |
| UX04-AC03 | P0 | F04, F08 | Back after successful edits remains consistent with existing autosave; empty new stubs retain their documented cleanup. | Written, not executed (Robolectric: RoutineEditorViewModelTest, existing + new) |
| UX04-AC04 | P0 | F04, F08 | Process recreation during failure retains sufficient draft state; reopening shows the accepted persisted result. | Implemented (SavedStateHandle keeps name/notes); Robolectric processDeathKeepsNameAndDoesNotMintASecondRoutine written, not executed; device check pending |

### UX05 — Separate saved receipts from missing or unavailable summaries

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX05-AC01 | P0 | F03, F08 | Missing ID, deleted/restored-over row, computation failure after confirmed save and valid no-work data each render distinct truthful states. | Written, not executed (Robolectric: WorkoutSummaryViewModelTest — missing id, missing row, read fault, summary fault, no-work); device render pending |
| UX05-AC02 | P0 | F03, F08 | Retrying summary never creates or re-finishes a workout. | Written, not executed (Robolectric: aSummaryThatWillNotComputeIsSavedButUnavailableNotMissing asserts one row, same finish stamp, no live session) |
| UX05-AC03 | P0 | F03, F08 | A bodyweight-only workout does not appear worthless because its load-volume is zero. | Executed (JVM lane: WorkoutSummaryBuilderTest.aBodyweightOnlySessionHeadlinesItsRepsNotZeroKilograms); Robolectric written; device render pending |
| UX05-AC04 | P0 | F03, F08 | Back and Done exit once and do not return to an already-finished live session. | Not executed — navigation unchanged (popUpTo Home on finish; Back is Done); emulator check pending |

### UX06 — Stop numeric input from silently changing meaning

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX06-AC01 | P0 | F10 | 8.5 reps is rejected rather than saved as 85; -50 kg is rejected rather than saved as 50. | Executed (JVM lane: NumericEntryTest, ComposerCopyTest, TargetEntryTest); production-screen paste/keyboard pass pending |
| UX06-AC02 | P0 | F10 | 1.2.3 and 8e2 do not become different accepted values; 62,5 and 62.5 behave equivalently under the supported decimal policy. | Executed (JVM lane: NumericEntryTest.ambiguousTextNeverBecomesADifferentAcceptedValue, blank/62,5 == 62.5) |
| UX06-AC03 | P0 | F10 | Existing valid values survive unit conversion/round trips without drift beyond specified display precision. | Executed (JVM lane: storedWeightsSurviveADisplayRoundTripWithinDisplayPrecision — ±0.05 kg / ±0.25 lb) |
| UX06-AC04 | P0 | F10 | All shared filter callers are covered, including composer, live cardio and routine targets. | Executed for the domain rules; caller wiring changed in composer, live cardio, custom rest, routine card and the custom week — every caller now type-checks against the widened signature (`./gradlew assembleDebug`, BUILD SUCCESSFUL) — Compose-level render check pending (no SDK here) |
| UX06-AC05 | P0 | F10 | The custom week's target boxes refuse what the week cannot hold, instead of applying the stored number underneath the shown one. | Written, not executed (Robolectric `CustomWeekViewModelTest`, 4 tests); the caller compiles (compile lane) and the arity guard is executed (`check-lambda-arity.py`, `test_lambda_arity.py` 15/15) |

### UX07 — Define draft preservation and leave-during-save behavior

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX07-AC01 | P0 | F04, F08 | Build a multi-row mixed draft, background, recreate the process and restore: date, rows, order and values survive. | Already covered at HEAD by R09 (SavedStateComposerDraft); Robolectric processRecreationRestoresTheTypedDraft written, not executed |
| UX07-AC02 | P0 | F04, F08 | Cancel/Back during delayed accepted save neither creates a duplicate nor tells the user committed work was discarded. | Written, not executed (Robolectric: cancelWhileSavingIsRefusedAndTheSaveStillLandsOnce) |
| UX07-AC03 | P0 | F04, F08 | Failed save preserves the draft; successful save clears only that draft. | Written, not executed (Robolectric: anAcceptedSaveClearsTheDraft, save() keeps draft on Rejected/thrown) |
| UX07-AC04 | P0 | F04, F08 | Onboarding draft restoration does not overwrite the saved current plan or create a new one without acceptance. | Written, not executed (Robolectric: processRecreationRestoresTheStepAndAnswersWithoutWritingAPlan) |

### UX08 — Make every onboarding step fit and remain editable

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX08-AC01 | P1 | F07, F11 | Every question and Continue/Skip action is reachable at 360×640 dp, font 2.0, and the smaller supported window configuration. | Not executed |
| UX08-AC02 | P1 | F07, F11 | Typed bodyweight, wheel, unit switch and Skip produce consistent optional values. | Not executed |
| UX08-AC03 | P1 | F07, F11 | Screen-reader focus lands on the new question and Back keeps the selected answer. | Not executed |
| UX08-AC04 | P1 | F07, F11 | Accepting a plan once creates the intended program; reopening setup does not silently apply a draft. | Not executed |

### UX09 — Carry an exercise through Create routine

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX09-AC01 | P1 | F01, F06 | From an empty routine catalogue, choose a lift → Create routine → save: exactly one routine contains that lift, without another search. | Not executed |
| UX09-AC02 | P1 | F01, F06 | Cancel returns to the original context and leaves no empty stub. | Not executed |
| UX09-AC03 | P1 | F01, F06 | Rotation/process recreation retains the pending exercise ID according to the draft contract. | Not executed |

### UX10 — Reduce accidental custom-exercise creation and keep picker errors visible

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX10-AC01 | P1 | F06, F08 | Searching 'bench' with existing matching variants prioritizes choosing them over creating 'bench'. | Not executed |
| UX10-AC02 | P1 | F06, F08 | Duplicate/disk/create errors are visible within the active sheet in single and multi modes. | Not executed |
| UX10-AC03 | P1 | F06, F08 | Create then cancel activity follows the disclosed catalogue persistence rule. | Not executed |
| UX10-AC04 | P1 | F06, F08 | Double confirmation adds the selected IDs once and preserves order. | Not executed |

### UX11 — Make edit scope and secondary actions discoverable

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX11-AC01 | P1 | F02, F06 | A participant can state the scope before saving a swap or schedule edit. | Not executed |
| UX11-AC02 | P1 | F02, F06 | Routine management is reachable without relying solely on long press, or documented usability evidence supports retaining the existing affordance. | Not executed |
| UX11-AC03 | P1 | F02, F06 | Deleting a routine preserves history and accurately explains its impact on scheduled days. | Not executed |

### UX12 — Make calendar dates and statuses distinguishable at small widths

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX12-AC01 | P1 | F02, F09, F11 | At 320/360/412 dp, date targets are measurable and usable without overlapping ambiguous activation regions, or an equivalent accessible selector is supplied. | Partial JVM evidence: fourteen actual Home/Plan profiles verify complete 48 dp targets, separation and pointer selection; whole-app viewport and phone acceptance remain open. |
| UX12-AC02 | P1 | F02, F09, F11 | A week containing done, skipped, missed, future and rest days has unambiguous spoken and visible detail. | Partial JVM evidence: full status text and civil-date semantics distinguish completed, skipped, missed, planned and rest in the stress fixture; connected status transitions and physical TalkBack remain open. |
| UX12-AC03 | P1 | F02, F09, F11 | Selecting a neighboring-month date opens the correct civil date; today remains distinguishable from selection. | Partial JVM evidence: actual pointer selection crosses December/January with exact date headings and distinct today/selected cues; whole-app and phone acceptance remain open. |

**Week geometry packet (9 October 2026 UTC):** The former seven equal cells
fell below 48 dp at small widths. Full labels/statuses now wrap in measured,
separate targets; the strip scrolls horizontally rather than shrinking them,
as allowed by [ADR-026](../architecture/ADR-026-frontend-redesign.md). Ordinary
360 dp renders show three complete day cards: more scrolling and a taller row
are explicit tradeoffs, not measured usability improvements. Widths remain
stable on count/proposal refresh. A changed selection or geometry reveals the
selected day; same-geometry restoration preserves manual exploration. Counts
separate completed activities from skipped occurrences without changing the
resolved-fill rule. Full civil dates remove month/year ambiguity.

Picker questions and a separate Cancel reflow when they cannot share a row.
Loaded Plan's header scrolls with the page and reflows its actions, exchanging
a pinned header for reachable full labels. Loading retains its header. Native
caller review also exposed narrow Body/History titles fragmented by a weighted
title beside the entry button. Only those title/action rows now reflow; main
titles retain their whole word and heading role. Their data/filter behavior is
unchanged. The shared entry label stays complete.

**Retained failures and fresh targeted evidence:** Geometry `targeted01`–`04`
and `06` stopped before tests on static checks or compilation. `targeted05`
finalized **74 tests / 32 failures**. `targeted07` finalized **74 tests / one
failure**: the Home Extra method stopped at a Windows-invalid `?` screenshot
filename before completing cancellation. All fourteen Home/Plan profiles and
fourteen Plan picker profiles passed separately. The first image manifest was
empty because a PowerShell UTC conversion lost its date kind; recovery verified
all **486 original PNG hashes**, executing **zero new tests**, and preserved
the original empty manifest. `targeted08` finalized **83 tests / nine failures**:
the fixture polled History before settling its screen switch. Later stages
loaded it and exposed the title fragmentation. No failed attempt is a pass.

After correcting the filename, settling the actual switch before the unchanged
20-second wait, and fixing the two headers, `targeted09` passed **83 tests / six
fresh suites, zero failures/errors/skips**, with **1,529 stable runtime inputs**
and stable references. The four-task targeted command returned exit 0 and
BUILD SUCCESSFUL; **579 fresh native-graphics PNGs** were archived with matching
hashes. Execution: **08:54:56.0859381Z–09:00:00.0786359Z**. Summary SHA-256:
`287a6ac0628d62fe8bb5eb3dbce0012c998b9a71949f0c212ba6252b4052748b`.
The unfiltered `full-gate01` then passed **3,820 tests / 560 fresh suites, zero
failures/errors/skips**, including all twelve shared-caller display profiles.
The complete standalone preflight passed **1,659 tests / 251 classes**; all
four required Windows tasks with `--rerun-tasks` passed, **135 tasks executed**.
Execution: **09:01:50.1432937Z–09:17:50.9527428Z**, with the same **1,529 stable
runtime inputs** and stable references. Summary SHA-256:
`669a3a9b2dc17cbf3b4329a8243dab58a09dd380a703e8177c0e6fe075a6140d`.
That gate belongs to initial candidate `2fa5bf7a`, not the later origin fix.
Its two closed hosted runs passed the same 3,820 tests / 560 suites and 222
native cases / 32 classes. Local `native03` passed those 222 cases, including
the connected synthetic workout, with 85 hashed captures and 13 fixture
restoration pairs. Earlier `native01`/`native02` failed before any application
test on emulator readiness; their failures remain preserved.

**Review finding and repair:** Independent review then found a reachable case
where changing Settings' week start shifts the cell coordinates while the
selected date remains the same. The initial handled-geometry identity omitted
that origin, so the selected day could disappear. `week-origin-reproduction01`
confirmed **four tests / four failures**, on actual Home/Plan in LTR and RTL,
before the production fix. Summary SHA-256:
`c75cc9fb4ddea79e0612e2a2e0389772b8250e9e8844dadab26f31086009e65f`.
The identity now includes the first epoch day without depending on activity
counts or proposals. Eight-field saved state keeps that identity and manual
exploration; older seven-field state retains its offset but admits a fresh
selection reveal rather than trusting an unknown origin. The older-format
branch has source review, not an explicit executed legacy-format test.

`week-origin-fix01` passed **24 tests / one fresh suite, zero failures/errors/
skips**, all four required targeted tasks, and **1,529 unchanged runtime
inputs**. The four new cases verify complete selected-day visibility after
the origin shift, restoration and subsequent deliberate exploration, without
scrolling the selected day into view for the assertion. Execution:
**09:57:16.9108926Z–10:01:51.0198436Z**. Summary SHA-256:
`c65518da04a7ce9c5d7e7cd559d124c7ffb433a926b4ac6954765f8574b60372`.
The archive contains **254 fresh native-graphics PNGs** with matching hashes.
Initial no-blocker reviews and green checks do not approve this changed
candidate. Its fresh unfiltered `full-gate02` passed **3,824 tests / 560 fresh
suites, zero failures/errors/skips**, and **1,659 standalone tests / 251
classes**. All four required Windows tasks passed with **135 tasks executed**,
**1,529 stable runtime inputs** and stable references. Execution:
**10:04:06.6741953Z–10:20:07.5262972Z**. Summary SHA-256:
`d4f30936eebf58c150ba87a184f280477b8712bb8b83eaf0298a1b799fedd0f2`.
Renewed independent and adversarial reviews accepted final candidate `588e2683`.
[PR #464](https://github.com/sinura7/PersonalTrainer/pull/464) integrated that
exact tree as `5c7f869b`. Fresh clean-trunk `postmerge-full01` passed **3,824
tests / 560 suites, zero failures/errors/skips**, the complete **1,659 tests /
251 classes** standalone preflight and all four required Windows tasks with
**135 tasks rerun**. Its runtime inputs and references stayed unchanged.
`native06` passed **222 cases / 32 classes**, including the connected workout,
with **85 hashed captures / 13 restored fixture pairs**. Actual-trunk hosted
run `37919469965` passed independently checked matching unit/native counts.
Integration review confirmed those source/evidence bindings; the owned emulator
was stopped and removed from running processes. Summary record SHA-256:
`a9bcc2bdd31b1dd6ee52cc356d8979a7b2fbbb7f54b2c980e410aa3087c31b59`.
Original failed attempts remain failures. Evidence is retained under
`build/ux-context/runs/home-week-geometry/`, including
`integration-complete-5c7f869b.json` and the independent integration addendum.
This closes this bounded packet's integration, with the limits below retained.

**Limits:** These are actual production screens/ViewModels on isolated stores,
mounted without AppNav, its bars or system insets. Inventory equality covers
checked rows/preferences, not zero SQL attempts or the entire database. Seeded
statuses are a stress fixture, not a connected completed workout. Saved-state
restoration/constraint changes are not Activity rotation or OS process death.
Semantics do not prove TalkBack speech, focus order or touch feel. KEEP remains
untraversed; Suggested Rest is component-only because Plan filters those
proposals. Body legend/explanation and History metric labels remain clipped in
the reviewed narrow/large-text frames. History calendar dates 10 and 11 also
appear as 1 and 1 at 320 dp/font 2.0 (frame SHA-256
`16fee37760e00ebee4216bb3cf92b65d00f17feb54093aabd07027a0e3ef7583`).
These are explicit UX12/UX24/UX25 dependencies for the already-next History
period/calendar packet and later Body work. Their source is outside the changed
headers; this evidence does not establish the original render cause. Phone IME/performance,
N2 and W3 remain open; no broader criterion is closed here.
Local `native03`'s Home/Plan baseline images are obscured by the Permissions
required dialog. Passing underlying semantics and the connected workout do
not supply an unobscured AppNav week/header visual acceptance. That evidence
gap remains open separately from the direct-screen JVM geometry matrix.

### UX13 — Expose recurrence and ordering consequences without restoring clock clutter

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX13-AC01 | P1 | F02, F09 | Add the same type once and recurring in separate scenarios; next week contains only the recurring case. | Not executed |
| UX13-AC02 | P1 | F02, F09 | Reordering states exactly which future/reminder behavior changes and preserves completed history. | Not executed |
| UX13-AC03 | P1 | F02, F09 | Home and Plan show the same dated occurrences after changes. | Not executed |

### UX14 — Preview bulk missed-work changes before applying

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX14-AC01 | P1 | F02, F09, F08 | Preview and committed outcome match for missed work crossing week boundaries. | Not executed |
| UX14-AC02 | P1 | F02, F09, F08 | Completed sessions remain unchanged and are explicitly separated from future schedule adjustments. | Not executed |
| UX14-AC03 | P1 | F02, F09, F08 | Commit failure preserves the proposal and offers retry; repeated taps do not apply it twice. | Not executed |

### UX15 — Make History's scope and deeper sections reachable

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX15-AC01 | P1 | F05 | With years of synthetic history, a participant can reach lifetime records and the newest activity without an unbounded scroll. | Not executed |
| UX15-AC02 | P1 | F05 | Changing a horizon produces one period across totals, calendar, progress and list; lifetime records and blocks are labeled secondary views. | Bounded #465 and captured-date #466 integration and machine checks completed below; participant acceptance not executed |
| UX15-AC03 | P1 | F05 | Editing a prior best refreshes visible results and record drill-down identifies the supporting session. | Not executed |

**Verification follow-up (9 October 2026 UTC):** Home recovery merged through
PR #462 as `60c01a0b`. Its clean-trunk local gate passed 3,770 tests / 557 fresh
suites and its native journey passed 222 tests / 32 classes. The actual-trunk
[required hosted run](https://github.com/sinura7/PersonalTrainer/actions/runs/37891704902)
remains failed: one existing History test expected zero records but observed two
before its edit. A controlled, persisted timestamp tie reproduces that initial
mismatch; the original hosted timestamps were not captured. The repair fixes the
synthetic fixture's calendar anchor and chronology while retaining actual
repository writes, the original zero-record baseline and exact correction/row
assertions. The corrected targeted gate passed all 20 History/record-calculation
tests in two fresh suites, with zero failures/errors/skips, plus build and lint.
The subsequent unfiltered four-task gate with `--rerun-tasks` passed all 3,770
app tests / 557 fresh suites and 1,656 standalone tests / 251 classes, plus
Debug/release build, lint and instrumented-source assembly. All 1,525 runtime
inputs and Git references stayed unchanged. At that verification point, final
reviews, corrected hosted verification and post-merge acceptance were pending;
the subsequent integration is recorded below.
The first diagnostic attempt failed its fixture-integrity assertion after a
replace removed child rows; it is preserved and is not credited as a tie proof.

**Current packet status (9 October 2026 UTC):** The chronology fixture repair
subsequently integrated through [PR #463](https://github.com/sinura7/PersonalTrainer/pull/463)
as `c58110d1`; later verified trunk `5c7f869b` includes it. The preceding failed
hosted run remains a failed historical result. F6a implements one saved civil
period for History's list, calendar, totals and keyed progress; full-log records
and blocks are explicitly activated lifetime views. Current Month is the default;
Day/Week strips, Month, Year month choices and All chronology retain full civil
dates, reachable controls and exact workout/activity identity. The immediate
captured-date visibility follow-up below precedes Summary/detail clarity.

**F6a executed local evidence (9 October 2026 UTC):** The targeted run passed
289 tests / 52 fresh suites. The subsequent complete standalone preflight and
unfiltered four-task gate with `--rerun-tasks` passed **3,894 app tests / 569 fresh
suites** and **1,675 standalone tests / 254 classes**, with zero failures, errors
or skips, plus Debug/release build, lint and instrumented-source assembly. All
1,540 runtime inputs and Git references remained unchanged. The full gate includes
the final strengthened recovery waits; the targeted result precedes those two
test-only refinements. Full-run summary SHA-256:
`2F92E9742DC1EAFD0CD2661C7A0483074CF07394A5457F0F95DE438283B42485`;
runtime-input manifest:
`9B02443DD1C8CC60F96552F2A9BA8AA6D0EAEB75496A3A1EE6FD6E7C996B81CF`.

Actual supported backup restores reproduced stale activity and workout readouts
when individual weights changed but IDs, dates, counts and total work did not.
Those failed runs are preserved. The repaired activity projection digest and
typed finished-work content token now pass those same real restore cases. The
workout projection retains empty sessions, excludes live sessions, preserves raw
Double precision and avoids set-by-planned-lift multiplication. Pending and failed
unit-change cases also pass: a review cannot carry labels from the previous unit;
a same-unit failed refresh may retain a visibly stale verified review. Three
subsequent recovery-fixture failures sampled the newly explicit Loading stage
before recovery finished. Terminal waits now require the relevant reads to finish,
with original value/health/layout assertions and timeouts retained; public trace
checks forbid presenting an intermediate empty list as successful recovery.

Mounted JVM checks cover the real period/range controls, exact routes, populated
and empty lifetime views, failed refresh/Retry, long names/durations, 48 dp targets,
full glyph/native ink and Year/Week reachability across the 14-profile matrix
(including 320 dp/font 2.0, 360 dp, 412 dp, landscape, adaptive 600 dp and RTL).
Range boundaries, late reads, correction/Delete/Undo, owner SavedState restoration
and mounted midnight/resume zone changes also passed. JVM pixels and factory
restoration do not establish physical-phone use, TalkBack, OS process death,
performance or atomic cross-store snapshots. Final clean candidate reviews,
hosted checks and integration remain pending at this snapshot.

**F6a native evidence (9 October 2026 UTC):** The owned offline API-29
`history-period-coherence/native01` passed **222 tests / 32 classes**, one fresh
XML, zero failures/errors/skips, from **14:06:31.4826414Z** to
**14:13:06.5487553Z** on clean candidate `8a1da0031383ffaba229d5a4678dd5199ec424a7`
(tree `34ae1a1046a0c126e10387efe7c948d210cc7fa2`). All 1,540 runtime inputs match
the full local gate and remain unchanged; references remained stable. All 85
fresh captures matched device/host hashes, all 13 fixture restoration pairs and
network/settings restoration passed, and the final package inventory was empty.
The connected AppNav journey retains one corrected set, 85 kg × 3, in Summary
and History with 255 kg total work. The baseline History screenshot is obscured
by the existing permissions modal; it does not establish unobscured native
period-layout acceptance. Mounted JVM coverage supplies the period-layout
matrix. Native summary SHA-256:
`2CC5E6FB50C98116F8AB53A11FFFF9E574D5C6D76619721126BDE2F3A1E7A165`.
Later documentation-only pins must bind to the same verified runtime bytes;
these tests executed the candidate above, not a later Git tree. Physical-phone,
TalkBack, OS process-death and performance limits remain open.

**F6a integration completed (9 October 2026 UTC):** [PR #465](https://github.com/sinura7/PersonalTrainer/pull/465)
merged as `7a41c9779a5d5a1bd00714c6ade30053a2c5e09e`. Its tree equals reviewed
candidate `ec258beb5c15f8da5e90c6ca9d064ca35b0a64fa`; fresh independent integration
review approved this bounded packet and its executed machine gates, with the
captured-date follow-up below explicitly open. All **1,540 runtime inputs**
match the pre-merge gate and actual-trunk local/native executions.

The clean-trunk full gate freshly passed **3,894 tests / 569 suites**, zero
failures/errors/skips, and the complete standalone preflight passed **1,675
tests / 254 classes**. All 135 Gradle tasks executed, including Debug/release
assembly, lint and Android-test assembly. Full summary SHA-256:
`27DE6BCE97FB71A5A14712833818162C6FC7BEB11430D37D84447ED1C3CF3AFA`.
The unchanged clean-trunk native run passed **222 tests / 32 classes**, zero
failures/errors/skips, with all **85 fresh captures** matching device hashes,
13 fixture restoration pairs, restored network/settings, empty package
inventories and completed collector/emulator cleanup. Native summary SHA-256:
`562BB8992EEA533026E1472E5F57E8AC1D9F2F5462E43373EF88E35EE1CD7908`.

[Actual-trunk CI 37945856269](https://github.com/sinura7/PersonalTrainer/actions/runs/37945856269)
checked out that exact squash commit in both jobs. Independent parsing verified
**3,894 tests / 569 suites** in the required job and **222 tests / 32 classes**
in the supplemental native job, all passed. Hosted summary SHA-256:
`0d2a1def30687786c7b059026542540e67d3e9c9d72c86df6c25917407c09cd8`.
The earlier [branch push 37942981235](https://github.com/sinura7/PersonalTrainer/actions/runs/37942981235)
remains failed: `failedLiveNotesKeepExactTextAndRetryCommitsOnlyNotes` timed out
while observing the keyboard window before its write/Retry assertions. Hidden
IME versus observation failure remains unresolved; later unchanged passes do
not prove a cause or replace that result.

The execution/review bindings are retained in
`build/ux-context/runs/history-period-coherence/integration-complete-7a41c977.json`
and `build/ux-context/reviews/history-f6a-integration-independent-7a41c977.md`.
These are local ignored evidence. The native History baseline remains obscured
by the existing permissions modal; it does not certify unobscured native period
layout. Mounted JVM evidence retains that separate scope. This integration
does not close broader UX15/Q08, physical-phone/TalkBack, OS process-death,
performance, equal-millisecond record ordering or legacy strength captured-date
storage. No owner-phone testing or installation, or Obtainium drop, is claimed.

**Immediate F6a / UX15 / Q08 captured-date visibility follow-up (9 October):**
One real counterexample on merged `7a41c977` confirms a completed Tokyo activity
through the production repository with captured date **9 October**. Nine hours
later the instant has advanced, while the device in Honolulu reads **8 October**.
The durable activity graph and completed summary remain exactly unchanged, but
the ready All History projection omits the activity. This is a visibility failure,
not a failed save, rewritten captured date or legacy strength-date attribution.

`history-captured-date-visibility/counter01` ran from **15:13:27.0214152Z** to
**15:16:54.6117497Z** and finalized one fresh XML: **one test, one visibility
assertion failure, zero errors/skips**, with **1,541 unchanged inputs** and
stable references. It is not a passing regression or full gate. Its original
test, raw log and summary remain under
`build/ux-context/runs/history-captured-date-visibility/counter01/`.

The current single packet, `codex/history-captured-date-visibility`, repairs
read-side bounds and navigation so **All includes all known completed captured
records**, their dates remain reachable, and a current period can include known
captured dates ahead of device today within that selected civil span. Empty
future-only periods stay unavailable. Totals, calendar, progress and list must
continue using one coherent range, with exact stored graph/summary identity
preserved. This follows [ADR-011](../architecture/ADR-011-time-semantics.md) and
[ADR-026](../architecture/ADR-026-frontend-redesign.md); no new policy authority,
ADR amendment or captured-date rewrite is introduced. Repair verification,
review and integration remain pending at this initial counterexample snapshot.

**Travel repair targeted evidence:** `targeted01` finalized **136 tests / 24
fresh suites, nine failures, zero errors/skips** with 1,543 stable inputs. All
five real-data travel cases and seven date-policy cases passed. Eight new render
cases waited for the changed date before delivering its Compose frame; their
failed captures show the old date before later semantics synchronize to the new
date. The existing native layout drain now precedes that READY wait, retaining
the real date effect, assertions and 20-second timeout. The ninth failure was
the old mixed-source expectation excluding known completed work ahead of Today;
it now includes that work and separately excludes a completed next-month row.
Original failed XML/logs and all 157 fresh PNGs remain preserved.

`targeted02` then passed **22 tests / four fresh suites**, zero failures/errors/
skips, from **15:35:33.3547894Z** to **15:39:21.2661114Z**, with 1,543 stable inputs
and stable references. It covers six real-data cases, seven date-policy cases,
eight native-graphics travel interactions and the corrected mixed-source case.
Supported deletion and backup recovery preserve the raw selected date and
restore the exact activity graph, range and metrics; no Activity Undo is claimed.
The eight renders cover 320 dp/font 2.0, landscape/font 2.0, RTL/font 2.0 and
412 dp/font 1.0 at both October and year boundaries, with exact detail routes,
48 dp actions, full text and unchanged durable data. All **56 fresh PNGs** were
archived with matching hashes. Summary SHA-256:
`788BAF21458095DA56A8540AF33C3F5FB74B791D539FA14BC8F8A1214EBCA8F5`;
runtime manifest SHA-256:
`95BAD8FEA772682A194FEB728F0CA2A684C9C84C903580069BBD3F0B38240FC8`.
These targeted checks do not substitute for the complete local gate, connected
Android verification, clean candidate reviews, hosted checks or integration,
which remain pending. They do not establish physical-phone acceptance.

**Travel repair complete local gate:** `full01` passed the complete standalone
preflight (**1,682 tests / 255 classes**) and unfiltered four-task Windows gate
with `--rerun-tasks`: **3,915 tests / 572 fresh suites**, zero failures/errors/
skips, from **15:40:41.1548844Z** to **15:57:26.0967913Z**. All 1,543 runtime
inputs and Git references remained unchanged. The existing 14-profile screen
matrix and eight focused travel renders pass together; original failed attempts
remain failed evidence. Full summary SHA-256:
`CDA0E71160B93E27A8F9D3ED4A465BCBB5F3DF394DAE073033D058BE318A10E3`.
At that local-gate snapshot, clean candidate binding, connected verification
and final review were pending. Their executed closure follows; hosted checks
and integration remain subsequent work. No phone or whole-app acceptance is inferred.

**Travel repair native and final-review closure:** Clean candidate
`dd82321fe8b780b4ec8eec0440a11feca22781d3`, tree
`09660cc24762e0b8014700d9c274060c789d627f`, binds all **1,543 runtime inputs**
to the closed full gate. `native01` ran from **16:00:28.1099563Z** to
**16:07:08.3710194Z** on the dedicated offline API-29 emulator and passed
**222 tests / 32 classes**, zero failures/errors/skips. All **85 fresh captures**
match their device hashes; all 13 fixture admission/restoration pairs, final
settings/network, empty package inventories, collector stop and unchanged
source/references were verified. Root's separate **16:08:24.2547147Z** process
query confirmed emulator shutdown. Native summary SHA-256:
`C5B515A679105E65C4C1619FC4536F115AEDB3AFF2BEF3905FC9049E509EB0F4`;
artifact manifest SHA-256:
`99EE6C4630D5A0F41A59C3E6F5E322E595961C38CE09104AD9BC3A11B554E0C9`.

Fresh independent and adversarial reviewers approve that exact bounded
candidate with no new blocking findings, independently checking raw XML,
runtime blobs, render/capture hashes and selected changed frames. Their ignored
reports are `build/ux-context/reviews/history-captured-independent-dd82321f.md`
(SHA-256 `B17A75B9F8649DD4E015AE4930CDB697EB323C041FBC07C8287D543813E28247`)
and `history-captured-adversarial-dd82321f.md`
(SHA-256 `A0C0BA7DDA445CE466842401047A82C66E05193C71E72DDDE92D427F69CB9456`).
The subsequent documentation-only closure must retain this runtime binding;
native execution occurred on `dd82321f`, not on the later documentation tree.
Hosted verification and integration are not yet complete at this snapshot.

The new travel proof uses real Repository/Room data and mounted real Compose
JVM renders. The inherited connected suite verifies adjacent workout/History
behavior, including the same corrected **85 kg × 3 / 255 kg** saved result; it
does not perform a new Android timezone-travel journey. The native History
baseline remains permission-modal obscured. Primitive saved-selection recovery
does not establish OS process-death recovery. Prior hosted IME-observation
failure, legacy strength captured-date storage, equal-millisecond ordering,
cross-store atomicity and physical-phone/performance acceptance remain open.

Equal-timestamp record ordering remains a medium UX15 dependency: current record
feedback can depend on input order when chronology ties. This repair changes no
production calculation or saved data. Resolve and verify that rule before
presenting improved History statistics as trustworthy; this bounded fixture
check does not close the wider UX15 acceptance tasks above.

**Captured-date integration closure (9 October 2026 UTC):**
[PR #466](https://github.com/sinura7/PersonalTrainer/pull/466) merged `9fd21456`,
tree `42029232fbffdc8ecc78790752a36e33a4eadcc4`, identical to accepted `16521ef1`.
Fresh clean-trunk `postmerge-full01` passed **3,915 app tests / 572 suites**,
**1,682 standalone tests / 255 classes** and all four local tasks with all
135 tasks executed. Its summary SHA-256 is
`D8996FCF38CED74FC41D3D5BDF8070D79596A2BC6F12CEE3095D4A7E9B1CAFE9`.
All 1,543 runtime inputs matched actual commit blobs. `postmerge-native01`
failed cold-boot admission on a Launcher ANR before install, with zero tests;
its cause remains unresolved. Fresh `postmerge-native02` passed the unchanged
guard and all **222 native tests / 32 classes**, with 85 verified captures,
all 13 fixture restoration pairs and verified settings/network/package cleanup
and shutdown. Its summary SHA-256 is
`60F8CA40BBEFDF351B8C8A1B3CD77EC569A6E2E9F645AA317EB8F86484EC86F5`.
[Actual-trunk hosted run](https://github.com/sinura7/PersonalTrainer/actions/runs/37960635611)
passed both jobs on that exact source/tree with the same app/native counts.
Independent integration review verified raw XML, all input and capture hashes,
hosted archives, restoration and failed-attempt classification; report SHA-256:
`A2FDD2C96F0CE2DB16F65DA542E47BB17DE24506CA9B2E153B371575AE68E5DF`.
Cleanup retained a recoverable feature bundle. This closes this packet's
integration; prior failures and the wider UX15/phone limits above remain.

### UX16 — Make chart axes, comparisons and sparse data honest

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX16-AC01 | P1 | F05, F10 | Jan 1, Jan 2 and Apr 1 samples do not imply equal elapsed intervals without an explicit session-axis label. | Not executed |
| UX16-AC02 | P1 | F05, F10 | Screen-reader/data-list users can recover values and dates without relying on the plotted line. | Not executed |
| UX16-AC03 | P1 | F05, F10 | Bodyweight-rep series use reps, loaded series use the selected physical unit, and empty/flat series are truthful. | Not executed |

### UX17 — Explain the body map as recorded muscle workload

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX17-AC01 | P1 | F02, F11 | Participants can explain that an uncolored muscle means no mapped work in the selected period, not a recovery guarantee. | Not executed |
| UX17-AC02 | P1 | F02, F11 | All muscles remain reachable via full-size text rows with equivalent detail/actions. | Not executed |
| UX17-AC03 | P1 | F02, F11 | The selected window and contributor data agree; caption remains readable at font 2.0. | Not executed |

### UX18 — Support mixed unit preferences deliberately

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX18-AC01 | P2 | F10, F12 | All four kg/lb × km/mi combinations render consistent input, summary, history and export behavior. | Not executed |
| UX18-AC02 | P2 | F10, F12 | Changing preference does not change stored distance or weight. | Not executed |
| UX18-AC03 | P2 | F10, F12 | Old backups lacking distance preference restore to the documented default and drafts do not reinterpret numbers. | Not executed |

### UX19 — Make bodyweight history correctable and clearing explicit

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX19-AC01 | P2 | F05, F10 | Correcting an older synthetic measurement changes that date and refreshes the affected review. | Not executed |
| UX19-AC02 | P2 | F05, F10 | Clear/delete copy identifies the exact affected data; cancelling leaves it unchanged. | Not executed |
| UX19-AC03 | P2 | F05, F10 | No bodyweight entry is required to record a workout or complete setup. | Not executed |

### UX20 — Reduce Settings scanning without burying recovery

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX20-AC01 | P2 | F07, F11 | A participant can change units/rest preferences and find export/restore with no worse success than the baseline. | Not executed |
| UX20-AC02 | P2 | F07, F11 | Reopening Settings restores useful context without hiding an unresolved save error. | Not executed |
| UX20-AC03 | P2 | F07, F11 | Production and debug build screenshots are evaluated separately. | Not executed |

### UX21 — Specify backup/recovery as a truthful multi-stage task

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX21-AC01 | P1 | F12, F08 | Wrong password, oversized input, unsupported version, offline Drive, live workout, partial preferences failure and completed restore each have a distinct actionable screen. | Not executed |
| UX21-AC02 | P1 | F12, F08 | Displayed success follows the actual accepted operation; no UI-only fix masks R01–R04. | Not executed |
| UX21-AC03 | P1 | F12, F08 | Back/rotation/permission return retains the operation's correct stage and never repeats a destructive commit. | Not executed |

### UX22 — Preserve context through navigation, notifications and external screens

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX22-AC01 | P1 | F04, F09 | Repeated delivery/recreation of the same reminder intent does not stack duplicate live screens or start twice. | Not executed |
| UX22-AC02 | P1 | F04, F09 | Returning from system settings keeps the active session/draft and reflects the real permission result. | Not executed |
| UX22-AC03 | P1 | F04, F09 | Back from a completed summary cannot reopen the finished workout as live. | Not executed |

### UX23 — Create a shared language for loading, failure, saving and retry

**8 October workout follow-up:** Quiet #460 is reviewed, integrated and reverified.
Before Home/History design, `codex/workout-truth-recovery` addresses truthful live
and saved-session notes feedback, reachable retry and serialized exit/finish saves;
factual missing-session copy; and an Add-a-set explanation tied to its existing
decision. This extends AC01–AC03 within the workout, without closing their broader
cross-app scope. Frozen source, including later review corrections, passed the
complete local gate, including the notes-guard text-proof repair across nine
profiles. The corrected 222-test native rerun passed both previously failing
Why cases; the original failures remain preserved. The subsequent missing-workout
message edit passed the fresh Full08 complete local gate across nine profiles.
Native03 then exposed an incomplete draft snapshot in its Why fixture. That
fixture's synchronization repair compiled under the required Full09 command,
which reused unchanged Full08 JVM results. Native04 then passed all 222 native
tests on the corrected source. Draft PR #461 now records pre-repair candidate
`f8943ae5`; the later hosted CI failure and fresh Full11 fixture verification are
recorded below. An amended immutable pin, fresh independent/adversarial reviews
and updated hosted checks remain required before integration.
Fresh provisional reviews added forward History Repeat/Resume and outside-editor
Finish to that same notes-safety acceptance: protected Back/Repeat/Resume requires
a notes save or explicit recovery choice before leaving or starting another
workout, and an unresolved kept draft or live writer cannot be cleared by Finish
from the bar. Explicit forward discard
leaves the retained History editor usable on return. Exercise Details and Rest
pushes retain the editor/cache owner. The later review corrections passed the
local mechanical checks below.

**Retained targeted failure, 8 October:** `targeted-attempt08` executed 182 tests
across 32 fresh suites with 18 failures, zero errors/skips and 1,521 unchanged
source inputs. Nine failures exposed real 40 dp compact recovery targets;
their minimum is now 48 dp with bounds/scroll checks retained. Nine other
failures came from render/focus fixtures addressing the wrong native window;
those fixtures are repaired. The later failing rerun and complete passing gate
are recorded separately below.

Further provisional review found stale-snapshot and post-check notes-write
races in outside-editor Finish. The current repair reserves the session before
its first read, shares the notes-write gate through actual Finish/cache clear,
and holds the editor lock for that interval. Failed Finish unlocks without
discarding the authored draft. History Repeat/Resume stays queued behind the
notes barrier. A failed forward-discard reload pauses autosave for the exact
draft revision until a new edit or explicit Retry. Three Room-backed Finish
regressions and a failed-discard regression cover these repairs. Their source
passed the complete gates below, including later corrections. Complete native
verification and reviews were pending at that stage; Native04 is recorded below.
These internal Finish/cache repairs add no schema,
backup format, public API or coaching-rule change.

`targeted-attempt09` ended incomplete with zero fresh XML and no completed test
count. Its old XML, notes-render scroll/idle loop, worker dump and identity-checked
termination of the owned worker are preserved; all 1,521 inputs were unchanged.
`targeted-attempt10` freshly executed **187 tests across 32 suites: eight
failures, zero errors/skips**, with 32 fresh XML files and 1,521 unchanged inputs.
Seven failures assumed immediate DAO entry before the real Room prewrite read;
the repaired fixtures await actual entry while preserving the 400 ms deadline
and notes assertions. All seven cases passed in `full-gate-attempt05`.

The eighth failure was the landscape/font-2.0 End workout notes toggle: the
unscrolled touch did not expand its field. `end-landscape-probe01` then freshly
ran **one test, one failure, zero errors/skips**, with unchanged 1,521 inputs.
Its `f7778a13-e615-4173-8057-e313ba488829/800x360-font2.0` geometry shows the
toggle fully clipped one pixel below the 236 px text viewport while Save/Leave
without saving remain fully visible at 56 dp. A bounded-scroll/actual-touch
driver repair preserves the clipping, target and exact-record checks; all nine
notes profiles then passed. These failures remain archived under
`build/ux-context/runs/workout-truth/`.

`full-gate-attempt05` freshly passed **3,701 tests across 554 suites, zero
failures/errors/skips**, with all 554 XML files fresh and 1,521 stable source
inputs. From `2026-10-08T23:13:30.6250547Z` to `2026-10-08T23:24:09.8255104Z`, the
four required tasks ran with `--rerun-tasks`; all **135 actionable tasks executed**
with builds, lint, Android test assembly and static checks passing. The seven
Room-timing cases and nine each Notes/Why/Missing profiles passed. The Q01 record
pins the archived uncommitted source and summary hash; no candidate commit was
pinned at that run.

Three subsequent **P2 corrections passed in `full-gate-attempt06`**: protected Back
must allow a confirmed finished session despite stale exit intent in both
same-process and cold-cache cases; a queued kept-draft live exit must reject late
IME edits; and the live notes Leave guard must state its current-app-run retention
limit. The copy executed in this attempt was: “Notes are not saved. This draft is kept while the app
runs and may be lost if the app closes.” After popping the editor, failed raw notes
are in process cache; no durable-store/process-death guarantee is claimed.

`full-gate-attempt06` freshly passed **3,702 tests across 554 suites, zero
failures/errors/skips**, with all 554 XML files fresh and 1,521 stable source
inputs. The required four tasks ran with `--rerun-tasks` from
`2026-10-08T23:27:22.0464651Z` to `2026-10-08T23:37:53.4235623Z`; all **135
actionable tasks executed**, with builds, lint, Android test assembly and static
checks passing. The new stale-finished-route Room case, held queued-Back/late-IME
case, all nine changed Notes-guard/nine Why/nine Missing profiles and five loaded
shipping-tab renders passed their then-current assertions. The Q01 record pins
the summary and stable source manifest hashes for that uncommitted executed
source.

After that gate, the landscape/font-2.0 notes-guard capture showed its last line
below the text viewport. The fixture compared the scrolling Text node's viewport
with its visible bounds, which did not prove the whole message was exposed.
This is a verification gap, not proof that scrolling cannot reach the line. The
live/History copy and full text-layout/per-line glyph checks were subsequently
repaired and passed the new complete gate below. The original capture and
executed source remain preserved; the earlier pass does not gain that proof
retroactively.

The owned, unfiltered, offline API-29 `native-suite-attempt01` completed **222
tests across 32 classes: two failures, zero errors/skips**, with all **1,521
source inputs unchanged** and **83 host/device-hash-matched captures**. The eight
new notes and missing/failed-read recovery cases and the connected workout
journey passed. Both new Why cases stopped at an exact summary assertion that
expected only a fragment of the actual full sentence. Their subsequent facts,
Keep/Use actions and no-write assertions were not reached; the fixture was
corrected to check the full sentence for the next run. This is a failing native suite, not a pass
or a demonstrated coaching-decision defect. The Q01 record pins its preserved
summary, source and original failures. The collector stopped, packages were
removed and network settings restored.

`full-gate-attempt07` freshly passed **3,702 tests across 554 suites, zero
failures/errors/skips**, with all **554 XML files fresh** and **1,521 stable
source inputs**. The required four tasks ran with `--rerun-tasks` from
`2026-10-08T23:50:13.2875720Z` to `2026-10-09T00:00:54.5239809Z`; all **135
actionable tasks executed**, with builds, lint, Android test assembly and static
checks passing. Both shorter, truthful notes warnings now have full-text,
final-character, unclipped-line/no-ellipsis and per-line rendered-glyph proof at
the actual OS font across all **nine notes profiles**, alongside retained real
touch/action-size/exact-record checks. Nine each Why/Missing profiles and the
five loaded shipping tabs also passed. The agent visually reviewed complete
live/History messages and actions at landscape/font 2.0; the Q01 record identifies
the native-graphics JVM captures and pins the preserved summary/source.

The owned, unfiltered, offline API-29 `native-suite-attempt02` completed **222
tests across 32 classes, zero failures/errors/skips**, with **1,521 stable source
inputs** and **85 host/device-hash-matched captures**. Both corrected Why cases
reached their facts, Keep/Use actions and draft-only/no-write/no-rest assertions;
the eight new notes/read-recovery cases and connected workout journey passed
again. The Q01 record pins the preserved summary/source/XML/log/capture hashes.
The collector stopped, packages were removed and network settings restored.
Native01's original failures remain preserved.

After visual review, the agent clarified the missing-workout body on the live
floor and Rest screen to “This workout is not running. If you finished it, look
in History.” The two production strings and matching JVM/native expectations
changed; recovery actions and data contracts did not. These edits postdate
Full07/Native02 and passed the fresh complete local gate below; prior passes do
not certify the later source edits.

`full-gate-attempt08` freshly passed **3,702 tests across 554 suites, zero
failures/errors/skips**, with all **554 XML files fresh** and **1,521 stable
source inputs**. The required four tasks ran with `--rerun-tasks` from
`2026-10-09T00:08:02.1849942Z` to `2026-10-09T00:18:37.9961652Z`; all **135
actionable tasks executed**, including required builds, lint, Android test
assembly and static checks. All **nine Missing-session profiles** passed with
the clarified copy and retained full-text/glyph, bounds, scroll/touch-target and
exact-record checks. The nine-profile Notes/Why matrices and loaded shipping-tab
smoke coverage also passed. The Q01 record pins the preserved summary/source.

Portable Full08 evidence is linked in the Q01 record: [live notes guard
N14](../ux-context/studies/q01/assets/N14-live-notes-guard-landscape-font20.png),
[failed History guard N15](../ux-context/studies/q01/assets/N15-history-notes-guard-landscape-font20.png),
[missing workout N16](../ux-context/studies/q01/assets/N16-missing-workout-font20.png)
and [reached extra-set explanation N17](../ux-context/studies/q01/assets/N17-extra-set-explanation-landscape-font20.png).
All are synthetic native-graphics JVM/font-2.0 captures with origin/copy hashes
matched; N17's earlier callout is partly scrolled out. They establish their
recorded fixture frames, not physical-phone acceptance.

The owned, unfiltered, offline API-29 `native-suite-attempt03` completed **222
tests across 32 classes: one failure, zero errors/skips**, with **1,521 stable
source inputs** and **85 host/device-hash-matched captures**. The Why Keep case
snapshotted **87.5 kg × 10 with no effort** before the full manual draft arrived;
after Keep, equality observed the intended **87.5 kg × 12 at effort 8**. The
failure is an incomplete fixture precondition, not demonstrated mutation of a
complete draft by Keep. Its raw failure is preserved; Q01 pins the summary.
Collector, package and network cleanup completed.

The sole subsequent source change waits for all three draft values and asserts
the complete precondition before Why. Post-Keep equality, stored-row/no-SQL and
no-rest checks remain. Production and JVM inputs are byte-identical to Full08.
`full-gate-attempt09` completed the normal required four-task command: builds,
lint and Android test assembly executed, while `testDebugUnitTest` was
**UP-TO-DATE**. Of 135 actionable tasks, **11 executed and 124 were up-to-date**.
With **1,521 stable source inputs**, it executed **zero new JVM tests** and reused
all **554 byte-identical XML files** from the fresh Full08 **3,702-test pass**.
This is explicitly reused evidence, not a new JVM execution. Q01 pins the
preserved summary/task log/source comparison.

The owned, unfiltered, offline API-29 `native-suite-attempt04` completed **222
tests across 32 classes, zero failures/errors/skips**, with **1,521 stable source
inputs** and **85 host/device-hash-matched captures**. Both Why cases passed with
the complete intended draft precondition and retained equality/stored-row/no-SQL/
no-rest assertions; notes/read recovery and the connected workout journey passed
again. Q01 pins the preserved summary/source/XML/log/capture hashes. The collector
stopped, packages were removed and network settings restored. Native03's original
failure remains preserved separately.

A clean-trunk full rerun and affected connected journey remain required after
integration. The separate ignored N2 driver APK build passed `assembleDebug` and
`lintDebug` with 43 executed tasks; the separate controller compiled and packaged
with 50 executed tasks. The first setup timed out awaiting the fresh catalog
after registering a partial session and before writing a set; its exact cleanup
passed and restored seven raw preference keys. The ignored controller's normal
idempotent catalog-seed prerequisite was then repaired, the separate builds
freshly passed again and one named setup method passed. Actual UI traversal used
battery **Not now**, then overlay **Open settings**, enabled it and returned.

N2 `measure01` ended **incomplete before editing**: actual live AppNav started
foreground `RestTimerService` with `EXTERIOR_SYNC` despite no running rest, so
the driver's no-foreground-service precondition rejected the branch. No notes
clear or Recents task removal occurred; no `<400 ms`, process-death or deletion
pass is established. No service, timing threshold or driver guard was changed.
A separate passing inspect method verified the same session/set/date/start/
exercise records, no running rest and unchanged notes. A separate passing
cleanup method removed only the registered fixture and verified all seven raw
preference presences/values and mapped settings restored. Root restored overlay
default and original Wi-Fi/data `1/1`, retained the active default-network `104`
capture, verified the three owned APK identities and removed only those packages.
Q01 pins the preserved runtime summary and phase evidence. Experiment builds and
named runtime methods are excluded from the permanent 222-test native count;
unchanged readback after the rejected precondition does not close N2 recovery.

Draft [PR #461](https://github.com/sinura7/PersonalTrainer/pull/461) records the
committed pre-repair candidate `f8943ae5`. Full11 freshly passed the fixture-only
synchronization repair below. An amended immutable pin, fresh independent/
adversarial reviews and updated hosted checks remain required. Integration and post-merge
verification remain pending; N2 and physical-phone acceptance remain open.
No owner-phone test or Debug drop is recorded for this development work.

See the [Q01 follow-up record](../ux-context/studies/q01/README.md#latest-targeted-verification-and-pending-repairs--8-october-2026)
for the attempt boundaries. These updates do not mark UX23-AC01–AC03, the
related UX24/UX25 recovery-target coverage, or any broader matrix complete.
Quiet's earlier post-merge 3,625-test local and 212-test native passes remain
valid for that completed packet only; broader work and phone acceptance remain
open.

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX23-AC01 | P0 | F08, F11 | Injected initial/later load failures, invalid fields and post-commit side-effect failures produce the defined states. | Partially: summary read/compute faults, composer save faults, finish outcomes distinguished; injected load faults on other screens remain open (see UX23 residue) |
| UX23-AC02 | P0 | F08, F11 | Users can locate the error and retry at the point of action without losing authored data. | Implemented for the composer (error above Save, field errors inline); device/IME check pending |
| UX23-AC03 | P0 | F08, F11 | Missing, empty and unavailable never share a misleading generic success or start-new-workout remedy. | Implemented for summary, finish and History stale wording; remaining conflations listed in UX23 residue |

### UX24 — Apply responsive type and layout rules to actual content

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX24-AC01 | P1 | F06, F11 | Two long similarly named variants can be distinguished before selection. | Not executed |
| UX24-AC02 | P1 | F06, F11 | At font 2.0, primary actions and full values/units are readable and not clipped. | Partial JVM evidence for Home/Plan week content and Plan day/picker questions/actions; broader value/unit and content clipping remain open (see UX12 packet). |
| UX24-AC03 | P1 | F06, F11 | Shared component changes preserve all callers and do not reduce touch targets. | Partial JVM evidence: week/picker geometry, Plan day Back and actual Body/History entry controls retain measured 48 dp targets; bounded Home/Plan review/integration complete through #464, broader caller acceptance open. |

### UX25 — Validate complete-screen accessibility, not just component tags

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX25-AC01 | P1 | F11 | Physical TalkBack can complete planned start, set log/edit, cardio finish, routine create, history correction and export review. | Not executed |
| UX25-AC02 | P1 | F11 | Search and routine-name fields announce their purpose when empty and populated. | Not executed |
| UX25-AC03 | P1 | F11 | Large text/IME does not hide essential actions; measured issues have production-screen regression coverage. | Partial JVM evidence for bounded week/picker and shared-header reachability; AppNav/insets, phone IME and broader accessibility remain open. |

### UX26 — Tune feedback timing without adding decorative motion

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX26-AC01 | P2 | F03, F08, F11 | With reduced motion enabled, all data and records appear immediately and remain accessible. | Not executed |
| UX26-AC02 | P2 | F03, F08, F11 | An extended accessibility timeout is honored for relevant messages or equivalent persistent access exists. | Not executed |
| UX26-AC03 | P2 | F03, F08, F11 | Save failure remains actionable after any transient banner disappears. | Not executed |

### UX27 — Make exercise discovery precise and stateful

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX27-AC01 | P2 | F06, F08 | A participant finds a specified muscle+equipment variant and clears a restrictive filter without losing orientation. | Not executed |
| UX27-AC02 | P2 | F06, F08 | Returning from detail restores the original results position. | Not executed |
| UX27-AC03 | P2 | F06, F08 | Load failure does not invite creating a duplicate exercise because the catalogue appeared empty. | Not executed |

### UX28 — Improve setup preview and returning-user orientation

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX28-AC01 | P2 | F07 | A user can inspect the proposed week, change equipment/days and return to an updated preview with other answers preserved. | Not executed |
| UX28-AC02 | P2 | F07 | An existing user can explain the acceptance impact before tapping Use this plan. | Not executed |
| UX28-AC03 | P2 | F07 | Dismissed/failed setup does not create a partial unexpected program. | Not executed |

### UX29 — Make activity-type differences explicit in details and receipts

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX29-AC01 | P1 | F05, F08, F10 | Each supported type has an accurate receipt and correct units with clear action capabilities. | Not executed |
| UX29-AC02 | P1 | F05, F08, F10 | Opening history detail does not replay a misleading new-save confirmation. | Not executed |
| UX29-AC03 | P1 | F05, F08, F10 | Repeat/edit where supported preserves correct IDs, chronology and plan linkage and cannot silently convert one type to another. | Not executed |

**Active bounded F9 / W11 / V12 / Q08 packet:** The connected native receipt
shows Summary's missing lift artwork and unqualified planned detail metrics
beside different saved results. Implement O11's matching identity, complete
readable names, explicit Planned / Recorded sets and timed hold targets, with
exact-ID real-store and native-graphics regressions. The targeted gate now
passes; complete and connected verification and final reviews remain pending;
this does not close all activity-type receipts, participant acceptance or the
deliberate read-once Summary return-after-correction contract. That return path
needs an actual AppNav counter and policy review before a refresh change.
The existing Summary top-set model omits saved hold duration and can display
zero reps for a pure hold. Detail's duration fix does not close that Summary
issue; retain it for an actual hold counter and a separate display packet,
without changing training calculations.

**Receipt targeted verification (9 October 2026 UTC):** `targeted01` finalized
37 cases / eight fresh suites with one failed native final-glyph assertion and
zero errors/skips. The original six PNGs remain archived. An independent pixel
probe of the exact final `g` in `140 kg` found zero TextPrimary and 92
TextSecondary pixels: the annotated unit uses its own color. The corrected
helper resolves actual span colors while retaining every-line ink, actual
clipping, complete character layout and final-glyph assertions. It reveals the
actual decorative image square without requiring an arbitrarily tall parent
header to fit; action targets still require full visibility and 48 dp bounds.

Fresh `targeted02` passed **51 tests / eight fresh suites**, zero
failures/errors/skips, from **17:28:18.3478399Z–17:32:34.1321666Z**, with all
**1,545 runtime inputs** and references unchanged. Its summary SHA-256 is
`8EA946FF59F58F46D8076DCF36CCD5121E7E8853C4A413E81101CB95E45C961F`.
The archived **400 fresh native-graphics PNGs / 800 files** cover 14 real
resource profiles (360×640, 412 dp, landscape and 600 dp at fonts 1.0/1.6/2.0,
plus 320 dp font 2.0 and RTL font 2.0) and independent artwork/fallback identity.
The real-store tests exercise read faults and Retry; rendered read outcomes
use controlled UI state and verify exact callbacks, not a simulated successful
database retry. Read-only route/correction callback checks leave all synthetic
rows unchanged. Complete local/native gates and final review must still execute.

**Retained complete-gate failure and visual counter:** Clean `fc68ebb1`
`full01` finalized **3,939 app cases / 573 fresh suites**, one failure and
zero errors/skips, with **1,685 standalone tests / 255 classes** passing and
1,545 unchanged runtime inputs. The connected JVM journey failed only its old
detail spoken-label lookup after its exact saved-row checks. Its summary
SHA-256 is `AE71156F2A107B1E76E9A1BC9CE0172A7F20032B28EEA5B8A996E36CDC821FB3`;
2,139 fresh PNGs / 3,595 files remain archived. JVM and native journey
expectations now require the explicit Recorded working label and the exact
saved-set ancestor, retaining all value/write/navigation assertions.

Independent targeted-render inspection also found semantic `3 × 5` visually
drawn `5 × 3` in RTL. `counter03` finalized two cases / two fresh suites with
one intentional operand-order failure, zero errors/skips and unchanged inputs;
the corrected connected JVM journey passed. Summary SHA-256:
`E2BB66EA3C5EDF33AAA2003F8B322D660DBAB09EC10C478D4AC2DAF89CAF45FE`.
The new probe confirms real ink and clipping for each operand/operator before
checking their measured order. The local planned metric now shapes mathematical
values LTR while retaining RTL card flow. The original targeted tests' green
text assertions did not establish operand order. Fresh full/native gates and
final reviews remain necessary; neither original failed run becomes a pass.

**Fresh receipt gate, 9 October 2026 UTC:** Clean `c49b19ba`, tree
`41c6c6bde06356854309b17ef5cdb09a65d72610`, passed `full02` from
**17:59:41.9925100Z–18:16:55.6149836Z**: **3,939 app tests / 573 fresh suites**,
zero failures/errors/skips, **1,685 standalone tests / 255 classes**, all four
required Windows tasks and all 135 tasks executed. All **1,545 runtime inputs**
and references remained unchanged and match the actual candidate Git blobs,
with only the established `gradlew.bat` CRLF checkout filter. Summary SHA-256:
`32E30AC0533861F3BF9D6EE9B249E62F0C759A4F4E9F841E22DBA44881BA3708`.
Runtime manifest SHA-256:
`78CFEEBFD9ADA78BDC0D62EC5C77EDBE6A2A189892E3964AD098B91D7C8F2AE7`.
The fresh archive contains **2,139 PNGs / 3,595 files**, including all **400**
receipt-profile and exact-artwork PNGs. Render manifest SHA-256:
`6179D7CD07502680037AD4CD5350D8EAA7C59262F68D25F2ABD5F8EF7C68E5A4`.
Actual RTL glyph evidence now shows ordered `3 × 5`; original failed runs remain.

Candidate `native01` stopped at the unchanged cold-boot admission guard on a
SystemUI `BOOT_COMPLETED` ANR before app installation: **zero tests and captures**,
no native suite exit code. Its summary SHA-256 is
`420D00B8BB516A5A4F5FD1CDD44E1CF07BC3565329FC05D4AF1EED7B19FAAC73`.
The underlying system cause remains unknown. Empty packages, restored settings,
collector cleanup and subsequent actual emulator-process absence were verified.
An unchanged fresh cold boot, `native02`, passed **222 tests / 32 classes** from
**18:20:56.0431468Z–18:27:33.1975217Z**, with zero failures/errors/skips,
**85 device-hashed captures**, all **13 fixture restoration pairs**, offline
admission before first app launch and verified network/settings/package cleanup.
All 1,545 runtime inputs and references still matched. Summary SHA-256:
`B64C0A05BB86726657D4A340B1F328D9D91A769194EEA8CE1CF263AE627252CE`.
The actual same-session AppNav receipt and detail both retain **255 kg**, the
exact corrected **85 kg × 3** set and corresponding exercise artwork; Detail
qualifies **3 × 5 / 140 kg** as Planned. These are **pre-integration candidate**
results; copied wrapper diagnostic labels do not establish postmerge evidence.

**Confirmed recovery blockers, 9 October 2026 UTC:** `counter04` ran from
**18:39:01.8727528Z–18:42:37.3732312Z**, against unchanged `c49b19ba` production
and two test-file additions: **seven cases / two fresh suites / four failures**,
zero errors/skips, exit 1, unchanged **1,546 runtime inputs**. Real BackupService
prepare/commit accepted both serialized graphs, created its safety copy and
completed its journal before shipping Detail inspection. A saved **45 kg × 6**
set contributed to **270 kg / one set** but its original row/Edit was unreachable.
Repeated exercise prescriptions crashed with a duplicate LazyColumn key while
scrolling to the second prescription. Domain counters separately reproduced
the omitted exercise and duplicated original SetLog. Every postrestore inventory
remained byte-identical through teardown; no record loss or wrong write is inferred.
Summary SHA-256:
`64A7028B3EDE1740115C3948BE8D7A5603CB1E6FCD899ADE198A0878A2E9FED4`.
The archive retains **eight PNGs / 28 files**; render manifest SHA-256:
`4F279DEE9C2BBEC5AC37C7E970D1D5FC952A857C123BFB085AB3595967770451`.
Both defects are high blockers for F9 / Q08 / UX23 / UX29 saved-work reachability.

The pending display repair groups exact exercise IDs, retains every original
prescription object and numbered program position, and exposes each original
SetLog/Edit once, including saved-only exercises. Interleaved repeated exercises
are grouped at first occurrence; original positions remain explicit and badges
may have gaps. Repeated prescriptions have a recorded-only count, not an inferred
completion denominator. Two independent 14-profile recovery render matrices
retain validated restore, graph equality and native glyph/clip/touch checks.
Fresh execution and complete gates must establish the repair. Final
independent/adversarial approval, hosted verification and integration remain
pending. Recovery takes priority over Summary hold refinement; the read-once
return policy stays open. No broader UX23/UX29 or physical-device closure is claimed.

`targeted03` retains the first repaired execution, **18:49:57.7847855Z–18:55:09.5603693Z**:
**142 cases / 11 fresh suites / 28 failures**, zero errors/skips, exit 1 and stable
1,546 inputs. All 26 non-RTL recovery renders passed original row, prescription
and Edit reachability before a test expected `Selected` for InstrumentChip's
actual `ToggleableState.On`. The two RTL cases stopped before restore because
native resource direction was still LTR. These are demonstrated test setup/assertion
defects; their correction must retain exact effort and real resource-direction
checks. The other 114 cases passed. The run is not a gate pass. Summary SHA-256:
`CB810612C9C37CF03FBEAE4E028094F9B459FA4A01C690447C7D65955F8F3BAF`.
All **589 PNGs / 1,334 files** remain archived, render manifest SHA-256:
`32C5B4A727F2F4B137B9C91ADC598C688DA5EE472F38F9D1FBADDC2DD20150BD`.

`targeted04`, **19:00:12.9896143Z–19:04:56.3179451Z**, retains **142 cases /
11 fresh suites / eight failures**, zero errors/skips and stable 1,546 inputs.
Both real Hebrew-resource RTL cases pass; 20 recovery cases and all 114 adjacent
cases pass. Eight RPE visibility inspections stop on the actual partially expanded
sheet: its visible handle offers Expand, while the inner ScrollBy action cannot
expand that anchored sheet. The next counter must perform the shipping Expand
action, verify settled geometry and then retain the whole 48 dp action/native
glyph checks. No production sheet defect or passing gate is inferred. Summary
SHA-256: `B4987E2DBDE144131FE295267D6DA4B367732C6F84904590722A94E377472305`.
The archive preserves **621 PNGs / 1,473 files**, render manifest SHA-256:
`4979F9DE9588B9991A5858011DB7918F7A728700434C761939DC5207596372D1`.

**Focused repaired execution:** `targeted05`,
**19:08:52.4669440Z–19:13:39.4774753Z**, passed the same **142 cases / 11 fresh
suites**, zero failures/errors/skips, all four named Windows tasks and unchanged
**1,546 runtime inputs**. All **28 independent recovery renders** pass their
validated full restore, original prescription/SetLog multiplicity, exact Edit
prefill, native glyph/order/clip and 48 dp action checks. Default anchor captures
remain; when offered, the actual Expand action must accept and settle the sheet
fully inside its window before effort inspection. Every restored inventory
remains unchanged through expansion, inspection, real Dismiss and teardown.
Summary SHA-256: `153CE82D43CC44831232410476998F81DCA8A2EE08B7CC03BFBF223BD477763F`.
Runtime manifest SHA-256:
`4B8055DCACCF7B518363C2DB240B3A1BA8E141ADB16A119EBF8FDAD2579D38BC`.
The archive contains **685 PNGs / 1,681 files**, render manifest SHA-256:
`7C2C42C3C200E1D58F598E611FE6C841F1EA4B90642E2AFAD0B85F5BE2DD7DE6`.
Root inspected the actual formerly hidden row and both preserved repeated targets.
Original red runs stay red. This is a filtered pre-integration pass; standalone
preflight, clean complete/native gates and both final reviews still remain.

**Complete repaired gate remains failed:** Clean `9d988658`, tree
`edcb3af38115aff6f239e0dd09f671ab014e51f5`, ran `full03` from
**19:15:39.8962642Z–19:33:25.7412982Z**. The complete standalone preflight passed
**1,694 tests / 255 classes**; the unfiltered four-task Windows gate executed
**3,976 app tests / 574 fresh suites, one failure, zero errors/skips**. Its failed
History retry check includes a previous `progressFailed=true` snapshot after a
trace index sampled before retry; the independently awaited new pending state
passed. Application and test causes remain to be distinguished with a deliberately
delayed recorder, preserving the original assertion and failed XML. No prior pass
waives this failure. All **1,546 inputs** stayed unchanged. Summary SHA-256:
`2C951F657577151628DF678DC460AB0A12A41851A7C5D021AE771B6DB3C79889`;
execution seal: `D1A2C82642D65368948E644A166F974F10AF3AA575258F8A97CA9F6A404936B4`.
All **2,413 PNGs / 4,465 render files** are preserved, manifest SHA-256:
`1C23E4FD21E653176CD007C6FFB5F7E1F3E30D8DDF62BD2E3B491543C17A2283`.
The new native run is deferred while a separate supported-restore/actual-editor
counter tests the source-predicted conversion of an original weighted timed hold
outside the prescription graph into repetition work. Planned holds are controls;
the raw stored row, volume and full unrelated inventory are the safety oracles.
No wrong write or safe correction is credited before executing that counter.

**Executed causal counters:** `counter06` stopped before tests because one new
`BackupSetLog` call mixed positional/named arguments and exceeded the existing
182-skip ceiling. It has zero fresh XML/cases/frames; no pass is inferred from
old results. Only that call became fully named with identical values, retaining
all checker limits. Fresh `counter07`, **19:47:52.3585034Z–19:51:36.6384608Z**,
executed **six cases / two fresh suites, two failures, zero errors/skips**, with
**1,547 unchanged inputs**. The original retry test passed. The delayed recorder
received the old FAILED before recovery, appended it at index 4 after the index-4
cut, then acknowledged a genuine new pending state at index 5. The recovery query
remained held/unreturned and every state from that new boundary passed the loading,
null-result and no-failure assertions. The unchanged old-cut helper failed on the
prior snapshot. This establishes a recorder observation error without a production
History change; the original full03 failure remains failed evidence.

The separate full supported-restore/shipping-editor hold counter confirmed a high
wrong-type write: original **12.5 kg / reps 0 / 45 seconds / no effort** became
**reps 1 / RPE 8**, retaining load/duration/ID/time, after actual +1/effort/Save.
The stored row and resulting native receipt are retained before the failed type
and teardown-inventory assertions. This invents **12.5 kg repetition volume**.
Untouched saved-only Save explicitly refused `Reps must be at least 1`; both planned
hold controls passed. Do not infer safe saving from the unchanged refused row.
Summary SHA-256: `C8199D4F809C22873889041D8C4F15AB51C5211BF850C7F5D28B544B9D95AC9D`;
input manifest: `76798765731DFBE8278CFE0BF513553DC2CB6445AC8AA931DFDD455B029A8183`;
execution seal: `767F3C78D7F26FD12AFEC89BE57E4F830EFF78F7845B98FFCF8826166ED95AFF`.
All **32 PNGs / 107 files**, including complete raw inventories and recorder
chronology, are preserved; render manifest SHA-256:
`7FCFA8CC6607C206254859E3171AAA61C0E2BC8C66AE7C0699CABA2AFA122CA9`.
The repair uses the original saved positive-duration/nonpositive-rep distinction
already used by work/copy, preserves its exact representation, retains the planned
hold fallback and leaves strict load/weight rules unchanged. Positive-rep stopwatch
controls and accepted older negative-rep timed backup preservation require fresh
execution. No schema, validator, backup format or public API change is planned.

**Fresh repaired focused pass:** `targeted06`,
**19:59:18.7955314Z–20:04:41.6566426Z**, passed **214 cases / 18 fresh suites**,
zero failures/errors/skips, all four required Windows tasks and unchanged
**1,548 inputs**. All six independent supported full-restore/editor cases now
require successful no-refusal Save. Original zero-rep and literal -3 timed rows
retain their exact representation; +1 with effort 8 changes only effort, while
+1 with all effort controls off retains null effort and all original fields.
Planned controls also pass. Every inventory from restore through teardown remains
exact apart from the specifically requested original-row effort. Seven real-Room
controls verify normal stopwatch strength, zero-rep strength refusal, unknown-load
zero/invalid-weight refusal, explicit repository duration correction and unrelated
saved-row/session preservation. Duration editing through the sheet remains open.

All 22 History period checks pass, including original retry and delayed recorder
with acknowledged recorded boundaries, strict held tails and actual recovery.
Production History and its keyed ownership rules are unchanged. All 28 restored
graph render cases and the existing Summary/detail matrix still pass. Summary
SHA-256: `F22CF19CFF89F31F54858BFBF737F73983E01914BFDA44A241CF87F6DC05353A`;
input manifest: `D2EC308FBC523208B99F5C79B82E560285BEC50C47F10AA512C8AD9A31967A2F`;
execution seal: `BC314AFBD82B0DDA707473F3B5F8B52C9774DE50FEDFD1D7D9B7656BBB7AA61A`.
The fresh archive preserves **733 PNGs / 1,842 files**, manifest SHA-256:
`952E0F33E5843B003789DF9D7E217AC1EF7965E4F09511ED6FE3DEE5174DA7CE`.
This remains filtered pre-integration evidence; complete standalone/unfiltered
verification, fresh native execution and both nonauthor final reviews are pending.
Original failed runs and their red causal evidence remain preserved.

**Complete repaired candidate gate and final reviews closed:** Clean
`f2504264ecb52f9bcd4baec231945ae71f06fa96`, tree
`20b6d0c04fa0c9635fa3f9cc8027ba55a857b451`, passed fresh `full04` from
**20:06:12.5372885Z–20:23:52.8194519Z** on 9 October 2026 UTC. The complete
standalone preflight passed **1,694 tests / 255 classes**; the unfiltered four-task
Windows command with `--rerun-tasks` passed **3,990 app tests / 576 fresh suites**,
zero failures/errors/skips, with all **135 tasks executed**. All **1,548 inputs**
and references stayed unchanged. Summary SHA-256:
`9A292AA43863603069826A69A6A0766F0174540E373C2312415AAA5F24DBC136`;
input map: `D2EC308FBC523208B99F5C79B82E560285BEC50C47F10AA512C8AD9A31967A2F`;
execution seal: `A67B949FAE0A6BEF6EA616F613969D4E2874898822AE863733BAFC9E31BF29EA`.
The archive contains **2,461 PNGs / 4,626 files**, manifest SHA-256:
`B3B87E41C01120E4358D56B8967EFC02486F3EA2A88553DCA9543C4F9F79C52D`.
The full run retains all 28 restored-graph cases, six successful actual hold saves,
seven Room safety controls and 22 History period cases; every original failed
execution and its causal evidence remain separate.

Fresh unfiltered `native03`, **20:24:48.1680776Z–20:31:28.3699991Z**, passed
**222 tests / 32 classes**, zero failures/errors/skips, one fresh device XML,
**85 device-hashed captures** and all **13 fixture restoration pairs**. Offline
admission, network/settings restoration, empty final packages, collector cleanup
and the same 1,548 inputs were verified. Summary SHA-256:
`3E395CB1CCE5251A25C9D3A25D00BC52793BD9AE94111868F85CCC425363E641`;
execution seal: `715C8AC1187D12C43AE60023E95486627B4B1210AD6A0A4F2E39AC427EE0015D`.
The connected AppNav Summary/detail images show the exact corrected
**85 kg × 3 / RPE 9**, **255 kg** total, matching Squat artwork and explicit
**3 × 5 / 140 kg** Planned targets. Actual emulator absence after shutdown was
separately observed at **20:32:14.8060556Z**, receipt SHA-256:
`E42C1AA2626A88EA170054F6722D5C602FF5FADD294821B5095A0475E5350C07`.
Actual committed Git blobs match the complete runtime map with only the accepted
`gradlew.bat` CRLF conversion; binding receipt SHA-256:
`B76934C546F806EDBFE8ED8189F1157E650E78BF2C6C63FD793E0875EDE043A0`.

Fresh nonauthor final reviews of the **entire 25-file `9fd21456` → `f2504264`
packet** approve integration with no unresolved critical/high finding. The
independent report SHA-256 is
`F3AD606AA26E1A954AB64E793E3AE84B1C97448CC0C77240767F9C304719202E`;
adversarial report SHA-256 is
`9398B1C4FEB1A432B1D9C1621B61175FA335A95AE5039889725858BAAAEE83BC`.
Both inspect source, raw results, original-row inventories, relevant current
frames and actual cleanup. This is pre-integration development evidence: hosted
checks, integration and actual clean-trunk full/native gates remain pending.
The named medium editor refinement below, pure-hold Summary display,
correction-return snapshot policy, broader UX23/UX29 and physical-phone acceptance
remain open. There is no schema, backup format, validator, navigation, signer or
version change, and no Obtainium drop was requested.

**Named dependent refinement — Set Edit sheet identity and numeric controls
(F9/W11/V12/Q08/UX29):** Fresh full03 frames show the identity kicker truncating
at font 2.0 and the actual default 360 dp ±2.5 labels splitting into two lines.
RPE 10 reflows at 320 dp/font 2.0, but the observed viewport alone does not prove
permanent clipping. Holds have no dedicated duration correction control. Exact
original row and value/effort prefill are verified by targeted05; no incorrect
increment is inferred from label wrapping. Retain these observations for the next
editor refinement, while any demonstrated wrong-type save blocks this packet.

### UX30 — Establish an evidence-led design delivery and regression process

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX30-AC01 | P1 | F01, F02, F03, F05, F11, F12 | Each implemented UX item has acceptance evidence, source/commit links and limitations. | Not executed |
| UX30-AC02 | P1 | F01, F02, F03, F05, F11, F12 | Global-navigation changes follow the repository's current comparative task gate; no extra tab appears from taste alone. | Not executed |
| UX30-AC03 | P1 | F01, F02, F03, F05, F11, F12 | Required device/accessibility gaps remain visibly open until actually tested. | Not executed |

**Final repository formatting gate (8 October 2026):** The staged check exposed
CRLF in one new JVM fixture; exact CRLF-to-LF normalization preserved its entire
source text and assertions. Full10 passed the normal required gate (24 executed,
2 cached, 109 up-to-date tasks), reporting zero new JVM tests and 554 XML files
byte-identical to Full08's fresh 3,702-test pass. All 1,521 inputs remained stable;
production/Android inputs at that snapshot still match Native04. Summary SHA-256:
`c549d8413f8d1cb0d41e59df5fadbb66bffe7f6b21192cced079280ef3567975`.
Pinned rereviews/integration and clean-trunk fresh verification remain required;
N2 and broader acceptance remain open. This formatting pass
does not add executed tests or close an acceptance criterion.

**Hosted CI failure and fresh fixture verification (8 October 2026):** Push
[CI 37867942005](https://github.com/sinura7/PersonalTrainer/actions/runs/37867942005)
completed **3,702 tests with one failure** in the deterministic `Tests, lint,
debug build` job. The sole failure was
`DeletedNoteStaysDeletedTest.wordsTypedJustBeforeAProcessDeathComeBackAndAreWritten`:
its immediate exact-once assertion saw no DAO write after **401 virtual ms**.
PR [CI 37867945752](https://github.com/sinura7/PersonalTrainer/actions/runs/37867945752)
passed all deterministic steps; both hosted native jobs passed. Those results
retain their own source boundaries and do not turn the failed push gate green.
The original log, reports and fixture are preserved under
`build/ux-context/runs/workout-truth/hosted-ci-diagnosis/`.

The author and independent reviewer identified that the transactional live-row
read can still await real Room I/O before notes DAO entry when `runCurrent`
returns. Only the JVM restore fixture changed: both positive cases now use the
existing bounded `awaitWriteLanded` before their exact-once assertion and check
that virtual time stays **401 ms**. Negative checks, SavedState restoration,
cancellation/join, cache clear and actual-row checks remain. The dedicated
**399/400 ms** debounce test passed in the failed hosted run; production and
native code are unchanged.

Full11 freshly passed the frozen repaired source: **3,702 tests across 554
suites, all XML fresh, zero failures/errors/skips**, with **1,521 stable inputs**.
The required four-task command with `--rerun-tasks` ran from
**2026-10-09T01:27:17.3770067Z** to **2026-10-09T01:38:04.0240231Z**, returned
exit **0** and **BUILD SUCCESSFUL** in 10m 40s; all **135 actionable tasks
executed**. Restore **7/7** and Move **24/24**, including the dedicated
**399/400 ms** case, passed. Summary SHA-256:
`dc00702c2787049f1fecc1c99cfe673b32dcdef1bd7c979e97ed9a720599dc0d`.
Only `DeletedNoteStaysDeletedTest.kt` differs from Full10's runtime inputs;
production and Android bytes still match Native04. The original failed push
remains failed. Final reviews, hosted checks, integration and post-merge verification
were pending at that snapshot; the completed packet is recorded below. See the [Q01 hosted CI record](../ux-context/studies/q01/README.md#hosted-ci-failure-and-fresh-fixture-verification--8-october-2026)
for the source/manifest hashes. N2, UX23-AC01–AC03, related UX24/UX25 coverage and
physical-phone acceptance remain open.

**Bounded workout integration (9 October 2026 UTC):** [PR #461](https://github.com/sinura7/PersonalTrainer/pull/461)
merged `c2384951`, the same tree as independently/adversarially accepted `473fff3a`.
Fresh clean-trunk checks passed **3,702 JVM tests / 554 suites** and **222 native
tests / 32 classes**, all zero failures/errors/skips, with 1,521 stable inputs.
The original native archive failed on a PowerShell digest-parser bug; its exit 1
is preserved. A separately reviewed exit-0 recovery verified all 85 original
captures, unchanged raw execution/source and restoration, executing zero new tests.
Candidate and actual-trunk hosted reports also passed independently verified counts.
[Q01 records the exact pins and limits](../ux-context/studies/q01/README.md#workout-follow-up-integration--9-october-2026-utc).
This completes that packet's integration, not the broader criteria above.

**Current development:** Required-read/Retry recovery integrated through
[PR #462](https://github.com/sinura7/PersonalTrainer/pull/462) as `60c01a0b`,
followed by the bounded History chronology-fixture repair in
[PR #463](https://github.com/sinura7/PersonalTrainer/pull/463) as `c58110d1`.
Earlier attempts below retain their dated evidence boundaries. Home/Plan week
geometry integrated through [PR #464](https://github.com/sinura7/PersonalTrainer/pull/464)
as `5c7f869b`, with completed reviews and clean-trunk verification recorded under
UX12. History's one-period/coherent-progress work integrated through
[PR #465](https://github.com/sinura7/PersonalTrainer/pull/465) as `7a41c977`, with
independently verified actual-trunk local, native and hosted gates recorded under
UX15. Captured-date travel visibility then integrated through
[PR #466](https://github.com/sinura7/PersonalTrainer/pull/466) as `9fd21456`, with
fresh actual-trunk local, native and hosted gates and independent integration
review recorded under UX15. The active single F9/W11/V12/Q08/UX29 packet now
addresses Summary/detail identity and planned/recorded clarity; its verification
remains pending.
Legacy strength captured-date
storage remains a separately specified dependency. These integrations do not
close broader F4, UX23, UX24/UX25 or physical-phone acceptance.

The first executed Home attempt (`targeted04`) passed its static checks but failed
the screen tests: an open planned-start confirmation disappeared when recovery
content was inserted, and an Extra equipment choice reset during saved-state
restoration. Held weight-save checks also failed synchronization. Two thread
captures then showed the Undo test looping in a scrolling helper with its frame
clock frozen; only that owned test worker was stopped. The attempt remains failed
and incomplete: console diagnostics reported 144 completed, 8 failed and 1 skipped,
but no fresh XML was finalized, so no test passes are credited. Its 1,525 runtime
inputs and Git references remained unchanged. Corrections and a fresh run are
required before Home acceptance.

`targeted05` finalized 31 fresh XML suites: **225 tests, 32 failures, zero errors
or skips**, with the same 1,525 unchanged inputs and stable references. Shared-read
producer suites passed on that pin; the overall attempt remains failed. Sixteen
Home safety cases stopped in fixture setup because a rule created today cannot
produce yesterday's occurrence. Screen failures separately identified undersized
numeric action targets, held-frame synchronization, cached offscreen rows mistaken
for uncomposed rows, the encoded occurrence/session binding, invalid RTL qualifier
order, and the font-2.0 write-error layout transition. Corrected setup and fresh
execution must verify those contracts; none is dismissed as a Windows limitation.

`targeted06` stopped before tests when the static import check found an undefined
test reference; it has zero fresh test results. After correcting that reference
and seeding a valid current suggested day in the refusal matrix, `targeted07`
finalized two fresh suites: **31 tests, five failures, zero errors or skips**,
with 1,525 unchanged inputs and stable references. All 18 Home safety cases passed;
the 13 selected screen cases include four bounded pending-layout failures after
a failed weight write and one navigation observation while the frame clock is
held. At that point those failures awaited diagnosis and fresh execution; the
complete gate, full screen matrix and native journey had not passed the packet.

The next two attempts stopped before tests on the new test-call syntax; the
original checker ceilings were retained. Corrected `targeted10` passed all **43
Home safety/render cases**, with two fresh suites and unchanged inputs/references.
Its 112 fresh native-graphics frames include the complete size/font matrix and
RTL/reduced-motion states; explicit JVM focus/traversal remains separate from
phone IME evidence. The unfiltered `full-gate01` subsequently passed the complete
standalone preflight (**1,656 tests**) but failed the app suite: **3,770 tests /
557 fresh suites, one failure, zero errors or skips**, all 135 tasks executed.
The sole failed check required a weight dialog to close before awaiting the
actual write; its captured screen truthfully showed the exact value and Saving
weight. That pin required a corrected completion wait and equivalent restoration
boundary before a fresh unfiltered run; the original attempt remains failed.

**Fresh bounded Home verification (9 October 2026 UTC):** `full-gate02` passed the
complete standalone preflight (**1,656 tests / 251 classes**) and the unfiltered
four-task Windows gate with `--rerun-tasks`: **3,770 tests / 557 fresh suites,
zero failures/errors/skips**, all **135 tasks executed**. It ran from
**04:46:41.7694127Z** to **05:02:07.4378197Z**, with **1,525 unchanged runtime
inputs** and stable references. Actual save completion precedes the retained
dialog-close/acknowledgment assertions; refusal preserves exact draft and zero
write attempts, and success preserves exactly one intended row/write. Its
**112 fresh JVM frames** cover the required size/font matrix and RTL/reduced
motion, with explicit host focus/traversal separate from phone IME acceptance.
Summary SHA-256: `51f859d953a65afdaa3c4fffea17b159064fece925173e43760159eca6a60fde`.
Runtime manifest SHA-256: `9c14515decc42fa0d3e8ec2b076eb6063e8f4b9d4e025e024b70aeee4c2efa78`.

The first native attempt, `native01`, remains **failed: 222 tests / 25 failures /
zero errors or skips**. Its raw logs identify a SystemUI `BOOT_COMPLETED` ANR
and the resulting system dialog intercepting all 25 shell Back assertions.
The focused launcher startup check had not established completed boot receivers.
A separate host wrapper checked idle actual broadcast queues, launcher focus and
the entire boot log for ANRs before app installation, with bounded reads and
two completed samples. It retains real Back, permissions and every suite check.

Unchanged-source `native02` then passed **222 tests / 32 classes**, one fresh XML,
zero failures/errors/skips, from **05:22:36.6583977Z** to **05:29:16.4728453Z**.
All **85 fresh captures** matched device/host SHA-256; all **13 fixture pairs**,
network/settings restoration, empty final package inventory and collector cleanup
were verified. All 1,525 inputs still match the full-gate manifest. Summary SHA-256:
`018886134e0966b4cd6e0f89ae640ff8429beae1b2e2c00dedb1cff21e5b4e6a`.
The original failed run and its restoration evidence remain preserved. These
results cover this Home recovery packet and the affected connected journey;
they do not close existing WeekStrip/header geometry, N2, W3 or phone evidence.
