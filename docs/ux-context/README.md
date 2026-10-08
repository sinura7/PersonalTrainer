# Temper UX foundation

**Created:** 8 October 2026

**Status:** Owner-approved complete-workout roadmap in execution; design alternatives still open.

**Audience:** Temper's owner and the next designer, engineer, or agent continuing this work.

## Start here

The goal is a smoother complete workout: understand what to do, record it with little friction, rest, return after an interruption, and finish with confidence. Study the real workflow before deciding the next layout.

Read this brief, then [the workout map](WORKFLOWS.md), then [the screenshot catalogue and questions](EVIDENCE.md). The catalogue starts with five owner-supplied images. It can accept later batches without replacing this baseline.

The [second audit](AUDIT-2026-10-08.md) records the foundation's weaknesses, corrections, verification, and remaining evidence limits. Use [the first-study procedure](EVIDENCE.md#how-to-run-the-first-study) to begin design research without collecting every missing screen first.

[Q01: visible logging readiness](studies/q01/README.md) is the first worked study: native renders, connected Android captures, evidence-backed findings and interactive comparisons. The Debug-122 verification packet retained 3,571 passing JVM results and freshly passed all 211 Android cases. A later walkthrough of matching published Debug-124 source passes three guided journeys after exposing controls covered by pinned Tempo. Its bounded readiness, 72 dp target, adaptive entry and Why-action repairs freshly pass all 3,603 JVM tests and 212 Android cases. Original failures, review and verification limits are recorded in [the dated follow-up](studies/q01/debug124-followup.json). Integration awaits resolution of the underlying open Tempo/coaching stack. Two connected workout proposals explore the owner's simpler direction and pass 136 browser checks. The earlier 48 targeted checks and 29 comparison checks retain their own scope. Owner observation and selection remain pending.

This area brings existing decisions, actual code, phone observations, and unanswered questions together. It is a research layer under [ADR-001's authority order](../architecture/ADR-001-documentation-authority.md). It does not replace the [Foundation Program](../FOUNDATION_PROGRAM.md) or reopen signed product rules. O08 records the owner's explicit change to the implementation priority. A design change becomes an accepted decision only when the owner chooses it and the applicable decision record is updated.

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

No redesign alternative has been selected yet. No claim of phone acceptance follows from these decisions.

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
| C06 | S05 contains Tempo/mascot/dismiss UI; pinned trunk Debug 122 renders the ordinary Next set card. [Open PR #457](https://github.com/sinura7/PersonalTrainer/pull/457) supplied the published Live 123/124 Tempo UI. | The published Debug 124 source now passes three guided native journeys after explicit swipes past the pinned card. Source and installed-version identity are verified on the emulator. Exact screenshot transition/device metadata and phone acceptance remain unknown; the PR remains open. See Q01's dated follow-up before changing overlapping UI. |
| C07 | Old emulator-golden instructions coexist with ADR-032's retirement of those goldens. | Use the current JVM Compose render/reachability lane; retain physical/platform checks where required. |
| C08 | The original local branch has older Home implementation and pending evidence edits. | Leave that packet intact. Do not label its goldens or source as current trunk evidence. |

C01-C08 are documentation/provenance issues, not additions to the app defect backlog.

## How design decisions will be made

For each proposal, document: the user's job, observed friction, evidence and existing finding IDs, alternatives, owner choice, affected contract, acceptance task, and outcome. Use statuses **Question → Proposed → Owner accepted → Implemented → Validated**. A source review can establish implementation; it cannot establish usability acceptance.

[The question register](EVIDENCE.md#question-register) ranks the first investigations. Priority reflects plausible impact on a repeated task, not measured frequency or a decision to ship. Preserve existing backlog IDs and packet order until the owner explicitly changes them.

For a comparison, change one main workflow choice at a time. Evaluate the complete action and recovery sequence, retaining the strengths of the current screen. Record the owner's first interpretation and unaided actions before teaching the controls. Select the relevant measures and decision rule in [the first-study procedure](EVIDENCE.md#how-to-run-the-first-study) before comparing alternatives. Preference is useful evidence, but it does not establish correct saving or recovery. A visual prototype is discussion evidence; native adaptation and phone behaviour are separate checks.

## Execution order

1. **Connect the baseline and repair verification.** Follow one synthetic session through rendered Start, different actual values, effort, Log, rest, switching, correction, Finish, Summary and History (W01-W11). Assert the same session and saved row throughout. Pin source, installed variant and capture settings. Repair the four migration-copy failures and standalone preflight, retain the initial failures, and run the complete gate. Separate screen-host, Android-navigation and phone evidence.
2. **Close Q01 with the owner.** Use [the prepared comparison and task](studies/q01/README.md); record understanding, assistance, wrong interpretations, unnecessary navigation and what saved. Record prior exposure as a learning effect. C06 still matters before attributing the pictured Tempo behavior to trunk. Retain the present design, revise the candidate or advance it based on evidence. No observed meaningful problem is a valid result.
3. **Choose the connected workout direction.** Study entry, coaching, saved work, rest/return, exercise progression and completion together. Include saving, failure and recovery states. For each justified proposal record the problem, alternative, tradeoff, affected product rule and acceptance task using the existing Q/UX/V records. Data loss or wrong writes take priority. Do not create a second backlog.
4. **Implement and accept one packet at a time.** Start with logging readiness only if its comparison supports it; otherwise use the highest supported finding. Preserve manual values, required working-set effort, Apply-as-draft, explicit progression and logging during rest. Run regression coverage, the complete local gate, applicable native renders, independent and adversarial review; integrate into `trunk` and rerun the affected connected journey. Obtainium drops follow the existing signer/version flow when requested. The workout milestone needs both correct records/recovery and owner confidence recording, correcting, returning and finishing.
5. **Extend the same process across the app.** Follow the order below through the corresponding reconciled frontend/product packets. Verify current behavior before changing it; an area that already works does not require a redesign. Keep necessary technical dependencies. Recovery or data-loss findings move forward immediately.

| Next area | Journey to establish |
|---|---|
| Home and return | Selected day versus today; planned, rest, empty and completed states; start confirmation and the live-session return. |
| History, Summary and details | Consistent totals/periods; locating saved sessions; units/dates and safe correction. |
| Plan and routines | Create, edit, reorder, schedule, import, cancel and failed-save recovery. |
| Library and selection | Search, combined filters, selection context, custom exercises and edit/cancel. |
| Body | Measurements/recovery; accurate selectable anatomy with equivalent list access. |
| Cardio and mixed days | Live/backdated entry, activity-specific fields, correction and one live activity. |
| Onboarding | Guided/custom setup, meaningful choices, back navigation, interruption and permission timing. |
| Settings and recovery | Notifications/status, sound/haptics, permissions, backup preview/restore and truthful account language. |

Each area ends with a current journey, supported changes where needed, an implemented packet and recorded acceptance. Use the roadmap's complete recording/continuity/layout/accessibility/recovery gates and existing physical performance targets; browser timings cannot establish phone improvements. Debug distribution and a public release have separate gates. Cloud-sync activation, new coaching algorithms, coach naming, iOS and commercialization remain outside this UX authority.

No new plugin is required for this foundation. Q01 demonstrates a focused interactive comparison alongside source and native evidence; additional concepts should follow equally specific workflow questions.
