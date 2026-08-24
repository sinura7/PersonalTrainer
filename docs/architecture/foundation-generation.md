# Foundation generation — TemperDatabase, reset, and freeze

- **Status:** Accepted — P5.3–P5.7
- **Date:** 24 August 2026
- **Authority:** [FOUNDATION_PROGRAM.md](../FOUNDATION_PROGRAM.md) Phase 5
- **Does not reopen:** `fallbackToDestructiveMigration`; Room 2.8; Kotlin 2.0.21

This packet is the one authorized cutover from `TrainerDatabase` v2
(`personal_trainer.db`) to a new database generation.

## Decision

1. **`TemperDatabase` is a new generation.** New name (`temper.db`), new
   schema folder, new version series that started at 1. P6.1 migrated
   it to version 2 (bodyweight entries and training blocks). P7.1
   migrated it to version 3 (schedule rules, occurrences, missed-work
   decisions, reminder deliveries). P8.1 migrated it to version 4
   (measurable goals and the bodyweight four-tuple). It is not a silent
   v2→v3 patch of `TrainerDatabase`.
2. **`fallbackToDestructiveMigration` remains prohibited** on both
   databases. A migration bug fails closed.
3. **The activity tables persist [ADR-007](ADR-007-activity-model.md).**
   A unique `liveToken` allows at most one ACTIVE row. Cardio-only
   sessions store zero strength-set rows and zero legacy `set_logs`.
4. **One live activity is a transactional invariant.**
   `ActivityRepository.confirm` runs [ActivityRules](activity-contract.md)
   inside the same Room transaction as the insert.
5. **Export version 5 carries measurable goals** on top of version 4's
   planner arrays (`scheduleRules`, `scheduleOccurrences`,
   `missedWorkDecisions`, `reminderDeliveries`) and version 3's
   `activities` / `activityTemplates`. v1–v4 files still decode with
   empty `measurableGoals`. Live activities are excluded, same rule as
   unfinished workout sessions.
6. **The signed development reset (P5.6)** requires an off-device export
   acknowledgement and irreversible copy. It integrity-checks and seeds
   `TemperDatabase` *before* deleting `personal_trainer.db` / WAL / SHM.
   Only weight unit and rest sound / vibration / default duration are
   preserved. Encoded bodyweight and block strings are dropped.
7. **After P5.7 the generation is frozen.**
   `FoundationGeneration.FROZEN` is true. `AppContainer` opens
   `TemperDatabase` only. A second wipe of the foundation database is a
   defect. Later schema changes migrate `TemperDatabase` with generated
   artifacts and tests. `TrainerDatabase` remains for historical v1→v2
   migration tests.

## Finding coverage

FND-002 closed at P6.6: History, calendar, insights, and detail read
completed activities. FND-019 closed at P6.1: bodyweight and training
blocks are Room tables on `TemperDatabase` v2. P7.1 migrated that
   generation to v3, and P8.1 to v4, without a second wipe.
   `FoundationGeneration.FROZEN` stays true. Generated `4.json` is
   committed. Export version is 5.
