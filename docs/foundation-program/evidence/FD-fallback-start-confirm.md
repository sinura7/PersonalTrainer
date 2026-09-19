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
- `./gradlew testDebugUnitTest` / `assembleDebug` — PASS (see
  `docs/archive/handoffs/HANDOFF-2026-08-29.md` Status). Live test 13 is cut as
  pre-release `debug-live-13`.

## Known limitations

- Phone judges Temper Debug via Obtainium.
- Physical TalkBack is still outstanding.
- Live test 13 is `debug-live-13` (`PersonalTrainer-1.0.0-debug.apk`).
