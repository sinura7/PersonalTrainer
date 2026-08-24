# ADR-009 — Backup, privacy, Drive, and sync gate

- **Status:** Accepted
- **Date:** 24 August 2026
- **Supersedes:** Implicit Android Auto Backup as an accepted recovery path;
  any current-voice description of Drive as “sync”
- **Related:** FND-011, FND-012, FND-014A–C, FND-030, FND-038; Phase 3, 11, 12

## Context

Training history, bodyweight, and notes are sensitive. The app currently
allows Android Auto Backup, writes plaintext JSON, and offers optional
Google Drive whole-file replacement. The UI already over-promises restore
safety. Incremental account sync is a later product, not a rename of Drive.

## Decision

### Backup versus sync

1. **User-controlled backup is the authoritative recovery path.** SAF export
   and optional Drive whole-file backup are backups. They replace local state
   after preview and confirm. They are not synchronization.
2. Drive remains `drive.file` whole-file backup until an incremental sync
   protocol is separately delivered and proven (Phase 11). Settings, README,
   and recovery copy say **backup**, never sync.
3. Local file export remains available with no Google account.

### Implicit OS backup

4. **Policy:** disable implicit Android backup and device-to-device app-data
   transfer for Temper data. Implementation is P3.5 (`allowBackup=false` plus
   explicit legacy and API-31+ exclusion rules covering databases, files,
   shared preferences, safety snapshots, journal, and migration snapshots).
5. Existing OS backups are **not retroactively recalled**. Documentation
   must say so. They are luck, not a plan, and after P3.5 they are not a
   supported channel.
6. Upgrade-in-place must prove the manifest change does not erase local data.

### Export privacy

7. App-layer plaintext export and Drive payloads expose fitness and
   bodyweight data. Do not claim they are private. Do not claim Android
   Keystore encryption is portable to another device.
8. Phase 3.6 adds a versioned portable authenticated encrypted envelope
   (reviewed platform cryptography, random salt/nonce, password-based KDF).
   Device-bound keys are forbidden for portable files. Custom crypto is
   forbidden.
9. Legacy plaintext import is preserved. Plaintext export becomes an
   explicitly warned advanced choice.

### Restore honesty (implementation in Phase 3)

10. Restore is select → decode/validate → compare incoming vs current authored
    counts → confirm. No mutation on first tap.
11. Authored inventory counts sessions, sets, routines, custom exercises,
    schedule, bodyweight entries, and blocks. Seeded built-ins do not count
    as “has data.” A catalog-only file cannot erase authored data through
    the normal restore path.
12. A verified safety snapshot is required *before* teardown or deletion.
    Snapshot failure aborts restore. Retained snapshots are user-recoverable
    through Settings (list, counts, export, restore-through-preview, delete).
    Raw private paths are not shown.
13. Session start/repeat and restore serialize through one coordinator.
    Restore is journaled and recoverable across process death. Success means
    the committed state, never a mixed Room/DataStore result.

### Sync start gate

14. **Phase 11 does not start** until all of the following are accepted:
    - local product through Phase 9;
    - this privacy posture implemented;
    - stable IDs, revisions, and tombstones in the foundation schema;
    - the KMP decision in [ADR-003](ADR-003-shipping-platform.md) (run or
      explicitly deferred);
    - a backend and security review.
15. Sync, when it exists, is opt-in, E2EE, per-entity conflict-specified,
    and outbox-transactional. Last-write-wins is rejected for set logs and
    schedules. Sign-out retains local data. Tokens and keys never enter
    export or logs.

### Diagnostics

16. Default remains **no automatic telemetry**. Phase 12 may add a user-
    triggered redacted diagnostic bundle. It must not include messages,
    workout names, weights, notes, bodyweight, emails, tokens, database or
    backup contents, or raw paths.

## Consequences

- P3.1’s threat-model packet records the inventory; it does not reopen
  whether Auto Backup stays on.
- Commercial privacy/Data Safety copy in P12.2 must match this ADR.
- Calling Drive “cloud sync” in current-voice docs is a failed authority
  check.

## Review questions

- Is Auto Backup the recovery path? No.
- Is Drive sync? No.
- Can a catalog-only file wipe history? Not after P3.2, and not by design.
- When may incremental sync begin? Only after the Phase 11 start gate.
