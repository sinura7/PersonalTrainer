# Silhouette imagery — 26 Aug 2026

One plate language for Library thumbs and the Body figure. Not a PNG pack.

## Why not generated bitmaps

`GenerateImage` can make photoreal clay renders. They drift per prompt, fight
Instrument (ADR-005), and 101 of them bloat the APK. Phase 8 already chose
Compose-drawn plates. This packet keeps that decision and raises the density.

## What shipped

- **Body tab.** Same `drawTemperFigure` plates. Quadratic corners plus a denser
  skull so the standing figure reads as high-definition anatomy at every
  density. Live weekly heat is unchanged. `BodyMap.kt` was not forked — it is
  owned on the further-design vehicle.
- **Library / picker / detail header.** A known `movementKey` draws a family
  pose with working plates at fixed Heat3 and the kit in the silhouette
  (bar, bells, cable stack, smith posts, machine frame). The corner badge
  remains the second equipment read. Customs with no family keep the standing
  figure.
- **Coverage.** All 39 `MOVEMENT_FAMILIES` map to a non-standing pose. Every
  built-in lights its primary on that pose.

## What this is not

Commissioned line-art, Coil, WebP, VectorDrawable packs, or writing `imageKey`
on catalog rows. `imageKey` stays null and is still only ever read inside
`ExerciseThumb`.

## Gate (this packet)

- `./gradlew testDebugUnitTest` — 1329 tests, 0 failures
- `./gradlew assembleDebug` — pass
- `tools/preflight.sh` — OK; 922 domain tests; named-args 0 mismatches

Phone / emulator judging stays owner-side. Studio preview sheet is
`ExerciseThumbGallery` (row, header, pose families, standing Body figure).