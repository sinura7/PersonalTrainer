# Silhouette imagery — 27 Aug 2026

Library thumbs are the locked 18-still pack. Those pictures ship. They are
not traced into a second drawing.

## What shipped

- **18 WebP stills** in `res/drawable-nodpi/` (~390 KB). One per lift family
  plus unlit and demo-heat front/back. Catalog `imageKey` stays null; the
  pack is keyed by `LiftPose` / `BodyView`.
- **Library / picker / detail header / Temper mark.** `ExerciseThumb` and
  `TemperMark` draw the stills. Equipment badge stays the second read.
  Customs with no family stand on the unlit still of the settled view.
- **Body tab.** Live weekly heat still paints on `drawTemperFigure`. That
  screen is a map of this week's work, which a baked still cannot do.
  `BodyMap.kt` is owned on the further-design vehicle and was not forked.

## What this is not

101 catalog PNGs. Coil. Writing `imageKey` on every row.

## Gate (this packet)

- `./gradlew testDebugUnitTest` — pending this turn
- `./gradlew assembleDebug` — pending this turn
- `tools/preflight.sh` — pending this turn
