# Temper UX acceptance matrix — updated 7 September 2026

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
| UX12-AC01 | P1 | F02, F09, F11 | At 320/360/412 dp, date targets are measurable and usable without overlapping ambiguous activation regions, or an equivalent accessible selector is supplied. | Not executed |
| UX12-AC02 | P1 | F02, F09, F11 | A week containing done, skipped, missed, future and rest days has unambiguous spoken and visible detail. | Not executed |
| UX12-AC03 | P1 | F02, F09, F11 | Selecting a neighboring-month date opens the correct civil date; today remains distinguishable from selection. | Not executed |

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
| UX15-AC02 | P1 | F05 | Changing a horizon produces exactly the documented scope across totals/list/records. | Not executed |
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
inputs and Git references stayed unchanged. Final independent reviews,
corrected hosted verification and post-merge acceptance remain pending.
The first diagnostic attempt failed its fixture-integrity assertion after a
replace removed child rows; it is preserved and is not credited as a tie proof.

Equal-timestamp record ordering remains a medium UX15 dependency: current record
feedback can depend on input order when chronology ties. This repair changes no
production calculation or saved data. Resolve and verify that rule before
presenting improved History statistics as trustworthy; this bounded fixture
check does not close the wider UX15 acceptance tasks above.

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
| UX24-AC02 | P1 | F06, F11 | At font 2.0, primary actions and full values/units are readable and not clipped. | Not executed |
| UX24-AC03 | P1 | F06, F11 | Shared component changes preserve all callers and do not reduce touch targets. | Not executed |

### UX25 — Validate complete-screen accessibility, not just component tags

| Check | Priority | Fixtures | Expected outcome | Status |
|---|---|---|---|---|
| UX25-AC01 | P1 | F11 | Physical TalkBack can complete planned start, set log/edit, cardio finish, routine create, history correction and export review. | Not executed |
| UX25-AC02 | P1 | F11 | Search and routine-name fields announce their purpose when empty and populated. | Not executed |
| UX25-AC03 | P1 | F11 | Large text/IME does not hide essential actions; measured issues have production-screen regression coverage. | Not executed |

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

**Current Home development:** F4 / UX23 required-read recovery, with UX22 reminder
handoff continuity and relevant UX24/UX25 layout/accessibility checks. Initial failed
reads must show Retry; later failure must retain a labeled last complete board.
Retry must restart shared reads, refuse durable actions until a complete fresh result,
and preserve open confirms/sheets/numeric text. Waiting reminder taps survive rotation;
refused requests cannot start automatically after Retry. Bounded implementation and
candidate execution have passed the local and native checks recorded below; final
review/integration follows the packet protocol. WeekStrip geometry remains the
separate UX12/F4 packet, followed by History's one-period/coherent-progress work.
Legacy strength captured-date storage remains a separately specified dependency.
Broader F4, UX23 and physical-phone acceptance are not closed by this packet.

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
