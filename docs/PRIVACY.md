# Temper privacy

**Status:** Current published posture (P12.2 / Phase 11 account sync)  
**Related:** [ADR-009](architecture/ADR-009-backup-privacy-sync.md),
[backup-threat-model.md](architecture/backup-threat-model.md),
[sync-personal-build.md](architecture/sync-personal-build.md),
[DATA_SAFETY.md](DATA_SAFETY.md)

Temper is a local-first Android fitness log. Recording, history, templates,
schedules, reminders, goals, deterministic recommendations, and export work
without an account.

## What the app stores on this device

On this phone, in Temper’s own database and preferences:

- exercise catalog and custom lifts
- routines, pinned week, schedule rules and occurrences
- strength sets, cardio blocks, mixed activities
- bodyweight entries
- measurable goals and their pause intervals
- rest-timer and reminder preferences
- optional local diagnostic events (exception class and Temper stack frames only)
- when Temper Account is configured: a Supabase Auth session (tokens in app-private
  storage) and a sync upload queue (`sync_outbox`) until uploads finish

Weights are stored in kilograms. Display units are a preference.

## Temper Account (optional cloud sync)

Temper Account is **opt-in**. Core training is never gated on sign-in
([ADR-004](architecture/ADR-004-offline-core-and-entitlements.md)). On first
install, after the short branded intro, you choose Temper Account or saving on
this phone (with optional Google Drive backup later). You can change that in
Settings → **How you save**.

When you sign in with email and password:

- **Authentication:** Supabase Auth stores your account (email, hashed password,
  user id). Temper sends your email and password over **HTTPS** only for sign-in,
  sign-up, and account deletion.
- **Synced training data (trusted server, not E2EE):** When you are signed in and
  online, Temper Account replicates finished workouts and plan structure: activity
  sessions (with blocks, strength sets, and cardio intervals); schedule rules and
  occurrences; routines and the exercises in each routine; saved activity templates
  (with the same block, set, and interval rows used for template structure in sync
  today). Live in-progress (`ACTIVE`) sessions are not uploaded. Custom exercises
  and catalog seed rows stay on this phone only (deferred).
- **Who processes it:** Your Supabase project (Auth + Postgres with row-level
  security). The app uses the public anon key and your signed-in access token; there
  is no separate Temper-operated backend beyond that project. Cloud data is **not**
  end-to-end encrypted in v1; the server can read synced rows (honest trusted-server
  posture per [ADR-009](architecture/ADR-009-backup-privacy-sync.md)).

When you are signed out or never use Temper Account, **none** of the above leaves
the phone except what you explicitly export or back up.

## What the app does not do

- No account is required.
- No ads.
- No analytics SDK.
- No automatic crash or usage telemetry.
- No end-to-end encryption on the Temper Account lane (deferred).
- Drive is optional whole-file **backup**, not synchronization.
- Implicit Android Auto Backup is disabled. User-controlled export is the
  recovery path.

## Backup and Drive

A file export can be written to storage you pick. You may protect it with a
password. Optional Google Drive backup uses `drive.file` scope for files the
app created. A restore previews authored counts and asks before replacing
local data.

Drive backup can be set to run after each finished workout. It is off until
you turn it on, it always writes the same password-protected envelope, and it
never writes an unprotected one. Turning it on keeps your backup password on
that phone, sealed by an Android Keystore key that cannot be exported; turning
it off, or signing out of Drive, deletes that sealed copy. It is never included
in an export, so it cannot travel to another phone.

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

If the app crashes, that one event — already redacted to the same fields —
is kept in app-private storage so the next Share diagnostics can include
it. It is replaced by the next crash and deleted by Settings → Clear
diagnostics or by uninstalling.

## Retention, sign-out, deletion, and export

**Local history** stays until you delete a session, restore a backup over it,
or uninstall the app. Uninstall removes local Temper data. Export a backup
before you wipe a phone.

**Sign out of Temper Account:** Your workouts and plan on **this phone stay**.
The upload queue is cleared so nothing pending is sent after sign-out. The next
sign-in can enqueue fresh uploads.

**Delete Temper Account (Settings → Account, while signed in):** You must confirm
by typing your account email. The app deletes your synced rows on the Supabase
project, then deletes your Auth user via Supabase’s self-service delete API.
Local training data on the phone is **kept** unless you remove it yourself.
After success you are signed out locally and the sync outbox is cleared. If
deletion fails, the app shows an error and does not pretend it succeeded.

**Google Drive backup** files you created remain in your Drive until you delete
them there.

## Health

Temper is a training log. It is not a medical device and does not diagnose,
treat, or prevent any condition.

## Contact

See [SUPPORT.md](SUPPORT.md).
