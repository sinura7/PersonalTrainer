# Persistence toolchain matrix

- **Status:** Accepted — P4.4
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) P4.4
- **Does not reopen:** compile/target 36; minSdk 26; Core / Compose
  matrices; Kotlin 2.0.21; serialization ceiling 1.8.1; schema v2;
  `fallbackToDestructiveMigration` ban; Google Sign-In

This packet upgrades Room and DataStore as one persistence matrix.
The committed v1 and v2 schema hashes stay the migration substrate.
Versions that need a newer Kotlin serialization compiler are refused.

> **Read the schema lines below as of 24 August 2026, not as of today.** The
> signed Room and DataStore versions are still current and this document is
> still the authority on them. The schema is not: Phase 5 cut the app to a new
> generation, `TemperDatabase`, which is at version 4 with three migrations of
> its own. `TrainerDatabase` v1/v2 and `MIGRATION_1_2` are the historical
> substrate this packet froze, and freezing them is exactly why the cutover
> made a new database rather than a v3.
> See [`foundation-generation.md`](foundation-generation.md),
> [`ADR-010`](ADR-010-schema-reset-migrations.md) and
> [`app/schemas/README.md`](../../app/schemas/README.md).

## Signed matrix

| Family | From | To | Why it stops here |
|---|---|---|---|
| Room | 2.6.1 | **2.7.2** | Last 2.7 stable that KSP-compiles on Kotlin 2.0.21 / serialization 1.8.1. |
| DataStore | 1.1.1 | **1.2.1** | Latest 1.2 stable. 1.3 is still alpha. |

## Decisions

1. **Refuse Room 2.8.x.** The Room 2.8 compiler dies in KSP with
   `AbstractMethodError` on
   `GeneratedSerializer.typeParametersSerializers()`. That method
   exists on newer kotlinx.serialization than the P4.2 ceiling (1.8.1).
   Taking 2.8 means reopening the Kotlin train. The serialization
   artifact is not shipped.
2. **Do not change schema version, identity hashes, or migrations.**
   `1.json` stays `6d58ad40d5c03785ab29aaf61157f369`. `2.json` stays
   `3eedd5301f0344b7802f5d0da2f68b3e`. `MIGRATION_1_2` stays the only
   production migration.
3. **`fallbackToDestructiveMigration` remains prohibited.**
4. **Do not bump Kotlin, KSP, Compose, or play-services-auth.**
5. **`PersistenceToolchain` lives in `toolchain/`.** Same reason as
   the core and Compose catalogs.

## Finding coverage

FND-027 continues. Lint-baseline and supply-chain cleanup remain P4.6.
