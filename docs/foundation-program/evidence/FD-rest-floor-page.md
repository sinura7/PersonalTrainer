# Rest floor page — evidence

Packet: condensed live-log rest bar plus a pushed floor Rest page.

## What changed

- Running rest on Active Workout is a ~56 dp bar: kicker REST, `numeralMd`
  clock, 4 dp linear track, trailing Skip. No 88 dp ring, no −15/Skip/+15
  stack on the log.
- Idle is Next rest + planned clock + Start. Preset chips moved to the
  floor page.
- Tap bar or idle line pushes `session/{sessionId}/rest`. Close pops to
  the log; the timer keeps running. Finish stays on the log
  (`popUpTo Home`).
- One `RestTimerGateway` clock. Skip is never Volt. No Finish on Rest.
- Notification / live-bar resume `popUpTo` Active Workout so Rest cannot
  sit on top. Rest is in `LIVE_BAR_HIDDEN_ROUTES`.
- Floor page: Pit, huge remaining, linear track, exercise, last set,
  session-grain `ProgressionHint`. Running: −15 / Skip / +15. Idle:
  chips + Start. Finished: Volt “Back to the bar”.
- `RestTimer.shouldStartAfterLog` is unchanged.

## Commands

- `tools/preflight.sh` — OK (963 domain tests)
- `./gradlew testDebugUnitTest` — 1406 tests, 0 failures
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone journey (`ActiveWorkoutJourneyInstrumentedTest`) is written:
  bar not ring, opens floor, skip, finish. Run on Temper Debug, not
  gym-floor Temper.
- Resume-to-Rest-page, ±15 on the log bar, RPE-based rest seconds, and
  cardio rest are later packets.
- Overlay rest and a 240 dp ring stay won’ts.
