# Temper Account sync (personal build)

- **Authority:** [ADR-004](ADR-004-offline-core-and-entitlements.md), [ADR-009](ADR-009-backup-privacy-sync.md) (plaintext personal lane; E2EE deferred)
- **Local-first:** Room on the phone remains gym-floor source of truth. Sync runs only when Temper Account is signed in and the device is online.
- **Backup:** Google Drive whole-file backup is unchanged and is not sync.
- **Settings → Account (signed out):** An entry screen (headline + “Open account”) runs before email/password. **Not now** or the subpage back control returns to Settings without signing in. Home, Plan, History, and live workouts are never gated.
- **Cold start (process):** A short branded Compose intro (Temper mark + wordmark) overlays the root UI once per process; tap skips or it auto-advances in ~2s. Not sign-in, not first-launch plan onboarding. Rotation / warm return within the same process does not replay it.

## Scope (Phase 11 steps 4–5)

| Direction | Tables |
|-----------|--------|
| Push + pull | `activity_sessions`, `activity_blocks`, `activity_strength_sets`, `activity_cardio_intervals`, `activity_templates`, `schedule_rules`, `schedule_occurrences`, `routines`, `routine_exercises` |
| Deferred | Custom exercises / catalog seed (not plan structure) |

## Mechanics

- **Outbox:** `sync_outbox` rows enqueue on completed activity writes, schedule mutations, routine/template edits, and after backup restore of plan rows. Live (`ACTIVE`) sessions are not uploaded.
- **Worker:** WorkManager drains the outbox to Supabase PostgREST, then pulls rows with `updated_at_ms` greater than per-table cursors in `sync_table_cursors`. Each table loops PostgREST pages (500 rows) within one worker pass until a short page, so large restores are not stranded across extra wakes.
- **Conflicts:** Per-entity `revision` (activity sessions) or `updated_at_ms` (schedule, routines, templates with revision `0`); higher revision wins, then later `updated_at_ms`. Child rows (`routine_exercises`, activity blocks/sets/intervals) apply server tombstones and upserts only when the parent row exists locally and the same child is not waiting in the upload outbox (local queued edits win until pushed). Soft deletes use `deleted_at_ms` tombstones on the server (routine delete and removed lifts enqueue tombstones).

Supabase column names are **snake_case** in PostgREST payloads; Room keeps **camelCase** locally.

## Sign-out and outbox

- **Local data stays** on the phone (ADR-004). Sign-out clears the **upload queue** only (`abandonOutboxOnSignOut`); it does not delete workouts or plan rows. When pending uploads &gt; 0, Settings → Account shows a confirm dialog before sign-out (cancel keeps the session; confirm drops the queue then signs out).
- Edits after the next sign-in enqueue fresh outbox rows. A failed upload does not block **pull** in the same worker pass (downloads still run).

## Privacy, Data Safety, account deletion (Phase 11 step 7) — done

- [docs/PRIVACY.md](../PRIVACY.md) and [docs/DATA_SAFETY.md](../DATA_SAFETY.md) describe Temper Account (trusted-server, not E2EE).
- Settings → **About** and Settings → **Account** open the published privacy policy URL.
- Settings → **Account** (signed in) → **Delete Temper Account…** — type email to confirm; deletes Supabase Auth user and synced server rows; local Room data stays; outbox cleared.

## Deferred (needs product / later Phase 11)

- **E2EE** cloud lane; **Google Sign-In**.
- **Custom exercises / catalog seed** sync (plan structure is in scope above; catalog is not).
