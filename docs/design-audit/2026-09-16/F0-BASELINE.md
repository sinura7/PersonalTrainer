# F0 — Native baseline and development lane

Implementation baseline: `20789157` (Temper Debug 78). Production UI is unchanged
by this packet. The owner's supplied screenshots have no embedded build identity;
they remain design evidence rather than proof of this exact source revision.

## What this packet establishes

- Dedicated repository-owned Windows Android profiles for API 26, 29 and 36.
- Native main-tab captures from real navigation, Room and a live workout.
- A separate explicit Windows SwiftShader 37 golden profile.
- A scanline comparator that avoids duplicating full images on the managed heap.
- The accepted programme and ADR-026, with `codex/` hosted branch coverage.

## Baseline findings

The first full API 29 run executed 100 tests and failed 16. Twelve failures were
out-of-memory errors in the full-frame comparison arrays. The bounded scanline
implementation preserves pixel counts, bounds and the existing rounding rule.
Its own token-change test caught and helped correct a total-pixel denominator
mistake during implementation; this is why recording and comparison are separate.

After memory repair, existing references revealed genuine stale drawings: the
committed workout PNGs show the older two-row rest controls, whereas source at
`20789157` uses a compact rest bar. These must not be described as antialiasing.
The stale timer changes are source commits `8a7955dd` and `f5acf68d`.
The gallery difference is localized to rounded-corner coverage on this Windows
renderer. No comparison threshold is increased. Legacy references are retained;
the new named renderer profile records the actual baseline before redesign.

Other stale test assumptions corrected here:

- Five-rep working sets prescribe 150 seconds of rest under the current domain
  rules; the test still expected the routine's 120-second value.
- The equipment prompt is rendered uppercase by Kicker, and the floor-core
  catalogue caption has changed. Assertions now target the displayed text.
- Long sheet content and enlarged target editors must be scroll-reachable,
  not all simultaneously above the fold. The isolated editor fixture now uses
  a scrollable host like its production parent.
- The current workout intentionally scrolls to saved history. Its recommendation
  remains reachable by scrolling; packet F2 replaces that movement with retained
  entry position and will update the interaction assertion accordingly.

## Evidence limits

The whole-device capture fixture uses the real civil clock. It proves actual
composition and navigation, not deterministic dates. Timed workout golden
fixtures still require a controlled clock in the workout implementation packet.
The legacy requested 360 × 800 dp golden viewport is physically constrained to
360 × approximately 659 dp; the recorded PNG is 945 × 1731 pixels. It does not
prove the required 360 × 800 layout. Full native captures include all system bars.

No owner phone has been targeted. Physical performance, TalkBack, Doze, haptics,
distribution-signer upgrade and Obtainium acceptance are still milestone gates.
API 26 and 36 provisioning is complete; their executed checks are recorded below
only after completion. Packet F1 owns the expanded component standard.

## Executed results

| Check | Result |
|---|---|
| API 29 full connected suite, comparison mode | 101 passed, zero failed/skipped |
| API 36 full connected suite | XML: 100 cases; 90 passed, 10 API-29-only goldens skipped; zero failures/errors |
| API 36 native capture after artifact hardening | Passed; persisted PNG hashes verified |
| Full local unit/build/lint/instrumented-source gate | Passed; static checks and fresh 2,536 unit tests executed, zero failures/skips |
| API 26 critical workout/navigation/alarm smoke | 5 passed, zero failed/skipped |
| Final API 29 suite and persistent artifact verification | 101 passed; five native captures inspected, matching manifest and SHA-256 retained |

The API 36 pre-31 alarm test is excluded by `SdkSuppress(maxSdkVersion=30)`;
its title, assertions and original documentation already restricted its contract
to pre-31 exact alarms. It is not evidence for Android 12+ grant/deny behaviour.
The API 36 full run still exercises the native workout, navigation, migration
and repository suites. Android runner progress logs double-count assumption
skips; the XML's 100 cases, rather than the console's 110 events, are the count.

The first API 26 fixture stalled before Activity launch. A thread dump showed
the instrumentation thread waiting on its seed coroutine; no startup exception
was logged. After stopping that run, the same fixture and all four other smoke
tests passed. Setup now has a 60-second timeout, cleanup 30 seconds, and stage
breadcrumbs. The intermittent cause remains unconfirmed; a green retry is not
presented as a root-cause fix. A recurrence must retain the stage and thread dump.

## Independent and adversarial review

Both required clean-context reviews completed, with all reported code findings
resolved and re-reviewed:

- Connected runs are scoped before Gradle; abbreviated task selectors cannot
  bypass device preconditions. Only explicit supported full task names are accepted.
- Start validates the repository AVD path and image mapping, exact emulator
  version, actual guest API, locale, timezone, display, font and animation settings.
  A fresh launch establishes the renderer instead of trusting an AVD name.
- Every main-tab capture waits for that page's loaded content.
- Artifact names contain a run ID shared with the retained environment manifest;
  a separately executed SHA-256 command verifies the persistent copy.

Review also caught a first artifact-helper attempt that incorrectly assumed
UiAutomation interpreted shell operators. It now executes copy/hash commands
separately, and successful native API 36 captures verify the corrected path.

The review page's committed images contain seeded emulator data. The supplied
phone images and local interactive comparison remain excluded from the public
repository. F0 acceptance and integrated verification are recorded when complete.

Final pre-merge command (run `20260917-033333588`):

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\tools\dev-windows.ps1 testDebugUnitTest --rerun assembleDebug lintDebug assembleDebugAndroidTest connectedDebugAndroidTest
```

Completed successfully in 2m 36s. API 26 run: `20260917-033142108`;
API 36 full run: `20260917-031801721`. The committed API 29 captures have
their own matching `native/api29/captures.json` and `profile.json`.
Both reviewers cleared the final bounded-fixture and timezone changes.
