# Audit hygiene — leftover Volt, one-live lock, query trust, backup tuple

- **Implementation commits:** `32fcdf7` `032df0f` `2096c76` `ed81201` `c448497` `dbf5c1d` `f21e81d` `83fee61` `60b8598`
- **Evidence date:** 26 August 2026
- **JVM host:** Cursor Linux VM, JDK 21 (source/target 17)
- **Application:** `com.sinura.personaltrainer.debug`

Stacked on the further-design vehicle (`cursor/frontend-further-design-81e1`,
PR #76). Merge the tip only. Independent of #75 except pointer lines in
`docs/ROADMAP.md`.

## Behavior contracts

**A. Status is not the act.** Lighter-week captions, Plan/History "moved
most" deltas, Tune Done, and a met goal snapshot use `TextSecondary` /
`TextPrimary`. Dialog confirms, rest-alert recovery, and spinners stay
Volt. A noon-stamped receipt (start == end, no cardio clock) omits the
duration tile. `RestControl` has no emphasised fill. Start-sheet loading
uses `ScreenLoading()`. Goals header Cancel is not Volt; Save is the
filled act.

**B. One live activity.** `ActivityRepository.confirm` / `discard` /
`completeLive` take the same `DbMaintenance` lock as strength starts.
`insertSessionIfIdle` re-checks `activityDao().getLive()` inside the Room
transaction. Live lookup is `liveToken = 'LIVE'`. `confirm` feeds
`ActivityRules` the live row, not every graph. Day reads use
`graphsOnLocalDate`. History / Goals / insights subscribe to completed
summaries; detail and backup still load full graphs. Insights SQL still
windows 30 days at subscribe; `windowedInsightHistory` filters with a
fresh `nowMs()` so aged sessions fall out without a spinning delay.
Restore `refuseIfLive` and Settings `sessionLive` cover both a live
strength row and a live cardio row.

**C. Preflight and backup names.** Named-args does not judge Compose
`path(fill=…)` against `OnboardingStep.path`. `backup_rules.xml` and both
sections of `data_extraction_rules.xml` name `temper.db` plus journal /
wal / shm. `check-backup-policy.py` ratchets that path.

**D. Screen ViewModels.** `GoalsViewModelTest`,
`ActivityDetailViewModelTest`, and `LiveCardioViewModelTest` assert
add/pause/delete, missing/noon/cardio duration, and finish/discard
against in-memory Room. `AppViewModel` remains the only production
`*ViewModel.kt` without a dedicated suite.

**E. Bodyweight backup four-tuple.** Encode writes
`epochDay:kg:recordedAtMs:offsetSeconds:zoneId`. Two-field `epochDay:kg`
strings from older exports still decode. Zone stays last so it may
contain colons.

Physical TalkBack remains P9.7 / owner-side. Phone check is
`origin/trunk` after squash-merge. Hosted GitHub runners are not the
test lane. P10 KMP and P11 sync stay gated.

## Commands and results

| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest --offline --rerun` | PASS, 1358 tests, 0 failures, 0 errors, 0 skipped, 100% successful, 14.872s |
| `./gradlew assembleDebug --offline` | PASS, `PersonalTrainer-1.0.0-debug.apk` |
| `./tools/preflight.sh` | OK. Named-args 0 mismatch(es) (main 304 files, test 205 files). Backup policy 0 findings. Domain lane 940 tests OK. Play rehearsal static OK; BLOCKED physical TalkBack / Public Candidate / Play upload. |
| `./gradlew lintDebug` | Not a lane for this train: androidTest classpath verification of `kotlinx-coroutines-bom`. `testDebugUnitTest` + `assembleDebug` are the push gate. |

## Finding disposition

Scan findings 1–8 and 10 from the 26 Aug 2026 full-development audit are
closed in code on this vehicle. Finding 6's hourly cutoff loop was
withdrawn: it starved other JVM tests under the full suite; the
in-memory window is the durable answer.

QC follow-up `60b8598`: restore no longer wipes live cardio; Settings
restore-blocked watches `observeLive()` as well as in-progress strength.

Still named, not this vehicle: composer authorable time/zone (ADR-011
§3), `workout_sessions` captured-zone column, Library "lift" wording,
owner physical TalkBack, P12.4 Play rehearsal, P10, P11.
