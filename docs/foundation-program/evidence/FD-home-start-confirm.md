# Home start confirm — evidence

Packet: Home planned rows and the Volt open a session-summary confirm.
Confirm starts that occurrence. The start tag prefers a workout over
an auxiliary pack.

## What changed

- Planned Home rows are tappable, including the Volt-tagged row
  ([ADR-018](../../architecture/ADR-018-home-start-confirm.md)).
- Tap (row or Volt) opens `Start {title}?` with clock, kind, and the
  numbered lift order (or Ready). Confirm starts. Cancel does not.
- Volt prefers a non-aux planned workout over Stretch. Stretch stays
  startable from its row.
- Free workout stays quiet. Notification pending-start still starts.
- Plan still does not Start. No schema bump. No catalog seed.

## Commands

- `tools/preflight.sh` — PASS. 1025 domain tests. 0 authority findings.
- `./gradlew testDebugUnitTest` — 1491 tests, 0 failures.
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone judges Temper Debug via Obtainium.
- Physical TalkBack is still outstanding.
- Leftover `ThisWeekCard` (empty agenda) still starts from its own Volt.
