# ADR-010 — Schema evolution, one-time reset, and migration ban

- **Status:** Accepted
- **Date:** 24 August 2026
- **Amended:** 23 September 2026 — decision 7 by
  [ADR-032](ADR-032-jvm-evidence-lanes.md): the JVM migration test is the gate;
  decision 12 added (X2b): a raw copy of `temper.db` before every migration
- **Supersedes:** Historical signed “Room v3 won’t” in Jobs 3–6, UX page pass,
  and owner-loop; the implication that Room v2 is the last database
  generation
- **Does not supersede:** the ban on `fallbackToDestructiveMigration`
- **Related:** FND-002, FND-019; Phase 5

## Context

Jobs 3–6 forbade Room v3 because a casual `3.json` invented during polish
would crash or wipe phones that already held v2 history. That fear was
correct for leftover-intuition packets. It is incorrect as a permanent
product constraint: the agreed fitness platform cannot be represented on
the v2 strength schema.

A development-stage data reset is acceptable. After the foundation freezes,
it is not.

## Decision

### Destructive fallback

1. **`fallbackToDestructiveMigration` remains prohibited**, here or ever.
   A migration bug must fail closed, not silently erase training history.

### One authorized reset

2. **Exactly one pre-public development reset / new-database cutover is
   authorized.** It is Phase 5.6. Its purpose is to cut from
   `TrainerDatabase` v2 to a new `TemperDatabase` generation that can
   represent [ADR-007](ADR-007-activity-model.md).
3. The reset is not a hidden wipe on next launch. It requires:
   - an external pre-reset export the user keeps off the device;
   - irreversible development-reset copy in the UI;
   - integrity-check and seed of the new database *before* it is marked
     active;
   - deletion of the legacy DB/WAL/SHM and encoded historical DataStore
     strings only after those checks pass.
4. **Preserve across the reset:**
   - weight display unit;
   - rest sound preference;
   - rest vibration preference;
   - default rest duration.
5. **Reset (do not migrate) across the cutover:**
   - onboarding-complete and questionnaire answers;
   - routines, schedule slots, sessions, sets;
   - bodyweight history and training-block strings;
   - heat-window and coaching-goal preferences;
   - backup timestamps and Drive metadata;
   - any other training-specific DataStore.
6. There is no dual-write period. After cutover, `AppContainer`,
   repositories, ViewModels, workout lifecycle, insights, backup, and tests
   target only the new database.
7. **After the Phase 5.7 foundation-freeze gate, reset authority expires.**
   Every later schema change requires a generated schema artifact and JVM
   plus device migration tests. A second “just reset it” is a defect.
   *Amended 23 September 2026 by [ADR-032](ADR-032-jvm-evidence-lanes.md):*
   the JVM test (with the schema copied into `app/src/debug/assets/`, checked
   by `TemperSchemaAssetsTest`) is the gate; the device test is still written
   and runs in the hosted lane as evidence.

### What “Room v3 won’t” meant and now means

8. The historical won’t meant: do not invent a v3 identityHash during Job 6
   polish; do not use destructive fallback; catalog versioning stays in
   `CATALOG_VERSION`.
9. That won’t is **superseded** for the signed foundation cutover. A new
   database *generation* (new name, new schema folder, new version series)
   is required. Patching v2 in place to hold cardio, occurrences, and goals
   is rejected.
10. Until Phase 5.6 actually cuts over, production remains Room v2. Feature
    packets before Phase 5 do not bump `TrainerDatabase` to 3.

### Structured history

11. Bodyweight entries and training blocks become first-class Room
    repositories after cutover (P6.1). DataStore holds preferences only.
    Encoded historical strings are a temporary defect, not a model.

### A copy before every migration

12. **Before Room migrates `temper.db`, the app copies it** (X2b,
    `TemperPreMigrationCopy`, run from `PreMigrationSnapshot.ensure` first in
    `Application.onCreate`). The copy is the main file plus `-wal`, `-shm` and
    any `-journal`, byte for byte, where *n* is the schema on disk. It is
    written into `files/pre-migration/temper-v<n>.partial/`, each file synced
    to disk, and only then renamed to `temper-v<n>/`, so a copy cut short by a
    killed process or a power cut is thrown away and taken again, never kept
    as if whole. The version is read through SQLite (the migration's own commit
    can sit in the WAL), and only a file older than
    `FoundationGeneration.VERSION` is copied. A finished copy is never
    overwritten. The copy just taken is always kept, with the newest older
    one. Only folders named `temper-v<n>` with *n* below the code's version
    count as copies; anything else there, the legacy `pre-migration/v1` copy
    included, is never touched.
    **When the copy cannot be taken, the app still opens and Room migrates in
    the same launch, so that schema bump has no rollback copy** (owner
    decision, 23 September 2026): a phone that will not open is worse than one
    without the copy, and the user's export stays the authoritative recovery
    path ([ADR-009](ADR-009-backup-privacy-sync.md)). That happens when the
    copy cannot be written, when free space is under three times the
    database's size plus 32 MiB (the migration needs room too), and when the
    version cannot be read — which includes a hot rollback journal left by a
    crash, since SQLite will not open that file read-only (rollback-journal
    mode only; a WAL-mode phone never has one). Every case is logged. A power
    cut in the moments after the copy can also lose it: the files are synced,
    the folder's rename is not.
    `TemperPreMigrationCopyTest` holds it.

## Consequences

- Owner-loop, UX page pass, and Job files that still say “Room v3 won’t”
  are historical unless they carry a supersession banner.
- Executors of P1–P4 do not open a schema packet “because v3 is now
  allowed.” The allowance is Phase 5, and only Phase 5.
- FND-019’s storage direction is established here; the move happens in P6.1.
- A schema bump needs no extra step for its rollback copy: decision 12 takes
  it for every version, and the corrective release that restores one is
  written only if a migration actually fails.

## Review questions

- May a Phase 2 packet add a Room v3 migration for convenience? No.
- May we reset again after public-candidate if analytics are slow? No.
- Is `fallbackToDestructiveMigration` allowed for the new database if
  something goes wrong during cutover? No. Fail closed and keep the legacy
  file until integrity checks pass.
