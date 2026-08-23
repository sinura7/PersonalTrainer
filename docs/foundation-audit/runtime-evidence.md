# Runtime, accessibility, and visual evidence

## 1. Method and limits

The app was built and exercised from a fresh install on two headless Android emulators:

| Profile | Use |
|---|---|
| API 35 Google APIs, Pixel 6 profile | Main guided flow, all tabs, pushed pages, logging, rest, summary, populated history |
| API 29 AOSP, Pixel 2 profile | Instrumented tests, custom-week flow, font scale 2.0, landscape |

The primary portrait viewport was 360 dp wide. Animations were disabled only to make
software-emulated interaction practical; the app’s motion implementation was reviewed
statically.

The host had no KVM. API 35 system processes produced ANR dialogs during cold boot and its
instrumentation process crashed before tests. Those environment failures are not attributed
to Temper. API 29 was stable enough to execute all 14 instrumented tests.

This pass did not claim:

- physical-phone performance;
- real haptic or sound quality;
- pocket/Doze rest reliability;
- Google Drive OAuth success;
- TalkBack traversal quality;
- color perception under gym lighting;
- accurate frame timing.

## 2. Build and automated evidence

### Gradle lane

```text
./gradlew testDebugUnitTest assembleDebug lintDebug
BUILD SUCCESSFUL
758 tests, 0 failures, 0 ignored
```

- Duration reported by Gradle test report: 16.021 seconds.
- Debug APK: approximately 20 MB.
- Compiler warnings: deprecated Google Sign-In APIs and deprecated vibration overload.
- Lint: 0 errors, 64 warnings.

### Static preflight

```text
tools/preflight.sh
647 plain-JVM tests passed
preflight: OK
```

All configured static checks reported zero findings. The preflight count is lower because it
does not include Robolectric/ViewModel/Android-classpath tests that Gradle executes.

### Instrumented lane

API 29 ran 14 tests:

- 13 passed;
- 1 failed in
  [`InstrumentationSmokeTest.kt`](../../app/src/androidTest/java/com/sinura/personaltrainer/InstrumentationSmokeTest.kt);
- expected package: `com.sinura.personaltrainer`;
- actual debug package: `com.sinura.personaltrainer.debug`.

This is a stale test assertion, not an app launch failure. It proves that the checked-in
instrumented lane is not green despite documentation describing debug-id isolation.

API 35 started zero tests because the non-accelerated emulator’s instrumentation process
crashed. The result is recorded as an environment limitation, not a second product failure.

## 3. Runtime coverage

| Surface/state | Evidence | Result |
|---|---|---|
| Setup fork | [`01-onboarding-fork.png`](evidence/01-onboarding-fork.png) | Clear hierarchy and two paths |
| Guided experience | Inspected interactively | Cards and selection worked |
| Days/week | Inspected interactively | 1–7 layout fit 360 dp |
| Preferred days | Inspected interactively | Seven day controls fit |
| Place | Inspected interactively | Selection and copy rendered |
| Goal/emphasis | Inspected interactively | Selection advanced correctly |
| Bodyweight | Inspected interactively | Optional wheel and units rendered |
| Generated preview | [`02-plan-preview.png`](evidence/02-plan-preview.png) | Real lifts and final actions rendered |
| Custom week | [`03-custom-week.png`](evidence/03-custom-week.png) | Day strip and empty CTA rendered |
| Exercise picker | [`05-exercise-picker.png`](evidence/05-exercise-picker.png) | Search/list/multi-add action rendered |
| Home planned state | [`04-home.png`](evidence/04-home.png) | One-tap start, week strip, clear hierarchy |
| Start sheet | [`05-start-sheet.png`](evidence/05-start-sheet.png) | Routine estimates and free option rendered |
| Body empty | Inspected interactively | Empty guidance and start action worked |
| Body populated | Inspected interactively | Map rendered after completed set |
| Body recommendations | [`16-body-recommendations.png`](evidence/16-body-recommendations.png) | Actionable cards below map |
| Plan | [`06-plan.png`](evidence/06-plan.png) | Pinned week and routines rendered |
| Plan day sheet | [`07-plan-day-sheet.png`](evidence/07-plan-day-sheet.png) | Start/edit/swap/unpin actions rendered |
| History empty | Inspected interactively | Empty guidance rendered |
| History populated | [`17-history.png`](evidence/17-history.png) | Calendar worked; row identity collapsed |
| Settings top | Inspected interactively | Units/schedule/coaching rendered |
| Settings backup | [`18-settings-backup.png`](evidence/18-settings-backup.png) | Local/Drive controls and long caption rendered |
| Library | [`08-library.png`](evidence/08-library.png) | Family expansion and thumbnails rendered |
| Routine editor | [`09-routine-editor.png`](evidence/09-routine-editor.png) | Targets worked; compact labels/names |
| Exercise Detail empty | Inspected interactively | Add-to-routine empty action rendered |
| Active Workout | [`10-active-workout.png`](evidence/10-active-workout.png) | Entry, validation, scrolling worked |
| Rest running | [`11-rest-running.png`](evidence/11-rest-running.png) | Countdown, adjust, skip, set receipt rendered |
| Leave dialog | Inspected interactively | Keep/discard copy matched live bar |
| Home with live workout | [`12-live-session-bar.png`](evidence/12-live-session-bar.png) | Global resume strip rendered |
| Workout Summary | [`13-workout-summary.png`](evidence/13-workout-summary.png) | Saved state confirmed; volume mismatch found |
| Session Detail | [`14-session-detail.png`](evidence/14-session-detail.png) | Receipt and repair controls rendered |
| Exercise Detail populated | [`15-exercise-detail.png`](evidence/15-exercise-detail.png) | Records/totals/ghost trends rendered |
| Font scale 2.0 | [`19-font-scale-2.png`](evidence/19-font-scale-2.png) | Setup remained scrollable and readable |
| Landscape + font 2.0 | [`20-landscape-font-scale-2.png`](evidence/20-landscape-font-scale-2.png) | First question remained usable |

Sheets/dialogs not individually opened were verified through their Compose call sites and
state branches in the page atlas. They remain runtime checklist items.

## 4. Verified runtime findings

### RT-01 — History loses session identity at 360 dp

The populated History row rendered title and date as `…` and `…`. Sets, work, duration, and
overflow remained visible.

Cause:

- `SessionLogRow` uses three fixed columns: 48 dp, 88 dp, and 48 dp;
- `InstrumentRow` also adds spacing and the optional 48 dp menu;
- identity receives only the remaining weighted width.

Impact: the user cannot tell which session they are opening or repeating.

Acceptance:

- at 360 dp and font scales 1.0–2.0, a history row exposes at least a meaningful title;
- metrics may stack, collapse, or move to a second line before identity disappears.

### RT-02 — Summary and receipt disagree on volume

One set entered as 100 lb × 5 displayed:

- Summary hero: **501 lb**;
- Summary lift row: **500 lb**;
- Session Detail: **500 lb**;
- Exercise Detail lifetime volume: **500 lb**.

Cause:

- pounds are normalized to tenths of a kilogram;
- the resulting volume converts to a half-pound boundary;
- Summary calls `roundToInt`;
- shared volume formatting calls ties-to-even `round`.

Impact: a post-workout confirmation contradicts the durable receipt, weakening trust.

Acceptance:

- all surfaces consume one volume-format function;
- property tests cover kg/lb round trips and exact `.5` boundaries;
- a session’s hero, lift subtotal, history row, detail receipt, and exercise lifetime total
  agree byte-for-byte for the same work.

### RT-03 — Notification denial dominates the first logging viewport

After declining notification permission, the recovery banner filled most of the content
area. On the 360 dp profile, the user had to scroll before weight and repetition wells became
visible.

Impact: the app explains an important capability at the moment it most obstructs.

Acceptance:

- the primary set entry remains visible or one obvious scroll away;
- the banner can collapse after its first full explanation;
- the recovery action remains available without repeating a wall of copy.

### RT-04 — Routine rows over-prioritize movement controls

Long exercise names truncated aggressively. In expanded state, the target load’s label was
only the unit (`lbs`), so an empty field read ambiguously.

Acceptance:

- exercise identity remains distinguishable at 360 dp;
- reorder controls can move to an explicit reorder mode or secondary line;
- target load is labeled “Target weight” with unit as supporting/suffix content.

### RT-05 — Body’s useful actions begin below the figure

The populated figure occupied almost the entire first viewport. Recommendation cards began
below the fold.

This is not automatically wrong—the figure is the tab’s identity—but it makes the user’s
action cost depend on scrolling past a mostly read-only visualization.

Acceptance:

- usability test answers whether users understand the map and find advice;
- if they do not, expose the top recommendation or a compact summary before the full figure
  without adding a fifth surface.

### RT-06 — Large text is viable but not fully proven

The setup fork and first question remained readable at font scale 2.0 in portrait and
landscape. Content correctly required scrolling rather than shrinking text.

Unproven:

- active-workout wells and pinned log action at 2.0;
- History’s already-constrained row;
- live bar metrics;
- all sheets with the IME open;
- TalkBack focus order after rotation.

## 5. Static findings that runtime did not close

### Exact rest scheduling

On-screen countdown worked. That does not prove screen-off completion. Both exact alarm calls
require declared access or a system exemption that the app neither declares nor checks on
Android 12+, and exceptions are swallowed. The receiver also does not verify that the
elapsed-realtime deadline is due before completing a still-present timer.

Required physical/device test:

1. Fresh install on API 31+.
2. Start a 60-second rest.
3. Turn screen off.
4. Force Doze where practical.
5. Verify cue at the expected second with app backgrounded.
6. Repeat with notifications denied and granted.
7. Record `canScheduleExactAlarms()` and chosen fallback behavior.
8. Extend or immediately replace a running rest while the original alarm is in flight; verify
   the stale alarm cannot complete the current rest early.

### TalkBack

Code contains useful semantics, but no service-level traversal was performed. Required gates:

- tabs announce selected state and label;
- Body muscles are reachable without precision tapping;
- rest time changes do not spam announcements;
- charts announce trend direction and range;
- dialogs return focus to the invoking action;
- destructive actions are distinguishable without color.

### Real performance

The API 35 emulator had no hardware acceleration. Timings and ANRs from that system are not
valid app-performance evidence. A physical mid-range device and synthetic multi-year data are
required.

## 6. Screenshot catalog

| File | What it proves |
|---|---|
| `01-onboarding-fork.png` | Current first choice and visual direction |
| `02-plan-preview.png` | Generated routines and final commit |
| `03-custom-week.png` | Manual day/lift foundation |
| `04-home.png` | Planned Home state |
| `05-start-sheet.png` | Shared alternative-start model |
| `05-exercise-picker.png` | Shared catalog picker |
| `06-plan.png` | Week/routine co-location |
| `07-plan-day-sheet.png` | Day action hierarchy |
| `08-library.png` | Movement-family catalog |
| `09-routine-editor.png` | Expanded targets and small-phone compression |
| `10-active-workout.png` | Set-entry viewport |
| `11-rest-running.png` | Rest instrument and logged set |
| `12-live-session-bar.png` | Global unfinished-session state |
| `13-workout-summary.png` | Summary and 501 lb observation |
| `14-session-detail.png` | Durable 500 lb receipt |
| `15-exercise-detail.png` | Populated records and trends |
| `16-body-recommendations.png` | Coach cards after the figure |
| `17-history.png` | Calendar and erased session identity |
| `18-settings-backup.png` | Data survival controls and copy density |
| `19-font-scale-2.png` | Portrait large-text behavior |
| `20-landscape-font-scale-2.png` | Landscape large-text behavior |
