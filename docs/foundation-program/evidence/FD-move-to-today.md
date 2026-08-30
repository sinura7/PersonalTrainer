# Move leftover sessions to today — evidence

Packet: a leftover Home session (yesterday’s Friday, a missed block)
confirms as **Do it today**, which relocates it onto today then starts.
Today lists earlier leftovers under **Still open**. Recurrence does not
change.

## What changed

- Leftover `PLANNED` / `MISSED` occurrences (civil day before today)
  confirm as `Do {title} today?` / `Do it today`
  ([ADR-019](../../architecture/ADR-019-move-to-today.md)).
- Confirm vacates the old day as `MOVED` and mints a `PLANNED` row for
  today, then starts that row. Cancel does not move.
- When Home is on today, leftovers from this week and the previous
  week sit under **Still open**. Volt still prefers a planned block on today.
- Same-day planned rows keep ADR-018 `Start`. Week-level missed-work
  (Keep the dates) is unchanged. Plan still does not Start.

## Commands

- `tools/preflight.sh` — PASS. 1038 domain tests. 0 authority findings.
- `./gradlew testDebugUnitTest` — 1506 tests, 0 failures.
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone judges Temper Debug via Obtainium.
- Physical TalkBack is still outstanding.
- Leftover `ThisWeekCard` (empty agenda, no Still open) still starts
  from its own Volt.
