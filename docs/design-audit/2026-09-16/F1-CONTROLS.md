# F1 — Shared controls and app shell

Status: implementation and verification in progress; not yet merged.
Base: `d77fc432` (F0 integrated). Branch: `codex/frontend-controls`.
Authority: ADR-026 and the approved frontend plan.

## Scope and contracts

- Radio choices, checkbox toggles, value-application buttons, quiet suggestions,
  and informational content now have named component roles. Selection includes
  a shape/state marker rather than relying on lime alone.
- Button labels grow instead of truncating. Buttons and chips keep a 48 dp floor;
  logging retains its 72 dp floor. Pressed, focused, saving and disabled states
  are exercised in the native component gallery.
- The shared header and confirmation actions wrap text. Fixed headers/live bars
  preview unrestricted saved names in two lines with full text semantics; short
  windows combine live kind/status/identity in one line to preserve navigation and
  content. Names remain unchanged in storage. Confirmation title and warning share
  a scroll area, with actions outside it. Confirmation
  gives a press tick, not a false persistence-success haptic.
- All five navigation labels remain available. Actual text measurement selects
  a five-item row or three-plus-two rows. Tab restoration remains in AppNav.
- The live bar's rail, identity, elapsed time, set count and rest context share
  one resume target. Overflow is separate. Workout/Cardio labels distinguish
  activity kind; enlarged text keeps metrics visible. Error dwell honors the
  accessibility timeout preference; timer ticks are not live-region messages.
- History/Body period choices, Body front/back and load-type choices adopt the
  exclusive component. Other screen-specific selectors migrate with their
  owner packets. In particular, the legacy workout recommendation border is
  still assigned to F2; F1 does not close D02 on the workout screen.

## Native verification design

`FrontendShellInstrumentedTest` composes the shipping navigation and live bar
inside a fixture Scaffold. It is a shared-shell test, not a replacement for
real feature-page tests. Twenty-one profiles cover 360 x 640, 360 x 800,
412 x 840, 600 x 840 and 640 x 360 dp at font 1.0/1.6/2.0, plus RTL and
reduced-motion cases, plus long-name/error/stale/cardio combinations. Tests measure
target dimensions, full label layout, non-overlap, actual metric taps, overflow
independence and unclipped last-action containment with a real click.

`FrontendControlsInstrumentedTest` verifies role semantics, draft-only preset
application, numeric validation/absolute entry/cancel, action states, large-text
reachability and overlay dismissal. Native dialog/keyboard observations use
actual device windows rather than pretending ForcedSize controls another window.
The keyboard/accessibility-window preferences are restored after each test.
Separate-window captures wait for native idle plus a 750 ms software-renderer
presentation interval; IME presence also requires a nonzero native window extent.

New viewport goldens record actual constrained logical dimensions. Legacy
references retain their original clipped mount until their owning packet replaces
them. Missing references still fail and now retain the actual PNG for inspection.
No comparison threshold is relaxed. Pixel references and interaction assertions
have separate roles; neither establishes physical-device performance or TalkBack.

## Audit disposition

- D13: implemented; acceptance pending final native comparison and reviews.
- D15: component contract established; screen composition remains with F2–F10.
- D02: prerequisite established; workout migration remains F2.
- D16/D17: broader layout/accessibility/evidence work continues in owner packets.

## Executed evidence

- Initial native recording: 23/23 behavioral checks, run `20260917-040241239`.
  Recording is not a pixel comparison pass. Visual review prompted explicit
  radio/checkbox markers, stronger primary focus contrast and metric separators.
- Full local unit/build/lint/test-APK tasks passed: 2,536 tests, no failures.
  The later combined run `20260917-044305684` also passed those tasks; its native
  portion was 127/129, with all 28 new F1 checks and 25 new pixel comparisons passing.
- Native failures in that run: Home's legacy unconfined test recomposer resumed
  from a background flow emission without a Looper; success capture raced the
  delayed rest start. Corrections are under verification. No failed image was
  promoted to a reference to hide these failures.
- Trial run `20260917-044800646` exposed a pre-existing alarm assertion race
  (persisted row precedes arm), then stalled under an explicit StandardTestDispatcher
  in the old Compose rule. That run was stopped after recording the failure. The
  affected screen-test class now uses Compose's v2 rule; the alarm capability test
  synchronously reschedules its persisted current timer before asserting the result.
- Action focus uses real keyboard input mode and focus, with an assertIsFocused
  check; its targeted recording passed in `20260917-044128762`. Whole Android
  font-scale-2 overlays passed in `20260917-043214757` and the combined run.
- References: 25 new shell/control PNGs and one bounded legacy font-2 warm-up-label
  update. The other nine existing Windows references remain unchanged. Source and
  image hashes are in the renderer-specific frontend manifest. The native
  [review page](controls.html) also contains real MainActivity before/after views.
- The lambda-arity checker now understands composable function slots, with five
  added checker fixtures. No checker ceiling was increased over the committed
  baseline. Gallery public-component coverage was preserved. The edit ViewModel
  test now awaits the completed update state. Onboarding tests own and cancel a
  private DataStore; an earlier full-run failure was not reproduced in the targeted
  102-test rerun, so isolation is a fixture correction, not a proven root cause.

The queued Compose runner follows the official
[test migration guidance](https://developer.android.com/develop/ui/compose/testing/migrate-v2).
Text direction follows the content's language while layout mirrors independently,
using [TextDirection.Content](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/style/TextDirection).

Run `20260917-045303330`: all 23 affected legacy native tests passed, including
the production-screen matrix, nine workout pixel comparisons and exact-alarm
capability.

## Review corrections and latest verification

Both clean-context reviewers identified unrestricted identity growth in fixed
chrome; the adversarial pass additionally found a dynamic dialog-title overflow.
These are corrected with bounded previews and the shared dialog scroll area.
The independent pass caught two inaccurate native window observations: the sheet
and keyboard had not yet been presented. Those earlier observations are rejected.
Run `20260917-050534591` passed both native window journeys, and the replacement
images have been inspected: the sheet, numeric keyboard and long-name warning/
actions are visible. No pixel reference was loosened to accommodate this race.

Run `20260917-050210256` passed all 31 expanded F1 native checks in recording mode.
There are now 28 shell/control references. Existing non-landscape references were
pixel-identical; the three short-window references intentionally use compact live
identity. The three additional references cover adverse long-name states.

Combined run `20260917-045516575` passed local tasks but failed 29 native checks
during an emulator System UI ANR/restart (confirmed in ActivityManager logcat).
Status-bar disappearance changed capture heights and caused injection failures.
Those images are rejected. Subsequent native runs are separated from the heavy
local gate; final full comparison and cross-API results are still pending.

## Remaining acceptance

Final local gate; reviewed references and comparison run; real-page observations;
API 36 behavior pass; independent and adversarial reviews; integrated verification.
Physical TalkBack, normal phone display settings, gesture navigation and upgrade
acceptance remain explicit milestone checks. No Obtainium drop is issued by F1.
