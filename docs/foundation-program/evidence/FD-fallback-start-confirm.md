# Empty-agenda leftover Volt start confirm — evidence

Packet: `ThisWeekCard`'s **Start this session** Volt opens the same
session-summary confirm the agenda card uses (ADR-018, amended). It was
the one Home start left that jumped straight into the log. Confirm
starts it. Cancel does not.

## What changed

- `HomeToday.fallbackStartConfirm(day, routines)` builds the summary
  for the slot week's derived session: the numbered lift order and the
  lift-count / about-minutes line, or `No lifts yet`. No clock line —
  a slot day is untimed — and never a leftover move; the session starts
  on today as before.
- The Volt tap sets a pending flag; the `ConfirmActionDialog` confirm
  calls the existing start path (`startSuggestedDay`). Cancel, back, or
  tap-outside dismisses without starting. Going live, logging today, or
  losing the training day clears the pending confirm.
- The empty-week Suggest / Replay / free-workout paths are unchanged.
  The agenda card is untouched.

## Commands

- `tools/preflight.sh` — PASS. 1040 domain tests. 0 authority findings.
- `./gradlew testDebugUnitTest` / `assembleDebug` — **not runnable from
  this environment**: the network policy blocks `dl.google.com`, so AGP
  and the Android SDK cannot resolve, and hosted CI has no assigned
  runner. The Gradle gate and the live test 13 APK cut belong to the
  next Gradle-capable lane (Cursor or the owner's machine).

## Known limitations

- Phone judges Temper Debug via Obtainium.
- Physical TalkBack is still outstanding.
- The `debug-live` pre-release for `debugLiveCode` 13 is not yet cut —
  see Commands above.
