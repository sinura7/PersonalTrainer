# Log → Next, extra sets, RPE targets, lock-screen rest — evidence

Packet: prescribed sets done turns Log into Next. A + after the last
recorded set logs extra. Selected RPE retargets reps and weight from
this session and last time. Rest countdown sits on the lock screen.

## What changed

- `WorkoutAdvance.liftComplete` / `nextExerciseId` decide when the dock
  Volt is Next. `LOG_SET` stays on Log so one set on a 5-set lift does
  not become Next. Last lift: Finish stays in the header; Log stays
  until they leave. + is secondary (`ADD_SET`), never Volt.
- Extra set is UI state (`wantAnotherSet`), not a database flag. Micro-rec
  `allowExtra` skips `LIFT_DONE` so extras still get a next load. Extra
  logs start rest; the last prescribed set still does not.
- Selected RPE is intent for *this* set (`rpeIntent`), computed from last
  working / the first-set hint, not a preview of the draft wells. Wells
  fill on RPE select after a working set exists. Auto-apply after a log
  stays a won’t. Locked v1 calculator rows are unchanged.
- `WorkoutCopy.setProgress` can take live reps/weight from the rec so
  the header tells the lifter how many to do at the selected RPE.
- Running rest channel is `rest_timer_running_v2` at IMPORTANCE_HIGH,
  silent, public, with a countdown chronometer. SystemUI draws the lock
  screen clock; the service does not re-post every second. Overlay rest
  and a 240 dp ring on the log stay won’ts.
- `RestLockActivity` is `showWhenLocked` + `turnScreenOn`. Tap the
  running notification to see the ring over the lock screen. Rest-done
  uses a full-screen intent to that activity. `USE_FULL_SCREEN_INTENT`
  is declared; it is not requested at onboarding.

## Commands

- `tools/preflight.sh`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Known limitations

- Phone judges Temper Debug via Obtainium. Do not run connected tests
  on gym-floor Temper.
- Lock-screen chronometer needs the HIGH public channel; a phone that
  still has the old LOW channel is migrated on `ensureChannels`.
- Android 14 may demote rest-done full-screen intent until the user
  grants `USE_FULL_SCREEN_INTENT` in settings. The chronometer and the
  exact-alarm cue still fire.
- Journey still asserts `Type a weight`, `Log 100 kg × 5`, rest-floor
  `CLOCK`. One log on a multi-set lift must keep `LOG_SET`.
