# Temper UX evidence and design questions

**Research date:** 8 October 2026

**Source baseline:** commit `eae6517845ee560ceb9695bf2f92788e2b4339cc`, Debug 122.

**Owner confirmation:** S01-S05 show current Temper. Installed variant/version and capture settings are not established.

**Later owner report, 8 October:** currently using **Temper Debug 124**. The
workflow feels clunky; the desired direction is one obvious action, simpler
navigation and a polished, sophisticated front end, with Wealthsimple/Web3 as
references. This establishes broad friction and a design direction, not an
observed cause, task-specific difficulty or acceptance of an alternative. Exact
metadata for the five earlier captures remains unknown. See O09-O10 in the brief.

Read [the brief](README.md) and [workflow map](WORKFLOWS.md) together with this catalogue. Supplied image order is not a chronology.

## Evidence vocabulary

| Label | What it supports |
|---|---|
| Owner report | The owner's stated goal, provenance, preference, or experience. |
| Screenshot observation | Exactly what is visible in a supplied still. It cannot prove tap behaviour, timing accuracy, cause, or accessibility. |
| Code-supported | A behaviour traced to the pinned source. It has not necessarily been reproduced on the phone. |
| Accepted decision | The prescribed product direction in an applicable accepted ADR/amendment. |
| Hypothesis | A possible user consequence or explanation to investigate. |
| Proposed change | An alternative awaiting owner selection. |
| Tested result | A recorded execution with revision, fixture, environment, steps, outcome, and artifact. |

Keep separate evidence types in one finding rather than promoting a hypothesis to a bug. The screenshots contain real owner experience; preserve them as evidence, never as disposable database fixtures.

## Screenshot register

All five files were inspected visually from the conversation. Local file reads establish 658 × 1280 JPEGs; dimensions do not establish original device resolution, dp size, or whether the files were resized before attachment.

The original repository keeps owner phone reference images local. This packet follows that convention: unchanged copies live in ignored `docs/ux-context/references/`. The portable record below includes filenames, hashes, observations, and sources; a fresh clone will not contain the local originals. Shared native evidence can later use synthetic fixtures.

| ID | User filename / attachment filename | Local copy | Visible surface | Workflow |
|---|---|---|---|---|
| S01 | 43960.jpg / 1-43960.jpg | `references/S01-43960.jpg` | Rest on the phone launcher | W06-W07 |
| S02 | 43958.jpg / 2-43958.jpg | `references/S02-43958.jpg` | Exercise-list sheet over workout | W02/W08 |
| S03 | 43956.jpg / 3-43956.jpg | `references/S03-43956.jpg` | Home/selected-day board with live bar | W01/W07 |
| S04 | 43954.jpg / 4-43954.jpg | `references/S04-43954.jpg` | Expanded rest page | W03/W06 |
| S05 | 43952.jpg / 5-43952.jpg | `references/S05-43952.jpg` | Set entry, coaching and compact rest | W03-W06 |

### Integrity manifest

The attachment names are the source of each copied file. Copying preserves bytes and does not edit, crop, or annotate the original.

| ID | Bytes | Pixel size | SHA-256 |
|---|---:|---|---|
| S01 | 35,609 | 658 × 1280 | `8591d61d9048a8ec2227ab7e02a5df949e57527106323bde72bebe035eb5c030` |
| S02 | 47,080 | 658 × 1280 | `b22e6fa96e85b83057da4004cd682f7e06f445fca64815256639ecc961266290` |
| S03 | 51,331 | 658 × 1280 | `abfc8beb171cfffe3ddaed7219c15464949b40b9a0136ff73d83f71f24e92cda` |
| S04 | 33,445 | 658 × 1280 | `a6a9389c1acbd6bb8401bbf5a0fc19e7a120770f86b91817f55f89232369d90e` |
| S05 | 49,511 | 658 × 1280 | `cf182d8e26071d2c8c33ded1967daa72c15793f0875148bdf18fb9509740b467` |

**Still unknown for S01-S05:** capture time, app variant/version/signer, source commit, device/model, Android/API, OEM, density/display scale, font scale, navigation mode, permissions, whether an IME was recently dismissed, exact preceding actions, and whether the images share a session. Record only metadata that can actually be obtained; mark unavailable values rather than guessing from appearance.

### S01 — Rest outside the app

**Immediate job:** Track rest while using the phone elsewhere and return when ready.

**Visible facts:** A Rest notification reads 01:28 with -15s, +15s, Skip. A dark floating card contains a Volt ring, REST, and 1:29. An outlined × target is below it. The launcher/folder remains visible behind both.

**Code-supported context:** [Running presentation](../../app/src/main/java/com/sinura/personaltrainer/timer/RestTimerRunningPresentation.kt) intentionally supports background shade notification plus overlay. [Overlay controller](../../app/src/main/java/com/sinura/personaltrainer/timer/RestTimerOverlayController.kt) uses Android application-overlay permission, supports dragging/resizing, and a timer-scoped dismiss zone. [Overlay color](../../app/src/main/java/com/sinura/personaltrainer/ui/overlay/OverlayVoltColor.kt) uses Volt.

**Strength:** Rest remains visible after leaving the app; familiar adjustment/skip vocabulary is available.

**Hypotheses:** Two visible timer surfaces may cost attention or may be useful redundancy. The × could be a temporary drag target, not a permanently visible close button. The one-second counter difference can occur at different rendering moments and does not establish timer drift.

**Questions:** Which surface does the owner use? What does dismiss mean to them? Can they get back without finding the launcher icon? Does the overlay obstruct another task? Related Q03/Q06.

**Needed evidence:** Entry/exit sequence, overlay drag/dismiss, notification tap, permission-denied path, locked state, natural finish and Skip.

### S02 — Exercise-list sheet

**Immediate job:** Find current or unfinished work and switch intentionally.

**Visible facts:** “Upper B,” Finish, and “1 of 9 exercises · 1 of 24 sets” remain behind a dimmed “Lifts” sheet. Visible rows show thumbnails, names, working-set progress, Remaining, and rest durations. Add exercise appears at the bottom.

**Code-supported context:** [LiftSwitcherSheet](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/LiftSwitcherSheet.kt) supports Current/Complete/Remaining, selected semantics, and a current-row border. Selection restores the lift's draft through [ActiveWorkoutViewModel](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutViewModel.kt). The current row could be above the visible scroll position.

**Strength:** Rows provide progress and rest context, not only names.

**Hypotheses:** Current position may be harder to recognize in a long scrolled list. Repeated Remaining labels may add visual work. A partial first row can simply reflect scrolling; do not file clipping from this still alone.

**Questions:** Can the owner find the next unfinished exercise quickly? Do they expect a row tap to select, preview, or open details? Is Add exercise reachable at large text? Related Q04.

**Needed evidence:** Open sheet at current row, select another/back, preserve prepared inputs, running hold/stopwatch guard, all-complete and long-list states.

### S03 — Home with a live workout

**Immediate job:** Understand the selected day while retaining access to ongoing work.

**Visible facts:** Header “Thursday · 8 Oct,” Back to today, “Strength day · 9 lifts,” seven day cards, and an Upper B exercise list. Monday/Tuesday have checks; Wednesday has a Volt marker/text; Thursday has a brighter card; Friday's Workout text is red. A persistent bar says Workout · In progress, Upper B, elapsed 1:05, 1 set, Rest 1:43. Five tabs are visible.

**Accepted/source context:** [Home week-board decision](../architecture/ADR-017-home-week-board.md) and [HomeScreen](../../app/src/main/java/com/sinura/personaltrainer/ui/home/HomeScreen.kt) define the board; [AppNav](../../app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt) hosts the live session return. This still is insufficient to label Wednesday “today” or Friday “missed.”

**Strength:** Plan context and a route back to the workout coexist.

**Hypotheses:** Selected/today/completed/exceptional markers may require interpretation. A long plan can dominate the day board while active-session information remains compact.

**Questions:** What can the owner explain from the strip without opening a day? Does the full main area of the live bar read as tappable? Are dates/statuses understandable without color? Related Q05.

**Needed evidence:** Today/selected historical day, rest/empty/planned/completed days, live bar return/overflow, start conflict, midnight/date-change behaviour.

### S04 — Expanded rest

**Immediate job:** Rest and see enough context for the next set.

**Visible facts:** A top-left ×, large cyan ring, REST, 1:54; Incline Dumbbell Bench Press; Last set · 60 lb × 8; Next: 60 lb × 8 · RPE 9; Planned rest · 2:00. Bottom controls are -15, +15, Skip.

**Code-supported context:** [RestTimerScreen](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/RestTimerScreen.kt) and [RestCommands](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/RestCommands.kt) provide shared rest state/commands. [AppNav](../../app/src/main/java/com/sinura/personaltrainer/ui/navigation/AppNav.kt) makes Close return from the rest route.

**Strength:** Remaining time is immediately readable; the exercise and previous/next values keep the timer connected to training.

**Hypotheses:** Users may interpret Close as ending rest unless the transition is clear. The displayed RPE may be read as a target or as actual effort; the still cannot resolve that interpretation.

**Questions:** What does the owner do when rest ends? Is a quiet countdown sufficient, or is next-set preparation the main need? Related Q03/Q06.

**Needed evidence:** Close/return, adjustment, Skip, done/idle, next advice changed by a correction, extra-set planned rest, large text/landscape.

### S05 — Logging during rest with coaching

**Immediate job:** Understand current inputs and advice, then record the actual set.

**Visible facts:** Upper B header/progress; partial stats row; weight 60 lb and reps 8 with -/+ controls; “Last 50”; effort 6-10 with a small Volt marker by 9; Tempo with character image; reason truncated at “Hard set — hold the wei…”; ×, Why?, Apply; suggestion 60 lb × 8; cyan REST 1:56 with Planned 2:00 and -15/+15/Skip; subdued Log set with 60 lb × 8.

**Code-supported context:** [Primary actions](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/WorkoutPrimaryAction.kt) and [logCommitReady](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutViewModel.kt) do not use running rest to disable Log. A successful save clears effort. A suggestion indicator is not necessarily a selection. Apply fills weight/reps/effort without recording. The inspected [NextSetRecommendation](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/NextSetRecommendation.kt) does not establish this Tempo/mascot/dismiss presentation.

**Visible strength:** Large entry and timer numerals are visually prominent; -/+ controls, effort help, and Why/Apply affordances are visible. Their behaviour on this pictured build still needs matching or observation.

**Visible limitation:** The recommendation's full reason is not readable in the card. This establishes truncation in this state, not the owner's interpretation or its frequency.

**Hypotheses:** The information panels compete for attention. “Last 50” may be unclear about unit and reference. Subdued Log may lack an obvious reason; required effort is one possible explanation, not a proven cause. Partial upper content may be the intended scroll position.

**Questions:** Can the owner distinguish suggested effort from selected effort, identify the pending set, and explain Apply? What should stay visible during the most frequent entry/rest transition? Related Q01/Q02/Q07.

**Needed evidence:** Same state with effort absent/selected, complete top-of-screen, full advice and Why, Apply before/after, saved receipt, correction, disabled reason, exact build match for Tempo.

## Question register

**Q01 progress:** [The first worked study](studies/q01/README.md) establishes that missing-effort wording is present in accessibility semantics but absent from the visible Log control. A focused comparison and fresh mechanical baseline are available; owner comprehension and design choice are still pending.

Priority is a provisional research ordering for the complete-workout goal. Start with Q01 because recording actual work is repeated throughout a session. Q02 follows where advice contributes to entry confusion; Q03 follows for interruption/return. An observed wrong write, lost work, unreachable action, or owner-identified major disruption can override that order. Frequency and severity have not been measured. IDs Q01-Q08 are local research questions; they are not replacement defect/backlog IDs. No alternative below is accepted yet, and this sequence does not reorder the implementation queue.

| ID / priority | Question and proposed comparison | User consequence to test | Evidence / related history | Acceptance task |
|---|---|---|---|---|
| Q01 / 1 | Does the logging floor make saved result, draft, suggestion, and readiness distinguishable? Compare the current hierarchy with a focused layout that keeps entry/commit meaning clear. | Fewer uncertain taps or accidental reuse of the wrong value. | S05; W03/W05; UX03/UX23/UX24/UX26 | “You just completed this set with the result on the task card. Record what happened as you normally would.” Observe before explaining effort or Log. Then ask what they believe was saved. |
| Q02 / 2 | Is coaching understandable before Apply? Compare complete action-focused copy with the current truncated reason; keep Why for explanation. | Advice can be accepted deliberately without opening another surface to learn the proposed action. | S05; W03; ADR-027/029; identity mismatch C06 | “What do you expect this advice to do? Use it if you want to.” Record the prediction and first action before explaining Apply/Why; verify actual writes separately. |
| Q03 / 3 | Which rest/return surfaces help in each context? Compare current coverage and return paths before considering visibility changes. | Useful continuity without unexplained dismissal or obstruction. | S01/S03/S04/S05; W06/W07; UX22/UX25/UX26 | “During this rest, use another app, lock the phone, and return to your workout.” Observe return and expectations first; test individual Close/dismiss/Skip/natural-end cases afterward. |
| Q04 / Next | Can the owner orient and switch in a long exercise list? Compare current list with clearer current/next cues if actual difficulty appears. | Fast intentional switching with preserved input. | S02; W08; UX03/UX24/UX25; W1a already implemented | Find next unfinished lift, switch away/back, and predict guard/correction behaviour. |
| Q05 / Next | Are day-board and live-session states independently clear? Compare current markers with explicit non-color state cues as needed. | Avoid starting the wrong day's work or losing the live session. | S03; W01; UX01/UX12/UX22; F4 historical queue | Identify selected/today/completed status and return to the same live workout. |
| Q06 / Next | Does color and terminology carry consistent meaning across rest, actions, and progress? Assess cyan/Volt discrepancy and Close/Skip vocabulary together. | State meaning transfers between app and system surfaces. | S01 versus S04/S05; C04; ADR-005/023/027 | Owner explains timer state/action using labels; verify semantics without color-only cues. |
| Q07 / Next | Is “Last” explicit enough to serve the next action? Compare existing shorthand with a clear source/unit label if confusion is observed. | Previous performance can be reused without guessing. | S05; W02/W03; ADR-030 | Owner identifies whether Last refers to prior session/saved work and predicts its fill. |
| Q08 / Next | Is ending, leaving, correcting, and discarding understandable? Study current transitions before proposing new confirmation copy. | Saved history and prepared changes are retained intentionally. | W09-W11; no supplied end-state image; UX23/UX26 | Correct a set, leave/return, finish early, cancel discard, and inspect the summary. |

### How to run the first study

This is a bounded owner study for Q01, with Q02 included only if advice affects the same task. The output is one evidence-backed problem statement, a recorded absence of difficulty in the observed task, or a named evidence gap. It is not necessary to complete the whole screenshot wish list first.

1. **Prepare comparable states.** Record the installed build/variant, source match, units, viewport/font settings, and a synthetic fixture with known prior work and a known pending set. Use an actual-result task card whose weight/reps differ from the suggestion, then a repeat-set case. These are logging fixtures, not training prescriptions. Capture the before state and saved result. If the Tempo/source mismatch prevents a reliable reproduction, study visible interpretation and label behaviour unknown; use the pinned native build for a separately labelled functional check.
2. **Observe without teaching.** Use Q01's neutral task. Record the first interpretation/action before naming the expected control, supplying effort instructions, or correcting an assumption. Ask what saved after the action. If help is requested or required, give it and mark the result assisted. Treat advice acceptance as optional. For Q02, capture the prediction before use, then observe and verify the result.
3. **Check the whole consequence.** After the unaided task, run relevant mechanical scenarios V02-V07/V14. Include a short start → record → correct → finish → summary/history baseline (V11/V12) before recommending a layout for the complete workout. Explore rest/return separately through Q03/V09/V10. These checks may be guided; do not count them as unaided usability success.
4. **Measure only useful differences.** Record unaided completion, incorrect interpretations/actions, assistance, corrections, taps/scrolls, and whether the intended result saved once. Measure UI interaction time only when useful, excluding physical exercise, planned rest, facilitator questions, and think-aloud narration. Record device/input method and first-use versus repeat attempts. A shorter task is not an improvement if the saved result or recovery is wrong.
5. **Compare one alternative when justified.** Before the comparison, choose the relevant measures and what result would change the design decision. Use equivalent fixtures and the same task; record presentation order and learning effects. Where practical, revisit the baseline after the alternative. Do not invent percentage speed targets or generalize one owner's results to a population.

**Facilitator note:** The synthetic scenario also needs a natural description of how difficult the set felt, or an agreed fictional exertion, so the owner has enough information to record the result. Do not instruct which UI control to use. Assess discovering and recording effort, not the physiological accuracy of a fictional rating.

**Decision rule:** Recommend an alternative only if it addresses the observed problem, the owner understands/prefers the resulting task, and relevant recording, recovery, accessibility and reachability checks still pass. Retain the current design when no problem or meaningful benefit is observed. Revise when the alternative creates a misunderstanding or functional regression. Mark the result unresolved when build mismatch, assistance, missing transitions, or conflicting evidence prevents a conclusion. A visual concept may advance to native evaluation; “Validated” requires the relevant executed evidence, not preference alone.

Use this compact result record for each Q ID; leave unexecuted fields explicit:

| Field | Required record |
|---|---|
| Scope and provenance | Q/W/V and existing UX IDs; build/commit, fixture, device/settings, artifact IDs, source match or gap |
| Task and comparison | Exact neutral prompt, expected actual result, current/alternative, order, first/repeat attempt, selected measures and prior decision rule |
| Observation | First interpretation/action, unaided or assisted completion, wrong actions, taps/scrolls, correction/recovery, optional interaction time with exclusions |
| Verification | What actually saved and how checked; relevant V results; accessibility/reachability limits; artifact location |
| Evidence against the hypothesis | Contrary observations, successful current behaviour, order/learning effects, unresolved alternative explanations |
| Outcome and next action | Supported problem / no observed issue / evidence gap; retain / revise / recommend native evaluation / unresolved; owner's decision if made; smallest next task |

Capture the owner's words separately from the observer's interpretation. More screenshots can refine a hypothesis; they cannot retrospectively turn an assisted task into an unaided success.

### Crosswalk to existing work

Reuse [UX backlog](../ux-program/UX-Backlog.json) and [acceptance tasks](../ux-program/UX-Acceptance-Matrix.md) as historical references: UX01 start semantics, UX03 primary transitions, UX06 numeric input/units (including V14), UX12 calendar states, UX22 navigation context, UX23 truthful states, UX24 responsive content, UX25 accessibility, UX26 feedback.

[Frontend redesign](../FRONTEND_REDESIGN.md) records W1a lift switching, W1c/W1d stable/adaptive entry, and W2b shared rest/next-set work as completed. The current source commit also includes P4a permissions/exterior rest. Their historical findings must be rechecked, not reopened by default. W3/native acceptance and surrounding screen packets require current status reconciliation before a new implementation order is proposed.

Existing fixture references worth reusing are F02 day board, F03 live strength, F08 failures, F10 units/input, and F11 accessibility/windows. Confirm their current builders before execution.

## Next evidence to collect

The present batch covers visible mid-workout/rest surfaces. The following is an evidence menu, not a prerequisite checklist for Q01. Select only states needed to resolve the current question and complete-workout baseline:

1. Preparation before the first working set, including suggested-only and selected effort.
2. Coaching with the full recommendation, Why, Apply, and the resulting draft.
3. Immediately after a successful save, plus correction/Undo and a failed-save recovery.
4. Last prescribed set, Next exercise, all-complete, explicit extra set, warm-up and hold lead-in.
5. Rest idle/done, Close/return, overlay dismissal, permission-denied and lock-screen states.
6. End workout dialog, outstanding correction, summary, History and Session Detail.
7. Planned/rest/empty/completed Home days and the start confirmation.

Accept new screenshot batches of up to five using the next stable IDs. Ask for a short description of what happened before/after each screen when it matters; a screen recording or narrated walkthrough can establish transitions more effectively than an isolated still. Do not require every metadata field before useful discussion can continue.

For each new item record: ID, original filename, supplied order/date, current/reference/historical provenance, image hash and dimensions when available, source/build match, user job, observations, hypotheses, related W/Q/UX IDs, and missing evidence. Do not overwrite S01-S05 or infer chronological order.

## Local/runtime validation plan

The original checkout and the current source snapshot differ; the brief records the worktree separation. The session now has unrestricted local access following the initial sandbox failure. Runtime evidence collection remains a separate task:

- Establish installed variant/version using available metadata or the owner; check signing identity before any install over Obtainium.
- Reproduce W01-W11 with synthetic records and current fixture builders in a dedicated Temper Debug emulator.
- Run V01-V14 selectively for the chosen design question; record exact actions and expected/observed results.
- Use current JVM Compose renders and reachability checks under ADR-032: 360×640, 412 dp, landscape, fonts 1.0/1.6/2.0, and adaptive 600 dp. Add RTL, IME, TalkBack and reduced-motion evidence where relevant.
- Run the repository verification gate for an implementation packet. On Windows use `./tools/dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`; this compiles device tests but does not execute them.
- Physical-device checks remain necessary for actual touch, notifications, lock/overlay behaviour, Doze, sound/haptics and owner acceptance. Follow the supported emulator/phone lanes in [DEVELOPMENT](../DEVELOPMENT.md) and distribution in [SETUP](../../SETUP.md).

Historical Windows Room/Robolectric caveats are diagnostics, not a preemptive waiver. Reproduce failures and report host limitations separately from app failures. A successful toolchain startup, source review, or HTML prototype does not establish a successful app build or native UX acceptance.

## Initial foundation verification

This records the initial three-document packet before [the second audit](AUDIT-2026-10-08.md). Counts and review outcomes below belong to that initial revision; the later audit records its own corrections and checks.

- Read local git status: original branch `codex/home-day-design` at `86311fbda00940d4d7e78fbff54b498edfddc989`, with Home-related tracked edits.
- Fetched current trunk and created the isolated documentation worktree at the pinned `eae6517` source baseline.
- Inspected the five supplied images and read their byte sizes, dimensions, and SHA-256 hashes.
- Read accepted decisions, historical UX sources, and current workout/timer/navigation code.
- Kept this packet to three new research documents, a root README pointer, and the local-reference ignore rule. No app, database, distribution, or existing Home implementation files were changed.

### Initial checks run on 8 October 2026

These results describe the documentation foundation before the owner authorized
roadmap implementation. Preserve these failures; later repaired reruns belong
in [the Q01 study](studies/q01/README.md), rather than replacing this initial evidence.

| Check | Observed result | Meaning |
|---|---|---|
| Documentation authority checker | Passed with zero authority findings. | Configured vocabulary and active-document link checks passed. The checker does not establish semantic agreement with all decisions; that requires source review. |
| Local documentation validator | Passed: 125 relative links/anchors, required O/W/V/S/Q/C IDs, UTF-8/LF formatting, five screenshot hashes and ignore rules. | References resolve in this worktree; the local image copies match the originals and remain outside version control. |
| Staged whitespace check | `git diff --cached --check` passed. | The five-file documentation packet has no whitespace errors. |
| Gradle static checks | Passed on the corrected packet, including source checkers and Kotlin syntax. | Static verification passed; its `preflight: OK (static only)` output is not the full JVM preflight result. |
| Full Windows gate | `./tools/dev-windows.ps1 testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest` failed at `testDebugUnitTest`: 3,570 tests executed, four failures, zero skipped. | The required app gate is not green. All four failures are in `TemperPreMigrationCopyTest`; see below. |
| Build/lint confirmation | `./tools/dev-windows.ps1 assembleDebug lintDebug assembleDebugAndroidTest` completed successfully after the unit-suite result. Lint reported zero errors and zero warnings. | Debug and device-test APK assembly and lint succeeded. Device-test assembly does not execute those tests. |
| Standalone full preflight | `tools/preflight.sh` failed in the pure JVM lane while launching `org.jetbrains.kotlin.cli.jvm.K2JVMCompiler`: `ClassNotFoundException`. Static checks and syntax had passed. | The fallback compiler launch failed on this host. The cause remains unresolved; this is separate from the successful Gradle app compilation. |

The first gate attempt found a bad Library source link in this packet. The link was corrected to `ExerciseLibraryScreen.kt`, then the static checks and full gate were rerun. The results above describe that corrected packet.

The four unit failures were:

| Test in `TemperPreMigrationCopyTest` | Reported failure |
|---|---|
| `anOlderTemperDatabaseIsCopiedWithItsWalEvenAfterTheLegacyCopyIsDone` | `SQLiteCantOpenDatabaseException`, SQLite code 14 (`SQLITE_CANTOPEN`). |
| `theCopyStillHoldsTheOldFileAfterRoomMigratesTheLiveOne` | `SQLiteCantOpenDatabaseException` opening the pre-migration copy in the Robolectric temporary directory. |
| `aCopyFinishedByALaunchThatDiedBeforeItsMarkerIsKeptAndOlderOnesAreStillTidied` | Expected versions `[5, 6]`; observed `[4, 5, 6]`. |
| `aCopyLeftHalfWrittenByADeadLaunchIsTakenAgainNotKept` | `SQLiteCantOpenDatabaseException` opening the pre-migration copy in the Robolectric temporary directory. |

These were failures in the existing migration-copy suite on this Windows host. At this stage their cause had not been established. Neither historical Windows notes nor the absence of app changes proved a host-only cause. No tests, checks, or merge protections were bypassed.

The initial full unit report and preflight log are preserved locally under `build/ux-context/runs/initial-gate-20261008/` (the report entry point is `html/index.html`). They were archived before the Q01 targeted run replaced Gradle's normal report directory. These are ignored build outputs, not durable versioned evidence.

### Review and handoff

An independent source/authority review found no critical, high, or medium findings in the three documents. A separate adversarial screenshot/workflow review found no actionable findings. The latter read the brief, evidence catalogue, and initial workflow material directly; after its shell access failed, it reviewed the remaining workflow material from the parent-supplied continuation. That access limit is part of the review record.

At this initial handoff the foundation was local on `codex/ux-context-foundation`, and had not been pushed or submitted for merge because the gate was red. The subsequent owner-authorized implementation repairs those verification failures and records new results in [Q01's connected baseline](studies/q01/README.md#connected-workout-baseline-and-verification-repair).

No fresh emulator journey, physical-phone acceptance, installation, or Obtainium drop belonged to that initial handoff. V01-V14 were future research/acceptance tasks; the later connected tests cover specific parts, not all branches of those tasks. Continue with the bounded first-study procedure and the executed baseline; owner task observation remains separate.
