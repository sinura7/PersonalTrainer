# Silhouette imagery — 27 Aug 2026

One plate language for Library thumbs and the Body figure. Not a PNG pack.

The owner locked an 18-still style bible (faceless athlete, pit, hairline
seams, Heat3 primary / muted secondary). This packet traces those stills
into Compose vertices. The stills are the quality bar, not shipping art.

## Why not generated bitmaps

The stills drift per prompt, fight Instrument (ADR-005), and 101 of them
bloat the APK. Phase 8 already chose Compose-drawn plates. Live Body heat
needs unlit geometry plus a fill, which a raster cannot do.

## What shipped

- **Body tab.** Standing `drawTemperFigure` plates densified to the unlit
  front/back stills: six-pack, three quad heads, shin plates, split traps.
  Live weekly heat is unchanged. `BodyMap.kt` was not forked — it is owned
  on the further-design vehicle.
- **Library / picker / detail header.** Family poses retarget to stills
  03–16 (front, rear, or 3/4 — both limbs stay in frame). Working plates
  at fixed Heat3. Kit in the silhouette: bar, bells, L-chair, hip bench,
  pull-up bar. The corner badge remains the second equipment read.
  Customs with no family keep the standing figure.
- **Coverage.** All 39 `MOVEMENT_FAMILIES` map to a non-standing pose.
  Every built-in lights its primary on that pose.

## What this is not

Commissioned line-art, Coil, WebP, VectorDrawable packs, or writing
`imageKey` on catalog rows. `imageKey` stays null and is still only ever
read inside `ExerciseThumb`. Instrument plates will never be a screenshot
of the stills.

## Gate (this packet)

- `./gradlew testDebugUnitTest` — pending this turn
- `./gradlew assembleDebug` — pending this turn
- `tools/preflight.sh` — pending this turn

Phone / emulator judging stays owner-side. Studio preview sheet is
`ExerciseThumbGallery`. SVG dumps: `silhouette-board.svg`,
`body-figure-board.svg`.
