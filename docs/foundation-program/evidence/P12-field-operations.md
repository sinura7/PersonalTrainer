# P12 field operations

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
`tools/check-version-code.py` keeps `versionCode` at or above the
released floor (currently 1). `versionCode` is not bumped here — that
happens when a public artifact is cut.

## P12.4

Play publish is **blocked**. Physical TalkBack, a signed upgrade over
populated release data on a physical phone, and a clean-clone release
rehearsal remain open. Do not upload an AAB while those gates are red.

Commercial Release Candidate is **not claimed**.
