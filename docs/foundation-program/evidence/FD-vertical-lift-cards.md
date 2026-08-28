# Vertical lift cards — evidence

Packet: builder and live log stack lifts as full-width vertical cards.
Rest floor wraps the remaining clock in a countdown ring.

## What changed

- `SessionLiftStrip` is a vertical `Column` of full-width cards, not a
  156 dp `LazyRow`. Tap expands that card for sets, reps, rest, and
  load. Tap again collapses. Parent lists still scroll vertically.
- Live workout replaces `LiftSwitcher` chips with the same stack.
  Tap selects and expands logging (last time, progression, set entry,
  warm-up/RPE, logged sets) inside that card. A second tap does not
  collapse the open lift. Add a lift is a secondary button; Log stays
  the one Volt.
- Rest floor (`session/{id}/rest`) draws a 280 dp `RestSweepRing`
  around `numeralHero` remaining time. Sweep uses
  `RestTimer.sweepFraction` (full at start, empty at zero), shared
  with the log bar’s linear track. `RestFloorTags.CLOCK` stays on the
  numeral. The log dock is unchanged: condensed bar + linear track.
- Overlay rest and a 240 dp ring on the log stay won’ts.

## Commands

- `tools/preflight.sh` — pending this packet
- `./gradlew testDebugUnitTest` — pending this packet
- `./gradlew assembleDebug` — pending this packet

## Known limitations

- Phone judges Temper Debug. Do not run connected tests on gym-floor Temper.
- Journey still asserts `Type a weight`, `Log 100 kg × 5`, rest-floor
  `CLOCK`, and the live bar. Expanded selected lift keeps `SET_ENTRY`
  in the same `LazyColumn`.
- Instrument / ADR-005 unchanged. Micro-rec calculator unchanged.
