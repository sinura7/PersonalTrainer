# Golden re-record — 10 September 2026

**Golden:** `app/src/androidTest/assets/goldens/foundation-state-gallery-api29.png`
**Recorded from:** the hosted emulator lane's own capture on pull request #210
(`.github/workflows/ci.yml`, job *Instrumented smoke*, run 34430419245),
printed base64 by `tools/ci-instrumented.sh` and decoded with `base64 -d`.

> **Corrected 10 September 2026.** The first version of this note attributed
> the whole 0.433 % to renderer anti-aliasing. That was wrong, and the
> correction is below: all but 17 of those pixels are a **deliberate colour
> change from packet F3** that the golden was never re-recorded for. The
> re-record was still the right action; the reason on the record was not.

## Why the golden failed

`FoundationGoldenTest.galleryMatchesCommittedApi29Golden` failed on every
hosted run since the profile was matched (#199) with the same numbers:

```
7091/1635795 pixels (0.433%), bounds=[84,664..858,1321]
```

## What the diff actually is

The previous baseline was committed on **2 September 2026** (`0063de6`).
On **3 September** packet F3 (`6787b17`, *"F3: make gym captions readable
and split disabled ink"*) raised `TextTertiary` from `#5F6B73` to `#7F8B93`
because the old grey was 3.2:1 under every unit label. `MetricCluster`
draws its `PRIMARY` / `SECONDARY` labels with
`Kicker(label, color = TextTertiary)` (`ui/components/GymSurfaces.kt`), and
the gallery has two of those cards. The golden was never re-recorded for
that change, so every run since has been comparing new ink against an old
screenshot.

Decoded both PNGs and compared them pixel by pixel here (pure-Python PNG
decode, no tolerance):

| Region | Pixels | What it is |
|---|---|---|
| The two label bands (y 640–705 and 1095–1160) | **7,074** | the `PRIMARY` / `SECONDARY` labels — 3,934 of them exactly `#5F6B73` → `#7F8B93`, the remaining 3,140 anti-aliased blends of that same pair |
| The Volt button's rounded corners (y > 1160) | **17** | renderer edge coverage, ±1 level on one channel |

So 99.8 % of the diff is F3's intended contrast fix, and the label text is
now the colour the app actually ships. Font hinting and geometry are ruled
out: every other text line and every card corner in the capture is
pixel-identical, and the capture is the same 945 × 1731 as before.

## Decision

Re-recorded from the lane's own capture, byte for byte, naming F3
(`6787b17`) as the intended visual change per this document's own rule that
an accepted PNG must name it. The comparator stays exact — zero differing
pixels, no tolerance added.

The lane is also now the reference renderer: it is what runs on every pull
request, so a desk emulator with another GPU can differ from it by a level
or two on tracked small caps and rounded corners.

## Residual: the lane is not bit-stable

Two runs of the *same* commit (#212, runs 34431444016 and 34431566851)
disagreed by **17 pixels**, each off by exactly one level in one channel,
all on the Volt button's rounded corners:

```
17/1635795 pixels (0.001%), bounds=[84,1183..850,1321]
```

The first run passed and the second failed. SwiftShader's edge-coverage
rounding is not reproducible run to run, so an exact comparator flakes on
this golden roughly half the time.

**Decided, 10 September 2026:** the comparator now treats a difference of at
most one level per channel as the same colour, capped at 256 such pixels, and
is otherwise exact. See [VISUAL_TESTING](../VISUAL_TESTING.md) for the rule
and the table of what still fails. The recolour documented above would fail
under it on 6,954 pixels; the seventeen would not.
