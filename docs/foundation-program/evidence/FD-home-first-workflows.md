# Home-first workflows — evidence

Packet: first visit opens Home; generate / build / workout; research-aligned dose.

## What changed

- Launch gate maps a readable settings store to `APP` even when
  `onboardingComplete` is false. The questionnaire is `Route.Onboarding`.
- Home shows `GetStartedSheet` until a plan or custom week is accepted.
- `ProgramDose` sizes generated sets/reps from age, goal, and days.
  Hand-added lifts still use `AddDefaults` alone.
- `RoutineGenerator` emits `RuleTrace.forGeneration`. Strength templates
  keep compounds in the slots.

## Commands

- `tools/preflight.sh`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Known limitations

- Physical TalkBack is still outstanding (P9.7).
- Overlay plates on Body still will not pixel-match every still seam.
- Suggest on Plan still matches existing routines; it does not re-author
  a catalog week.
