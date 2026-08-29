# Settings / Home trim — evidence

Packet: Display (lbs/kg, Regular/Military), questionnaire-shared schedule
and coaching, weekly weigh-in, grouped equipment that filters generated
weeks, reminders on Plan, Goals UI gone, Home is Start.

## What changed

- Settings opens on compact weight and hour chips ([ADR-016](../../architecture/ADR-016-settings-home-trim.md)).
- Re-running setup seeds from `storedOnboardingAnswers()`. Shrinking
  training days trims `preferredDays`.
- Weekly bodyweight check-in defaults to the first training day. Home
  asks when due. No second exact-alarm path.
- Equipment is grouped. `CoachPreferences.allows()` filters generated
  weeks and recs.
- Reminders and session hours live on Plan Tune / the day page.
- Goals UI is unwired. Room tables stay.
- Home no longer repeats This week, Goals, Library, or a calendar strip.

## Commands

- `tools/preflight.sh` — PASS. 1011 domain tests. 0 authority findings.
- `./gradlew testDebugUnitTest` — 1477 tests, 0 failures.
  `ActiveWorkoutViewModelTest.logSetPersistsSetClearsErrorAndEmitsRecord`
  timed out once in an earlier full-suite run; rerun of that test alone
  passed, and a later full suite was green.
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone judges Temper Debug via Obtainium.
- Goals Kotlin files remain on disk, unwired. Schema freeze.
- A sixth tab, or Library as a tab, still needs a new ADR.
