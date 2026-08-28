# Day stack — evidence

Packet: a weekday can hold morning cardio, a pinned workout, and a
later accessory / Hyper Pro session. The user follows the list. One
live activity at a time.

## What changed

- Plan day sheet **Add another session** mints a STRENGTH timed rule
  after the latest existing hour (20:00 after a typical 18:00 pin),
  bound to a picked routine or a new `{Weekday} extra` routine. Recurring
  on that weekday. **New session…** opens the editor so the lifts exist
  before Home Start.
- **Add morning cardio** stays. It still hides once a CARDIO rule exists
  on that day.
- **Remove a session** drops user-timed rules (cardio or later strength).
  The imported evening pin stays on Unpin. DONE rows stay as history.
- `syncSlotsToRules` only deletes imported slot-backed rules. Extra
  `rule-strength-…` rows survived pin / swap / unpin after this fix;
  they would have been wiped before.
- Home agenda already listed N rows. Copy is now “Each session stays
  its own.” One Volt still starts the first planned strength. Later
  accessory is tappable. Completing one does not start the next.
- Default hours are 07:00 cardio, 18:00 imported strength, +2 hours
  for each later session. No time-picker chrome this packet.

## Commands

- `tools/preflight.sh` — OK (985 domain tests)
- `./gradlew testDebugUnitTest` — 1445 tests, 0 failures
- `./gradlew assembleDebug` — SUCCESS (`PersonalTrainer-1.0.0-debug.apk`)

## Known limitations

- Hours are defaults, not a clock picker.
- Later sessions are STRENGTH (accessory / Hyper Pro). A second CARDIO
  later in the day is not a first-cut control.
- Phone judges Temper Debug via Obtainium. Do not run connected tests
  on gym-floor Temper.
