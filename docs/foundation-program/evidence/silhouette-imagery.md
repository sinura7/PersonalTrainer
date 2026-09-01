# Silhouette imagery — 27 Aug 2026

> **Superseded in part on 1 Sep 2026.** Catalog `imageKey` is no longer
> null. One keyed still per built-in lift
> ([ADR-022](../../architecture/ADR-022-keyed-catalog-stills.md),
> [FD-keyed-stills.md](FD-keyed-stills.md)). The 18-still family pack
> remains the fallback. Body still uses the unlit/heat stills. The
> “not 101 catalog PNGs / `imageKey` stays null” line below is
> historical for *this* packet.

Library thumbs are the locked 18-still pack. Those pictures ship. They are
not traced into a second drawing.

Body is the same person, with live colour. The unlit front/back still is
the figure; this week's (or 30 days') load is a wash on trained plates.
Rest stays the photograph. There is no day chip.

## What shipped

- **18 WebP stills** in `res/drawable-nodpi/` (~390 KB). One per lift family
  plus unlit and demo-heat front/back. Catalog `imageKey` stays null; the
  pack is keyed by `LiftPose` / `BodyView`.
- **Library / picker / detail header / Temper mark.** `ExerciseThumb` and
  `TemperMark` draw the stills. Equipment badge stays the second read.
  Customs with no family stand on the unlit still of the settled view.
- **Body tab.** `drawTemperFigure` blits the unlit still (bound once from
  `PersonalTrainerApp`) and SrcAtops `heatColor` onto trained plates only.
  Structure, rest, and `HeatEmpty` stay the still. A missing still falls
  back to steel plates so JVM tests keep an exact map. `BodyMap.kt` is
  owned on the further-design vehicle and was not forked. Windows remain
  This week and Last 30 days.

## What this is not

101 catalog PNGs. Coil. Writing `imageKey` on every row. A third Body
window. Overlay plates that pixel-match every ChatGPT muscle seam — the
still is the person; heat is a regional wash.

## Gate (this packet)

- `./gradlew testDebugUnitTest` — 1341 tests, 0 failures
- `./gradlew assembleDebug` — pass
- `tools/preflight.sh` — OK; 922 domain tests; named-args 0 mismatches
