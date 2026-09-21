# Temper Account sync (personal build)

- **Authority:** [ADR-004](ADR-004-offline-core-and-entitlements.md), [ADR-009](ADR-009-backup-privacy-sync.md) (plaintext personal lane; E2EE deferred)
- **Local-first:** Room on the phone remains gym-floor source of truth. Sync runs only when Temper Account is signed in and the device is online.
- **Backup:** Google Drive whole-file backup is unchanged and is not sync.
- **Settings → Account (signed out):** An entry screen (headline + “Open account”) runs before email/password. **Not now** or the subpage back control returns to Settings without signing in. Home, Plan, History, and live workouts are never gated.
- **Cold start (process):** A short branded Compose intro (Temper mark + wordmark) overlays the root UI once per process; tap skips or it auto-advances in ~2s. Not sign-in, not first-launch plan onboarding. Rotation / warm return within the same process does not replay it.
- **First install save posture ([ADR-028](ADR-028-save-posture-and-account-sync-target.md)):** After that intro, a one-time chooser offers Temper Account (Settings → Account) or continue on this phone, with an optional jump to Settings → Backup for Google Drive. Persisted in DataStore (`save_posture`, `save_posture_chosen`). Settings → **How you save** can change the posture later. Upgrades with prior permissions, plan setup, Drive, or Account skip the chooser.

## Scope (Phase 11 steps 4–5)

| Direction | Tables |
|-----------|--------|
| Push + pull | `activity_sessions`, `activity_blocks`, `activity_strength_sets`, `activity_cardio_intervals`, `activity_templates`, `schedule_rules`, `schedule_occurrences`, `routines`, `routine_exercises`, `custom_exercises`, `custom_exercise_muscles`, `bodyweight_entries` |
| Deferred | Built-in catalog seed (98 rows); goals; coach prefs; reminders; display prefs; account profile |

## Mechanics

- **Outbox:** `sync_outbox` rows enqueue on completed activity writes, schedule mutations, routine/template edits, custom exercise edits, bodyweight weigh-ins, and after backup restore of plan/library rows. Live (`ACTIVE`) sessions are not uploaded. Sign-in bootstraps a one-time upload queue snapshot for existing custom lifts and weigh-ins.
- **Worker:** WorkManager drains the outbox to Supabase PostgREST, then pulls rows with `updated_at_ms` greater than per-table cursors in `sync_table_cursors`. Each table loops PostgREST pages (500 rows) within one worker pass until a short page, so large restores are not stranded across extra wakes.
- **Conflicts:** Per-entity `revision` (activity sessions) or `updated_at_ms` (schedule, routines, templates, custom exercises, bodyweight with revision `0`); higher revision wins, then later `updated_at_ms`. Child rows (`routine_exercises`, `custom_exercise_muscles`, activity blocks/sets/intervals) apply server tombstones and upserts only when the parent row exists locally and the same child is not waiting in the upload outbox (local queued edits win until pushed). Soft deletes use `deleted_at_ms` tombstones on the server (routine delete, custom exercise delete, and removed lifts enqueue tombstones).

Supabase column names are **snake_case** in PostgREST payloads; Room keeps **camelCase** locally.

## Sign-out and outbox

- **Local data stays** on the phone (ADR-004). Sign-out clears the **upload queue** only (`abandonOutboxOnSignOut`); it does not delete workouts or plan rows. When pending uploads &gt; 0, Settings → Account shows a confirm dialog before sign-out (cancel keeps the session; confirm drops the queue then signs out).
- Edits after the next sign-in enqueue fresh outbox rows. A failed upload does not block **pull** in the same worker pass (downloads still run).

## Privacy, Data Safety, account deletion (Phase 11 step 7) — done

- [docs/PRIVACY.md](../PRIVACY.md) and [docs/DATA_SAFETY.md](../DATA_SAFETY.md) describe Temper Account (trusted-server, not E2EE).
- Settings → **About** and Settings → **Account** open the published privacy policy URL.
- Settings → **Account** (signed in) → **Delete Temper Account…** — type email to confirm; deletes Supabase Auth user and synced server rows; local Room data stays; outbox cleared.

## Target vs today (ADR-028)

Signed-in Temper Account users should eventually have workouts, plan, settings, and
account details cloud-backed with Room as cache. **Today** the tables in
[Scope](#scope-phase-11-steps-45) replicate, including **custom exercises** (not built-in
catalog seed) and **bodyweight weigh-ins**. See ADR-028 for Packet 3+ (goals, coach prefs,
reminders, display prefs, account profile).

## Supabase DDL (Packet 2)

Apply [docs/supabase/packet-2-account-sync-ddl.sql](../supabase/packet-2-account-sync-ddl.sql)
on the Temper Account project before testing sync for customs or bodyweight on a second device.

## Deferred (needs product / later Phase 11+)

- **E2EE** cloud lane; **Google Sign-In**.
- **Built-in catalog seed** sync (owner customs replicate; 98 built-ins stay device-local).
- **Goals, coach prefs, reminders, display prefs, account profile** (ADR-028 Packet 3+).
