# Q01 study: make logging readiness visible

**Date:** 8 October 2026

**Status:** Connected mechanical baselines established on Debug 122 and matching published Debug 124 source. The complete synthetic Android suite passed on Debug 122; the bounded Debug-124 repairs and their final gate are recorded in the dated follow-up below. The focused readiness comparison and two connected workout proposals await owner task observation and design choice.

**Production baseline:** `eae6517845ee560ceb9695bf2f92788e2b4339cc`, Debug 122. Verification and documentation changes are recorded below; production behavior is unchanged.

The later source reconciliation confirms published Live 124 comes from open PR
#457's Tempo UI branch. The owner screenshot's installed version remains unknown.
This comparison isolates readiness on trunk 122; it does not reproduce that
branch's complete layout. Reconcile the overlapping UI before a native proposal.

The owner subsequently reports currently using Debug 124 and finding the
workflow clunky. The requested direction is a sophisticated but simple front
end with one obvious next action, inspired by Wealthsimple/Web3. This supports
exploring the connected workout's hierarchy and traversal; it does not select
the existing visible-readiness alternative. Execute the published Tempo source
and compare a connected proposal before changing its overlapping native UI.

## Finding

The existing missing-effort explanation, “Pick your effort first,” reaches the disabled Log button's accessibility state description but is not rendered as visible text. Native floor images show a subdued Log button and its pending payload. At 360×640, Effort can be below the captured viewport while Log remains visible.

This establishes a visible-feedback gap worth comparing. It does not establish that the owner is confused, that rest disables logging, or that the supplied S05 image runs this exact build.

The owner reports broad clunkiness and a preference for a simpler, polished front end. No task-specific cause or unaided task performance has been recorded in this study yet. No completion time, variant preference, or improvement percentage is claimed.

## Evidence chain

| Evidence | What it establishes | Limit |
|---|---|---|
| [LogCommitCopy](../../../../app/src/main/java/com/sinura/personaltrainer/domain/LogCommitCopy.kt), `EFFORT_MISSING` and `disabledReason` | The existing reason is available when missing effort is the blocker. | Source policy does not show where the words are visible. |
| [WorkoutDock](../../../../app/src/main/java/com/sinura/personaltrainer/ui/workout/WorkoutDock.kt), `disabledReason` argument | The dock passes that reason to the primary control. | Passing a string does not imply visible rendering. |
| [GymButtons](../../../../app/src/main/java/com/sinura/personaltrainer/ui/components/GymButtons.kt), `PrimaryGymButton` | `disabledReason` is assigned to semantic `stateDescription`; visible text is the verb and supporting payload. | This is the pinned source, not verified phone-build identity. |
| [DockCommitRenderTest](../../../../app/src/test/java/com/sinura/personaltrainer/ui/workout/DockCommitRenderTest.kt), `aCommitThatCannotActSaysWhyItWaits` | The existing test asserts the semantic explanation. | It does not assert a visible explanation or owner comprehension. |
| N01-N03 below | Disabled Log and its payload are visible without the textual reason; effort is outside N03's viewport. | These are native JVM renders with synthetic data. Physical reach, glare, touch and screen-reader operation remain untested. |
| [S05](../../EVIDENCE.md#s05--logging-during-rest-with-coaching) | The owner's screenshot also shows subdued Log and a recommendation marker. | Its exact build and Tempo implementation remain unmatched; its disabled cause is unproven. |

Contrary/supporting detail matters: the app already distinguishes selected effort with a filled/bordered chip and explanatory text (N06), already exposes the disabled reason to accessibility semantics, and already supports manual input. The proposal does not treat those capabilities as absent.

## Native render register

These six files are unchanged copies of retained synthetic JVM renders from the earlier local run. Their generator names, original locations, byte sizes and SHA-256 values are in [the manifest](assets/manifest.json). The session records the source baseline; the PNGs/XML do not independently embed a Git commit. Copies are versioned here so the comparison remains inspectable after build cleanup. The owner's S01-S05 originals remain local under their existing ignore rule.

| ID / image | Generator and state | Observation relevant to Q01 |
|---|---|---|
| [N01: first set](assets/N01-first-set-360x800.png) | `WorkoutFloorRenderTest.rendersTheFirstSetFloor`; 360×800 at default text | First working set, 70 lb × 10, unselected effort, idle rest, subdued Log. |
| [N02: resting](assets/N02-resting-360x800.png) | `WorkoutFloorRenderTest.rendersTheRestingFloor`; 360×800 at default text | Two saved sets are seeded and rest is started separately. A dot marks recommended 9; no selected chip or visible Log reason. This is not a captured save-to-rest transition. |
| [N03: small viewport](assets/N03-working-360x640.png) | `WorkoutFloorRenderTest.rendersTheFloorAt360By640`; 360×640 at default text | Effort is below the captured view; the fixed action area shows disabled Log and payload. |
| [N04: before first save](assets/N04-360x640-font1.0-before.png) | `FirstWorkingSetRenderTest.theFirstWorkingSetHoldsTheEntryAt360By640` | Zero of three sets, pending first set, 70 lb × 10, no visible reason beside Log. |
| [N05: after first save](assets/N05-360x640-font1.0-after.png) | Same test/fixture as N04 | Progress/ordinal and saved statistics update while entry remains in place. The test supplies effort programmatically before Log. |
| [N06: correction](assets/N06-editing-360x800.png) | `WorkoutFloorRenderTest.rendersTheEditingFloor` | Selected effort 8 is visibly distinct; Save changes includes the correction payload. |

The similarly named `screen-renders/w1d/before-first-set-360x640-font1.0.png` belongs to `ExerciseDetailThisWorkoutRenderTest`, not the workout floor. Its companion after image was nearly blank when inspected. Both were excluded from this floor comparison; the filename alone is insufficient evidence of screen identity. The blank artifact's cause was not investigated, and no app defect is inferred from it.

## Proposed comparison

**Current structure:** Keep the present weight/reps entry, effort choices, rest context, disabled Log and payload preview.

**Visible readiness:** Keep those controls and add the existing “Pick your effort first” reason adjacent to Log when missing effort is the actual blocker. Preserve the semantic reason as well. Once effort is chosen, the action returns to its ordinary ready presentation. Reserve the message's space within this concept so selection does not move the button.

This tests one change: whether a visible reason makes the next action understandable. It does not add a training rule, auto-select effort, make rest a logging requirement, change Apply into saving, or decide a new app identity.

**Tradeoff to measure:** The message takes vertical space. A native implementation must check whether it pushes useful entry content out of view at 360×640 and larger fonts. C05's provenance is now reconciled: the current ratchet is 852 dp for the floor's identity-to-history region, excluding the dock. This concept does not raise that budget or certify a fit.

The [comparison source](visible-readiness.html) is an interactive concept displayed in conversation. It uses a simplified synthetic fixture: one saved 70 lb × 10 set, a pending second of three sets, no selected effort, and a displayed rest of 1:32. This fixture is declared separately from N02's two seeded rows. The two variants share equivalent controls and state rules. The prototype models local entry, effort, save/reset and completion; it does not execute Android, Room, service timers, permissions, or the owner's history.

The concept reserves 30 CSS pixels for the candidate's explanation. Its number fields, fixed recommendation marker, 700 ms saving delay, and input limits (0–1,000 lb in half-pound steps; 1–100 reps) are simulation choices, not new product requirements. Saved rows can be reviewed, but correction, coaching/Apply, failed-save recovery, early finish, and multi-exercise navigation are omitted. Rest responds to controls but does not count down. This comparison can test interpretation of the proposed feedback; its simplified layout cannot establish the native screen's fit or exact tap cost.

## Owner task and decision record

Use the neutral method in [the foundation](../../EVIDENCE.md#how-to-run-the-first-study). The owner's earlier exposure to the explanation in this conversation is a learning effect: a later attempt cannot be described as completely unprimed discovery.

1. Present the current screen and a fictional result: **75 lb × 8**, with a natural description that the final repetitions were hard but roughly two more felt possible. This supplies context, not a control instruction or training prescription.
2. Say: “You just completed this set with the result on the task card. Record what happened as you normally would.” Observe the first interpretation/action before help. Do not initially name effort or the reason Log is disabled.
3. After the action, ask what was saved and what the screen is ready for next. Mark any help as assistance. Separately check the stored payload once on the real app with synthetic data.
4. Compare the same task with the candidate. Record order and learning effects; repeat with an equivalent result if useful. A browser interaction result is evidence about the concept, not native acceptance.
5. Use the connected native baseline below for the start → actual entry → correction → finish → summary/history mechanics. Observe the owner's complete task separately before making a whole-workout recommendation.

| Measure / outcome | Current structure | Visible readiness |
|---|---|---|
| Owner's first interpretation and action | Pending | Pending |
| Correct result entered/saved once | Owner/native task pending | Concept/browser checks separate; owner/native task pending |
| Assistance, wrong actions, taps/scrolls | Pending | Pending |
| Understands saved result versus pending set | Pending | Pending |
| 360×640 / font 1.6 and 2.0 / IME native reachability | Existing baseline evidence only | Pending native implementation/evaluation |
| Preference with reason; contrary evidence | Pending | Pending |
| Design decision | Retained baseline | Proposed; not owner accepted |

Recommend proceeding only if the visible reason addresses the observed difficulty without worsening value visibility, reachability, correct saving or recovery. If no meaningful benefit appears, retain the current design. If build mismatch or learning prevents a conclusion, record the specific evidence gap.

## Executed mechanical baseline

On 8 October, the existing targeted JVM/Compose selection ran successfully: **48 tests, zero failures/errors/skipped**. The normal Gradle test dependency ran static checks and syntax checks. [The result manifest](baseline-results.json) lists every executed case, source revision, environment description, XML hash and timestamp.

| Executed selection | Count | What this can establish |
|---|---:|---|
| `DockCommitRenderTest` | 12 | Payload/action semantics, disabled reason semantics, primary-action identity and related component rules. |
| `FloorRestAndCoachWiringRenderTest` | 27 | Real screen/ViewModel wiring including suggested versus selected effort, Apply without a new row, rest controls and opening Finish. |
| Two `WeightRepsEditorRenderTest` typing cases | 2 | Tapping numerals opens numeric entry; typed 82.5 lb and 12 reps reach the correct callbacks. These isolated tests do not assert database persistence. |
| Six `ActiveWorkoutViewModelTest` cases | 6 | Typed/stepped values persist, effort is required and preserves values, correction updates the same row, finish persists and clears draft state. These use direct ViewModel calls; helper-selected effort is guided setup. |
| One `WorkoutSummaryViewModelTest` case | 1 | A separately seeded finished session yields its expected summary payload. It is not the session from the correction/finish cases. |

The selection is reproducible with the Windows wrapper: run `testDebugUnitTest` with one `--tests=<fully-qualified-class>` argument for each full class above and one `--tests=<fully-qualified-class>.<case>` for each selected case in the result manifest. Use the equals form because the wrapper rejects a standalone filter value. The exact local invocation script/log are retained under ignored `build/ux-context/q01/`.

Before rerunning, the full earlier report was copied to ignored `build/ux-context/runs/initial-gate-20261008/` (XML, HTML and preflight log). The targeted report is separately retained at `build/ux-context/runs/q01-baseline-20261008/`. These build archives are local; the compact result manifest is versioned. Those initial failures are retained; the subsequent repairs and connected journey are recorded below. A passing targeted selection is not a passing full gate.

## Connected workout baseline and verification repair

The owner's subsequent roadmap instruction started one verification/baseline
packet on the same production source, Debug 122. No production behavior,
schema, backup format or signing configuration changed.

### Connected screen journey

[ConnectedWorkoutJourneyTest](../../../../app/src/test/java/com/sinura/personaltrainer/ui/workout/ConnectedWorkoutJourneyTest.kt)
passed **one test, zero failures/errors/skips**, with the ordinary static gate.
It uses real Compose screens, ViewModels and one synthetic Room session on
Robolectric API 35, native graphics, 360×800 and default text. The same session
and saved set are asserted through every step; this is not a collection of
separately seeded success cases.

| Step | Exact task and verified outcome |
|---|---|
| Start and actual entry | Start the two-lift routine through the rendered sheet. Type 82.5 lb × 12, different from both suggested values. No row exists yet; missing effort disables Log. |
| Effort and Log | Choose 8 without changing numbers; Log creates one 37.4 kg × 12, effort-8 row. Pounds use the existing D15 tenth-kilogram storage precision. |
| Rest and switching | The save path starts rest for that session. An unsaved 87.5 lb × 9, effort-7 draft survives switching away/back; the saved row and timer identity remain unchanged. |
| Correction | Open the saved chip's Revise action; save 80 lb × 11, effort 9. The row is 36.3 kg × 11 with its original ID, ordinal and completion time. |
| Finish and review | Finish the partial plan through Save as is. Only the corrected row remains; rest and live-session state end. Summary, History and Detail agree on the same row and 399.3 kg volume (880 lb displayed). |

The host observes Summary's rendered Done callback and then explicitly mounts
History. AppNav returns Done to Home; this test does not certify that route.
Rest uses the existing fake gateway, not an Android service. The passing
XML/log are retained at `build/ux-context/runs/connected-passing/`. Failed
attempts are separately archived: canonical precision and synchronization with
newly mounted WhileSubscribed review screens were test-driver issues, not
demonstrated recording defects. No owner comprehension or phone acceptance
follows from this guided automation.

### Real Android navigation journey

[ConnectedWorkoutJourneyInstrumentedTest](../../../../app/src/androidTest/java/com/sinura/personaltrainer/ui/workout/ConnectedWorkoutJourneyInstrumentedTest.kt)
adds one complete session through MainActivity's real AppNav, production
ViewModels, Room and Android rest service. Home starts a uniquely named
two-exercise routine; no deep link or test-host route substitution is used.

| Step | Exact task and verified outcome |
|---|---|
| Home and manual entry | Choose the routine through Home's Start sheet. Enter 87.5 kg × 4, different from both displayed prefills; effort remains unselected and nothing saves. |
| Effort and Log | Choose 8; the visible Log payload is 87.5 kg × 4 · RPE 8. One exact row saves in the same live session. |
| Rest | Observe the real service timer running for this session with 150 seconds planned. Verify visible Skip, invoke its Android accessibility action, and observe the service stop. |
| Switching and correction | A 92.5 kg × 6, effort-7 draft survives switching away/back without another saved row. Revise the saved set to 85 kg × 3, effort 9; preserve its ID, completion time and ordinal. |
| Finish and return | Save the partial workout as is. Summary shows one working set and 255 kg. Done returns to Home; the live bar disappears. Open the actual History tab and the same session's Detail, which contains the corrected row; Back returns to History. |

Two earlier Android smoke cases run alongside it. One covers leaving/resuming,
Summary rotation and Detail Delete/Undo. The other now enters both actual
numbers before effort and checks the saved payload. That older smoke still
uses its existing direct timer-stop seam; it does not establish rendered Skip.
The new connected case does.

The native fixture deliberately uses a disposable owned emulator, local/offline
posture, verified Android grants and acknowledged setup hints. A separate prior
synthetic session suppresses the first-ever-record overlay. Thus this baseline
does not cover first-launch prompts, denied permissions, remote update behavior
or the owner's installed build. OS state restoration and synthetic-record
cleanup are test-fixture responsibilities; app preferences stay seeded for
the disposable fixture.

Compose's frame clock is pumped around the running-rest capture and Skip so the
rendered state catches up with the saved data. The timer still uses Android
elapsed real time; no controller stop or timer-state override occurs in the
connected case. This demonstrates the save → running rest → Skip path, not
natural expiry, notification delivery, lock-screen behavior or timing quality.

Early native attempts exposed permission/update dialogs obscuring older
semantic passes, and a stale rendered frame while the backend had already
saved. Those attempts are archived and superseded by unobscured captures; they
are not counted as successful user journeys. Final results and frame hashes
are retained in [the connected capture manifest](assets/connected-manifest.json).
The final combined run, `20261008-135800403`, passed **three tests, zero
failures/errors/skips**. PackageManager recorded installed Debug **122 /
1.0.0+debug.122** for each test before activity launch. The owned API-29 AOSP
emulator used 1080×1920 pixels, density 420 (about 411 dp wide), font 1.0,
portrait, en-US and America/Toronto. Only this profile was executed in Android.

| ID / connected capture | Recorded observation |
|---|---|
| [N07: Home routine selection](assets/N07-home-routine-api29.png) | Unique two-lift routine in the real Start sheet. The visible completed workout behind it is the separately seeded prior fixture. |
| [N08: manual result ready](assets/N08-manual-ready-api29.png) | Effort 8 is selected; Log previews the actual 87.5 kg × 4 result. This screen is scrolled to Effort. |
| [N09: saved and resting](assets/N09-saved-rest-running-api29.png) | Progress is one of six; suggested effort is unselected, rest is running, and Skip is visible. |
| [N10: completed Summary](assets/N10-summary-api29.png) | One corrected 85 kg × 3 set totals 255 kg; Done is reachable. |
| [N11: same History detail](assets/N11-history-detail-api29.png) | The same completed session contains the 85 kg × 3, effort-9 row and 255 kg total. Planned work remains distinguishable from the saved row. |

The five files are unchanged device captures. Device/local SHA-256 values
matched and every frame was visually inspected. Raw XML, HTML, logcat, runtime
profile and all eight captures from the combined run remain under ignored
`build/ux-context/runs/android-attempt-09-success/`. The capture manifest is
portable; those build archives are local.

Fixture review found unchecked OS-restore commands and unbounded older cleanup.
The repair bounds seed/cleanup, preserves cancellation, and verifies captured
setting strings, app-op modes and permission/battery capabilities after restore.
Three failed setup attempts also exercised cleanup: original parser errors were
preserved while restoration passed. Their archive remains
`build/ux-context/runs/android-attempt-08-failure/`; the final successful run
verified restoration after each of its three cases. This does not claim seeded
app preferences were restored.

### Ranked next work from this baseline

| Priority / existing record | Evidence and next action |
|---|---|
| Verification first | The initial four copy failures and standalone compiler failure prevented a trustworthy gate. Fix their demonstrated causes and verify restoration of the native fixture before integration. No recording defect was demonstrated by those failures. |
| C06: match the owner's source | The earlier baseline is trunk 122. The dated follow-up below executes published Debug 124 from open PR #457, with actual installed-version and source equality checks. Phone acceptance and the original screenshots' exact identity remain separate. |
| Q01 / UX03, UX23, UX24, UX26 | Source and small native renders establish the absent visible missing-effort reason. The connected path saves the intended values correctly. Compare the existing candidate with the owner; retain current behavior unless the evidence supports a change. |
| Q02, Q03, Q04, Q08 | Coaching interpretation, interruption/return, progression and finishing remain complete-workout research tasks. Mechanical switching, correction, early finish and review now have connected coverage. Owner uncertainty, assistance and friction remain unobserved. |

No meaningful owner difficulty has been observed in this automated walkthrough.
The native manual-ready frame is scrolled to Effort: the main numerals are above
the viewport while Log repeats the actual payload. That is a recorded layout
state, not proof of a usability defect or permission to enlarge the floor.
Do not turn these research questions into presumed failures or a mandatory
redesign.

### Repaired verification

- **Migration copy:** the unchanged suite reproduced all four failures. Long
  Robolectric fixture paths reached 260–262 characters. A short unique temporary
  root reduced copy paths to 104–106; the same **21 tests passed**, with no
  assertion or production-code changes. Original and corrected logs/XML/path
  measurements are retained under `build/ux-context/migration-copy/`.
- **Standalone preflight:** corrected native Windows classpaths, rejected
  source/Javadoc dependency archives, restored pure backup source membership
  and test resources, and preserved compiler/JUnit failure exits. The complete
  lane passed **248 classes / 1,635 tests**. Exactly three Android-dependent
  domain test files are explicitly Gradle-only; they remain in the app gate.
- **False-pass probes:** nine command-contract checks passed on Windows and
  Linux. They cover compiler failures after partial output, empty discovery,
  JUnit failure, relative/spaced classpaths, resources and syntax launch/parse
  outcomes. Removing the zero-discovery guard in a temporary runner copy made
  its regression fail. Final preflight evidence is
  `build/ux-context/runs/preflight-repair-20261008/preflight-verified.log`.
- **Layout provenance:** ADR-027 now records the existing 840 → 868 → 852 dp
  history. The current floor ratchet remains 852 dp; no threshold or layout
  was raised to fit a proposal.

The required local gate passed: **3,571 tests in 537 suites, zero
failures/errors/skips**, with Debug build, lint and device-test APK assembly.
Lint reports **zero errors and warnings**. After the final native-fixture repair,
the complete gate passed again; Gradle reused the unchanged unit inputs/results
and reran static checks. The three repaired Android cases were actually executed
on the emulator separately. Device-test assembly is not that execution.

[The connected result record](connected-results.json) preserves counts, cases,
source/test hashes, XML/log hashes, environment, review outcomes and limits.
Full reports are archived locally under
`build/ux-context/runs/roadmap-final-gate/`. The initial failures remain under
their original archive. Independent and adversarial rereviews closed the
fixture findings and found no remaining material/actionable findings in this
bounded packet.

This verification/baseline packet is ready for integration. The owner
comparison, complete-workout usability milestone, native candidate fit and
physical-device acceptance remain open. No Debug drop or public release is
part of this packet.

## Executed concept checks

The sandboxed comparison passed **29 browser checks** in headless desktop Chrome. [The check record](prototype-checks.json) includes the browser version, concept source hash, time, individual outcomes and measured geometry. These checks apply to the browser simulation, not the Android app.

- Both variants fit 736 px and 320 px outer browser widths without horizontal overflow. The preview's padding left 704 px and 288 px respectively for the fragment. Both variants use the same stage height, including the expanded two-row review state at the narrower width. Initial and completed screenshots were inspected visually.
- Manual entry and adjustment controls update the pending payload. A recommendation remains unselected. Invalid values block saving and suppress the misleading missing-effort message. Keyboard selection enables Log without changing values; the candidate's button position stayed exactly fixed when its helper cleared.
- Active simulated rest does not prevent logging. Rest adjustment/Skip, the save lock, repeated-click protection, exact saved payload, effort reset, independent variant state, final planned-set completion and simulated summary passed.
- Verification caught and corrected an interaction defect: an unchanged host state echo cleared the confirmation message. Equivalent echoes now preserve transient announcements. The finish regression passed without JavaScript errors. This is a prototype defect and correction, not a finding about Temper.

The local browser driver, wrapper and screenshots remain under ignored `build/ux-context/q01/`. No native keyboard, TalkBack, physical target size, timer service, persistence backend, complete Android journey, or owner usability result follows from these checks. The 48 targeted native tests also do not include the last-set-during-rest test; the simulated rest/completion checks above must not be presented as fresh native verification of that transition.

An independent source/evidence review found no material findings in the study record. It verified the six native image copies, five targeted XML hashes/case lists, test totals, log, source links and distinctions between separate fixtures and a complete journey. It did not perform owner acceptance.

## Readiness comparison conclusion

The original Q01 comparison exposes the existing readiness explanation visually. Native baseline facts and mechanical contracts have been checked. The owner's comprehension, preference, complete-workout performance, and the candidate's native fit remain pending. The later connected proposals also respond to O10's explicit direction and the demonstrated pinned-card traversal problem; they do not imply owner acceptance of a new layout.

## Connected proposals following O10

[Quiet and Context](connected-workout-proposals.html) explore the owner's reported
clunkiness and request for a sophisticated, simple front end. They are proposed
workout compositions, not replicas of current Debug 124 or accepted ADR changes.
The earlier readiness comparison remains open. Quiet is the recommended direction
for the next discussion; no native redesign is selected or implemented here.

| Existing record | Proposal and tradeoff | Acceptance task / product rule |
|---|---|---|
| Q01 / UX03, UX23, UX24, UX26 | Both place actual values, effort, visible readiness and Log together, ahead of secondary content. Quiet hides saved rows behind one inspection action; Context exposes them and a last-saved/planned-next strip, adding height. | Enter 82.5 lb × 12, choose effort 8 and save once; distinguish the pending entry from saved work. Preserve manual values, required effort and the explicit save payload. A new composition would amend ADR-027, after selection and native evidence. |
| Q02 | Tempo is secondary to recording. Why explains the fixed synthetic suggestion; Apply explicitly fills numbers **and effort**, without logging. | Apply, inspect the draft, then enter a different actual result. No new coaching policy or automatic log is proposed. |
| Q03 / Q04 | Rest controls and Home return remain reachable; switching preserves per-exercise drafts. The expanded timer identifies its saved source exercise even after switching. | Close rest, return through Home, switch away/back, then Skip or allow completion. Neither rest nor exercise switching writes a set or finishes the workout. |
| Q08 | Saved-row correction, separate correction draft, Undo, early-finish warning, Summary and same-workout History are connected. | Correct 82.5 × 12 to 80 × 11, undo/retry it and finish with an unlogged entry. Saved identity/timestamp and totals must reconcile; unfinished work must be explicit. |

The [136-check record](connected-proposal-checks.json) pins the fragment and driver
hashes, Chrome version, tasks and results. It covers both complete branches,
duplicate/slow writes, failed logs and corrections, reload of a pending write,
exact Retry, keyboard focus, Apply, Undo, rest-source/Close/Skip/expiry, explicit
progression/extra sets, early and prescribed finishing, Summary and History.
Widths 736/412/360/320 px with text scales 1.0/1.6/2.0 reflow without horizontal
clipping; equal variant stages and representative images were checked.

This uses display-pound arithmetic and a browser clock, not Room's canonical
storage or the Android timer. Twelve saved sets per variant is a comparison
capacity, not a Temper product limit. The prepared routine start omits the native
confirmation sheet; it does not authorize removing that confirmation. Native
height/IME/accessibility, physical touch/timing/performance and owner comprehension
remain separate. Drivers and images are in ignored `build/ux-context/quiet-flow/`.

Try one task on each proposal: start, record 82.5 lb × 12 at effort 8, return through
Home, correct the saved result to 80 lb × 11, then finish and find it in History.
Record the first unclear step and any extra navigation. Earlier explanations are
a learning effect; do not describe the result as an unaided first encounter.

## Complete native suite follow-up

After baseline integration in [PR #458](https://github.com/sinura7/PersonalTrainer/pull/458),
the hosted [initial run](https://github.com/sinura7/PersonalTrainer/actions/runs/37790074455)
reported one JVM timeout and nine Android failures. These are retained alongside
the later [trunk run](https://github.com/sinura7/PersonalTrainer/actions/runs/37792090519),
whose deterministic job passed but whose emulator repeated the nine failures.

The three journeys encountered a dialog consistent with an update offer fetched
by an earlier class; per-journey offline setup could not remove that cached offer.
The suite runner now establishes and verifies offline state before any app launch
on a fresh, explicitly authorized disposable emulator. Exact network settings and
active-default posture are restored and verified on exit. It refuses physical,
additional, unrelated or preinstalled-Temper devices; no app/cache is cleared.
Twenty-three mocked process-contract checks run through static preflight.

Six completion-layout failures expected enabled Log with a fresh, unselected
effort. The fixture now taps the displayed effort control at each demonstrated
fresh-draft transition. Existing enabled, layout, recovery and durable-row
assertions remain. No production behavior, database or signer changed.

The complete local API-29 suite executed **211 tests across 31 classes with zero
failures, errors or skips**, including all nine previously failing cases, in
4m32s. All three actual MainActivity journeys observed Debug 122 and offline
state; their restoration returned to the suite's offline posture, then the suite
verified original WiFi/data/connectivity restoration. Twenty-five captures match
device/host hashes. See [the portable follow-up record](native-suite-followup.json)
and ignored `build/ux-context/runs/native-fixture-reliability/` for raw evidence.

The separate JVM timeout occurred after exercise removal with the survivor
projected but readiness `NONE`. Source review suggested observer selection could
be cleared by later removal cleanup. That production race was not repaired by
the fixture packet. The later Debug-124 follow-up below executes the ordering
regression and independently measures the primary target. A fresh green JVM run
does not erase the original failure; phone acceptance remains open.

## Published Debug 124 follow-up — 8 October

The source baseline is published tag `debug-live-2026-10-06-1`, commit
`a1f47d1a0d41d1e0264d4457407391cf7d4af92e`. The research stack ports only the
reviewed verification/documentation packets from PRs #458 and #459. Before the
walkthrough, production sources, build configuration and dependency catalogue
were compared with the tag and matched. PackageManager observed **124 /
1.0.0+debug.124** in every case. This is a local synthetic APK built from matching
source, not inspection of the owner's phone or proof of its distribution signer.

The same API-29 profile, about 411 dp wide at font 1.0, executes the complete
AppNav task: actual **87.5 kg × 4 at effort 8**; real service rest and rendered
Skip; **92.5 kg × 6 at effort 7** retained across switching; correction of the
original row to **85 kg × 3 at effort 9**; early finish; **255 kg / one working
set** in Summary; Done to Home; actual History and the same session's Detail.
The two legacy journeys also pass, including leave/resume, rotation and Delete/
Undo. The final run executes **three tests, zero failures/errors/skips**, with
sixteen captures matching device hashes and verified OS restoration.

The original driver did not complete this task. Four attempts remain archived:
1/3 passed with the pinned-card selector adapted; 0/2 in the diagnostic repeat;
2/3 after numeric/effort controls were exposed; 3/3 after the saved chip was also
exposed. These are separate runs with separate drivers, not one green run with
discarded failures. Ordinary, bounded swipes above the card expose the controls;
their entire target bounds must then be above the overlay before a real touch.
No accessibility shortcut replaces numeric entry or correction.

| Retained image | What to inspect |
|---|---|
| [Weight before the original touch](assets/debug124/weight-before-tap-api29.png) | The pinned Tempo card covers the weight-field center on unchanged published production. This is attempt 02's synthetic long-label state, before manual entry. |
| [Weight after a real swipe](assets/debug124/weight-after-scroll-api29.png) | Attempt 03 exposes the same entry region above Tempo. These captures belong to separate runs; they are not an uninterrupted before/after pair or a redesigned screen. |
| [Repaired commit, 360×640 / font 2.0](assets/debug124/commit-360x640-font20.png) | Actual dock component with the 72 dp minimum and readable payload; the larger text grows it to 88.5 dp. The empty area is the component host, not missing workout content. |
| [Repaired Next, landscape / font 2.0](assets/debug124/commit-landscape-font20.png) | The one-line Next action meets the 72 dp minimum and remains inside this component frame. This does not certify the complete landscape workout. |
| [Why before the footer repair](assets/debug124/why-before-footer-repair-jvm.png) | Actual JVM modal window at 360×640/font 1.0: the explanation leaves both actions at zero height. |
| [Why after the footer repair](assets/debug124/why-after-footer-repair-jvm.png) | The same synthetic fixture and viewport in a separate final-gate execution: Use and Keep retain full targets below the scrolling explanation. This is JVM modal evidence, not a phone capture or an uninterrupted pair. |
| [Why actions at actual OS font 2.0](assets/debug124/why-osfont20-actions-api29.png) | Final API-29 suite: modal and OS font both 2.0, Keep 48 dp and Use 64 dp fully visible. The transient drag-handle tooltip overlaps the heading; action bounds and touches are verified separately. This synthetic emulator image does not establish owner phone acceptance. |

Image source paths, hashes and generating runs are retained in the follow-up
record. Copies preserve the original PNG bytes.

Repair order is to preserve recording readiness, restore the accepted primary
target and recover constrained-screen reachability, then compare the broader
recording/coaching hierarchy with the owner. The repairs restore existing
contracts; the broader Quiet/Context choice remains open.

| Existing study / rule | Demonstrated problem and next action | Evidence limit |
|---|---|---|
| Q01 / UX03, UX23 | The pinned Tempo card covers the weight-field center and later a saved chip at the tested scroll positions. Recording/correction require extra swipes. The connected Quiet proposal keeps recording together and makes coaching secondary; compare it before adopting the new hierarchy. | Long synthetic routine/exercise labels on this API-29 profile; no claim that every normal phone entry is covered or that this explains all owner friction. |
| Q01 / recording readiness | Real Room can commit removal, deliver the survivor and finish its prefill before removal cleanup resumes. Unconditional cleanup then changes READY to NONE. Preserve the newer raw selection when cleaning up the removed lift. | The regression controls continuation order; it does not estimate how often this happens on a phone. |
| ADR-027 / primary target | All five measured component states render at 64 dp at 360×640/font 1.0, below the accepted 72 dp floor. Use the established commit token and an independent hard 72 dp test oracle. | Component height and native bounds are evidence; full-screen reachability and physical touch remain separate. |
| Q01 / UX03, UX23 / adaptive entry | At 360×640/font 2.0, Tempo leaves only 3 px of the content region clear for a 126 px effort target. Another entry fixture leaves 180 px for a 205 px numeric target. Scrolling cannot expose either full control above the pin. Move the existing card into the scroll order after effort and before saved sets at font ≥1.6 or landscape; retain its actions and normal portrait pin. | Actual API-29 geometry and failed touches prove these states. This does not establish the owner's font settings or select the broader Quiet layout. |

The two regression probes first fail on unchanged production: the real observer
ordering loses readiness, and five measured targets are 64 dp. The small fixes
then pass **29 targeted tests across four suites**, including the seven existing
removal/Undo cases and twelve dock cases. Nine component profiles cover
360×640, 412×840 and 640×360 landscape at fonts 1.0/1.6/2.0: all **45 state
measurements** remain inside the frame and meet 72 dp. Permanent regression
tests retain the same bodies with ordinary test names; full-gate execution is
recorded separately.

The first required full gate executed 3,592 JVM tests with one failure in the
connected screen-host journey's effort touch. Manual 37.4 kg × 12 remained
intact at unselected effort. A separate repeat retained frames and actual
bounds: RPE 8's center `(360,1178)` lay inside Tempo's
`(32,1106)-(688,1218)` rectangle. The ordinary touch dismissed Tempo while
leaving effort unselected. This is a demonstrated traversal problem in that
test state, not a host limitation or evidence that manual values were lost.
The driver repair must expose the complete target with real swipes and retain
all same-row/manual-entry assertions. Original failures and final results are
indexed separately in the follow-up record.

The first complete Android run on the modified Debug-124 source executed all
211 cases with nine failures: six effort-selection waits in completion fixtures
and three entry-dialog/sheet/switcher lookups. All three AppNav journeys passed;
the suite also verified original WiFi/data/default-network restoration. That
does not make the complete suite a pass. A second diagnostic run executed 23
cases and repeated all nine failures, retaining before/after window captures and
draft state. It separates scroll-recoverable traversal from a genuine product
failure: at large text the pinned coach can leave less room than an entry
control's full height. Shrinking targets, hiding coaching in fixtures or
assigning effort through the ViewModel would conceal that failure.

The bounded adaptation composes one existing card inside the list at large text
or landscape, removing the overlay and its extra bottom reserve there. It keeps
the entry anchor at item 2 and the normal portrait pin. Open Why/Evidence state
and modal composition live outside the lazy item, so moving or scrolling the
card away cannot close an explanation. Suggestion rules, Apply, Log and explicit
progression remain unchanged; the 852 dp portrait budget is not raised. Actual
screen touch, Apply-without-save and open-Why relocation regressions verify this
separately from the earlier component target measurements. The
first executed ten-case matrix passed all nine entry/effort/Apply profiles and
preserved Why across relocation, but failed to close the sheet after the final
Keep touch. The modal target and dismissal required diagnosis; this result is
not a green ten-case gate. A one-case diagnostic repeat confirmed that both Use
and Keep measured **656×0 px**, with zero visible/window bounds before and after
the unchanged touch. A separate live modal-window render shows the explanation
consuming the sheet. The explanation body now receives the height left after
the header/actions and scrolls within it; action targets and callbacks stay
unchanged. The final eleven-case JVM regression passes full modal target bounds
and actual touches. Activity-window renders alone exclude the
modal, so those earlier PNGs are not presented as dialog evidence.

The final required local command executed **3,603 tests across 543 suites with
zero failures/errors/skips**, plus static checks, Debug/release R8 builds, lint
and Android test assembly, in 9m59s. All source hashes stayed unchanged and all
543 XML files were freshly written after the gate began. Its eleven adaptive
cases preserve exact manual values and saved rows, exercise real Keep/card Apply
across nine floor profiles, retain an offscreen Why explanation, and exercise
Why Use in its own fixture without a write. An intermediate 24-case run retained
nine setup failures because it asked for another Apply after Why Use had already
dismissed the tip; the final fixtures preserve that product rule without
manufacturing a new offer. The final record keeps all these outcomes separately.

Scoped JVM floor-font changes do not change the dialog's actual font. The final
native suite separately verifies OS and modal font 2.0, full 48/64 dp Keep/Use
targets, real Keep touches and draft-only card Apply. Native Why Use has enabled
target/geometry coverage; its actual callback is exercised in the separate JVM
fixture. The floor-font relocation does not establish Why Activity/process
restoration or owner-phone usability acceptance.

The final offline API-29 command executes **212 tests across 31 classes with zero
failures/errors/skips**, in 5m45s. All three MainActivity journeys pass on local
Debug 124: one connected AppNav journey and two legacy journeys with their
documented seams. All 1,505 source-file hashes stay unchanged; 71 new captures
match device hashes. Startup records a coherent original `1/1/active` network,
verifies offline state before Gradle/launch and strictly restores `1/1/active`.
Font/window settings are restored, Temper packages are absent after fixture
cleanup and the capture collector is stopped. An archive helper initially
looked for the wrong APK filename after all tests and restoration had passed.
Its exit/error remain preserved; output-metadata recovery completes the archive
without a test rerun or source change.

A further hosted PR run finished 211 Android tests successfully but its runner
failed strict network restoration: original `1/1/none`, restored `1/1/active`.
The merged-trunk hosted run passed both jobs. Preserve both outcomes. The
startup repair waits for two coherent, unchanged real network observations
before mutation; strict restoration remains intact. Late or unreadable startup
observations fail before mutation. An already-started adb read retains its
existing ten-second timeout; expiry prevents another read or baseline admission.
The original 23 runner checks, seven startup cases and two slow-read deadline
cases all pass in the final 32-case mocked harness. This addresses the demonstrated transient
snapshot case without assuming that every hosted failure is a host issue.

The bounded fixes and native drivers are verified on the Debug-124 stack; their
complete gate, real runner execution and independent/adversarial review results
are retained in the [portable follow-up record](debug124-followup.json).
Integration is pending because the stack also
contains open PR #457's existing Tempo/coaching work. This UX packet does not
authorize integrating new coaching policy, choose the final layout, change the
database/backup format or prepare a Debug drop.
