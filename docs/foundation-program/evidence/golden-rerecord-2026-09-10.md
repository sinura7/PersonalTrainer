# Golden re-record — 10 September 2026

**Golden:** `app/src/androidTest/assets/goldens/foundation-state-gallery-api29.png`
**Recorded from:** the hosted emulator lane's own capture on pull request #210
(`.github/workflows/ci.yml`, job *Instrumented smoke*, run 34430419245),
printed base64 by `tools/ci-instrumented.sh` and decoded with `base64 -d`.

## Why

`FoundationGoldenTest.galleryMatchesCommittedApi29Golden` failed on every
hosted run since the profile was matched (#199) with the same numbers:

```
7091/1635795 pixels (0.433%), bounds=[84,664..858,1321]
```

The previous baseline was recorded on a developer machine's emulator. The
lane runs the same API 29 x86_64 image on the Nexus 5X profile (1080 × 1920,
420 dpi, 360 × 800 dp golden viewport) but under GitHub's `ubuntu-latest`
runner with `reactivecircus/android-emulator-runner` v2.38.0, whose GPU is
SwiftShader.

## What the diff is

Compared pixel by pixel here (pure-Python PNG decode, no tolerance):

| Where | Pixels | What is drawn there |
|---|---|---|
| y 650–700, x 50–250 and 650–900 | 3,537 | the `PRIMARY` / `SECONDARY` kickers of the Loading card |
| y 1100–1150, same columns | 3,370 | the `PRIMARY` / `SECONDARY` kickers of the Empty card |
| y 1150–1320, x 80–110 and 830–860 | 184 | the rounded corners of the Volt *Start activity* button |

Largest per-channel difference on any pixel: **39 of 255**. No pixel
differs by more; no region outside the kickers and the button corners
differs at all. The kicker is `InstrumentType.kicker`, 11 sp tracked
uppercase (`Kicker` in `ui/components/GymSurfaces.kt`); tracked small caps
and a 16 dp corner are exactly where two rasterisers disagree by a few
levels. No component drawn in the gallery changed between the two
recordings (`git log` on `ui/preview/`, `ui/components/GymSurfaces.kt`,
`ui/theme/`).

## Decision

The lane is the renderer that runs on every pull request, so its capture
is the baseline. The comparator stays exact (zero differing pixels); no
tolerance was added. A developer emulator with a different GPU may now
show the same 0.433 % in reverse; re-record from the lane, not from the
desk.

Evidence kept: the lane's `foundation-state-gallery-api29-diff.png`
(magenta over the changed pixels) is reproducible from the run above.
