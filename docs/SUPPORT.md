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
