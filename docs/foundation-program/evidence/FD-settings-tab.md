# Settings tab — evidence

Packet: Settings is the fifth tab. The gear leaves Home and Plan. Library
and Goals stay pushed. Instrument stays.

## What changed

- Tabs are Home · Body · Plan · History · Settings ([ADR-014](../../architecture/ADR-014-settings-tab.md)).
- Home masthead and Plan header have no Settings gear.
- Settings is a tab destination: the bar stays, Back is gone, Export
  remains the Volt act.
- `TemperIcons.Settings` is a plate-language cog, not Material's gear.

## Commands

- `tools/preflight.sh` — PASS. 992 domain tests. 0 authority findings.
- `./gradlew testDebugUnitTest` — 1453 tests. `ShippingTabsTest` and
  `AccessibilityMatrixTest` pass. Known flake
  `ActiveWorkoutViewModelTest.logSetPersistsSetClearsErrorAndEmitsRecord`
  timed out in the full suite; rerun of that test alone passed.
- `./gradlew assembleDebug` — SUCCESS

## Known limitations

- Phone judges Temper Debug via Obtainium.
- A sixth tab, or Library/Goals as a tab, still needs a new ADR.
