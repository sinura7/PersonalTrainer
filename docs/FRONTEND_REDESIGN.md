# Temper frontend redesign

Status: implementation in progress. Owner approved the comprehensive plan on
16 September 2026. Baseline: `20789157` (Temper Debug 78).
Authority: [ADR-026](architecture/ADR-026-frontend-redesign.md), within the
[Foundation Program](FOUNDATION_PROGRAM.md). Evidence starts with the
[visual audit](design-audit/2026-09-16/AUDIT.md).

## Outcome and fixed decisions

Rebuild complete native Compose journeys within Instrument: dark semantic
surfaces, Inter/Space Grotesk, tabular numerals, one dominant Volt action,
cyan timing, accessible non-color state channels. Substantial layout changes
are approved. A beautiful normal state alone does not close a screen.

- History's period controls totals, calendar, progress and sessions together.
- After planned working sets, the primary is Next exercise (next unfinished,
  wrapping to skipped exercises), then Finish workout. Extra sets are explicit.
- Workout first, then propagate the same component and interaction standard.
- Preserve canonical IDs, stored measurements, captured dates, timer identity,
  one live activity, offline behavior, schemas, backups and distribution signer.
- Home keeps the current ADR-021 start sheet. The plan's wording about
  preserving a Home add/recurrence choice predates ADR-021's 13 September
  amendment: no obsolete Add control is restored. Plan remains the recurring
  author. Planned Home rows still confirm before starting.

## Implementation packets

Only one implementation packet is open at a time. Each has targeted checks,
the complete local gate, native evidence where relevant, independent review,
an adversarial review, and integrated verification. A packet is not complete
because source assertions or JVM tests alone pass.

| Packet | Deliverable | Status |
|---|---|---|
| F0 | Reproducible emulator, fixture baseline, decision record and verification lane | Complete — PR #348; integrated gate passed |
| F1 | Typed controls, component gallery, headers, navigation, insets and live bar | Complete — PR #349; integrated unit/native gates passed |
| F2 | Compact workout identity, entry, warm-ups/RPE, latest sets and dock geometry | Complete — PR #350; integrated unit/native gates passed |
| F3 | Primary-action state, completion, timing, switcher, retry, undo and resume | Implementation complete — PR #351; integrated checks passed; Milestone A phone acceptance pending |
| F4 | Truthful Home dates, day picker, planned/completed/live/empty states | Pending |
| F5 | Registered Body heat geometry, viewport, selection and list equivalence | Pending |
| F6 | Unified History periods, calendar, readable duration and lifetime views | Pending |
| F7 | Plan/routines, ordering, editing, import and failed-save recovery | Pending |
| F8 | Library/pickers, filters, custom exercises, onboarding and plan preview | Pending |
| F9 | Live/past cardio, summary, session/exercise details and correction | Pending |
| F10 | Settings labels/status, backup/restore, diagnostics and About | Pending |
| F11 | Integrated visual, accessibility, performance and upgrade acceptance | Pending |

Milestone A follows F3; B follows F6; C follows F11. Each has native captures,
executed checks, limitations, a focused phone checklist and an Obtainium drop
through the existing stable-signing and monotonically increasing version flow.

## Shared contracts

- Existing 4 dp grid, 16 dp gutters/panel padding, role-based spacing and
  8/12/16/24 dp corners; no raw UI token escapes.
- At least 48 x 48 dp interactive targets; workout commit minimum 72 dp.
  Required text and entered/committed values wrap or reflow rather than clip.
- Distinct single-choice/radio groups, independent toggles, action presets,
  suggestions and noninteractive statuses. Suggested is never Selected.
- One dominant filled action per active screen/modal; secondary acts quiet.
- Consistent headers, accessible back controls, one foreground overlay,
  truthful disabled/saving/error states and retry owned by the failed action.
- Keep all five navigation destinations, names, order and state restoration.
  If labels cannot fit, reflow 3 + 2. The whole live-bar main region resumes;
  its overflow remains independent. Measure actual system/live/nav/IME insets.
- Keep established 90/150/240 ms motion roles. Reduced motion snaps animation,
  but does not shorten status/undo reading time. Saved haptics follow writes.
- Component gallery covers state and role variants, not just enabled buttons.

## Workout contracts

Order: session header; compact approximately 64 dp exercise identity with
Switch and overflow; set ordinal and Working/Warm-up; stacked load and
reps/duration; optional RPE/help; latest saved set/View sets; anchored action.
Keep session totals out of exercise-specific telemetry. Preserve load meanings
(lifted, added, assistance, bodyweight, hold) and existing numeric validation.

Tap-to-type is visibly available; entry confirms an absolute value and Cancel
preserves the draft. Large steppers and long press remain. Presets say Use
<weight> with the percentage secondary, set a warm-up draft and never log.
Warm-ups do not count as working sets. New saves clear RPE and return from
warm-up to Working with visible context. Optional RPE remains removable and
its help is always available.

Primary action is derived from existing durable/session/draft state: Add
exercise, Log set, Log warm-up, Saving, Retry save, Save changes, Start hold,
Log hold, Next exercise, Finish workout, or read-error recovery. Editing and
explicit extra-set/warm-up intent take precedence over ordinary completion.
Free lifts stay loggable. Duplicate taps cannot save twice or become Next
because the label changed. Reopen/edit/delete/undo recompute completion.

Dock: one minimum-56 dp companion row plus minimum-72 dp primary, no empty
reserved context rail. Error/undo takes companion priority while an active
timer stays reachable through a compact clock. Success updates latest-set
content and accessibility, not another tall panel. Receipt expiry does not
move the primary's bottom edge. Large text may grow controls.

Ordinary logging retains entry position, not bring-into-view on growing
history. View sets opens full labeled history with edit/delete. Edits reveal
their fields, removal/undo visibly updates progress, switches intentionally
reset entry. Switcher shows identity, progress, current/completed state,
secondary rest and Add exercise; preserve interrupt-timing confirmation.

Timing: explicit Rest/Hold/Set time, Start rest versus Time set, consistent
-15/+15/Skip. Planned rest replaces Start at. Closing rest does not stop it;
Stop stopwatch does not log; hold target does not auto-save. Keep persistence,
elapsed-time deadlines, notification honesty and generation safety. Expanded
rest prioritizes readable controls over the decorative ring on constrained
layouts, places actions within reach and has a clear completion state.

## Main screens and remaining journeys

- Home: selected-date-aware headline, distinguish today/selection/status,
  Today return, scroll days when seven 48 dp targets cannot fit. Collapse
  completed workouts with access to actual recorded details; remaining
  planned work and the live-session route remain clear.
- Body: retain anatomical family, register base/masks/selection/hit geometry
  to one coordinate transform, clip heat to muscle boundaries, align front
  and back. Figure uses measured content height; readable rows remain useful.
  Wrapping legend, completed-set load wording, equivalent list interactions.
- History: current Month default; saved anchor + Day/Week/Month/Year/All;
  previous/next/current with no future-only periods. Shared half-open range
  respects captured date/week start. Day/week strips, month grid, year-month
  cells; All grouped chronology. Calendar day selects Day (no hidden filter).
  Stale requests never display old totals under a new period. Empty-range
  recovery, readable h/min, clear records and blocks in lifetime destinations.
- Plan: distinguish schedule from templates; explicit recurrence, Add session
  primary, proposal acceptance, missed-work options, accessible ordering,
  visible routine menus, reminders and stored time behavior preserved.
- Editors: identity/order/targets/notes/save hierarchy, field-owned errors,
  truthful partial saves, recovery, import mismatch/duplicate handling.
- Library/pickers: separate muscle/equipment filters, selected state/reset,
  distinct no-results/empty-library, consistent thumbnail frames/badges,
  query/scroll restoration, explicit selection mode and custom-edit actions.
- Cardio/composer: readable elapsed/type/distance/date/zone/units, validation,
  Finish versus leave-running/discard and interruption recovery.
- Summary/details: saved truth, accurate totals/records, clear Done and detail
  access, missing/read-error cases and correction journeys.
- Onboarding: retained answers, step progress, optional labels and full plan
  preview before writing. Settings: coherent rows and live summaries, Plan
  preferences/setup, 12-hour/24-hour, discoverable version/build/update.
- Backup: actual status and timestamps, export/incoming preview/restore,
  operation progress, failure and reconciliation with fixture data only.

## Acceptance matrix and evidence

Native layouts: 360x640, 360x800, 412x840, 600 dp width and 640x360 landscape.
Primary changed screens at font 1.0/1.6/2.0; gesture/three-button navigation,
RTL, normal/reduced motion, keyboards, long labels/numbers, sparse/populated/
large data, rotation/background/process recreation. API 29 reference renderer,
API 36 platform behavior, API 26 compatibility smoke; owner's phone for touch,
TalkBack, haptics, notifications, upgrade and measured performance.

Critical scenarios: eight successive logs; slow/double/failed writes; applying
without logging; warm-up then work; RPE clear; edit/delete/undo at completion;
extra/skipped/final/free exercises; all load types; timing switch/leave/resume;
denied notification; rapid History range changes and edits, leap/year/week/
timezone/backdate boundaries; empty range/All/lifetime records; routine
create/order/save failure/import; combined search filters; custom edit/cancel;
onboarding back/accept; invalid/valid cardio; missing detail; fixture export,
preview, restore and count reconciliation.

Golden evidence pins profile/API/image revision/renderer/fonts/locale/clock/
fixture state. Full screen includes persistent chrome. Required missing
baselines fail rather than skip. Keep narrowly bounded rounding allowance;
attach actual/expected/diff and rationale for intentional baseline updates.
Coordinate and interaction assertions complement screenshots.

Accessibility: TalkBack complete journeys, proper roles/groups, no repeated
decorative descriptions or tick announcements, focus restoration, accessible
map/menu alternatives, measured contrast, no clipped essential values and
accessibility-adjusted undo timeouts.

Physical performance targets (release-equivalent, documented warmed device):
entry response p95 <=100 ms, normal durable set acknowledgement p95 <=300 ms,
95% frames within the device refresh budget; investigate/fix reproducible
app-caused >100 ms input stalls. No timer-driven full-screen recomposition,
per-tick decoding or growing memory across repeated loops. Emulator results
are not physical performance evidence.

No packet/screen is complete without relevant states, native review,
functional checks, accessibility and performance evidence, audit dispositions
and zero unresolved critical/high findings. Named dependent packets own any
lower-severity deferral. Final upgrade preserves user data and settings;
rollback is a compatible forward-version release.

## Open acceptance inputs

- Screenshot source build is unknown; runtime reproduction resolves uncertain
  screenshot-only findings before they are treated as defects.
- Owner phone model, Android version and display settings are not yet recorded.
- Physical TalkBack, haptics, Doze, upgrade and performance evidence cannot be
  substituted with an emulator or a browser concept.

## Execution log

- 2026-09-16: created `codex/frontend-baseline`, preserved existing Windows
  setup/audit changes; confirmed WHPX acceleration. No system images/AVDs or
  SDK command-line tools were installed. F0 provisions repository-owned AVDs.
