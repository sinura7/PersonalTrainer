# In-set micro-rec — evidence

Packet: deterministic next-load / next-reps on the live log, same line on
the rest floor. Use fills the draft only.

## What changed

- Compact `Next: 100 kg × 5 · RPE 8` pinned above Log. Use is Volt text,
  not a second filled button. Why is `RuleTraceCopy` (`ruleId = "micro-rec"`).
- `SetMicroRecCalculator.suggest` locks the 16 v1 rows. In-session
  hit-target repeats; `ProgressionCalculator` +step stays next session,
  except loaded RPE 6–7 in-tank and bodyweight +1.
- Null RPE is unknown, never in-tank. Assist miss adds assist. Lighter
  week never climbs. Editing hides the line. Lift done hides Use and
  invents no back-off. Preview (`If you log this: …`) has no Use.
- After a log, today's hold of weight×reps stays. Use is not auto-tapped.
- Rest floor `sessionTargetLine` is the same `SetMicroRecCopy.line`. No
  Use on the floor. Same `workoutMicroRec` inputs builder as the log.
- ProgressionStrip stays session-grain. Home `RecommendationEngine` and
  `ProgramDose` are unchanged.

## Commands

- `tools/preflight.sh` — OK (967 domain tests)
- `./gradlew testDebugUnitTest` — 1415 tests, 0 failures
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone journey (`ActiveWorkoutJourneyInstrumentedTest`) asserts the
  Next line on the rest floor and `workout-micro-rec` after skip/close.
  Run on Temper Debug, not gym-floor Temper.
- Resume-to-Rest-page, ±15 on the log bar, RPE-based rest seconds,
  optional back-off copy after lift done, and cardio rest are later.
- LLM-as-author, auto-apply after log, and in-session +step after one
  hit (except the locked in-tank / bodyweight +1 cases) stay won’ts.
