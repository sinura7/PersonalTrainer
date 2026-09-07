# Temper support

**Status:** Current published posture (P12.2)

Temper is maintained for one training log. There is no call centre.

## If something is wrong on the phone

1. Do not restore a backup over a phone that still has work you have not
   exported.
2. Settings → Export to file. Keep that file off the phone.
3. Settings → Share diagnostics. The bundle is redacted. Attach it only if
   you are comfortable sharing device metadata and Temper stack frames.
4. Open an issue on the project repository with the app version from
   Settings → About, what you tapped, and what you expected.

A crash is kept, redacted, until the next one or until Settings → Clear
diagnostics. Share the bundle *before* clearing; the bundle header's
`lastCrashAtMs` says whether a prior crash is in it (its event is kind
`previous-crash`).

## Reading frames from a release build

Release builds are minified (`isMinifyEnabled = true`), so R8 renames
methods. `-keeppackagenames` keeps the `com.sinura.personaltrainer` prefix
that `DiagnosticRedaction` filters on, but the class and method names in a
release bundle's `frame` lines are still the obfuscated ones. `mapping.txt`
is the file R8 writes for that exact build, translating every renamed
symbol back to its source name; without the one from the same build the
frames cannot be read.

- AGP writes it to `app/build/outputs/mapping/release/mapping.txt`.
- `release.yml` uploads it as the `mapping` workflow artifact on every run,
  so download the artifact for the tag the bundle's `appVersion` names.
- Paste the `frame` lines into a file and run
  `retrace mapping.txt frames.txt` (`$ANDROID_HOME/cmdline-tools/latest/bin/retrace`).

Debug builds are not minified; their frames read as-is.

## Security contact

Report a data-handling or backup defect the same way: a repository issue
with no backup passwords, no tokens, and no raw database files.

## What we will not ask for

- Your backup password
- A Drive token
- A copy of `temper.db`
- Bodyweight or workout notes in plaintext mail

## Commercial boundary

Local recording, history, reminders, goals, and export stay free and
signed-out. See [COMMERCIAL_BOUNDARY.md](COMMERCIAL_BOUNDARY.md).
