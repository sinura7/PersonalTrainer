# SDK 36 compatibility review

- **Status:** Accepted — P4.1
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P4.1
- **Does not reopen:** minSdk 26; the API 29 device lane; FGS special-use
  rest; `SCHEDULE_EXACT_ALARM`; Auto Backup off

Temper now compiles and targets API 36 (Android 16). This file is the
reviewed pass: every targeting-36 behavior change, whether it touches
this app, and the decision. Compose, Room, Lifecycle, and KSP/Kotlin
stay on the versions P4.2–P4.4 own. AGP 8.9.2 / Gradle 8.11.1 are the
official pair required to compile SDK 36 and land in this packet.

`minSdk` stays 26. The local gate stays the API 29 emulator
`temper-tests-api29`. Targeting 36 changes how the package is *meant*
to run on API 36 devices; it does not move the test lane.

## 1. Inventory that already existed

| Surface | Current handling | API 36 impact |
|---|---|---|
| Edge-to-edge | `MainActivity.enableEdgeToEdge` with dark transparent bars. Theme does not set `windowOptOutEdgeToEdgeEnforcement`. | Opt-out is disabled when targeting 36 on API 36. We already opted in. No change. |
| Back | `androidx.activity.compose.BackHandler` on workout, summary, session detail, routine editor, custom week. No `onBackPressed()`. | Predictive back is on by default. Manifest now sets `enableOnBackInvokedCallback=true` so the Activity dispatcher stays the path. |
| Orientation | No `screenOrientation`, `resizeableActivity=false`, or aspect-ratio lock. | Large-screen (`sw600dp`) ignores those locks anyway. We never set them. |
| Rest FGS | `foregroundServiceType=specialUse` + subtype property. | Health FGS / `BODY_SENSORS` rewrite does not apply. |
| Exact alarm | `SCHEDULE_EXACT_ALARM`, typed Exact/BestEffort/Failed. Never `USE_EXACT_ALARM`. | Unchanged. |
| Notifications | `POST_NOTIFICATIONS` on API 33+. Compact denial UX. | Unchanged. |
| Backup | `allowBackup=false` plus exclusion rules. | Unchanged. |
| Exported components | One launcher Activity (`MAIN`/`LAUNCHER`). Service and receiver `exported=false`. | Safer Intents is opt-in. We do not opt in this packet; the exported surface is already one filter. |
| Local network | Drive uses HTTPS, not LAN discovery. | Local-network permission is opt-in / future. No change. |
| Media / photos / Bluetooth / sensors | Not used. | No change. |
| `scheduleAtFixedRate` | Not used. | No change. |
| `elegantTextHeight` | Not set. Type is Compose Instrument tokens. | Compact-font override is gone. We never used it. |

## 2. Decisions

1. **compileSdk = 36, targetSdk = 36, minSdk = 26.**
2. **Predictive back is accepted.** `enableOnBackInvokedCallback=true`.
   Screens that intercept back already use `BackHandler`.
3. **Do not opt out of large-screen resizability.** Identity-before-metrics
   (P2.5) already treats 360 dp as the floor. A tablet is a wide window,
   not a locked portrait phone.
4. **Do not opt out of edge-to-edge.** The window is already edge-to-edge.
5. **AGP 8.9.2 and Gradle 8.11.1 are the minimum official pair for
   compileSdk 36** (API 36 requires AGP ≥ 8.9.1). Compose, Room,
   Lifecycle, and KSP/Kotlin stay on the versions P4.2–P4.4 own.
6. **Device lane stays API 29.** An API 36 emulator is not the gate
   (ADR-002). `ApplicationInfo.targetSdkVersion` is still 36 on that
   device.
7. **The JVM lane emulates API 35, not 36.** P4.2 takes
   Robolectric 4.16, which does ship an API 36 jar — but its SDK table
   requires Java 21 to load it and this project is Java 17, so
   `robolectric.properties` pins `sdk=35`. P4.2 claimed `sdk=36`; that
   was never executable and threw out of every Robolectric class the
   first time the lane ran (2 Sep 2026). Raise it only with the JDK.
8. **AGP 8.9 lint is accepted without jumping to AGP 9.**
   `enableOnBackInvokedCallback` is marked `tools:targetApi="33"`.
   `AndroidGradlePluginVersion`, `UseKtx`, and `GradleDependency` are
   disabled: latest-stable 9.x / core 1.19 is not this packet, and the
   KTX `edit()` inline inflates Robolectric-blind timer bytecode below
   the 18% floor. Durable `commit()` stays. `check-sdk-target.py` is
   the SDK and core-family ratchet. P4.6 owns zero-warning cleanup.

## 3. Finding coverage

FND-027 is a Phase 4 train. This packet is the SDK line only. Core KTX,
Compose, Room, Sign-In, and lint-baseline cleanup remain P4.2–P4.6.
