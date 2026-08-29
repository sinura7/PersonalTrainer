# Plan day blocks — evidence

Packet: Plan is a schedule workshop. A weekday is a pushed page of
untimed blocks (workout, cardio, auxiliary). Start lives on Home.
No Start Cardio, Swap, or Unpin. Catalog seed stays a won’t.

## What changed

- Tapping a weekday opens the Plan day page ([ADR-015](../../architecture/ADR-015-plan-day-blocks.md)).
- Add session is on the Plan tab (today, picker open) and is the Volt
  on the day page.
- Cardio types (Walk, Run / sprints, Ride, Row, Swim, Hike) store
  `cardio:{TYPE}` on `ScheduleRule.templateId`.
- Auxiliary packs (Stretch, Lower back, Hips, Holds) mint STRENGTH
  routines from existing catalog ids (`aux:{packId}`).
- Clocks stay in the model as defaults. The day page does not show them.
- Delete removes a block. Deleting the imported evening pin unpins that
  weekday. Logged work stays.

## Commands

- `tools/preflight.sh` — PASS. 1000 domain tests. 0 authority findings.
- `./gradlew testDebugUnitTest` — 1464 tests, 0 failures.
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone judges Temper Debug via Obtainium (live test 8).
- Reminders still fire from stored hours. Hiding clocks on Plan does not
  invent a second nag path.
