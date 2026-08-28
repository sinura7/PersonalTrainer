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

## Commands

- `tools/preflight.sh`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `tools/render-artifacts.sh`

## Known limitations

- Two-a-day morning + afternoon is already pinnable on Plan (ADR-007).
  This packet does not invent a second slot on each training day.
- Reminders stay ADR-012. No new nag path.
- Flexibility / isolated tendon goals are the Resilience goal plus the
  Hyper Pro stretch and nordic rows, not four new `TrainingGoal` values.
