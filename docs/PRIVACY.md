# Temper privacy

**Status:** Current published posture (P12.2 / FND-030)  
**Related:** [ADR-009](architecture/ADR-009-backup-privacy-sync.md),
[backup-threat-model.md](architecture/backup-threat-model.md),
[DATA_SAFETY.md](DATA_SAFETY.md)

Temper is a local-first Android fitness log. Recording, history, templates,
schedules, reminders, goals, deterministic recommendations, and export work
without an account.

## What the app stores

On this device, in Temper’s own database and preferences:

- exercise catalog and custom lifts
- routines, pinned week, schedule rules and occurrences
- strength sets, cardio blocks, mixed activities
- bodyweight entries
- measurable goals and their pause intervals
- rest-timer and reminder preferences
- optional local diagnostic events (exception class and Temper stack frames only)

Weights are stored in kilograms. Display units are a preference.

## What the app does not do by default

- No account is required.
- No ads.
- No analytics SDK.
- No automatic crash or usage telemetry.
- Drive is optional whole-file **backup**, not synchronization.
- Implicit Android Auto Backup is disabled. User-controlled export is the
  recovery path.

## Backup and Drive

A file export can be written to storage you pick. You may protect it with a
password. Optional Google Drive backup uses `drive.file` scope for files the
app created. A restore previews authored counts and asks before replacing
local data.

Tokens and keys never enter an export. Sign-out of Drive, when used, leaves
local history on the phone.

## Notifications and exact alarms

Workout reminders are best-effort WorkManager jobs. They are off until you
opt in. Quiet hours are 22:00–07:00. Rest completion may use
`SCHEDULE_EXACT_ALARM` when the system grant is present; otherwise the app
uses an inexact wakeup and says so.

Onboarding never asks for notification permission.

## Diagnostics

Settings → Share diagnostics builds a redacted text bundle: app and schema
version, device metadata, event IDs, exception classes, and Temper-owned
stack frames. It excludes workout names, weights, notes, bodyweight, emails,
tokens, database contents, and raw paths. Nothing is uploaded unless you
share the file yourself.

## Retention, deletion, and export

History stays until you delete a session, restore a backup over it, or
uninstall the app. Uninstall removes local Temper data. Export a backup
before you wipe a phone.

## Health

Temper is a training log. It is not a medical device and does not diagnose,
treat, or prevent any condition.

## Contact

See [SUPPORT.md](SUPPORT.md).
