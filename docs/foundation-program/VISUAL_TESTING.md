# Visual evidence and golden testing

**Decision date:** 24 August 2026  
**Packet:** P1.2

## Chosen substrate

Temper uses Compose UI Test’s own `captureToImage()` on the local API 29
emulator, with the small comparator in
`app/src/androidTest/.../testutil/GoldenImageAssert.kt`.

This was chosen over Paparazzi/Roborazzi in this packet because:

1. Compose UI Test is already required for the critical device journey.
2. It exercises the Android font renderer, bundled fonts, Material theme,
   resources, density, and semantics in the same APK later journey tests use.
3. It is compatible with the current AGP/Kotlin/Compose matrix without
   pulling the Phase 4 dependency upgrade into Phase 1.
4. The comparator is explicit: exact dimensions and ARGB pixels. A mismatch
   writes both actual and a magenta diff and reports count, ratio, and bounds.
5. The recorded profile is named in the asset and evidence; it does not claim
   a device PNG is renderer-independent.

This is not a custom renderer. It is a thin assertion/artifact layer around
the supported Compose capture API.

## Recorded profile

| Property | Value |
|---|---|
| AVD | `temper-tests-api29` |
| Android | API 29 |
| ABI | x86_64 |
| Display | 1080 × 1920 |
| Density | 420 dpi |
| Acceleration | software (`-accel off`) |
| Renderer | SwiftShader — the hosted lane (`ubuntu-latest`, `reactivecircus/android-emulator-runner` v2.38.0, `profile: Nexus 5X`) |
| App | `com.sinura.personaltrainer.debug` |
| Golden viewport | 360 × 800 dp |
| Theme | Instrument dark |

The committed baseline is
`app/src/androidTest/assets/goldens/foundation-state-gallery-api29.png`,
recorded on 10 September 2026 from the lane's own capture
([evidence](evidence/golden-rerecord-2026-09-10.md)). The lane is the
renderer that runs on every pull request, so it is the reference; a desk
emulator with another GPU can differ from it by a few levels on tracked
small caps and rounded corners.

### Re-record from the lane

When a golden mismatch is a reviewed, intended change, take the new
baseline from the failing run rather than a desk emulator:

1. Open the *Instrumented smoke* job log; `tools/ci-instrumented.sh` prints
   every PNG the harness wrote to the device's Download folder as a
   base64 log group named `png <file>`.
2. Copy the `foundation-state-gallery-api29-actual.png` group's body and
   decode it: `base64 -d > app/src/androidTest/assets/goldens/foundation-state-gallery-api29.png`.
3. Read the `-diff.png` group the same way, and write what changed and why
   into `evidence/`.

## Commands

Record deliberately:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class='com.sinura.personaltrainer.ui.preview.FoundationGoldenTest#galleryMatchesCommittedApi29Golden' \
  -Pandroid.testInstrumentationRunnerArguments.recordGoldens=true

adb pull \
  /sdcard/Download/foundation-state-gallery-api29-recorded.png \
  app/src/androidTest/assets/goldens/foundation-state-gallery-api29.png
```

Verify without the record flag:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.sinura.personaltrainer.ui.preview.FoundationGoldenTest
```

Never accept a changed PNG from an unreviewed batch. Read it, read the diff,
and name the intended visual change in the packet evidence.

## Preview profiles

`app/src/debug/.../ui/preview/PreviewProfiles.kt` provides:

- 360 × 800 dp;
- 412 × 915 dp;
- 600 × 960 dp;
- 360 dp at font scale 2.0;
- 360 dp RTL (`ar`);
- 360 dp reduced motion.

`PreviewFixtures` supplies loading, empty, populated, error, long identity,
large metrics, active, resting, and permission-denied data. Feature previews
map those values into real `UiState` types; production ViewModels never know
about preview fixtures.

## Stability proof

- `unchangedCapturesArePixelStable` captures the same mounted composition
  twice and requires zero differing pixels.
- `galleryMatchesCommittedApi29Golden` passed again after a fresh target/test
  APK install.
- `deliberateTokenChangeProducesSmallLocatedDiff` changes only Volt → Warn
  on the probe rule and requires a visible but bounded 0.1–2% diff with a
  non-empty bounding box.

This foundation closes the substrate portion of FND-043. Page coverage and
visual acceptance remain P9.6/P9.7.

## Page golden fan-out (H3)

`GoldenPageCatalog` names six gym-floor populated goldens
(`home`, `body`, `plan`, `history`, `settings`, `active-strength`) plus a
component gallery and the three `ThemeGallery` previews. The committed
set today is still only `foundation-state-gallery-api29`. Recording those
PNGs is an owner emulator gate on `temper-tests-api29` — this VM cannot
run `connectedDebugAndroidTest`. The 108-name AccessibilityMatrix fan-out
is no longer the required set.

Mount every later capture through `GoldenCapture` (360 × 800 dp,
Instrument theme) so the viewport cannot drift per page.

Debug previews now cover the previously unaudited pieces with real
composables, not a second layout:

| Surface | Preview file | States drawn |
|---|---|---|
| Home + DailyAgenda + MissedWork | `ui/home/HomePreview.kt` | populated (agenda + missed), empty |
| Live cardio | `ui/activity/LiveCardioPreview.kt` | active, missing, error |
| Activity composer | `ui/activity/ActivityComposerPreview.kt` | populated, empty, error |

A later packet records the matching `{page}-{state}-api29` PNGs and adds
`GoldenImageAssert.assertMatches` callers. Do not add those tests until
the PNG is committed — a missing asset fails the connected suite.
