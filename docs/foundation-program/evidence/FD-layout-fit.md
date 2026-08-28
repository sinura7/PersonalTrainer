# Layout fit — evidence

Packet: cross-tab frontend fit. Instrument tokens stay. Not a restyle.

## What changed

- Gym buttons ellipsize. Confirm Why dialogs scroll and may omit Cancel.
- Live bar: `heightIn(min = rowMin)`, title ellipsis, resume tap on identity
  only (`live-session-bar` stays on that column). History session ⋮ is
  outside the row tap.
- Home `LinkRow` is a 48 dp row; label and trailing ellipsize.
  Goal name/progress ellipsize. Rec Why is a dialog, not a nested card tap.
- Plan day Volt sits under `GroupedList`. Day sheets skip half-expanded
  and scroll. Missed-work Move/Adapt/Skip are secondary buttons.
  Four-up metric clusters share width.
- Workout: Next line is one line. Why opens a dialog (tags unchanged).
  Lift name, rest clock, progression strip, set rows ellipsize.
  Numeral well dropped the always-on type hint.

## Commands

- `tools/preflight.sh` — OK (967 domain tests)
- `./gradlew testDebugUnitTest` — 1415 tests, 0 failures
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone judges Temper Debug. Do not run connected tests on gym-floor Temper.
- Log copy stays `Log 100 kg × 5`. Micro-rec calculator is unchanged.
- Light theme, fifth tab, overlay rest, and DIRECTION_A stay won’ts.
