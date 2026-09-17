# F1 — Shared controls and app shell

Status: complete and integrated — PR #349, `97d08ca4`.

The [native review page](controls.html) contains reviewed component states,
layout extremes, Android dialog/keyboard observations and real app pages.
This establishes shared controls and shell behavior. The workout and whole-app
redesign remain pending in their assigned packets.

## Delivered behavior

- Named radio choices, independent checkbox toggles, value-application actions
  and quiet suggestions. Selection includes a shape/state marker.
- Wrapping button/chip labels, 48 dp minimum targets and the existing 72 dp
  workout-action floor. Native gallery covers pressed, focused, saving,
  disabled, selection, entry, content, loading and overlay states.
- Shared headers and live bars retain full accessibility text while previewing
  unrestricted saved names in two lines. Short windows combine activity kind,
  status and identity in one line. Stored names are unchanged.
- Confirmation title and warning share a bounded scroll area with actions
  outside it. Confirmation produces a press tick, not a false save-success haptic.
- All five navigation labels remain visible. Measured label fit determines a
  single row or three-plus-two rows. Short windows place icons beside labels
  only when the already-selected rows fit. Tab restoration remains in AppNav.
- Live-bar identity, elapsed time, set count and rest context share one resume
  target; overflow stays separate. Workout/Cardio labels identify the activity.
  Metrics wrap; timer ticks are not live announcements. Error dwell honors the
  accessibility timeout preference. Compact rows retain their minimum touch
  height while reducing optional padding to make room for full error messages.
- Body/History periods, Body front/back and load-type choices adopt explicit
  single-choice semantics. Body figure context has its own full-width row.
  Remaining feature-specific migrations belong to F2–F10.

## Native verification design

`FrontendShellInstrumentedTest` composes shipping navigation and the live bar
inside a fixture Scaffold. Its 25 profiles cover 360 × 640, 360 × 800,
412 × 840, 600 × 840 and 640 × 360 dp at font 1.0/1.6/2.0; RTL; reduced
motion; and long-name, stale, error and cardio states. Four adverse profiles
reserve logical 24 dp status and 48 dp three-button or 24 dp gesture insets.
These simulate space requirements; they do not prove physical gesture behavior.
Adverse workout fixtures use production `DataHealthCopy.FINISH_FAILED`; cardio
uses its own production `CompleteTraining.LIVE_FINISH_FAILED` message.

Assertions check minimum targets, non-overlap, complete single-line navigation
labels, preservation of a fitting five-item row, actual metric taps, separate
overflow, exact inset allocation, at least one full target of scrolling space,
and complete last-action containment inside the list before a real click.

`FrontendControlsInstrumentedTest` has ten cases covering semantics, draft-only
presets, numeric validation/absolute entry/cancel, focus and action states,
large-text reachability and overlay dismissal. Seven control and 25 shell
images form the 32 new renderer-specific references.

Logical viewport tests use Compose ForcedSize, FontScale and explicit
WindowInsets. This prevents host portrait pixel insets from being interpreted
at a different density. Android dialogs and keyboards instead use actual
device windows; system font scale is set before launch and restored afterward.
IME observations require a nonzero native window extent. Captures wait for
native idle and a 750 ms software-renderer presentation interval.

Missing required references fail and retain the actual image. Existing pixel
tolerances are unchanged. Exact source/image hashes and device properties are
in the [golden manifest](../../../app/src/androidTest/assets/goldens/windows-swiftshader37/frontend-manifest.json).
Real app and separate-window observations have their own
[manifest](native/f1/manifest.json). Pixel equality, interaction correctness,
visual review and physical-device acceptance remain separate evidence.

## Final-source evidence

| Final-source check | Result |
|---|---|
| Recording `20260917-060135045` | 35/35 passed; 32 images reviewed |
| API 29 comparison `20260917-060414598` | 136/136 passed, including 32 F1 image comparisons |
| API 36 shell matrix `20260917-060817179` | 25/25 passed |
| API 26 shell matrix `20260917-061028797` | 25/25 passed |
| Full local gate | Passed: 2,537 unit tests, static checks, debug build, lint and test APK; 2m 8s |
| Independent and adversarial reviews | Both clear; no unresolved product or visual findings |

The recording manifest preserves its original 29 source hashes and 32 image
hashes. A subsequent JVM-test-only synchronization correction is recorded below;
the native rendering sources and references are unchanged.
Compared with local commit `f142ea5c`, six references changed intentionally and
four inset references were added. The other 22 references are unchanged. Review
acceptance was subsequently confirmed by hosted and integrated checks below. Final
commands, counts, profiles and report hashes are recorded in
[verification.json](native/f1/verification.json).

Earlier executed evidence:

- API 29 full run `20260917-055400296`: 136/136 passed before that correction.
- API 29 recording `20260917-054841289`: 35/35 passed; all 32 images reviewed.
- API 36 `20260917-052912650` and API 26 `20260917-053449375`: 39/39 each.
  Later shell runs `20260917-053214573` and `20260917-053713208`: 25/25 each,
  before the final optical grouping and error-copy corrections.
- Earlier full local gates passed 2,536 unit tests, debug build, lint and
  test-APK compilation; the final source also passed as recorded above.
- Native window run `20260917-050534591`: both journeys passed. Reviewed
  captures show the real numeric keyboard, rest sheet, font-2 confirmation
  and long-name warning with reachable actions.
- Real MainActivity observations: `20260917-043013541`. Their portrait shell
  is unchanged by the final compact landscape correction.

One legacy font-2 workout reference was deliberately updated in run
`20260917-042534632` for wrapping warm-up labels; the other nine existing
Windows references remain unchanged. Obsolete workout composition belongs
to F2/F3. Each intentional new-reference change has a manifest rationale.

## Corrections and rejected evidence

- Both reviewers found unbounded saved-name growth in fixed chrome. The
  adversarial pass also found an unbounded dialog title. Bounded previews,
  full semantics and the shared dialog scroll area resolve these cases.
- Earlier sheet/keyboard observations captured before native presentation.
  They were rejected and replaced by the reviewed actual-window captures.
- A stronger last-row assertion exposed insufficient landscape space on
  API 36. Explicit logical insets and compact navigation resolve it. The test
  checks actual list bounds and fails before attempting a zero-height scroll.
- Initial compact navigation let icon width create an unnecessary extra row
  and allowed word wrapping. Row selection now uses label fit first; complete
  measured labels and matched padding determine compact eligibility.
- The final adversarial image pass caught an abbreviated fixture error. All
  adverse fixtures now use the longer runtime message; short-window live rows
  remove optional vertical padding while retaining the 56 dp touch floor.
- Run `20260917-045516575` suffered a System UI ANR/restart while heavy local
  tasks overlapped native tests. Those captures were rejected. Native checks
  and the heavy local gate now run sequentially.
- Run `20260917-054311890` had 18 input failures after a Launcher3 ANR; all
  its images were rejected. Owned-emulator startup now checks global window
  focus for Launcher3 with a 30-second bound and fails before testing if absent.
  The first check used an incomplete dump and failed closed; the corrected
  global dump passed before the replacement recording. This does not guarantee
  against a later Android system ANR.
- Legacy test corrections: Compose's queued v2 rule for background flow
  emissions; explicit settling of delayed rest state in pixel fixtures;
  synchronous rescheduling of the persisted timer before exact-alarm assertions.
  Affected legacy run `20260917-045303330` passed all 23 cases.
- Edit tests await completed update state. Onboarding tests own/cancel a private
  DataStore; an earlier failure did not reproduce in the targeted 102-test rerun,
  so isolation is not claimed as a proven root-cause fix.
- The lambda-arity checker supports composable slots with five new fixtures.
  All 20 checker fixtures pass. No static-check ceiling was increased.

Supporting official guidance: [Compose v2 test migration](https://developer.android.com/develop/ui/compose/testing/migrate-v2),
[content text direction](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/style/TextDirection),
[device configuration overrides](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/DeviceConfigurationOverride.Companion).

## Audit disposition and remaining acceptance

- D13: integrated; native geometry and complete label checks pass.
- D15: shared contracts implemented; screen compositions remain F2–F10.
- D02: prerequisites implemented; legacy workout suggestion treatment remains F2.
- D16/D17: local shell checks above pass; feature-page, physical accessibility,
  performance and integrated evidence continue in owner packets.

The hosted runs `35192689967` and `35192657616` produced 32 pixel-identical F1
captures. All were reviewed and accepted as initial references for the hosted
renderer, separately from Windows captures; their comparison rerun passed.
Origin, image hashes and renderer limitations are recorded in
`app/src/androidTest/assets/goldens/frontend-hosted-manifest.json`.
Both initial native jobs reported exactly 41 failures: 32 missing new references
and the nine older workout differences assigned to F2/F3. No older workout
reference was replaced. Hosted emulator results remain nonblocking under
ADR-024; deterministic hosted checks remain required.

The PR's required check exposed a real/virtual-clock race in a test wait, while
the same source passed the push check. Room can finish after the test's last
virtual-clock advance, leaving the subsequently scheduled rest delay parked.
`awaitRestRunning` now drives that clock and yields to Room within the existing
30-second bound, still requiring the actual rest state to become running. A
gated-write regression deliberately schedules the delay after the first advance.
All 88 workout ViewModel tests passed locally, including that regression. This
changes test synchronization only; the production timer contract is unchanged.

The owner explicitly approved publishing this packet's source and synthetic
screenshots, opening its PR and merging after checks on 17 September 2026.
The push succeeded; [PR #349](https://github.com/sinura7/PersonalTrainer/pull/349)
is merged. Hosted and post-merge integrated acceptance passed. F2 follows on
its own branch under the approved one-packet-at-a-time protocol.

Physical TalkBack, actual gesture navigation, the phone's display settings,
haptics, refresh-rate performance and signed upgrade/data retention remain
milestone checks. No Obtainium drop, signer change or version increment is
issued by F1.

## Integrated closure

PR #349 merged at `97d08ca4`. Both required hosted checks passed. Hosted F1
comparison passed all35 control/shell tests including32 images; the only nine
remaining hosted failures are the older workout references owned by F2/F3.
From clean trunk, the full gate passed, then a forced fresh unit run passed
2,537 tests. API29 integration `20260917-073927328` passed35/35 native checks,
including all32 F1 image comparisons. The short-lived branch was deleted.
F2 may now begin. No signed phone drop is claimed.
