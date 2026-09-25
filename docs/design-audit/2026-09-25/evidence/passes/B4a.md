# B4a — Room schema, migrations, DAOs

Pass B4a, audit X6, 25 September 2026, HEAD `1ad3b11`. ID prefix `DB-`. Paths below are
relative to `/home/user/PersonalTrainer`; `P` = `app/src/main/java/com/sinura/personaltrainer`,
`T` = `app/src/test/java/com/sinura/personaltrainer`.

## 1. Prior findings in my scope

| Prior ID | Claim (short) | Status | Evidence (PR # or path:lines) |
|---|---|---|---|
| 22 Sep "System" (migrations) | No test ran `MIGRATION_TEMPER_6_7`; device test stopped at v6; guard wanted | **Closed** (X2a, #382) | `T/data/local/TemperMigration5To6Test.kt:29,51` and `TemperMigration6To7Test.kt:25,39,69-76` call `runMigrationsAndValidate(…, true, …)`; `6To7Test.kt:66-81` chains v4→v7; device twin `app/src/androidTest/…/TemperMigrationDeviceTest.kt:34-44` chains v1→v7; guard `T/data/local/TemperSchemaAssetsTest.kt:22-40`; all nine `app/schemas/**/N.json` byte-identical to `app/src/debug/assets/**/N.json` (`cmp`, this pass) |
| 22 Sep X2b (#402) | A raw copy of `temper.db` with its WAL before every migration (ADR-010 decision 12) | **Closed** | `P/data/local/TemperPreMigrationCopy.kt:64-123`; called from `PreMigrationSnapshot.ensure` (`PreMigrationSnapshot.kt:58-67`) at `P/PersonalTrainerApp.kt:101`, before `container = AppContainer(this)` at `:103`; pinned by `T/data/local/PreMigrationStartupOrderTest.kt:21-30` and the 20 tests in `TemperPreMigrationCopyTest.kt`. Free-space rule `bytes * 3 + 32 MiB` (`TemperPreMigrationCopy.kt:130-131`), keep-2 pruning (`:51,165-176`), failure → app opens and migrates unmarked (`:115-119`) all match the ADR text |
| S-1 (local half) | REPLACE on parents cascades children; sync fixed (S0b), local callers still REPLACE | **Open, unchanged, not worse** | Sync writes use `@Upsert` (`P/data/local/dao/ActivityDao.kt:232-245`, `RoutineDao.kt:37-38`, `P/data/sync/SyncEngine.kt:331`) pinned by `T/data/sync/SyncPullInPlaceTest.kt:77`. Local REPLACE survives at `ActivityDao.kt:196,215`, `RoutineDao.kt:29,46,70,73`, `WorkoutDao.kt:160,220,223,474,477`, `ExerciseDao.kt:91`. Every local caller is proven unable to hit an existing parent with children (DB-1, DB-3 in §3), so no P1 |
| CURRENT_STRUCTURE "Six soft foreign keys" | Text says six, lists seven | **Confirmed** (docs) | `docs/architecture/CURRENT_STRUCTURE.md:194-198` lists seven columns; logged for B14 in §4 |
| 16 Sep D01–D17 | Visual findings | **None touch this scope** | grep for room/migrat/database/dao/schema/foreign in `docs/design-audit/2026-09-16/AUDIT.md` returns nothing |

## 2. Ranked findings (P1 and P2 only; at most 12 rows)

| ID | Pass | Sev | Type | C/R | Prior | Files | Claim | In plain terms | Probe or repro |
|---|---|---|---|---|---|---|---|---|---|
| — | B4a | — | — | — | — | — | No P1 or P2 found in this scope. | The database's shape, its seven upgrade steps and the safety copy before each upgrade are in good order; what is left is tidying and fragile-but-safe construction. | — |

### Notes on §2

Looked for, and did not find, a P1/P2. The candidates and why they fell to P3:

- **A local REPLACE that can hit an existing parent with children** (the S-1 shape). Every
  local caller was followed (grep across `app/src/main`). `RoutineRepository.create` (`P/data/repository/RoutineRepository.kt:38-51`) mints
  `UUID.randomUUID()` before `upsertRoutine`; `WorkoutRepository.insertSessionIfIdle:431` is
  the only `upsertSession` caller and every session id comes from `ids.newId()` (`:284,342,377`);
  `ExerciseDao.replaceAll`, `replaceRoutines`, `replaceRoutineExercises`, `replaceSessions`,
  `replaceSets` run only inside restore after every table is emptied in the same transaction
  (`P/data/repository/LocalBackupRepository.kt:358-373`); `upsertTemplate` runs only after
  `deleteAllTemplates()` (`P/data/repository/ActivityBackupIo.kt:29-31,43`); `insertBlock`'s
  three routes are the composer (fresh ids, `P/ui/activity/ActivityComposerViewModel.kt:412,421,436`),
  live-cardio start (`P/workout/StartLiveCardio.kt:39`) and `completeLive`, which deletes the
  session first (`P/data/repository/ActivityRepository.kt:349-350`). The session id itself is
  `draft.id ?: ids.newId()` (`P/domain/ActivityRules.kt:76`) and the composer passes no id
  (`ActivityComposerViewModel.kt:377-384`), so a double tap cannot re-use ids. Fragile by
  construction, not a defect: DB-1.
- **Restore validator and templates** (DB-4): a duplicated template id, or a template block id
  equal to a session block id, would silently drop blocks instead of failing closed. Only a
  file this app did not write can carry that, and no phone has templates (nothing in `main`
  constructs `ActivityTemplate` outside `P/domain/ActivitySession.kt:111` and the mappers).
- **Migrations**: every index and foreign key the schema JSON declares for v2–v7 is created by
  the matching migration (diffed by script over `entities[].createSql`/`indices`/`foreignKeys`
  of `N-1.json` vs `N.json`); the only DDL difference is DB-6.

## 3. P3 findings (unlimited)

| ID | Pass | Sev | Type | C/R | Prior | Files | Claim | In plain terms | Probe or repro |
|---|---|---|---|---|---|---|---|---|---|
| DB-1 | B4a | P3 | architecture debt | C | S-1 | `P/data/local/dao/RoutineDao.kt:29-30`, `WorkoutDao.kt:160-161`, `ExerciseDao.kt:91-92`, `ActivityDao.kt:196-197,215-216`, `P/data/repository/RoutineRepository.kt:48`, `WorkoutRepository.kt:431`, `ActivityBackupIo.kt:43,54`, `LocalBackupRepository.kt:376,395` | Five `@Insert(onConflict = REPLACE)` writes target parents whose children cascade (`routines` → `routine_exercises`, `schedule_slots`, `workout_sessions.routineId` SET NULL; `workout_sessions` → `session_exercises`, `set_logs`; `activity_templates`/`activity_blocks` → blocks, sets, intervals; `exercises` → `exercise_muscles` and three RESTRICT children); each is safe today only because its caller mints a fresh id or has just emptied the table, and nothing pins that. | Five "save" commands would quietly throw away a record's sub-items if they were ever asked to save over an existing record; today no screen can ask them to, but that safety lives in the callers, not in the command. | JUnit over in-memory `TemperDatabase` (pattern `T/data/local/TemperDatabaseTest.kt:29-35`): insert a routine + one `routine_exercises` row, call `upsertRoutine` with the same id, assert `getById(id).items.size == 1` — fails today (documents the hazard); then replace the five with `@Upsert`/`@Update` or keep REPLACE only on the restore-after-wipe paths |
| DB-2 | B4a | P3 | architecture debt | C | — | `P/data/local/dao/RoutineDao.kt:46-47`, `P/data/repository/RoutineRepository.kt:138,156` | `swapExercise` and `updateExercise` use `upsertRoutineExercise` (REPLACE) as an update of an existing row: SQLite deletes and re-inserts it; harmless only because nothing references `routine_exercises`. | Editing a lift inside a routine deletes and re-creates that line rather than editing it; fine now, a trap if anything ever links to those lines. | Read `RoutineDao.kt:46-47`; switch to `@Update` (an `@Update updateRoutine` already exists at `:40-41`) |
| DB-3 | B4a | P3 | architecture debt | C | S-1 | `P/data/repository/ActivityRepository.kt:342-350`, `P/ui/activity/LiveCardioViewModel.kt:177-198` | `completeLive` finishes a live activity by `deleteSession` (cascading its blocks, sets and intervals) then re-inserting the session from memory with `blocks ?: session.blocks`; the ViewModel passes `listOf(block)` built from the first cardio block only, so any other block a live activity ever carries would be dropped on finish. Safe today because live activities are cardio-only (`:319-321`). | Finishing a live cardio session erases and rewrites it; it works because those sessions hold exactly one thing, and would lose anything extra if they ever held more. | JUnit: confirm a LIVE draft with two blocks via `StartLiveActivity`, call `completeLive(id, now, clock, blocks = listOf(firstBlock))`, count `activity_blocks` for the session — expect 2, get 1 |
| DB-4 | B4a | P3 | validation gap | C | — | `P/data/backup/BackupValidator.kt:304-332`, `P/data/repository/ActivityBackupIo.kt:28-66`, `P/data/local/dao/ActivityDao.kt:196-200` | The validator checks ids for activities and their blocks (`:310,330`) but nothing at all for `activityTemplates` (grep "template" in the file: no hits), while restore inserts templates with `upsertTemplate` (REPLACE) and their blocks with `insertBlock` (REPLACE); a duplicated template id or a template block id equal to a session block id would silently lose blocks instead of rolling back like every other bad row. An ABORT `insertBlocks` exists unused (`:199-200`). | A hand-made or foreign backup file with a repeated template could restore with pieces missing and no error; the app's own files cannot do this. | JUnit: `BackupValidator` on a document with two templates sharing an id — expect `invalid`, get valid; or switch `ActivityBackupIo.kt:54` to the ABORT `insertBlocks` |
| DB-5 | B4a | P3 | defect (latent) | C | — | `P/data/repository/ExerciseRepository.kt:210-225`, `P/data/local/dao/ExerciseDao.kt:88-89`, `P/ui/library/ExerciseLibraryViewModel.kt:339-358` | `deleteCustom` runs the usage check (`usageFor`, three counts) and the `DELETE` outside any transaction (`writeExercise` is used by create/update at `:150,183` but not here), so a set logged between the check and the delete makes the RESTRICT foreign key throw `SQLiteConstraintException` out of the repository; `confirmDelete` has no catch around it. The check also ignores the two soft references (`measurable_goals.exerciseId`, `activity_blocks.exerciseId`), both harmless by design (snapshot columns, `ActivityDao.kt:76-104`, `GoalEntity.kt:16`). | Deleting a custom lift checks "is it used?" and then deletes in two separate steps; in a very narrow window a set logged in between turns the delete into an error the Library screen does not expect. | JUnit: log a set for a custom lift after `usageFor` but before `deleteCustom` (inject via a fake DAO), assert a `DeleteExerciseResult.InUse` rather than a throw; fix by wrapping in `writeExercise { }` and catching the constraint |
| DB-6 | B4a | P3 | validation gap | C | — | `P/data/local/TemperMigrations.kt:167-172,233-235`, `P/data/local/entity/HistoryEntities.kt:7-14`, `app/schemas/com.sinura.personaltrainer.data.local.TemperDatabase/4.json` (bodyweight fields carry no `defaultValue`) | Two ways a migrated phone differs from a fresh install: v3→4 adds `zoneId … DEFAULT 'UTC'` and `offsetSeconds … DEFAULT 0` that the entity never declares (Room tolerates a DB-side default the entity lacks, so `TemperMigration3To4Test` passes), and v5→6 seeds a `sync_metadata` row that fresh installs never get (readers are null-safe: `P/data/sync/SyncEngine.kt:56`, `SyncCoordinator.kt:25,43`). Later migration tests build from `N.json`, so they never run against the migrated DDL. | Phones that upgraded and phones installed fresh have slightly different table definitions; nothing breaks today, but tests only ever exercise the fresh shape. | Add `@ColumnInfo(defaultValue = "'UTC'")`/`"0"` to `BodyweightEntryEntity` on the next bump (v8 re-exports the schema anyway), and a JVM test that chains v1→v7 on the migrated file and then opens it with `TemperDatabase.create` |
| DB-7 | B4a | P3 | validation gap | C | 22 Sep System | `T/data/local/TemperSchemaAssetsTest.kt:33-40`, `T/data/local/TemperMigration6To7Test.kt:66-81`, `app/src/androidTest/…/TemperMigrationDeviceTest.kt:34-44` | The guard checks that `TemperMigration{N-1}To{N}Test.kt` exists by name only, not that it calls `runMigrationsAndValidate` for that version; and the only full v1→v7 chain runs in the non-blocking device lane (JVM chains v4→v7). | The tripwire for "every upgrade step has a test" only checks that a file with the right name is there, and the complete start-to-finish upgrade is proven only on the emulator lane that does not block a merge. | Extend the guard to assert the file contains `MIGRATION_TEMPER_${N-1}_$N` and `runMigrationsAndValidate(`; add a JVM `populatedV1ReachesV7` case using `seedFinishedWorkout` on a v4 fixture plus a v1 row |
| DB-8 | B4a | P3 | opportunity | C | — | `P/data/local/entity/SetLogEntity.kt:24`, `WorkoutSessionEntity.kt:18`, `P/data/local/dao/WorkoutDao.kt:23,43,119,123,270-275,337-345` | `set_logs` (the fastest-growing table) is indexed on `sessionId` and `exerciseId` only; `observeFinishedWorkingSets` orders by `completedAt` and `recordPriorsBefore` ranges on `(completedAt, setNumber)` after the `exerciseId` filter — a composite `(exerciseId, completedAt)` serves both. `workout_sessions.date` has no index but every history read orders or ranges on it (`:23,43,119,123`). | The two columns the workout history is sorted and searched by have no shortcut list, so the phone reads every row each time; small now, grows with every workout. | `EXPLAIN QUERY PLAN` on `observeFinishedWorkingSets` in a JVM test; add `Index("exerciseId","completedAt")` and `Index("date")` in v8 with the matching `CREATE INDEX` in the migration |
| DB-9 | B4a | P3 | opportunity | C | — | `P/data/local/entity/ActivityEntities.kt:8-14,66`, `P/data/local/dao/ActivityDao.kt:41,70,98,128,150` | `activity_sessions.status` is unindexed yet every completed-history query filters `status = 'COMPLETED'`; `observeCompletedGraphsSince` also ranges on `performedStartInstantMs` while the index is on `performedStartLocalEpochDay`; `activity_blocks.exerciseId` is unindexed but `observeFinishedWorkingSets(exerciseId)` filters on it. | Cardio and activity history is filtered on columns with no shortcut list; cheap now, a full-table scan per refresh later. | As DB-8: `Index("status","performedStartInstantMs")`, `Index("exerciseId")` on blocks, in v8 |
| DB-10 | B4a | P3 | opportunity | C | — | `P/data/local/dao/PlannerDao.kt:40-43,89-90`, `P/ui/plan/PlanDayViewModel.kt:58`, `P/ui/plan/PlanViewModel.kt:169`, `P/ui/home/HomeViewModel.kt:108`, `P/ui/workout/StartOptionsViewModel.kt:118`, `P/data/repository/PlannerRepository.kt:614-628` | `observeOccurrences()` streams the whole `schedule_occurrences` table (every planned day ever generated) to four ViewModels, including Home, and re-emits on any occurrence write, although a bounded `getOccurrencesBetween` exists (`:50-54`); `getDeliveries()` reads all `reminder_deliveries` ordered by an unindexed `scheduledAtMs` on every `rebuildReminders`, and delivery rows are never pruned except by occurrence cascade. Growth is ~rules × 52 per year, so small. | Home and Plan load every planned day the app has ever written, then keep only this week; reminders are re-read wholesale on every rebuild. Fine for a year or two of use, wasteful after. | Count rows emitted to `HomeViewModel` after 3 years of generated weeks in a JVM test; a windowed Flow `observeOccurrencesBetween` |
| DB-11 | B4a | P3 | opportunity | C | W2a | `P/data/local/dao/WorkoutDao.kt:22-24,386-398`, `P/data/repository/WorkoutRepository.kt:130-135,179-181`, `P/data/local/dao/ActivityDao.kt:199-200`, `P/data/repository/FoundationReset.kt:30-98` | Dead data-layer code that W2a's UI-scoped pass left: `WorkoutDao.observeFinishedSessions` and its wrappers `observeHistoryHealth`/`observeHistory`, `WorkoutDao.observeBestWorkingWeights` and its wrapper (no caller in `main` or `test`); `ActivityDao.insertBlocks`; and `FoundationReset` (no constructor call in `main`; it also opens Room with no `addMigrations`, `:35-41`, which would throw on an older file). | Several database commands and a whole "development reset" class are still in the app but nothing calls them. | grep each name across `app/src`; delete, with `FoundationResetTest` |
| DB-12 | B4a | P3 | opportunity | C+R | ADR-010 §3 | `P/data/repository/FoundationReset.kt:96`, `P/data/local/PreMigrationSnapshot.kt:80,103-114`, `P/data/local/FoundationGeneration.kt:18` | The only code that deletes the legacy `personal_trainer.db` is the unreachable `FoundationReset` (DB-11) and no other `deleteDatabase(` call exists in `main`, so a phone that still carries the legacy file, its WAL/SHM and the `files/pre-migration/v1/` copy keeps them forever. Whether the owner's phones still have them is R. | The old database from before the August cutover, and its safety copy, are never cleaned up; they cost space and hold old history in the app's private folder. | Phone: `run-as com.sinura.personaltrainer.debug ls databases/ files/pre-migration/`; if present, a one-time cleanup gated by the owner |
| DB-13 | B4a | P3 | validation gap | C | — | `T/data/local/ScheduleSlotCascadeTest.kt:31-38`, `RoutineSwapTest.kt:30-38`, `CatalogQueriesTest.kt`, `DbMaintenanceTest.kt`, `OnboardingApplierTest.kt`, `SessionNotesWriteTest.kt`, `SessionRepairRepositoryTest.kt`, `BackupV2RoundTripTest.kt` (each `TrainerDatabase::class`) | Eight data-layer tests build the legacy `TrainerDatabase` in memory rather than the production `TemperDatabase`; the shared entities make them valid for the shared tables, but none can reach `activityDao`, `plannerDao`, `goalDao` or `syncDao`, and the cascade proof for routine deletion runs against the database the app no longer opens. | Several database tests still run against the old database design instead of the one on the phone; they still prove what they prove, but they cannot cover the newer tables. | `sed` the eight to `TemperDatabase::class` and run them |
| DB-14 | B4a | P3 | validation gap | C | CURRENT_STRUCTURE "soft FKs" | `P/data/repository/PlannerRepository.kt:257-275`, `P/domain/SchedulePlannerModels.kt:128-143`, `P/data/repository/ActivityRepository.kt:248-262`, `P/data/local/dao/ActivityDao.kt:76-104` | Orphan inventory for the seven soft keys (see notes): none crashes and each has a fallback, but no test pins any of the fallbacks (a disabled rule whose routine is gone reads "Strength"; a DONE day whose activity is deleted keeps DONE; a deleted lift's activity block shows its snapshot name). | The seven "loose links" between tables each degrade gracefully when the thing they point at is deleted, but nothing proves it stays that way. | JUnit per key: delete the parent through the repository, then read the child through the same query the screen uses |

### Notes on §3

- **DB-1, evidence that Room enforces the foreign keys** (so REPLACE really cascades):
  `T/data/local/ScheduleSlotCascadeTest.kt:59-63` (a routine delete removes its slot) and the
  running S-1 proof recorded on 22 September.
- **DB-14 soft-key inventory** (all C): `schedule_rules.routineId` — on routine delete
  `onRoutineDeleted` (`PlannerRepository.kt:247-255`) deletes PLANNED occurrences and either
  deletes the rule or, when it has history, keeps it disabled with the dangling `routineId`
  (`:266-270`); the agenda title then falls back to `focusKind` or "Strength"
  (`SchedulePlannerModels.kt:138-141`). `schedule_rules.templateId` — templates are only ever
  deleted by sync (`SyncEngine.kt:306`); `ScheduleKind` parses the id as text (`ScheduleKind.kt:28`),
  no lookup. `measurable_goals.exerciseId` — `deleteCustom` does not check it (DB-5); the goal
  keeps `exerciseName`; `GoalProgress.measure` has no caller (§4). `activity_blocks.exerciseId` —
  `COALESCE` over snapshot columns (`ActivityDao.kt:86-92`). `activity_sessions.templateId` —
  as rules. `activity_sessions.occurrenceId` — DONE occurrences survive rule retirement
  (`:259-266`); nothing in `ui`/`domain` reads a completed session's `occurrenceId` (grep).
  `schedule_occurrences.completedActivityId` — `deleteCompleted` documents "keeps its DONE
  status" (`ActivityRepository.kt:248-251`); no reader outside mappers, generators and backup (grep).
- **Conflict-strategy inventory** (seam 7, all C from the DAO files): REPLACE with cascading
  children — DB-1's five plus `replaceSessions` (`WorkoutDao.kt:474`, restore only). REPLACE on
  childless tables (harmless delete+insert) — `upsertRoutineExercise`, `replaceRoutineExercises`,
  `upsertSessionExercise`, `insertSessionExercises`, `replaceSets`, `ScheduleDao.upsert/replaceAll`,
  `GoalDao.upsert/upsertAll`, `BodyweightDao`/`TrainingBlockDao` `upsert/upsertAll`,
  `PlannerDao.upsertDecision/upsertDelivery/upsertDeliveries`, `CatalogDao.insertCredits/upsertSeedMeta`,
  `SyncDao.insertOutbox/insertOutboxRows`. IGNORE — `ExerciseDao.insertAll:79`. ABORT —
  `ExerciseDao.insert:82` (create after `getByNameKey` inside `writeExercise`, `ExerciseRepository.kt:150-171`;
  `DbMaintenance.kt:125` insert-or-update under its mutex), `ActivityDao.insertSession:166`
  (fresh id per confirm, `ActivityRules.kt:76`; `SQLiteConstraintException` caught and reported as
  "One live activity at a time.", `ActivityRepository.kt:198-200`), `insertBlocks/insertStrengthSets/insertCardioIntervals:199-206`,
  `WorkoutDao.insertSet:232` (implicit ABORT; id from `ids.newId()`, `WorkoutRepository.kt:594`).
  `@Upsert` — `PlannerDao` rules/occurrences, `SyncDao` cursor/metadata, `RoutineDao.upsertRoutineInPlace`,
  five `ActivityDao.*InPlace`. No ABORT insert is reachable twice with the same id from a user retry.
- **Seam 4, pre-migration copy correctness** (all C, no defect): runs before any Room open in a
  single-process app (`AndroidManifest.xml` declares no `android:process`; the one `<service` is
  at `:53`); `.partial` → `renameTo` on the same filesystem (`TemperPreMigrationCopy.kt:109-113`),
  each file `fd.sync()`ed (`:151-158`), folder rename not synced (documented `:43-44`); the version
  is read through SQLite read-only (`PreMigrationSnapshot.kt:126-134`) so a WAL-resident version
  bump is seen (`TemperPreMigrationCopyTest.kt:210-227`); unreadable file → no copy, unmarked,
  app proceeds (`:83-88`; ADR-010 decision 12 says exactly this); marker `KEY_CHECKED_VERSION`
  is the code version, so a failed migration followed by a fix release at the same version skips
  the copy correctly (the copy already exists) and a later bump re-checks.
- **Seam 5, FROZEN**: gates `FoundationReset.run`'s refusal (`FoundationReset.kt:59-61`) and a
  debug-only About caption (`P/ui/settings/AboutSections.kt:84-90`, shown from `SettingsScreen.kt:377`).
  Pinned by `FoundationFreezeTest.kt:11-32` (constant, no `TrainerDatabase.create` in `AppContainer`,
  no `.fallbackToDestructiveMigration` in either database, `1.json` identity hash), `FoundationResetTest.kt:65-72`
  (frozen refusal), `TemperDatabaseTest.kt:45-46` and `UpgradeInPlaceTest.kt:100-102` (both
  hard-code `VERSION == 7`, a deliberate tripwire). `exportSchema = true` on Temper (`TemperDatabase.kt:73`,
  `room.schemaLocation` at `app/build.gradle.kts:207`); `false` on the frozen Trainer (`TrainerDatabase.kt:31`).
- **Seam 8, `TrainerDatabase`**: not constructed anywhere in `main` (grep: only KDoc mentions);
  used by `SchemaV1BaselineTest`, `Migration1To2Test`, the eight in DB-13, and the two device
  tests `MigrationDeviceTest`, `SchemaV1BaselineDeviceTest`.
- **Seam 9**: nine `cmp` runs identical; `TemperSchemaAssetsTest.kt:22-31` compares `readText()`
  for Temper 1..VERSION only (Trainer's two are frozen and not covered — acceptable).

## 4. Noticed, outside my scope

- **B14 (docs truth):** `docs/architecture/CURRENT_STRUCTURE.md:194` says "Six soft foreign
  keys" and lists seven. `docs/architecture/persistence-toolchain.md:17` says `TemperDatabase`
  "is at version 4 with three migrations" (now 7 and six). `docs/architecture/foundation-generation.md:51-53`
  says "Generated `4.json` is committed. Export version is 5." `P/data/local/FoundationGeneration.kt:9-12`
  KDoc stops at v5. `app/src/androidTest/…/TemperMigrationDeviceTest.kt:13-14` and
  `T/data/local/Migration1To2Test.kt:32` call the emulator "the migration lane of record",
  contradicting ADR-032 decision 4 (JVM gates).
- **Domain/UI pass (goals):** `GoalProgress.measure` (`P/domain/MeasurableGoal.kt:46-55`) has no
  caller in `main`, and its `liftBestKg` input's intended source `WorkoutDao.observeBestWorkingWeights`
  (KDoc "P8.2 lift-target goals", `WorkoutDao.kt:379-398`) has none either — if goal progress is
  meant to be shown (ADR-025), it is not wired.
- **Sync pass (S-3 family):** `RoutineRepository.delete` enqueues the tombstone before the delete
  transaction (`P/data/repository/RoutineRepository.kt:66-67`); `deleteCustom` likewise
  (`ExerciseRepository.kt:222-223`).
- **Reminders pass (RM-):** `PlannerRepository.retireOrDeleteRuleLocked` calls `cancelReminders`
  inside the routine-delete transaction (`PlannerRepository.kt:262` → `:667-687`), which reaches the
  scheduler from inside Room's transaction.
- **B13a (main-thread cost):** `PreMigrationSnapshot.ensure` at `P/PersonalTrainerApp.kt:101`
  copies the whole `temper.db` plus WAL synchronously with `fd.sync()` on the main thread before
  the migration (by design; the cost is B13a's). `observeOccurrences()` (DB-10) is collected by
  `HomeViewModel.kt:108`.
- **UI pass (Library):** `ExerciseLibraryViewModel.confirmDelete` (`:339-358`) has no catch
  around `deleteCustom`; pairs with DB-5.

## 5. Read ledger

| File | read / skimmed / not opened |
|---|---|
| `P/data/local/TemperDatabase.kt`, `TemperMigrations.kt`, `TemperPreMigrationCopy.kt`, `PreMigrationSnapshot.kt`, `Migrations.kt`, `TrainerDatabase.kt`, `FoundationGeneration.kt`, `AppRoomDatabase.kt` | read (all 8, in full) |
| `P/data/local/dao/` ActivityDao, WorkoutDao, PlannerDao, ExerciseDao, RoutineDao, SyncDao, CatalogDao, HistoryDaos, ScheduleDao, GoalDao, ExerciseBestWeightRow, ExerciseSetRow, FinishedWorkingSetRow, RecordSetRow, SessionActivityRow | read (all 15, in full) |
| `P/data/local/entity/` (18 files) | read (all, in full) |
| `P/data/local/relation/ActivityRelations.kt`, `Relations.kt` | read |
| `app/schemas/**/TemperDatabase/1–7.json`, `TrainerDatabase/1–2.json` | skimmed by script (entities, createSql, indices, foreignKeys, identity hashes); byte-compared to the debug copies |
| `app/schemas/README.md` | read |
| `app/src/debug/assets/**/*.json` | `cmp` only |
| `T/data/local/` TemperMigration1To2…6To7Test (6), TemperMigrationFixtures, TemperSchemaAssetsTest, TemperPreMigrationCopyTest, PreMigrationSnapshotTest, PreMigrationStartupOrderTest, UpgradeInPlaceTest, FoundationFreezeTest, TemperDatabaseTest, ScheduleSlotCascadeTest, RoutineSwapTest, SchemaV1BaselineTest | read (17) |
| `T/data/local/` BackupScaleSnapshotTest, BackupV2RoundTripTest, CatalogQueriesTest, DbMaintenanceTest, FoundationExportTest, Migration1To2Test, OnboardingApplierTest, RestorePrepareTest, RestoreRecoveryTest, SessionNotesWriteTest, SessionRepairRepositoryTest, TemperHistoryBackupRoundTripTest | skimmed (test names and database class only) |
| `app/src/androidTest/…/data/local/` TemperMigrationDeviceTest, MigrationDeviceTest, SchemaV1BaselineDeviceTest | read (3) |
| Out of scope, read in part for callers: `P/data/repository/` ActivityRepository (140-381), RoutineRepository (1-190), WorkoutRepository (125-135, 215-230, 280-470), LocalBackupRepository (95-140, 330-600), ExerciseRepository (140-250), DbMaintenance (40-145), ActivityBackupIo, FoundationReset, GoalRepository; `P/data/backup/BackupValidator.kt` (290-345 + grep); `P/PersonalTrainerApp.kt` (1-140); `P/AppContainer.kt` (grep); `app/build.gradle.kts` (grep); `P/ui/activity/ActivityComposerViewModel.kt` (355-450), `LiveCardioViewModel.kt` (165-200); `P/activity/ActivityUseCases.kt`; `P/workout/StartLiveCardio.kt` (28-55); `P/domain/ActivityRules.kt` (40-130), `MeasurableGoal.kt` (38-80), `SchedulePlannerModels.kt` (115-160), `WeekBoard.kt` (120-140); `P/ui/settings/AboutSections.kt` (78-95); `P/ui/library/ExerciseLibraryViewModel.kt` (330-360); `P/data/sync/SyncEngine.kt` (grep only); `T/data/repository/FoundationResetTest.kt` (grep) | partial |

## 6. Prior-art check

Behaviour already pinned by tests in this scope: every migration step by `TemperMigration1To2Test`
… `TemperMigration6To7Test` (each with `runMigrationsAndValidate(…, validateDroppedTables = true, …)`
and a populated fixture from `TemperMigrationFixtures.kt`), the v4→v7 chain (`TemperMigration6To7Test.kt:66-81`),
the v1→v7 chain on device (`TemperMigrationDeviceTest`), the asset/test guard (`TemperSchemaAssetsTest`),
the pre-migration copy in 20 cases (`TemperPreMigrationCopyTest`), the legacy copy in 8
(`PreMigrationSnapshotTest`), the `onCreate` order (`PreMigrationStartupOrderTest`), reopen-in-place
(`UpgradeInPlaceTest`), the freeze (`FoundationFreezeTest`, `FoundationResetTest.kt:65-72`), the
routine→slot cascade (`ScheduleSlotCascadeTest`), finished-session cascade
(`SessionRepairRepositoryTest.deleteFinishedSessionCascadesExercisesAndSetLogs`), the seeder's
insert-or-update (`DbMaintenanceTest`), and sync's in-place pull (`SyncPullInPlaceTest.kt:77`).
Nothing pins DB-1 (local REPLACE callers mint fresh ids), DB-3, DB-4 or DB-14. Docs contradicting
the code, for B14: `docs/architecture/CURRENT_STRUCTURE.md:194` (six vs seven soft keys);
`docs/architecture/persistence-toolchain.md:17` (v4, three migrations); `docs/architecture/foundation-generation.md:51-53`
(`4.json`, export v5); `P/data/local/FoundationGeneration.kt:9-12` (KDoc ends at v5);
`app/src/androidTest/…/TemperMigrationDeviceTest.kt:13-14` and `T/data/local/Migration1To2Test.kt:32`
("lane of record" is the emulator, against ADR-032 decision 4).
