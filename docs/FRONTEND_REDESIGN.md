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
because source assertions or JVM tests alone pass. *Since 23 September the
JVM render set, with its reachability assertions, is the native visual
evidence ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)); source
assertions alone still do not complete a packet.*

| Packet | Deliverable | Status |
|---|---|---|
| F0 | Reproducible emulator, fixture baseline, decision record and verification lane | Complete — PR #348; integrated gate passed |
| F1 | Typed controls, component gallery, headers, navigation, insets and live bar | Complete — PR #349; integrated unit/native gates passed |
| F2 | Compact workout identity, entry, warm-ups/RPE, latest sets and dock geometry | Complete — PR #350; integrated unit/native gates passed |
| F3 | Primary-action state, completion, timing, switcher, retry, undo and resume | Implementation complete — PR #351; integrated checks passed; Milestone A phone acceptance pending |
| F3.1 | Workout logging screen redesign to the owner's reference ([ADR-027](architecture/ADR-027-workout-logging-redesign.md)): header progress, image-led identity, stats row, hero numerals, RPE track, next-set card, set-history chips, rest card, two-line commit | Implementation complete on `claude/workout-logging-redesign-77hml8`; JVM gate and JVM renders; floor goldens retired ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)) and removed in X3; Milestone A phone acceptance pending |
| F4 | Truthful Home dates, day picker, planned/completed/live/empty states | Pending |
| F5 | Registered Body heat geometry, viewport, selection and list equivalence | Pending |
| F6 | Unified History periods, calendar, readable duration and lifetime views | Pending |
| F7 | Plan/routines, ordering, editing, import and failed-save recovery | Pending |
| F8 | Library/pickers, filters, custom exercises, onboarding and plan preview | Pending |
| F9 | Live/past cardio, summary, session/exercise details and correction | Pending |
| F10 | Settings labels/status, backup/restore, diagnostics and About | Pending |
| F11 | Integrated visual, accessibility, performance and upgrade acceptance | Pending |

Milestone A follows F3 (since the audit, W3); B follows F6 (F6b); C follows F11. Each has native captures,
executed checks, limitations, a focused phone checklist and an Obtainium drop
through the existing stable-signing and monotonically increasing version flow.

### Order since the whole-app audit (22 September 2026)

The [whole-app audit](design-audit/2026-09-22/AUDIT.md) re-scoped F4–F11 into
smaller packets and interleaved them with sync-safety (S), workout-floor (T, W)
and record (X, Q) packets. This table is the live order; the F rows above keep
their original scope for reference. **V** = visible, gets its own Obtainium
drop and phone check; **Q** = quiet, rides along with the next V drop. Visual
evidence is the JVM render set ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)).

| # | Packet | Scope | Kind | Status |
|---|---|---|---|---|
| 1 | S0a | Sync pause switch; Delete account hidden; honest Account copy; R8 keep rule | V | Done — #380, drop 99 |
| 2 | X2 | a) JVM migration tests 5→6→7, debug-asset schemas, guard · b) pre-migration `temper.db` copy | Q | X2a done — #382; X2b done — #402 |
| 3 | S0b | Pull writes in place; refused custom-lift delete retried | Q | Done — #383 |
| 4 | Q1 | First-launch chooser is a real gate; permission walk waits; D01 date-aware headline and Back to today | V | Done — #384, drop 100 (S0b and X2a rode along) |
| 5 | X1 | Docs truth pass; this table; ADR-031 (sync lane), ADR-032 (evidence lanes) | Q | Done |
| 6 | T1 | Workout test triage: source-string assertions that pin removable code become rendered/semantic checks | Q | T1a done — #388; T1b done — #392; T1c-1 done — #404; T1c-2 done — #408 (the last eleven files: the floor's chrome, identity, entry, switcher, notes and reduced motion) |
| 7 | W1a | Visible "Lift n of N" switch; numeric-entry cue; one Add set; 48 dp evidence chip; no double announcements | V | Done — #389, drop 101 |
| 7a | R0 | A second tap on a lift in the routine picker is never lost | Q | Done — #391 |
| 8 | W1b | "Effort · optional"; planned-rest copy; one ±15 control set; coach goal reaches the workout | V | Done — #393, drop 102 |
| 8a | X3 | Retired goldens leave the hosted lane: the golden checks, their PNGs and manifests, `FloorGoldenTest`, `FoundationGoldenTest`, `WorkoutFrozenFrame`, `GoldenImageAssert` and `GoldenPageCatalog` go; the layout and journey tests keep their reachability checks ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)) | Q | Done — #395 |
| 8b | W1c | Entry wells hold still on the first working set at side-by-side sizes (ADR-030 amended); timer overlay test types without the keyboard | V | Done — #396, drop 103 |
| 8c | X4 | Clean foundation: a rest length picked while the rest page or a lift is still loading is no longer overwritten by the coach's; the three hosted layout expectations the goldens hid (progress-line case, edit reveal, edit-denied dock); the static gate fetches the Kotlin compiler and fails when it cannot parse; Kotlin plugin markers from Maven Central only; cloud sessions reach Central through Google's mirror; the checkers' string stripper handles nested templates and char literals in them; the routine editor's Leave anyway can no longer throw on a write finishing at that instant and strand the screen (the long-standing `RoutineEditorViewModelTest` wedge) | Q | Done — #398 |
| 8d | W1d | Large-text floor (owner decisions of 23 September): at font 1.6 and above the stats row shows Last alone, Best and Volume in Details; stats values shrink to fit one line; the stepper's − / + drawn whole at large text; an outsized weight keeps its unit | V | Done — #399, drop 104 |
| 9–12 | W2a–d | Dead code and lower token ceilings · shared rest commands, atomic `adjust` · coach/picker performance · save/undo and session-state extraction | Q | W2b-1 done — #403; W2b-1b done — #405; W2b-1c done — #406; W2b-1d done — #407; W2a done — #409 (dead code out, four token ceilings down, the rest-length sheet honouring reduce motion); W2b-2 done — #411 (the Log and the rest page share one set of rest commands and one hint loader; no behaviour change); W2b-3 done — #412 (the lock screen's and rest page's Skip name their rest; the dock's Skip is unchanged); W2b-4 in review (the rest page's next-set line is the Log's, shown only where the Log shows it). Then, order of 23 September amended 24 September: W2c, W2d-1–3 (undo, save, timed work), the rest-alarm packet, then a check-only drop (owner decisions of 24 September) |
| 12a | X5 | Local coverage counts the classes the JVM tests load through Robolectric (`tools/verify.sh`), and the floors are re-baselined on honest numbers, never lowered to hide a fall (owner decision of 24 September) | Q | Done — #410 |
| 13 | W3 | Floor renders across the ADR-032 matrix, with reachability assertions, become the floor's gate | V | Pending — Milestone A |
| 14 | S1 | Outbox in the save's transaction; enrolled user id; delete callers; tombstone time; poison-row quarantine; restore/sign-out reset cursors → unpause | V | Pending |
| 15 | F8a | Single-choice radio roles app-wide; keyboard focus stays out of Home under the chooser | Q | Pending |
| 16 | F4 | Home: finished block links to its session (D14); 48 dp week strip; read-error state; set-up-my-week; saved selected date; `ThisWeekCard` on other days | V | Pending |
| 17 | F6a | History: one period for totals, calendar and list; period saved | V | Pending |
| 18 | F6b | History: calendar feedback, day-sheet title parity, shared effort control, error banner | V | Pending — Milestone B |
| 19 | S2a | Server: DDL and RLS for all 17 tables; server change time; conditional upsert; delete-account function (shown to the owner first) | Q | Pending |
| 20 | S2b | Client: composite cursor (Room v8 + migration tests); RPC push; Delete account back | V | Pending |
| 21–23 | F10a–c | Settings "Saving & sync" row with live status · named groups, icons, one regenerate path · split `SettingsScreen` / `BackupCoordinator` | V, V, Q | Pending |
| 24–25 | S3a–b | Room v9 revision/tombstone columns on live strength tables · strength history syncs; first-sync rule for settings | Q, V | Pending |
| 26–27 | F7a–b | Plan: routines visible with a menu, one word (routine), dead UI removed · split `RoutineEditorViewModel` | V, Q | Pending |
| 28 | S4 | WorkManager hygiene, timeouts, encrypted tokens, `WorkerFactory` | Q | Pending |
| 29–31 | F5a–c | Body sizing from the measured area · vector anatomy (one geometry for art, heat, taps) · polish and render matrix | V | Pending |
| 32 | F8b | Library: Clear filters; saved search | V | Pending |
| 33 | F8c | Onboarding entry; stale comments | V | Pending |
| 34 | F9 | Cardio composer: standard header, date picker; cardio details | V | Pending |
| 35 | F11 | Integrated acceptance: every tab × 360/412/600 dp × font 1.0/1.6/2.0 × landscape; owner-phone timings | V | Pending — Milestone C |

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

Order ([ADR-027](architecture/ADR-027-workout-logging-redesign.md)): session
header with `Exercise n of N · x of y sets` and one progress segment per lift,
Finish and the overflow (Switch, Skip, Swap, Remove, notes, summary); 112 dp
image-led exercise identity (tap to switch) with Details and Working | Warm-up;
Last set · Best set · Volume (this exercise); weight and reps (or hold time) as
two hero numerals with round − / + plates, side by side until large text stacks
them; optional RPE 6–10 with Easy / Max effort ends and help; Next set with
Why and Apply; today's sets as chips (current ringed, Add set once the plan is
met, Edit opens the labelled sheet); the dock. Keep session totals out of
exercise-specific telemetry. Preserve load meanings (lifted, added, assistance,
bodyweight, hold) and existing numeric validation.

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

Dock: one companion slot (the rest card at rest, running or done; the 56 dp
hold/set bar; or error/undo/honesty with a compact clock) plus the minimum-72 dp
primary, whose second line names the payload. Error/undo takes companion
priority while an active timer stays reachable through a compact clock. Success
marks the saved chip in the set history and announces the receipt once, not
another tall panel. Receipt expiry does not move the primary's bottom edge.
Large text may grow controls.

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

*The per-packet render matrix is [ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)
decision 1; the list below is the full F11 acceptance scope.* Native layouts:
360x640, 360x800, 412x840, 600 dp width and 640x360 landscape.
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

*Since 23 September ([ADR-032](architecture/ADR-032-jvm-evidence-lanes.md)) the
emulator goldens described in this paragraph are retired as baselines; the JVM
render set, with reachability assertions, is the visual evidence. Packet X3
removed them.* Golden
evidence pins profile/API/image revision/renderer/fonts/locale/clock/
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
- Haptics, Doze, upgrade and performance evidence cannot be substituted
  with an emulator or a browser concept. Physical TalkBack sessions are
  optional refinement, not a ship gate.

## Execution log

- 2026-09-16: created `codex/frontend-baseline`, preserved existing Windows
  setup/audit changes; confirmed WHPX acceleration. No system images/AVDs or
  SDK command-line tools were installed. F0 provisions repository-owned AVDs.
