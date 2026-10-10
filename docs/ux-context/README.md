# Temper UX foundation

**Created:** 8 October 2026

**Status:** Native Quiet workout and its bounded UX23 follow-up are integrated through PRs #460 and #461. Home required-read/Retry recovery and Home/Plan week geometry are reviewed and integrated through #462 and #464. History's one-period workflow and captured-date travel repair are reviewed and integrated through #465 and #466. Summary/detail identity, planned/recorded clarity and restored saved-work reachability are integrated through #467. Set Edit identity and numeric controls are integrated through #468. History saved-set recovery merged through #469; its final closure awaits the focused History test-recorder repair and a reliable complete gate. Broader app UX phases and physical-phone acceptance remain.

**Summary/detail integration closed, 9 October 2026 UTC:** [PR #467](https://github.com/sinura7/PersonalTrainer/pull/467)
merged as `755a2df9`, exact reviewed tree `f7628c17`. Actual clean-trunk full
verification passed **3,990 app tests / 576 fresh suites** and **1,694 standalone
tests / 255 classes**; local API 29 passed **222 tests / 32 classes** with the
connected workout → Summary → History/detail journey, 85 hashed captures and
verified synthetic-state restoration. Independent/adversarial packet reviews
and final independent integration review passed. The temporary branch was
removed after verifying its recoverable bundle. The first trunk hosted attempt
remains **failed by its enclosing 30-minute job limit**; one complete unchanged
retry passed the required build gate and the supplemental native job. The
render archive's separately recorded caller error remains distinct from its
verified stored files. Pure-hold Summary representation, retained Summary
return policy, broader UX and phone acceptance remain open; no Debug drop occurred.

**Set editor candidate, 9 October 2026 UTC:** The existing F9/W11/V12/Q08/UX29
refinement now implements full exercise names and matching artwork, an expanded
sheet, readable values and signed controls, reachable effort/actions, explicit
Cancel and captured-time correction. Historical repetitions retain their
accepted range; live/new-entry limits retain their existing rule. Add drafts
keep their exact session/exercise owner through unavailable-read/Retry, while
Cancel and successful owner removal clear that draft. Timed Delete/Undo copy
shows seconds and its action meets the 48 dp target.

Three actual failing counters remain separate evidence: `counter01` changed an
8-rep/45-second original to zero reps under hold metadata; `counter03` submitted
100 after an accepted 101-rep original and +1; `counter04` reset authored Add
reps 9 to 8 after Retry without losing a saved row. The repaired focused run
`targeted03` passed **67 cases / 3 fresh suites**, including exact restored rows,
draft recovery, Cancel/removal, identity, time/repetition boundaries and native
layout checks. It predates the subsequent timed Undo-copy assertion.

The earlier `full01` passed **4,049 app / 1,696 standalone** tests at its prior
source. Current-source `full02` ran **4,049 cases / 578 fresh suites** with one
failure: the old thumbnail test's unconfined Compose scheduler applied layout
from its IO worker. All new editor cases passed, but the complete gate **failed**.
The test now uses the installed Compose v2 scheduler with a cold real decode,
retaining every original fit/badge/pixel assertion and bounded wait; `targeted04`
passed both actual thumbnail cases. No app loader or check was weakened.
`counter02` had zero executed tests from fixture compilation/static errors;
the original failed runs and 2,694 full02 PNGs remain archived under
`build/ux-context/runs/set-editor-identity-time/`. The repaired `full03` complete
gate closed **PASS on 10 October UTC**: **4,049 app cases / 578 fresh suites**,
**1,696 standalone tests / 255 classes**, zero failures/errors/skips, all four
required Windows tasks and unchanged **1,550 runtime inputs**. Native graphics
review accepts the changed identity, controls, footer, timed Undo and draft
recovery; the thumbnail repair keeps real IO and every original assertion.
**Set editor integration closed, 10 October 2026 UTC:**
[PR #468](https://github.com/sinura7/PersonalTrainer/pull/468) merged as
`09ff23d5`, exact reviewed tree `307db5c3`. The actual clean-trunk complete
gate passed **4,049 app cases / 578 fresh suites** and **1,696 standalone
tests / 255 classes**, with zero failures/errors/skips. Local API 29 passed
**222 cases / 32 classes**, including the connected workout → Summary →
History/detail journey. Both hosted jobs passed on that exact trunk commit.
Independent/adversarial packet reviews and the final independent integration
review passed; recoverable feature refs and the owned emulator were cleaned up.
The sealed receipt is `build/ux-context/runs/set-editor-identity-time/integration-complete-09ff23d5.json`.
No Debug drop or phone acceptance is claimed.

**History save recovery, 10 October UTC:** Save now retains a History editor
until acknowledgement; failed submissions keep their fixed identity and exact
values for Retry. Retry inspects first, and Cancel/Edit first settle an unknown
outcome. Task-restored pending submissions inspect without automatically writing.
The editor also keeps the original opening snapshot so a newer correction cannot
be overwritten by an older draft's first Save. This uses the existing saved-state
codec and save engine with a separate History owner; no schema or backup format
changes are planned. It is task restoration, not a crash journal.

The original `counter02` reproduced draft loss after a refused write. Actual
`targeted03` ran **13 cases / 2 fresh suites, 2 failures**: a stale open editor
overwrote a newer correction, and a selector failed. `targeted04` ran **79 cases /
4 fresh suites, 2 failures**: the zero-loaded-working-weight refusal was bypassed
by an unchanged-row acknowledgement, and the test's discover-before-absence
helper failed after a successful Retry. Those are preserved failures, not passes.
The repaired `targeted05` passed **80 cases / 4 fresh suites**. The subsequent
`targeted06` tested two review findings: Retry during a degraded display read,
and refusal of an unchanged invalid original through Retry/Edit. It ran **12
cases / 2 fresh suites, 1 failure**, from an incorrect spoken-label expectation
after the invalid submission was correctly retained and released. The narrow
fixture correction's `targeted07` passed the complete invalid-row native path,
including an intentional valid correction and exact stored/exported inventory.
Independent and adversarial source reviews accepted the recovery repairs.
[PR #469](https://github.com/sinura7/PersonalTrainer/pull/469) merged as
`7a83cb5f`, reviewed tree `7c41739f`. Both the premerge and actual clean-trunk
complete local gates passed **4,064 cases / 579 fresh suites**, alongside **1,696
standalone cases / 255 classes**. The clean-trunk Android run passed **222 cases /
32 classes**, including the connected workout, with synthetic settings, network
and packages restored. These are source/emulator results, not a phone update.

The closed hosted trunk run `38015519794` failed one rapid-correction test while
its emulator job passed. It remains archived as failed. A real Room counter with
the separate state recorder deliberately delayed reproduced the same nonempty
trace assertion: direct public-state observation had completed before trace
append. The bounded test-only repair acknowledges actual append before the
unchanged pending and late-result assertions. Its whole History period class
passed **23 cases / 1 fresh suite**; complete-gate and integration results remain
pending. The original failures are not relabelled by later successful checks.
Pure-hold Summary display, broader UX and phone acceptance remain separate work.

**Audience:** Temper's owner and the next designer, engineer, or agent continuing this work.

## Start here

The goal is a smoother complete workout: understand what to do, record it with little friction, rest, return after an interruption, and finish with confidence. Study the real workflow before deciding the next layout.

Read this brief, then [the workout map](WORKFLOWS.md), then [the screenshot catalogue and questions](EVIDENCE.md). The catalogue starts with five owner-supplied images. It can accept later batches without replacing this baseline.

The [second audit](AUDIT-2026-10-08.md) records the foundation's weaknesses, corrections, verification, and remaining evidence limits. Use [the first-study procedure](EVIDENCE.md#how-to-run-the-first-study) to begin design research without collecting every missing screen first.

[Q01: visible logging readiness](studies/q01/README.md) is the first worked study: native renders, connected Android captures, evidence-backed findings and interactive comparisons. The Debug-122 verification packet retained 3,571 passing JVM results and freshly passed all 211 Android cases. A later walkthrough of matching published Debug-124 source passes three guided journeys after exposing controls covered by pinned Tempo. Its bounded readiness, 72 dp target, adaptive entry and Why-action repairs freshly pass all 3,603 JVM tests and 212 Android cases. Original failures, review and verification limits are recorded in [the dated follow-up](studies/q01/debug124-followup.json). That stack is now integrated through Quiet #460; the dated records retain their original verification scope. Two connected workout proposals explore the owner's simpler direction; adding the requested matching exercise images passes 154 browser checks, including the original 136 workflow/reflow checks. The earlier 48 targeted checks and 29 comparison checks retain their own scope. O12 authorized the native Quiet implementation, now integrated through #460 and bounded follow-up #461; the earlier results retain their dated scope and do not establish phone acceptance.

This area brings existing decisions, actual code, phone observations, and unanswered questions together. It is a research layer under [ADR-001's authority order](../architecture/ADR-001-documentation-authority.md). It does not replace the [Foundation Program](../FOUNDATION_PROGRAM.md) or reopen signed product rules. O08 records the owner's explicit change to the implementation priority. A design change becomes an accepted decision only when the owner chooses it and the applicable decision record is updated.

The fresh Quiet candidate passes **3,625 local tests and 212 Android tests**.
[Its follow-up](studies/q01/quiet-native-followup.json) records identical source
inputs across both gates, retained failures, native captures and remaining limits.
Fresh independent and adversarial clean-context reviews subsequently passed;
PR #460 merged as `9b7a7f5a`, and both complete gates passed again on clean trunk.
The bounded workout-truth follow-up then integrated through PR #461; its exact
verification pins and remaining limits are recorded in [Q01](studies/q01/README.md#workout-follow-up-integration--9-october-2026-utc).
Home required-read recovery and week geometry are integrated. History's shared-period
work merged through [PR #465](https://github.com/sinura7/PersonalTrainer/pull/465)
as `7a41c977`, with the same tree as reviewed candidate `ec258beb`. Fresh
clean-trunk verification passed **3,894 app tests / 569 suites**, **1,675 standalone
tests / 254 classes**, the complete local build/lint gate, and **222 native tests /
32 classes** with 85 device-hashed captures. All 1,540 runtime inputs matched
across the gates. The [actual-trunk hosted run](https://github.com/sinura7/PersonalTrainer/actions/runs/37945856269)
also passed both jobs with the same app/native counts, independently checked.
The earlier branch push's native IME-observation timeout remains failed evidence
with unresolved cause; subsequent passes do not relabel it.
[UX15 records the exact scope and limits](../ux-program/UX-Acceptance-Matrix.md#ux15--make-historys-scope-and-deeper-sections-reachable).

The next bounded History packet addresses a confirmed travel visibility failure
before Summary: a completed activity captured on 9 October in Tokyo disappears
from All History when the later device date is 8 October in Honolulu, although
its saved graph and summary are unchanged. All must include known completed
captured records; known captured dates ahead of device today must remain
reachable, and a current period must include them when they belong to its civil
span. Empty future-only periods remain unavailable. This applies existing
ADR-011 captured-date attribution and ADR-026's coherent History range, without
rewriting dates or introducing new product policy. The repair passes its complete
local gate: 3,915 app tests / 572 suites and 1,682 standalone tests / 255
classes passed with unchanged source, plus the required build/lint tasks.
Connected verification on clean candidate `dd82321f` passed all **222 Android
tests / 32 classes**, with 85 device-hashed captures and verified fixture/settings
restoration. Fresh independent and adversarial reviews approve this bounded
repair with no new blocking findings. All 1,543 runtime inputs match the full
local gate and native execution; the later documentation closure changes only
these evidence records. Hosted checks and integration remain pending. The
native suite provides adjacent workout/History regression evidence; the new
travel interaction is real Repository/Room and mounted Compose JVM evidence.
Wider app UX phases and physical-phone acceptance are not complete.

**Travel integration closed, 9 October 2026 UTC:** [PR #466](https://github.com/sinura7/PersonalTrainer/pull/466)
merged as `9fd21456`, tree `42029232fbffdc8ecc78790752a36e33a4eadcc4`, identical
to reviewed `16521ef1`. Actual clean-trunk verification passed **3,915 app tests /
572 suites**, **1,682 standalone tests / 255 classes**, all four required local
tasks, and **222 native tests / 32 classes**. The fresh native execution retained
85 device-hashed captures, all 13 fixture restoration pairs and unchanged
runtime inputs. The preceding native attempt stopped at the cold-boot admission
guard on a Launcher ANR before install and ran zero tests; its failed evidence
remains separate. A fresh cold boot passed the identical guard and suite.
[Actual-trunk hosted verification](https://github.com/sinura7/PersonalTrainer/actions/runs/37960635611)
passed both jobs on that exact commit/tree, independently checked. A fresh
integration review verified source, raw results, captures, restoration and
emulator shutdown before feature-branch cleanup. This supersedes the pending
integration status in the earlier dated snapshot above.

**Current Summary/detail packet:** The same-session native receipt confirms
missing Summary artwork and unqualified detail targets beside different recorded
results. Apply O11's corresponding images, complete readable names and explicit
Planned / Recorded sets sections, including hold targets. Exact IDs and metadata
come from the same successful session read; custom and unknown lifts retain the
existing fallback artwork. Calculation, persistence formats and captured identities,
navigation and the current read-once Summary snapshot contract remain. The targeted
gate passed 51 checks
across eight fresh suites, including the 14-profile native-graphics matrix and
exact artwork/fallback case, with 400 fresh PNGs and unchanged 1,545 runtime
inputs. The first run's glyph-color assertion failure remains preserved; a
pixel probe confirmed the annotated unit used a different color, and the
corrected helper retains actual glyph/clip checks. Independent render inspection found
RTL operands reversed despite correct semantic text; a new native glyph-order
counter fails before the local direction fix. The first complete gate's one
old spoken-label expectation is retained, and the corrected connected JVM
journey passes with exact saved-set identity. Clean `c49b19ba` then passed the
fresh complete gate: **3,939 app tests / 573 suites**, **1,685 standalone tests /
255 classes**, all required build/lint tasks and unchanged 1,545 runtime inputs.
The 2,139 archived native-graphics PNGs include all 400 receipt-profile frames.
Fresh native execution passed **222 tests / 32 classes**, 85 device-hashed
captures and all 13 fixture restoration pairs, with matching runtime inputs.
The preceding startup attempt stopped on a SystemUI `BOOT_COMPLETED` ANR before
app install and ran zero tests; that failed evidence remains separate.

Final review withholds integration for reproduced restored-history defects.
Supported backup inputs can contain a saved set outside a partially populated
plan, or distinct prescription rows sharing an exercise ID. `counter04` executed
real BackupService preview/commit, Room reads and shipping Detail: four failures
in seven cases reproduced an unreachable original saved set and a duplicate-key
crash while scrolling. All postrestore inventories remained byte-identical
through teardown; the defects affect access, not stored record integrity.
The display repair groups exact exercise IDs, preserves each original numbered
prescription and exposes every saved SetLog once, including saved-only exercises.
Both restored graphs now pass independent 14-profile render cases in `targeted05`:
**142 cases / 11 fresh suites**, zero failures/errors/skips and unchanged 1,546
runtime inputs, with **685 archived PNGs**. Original setup/workflow failures remain
preserved; the counter now uses checked effort semantics, actual Hebrew resource
RTL and the shipping sheet's Expand action. Complete clean gates, fresh native
execution and nonauthor final reviews remain required before approval.
Clean `9d988658` then ran the unfiltered `full03` gate: **3,976 app tests /
574 fresh suites, one failure, zero errors/skips**; **1,694 standalone tests /
255 classes passed**. The History retry assertion captured an old failed state
after its trace boundary, although the separately awaited new request was loading.
This remains a failed gate. A deterministic delayed-recorder counter will establish
whether the cause is the test's observation boundary or application behavior before
changing either. The 1,546 inputs stayed unchanged and all 2,413 fresh PNGs are
archived. `counter06` stopped before tests on a new mixed argument call; the
identical fully named fixture keeps the checker ceiling unchanged. `counter07`
then executed **six cases / two suites, two failures**. Supported restore → Edit →
rep +1 / effort 8 → Save changed an original weighted timed hold from zero to one
rep and invented repetition volume. The untouched saved-only hold was explicitly
refused; planned-hold controls remained correct. The delayed recorder independently
appended its old failed state after the trace cut while the real new read remained
pending. The unchanged old assertion failed although every state after the recorded
new pending boundary passed. Both demonstrated causes are being repaired: preserve
the original saved timed type and bind retry assertions to acknowledged recorder
boundaries. All **32 counter PNGs** and full raw inventories/chronology are retained.
Native03 remains deferred until fresh complete verification closes the repairs.
The repaired `targeted06` pass executes **214 cases / 18 fresh suites**, zero
failures/errors/skips, all four Windows tasks and unchanged **1,548 inputs**.
All six full-restore/editor cases now require successful Save: zero-rep originals,
the accepted literal -3 timed representation, and a rep nudge with all effort
controls off preserve exact timed data. Seven Room correction controls retain
stopwatch strength, strict refusals, explicit duration correction and every
unrelated saved row. The original and delayed-recorder History tests pass without
changing production History. All **733 PNGs** and inventories/chronology are
archived. This filtered result precedes the clean complete gate, fresh native
execution, final reviews and integration; it does not close the editor refinement.

The dependent **Set Edit sheet identity and numeric controls** refinement remains
open under F9/W11/V12/Q08/UX29: a long identity truncates at font 2.0, the default
360 dp ±2.5 labels wrap, and hold-duration correction lacks its own control. RPE 10
reflow at 320 dp/font 2.0 needs a complete reachability check. Exact original-row
selection and prefill passed; these layout observations do not establish wrong
increments or permanently unreachable effort. A demonstrated unsafe hold write
would take priority in the current packet rather than wait for this refinement.
Hidden saved work takes priority over the separate Summary
hold-duration refinement. No broader UX29 or phone acceptance is claimed.
Returning from a historical correction to the
retained Summary still needs an actual AppNav counter and a snapshot-policy
decision before changing that deliberate contract.

**Complete packet verification and reviews closed, 9 October 2026 UTC:** Clean
`f2504264`, tree `20b6d0c04fa0c9635fa3f9cc8027ba55a857b451`, passed fresh `full04`:
**3,990 app tests / 576 fresh suites**, **1,694 standalone tests / 255 classes**,
zero failures/errors/skips, all four required Windows tasks with `--rerun-tasks`
and all 135 tasks executed. The archive preserves **2,461 fresh PNGs / 4,626 files**.
Fresh `native03` passed **222 Android tests / 32 classes**, with **85 device-hashed
captures**, all **13 fixture restoration pairs**, offline admission and verified
network/settings/package/collector restoration. The same-session AppNav images
show **85 kg × 3 / RPE 9**, **255 kg**, matching artwork and clearly qualified
**3 × 5 / 140 kg** planned targets. Actual emulator-process absence was verified
after shutdown. All **1,548 runtime inputs** match both closed gates and the actual
committed Git blobs; the sole established checkout conversion is `gradlew.bat` CRLF.
Fresh nonauthor independent and adversarial reviews approve the entire
`9fd21456` → `f2504264` packet with no unresolved critical/high finding.
[UX29 records the execution and review pins](../ux-program/UX-Acceptance-Matrix.md#ux29--make-activity-type-differences-explicit-in-details-and-receipts).
Earlier failed runs remain failed evidence. This closes candidate verification;
hosted verification, integration and fresh actual-trunk gates remain pending.
The named editor refinement, hold Summary presentation, correction-return policy,
broader app UX and physical-phone acceptance remain open. No Debug drop was requested.

## Owner decisions for this project

These decisions were made in the conversation on 8 October 2026.

| ID | Decision | Consequence |
|---|---|---|
| O01 | The five images are current Temper screens. | Treat them as evidence of the owner's current experience. Their installed build is still unknown. |
| O02 | Keep the documentation project inside the Temper repository. | Use this directory and the existing product/architecture references. |
| O03 | Workflows first, using the existing visual identity as the starting point. | Question layout, interaction, and friction without assuming a new brand or navigation system. |
| O04 | Study the smoother complete workout first. | Cover the session end to end before isolating a timer-only or logging-only redesign. |
| O05 | Gather context, refer to the supplied images, and work outward. | Inventory the whole app, but deepen the workout and its adjacent flows first. |
| O06 | Implement the proposed documentation plan. | Create the brief, workflow map, evidence catalogue, and continuation checklist. App changes remain a later packet. |
| O07 | Implement the complete-workout UX roadmap, then extend the process across the app. | Begin with a connected synthetic native baseline and trustworthy verification; retain successful behavior and change only supported problems. |
| O08 | Prioritize validated workout UX ahead of notification-settings expansion, twelve-week coaching and coach naming. | This is the owner's new priority decision; necessary data-safety and verification work stays first. Deferred feature decisions remain intact. |
| O09 | The owner reports currently using Temper Debug 124. | A later native walkthrough executes that published source with synthetic data. Preserve the earlier Debug 122 evidence separately; neither run retroactively identifies the five screenshots' capture build/settings. |
| O10 | The current workflow feels clunky; aim for one obvious action and a sophisticated, simple front end, with Wealthsimple/Web3 as visual references. | Reduce unnecessary taps, scrolling and decisions; study entry, coaching, saved work and navigation together. Preserve recording contracts and Instrument as the starting point. This is a direction, not acceptance of a specific layout. |
| O11 | Show the corresponding exercise image beside its name. | Both connected proposals use the existing catalog illustrations in exercise identities and lists. Keep the complete name readable, match the actual exercise on rest/correction/review surfaces, and preserve recording controls. This requests thumbnails; it does not select the whole native composition. |
| O12 | The updated proposals look good; proceed with coding and development through completion, without asking the owner to test during development. | Implement the native Quiet workout direction as one packet on the reviewed Debug-124 stack, amend ADR-027's composition, and execute synthetic regression, adaptation and connected-workflow checks. Preserve existing policy and data; reconcile the overlapping stack before integration. Report remaining physical-phone evidence separately. |

Quiet is the authorized native implementation direction. The owner's visual approval and development directive do not establish complete-workout usability or phone acceptance. Development does not wait for an owner testing session.

## Baseline and evidence limits

| Layer | Recorded baseline | What it establishes |
|---|---|---|
| GitHub/source snapshot | [Commit eae6517845ee560ceb9695bf2f92788e2b4339cc](https://github.com/sinura7/PersonalTrainer/commit/eae6517845ee560ceb9695bf2f92788e2b4339cc), 5 October 2026; Debug 122 in [Gradle](../../app/build.gradle.kts) | The source version used for code-supported workflow statements. |
| Published Debug distribution | [Live 124](https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-10-06-1), published 6 October; tag points to `a1f47d1a0d41d1e0264d4457407391cf7d4af92e` on open PR #457 | Release metadata and tag were verified through GitHub. The owner subsequently reports currently using Debug 124 (O09). Publication does not mean the PR is merged; physical-device metadata remains uninspected. |
| Original local checkout | Branch `codex/home-day-design`, commit `86311fbda00940d4d7e78fbff54b498edfddc989`, 17 September 2026 | An older checkout with unfinished Home source, golden, and evidence edits. It is not the inspected current trunk. |
| Documentation worktree | Branch `codex/ux-context-foundation`, created from the pinned source snapshot | Isolates this packet from the original checkout's work. |
| Phone screenshots | S01-S05, supplied in this conversation; owner says current Temper | Visible appearance and displayed state. Variant, installed version, device/API, font scale, display scale, and preceding actions are unknown. |
| Local tools | Initial sandbox startup failed with `setup refresh had errors`; approved external commands worked, and the session later resumed with unrestricted local access | Local source inspection, documentation writes, and verification can now proceed. The initial host failure says nothing about app correctness. |
| Runtime UX | Connected synthetic screen-host and real-AppNav walkthroughs passed under O07; later AppNav baseline uses matching published Debug 124 source. PackageManager identifies each installed version; API 29, about 411 dp wide and font 1.0 | Exact trace, failed attempts, settings, fixtures and limits are in Q01. Guided automation cannot establish owner comprehension, touch feel or phone acceptance. |

Relative repository links refer to this worktree's files. The commit above pins the research baseline; when extending this pack after a code change, record the new revision and recheck affected claims. File modification dates and a document's word “current” are insufficient freshness evidence.

## Product and experience context

Temper is an Android strength-and-cardio tracker built for the owner's training. Its local core works offline without an account. Data stays useful and recoverable; the owner should be able to complete a session without understanding the implementation.

The immediate usage context is a workout with short glances, repeated entries, interruptions, and an often occupied or tired hand. Those conditions guide research tasks; they have not been measured as usability findings. Do not invent broader customer personas, commercial targets, or preferences for the owner.

Success in this phase means we can explain each screen's job, what each tap changes, what has actually saved, and how the owner returns after leaving. Later design success will be assessed through complete tasks, owner feedback, error recovery, and accessible operation, rather than a normal-state screenshot alone.

### Established boundaries and sources

| Area | Established direction | Source |
|---|---|---|
| Platform and local core | Android/Jetpack Compose; core recording, planning, history, and recovery remain usable offline; one live activity at a time. | [ADR-003](../architecture/ADR-003-shipping-platform.md), [ADR-004](../architecture/ADR-004-offline-core-and-entitlements.md), [ADR-007](../architecture/ADR-007-activity-model.md) |
| Visual identity | Instrument: dark semantic surfaces, tabular numerals, one dominant filled Volt action; legible non-color state channels and reduced-motion support. | [ADR-005](../architecture/ADR-005-instrument-identity.md), [ADR-023](../architecture/ADR-023-palette-and-reduced-motion.md) |
| Navigation | Home · Body · Plan · History · Settings. Library and workout/detail/editor surfaces are pushed routes. The live bar provides session return. | [ADR-006](../architecture/ADR-006-information-architecture.md), [ADR-014](../architecture/ADR-014-settings-tab.md), [ADR-016](../architecture/ADR-016-settings-home-trim.md) |
| Start versus planning | Home starts; planned rows confirm. The Home start sheet offers free workout, routine, cardio, and Extra. Plan authors recurrence. | [ADR-017](../architecture/ADR-017-home-week-board.md), [ADR-018](../architecture/ADR-018-home-start-confirm.md), [ADR-021](../architecture/ADR-021-home-start-and-day-add.md) |
| Workout completion | Explicit Next exercise after prescribed work, then Finish workout. No automatic advancement; extra sets are explicit. | [ADR-026](../architecture/ADR-026-frontend-redesign.md), [WorkoutAdvance](../../app/src/main/java/com/sinura/personaltrainer/domain/WorkoutAdvance.kt) |
| Set entry and feedback | Separate editable values, suggestions, and saved rows. Apply fills a draft. Important numbers stay readable; selected effort differs from suggested effort. | [ADR-027](../architecture/ADR-027-workout-logging-redesign.md), [ADR-030](../architecture/ADR-030-live-workout-set-copy-prepare-stats.md) |
| Effort | Effort is required for working repetition sets; warm-ups and holds are exempt. Saving clears effort for the next set. | [ADR-026 amendment](../architecture/ADR-026-frontend-redesign.md), [ActiveWorkoutUiState and save handling](../../app/src/main/java/com/sinura/personaltrainer/ui/workout/ActiveWorkoutViewModel.kt) |
| Coaching | Local, deterministic prescriptions with an explainable rule/evidence path. Presentation work must not invent training advice. | [ADR-008](../architecture/ADR-008-deterministic-rules.md), [ADR-029](../architecture/ADR-029-coach-engine.md) |
| Rest and return | One timer state across app and exterior surfaces; current exterior overlay is explicitly supported. Timing guarantees must reflect permissions and platform outcomes. | [ADR-012](../architecture/ADR-012-rest-and-reminders.md) and its later amendments |
| Touch and adaptation | At least 48 dp interactive targets, workout commit at least 72 dp; required values reflow rather than disappear. Native evidence includes font, window, orientation, and reachability. | [Frontend shared contracts](../FRONTEND_REDESIGN.md), [ADR-027](../architecture/ADR-027-workout-logging-redesign.md), [ADR-032](../architecture/ADR-032-jvm-evidence-lanes.md) |
| Recovery and distribution | Preserve history, IDs, units, dates, schema and backup formats. Obtainium Temper Debug and everyday Temper are separate installations; stable signer and increasing distribution codes. | [Foundation Program](../FOUNDATION_PROGRAM.md), [SETUP](../../SETUP.md), [AGENTS](../../AGENTS.md) |

Accepted boundaries are inputs to this study. If a promising design needs to change one, record the proposed amendment and owner decision explicitly.

## Source map: what to reuse

| Source | Use in this project | Freshness treatment |
|---|---|---|
| [Foundation Program](../FOUNDATION_PROGRAM.md) and [architecture index](../architecture/README.md) | Product promises, authority, execution and acceptance rules | Read applicable ADR amendments alongside program summaries. |
| [Frontend redesign](../FRONTEND_REDESIGN.md) | Shared components, current programme, named packets and milestones | Its progress table has not caught up with every later commit; do not infer open work solely from Pending. |
| [29 September owner plan](../owner-eight-plan-2026-09-29.md) | Owner requests and rationale: borders, effort, hold lead-in, permissions, exterior rest, coaching history and identity | Separate original requests from dated completion notes; recheck remaining gaps. |
| [25 September audit](../design-audit/2026-09-25/AUDIT.md) | Existing findings, reasoning and evidence | A dated audit baseline, not a present defect list. |
| [UX backlog](../ux-program/UX-Backlog.json), [acceptance matrix](../ux-program/UX-Acceptance-Matrix.md), [decision log](../ux-program/UX-Decision-Log.md) | Stable UX IDs, task criteria, fixtures and rationale | September snapshots; statuses and “not executed” entries require revalidation. |
| [Workout experience report](../workout-entry-experience-report.md) and [implementation plan](../workout-entry-implementation-plan.md) | Historical workflow reasoning and crosswalks | Live 65-era evidence, preceding the current hero-numeral screen. |
| [Timer plan](../timer-redesign-plan.md) | Historical timer rationale | Explicitly archived; follow accepted amendments and current source. |
| [Development runbook](../DEVELOPMENT.md) | Actual verification commands and Windows qualifications | Report executed checks; do not copy historical host limitations as fresh failures. |

### Known conflicts and their disposition

| ID | Conflict or missing match | Treatment |
|---|---|---|
| C01 | HANDOFF-NEXT and frontend progress notes stop around Debug 110/P4a pending, while the inspected source commit ships P4a/Debug 122. | Pin current facts to source/commit. Historical packet statuses are not a new task queue. |
| C02 | Older optional-effort wording survives in UX documents; the September 29 amendment and code require working-set effort. | Use the later decision and code. Preserve the distinction between suggested and selected effort. |
| C03 | Older blanket “no overlay clock” text coexists with ADR-012's explicitly supported exterior overlay. | Follow the later exterior presentation rules; do not remove a supported feature based on older prose. |
| C04 | ADR-027's cyan timing convention and the Volt exterior ring differ. | Record a semantic-color design question, not an automatic bug or a selected fix. |
| C05 | ADR-027's original density pass recorded 840 dp; CoachEngine #370 changed the test to 868 dp and P1 #444 lowered it to 852 dp. | Provenance reconciled in ADR-027 on 8 October. Keep the current 852 dp ratchet; do not raise it to fit a proposal. |
| C06 | S05 contains Tempo/mascot/dismiss UI; the original pinned trunk Debug 122 rendered the ordinary Next set card. Published Debug 124 supplied the dependency. | The dependency is now included by reviewed, merged Quiet [PR #460](https://github.com/sinura7/PersonalTrainer/pull/460); PR #457 was closed after tip integration. Clean-trunk full local/native gates pass. Exact owner screenshot/device metadata and phone acceptance remain unknown. |
| C07 | Old emulator-golden instructions coexist with ADR-032's retirement of those goldens. | Use the current JVM Compose render/reachability lane; retain physical/platform checks where required. |
| C08 | The original local branch has older Home implementation and pending evidence edits. | Leave that packet intact. Do not label its goldens or source as current trunk evidence. |

C01-C08 are documentation/provenance issues, not additions to the app defect backlog.

## How design decisions will be made

For each proposal, document: the user's job, observed friction, evidence and existing finding IDs, alternatives, owner choice, affected contract, acceptance task, and outcome. Use statuses **Question → Proposed → Owner accepted → Implemented → Validated**. A source review can establish implementation; it cannot establish usability acceptance.

[The question register](EVIDENCE.md#question-register) ranks the first investigations. Priority reflects plausible impact on a repeated task, not measured frequency or a decision to ship. Preserve existing backlog IDs and packet order until the owner explicitly changes them.

For a comparison, change one main workflow choice at a time. Evaluate the complete action and recovery sequence, retaining the strengths of the current screen. If a later owner study is undertaken, record the first interpretation, prior exposure and any assistance using [the first-study procedure](EVIDENCE.md#how-to-run-the-first-study). Under O12 the agent proceeds with development and synthetic verification without requiring that study first. Preference is useful evidence, but it does not establish correct saving or recovery. A visual prototype is discussion evidence; native adaptation and phone behaviour are separate checks.

## Execution order

1. **Retain the established baseline.** Q01 records connected synthetic Debug-122 and Debug-124 sessions and the repaired verification gate. Preserve their exact revisions, fixtures, failures and results; do not report them as checks of new Quiet code. Separate screen-host, Android-navigation and phone evidence.
2. **Workout packets integrated through #461.** Matching exercise artwork and complete names, grouped values/effort/readiness and explicit primary action are integrated under [ADR-027's amendment](../architecture/ADR-027-workout-logging-redesign.md#amendment--8-october-2026-native-quiet-workout-composition). The bounded UX23 follow-up has completed reviews and clean-trunk local/native verification. Continue Home and History development as requested; keep the broader W3/TS-3, N2 and physical-phone criteria explicit rather than treating these packet passes as complete app acceptance. Use the existing Q/UX/V records; data loss or wrong writes take priority.
3. **Verify and review without an owner development gate.** Exercise Start → actual values → effort → Log → rest/return → switching → correction → Finish → Summary/History (W01-W11), including failed writes and exact Retry. Preserve manual values, required working-set effort, Apply-as-draft, explicit progression and logging during rest. Run regression coverage, the complete local gate, applicable native renders/reachability, independent and adversarial review. Keep the 72 dp primary, 48 dp targets and 852 dp density ratchet. Report executed evidence and any remaining limits; do not ask the owner to supply development testing.
4. **Integrate each reviewed packet.** Quiet #460 and the workout-truth follow-up #461 are reviewed, merged and reverified on clean `trunk`. Apply the same review and affected-journey gate to each subsequent packet. Obtainium drops follow the existing signer/version flow when requested. Development completion, distribution and physical-phone acceptance remain separate; owner confidence is not inferred from synthetic checks.
5. **Extend the same process across the app.** Follow the order below through the corresponding reconciled frontend/product packets. Verify current behavior before changing it; an area that already works does not require a redesign. Keep necessary technical dependencies. Recovery or data-loss findings move forward immediately.

| Next area | Journey to establish |
|---|---|
| Home and return | Selected day versus today; planned, rest, empty and completed states; start confirmation and the live-session return. |
| History, Summary and details | #465's shared-period packet is integrated. First verify the F6a/UX15/Q08 captured-date travel visibility repair; then continue Summary/detail clarity, units/dates and safe correction. |
| Plan and routines | Create, edit, reorder, schedule, import, cancel and failed-save recovery. |
| Library and selection | Search, combined filters, selection context, custom exercises and edit/cancel. |
| Body | Measurements/recovery; accurate selectable anatomy with equivalent list access. |
| Cardio and mixed days | Live/backdated entry, activity-specific fields, correction and one live activity. |
| Onboarding | Guided/custom setup, meaningful choices, back navigation, interruption and permission timing. |
| Settings and recovery | Notifications/status, sound/haptics, permissions, backup preview/restore and truthful account language. |

Each area ends with a current journey, supported changes where needed, an implemented packet and recorded acceptance. Use the roadmap's complete recording/continuity/layout/accessibility/recovery gates and existing physical performance targets; browser timings cannot establish phone improvements. Debug distribution and a public release have separate gates. Cloud-sync activation, new coaching algorithms, coach naming, iOS and commercialization remain outside this UX authority.

No new plugin is required for this foundation. Q01 demonstrates a focused interactive comparison alongside source and native evidence; additional concepts should follow equally specific workflow questions.
