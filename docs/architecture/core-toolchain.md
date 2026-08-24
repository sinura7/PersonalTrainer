# Core toolchain matrix

- **Status:** Accepted — P4.2
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P4.2
- **Does not reopen:** compile/target 36; minSdk 26; Compose BOM;
  Room; DataStore; Google Sign-In

This packet upgrades the core AndroidX / Kotlin families that P4.1 left
in place. Each family moves together. Versions that require a newer
Kotlin compiler or AGP 9 are refused.

## Signed matrix

| Family | From | To | Why it stops here |
|---|---|---|---|
| Core KTX | 1.15.0 | **1.17.0** | Last stable that compiles against API 36 on AGP 8.9. 1.18+ wants API 36.1/37 and AGP 9. |
| Lifecycle | 2.8.7 | **2.10.0** | 2.11.0 compiles Compose against API 37 and requires AGP ≥ 9.2.0. |
| Activity | 1.9.3 | **1.12.4** | Latest 1.12 patch. 1.13.0 is left for a later Compose/AGP train. |
| Coroutines | 1.9.0 | **1.10.2** | 1.11.0 is the Kotlin 2.2.20 companion. We stay on Kotlin 2.0.21. |
| Serialization | (absent) | **1.8.1** + compiler plugin 2.0.21 | Gson remains the backup codec. kotlinx.serialization is the signed core JSON seam. |
| Robolectric | 4.14.1 | **4.16** | Ships API 36. `robolectric.properties` now pins `sdk=36`. |
| AndroidX Test | 1.6.x / 1.2.1 | **1.7.0 / 1.3.0** | core, runner, rules 1.7.0; ext-junit 1.3.0. |

## Decisions

1. **Do not bump Kotlin, KSP, Compose, Room, or play-services-auth.**
   Those are P4.3–P4.5.
2. **Do not take Lifecycle 2.11 or Coroutines 1.11** on this AGP/Kotlin
   pair.
3. **Domain stays serialization-free.** `CoreToolchain` lives in
   `toolchain/` so the plain-JVM domain lane does not need the
   serialization plugin.
4. **Gson still encodes BackupJson.** A later packet may migrate the
   document; this one does not.

## Finding coverage

FND-027 continues. Room, Sign-In, and lint-baseline cleanup remain
P4.4–P4.6.
