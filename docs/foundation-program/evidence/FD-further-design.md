# FD further-design train — composer, one-Volt, copy, signature moments

- **Implementation commits:** `c42f49d` `e5d0d78` `773f084` `8dead00` `339973a`
- **Evidence date:** 26 August 2026
- **JVM host:** Cursor Linux VM, JDK 21 (source/target 17)
- **Application:** `com.sinura.personaltrainer.debug`

## Behavior contracts

**FD.1.** Composer Save is the pinned Volt above the system nav inset.
Activity summary is a celebration receipt. Live bar copy follows
session kind; cardio does not print a zero-sets cluster.

**FD.2.** Log is the filled Volt on the active workout. Start rest and
Skip are Surface2 hairline. Plan recovery Volt is quiet while missed-work
Keep the dates is up. Summary "Workout complete", Why ink, and
block-complete fill are not Volt.

**FD.3.** Expanded Why uses `RuleTraceCopy` labels, never `reasonCodes`
keys. History chips caption as totals-only. Calendar heat is month-relative
sets; Body legend is windowed muscle load. Bodyweight lifetime tile prints
reps. Cardio onboarding skips Experience, Goal, and Emphasis.

**FD.4.** Reminders switch is on when opted in. Quiet hours are editable.
Notification recovery matches the rest-alert path. Goals Save is filled
Volt. Delete confirms.

**FD.5.** Compact rest dock stays. Finished rest flashes gold and pulses
in the last ten seconds. PR banner has radial glow and hairline flash.
Logged sets are one `GroupedList` with cyan warmup and Volt rail. e1RM
draws a dashed `PrGold` line and a one-shot draw-in gated by
`LocalReducedMotion`. Home agenda is un-nested; Start sits below the
group. Week strip marks two-a-day days.

Physical TalkBack remains P9.7 / owner-side. Phone check is `origin/trunk`
after squash-merge. Hosted GitHub runners are not the test lane.

## Commands and results

| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest` | PASS, 1340 tests, 0 failures, 0 errors |
| `./gradlew assembleDebug` | PASS |
| `./tools/preflight.sh` | Static checks PASS. `check-named-args` FAIL on `TemperIcons.kt:36` `path(fill=…)` — unchanged from `trunk`, not this train. |
| `./gradlew lintDebug` | Not a lane for this train: configuration-cache / androidTest classpath verification failed on `kotlinx-coroutines-bom-1.9.0.pom`. `testDebugUnitTest` + `assembleDebug` are the push gate. |
