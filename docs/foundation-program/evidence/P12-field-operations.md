# P12 field operations

- **Implementation commit:** `499c972`
- **Evidence date:** 25 August 2026
- **Application:** `com.sinura.personaltrainer.debug`

## P12.1 / FND-014

`DiagnosticStore` is a 64-event ring. `AppLog.e` and the uncaught
handler record exception class, optional `PT/` tag, and Temper frames
only. Settings → Share diagnostics builds a text bundle. Canary tests
prove workout names, notes, weights, bodyweight, emails, tokens, paths,
and log messages do not appear.

## P12.2 / FND-030 / FND-047 / FND-048

Published:

- [docs/PRIVACY.md](../../PRIVACY.md)
- [docs/DATA_SAFETY.md](../../DATA_SAFETY.md)
- [docs/SUPPORT.md](../../SUPPORT.md)
- [docs/COMMERCIAL_BOUNDARY.md](../../COMMERCIAL_BOUNDARY.md)

`tools/check-commercial-boundary.py` is on the preflight.

## P12.3 / FND-029

Release `isMinifyEnabled` and `isShrinkResources` are true.
`./gradlew assembleRelease bundleRelease` succeeded earlier on this
branch. Unsigned release APK is 2.9 MB (debug APK 24 MB). Release AAB
is 6.5 MB. `tools/check-version-code.py` keeps `versionCode` at or
above the released floor (currently 1). `versionCode` is not bumped
here — that happens when a public artifact is cut.

## P12.4 rehearsal

`tools/check-play-rehearsal.py` is on the preflight. It proves
`allowBackup=false`, rest FGS `specialUse`,
`FOREGROUND_SERVICE_SPECIAL_USE`, `SCHEDULE_EXACT_ALARM` (and no
`USE_EXACT_ALARM`), R8 + resource shrinking, privacy/support/commercial
docs, the wrong-password unwrap proof, and that Public Candidate still
requires physical TalkBack.

The script then prints, and must keep printing:

```
BLOCKED: physical TalkBack
BLOCKED: Android Public Candidate
BLOCKED: Play upload / Commercial RC
```

Play publish is **blocked**. Do not upload an AAB. Commercial Release
Candidate is **not claimed**.
