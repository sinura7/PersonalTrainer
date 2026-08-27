# Local lint and supply-chain policy

- **Status:** Accepted — P4.6
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P4.6
- **Does not reopen:** GitHub-hosted runners as the test lane; compileSdk
  36 / AGP 8.9.2; UseKtx `edit()` coverage floor; signed toolchain
  matrices

This packet closes FND-026 and FND-027. Lint is a local gate. Hosted
CI is still not the test lane.

## Lint gate

1. `warningsAsErrors = true`. A new warning fails `lintDebug`.
2. `app/lint-baseline.xml` is empty. Existing findings were fixed or
   given a site-level `@Suppress` with a reason.
3. The only project-level disabled ids are the signed waivers below.
   Anything else is a defect.

## Signed waivers

| Id | Why it stays off |
|---|---|
| `AndroidGradlePluginVersion` | Latest stable AGP is 9.x and wants compileSdk 37. AGP 8.9.2 is the official compileSdk-36 pair. `check-sdk-target.py` is the ratchet. |
| `GradleDependency` | Lint nags versions that P4.1–P4.5 refused (Compose 2026.08, Core 1.19, Room 2.8, Lifecycle 2.11). The signed catalogs are the floor. |
| `UseKtx` | `SharedPreferences.edit { }` inlines Robolectric-blind timer bytecode and drops the 18% floor. Durable `commit()` stays. |

Site-level `@Suppress("ApplySharedPref")` is allowed only on the durable
timer and schema-marker writes. Those are not project-level disables.

`app/lint.xml` ignores `ObsoleteSdkInt` only on `mipmap-anydpi-v26`.
Adaptive-icon XML is invalid in `mipmap-anydpi`; AAPT requires the v26
folder even though minSdk is 26.

## Supply chain

1. Project repositories are `google()` and `mavenCentral()` only.
   Plugin repos may also use `gradlePluginPortal()`.
   `RepositoriesMode.FAIL_ON_PROJECT_REPOS` stays on.
2. Version catalog entries are exact. No `+`, `latest`, or dynamic
   versions.
3. `gradle/verification-metadata.xml` checksums the resolved graph
   (`sha256`). A missing or unverified artifact fails the local build.
   Android Studio source/javadoc attachments and the Gradle distribution
   `-src.zip` are trusted artifacts (not on the APK classpath). Do not
   set `verify-metadata` false or `org.gradle.dependency.verification=off`
   to make Studio sync. Host-native `aapt2` jars (linux / windows / osx)
   stay checksummed; the ledger must not be Linux-only.

## Finding coverage

FND-026 closed: lint is gating, leftovers classified, remainder waived
in this file. FND-027 closed: the 2026-stable train that fits this
AGP/Kotlin pair is signed; newer cores stay refused.
