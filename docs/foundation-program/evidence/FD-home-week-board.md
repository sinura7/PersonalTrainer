# Home week board — evidence

Packet: Home selectable week bound to occurrences, Plan fill-the-day,
Add session as the Volt, Tune / New gone, reminder prefs on Settings,
hour on add, Core auxiliary pack.

## What changed

- Home shows a week strip. Selected day drives the masthead and the
  board. Completing planned blocks paints rest / none / some / all
  (Danger / Warn / check), not Volt-green cells
  ([ADR-017](../../architecture/ADR-017-home-week-board.md)).
- Saturday no longer inherits a leftover routine named Friday. Captions
  come from dated occurrences.
- Plan strip selects a civil day and lists that day's blocks. Add
  session is the filled act. Tune and header New are gone. Library
  stays. Lighter week is a quiet chip.
- Reminder opt-out / quiet hours live on Settings. Adding a Plan block
  may set an hour. Reminders still fire from stored hours (ADR-012).
- Auxiliary packs: Stretch, Lower back, Hips, Holds, Core. Existing
  catalog ids only.

## Commands

- `tools/preflight.sh` — PASS. 1018 domain tests. 0 authority findings.
- `./gradlew testDebugUnitTest` — 1484 tests, 0 failures.
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone judges Temper Debug via Obtainium.
- Physical TalkBack is still outstanding.
- A sixth tab, or Library as a tab, still needs a new ADR.
