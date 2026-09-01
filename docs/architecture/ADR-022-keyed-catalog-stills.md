# ADR-022 — Keyed catalog stills

- **Status:** Accepted
- **Date:** 1 September 2026
- **Supersedes:** the silhouette-imagery constraint that catalog
  `imageKey` stays null and that Library thumbs are family stills
  only ([silhouette-imagery.md](../foundation-program/evidence/silhouette-imagery.md));
  DESIGN_AUDIT L-01 only the reading that `imageKey` ships null as the
  complete shipping state
- **Related:** [ADR-005](ADR-005-instrument-identity.md);
  [ADR-010](ADR-010-schema-reset-migrations.md);
  [ADR-021](ADR-021-home-start-and-day-add.md) (gym-station photo
  portfolios stay deferred); DESIGN_AUDIT §8; owner request 1 September
  2026 (Brokenout stills, one per catalog lift)

## Context

Library thumbs used an 18-still family pack keyed by `LiftPose`. Every
bench press looked like every other bench press. The owner generated
one still per built-in lift (`Y:\My Drive\1 Personal\temper\Brokenout`,
129 files named `ex_<frozen id with hyphens → underscores>.png`) and
asked the app to use them accordingly.

`Exercise.imageKey` already exists on the v2 schema. It was left null.
Writing it is a catalog bump, not a Room bump. Gym-station photo
portfolios (several photos per lift, camera, backup of those files)
remain a later packet: they need schema, camera, and backup work this
record does not authorize.

## Decision

1. **One keyed still per built-in lift.** `imageKey` is the frozen
   catalog id with hyphens turned to underscores. That string is the
   drawable name. Example: `ex-barbell-back-squat` →
   `ex_barbell_back_squat` → `R.drawable.ex_barbell_back_squat`.

2. **Catalog version 7 writes the keys.** No new lifts. The seeder
   upserts `imageKey` onto built-in rows when the stored version is
   behind. A custom sitting on a built-in id is still left alone. Other
   customs keep whatever `imageKey` they already have (usually null).

3. **`ExerciseThumb` reads the keyed still first.** Unknown or blank
   keys fall back to the family still, then the unlit figure of the
   settled view. Equipment badge stays the second read. A missing
   bitmap never blocks logging.

4. **Family stills and Body stills stay.** The 18-still pack remains
   the fallback. Body live heat still paints on the unlit front/back
   stills. Warm-up extras reuse catalog ids, so they reuse these stills.

5. **No Coil, no schema bump, no gym-station portfolios.** Built-ins
   are `res/drawable-nodpi` WebP. User-taken station photos are not this
   packet.

## Consequences

- Packets do not put family stills back in front of a known
  `imageKey`.
- Packets do not invent `TrainerDatabase` v3, Coil, or a catalog seed
  of new lifts in order to show pictures.
- Customs without a key keep the family / unlit fallback. That is
  complete.
- Gym-station photo portfolios stay deferred (ADR-021).

## Review questions

- Does every built-in have its own still? Yes. 129 keys, 129 WebPs,
  catalog version 7.
- Do customs get a catalog still? No. Family / unlit fallback.
- Does this bump Room? No. `CATALOG_VERSION` only.
- Does Body change? No. Unlit / heat stills are unchanged.
- Are gym-station photo portfolios in this packet? No.
