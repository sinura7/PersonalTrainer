# ADR-031 — Temper Account is a trusted-server sync lane

- **Status:** Accepted
- **Date:** 23 September 2026 (owner decisions of 22 September, whole-app audit)
- **Amends:** [ADR-009](ADR-009-backup-privacy-sync.md) decisions 14 and 15, for
  the Temper Account lane only. ADR-009 decisions 1–13 and 16, and its answer
  that Drive is backup and not sync, are untouched.
- **Related:** [ADR-004](ADR-004-offline-core-and-entitlements.md),
  [ADR-028](ADR-028-save-posture-and-account-sync-target.md),
  [sync-personal-build.md](sync-personal-build.md),
  [whole-app audit](../design-audit/2026-09-22/AUDIT.md) S-1 to S-10

## Context

ADR-009 §14 said Phase 11 sync would not start until, among other things, a
backend and security review was accepted. §15 said sync, when it existed, would
be end-to-end encrypted, per-entity conflict-specified and outbox-transactional,
with last-write-wins rejected for set logs and schedules.

Temper Account sync shipped on Temper Debug on 21 September without that
amendment: [ADR-028](ADR-028-save-posture-and-account-sync-target.md) set a
cloud target, and the lane was built, without amending ADR-009. The 22 September audit then found it
destroying local rows on pull (S-1), writing its outbox outside the save's
transaction (S-3), and dropping deletes (S-2, S-4). The owner chose to make it
safe and then finish it, rather than remove it. This record states the lane the
app actually has and the bar it must clear before it runs again.

## Decision

1. **The lane.** Temper Account sync is a trusted-server personal lane: the
   owner's rows go to Temper's Supabase project over TLS, and every synced
   table must sit behind row-level security keyed to the signed-in user
   (packet S2a commits the definitions for all 17 tables and checks them;
   today 9 are missing from the repository). It is **not** end-to-end
   encrypted. ADR-009 §15's E2EE requirement does not apply to this lane; E2EE
   stays deferred until the owner revisits this record.
2. **Kept from ADR-009 §15.** Sync is opt-in, and training never needs it
   ([ADR-004](ADR-004-offline-core-and-entitlements.md)). Sign-out keeps local
   data. Tokens never enter an export or a log. The outbox must be
   transactional: the upload row is written in the same Room transaction as the
   change it describes. **That last requirement is not met yet** (audit S-3);
   packet S1 meets it.
3. **Paused until the owner says yes.** `AccountSyncGate.SYNC_PAUSED` holds
   (packet S0a). No pass runs while it holds; edits made while signed in, with
   the session loaded, still queue. Sync resumes
   only on the owner's yes, given on packet S1's evidence, and only when every
   item below is true. Each code item has a test that fails without it; the
   server item has a recorded check.
   - A pulled update changes that row and nothing under it, and a custom-lift
     delete refused mid-pass is retried after routine lifts (done, S0b).
   - The outbox row commits in the same transaction as the change
     (decision 2).
   - Every delete the app can make is queued, and a tombstone carries the time
     of the delete, not the row's last edit.
   - A pulled row whose parent is missing on the phone is skipped, not thrown,
     so it cannot stall the tables after it; a table's cursor advances with
     each page it applies, not only after the whole table.
   - A custom lift this phone kept against a server delete is not uploaded
     again as live.
   - A set's change time is when it was last changed, not when it was
     completed, so a late upload cannot fall behind another phone's cursor.
   - One bad upload cannot block the queue: it is retried a fixed number of
     times that S1 names, then set aside and shown in Settings → Account.
   - Two passes never run at once; restore holds sync while it runs and resets
     the pull cursors after; sign-out resets them.
   - The enrolled user's id is stored on the phone, so a change made while the
     session is still loading is queued for the right account, and every pull
     filters by that id.
   - Row-level security is on for all 17 synced tables on the live server,
     checked read-only through the Supabase connector with the security
     advisors clean, and the result recorded in the S1 PR.
   - The owner has confirmed the conflict rule in decision 4.

   **Known and accepted after S1, each owned by a named packet:** rows that
   share a change time can be skipped by the cursor, and phone clocks decide
   which edit is later (S2a, S2b); a second phone's default settings can
   overwrite real ones on first sync (S3b); live-logged strength workouts are
   not synced (S3a, S3b); tokens sit in plain app storage, requests have no
   timeouts, and WorkManager retries without a cap (S4).
4. **Conflicts: the later save wins.** When two phones change the same row, the
   later save silently replaces the earlier one. The owner confirms this rule
   before sync resumes (decision 3).
   - *Rows compared by version:* activity sessions compare revision first,
     then change time; schedule rules and occurrences, routines, templates,
     custom lifts, bodyweight entries, measurable goals, the three preference
     rows and the account profile compare change time.
   - *Rows under a parent* (activity blocks, sets and intervals, a routine's
     lifts, a custom lift's muscle credits) take the server's copy unless the
     same row is still waiting in this phone's upload queue, where the local
     edit wins until it is pushed.
   - On the server, until S2a, every upload overwrites the stored row, so the
     last upload wins whatever its change time. From S2a the server assigns
     the change time and refuses an older write, so neither a phone's clock
     nor upload order can reorder two edits.
   - This replaces ADR-009 §15's rejection of last-write-wins for schedules
     and set logs, for this lane only: there is one owner, and two phones
     rarely edit the same row at once. When live strength history syncs
     (S3a, S3b), its set logs follow this rule unless that packet's own ADR
     says otherwise.
5. **Server changes.** Every change to Temper's Supabase project (tables,
   row-level security, functions) is committed under `docs/supabase/`, shown
   to the owner before it is applied, and checked with the security advisors
   before and after. No other project is touched.
6. **Account deletion.** In-app deletion stays off
   (`AccountSyncGate.IN_APP_DELETE_AVAILABLE`) until a server-side delete
   exists (packet S2a/S2b). Until then the privacy policy says how to request
   deletion, and the app points to it.

## Consequences

- FOUNDATION_PROGRAM's Phase 11 "correctly not started" is no longer true; the
  Temper Account lane exists, paused, under this record.
- ADR-028 §5 (full cloud target) is read through this record: the target stands,
  and the lane that reaches it is trusted-server.
- PRIVACY.md and DATA_SAFETY.md describe a trusted server, not E2EE, and say
  what is and is not synced.
- A packet that turns sync back on without every item in decision 3, or
  without the owner's yes, is a defect, whatever its tests say.

## Review questions

- Is Temper Account end-to-end encrypted? No.
- May sync run today? No. It is paused until S1 meets decision 3 and the
  owner says yes.
- When two phones edit the same row, which edit is kept? The later save.
  Until S2a, "later" is by the phones' clocks; after it, by the server's.
- Is Google Drive sync? No, still backup (ADR-009).
- May an agent change the Supabase project without showing the owner? No.
