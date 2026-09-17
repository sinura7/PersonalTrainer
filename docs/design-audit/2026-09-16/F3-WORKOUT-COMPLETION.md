# F3 — Workout completion, timing and recovery

Status: in progress on `codex/workout-completion`, based on merged F2 `579642b6`.
This is not a milestone acceptance or a delivered phone build.

## Implemented behavior

- A typed primary action derives from the session, saved working sets, draft,
  editing, timer and save state. Completed planned work offers Next exercise,
  wrapping to unfinished exercises; final completion offers Finish workout.
  Extra sets and warm-ups explicitly return to entry. Free lifts stay loggable.
- The action carries a rendered identity. Queued Log taps cannot become Next,
  and a second tap inside the platform double-tap interval cannot log the next
  exercise. A timer tick does not cancel the press: the displayed duration is
  the duration submitted.
- A save freezes its ID, owner, timestamp and values before suspension. The
  repository inspects/inserts/updates inside a Room transaction. A repeated
  command acknowledges the same row. Editing uses the original row as a
  compare-and-set condition and cannot overwrite a later correction.
- Failed saves retain the command and disable conflicting entry operations.
  Retry checks the outcome before writing. Return to entry verifies that it
  is safe to release the operation. Ambiguous read failure keeps it owned.
  Removed exercises retain a usable Review save action until release.
- Pending operations and edit originals survive cache and task-restored
  SavedState handoffs. These are not a disk journal for arbitrary force-stop
  or crashes. Existing session and timer persistence remain unchanged.
- Read failure is distinct from an empty/missing session and provides Retry.
  Entry mutations serialize before suspension. Successful write acknowledgement
  precedes receipts and haptics; record/feedback failures cannot duplicate a set.
- Cancel edit is available in entry independently of notification guidance.
  The compact companion prioritizes errors/undo and keeps clock access.
- The rest page has a persistent completed state, anchored controls, wrapping
  context and a ring that yields to short layouts or enlarged text. Time set,
  Start rest, Hold and Set time have distinct labels.
- Clock reads are scoped to the dock, hold entry and open switcher, rather than
  the whole workout composition. The switcher names Current/Complete/Remaining,
  wraps exercise names and retains interruption confirmation.
- Fine-precision restored kg drafts are shown accurately in entry and commit.
  The keypad previews its applied normalized value. Existing kg/lbs conversion,
  progression, history and volume calculations remain unchanged.
- Landscape keeps the primary label "Next exercise" and shows the full next
  exercise name in the scrollable entry context. This deliberate responsive
  exception prevents a long name at font scale 2.0 from consuming the content
  viewport. Portrait retains "Next exercise · name". Completion context says
  "Planned sets complete"; "Extra" appears only after requesting another set.
- Landscape uses the surface's measured parent constraints, including embedded
  viewports. Custom rest uses a single inset-aware scrolling dialog; title,
  typed value, validation and actions remain reachable when the IME reduces
  available height. IME Done applies through the same validation callback.

## Verification in progress

Local logs are under ignored `build/workout-completion/`.

| Run | Executed result |
|---|---|
| `save-recovery-tests-6.log` | 122 targeted tests passed before the latest UI changes |
| `full-gate-1.log` | 2,571 tests; 12 obsolete source-presentation assertions failed |
| `full-gate-2.log` | Static argument-check ratchet stopped verification; corrected call shape |
| `full-gate-3.log` | 2,574 tests; one completion test read a transitional prefill snapshot; other checks passed |
| `native-compile.log` | 15 recovery tests passed after waiting for the selected draft; Android test APK compiled |
| `layout-record-1-native.log` | Test fixture compilation failed on SeedExercise/Exercise conversion; corrected |
| `layout-record-2-native.log` | API 29: 45 cases, 31 passed, 14 failed; recording, not comparison acceptance |
| `layout-record-3-native.log` | API 29: 38 cases, 23 passed, 15 failed; both complete-workout journeys and all 21 rest layouts passed |
| `full-gate-4.log` | Static checker rejected three mixed-argument fixture calls; replaced with named arguments |
| `full-gate-5.log` | 2,574 tests, debug build, lint and Android test build passed; later review fixes require a fresh final gate |
| `layout-record-4-native.log` | Test compilation rejected an unnecessary `onNode` import; removed |
| `layout-record-5-native.log` | API 29: 93 cases, 31 passed, 62 failed; new wrapping timer row triggered unsupported intrinsic measurement of a nested BoxWithConstraints; replaced that nested measurement |
| `layout-record-6-native.log` | Compilation required measuring outer constraints before entering the nested layout receiver; corrected |
| `layout-record-7-native.log` | API 29: 94 cases, 91 passed; short-landscape completion consumed its content viewport, and two overlay test selectors needed lazy-list scrolling / sheet scoping. Hold target reached without autosave, then explicit hold save passed |
| `layout-record-8-native.log` | API 29: 23 cases, 22 passed; overlay selectors corrected. Constrained landscape still read the portrait host window; now uses parent constraints |
| `layout-record-9-native.log` | API 29: 94 cases, 92 passed. Landscape completion passed. One forced-viewport remeasure reported inconsistent density; the harness now waits up to five seconds for the exact requested dimensions, without relaxing bounds. Stronger IME assertion exposed clipped custom-rest input; replaced that dialog layout |

The initial native primary-label assertion used `hasVisualOverflow`. Compose's
centered Text can retain the paragraph's maximum constraint while measuring its
own width to content (observed paragraph width 604 px, text width 244 px, height
42 px). The corrected check measures visible line widths, line ends, ellipsis
and vertical overflow. Pixel comparison tolerance is unchanged. The other
completion fixture failure was a test's `single()` after deliberately adding a
second exercise; this is corrected to select the first exercise.

## Evidence boundaries

`WorkoutCompletionLayoutInstrumentedTest` uses the actual route and Room data,
with durable completed sets seeded before display. `WorkoutCompletionJourney`
uses native buttons to log extra sets, advance, finish, delete and undo.
`WorkoutEntryJourney` retains eight-set, keyboard, correction and denial checks
and adds switcher/timing interruption at system font scale 2.0.

`FloorGoldenTest` now uses immutable `WorkoutFrozenFrame` component integration
fixtures: no live clock, receipt expiry, database or destructive cleanup.
`RestCompletionLayoutInstrumentedTest` renders the shipping rest presentation
with a pinned clock. These establish pixel references for transient states;
they do not replace real-route or lifecycle tests. All new references are
measured viewports with fixed logical insets. API 29 comparisons keep the
existing one-level/256-pixel renderer rounding allowance and fail missing refs.

Required before closure: final green local gate; reviewed recorded references
and fresh comparisons; API 36/26 native behavior; completed switcher and recovery
checks; final independent/adversarial review; deterministic hosted gate; merge
and clean integrated verification; stable-signer forward-version Obtainium drop.
Physical TalkBack, upgrade, haptics, background timing and performance acceptance
remain explicit phone work until executed.

## Latest verified checkpoint

- `layout-record-10-native.log`: all 94 native cases passed. Reviewed 83 pixel
  references and eight window observations; references accepted with hashes.
- `full-gate-6.log`: stopped on two unnecessary imports after the dialog rewrite;
  imports corrected without weakening checks.
- `full-gate-7.log`: all 2,577 JVM tests passed, zero skipped; debug build, lint,
  static checks and Android test build passed.
- `api29-final-native.log`: all 222 native cases passed, zero skipped, including
  fresh screenshot comparison, visible keyboard/IME Done and saved-state overlay
  recreation. Archived run `20260917-115421489` in the verification manifest.
- Manual rest starts synchronously before preference persistence. Three delayed
  preference tests verify that Skip/Time set cannot be reversed by late writes.
- Restoring the landscape dock retains unfinished custom-rest input; the native
  restoration check applies the preserved value exactly once.

Final independent and adversarial review attempts both stopped at the account
usage limit. This is an outstanding review requirement, not a review approval.
API 36/26, hosted verification, merge, integrated verification and distribution
remain pending. No new Obtainium build has been delivered at this checkpoint.

## Audit finding coverage at this checkpoint

| Finding | F3 contribution | Remaining acceptance |
|---|---|---|
| D03 — workout footer | One companion area; completion/recovery and font-scale coordinate checks pass on API 29 | Cross-API checks and physical reachability |
| D04 — entry scroll | Eight consecutive native logs retain entry; edit/delete/undo and explicit advancement journeys pass | Phone interaction trial |
| D09 — exercise navigation | Explicit Switch; current/complete states and large-text interrupt-timing journey pass | Physical accessibility focus review |
| D10 — numeric entry | Exact restored kg payload and normalized application preview; visible IME and Done check pass | Phone keyboard and locale trial |
| D11 — timing language | Distinct Rest/Hold/Set time, planned rest, persistent completion; no hold autosave; delayed-preference races covered | Device background/notification behavior and measured performance |
| D17 — native evidence | 83 reviewed API 29 references, fresh full 222-case native comparison; missing references remain failures | Hosted renderer references, cross-API runs and final independent review |

F3 does not close Home D01/D14 (F4), Body D05/D06 (F5), History D07 (F6),
or whole-app hierarchy D16 (F7–F10). Earlier component/entry findings retain
F1/F2 dispositions; final integrated accessibility and performance belong to F11.
API 36 follow-up: all 86 targeted native layout/interaction cases passed, zero
failures/errors/skips; run `20260917-133750885`, 82 captures archived. These are
cross-version behavior observations, not API 29 pixel comparisons. Detailed
visual review of these captures and API 26 compatibility remain pending.

## Resumed review and compatibility

API 26 passed all 86 targeted cases, zero failures/errors/skips, run
`20260917-134724036`, before the final terminal-timer guard. All 82 API 36
captures and the API 26 completion/small/landscape/rest/overlay cases were
visually reviewed. API 26 system-window captures retain the previously
documented compositor letterboxing; field/actions remain reachable.

Independent source review cleared the prior fixes. Adversarial review found
one terminal-operation race: delayed automatic or manual rest could start during
a slow Finish/Discard write. Both operations now cancel queued rest before
suspension; manual and delayed starts reject locked/terminal state. Focused
adversarial re-review cleared the fix. Two gated DAO regression tests exercise
Finish and Discard, including attempted restarts before and after completion.
Full gate 8 is running to verify this final change.

Full gate 8 ran 2,579 tests: Finish regression passed; the new Discard regression
timed out because its fixture intercepted deleteSession rather than the actual
guarded deleteInProgressSession. The fixture now intercepts the production path.
No production change or weakened assertion was needed; full gate 9 reruns it.
Final local gate 9 passed all 2,579 tests, zero skipped, plus static checks,
debug build, lint and Android test build. Both terminal-timer regressions passed.
Independent and adversarial source reviews are clear. Hosted renderer reference
review, required hosted gate and clean post-merge verification remain pending.

PR #351 opened at `5b598e34`. Final-source API 26 native journeys passed
12/12 with zero skips (`20260917-135849036`), including the terminal-rest guards.
Hosted checks are running; no merge or debug drop yet.

Initial hosted required job 105232837129 failed one of 2,579 tests: the
queued-tap regression observed an enabled initial Log action before its planned
draft finished prefilling. Its first action now waits for the same selected
exercise and prescribed weight/reps as the second action already did. Identity,
double-tap and saved-row assertions are unchanged. Full local gate 10 and a fresh
hosted run must pass before acceptance.
Full local gate 10 passed 2,579 tests and all build/static/lint checks. Hosted
native runs 35230444274 and 35230416832 each produced 81 expected image-reference
failures; all other native checks passed. All 81 actual captures were reviewed
and independently matched across both hosted runs within the unchanged one-level,
256-pixel rounding allowance. Accepted hosted references are separate from Windows
references and have per-file hashes/rationale. Fresh hosted comparison pending.
