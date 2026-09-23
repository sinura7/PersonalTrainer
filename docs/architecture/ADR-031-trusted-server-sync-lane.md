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
amendment. [ADR-028](ADR-028-save-posture-and-account-sync-target.md) cited
ADR-009 for a lane ADR-009 did not grant. The 22 September audit then found it
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
2. **Unchanged from ADR-009 §15.** Sync is opt-in, and training never needs it
   ([ADR-004](ADR-004-offline-core-and-entitlements.md)). Sign-out keeps local
   data. Tokens never enter an export or a log. The outbox is transactional:
   the upload row is written in the same Room transaction as the change it
   describes.
3. **Paused until S1.** `AccountSyncGate.SYNC_PAUSED` holds (packet S0a). No
   pass runs while it holds; edits still queue. Resuming is packet S1's
   decision to make, and S1 may flip it only when all of these are true and
   each has a test that fails without it:
   - a pulled update changes that row and nothing under it (done, S0b);
   - the outbox row commits with the change (decision 2);
   - every delete the app can make is queued, and a tombstone carries the time
     of the delete, not the row's last edit;
   - one bad upload cannot block the queue: it is retried a bounded number of
     times, then set aside and reported;
   - restore holds sync while it runs and resets the pull cursors after;
     sign-out resets them;
   - the enrolled user's id is stored on the phone, so a change made while the
     session is still loading is queued for the right account.
4. **Conflicts.** Activity sessions resolve by revision, then change time.
   Schedules, routines, templates, custom lifts, goals and preferences resolve
   by change time: the later change wins. From packet S2a that time is assigned
   by the server and the server refuses an older write, so a phone's clock
   cannot reorder two edits. This is last-writer-wins by server order, and it
   replaces ADR-009 §15's rejection of last-write-wins for schedules for this
   lane: there is one owner, and two phones rarely edit the same schedule row
   at once. Sets are keyed per set, so two phones logging never collide;
   editing the same set on two phones keeps the later edit.
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
- A packet that turns sync back on without every item in decision 3 is a
  defect, whatever its tests say.

## Review questions

- Is Temper Account end-to-end encrypted? No.
- May sync run today? No. It is paused until S1 meets decision 3.
- Does a phone's clock decide which edit wins? Until S2a, yes; after it, no.
- Is Google Drive sync? No, still backup (ADR-009).
- May an agent change the Supabase project without showing the owner? No.
