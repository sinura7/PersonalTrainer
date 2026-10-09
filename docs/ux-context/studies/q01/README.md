# Q01 study: make logging readiness visible

**Date:** 8 October 2026

**Status:** Native Quiet (#460) and the bounded workout-truth follow-up (#461) are independently reviewed, merged and reverified on trunk. The follow-up's reviewed candidate `473fff3a` and merged `c2384951` have the same tree. Fresh post-merge verification passed 3,702 JVM tests and 222 native tests; all 85 native captures were verified through a separately reviewed archive recovery after a helper parsing failure. The original failed archive and earlier failures remain preserved. Home required-read recovery is implemented with completed local and native checks; remaining Home geometry and History follow its reviewed integration. N2 interruption recovery, broader UX23/W3 criteria, phone acceptance and the inherited native Why-title tooltip limitation remain open. The historical baselines, prototypes and verification snapshots below retain their original scope.

**Original study baseline:** `eae6517845ee560ceb9695bf2f92788e2b4339cc`, Debug 122. Later Debug-124 repairs and the new native implementation direction are recorded separately below.

The later source reconciliation established that published Live 124 came from
then-open PR #457's Tempo UI branch. The owner screenshot's installed version remains unknown.
This comparison isolates readiness on trunk 122; it does not reproduce that
branch's complete layout. The later Debug-124 follow-up reconciles that source;
Quiet subsequently integrated it through PR #460, after which #457 was closed.

The owner subsequently reports currently using Debug 124 and finding the
workflow clunky. The requested direction is a sophisticated but simple front
end with one obvious next action, inspired by Wealthsimple/Web3. This supports
exploring the connected workout's hierarchy and traversal. After the published
Tempo walkthrough and connected proposals, O12 authorizes native Quiet
development through completion without owner testing during development.

## Finding

The existing missing-effort explanation, “Pick your effort first,” reaches the disabled Log button's accessibility state description but is not rendered as visible text. Native floor images show a subdued Log button and its pending payload. At 360×640, Effort can be below the captured viewport while Log remains visible.

This establishes a visible-feedback gap worth comparing. It does not establish that the owner is confused, that rest disables logging, or that the supplied S05 image runs this exact build.

The owner reports broad clunkiness and a preference for a simpler, polished front end, then approves proceeding from the updated proposals. No unaided owner task performance, completion time or improvement percentage has been recorded. Later synthetic native findings establish specific traversal problems within their recorded fixtures.

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

This is the original, unexecuted owner-study protocol. O12 supersedes its place
as a prerequisite: native development and agent verification proceed without
asking the owner to test. If the protocol is used after development, use the
neutral method in [the foundation](../../EVIDENCE.md#how-to-run-the-first-study).
Earlier explanations and prototype browsing are learning effects; a later
attempt cannot be described as completely unprimed discovery.

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

The original Q01 comparison exposes the existing readiness explanation visually. Native baseline facts and mechanical contracts have been checked. The later connected proposals respond to O10's simpler direction and the demonstrated pinned-card traversal problem. O12 now authorizes native Quiet development; the original comparison did not measure owner comprehension or complete-workout performance, and its browser evidence does not establish the native candidate's fit.

## Connected proposals following O10

[Quiet and Context](connected-workout-proposals.html) explore the owner's reported
clunkiness and request for a sophisticated, simple front end. They remain browser
proposals rather than replicas of Debug 124. Following favorable feedback and
O12's development instruction, Quiet is the native implementation direction under
[the ADR-027 amendment](../../../architecture/ADR-027-workout-logging-redesign.md#amendment--8-october-2026-native-quiet-workout-composition).
This decision does not convert browser results into native or phone acceptance.

The native adaptation keeps one anchored 72 dp primary, a compact 64 dp matching
exercise identity, and the saved-set strip. Entry, effort and readiness precede
the strip; coaching scrolls after it, followed by saved statistics. It removes
the floating coach in every layout. The complete floor, including relocated
statistics, remains subject to the existing 852 dp ratchet and 48 dp touch floor.

| Existing record | Proposal and tradeoff | Acceptance task / product rule |
|---|---|---|
| Q01 / UX03, UX23, UX24, UX26 | Both place actual values, effort, visible readiness and Log together, ahead of secondary content. Quiet hides saved rows behind one inspection action; Context exposes them and a last-saved/planned-next strip, adding height. | Enter 82.5 lb × 12, choose effort 8 and save once; distinguish the pending entry from saved work. Preserve manual values, required effort and the explicit save payload. The native Quiet composition follows ADR-027's dated amendment and requires its own verification. |
| Q02 | Tempo is secondary to recording. Why explains the fixed synthetic suggestion; Apply explicitly fills numbers **and effort**, without logging. | Apply, inspect the draft, then enter a different actual result. No new coaching policy or automatic log is proposed. |
| Q03 / Q04 | Rest controls and Home return remain reachable; switching preserves per-exercise drafts. The expanded timer identifies its saved source exercise even after switching. | Close rest, return through Home, switch away/back, then Skip or allow completion. Neither rest nor exercise switching writes a set or finishes the workout. |
| Q08 | Saved-row correction, separate correction draft, Undo, early-finish warning, Summary and same-workout History are connected. | Correct 82.5 × 12 to 80 × 11, undo/retry it and finish with an unlogged entry. Saved identity/timestamp and totals must reconcile; unfinished work must be explicit. |

Following O11, both proposals place each existing exercise illustration beside
its complete name: 64 px in exercise headings and 48 px in routine/picker/review
rows. The source assets are `ex_incline_dumbbell_bench_press.webp` and
`ex_seated_cable_row.webp`; their original bytes are embedded for offline use and
hashed in the check record. A rest names and depicts its saved source exercise,
while a correction depicts that saved row's exercise. Text wraps beside the
image, and the image has no separate tab stop or redundant spoken name. These
are proposed browser dimensions, not native dp acceptance or a new artwork set.

The [154-check record](connected-proposal-checks.json) pins the fragment and driver
hashes, Chrome version, tasks and results. It covers both complete branches,
duplicate/slow writes, failed logs and corrections, reload of a pending write,
exact Retry, keyboard focus, Apply, Undo, rest-source/Close/Skip/expiry, explicit
progression/extra sets, early and prescribed finishing, Summary and History.
It reruns the original 136 checks and adds eighteen checks of decoded matching
artwork beside exercise names across Home, entry, switching, rest, correction,
Finish, Summary and Detail. The original run's hash/count remain indexed as the
previous comparison; this update changes no Android source or workout policy.
Widths 736/412/360/320 px with text scales 1.0/1.6/2.0 reflow without horizontal
clipping; equal variant stages and representative images were checked.

This uses display-pound arithmetic and a browser clock, not Room's canonical
storage or the Android timer. Twelve saved sets per variant is a comparison
capacity, not a Temper product limit. The prepared routine start omits the native
confirmation sheet; it does not authorize removing that confirmation. Native
height/IME/accessibility, physical touch/timing/performance and owner comprehension
remain separate. Drivers and images are in ignored `build/ux-context/quiet-flow/`.

The native packet reuses the connected synthetic task: start, record a different
actual result with effort, return through Home, correct the saved result, then
finish and find it in History. The agent runs this and the recovery/adaptation
checks during development. A later owner task may add usability evidence, but is
not a prerequisite under O12; prior explanations remain a learning effect.

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

The bounded repair order preserved recording readiness, restored the accepted
primary target and recovered constrained-screen reachability. These repairs
restore existing contracts. O12 subsequently authorizes the native Quiet
hierarchy as the next implementation packet on this reviewed stack.

| Existing study / rule | Demonstrated problem and next action | Evidence limit |
|---|---|---|
| Q01 / UX03, UX23 | The pinned Tempo card covers the weight-field center and later a saved chip at the tested scroll positions. Recording/correction require extra swipes. The authorized native Quiet packet groups recording and makes coaching secondary; verify reachability and retained coach actions in the native implementation. | Long synthetic routine/exercise labels on this API-29 profile; no claim that every normal phone entry is covered or that this explains all owner friction. |
| Q01 / recording readiness | Real Room can commit removal, deliver the survivor and finish its prefill before removal cleanup resumes. Unconditional cleanup then changes READY to NONE. Preserve the newer raw selection when cleaning up the removed lift. | The regression controls continuation order; it does not estimate how often this happens on a phone. |
| ADR-027 / primary target | All five measured component states render at 64 dp at 360×640/font 1.0, below the accepted 72 dp floor. Use the established commit token and an independent hard 72 dp test oracle. | Component height and native bounds are evidence; full-screen reachability and physical touch remain separate. |
| Q01 / UX03, UX23 / adaptive entry | At 360×640/font 2.0, Tempo leaves only 3 px of the content region clear for a 126 px effort target. Another entry fixture leaves 180 px for a 205 px numeric target. Scrolling cannot expose either full control above the pin. The bounded repair moved the card after effort and before saved sets at font ≥1.6 or landscape, retaining its actions and normal portrait pin. The subsequent Quiet packet makes coaching scroll in every layout. | Actual API-29 geometry and failed touches prove these fixture states. This does not establish the owner's font settings or verify the subsequent Quiet implementation. |

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
At that stage, integration awaited reconciliation of PR #457's existing
Tempo/coaching work. Quiet subsequently carried that published Debug-124 tip
through PR #460; #457 was closed after its tip reached trunk. O12 authorizes
native Quiet on that reviewed stack, preserving the recommendation policy.
The bounded repairs add no coaching algorithms, change no
database/backup format and does not prepare a Debug drop. The new implementation
receives its own verification below; the preceding results belong to the bounded repairs.

## Native Quiet candidate — 8 October 2026

Quiet is implemented under O12 and ADR-027's composition amendment. The matching
64 dp exercise still sits beside the complete name. Actual values, effort and
visible logging guidance precede saved work, inline coaching and statistics. The
existing 72 dp primary action stays anchored. A pending write disables coaching
in place so removing the card cannot shrink the list during a save. Manual values,
effort requirements, explicit progression and Apply-as-draft retain their existing
contracts. The complete density budget still includes coaching and the moved stats.

The complete API-29 repeat executes **212 tests across 31 classes with no
failures/errors/skips**. It includes one connected AppNav workout through actual
entry, rest/return, switching, same-row correction, Finish, Summary and History.
All source hashes stay unchanged, 73 captures match device hashes, network and
display settings are restored, fixture packages are absent and the collector is
stopped. [N12](assets/N12-quiet-identity-api29.png) shows the real catalog identity;
[N13](assets/N13-quiet-correction-api29.png) shows the correction and its payload.

The preceding native run retains 25 shell failures: its log identifies a System UI
BOOT_COMPLETED ANR, then input directed at that system dialog. A complete
source-identical cold-boot repeat passes all 212; no shell checks are skipped.
Local failures are also retained: a held-transaction test tried a transactional
read before releasing the write, a long-header fixture still fitted beside the
compact image, a feedback test assumed pinned coaching, and the diagnostic dump
exceeded its existing 20,000-character limit. Each demonstrated cause is repaired
without raising checks. The final complete local gate passes **3,625 tests across
546 suites with no failures/errors/skips**, along with static checks, Debug/release
builds, lint and Android test assembly. All 546 XML files are fresh and the 1,509
source hashes match both the before/after gate snapshots and the native repeat.
The compact [Quiet follow-up](quiet-native-followup.json) separates these attempts from the
earlier bounded-repair evidence.

The first fresh review attempts stopped at the account usage limit. Subsequent
independent and adversarial clean-context reviews both passed the frozen candidate
`78ca67729b479d126f89236f91b03b483e8b75ef`, with no critical/high findings.
[PR #460](https://github.com/sinura7/PersonalTrainer/pull/460) merged as
`9b7a7f5a1b3f2904ab8a62c893c4728cbebeb1d6`; the whole merged tree equals the
reviewed tree. The included published Debug-124 dependency [PR #457](https://github.com/sinura7/PersonalTrainer/pull/457)
was closed after the tip merged, and both remote vehicle branches were removed.

Clean-trunk post-merge verification freshly passed the complete local gate:
**3,625 tests across 546 suites, zero failures/errors/skips**, plus all builds,
lint and static checks. The complete owned API-29 native runner then passed
**212 tests across 31 classes, zero failures/errors/skips** and 73 device-hashed
captures. All 1,509 source inputs matched between both runs and remained unchanged;
synthetic packages were removed and network/display settings restored. Raw results
remain under `build/ux-context/runs/quiet-native/postmerge-full-gate` and
`postmerge-native-suite`; `integration-complete.json` pins their summaries and reviews.

Quiet's implementation packet is complete. Reviews identified inherited notes-save
truth/recovery and Add-a-set explanation issues for the existing UX23 follow-up;
W3's permanent five-tab render evidence is also required before Home and History.
Broader app UX phases and physical-phone acceptance remain separate. O12 does not
require owner testing during development. No Debug drop was prepared.

### UX23 workout-truth follow-up — in development

Base: merged Quiet `9b7a7f5a`. Preserve this packet's completed acceptance and its
original failure archives. The next bounded packet precedes Home and History:

| Existing concern | Change and acceptance task |
|---|---|
| UX23 / notes truth and recovery | Distinguish pending, saving, confirmed saved and failed notes in the live floor and saved-session editor. Retry the latest authored text; hold overlapping writes and verify that exit/Finish retains the latest durable notes. Preserve raw draft/trim, empty deletion, restore hydration and the 400 ms debounce unless a measured interruption demonstrates a separate defect. |
| Q02 / existing Add-a-set explanation | Keep eligibility, thresholds, seed numbers and Apply-as-draft behavior. Explain the actual readiness inputs/window in its own trace; a fallback with no comparison must not claim observed improvement. Keep/Use controls remain reachable and write no set by themselves. |
| UX23/UX25 / truthful recovery and spoken actions | Missing-session copy reports only what the missing read proves; failed reads retain Retry. Coach dismiss has a meaningful spoken purpose and remains disabled during a pending write. |
| W3 / TS-3 development evidence | Permanently draw loaded Home, Body, Plan, History and Settings screens in the native-graphics JVM gate. Capture actual content; do not treat a navigation label/component fixture as a whole-tab draw. This is smoke coverage, not whole-app adaptive or phone acceptance. |

One initial notes-probe attempt failed to compile before executing tests. Its
source and log remain in `build/ux-context/runs/workout-truth/notes-race-baseline`;
that attempt provides no application-behavior evidence. The corrected attempt
`notes-race-baseline-attempt02` executed two tests against unchanged production
`9b7a7f5a`: both failed, with zero errors/skips and unchanged source hashes.
Exit started both `older` and `latest` writes while the older write was held;
after Finish committed `final words`, releasing the older write replaced the
durable notes with `older`. These observed races justify serialized notes writes
and a Finish barrier. Fixed-source results will be appended separately.

The intermediate `full-gate-attempt03` freshly passed 3,664 tests across 553
suites with no failures/errors/skips, plus all required builds/lint/static checks.
That source predates the required notes/Why/recovery matrices and the subsequent
review fixes; it is not final acceptance for this packet. Attempt 04 and targeted
attempt 06 stalled inside the JVM notes-render fixture. Their thread dumps,
identity-checked worker termination, unchanged source hashes and incomplete
results remain archived; neither produced fresh XML or a passing gate. The
fixtures now check the actual saving message, centered text width, correct dialog
focus ownership and explicit held-clock frames without removing clipping,
touch-target or exact-record assertions. The reached notes-dialog Done target
measured below 48 dp, so its native minimum height is corrected.

Fresh provisional independent and adversarial reviews found two additional
notes exits: History Repeat/Resume could navigate before failed notes were
resolved, and Finish from the live bar could clear a kept draft. The same UX23
packet now protects Back and gates History Repeat/Resume before starting another
workout, replays the intended destination on Retry, and reloads the actual saved
row after an explicit discard while leaving History editable on return. These
navigation claims cover the protected Back/Repeat/Resume paths. Exercise Details
and Rest push another screen while retaining the editor and cache owner.
Outside-editor Finish refuses an unresolved draft or live notes writer before stopping rest or
changing records; ordinary confirmed notes retain the existing finish path.
A failed clear restores as an authored empty draft rather than an unfilled field.
The guarded live exit owns its intent while saving, preventing a competing
Finish/discard from racing its navigation. These implementation changes passed
the complete local gates below, including the later review corrections. Native04
subsequently passed as recorded below; final pinned reviews and integration
remain pending. These changes introduce no database/backup format or coaching
algorithm.

#### Latest targeted verification and pending repairs — 8 October 2026

`targeted-attempt08` freshly executed **182 tests across 32 suites: 18 failures,
zero errors/skips**. All 32 XML files were fresh and all **1,521 source inputs**
remained unchanged. Its summary and original failures remain under
`build/ux-context/runs/workout-truth/targeted-attempt08/`. This was a failing
targeted run, not a full gate or completed matrix acceptance.

Nine failures exposed actual compact recovery targets measuring **40 dp**,
below the 48 dp minimum. Their implementation now uses the 48 dp minimum;
regressions retain bounds and scroll-reachability checks. The other nine
failures concerned render/focus fixtures observing the wrong native window.
Those fixtures now address the correct window. The later failing attempt below
records their rerun separately; source inspection alone is not a passing profile.

Further provisional review found that the first outside-editor Finish guard
could read a stale draft snapshot, and a notes write could still race after
that check while Finish reached SQL. The current repair reserves the session
before Finish's first read and shares the notes-write gate through actual
completion and cache clearing. The editor stays locked during that reservation.
A failed Finish releases the lock and keeps the authored draft available for
recovery. These internal Finish/cache contracts are necessary to protect the
same notes, rather than extending the product's workout behaviour.

The History forward-navigation barrier keeps Repeat/Resume queued until notes
are resolved. If explicit forward discard cannot reload the saved row, autosave
is paused for that exact draft revision until a new edit or explicit Retry, so
the text being discarded cannot later write itself back. Three Room-backed
regressions cover the Finish interleavings, and a separate failed-discard
regression covers that History recovery path. The complete gates below verify
that source and the subsequent review corrections. At that stage, complete native
verification and final pinned reviews remained pending; Native04 is recorded below.

`targeted-attempt09` ended incomplete with **zero fresh XML and no completed
test count**. Its 32 retained XML files are older results, not results from this
attempt. All 1,521 source inputs stayed unchanged. The stalled notes-render
scroll/idle loop, worker thread dump and identity-checked termination of the
owned worker remain under
`build/ux-context/runs/workout-truth/targeted-attempt09/`; this is not a pass.

`targeted-attempt10` then freshly executed **187 tests across 32 suites: eight
failures, zero errors/skips**, with all 32 XML files fresh and all **1,521 source
inputs unchanged**. Preserve its summary, executed source and original failures
under `build/ux-context/runs/workout-truth/targeted-attempt10/`. Seven failures
were in the older floor-notes fixtures: they assumed immediate DAO entry at the
typing deadline while the current write path first reads the real Room session.
The fixtures now wait for that read and actual DAO entry with virtual typing
time held fixed, preserving the exact 400 ms deadline, write ordering, latest
durable text and empty-deletion assertions. All seven formerly failing cases
passed in `full-gate-attempt05`, recorded below.

The eighth failure was `NotesTruthMatrixRenderTest.landscapeFont20`: an
unscrolled End workout dialog's Session notes toggle was tapped without opening
the text field, so the subsequent text-input assertion found no `SetText` node.
The separate `end-landscape-probe01` freshly executed **one test, one failure,
zero errors/skips**, with one fresh XML file and all **1,521 source inputs
unchanged**. Its captures and semantics at
`app/build/screen-renders/workout-notes-truth/f7778a13-e615-4173-8057-e313ba488829/800x360-font2.0/`
show the toggle fully clipped, starting **one pixel below** the text viewport.
The dialog retains a **236 px** scrollable text slot and fully visible **56 dp**
Save/Leave without saving controls; the attempted touch did not expand notes.
The driver repair adds a bounded scroll and actual touch before checking
the expanded field, without removing clipping, touch-target or exact-record
checks. Its nine-profile notes matrix passed in `full-gate-attempt05`. The failed
probe's source, log, XML and summary remain under
`build/ux-context/runs/workout-truth/end-landscape-probe01/`.

The earlier targeted failures remain archived even though their repairs passed
the subsequent complete gate. `full-gate-attempt05` freshly passed **3,701 tests
across 554 suites, zero failures/errors/skips**, with all **554 XML files fresh**
and all **1,521 source inputs unchanged**. It ran
`./tools/dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest --rerun-tasks`
from **2026-10-08T23:13:30.6250547Z** to **2026-10-08T23:24:09.8255104Z**.
All **135 actionable tasks executed**, including the required builds, lint,
Android test assembly and static checks. The seven Room-timing cases and all
nine Notes, nine Why and nine Missing-session profiles passed.

The summary and executed source remain under
`build/ux-context/runs/workout-truth/full-gate-attempt05/`. The summary's SHA-256 is
`aa938a634758d2566ad4ff4e4ca9c7fca6c4a5e0eec7aa7fbf2ddfefaf2f59e9`.
This pins the archived uncommitted source and its before/after hashes. At that
run, HEAD was still the merged Quiet base and no candidate commit was pinned.

Fresh provisional reviews after that gate found **three P2 corrections**, now
implemented and mechanically verified in `full-gate-attempt06`:

- Allow protected Back after confirmed Finish when the exit intent is stale,
  covering both same-process and cold-cache restoration.
- Reject late IME edits while a kept-draft live exit is queued, so the queued
  revision cannot change after the leave decision.
- Make the live notes Leave guard describe current-app-run retention accurately.
  Popping the editor removes its `SavedStateHandle`; failed unsaved raw text is
  retained in the process cache while the app stays running. The copy executed
  in this attempt was:
  “Notes are not saved. This draft is kept while the app runs and may be lost if
  the app closes.” This does not promise a durable draft store or process-death
  recovery after that exit.

These corrections postdate `full-gate-attempt05`. That frozen source's
`full-gate-attempt06` freshly passed **3,702 tests across 554 suites, zero
failures/errors/skips**, with all **554 XML files fresh** and all **1,521 source
inputs unchanged**. The same four-task command above ran with `--rerun-tasks`
from **2026-10-08T23:27:22.0464651Z** to **2026-10-08T23:37:53.4235623Z**;
all **135 actionable tasks executed**, with required builds, lint, Android test
assembly and static checks passing. The new Room case covers a stale finished
route before and after losing the completed cache. The held queued-Back case
covers the late IME rejection. All **nine changed Notes-guard profiles, nine Why
profiles, nine Missing-session profiles and five loaded shipping-tab renders**
also passed their then-current assertions. This establishes the two exit repairs
mechanically on that source. The guard-text proof limitation discovered after
this gate is recorded below; the passing count does not prove its full message
was visible.

The frozen-source local evidence is retained under
`build/ux-context/runs/workout-truth/full-gate-attempt06/`. The summary SHA-256 is
`c21e487bf1b61139b832675d28b5f578c28ec40ccccc5acd37d2eb99c8bcdc88`;
the before-run source manifest SHA-256 is
`2fe65d8624da86c193fd2c76bd3bf23a0bdbd93f039246d5bd2a92d44dd546d6`.
The executed source was uncommitted at that update; these archive hashes pin
that local result, not the later committed candidate.

After that gate, inspection of the landscape/font-2.0 notes guard showed its last
line, “app closes.”, below the visible text viewport. The fixture compared the
scrolling Text node's viewport size with its visible bounds, so it could pass
without proving that every message line was exposed. This is a verification gap;
the capture alone does not prove that scrolling cannot reach the line. The same
packet subsequently shortened the live and History messages and added full
text-layout and per-line rendered-glyph checks. That repair postdates
`full-gate-attempt06` and passed the new complete gate recorded below. The
original capture and executed source remain retained; the earlier passing count
is not retroactively strengthened.

The owned, unfiltered, offline API-29 `native-suite-attempt01` then completed
**222 tests across 32 classes: two failures, zero errors/skips**, with all
**1,521 source inputs unchanged**. It ran from
**2026-10-08T23:38:06.6669725Z** to **2026-10-08T23:46:59.9747135Z**, preserving
**83 captures whose host/device hashes match**. The eight new notes and
missing/failed-read recovery cases passed, including held/latest writes,
failed-save Retry, Finish, and History correction/clear. The connected workout
journey also passed in this run.

Both new Add-a-set Why cases failed at the summary assertion. The actual text
was “Your planned sets are complete. 4 of 5 readiness checks support one extra
set.”; the fixture used an exact-match assertion with only the latter fragment.
The cases stopped before their fact traversal and Keep/Use actions, so this run
does not verify those actions or their no-write assertions. The fixture was
corrected to assert the full sentence for the next run. This is a failing native suite, not a
pass or evidence of a different coaching decision.

The original XML, source, log, capture hashes and summary remain under
`build/ux-context/runs/workout-truth/native-suite-attempt01/`. Its summary SHA-256
is `ec95bd34149dfa0f62be4b1a5c2f10e34ac05cfabc1fc994ed76b0ff96f9624f`.
The collector stopped, test packages were removed and the runner restored the
device's network settings.

`full-gate-attempt07` freshly passed **3,702 tests across 554 suites, zero
failures/errors/skips**, with all **554 XML files fresh** and all **1,521 source
inputs unchanged**. The required four-task command with `--rerun-tasks` ran from
**2026-10-08T23:50:13.2875720Z** to **2026-10-09T00:00:54.5239809Z**; all **135
actionable tasks executed**, including required builds, lint, Android test
assembly and static checks.

The live guard now reads “Notes are not saved. Closing the app may lose this
draft.” The History guard reads “Notes are not saved. Leaving discards these
changes.” Both retain their recovery choices. Across all **nine notes profiles**,
the strengthened fixture checks the actual OS font, full expected text through
its final character, unclipped text-layout bounds without ellipsis, initial
self-scroll position and actual rendered glyph ink on every line in the measured
Android modal. The existing real-touch, action-size and exact-record assertions
remain. The nine Why and nine Missing-session profiles and five loaded shipping
tabs also passed.

The agent visually reviewed the complete live/History warnings and actions at
landscape/font 2.0 in
`app/build/screen-renders/workout-notes-truth/c8c0db77-f775-432a-8f70-f662c056ece0/800x360-font2.0/`.
These are native-graphics JVM captures, not physical-phone acceptance. Full07's
summary and frozen executed source remain under
`build/ux-context/runs/workout-truth/full-gate-attempt07/`; the summary SHA-256 is
`a27ea8a4d68b8bd5087c9b7333d3cea745e0c950944e4de7138b661bf3a3ce58`.
No candidate commit was pinned at that run.

The owned, unfiltered, offline API-29 `native-suite-attempt02` completed
**222 tests across 32 classes, zero failures/errors/skips**, with all **1,521
source inputs unchanged** and **85 captures whose host/device hashes match**.
It ran from **2026-10-09T00:01:03.6776496Z** to
**2026-10-09T00:07:47.9438441Z**. Both corrected Why cases reached their facts and
Keep/Use actions and passed their draft-only/no-write/no-rest assertions. The
eight new notes/read-recovery cases and connected workout journey passed again.
The original Native01 failures remain preserved separately.

Native02's summary, executed source, XML, log and capture hashes remain under
`build/ux-context/runs/workout-truth/native-suite-attempt02/`; its summary SHA-256
is `b3a5b1a00eac0a473a9dddb02bcb2a0822a0dcdc4df00af27d0fbe0494e37b80`.
The collector stopped, test packages were removed and network settings restored.
This pass applies to that frozen source and owned emulator fixture.

After visually reviewing that evidence, the agent clarified the missing-workout
body on the live floor and Rest screen to: “This workout is not running. If you
finished it, look in History.” The matching JVM constant and native expectation
were updated; these are four literal-copy edits, with no recovery-action or data
contract change. They postdate Full07/Native02 and were verified in the fresh
complete local gate below; the earlier passes do not certify these later edits.

`full-gate-attempt08` freshly passed **3,702 tests across 554 suites, zero
failures/errors/skips**, with all **554 XML files fresh** and all **1,521 source
inputs unchanged**. The required four-task command with `--rerun-tasks` ran from
**2026-10-09T00:08:02.1849942Z** to **2026-10-09T00:18:37.9961652Z**; all **135
actionable tasks executed**, with required builds, lint, Android test assembly
and static checks passing. All **nine Missing-session profiles** passed with the
clarified copy, preserving full-text/glyph, bounds, scroll/touch-target and
exact-record checks. The passing gate also retains the nine-profile Notes/Why
matrices and loaded shipping-tab smoke coverage.

Full08's summary, XML, log and frozen executed source remain under
`build/ux-context/runs/workout-truth/full-gate-attempt08/`; its summary SHA-256 is
`1d554478034a334326e2c65d44efa1b99727148b1ea61a56ab2def581e336a96`.
No candidate commit was pinned at that run.

The portable Full08 frames below match their original generator files and the
hashes in [the follow-up manifest](quiet-native-followup.json). All are synthetic
native-graphics JVM captures at the actual OS font 2.0, not physical-phone
acceptance or an Android-device screenshot suite.

| ID / portable capture | Recorded scope |
|---|---|
| [N14: live notes exit guard](assets/N14-live-notes-guard-landscape-font20.png) | Landscape, 800×360: complete live warning and all three recovery actions in the actual modal. |
| [N15: failed History notes exit](assets/N15-history-notes-guard-landscape-font20.png) | Landscape, 800×360: actual Room-backed failed exit, complete warning and all three recovery actions. |
| [N16: missing workout](assets/N16-missing-workout-font20.png) | 360×640: factual conditional History message and reached Back to home action. |
| [N17: extra-set explanation](assets/N17-extra-set-explanation-landscape-font20.png) | Landscape, 640×360: reached four-of-five explanation and fixed draft-only Keep/Use actions; the earlier callout is partly scrolled out. |

The owned, unfiltered, offline API-29 `native-suite-attempt03` completed **222
tests across 32 classes: one failure, zero errors/skips**, with all **1,521 source
inputs unchanged** and **85 host/device-hash-matched captures**. It ran from
**2026-10-09T00:18:50.6946985Z** to **2026-10-09T00:28:21.690272Z**. The Why Keep
fixture captured a partial manual draft, **87.5 kg × 10 with no effort**, after
waiting only for the weight. Its post-Keep equality assertion then observed the
completed intended draft, **87.5 kg × 12 at effort 8**. This is a failed fixture
precondition; the result does not demonstrate Keep changing a complete draft.
The original failure remains under
`build/ux-context/runs/workout-truth/native-suite-attempt03/`, with summary SHA-256
`7b458a2e59a46c7193e76daa4aa33bad842016cd2306d6a86caeea9b3b210e65`.
The collector stopped, test packages were removed and network settings restored.

The sole subsequent source change makes that native fixture wait for weight,
repetitions and effort together, then explicitly asserts the complete draft
before opening Why. Post-Keep equality, stored-row/no-SQL and no-rest assertions
remain. Production and JVM source inputs are byte-identical to Full08.

`full-gate-attempt09` completed the normal required command
`./tools/dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`
from **2026-10-09T00:30:41.6546001Z** to **2026-10-09T00:33:58.6825999Z**, with
**1,521 stable source inputs**. `assembleDebug`, `lintDebug` and
`assembleDebugAndroidTest` executed; `testDebugUnitTest` was **UP-TO-DATE**. Across
the command, **11 actionable tasks executed and 124 were up-to-date**. It executed
**zero new JVM tests**: all **554 XML files are byte-identical reused results**
from the fresh Full08 **3,702-test, zero-failure/error/skip** run. This is required
task-command success plus explicitly reused unchanged JVM evidence, not another
fresh 3,702-test execution.

Full09's summary, task log, source comparison and reused XML evidence remain
under `build/ux-context/runs/workout-truth/full-gate-attempt09/`; its summary
SHA-256 is `7ea19a47305f54376a6eb13cd294f5cfe43183004dac752023a51e3f556283fe`.
The owned, unfiltered, offline API-29 `native-suite-attempt04` completed **222
tests across 32 classes, zero failures/errors/skips**, with all **1,521 source
inputs unchanged** and **85 host/device-hash-matched captures**. It ran from
**2026-10-09T00:34:27.7143907Z** to **2026-10-09T00:41:09.9617529Z**. Both Why
cases passed with the complete intended draft precondition, retained post-Keep
equality and stored-row/no-SQL/no-rest checks. The notes/read-recovery cases and
connected workout journey also passed. Native03's original failure remains
preserved; this result applies to the corrected native source.

Native04's summary, XML, log, executed source and capture hashes remain under
`build/ux-context/runs/workout-truth/native-suite-attempt04/`; the summary SHA-256
is `0187020a853f5ce35a5efe3ff0b8e979366c51750ed2bd161719a016afa2c8fb`.
The collector stopped, test packages were removed and network settings restored.
A clean-trunk full rerun and affected connected journey remain required after
integration.

The selected portable Native04 frames below match the preserved device captures
and manifest hashes. The agent inspected both originals. These are actual owned
synthetic API29/font-2.0 emulator frames, distinct from N14–N17's JVM lane; they
retain the following visual limits.

| ID / portable native capture | Recorded scope and limit |
|---|---|
| [N18: failed notes, keyboard and Retry](assets/N18-native-notes-failed-retry-font20-ime.png) | Authored failed notes with the IME visible and Retry reached. This frame does not prove the whole modal or Done action is reachable. |
| [N19: extra-set Why facts and draft actions](assets/N19-native-extra-set-why-font20.png) | Explanation facts and draft-only actions reached. The inherited Material 3 “Drag handle” tooltip overlaps the title; this frame does not prove an unobscured complete title. |

N19's tooltip behaviour is also present in the prior frontend lane and does not
come from a changed Why-sheet component in this packet. Its overlap remains
visible in the preserved capture and evidence scope. Neither frame establishes
physical-phone acceptance.

The separate ignored N2 driver APK's `assembleDebug` and `lintDebug` build
passed with **43 executed tasks**; the separate controller compiled and packaged
with **50 executed tasks**. The first setup timed out waiting for the fresh
install's exercise catalog after registering a partial session, before writing
a set. Its separately named cleanup passed and restored the original seven raw
preference keys. The ignored controller then awaited the normal idempotent
catalog seed; fresh driver/controller builds passed again, followed by one
passing setup method. No production source changed for that prerequisite repair.

Actual first-launch UI traversal dismissed **Unrestricted battery** with
**Not now**, then used **Open settings**, enabled the overlay permission and
returned to Temper. Its UI XML is retained. The real live AppNav started
`RestTimerService` with `EXTERIOR_SYNC` even though no rest had been started.
`measure01` recorded `servicesBefore.isForeground=true` and rejected the
no-foreground-service precondition **before clearing the notes field or removing
the task from Recents**. This attempt ended **incomplete before editing**; it
establishes no `<400 ms` interval, process death or deletion success. The service,
driver guard and timing threshold were not changed to manufacture a pass.

A separate passing inspect method verified the same session
`421448df-0365-49ed-af65-3a739bb83bdd`, set, date/start and exercise records, no
running rest and unchanged original notes. This is unchanged readback after a
rejected precondition, not successful deletion recovery. A separate passing
cleanup method removed only the registered synthetic session and restored all
seven raw preference presences/values, including profile/display timestamps,
with mapped verification. The root logcat retains the `10650/10681`
`N2_FIXTURE_PREFERENCES_RESTORED` marker. Root restored the overlay app-op to
default and Wi-Fi/data to their original `1/1`; the restoration capture records
active default network `104`. All three known local APK identities were verified
before removing only the owned target, controller and driver packages.

The bounded runtime outcome, original setup timeout, separate named-method
results, UI and restoration evidence remain under
`build/ux-context/runs/workout-truth/n2-runtime01/`; its summary SHA-256 is
`e3c149cd9b54231e17170d50bdcf5ed9671b0d6627e69868d4fc91a1a84fd85c`.
Experiment builds and named setup/inspect/cleanup methods are excluded from the
permanent **222-test** native-suite count. N2 remains an explicit recovery gap.

Draft [PR #461](https://github.com/sinura7/PersonalTrainer/pull/461) records the
committed pre-repair candidate `f8943ae5`. The hosted failure and fresh Full11
fixture verification below supersede its earlier integration-ready status. An
amended immutable pin, fresh independent/adversarial reviews and updated hosted
checks remain required before integration, followed by the clean-trunk full rerun and
affected connected journey. The passing
local/native runs and bounded N2 attempt do not close UX23, the broader roadmap
or physical-phone acceptance. No schema, backup format, public API or new
coaching rule is introduced; the internal Finish/cache changes preserve the
existing notes-safety contract. No owner-phone testing or Debug drop is requested
for this development work.

Quiet's merged `9b7a7f5a` packet remains complete with its separately recorded
post-merge **3,625-test local gate and 212-test native suite**. Those earlier
passes do not certify this follow-up, close the broader roadmap, or establish
physical-phone acceptance.

#### Final repository formatting gate — 8 October 2026

The staged-file check caught CRLF line endings in the new
`SessionNotesSaveStateTest.kt`. The original bytes and failed check are retained
under `build/ux-context/runs/workout-truth/line-ending-finalization/`. Replacing
only CRLF with LF reproduces the final file exactly; test logic and all other
1,520 runtime inputs are unchanged. The staged whitespace check now passes.

`full-gate-attempt10` passed the normal required four-task command with **24
executed, 2 cached and 109 up-to-date tasks**. Builds, lint and Android test
assembly executed; `testDebugUnitTest` was **UP-TO-DATE**, with **zero newly
executed JVM tests** and all **554 XML files byte-identical** to Full08's fresh
**3,702-test pass**. All **1,521 inputs remained stable**. Its summary SHA-256 is
`c549d8413f8d1cb0d41e59df5fadbb66bffe7f6b21192cced079280ef3567975`;
final runtime-manifest SHA-256 is
`cb033c80d8ed63730ccd7cba8588d6870ff3c7b4ce999c5d7ac448ddb341dbc8`.

At the Full10 snapshot, Native04's production and Android sources remained
byte-identical; the sole subsequent change was that JVM fixture's line endings.
Its 222-test result retains that scope. The later hosted CI failure and test-only
repair are recorded below. Clean-trunk verification must freshly execute the full
required gate and affected native journey. N2, owner-phone acceptance and the
broader UX criteria remain open.

#### Hosted CI failure and fresh fixture verification — 8 October 2026

The committed pre-repair candidate is `f8943ae5` in draft
[PR #461](https://github.com/sinura7/PersonalTrainer/pull/461). The push
[CI run 37867942005](https://github.com/sinura7/PersonalTrainer/actions/runs/37867942005)
failed the deterministic `Tests, lint, debug build` job: **3,702 tests completed,
one failed**. The sole failure was
`DeletedNoteStaysDeletedTest.wordsTypedJustBeforeAProcessDeathComeBackAndAreWritten`:
the immediate exact-once assertion expected the restored authored note but saw
`writesStarted=[]` after **401 ms of virtual time**. The separate PR
[CI run 37867945752](https://github.com/sinura7/PersonalTrainer/actions/runs/37867945752)
passed its deterministic steps, and both hosted native jobs passed. Those passes
do not erase the failed deterministic gate or certify the subsequent repair.
The original push log, reports and pre-repair fixture remain under
`build/ux-context/runs/workout-truth/hosted-ci-diagnosis/`.

The author and independent reviewer identified a synchronization assumption:
the fresh transactional live-row read can still await real Room I/O before
entering the notes DAO when `runCurrent` returns. The fixture's existing bounded
`awaitWriteLanded` was after the failing immediate assertion. The repair changes
only `DeletedNoteStaysDeletedTest.kt`: both positive restore cases now await the
actual durable write before asserting exactly one write, while explicitly
checking that virtual time remains **401 ms**. Negative checks, SavedState
restoration, cancellation/join, cache clearing and actual-row assertions remain.
The dedicated exact **399/400 ms** debounce test passed even in the failed hosted
run; neither the production debounce nor native code changed.

`full-gate-attempt11` freshly passed the frozen repaired source: **3,702 tests
across 554 suites, all 554 XML files fresh, zero failures/errors/skips**, with
**1,521 stable inputs**. The actual command
`./tools/dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest --rerun-tasks`
ran from **2026-10-09T01:27:17.3770067Z** to **2026-10-09T01:38:04.0240231Z**,
returned exit **0** and **BUILD SUCCESSFUL** in 10m 40s; all **135 actionable
tasks executed**. Restore **7/7** and Move **24/24**, including the dedicated
**399/400 ms** debounce case, passed. Summary SHA-256:
`dc00702c2787049f1fecc1c99cfe673b32dcdef1bd7c979e97ed9a720599dc0d`;
final manifest SHA-256:
`ce85b25783fc00e7146eea9882d4a8114cfa4202c4e4a64b057928ce07d7008d`.
The only runtime-input delta from Full10 is `DeletedNoteStaysDeletedTest.kt`
(SHA-256 `db30b3b777966b58d3a6e9182770605acaf6c5577de09eb68a36987088cc2d9e`);
all production and Android bytes still match Native04. This pass verifies that
repair and preserves the original failed push result. An amended immutable pin,
fresh independent/adversarial reviews and updated hosted deterministic checks
were still required at that snapshot. They subsequently passed, followed by the
clean-trunk full rerun and affected connected journey recorded below. N2, UX23-AC01–AC03's broader scope, related UX24/UX25
coverage and physical-phone acceptance remain open; no Debug drop is recorded.

#### Workout follow-up integration — 9 October 2026 UTC

[PR #461](https://github.com/sinura7/PersonalTrainer/pull/461) merged as
`c238495172858d24ecc1c94353544eb602a9d6a0`, tree
`dacb74e747f0a720b613d873ad590a90657903ae`, identical to the reviewed candidate
`473fff3ae3033229e61ff810f254e37cec35eb23`. Both final source reviews passed with
no blocking findings. Clean-trunk `--rerun-tasks` verification executed all 135
tasks and **3,702 tests / 554 fresh XML suites**, with zero failures/errors/skips
and all **1,521 inputs unchanged**. The unfiltered native run passed **222 tests /
32 classes**, including the connected workout, with 13 fixture restoration pairs.

The native archive initially failed: its PowerShell helper indexed a scalar digest
string rather than splitting the completed command result. The unchanged original
run remains archive exit **1**, native exit **0**. A minimal parenthesis correction
and separate reviewed recovery verified exactly the original **85 captures** against
raw device hashes and host bytes, original XML/log/source identity and restored
device posture. Recovery executed **zero new tests** and preserved the failed archive.
The root also inspected the recovered manual-entry, notes-Retry/IME, Why and History
frames. Both amended candidate hosted runs and actual-trunk run
[37872983280](https://github.com/sinura7/PersonalTrainer/actions/runs/37872983280)
passed; raw hosted reports independently confirmed the same counts and source tree.

The ignored `build/ux-context/runs/workout-truth/integration-complete-c2384951.json`
pins the reports and execution summaries (SHA-256
`9dba3a5abcbde8f02a58e4b9a9fb126dea421ab36931ea447f031a59174c5284`).
The separately preserved reviewed Git bundle retains the deleted feature branch.
This completes integration of the bounded packet, not all UX23, W3, N2 or phone
acceptance. No distribution/version/signer change or Debug drop was made.
