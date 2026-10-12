# ADR-033 — Local draft recovery after reopening

- **Status:** Accepted
- **Date:** 12 October 2026 — owner selection and approval of P01–P20
- **Related:** ADR-009 backup/privacy; ADR-021 routine write-through and exit;
  ADR-026 native UX; existing SavedState draft contracts

## Context

SavedState retains bounded forms through Android restoration but does not keep
them after intentional task closure. The custom editor has neither a restored
draft nor durable admission for uncertain Save. The owner selected resume after
reopening and approved a private draft foundation without a public/schema/backup
change. Existing saved rows and write-through routine edits remain authoritative.

## Decision

1. Keep typed, owner-scoped draft checkpoints and immutable pending commands in
   versioned private storage under `noBackupFilesDir`. Use atomic replacement,
   ordered IO and revision/generation fencing. No draft payload enters backup,
   cloud sync, diagnostics or logs. Existing SavedState is a complement.
2. Restore only when entering the owning form. Back/Close retains staged work;
   explicit Discard clears it. Routine Back retains its flush/write-through
   contract: a saved edit is not undone by discarding unsaved fields.
3. An orderly exit promising preservation flushes the latest checkpoint first.
   Failure stays actionable. Recovery covers durable checkpoints, not a write
   killed before it completed. IO must not block input on the main thread.
4. Reserve Save and persist its frozen command before business mutation. Use
   stable identities and ignore duplicate activation. An uncertain outcome is
   inspected before Retry: exact persisted intent settles without rewriting;
   proven uncommitted work may retry; conflicts/unavailable reads retain fields.
5. Clear only the accepted owner/revision or explicit discarded draft. Fencing
   survives clear so queued old work cannot resurrect it. Cleanup failure cannot
   replay an acknowledged business write. Partial routines retry only what is owed.
6. Missing/changed owners are conflicts, not permission to recreate or overwrite.
   Restore uses the existing coordinator; after accepted restore, old draft
   generations require ownership reconciliation before another submission.
7. Add internal types/adapters as required; preserve public signatures, Room
   schema and backup format. Notification/coach/sync feature expansion is separate.

## Consequences

P02 supplies the typed storage/admission foundation; P03 first uses it in the
custom editor. Other forms adopt it in their packets, preserving each existing
write contract. Corrupt/unsupported checkpoints produce a truthful recoverable
state without changing business data or silently erasing authored fields.
Tests cover stale writes, clear fencing, interrupted atomic replacement,
uncertain cleanup, reopening, wrong ownership and generation changes. UI and
physical acceptance are not inferred from storage tests.

## Review questions

- Does reopening write a workout or apply a plan? No, it restores staged input.
- Does Discard undo saved routine edits? No.
- Is this a new portable backup format or Room migration? No.
- Is the owner required to test during development? No; P20 follows development.
