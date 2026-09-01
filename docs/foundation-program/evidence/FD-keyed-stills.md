# Keyed catalog stills — evidence

Packet: one still per built-in lift, keyed by `imageKey`.

## What changed

- 129 WebP stills in `res/drawable-nodpi/`, named from the frozen
  catalog id with hyphens turned to underscores. Source:
  Drive `1 Personal/temper/Brokenout`. Codex-dated junk and the
  duplicate `06. ex_bodyweight_squat.png` were ignored.
- `SeedExercise.imageKey` defaults to that drawable name.
  `CATALOG_VERSION` 7 writes it onto built-in rows. Customs keep
  null and fall back to the family still.
- `ExerciseThumb` reads `keyedArtwork` first, then `artworkFor`.
- Family pack and Body unlit/heat stills stay. No schema bump,
  no Coil, no gym-station photo portfolios.

## Commands

- `tools/preflight.sh` — OK (1070 domain tests)
- `./gradlew testDebugUnitTest` — 1601 tests, 0 failures
- `./gradlew assembleDebug` — SUCCESS
- Obtainium drop: live test 17, tag `debug-live-2026-09-01-2`,
  versionCode 17 (`1.0.0+debug.17`)

## Known limitations

- Customs have no keyed still unless a later packet attaches one.
- Gym-station photo portfolios (camera, several photos per lift,
  backup of those files) wait on schema work ADR-021 deferred.
- Phone check: confirm Hyper Pro reverse hyper vs elephant walk
  read as the named lifts. Wiring follows the filenames.
