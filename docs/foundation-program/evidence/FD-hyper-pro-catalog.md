# Hyper Pro catalog — evidence

Packet: Freak Athlete Hyper Pro as a first-class kit.

## What changed

- `EquipmentType.HYPER_PRO` with its own Library chip and glyph.
- `TrainingPlace.HYPER_PRO` on the questionnaire. Mixes with gym, home,
  or bodyweight. A full gym does not imply the bench.
- Catalog v6 seeds the official 28-movement list from
  https://freakathlete.ca/pages/exercise-list.
- Generator slots accept nordic / reverse-hyper / GHR / reverse-nordic.
  Resilience goal leads with those patterns.
- Empty coach kit still means gym-floor: Hyper Pro stays filtered off
  until the place or Settings chip says otherwise.
- Couch stretch, elephant walk, and incline pigeon stay in Library
  (own families) so they do not steal generated compound slots.

## Commands

- `tools/preflight.sh` — OK (958 domain tests)
- `./gradlew testDebugUnitTest` — 1395 tests, 0 failures
- `./gradlew assembleDebug` — SUCCESS
- `tools/render-artifacts.sh` — `catalog-v6-review.md`,
  `onboarding-programs-review.md`

## Known limitations

- Two-a-day morning + afternoon is already pinnable on Plan (ADR-007).
  This packet does not invent a second slot on each training day.
- Reminders stay ADR-012. No new nag path.
- Flexibility / isolated tendon goals are the Resilience goal plus the
  Hyper Pro stretch and nordic rows, not four new `TrainingGoal` values.
