# Phase 3 — Schema v2 Migration

> Executor spec packet. You are a fresh session with no memory of prior planning. This
> document plus `docs/gameplan/PROTOCOL.md` (branch, PR, and sign-off rules — read it, do
> not improvise around it) is your complete instruction set. Everything under "Settled
> decisions" is decided; do not reopen it.
>
> Ground truth: all code lives on `claude/app-hierarchy-navigation-cjzigo` (or on `main`
> if Phase 0's merge already happened — check `git log main` first; `main` originally held
> only the initial commit). Work on branch `claude/phase-3-schema-v2` (matches ci.yml's
> `claude/**` trigger, `.github/workflows/ci.yml:13`). One PR. Phase closes only on owner
> sign-off.
>
> **Execution order (phase numbers are identifiers, not sequence).** The order is
> 0 → 2 → 1 → **3** → 4 → 5 → 6a → 6b → 7 → 8. Three phases are merged before you start:
> **Phase 0** (schedule-slot semantics signed), **Phase 2** (test substrate: the Robolectric
> JVM lane hosting MigrationTestHelper, `tools/preflight.sh`, the connectedDebugAndroidTest
> runbook), and **Phase 1** (session hygiene, shipped as one PR from branch
> `claude/phase-1-session-hygiene` covering both the 1A and 1B packets).
>
> **Verify those prerequisites before writing any code**, using these tests — and note that
> `docs/gameplan/PROTOCOL.md` is committed on every branch, so its presence proves NOTHING:
> - Phase 0 merged ⇔ `grep -c "Signed:" docs/ROADMAP.md` returns greater than 0 (the test
>   PROTOCOL §4 prescribes). A 0 means Phase 0 has not merged.
> - Phase 2 merged ⇔ `tools/preflight.sh` exists and
>   `app/src/test/java/com/sinura/personaltrainer/data/local/SchemaV1BaselineTest.kt` exists.
> - Phase 1 merged ⇔ the `claude/phase-1-session-hygiene` merge is in your trunk's `git log`.
>
> If any test fails, STOP and report to the owner; do not improvise substitutes for any of them.
>
> **Mandatory first commit: the re-baseline report.** Every count, line number, and repo-state
> assertion in this packet is a baseline as of audit commit 2212628, not an oracle — and three
> phases have landed since. Your first commit on the phase branch records the current trunk tip,
> the actual domain-test count and class count, and every packet literal (line references into
> `PersonalTrainerApp.kt`, `AppContainer.kt`, `BackupJson.kt`, `LocalBackupRepository.kt`,
> `CanonicalMuscle.kt`, the DAOs) that has drifted, with its verified current value. Drift fully
> explained by Phases 0/2/1 or by the game plan's own commits is EXPECTED — proceed. Stop only on
> a mismatch with no such explanation.

## 1. Mission

Ship the one reviewed additive migration this app will ever get for the v2 feature wave:
catalog columns (`equipment`, `loadType`, `movementKey`, `imageKey`, `nameKey`), three new
tables (`exercise_muscles`, `seed_meta`, `schedule_slots`), versioned catalog seeding, and
the co-evolved backup format v2 — against the owner's only real training database, with a
raw-file safety net, a rehearsed upgrade, and the heat calculator switched to weighted
junction credits. This is the highest-risk phase in the plan: the migration runs exactly
once on a phone whose history cannot be re-created. Everything downstream (Plan tab, honest
heat bands, catalog growth, imagery) waits on these tables and fields.

## 2. Read first

In order, before writing any code:

1. `docs/gameplan/PROTOCOL.md` — branch/PR/sign-off protocol. Binding.
2. `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/1.json` — the
   committed v1 baseline you migrate FROM (identityHash `6d58ad40d5c03785ab29aaf61157f369`,
   1.json:5). Never hand-edit it or its successor.
3. `app/src/main/java/com/sinura/personaltrainer/data/local/TrainerDatabase.kt` — version 1,
   `exportSchema = true` (TrainerDatabase.kt:17-28); builder has NO
   `fallbackToDestructiveMigration` (TrainerDatabase.kt:35-41) and never will.
4. All six entities in `app/src/main/java/com/sinura/personaltrainer/data/local/entity/` —
   note `ExerciseEntity` is index-free today (ExerciseEntity.kt:6-13); FK precedents:
   routine_exercises CASCADE/RESTRICT, workout_sessions SET NULL, session_exercises and
   set_logs CASCADE/RESTRICT.
5. All DAOs in `data/local/dao/` — `ExerciseDao.count()` (ExerciseDao.kt:36-37),
   `insertAll` with `IGNORE` (39-40), `replaceAll`/`deleteAll` (51-55); WorkoutDao's
   backup queries (WorkoutDao.kt:188-211).
6. `app/src/main/java/com/sinura/personaltrainer/PersonalTrainerApp.kt` — onCreate ordering
   you must slot the safety net in front of: `container = AppContainer(this)` at line 26,
   notification channels at 30, `restTimerController.rehydrate()` at 32, fire-and-forget
   `seedDefaultsIfEmpty()` on applicationScope at 33-40.
7. `app/src/main/java/com/sinura/personaltrainer/AppContainer.kt` — DB constructed at line
   23; `onBeforeRestore` tears down timer + draft cache at 59-62; safety-snapshot dir at 63.
8. `data/repository/ExerciseRepository.kt` — `seedDefaultsIfEmpty` (47-51), `createCustom`
   (53-63), `updateCustom` (65-75).
9. The whole backup stack: `data/backup/BackupDocument.kt` (BackupExercise = five fields,
   26-32), `data/backup/BackupJson.kt` (`CURRENT_VERSION = 1` at :12, decode 63-96,
   newer-version refusal 74-76, `fromJsonList` returns emptyList for missing arrays
   133-137), `data/backup/BackupValidator.kt` (validate 50-176, Gson-null philosophy in the
   header comment and `isBlank` at 193), `data/repository/LocalBackupRepository.kt`
   (createSnapshot 60-154, hasLocalData 157-161, replaceWith delete order 183-188,
   writeSafetySnapshot 301-316), `data/repository/BackupRepository.kt` (restoreFromJson
   115-135 with the in-progress guard at 120-125; exportJson 98-100).
10. `domain/MuscleLoadCalculator.kt` — `mappingFor` resolves the session-embedded
    exercise's muscleGroup FIRST, catalog second (MuscleLoadCalculator.kt:99-108, embedded
    at 104); `SECONDARY_VOLUME_WEIGHT = 0.4` (:11); `normalizeHeat` window-max relative
    (87-90) — unchanged this phase.
11. `domain/CanonicalMuscle.kt` — the enum + `MuscleNormalizer` (aliasIndex 123-129,
    `secondaryByPrimaryLabel` maps only "posterior chain" and "legs" 131-134, normalize →
    OTHER on unknown 136-144, fuzzyMatch 154-159, normalizeKey 161-162).
12. `domain/DefaultExercises.kt` — the 37 seeds (4-42) and the slug function (44-55) whose
    output ids are frozen forever.
13. `domain/Models.kt` (`Exercise` at 3-9), `data/mapper/Mappers.kt` (16-30),
    `insights/TrainingInsightsSource.kt` (five-flow combine at 59-68 — the catalog reaches
    the heat calculator as `Map<String, Exercise>`, so junction data rides the `Exercise`
    domain model, no new flow), `domain/TrainingInsights.kt` (snapshot call 91-98).
14. `docs/ROADMAP.md` Phase 4 (89-97), `docs/DESIGN_AUDIT.md` §7 (457-480) + Q-06/Q-07
    (661-662), `docs/DEVELOPMENT.md` "Database schema changes" (112-115) and "Backups are
    destructive on restore" (121-123).
15. `app/build.gradle.kts:39-42` — androidTest assets already point at `app/schemas/`.
16. `.github/workflows/ci.yml` — CI uploads `app/schemas/` as the `room-schemas` artifact and
    the debug APK. **This is NOT a route you can count on**: CI on this repo has never
    executed (account billing block, docs/DEVELOPMENT.md:95-105), and the owner's billing
    errand is non-gating. The reliable way to obtain the generated `2.json` from an SDK-less
    environment is the owner round-trip in §9 (owner runs `./gradlew :app:assembleDebug` and
    ships back `2.json` plus the generated CREATE statements). Use the `room-schemas`
    artifact only if CI is demonstrably alive.

## 3. Binding doctrine

- **DESIGN_AUDIT.md §7** (457-480): the required-fields table (`imageKey`, `equipment`
  enum, `loadType`, canonical primary/secondary muscles) and the rule "Backup JSON must
  version these fields. Old backups stay valid." **Q-06** (:661): count-0 seeding cannot
  deliver catalog updates — versioned upsert required. **Q-07** (:662): backup must carry
  the new fields. (Q-01's `exportSchema = false` note at :656 is stale — export is on and
  1.json is committed.)
- **ROADMAP.md Phase 4** (89-97): "One reviewed migration, delivered with its
  MigrationTestHelper suite."
- **DEVELOPMENT.md** (112-115): bump version, write a Migration, commit the new schema
  JSON; `fallbackToDestructiveMigration` is forbidden. (121-123): test restores on the
  emulator, never the phone holding real history.
- **UI_REDESIGN.md §8 / DIRECTION_B_INSTRUMENT §2**: no UI or token work in this phase;
  the heat *values* change, the magma ramp and BodyMap rendering do not.
- **REVISED_STRUCTURE** (the binding brief): Phase 3 definition; accepted findings 1
  (nameKey replaces the inexpressible partial index; nothing exists outside the exported
  schema JSON), 3 (backup co-evolution in this phase), 4 (raw file copy is the only
  rollback), 11 (collisions are insert-and-flag; no FK merge tool ever), 13 (batch 1 = the
  37 upgraded in place + review artifact), 16 (one DB-maintenance mutex; every restore ends
  with idempotent catalog reconciliation; hasLocalData counts schedule slots), 17 (junction
  keys normalize via the alias index, unknown → parent group never OTHER; `muscleGroup`
  survives as display text; calculator goes catalog-first); the schedule-semantics DDL.

## 4. Settled decisions

State these as fact in code comments and the PR description. Do not redesign.

**S1 — No index or table may exist outside the exported Room schema JSON.** Every table is
an `@Entity`, every index is declared via `@Index`/`@ColumnInfo`; the migration creates
exactly what Room's expected schema declares, byte-for-byte (Room 2.6.1,
`gradle/libs.versions.toml:10`, validates at open and an extra or missing object is a
permanent crash loop on a phone with no destructive fallback). The old "partial unique
index on lower(name)" idea is dead; uniqueness of built-in names is a seed-data invariant
test plus app-layer checks (S8).

**S2 — New `exercises` columns** (all added by `MIGRATION_1_2`; `@ColumnInfo(defaultValue)`
declared on the entity fields and the SQL `DEFAULT` clauses must match those strings
exactly, or `validateMigrations` fails on the default-value diff):

| Column | Type | Default | Notes |
|---|---|---|---|
| `equipment` | `TEXT NOT NULL` | `'OTHER'` | storage form of `EquipmentType` |
| `loadType` | `TEXT NOT NULL` | `'EXTERNAL'` | storage form of `LoadType` |
| `movementKey` | `TEXT` (nullable) | — | family key from the closed vocabulary (S6) |
| `imageKey` | `TEXT` (nullable) | — | stays NULL everywhere this phase; null = "compose a thumbnail" in Phase 8 |
| `nameKey` | `TEXT NOT NULL` | `''` | normalized name; plain **non-unique** declared `@Index("nameKey")` |

Annotation values, exactly: `@ColumnInfo(defaultValue = "OTHER")` on `equipment`,
`@ColumnInfo(defaultValue = "EXTERNAL")` on `loadType`, `@ColumnInfo(defaultValue = "''")`
on `nameKey` (and `@ColumnInfo(defaultValue = "'[]'")` on `seed_meta.pendingCollisions`,
S4/WI-1). Room wraps unquoted TEXT defaults in single quotes at export time, so the
generated `2.json` createSql reads `DEFAULT 'OTHER'` / `DEFAULT 'EXTERNAL'` / `DEFAULT ''`
/ `DEFAULT '[]'`. The committed `2.json` is the byte-level arbiter: after generating it,
copy its `DEFAULT` clauses into the migration SQL verbatim rather than reasoning about
quoting.

`nameKey` normalization (one function, used everywhere): `name.trim().lowercase()` with
internal whitespace runs collapsed to a single space — exactly `MuscleNormalizer.normalizeKey`
semantics (CanonicalMuscle.kt:161-162); punctuation is kept ("Push-Up" → `push-up`). That
function is `private` today; WI-3 exposes it (public `normalizeKey`, or a public `nameKeyOf`
delegating to it) and every nameKey writer calls that one function — nobody re-implements it. The
migration backfills `UPDATE exercises SET nameKey = LOWER(TRIM(name))` (ASCII-safe for this
catalog); the first reconciliation pass rewrites every row with the Kotlin normalizer.

**S3 — Enums** (new file `domain/ExerciseTraits.kt`):

```kotlin
enum class EquipmentType { BARBELL, DUMBBELL, CABLE, MACHINE, SMITH, KETTLEBELL, BAND, BODYWEIGHT, OTHER }
enum class LoadType { EXTERNAL, STACK, BODYWEIGHT, BODYWEIGHT_PLUS, ASSISTED }
```

Stored as their `.name` strings. Each has `fromStorage(raw: String?): X` returning
`OTHER`/`EXTERNAL` on unknown input — DB junk must never crash a mapper. (Equipment set is
DESIGN_AUDIT §7's list verbatim; `BODYWEIGHT_PLUS` is the brief's weighted-bodyweight kind
for pull-ups/dips; `machineKind` and `defaultRestSeconds` from §7 are deliberately NOT in
v2 — cut, out of scope.)

**S4 — New tables** (DDL is normative; entity annotations must generate exactly this):

```sql
CREATE TABLE IF NOT EXISTS `exercise_muscles` (
  `exerciseId` TEXT NOT NULL,
  `muscleKey` TEXT NOT NULL,
  `weight` REAL NOT NULL,
  PRIMARY KEY(`exerciseId`, `muscleKey`),
  FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_exercise_muscles_muscleKey` ON `exercise_muscles` (`muscleKey`);

CREATE TABLE IF NOT EXISTS `seed_meta` (
  `id` INTEGER NOT NULL,
  `catalogVersion` INTEGER NOT NULL,
  `pendingCollisions` TEXT NOT NULL DEFAULT '[]',
  PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `schedule_slots` (
  `id` TEXT NOT NULL,
  `position` INTEGER NOT NULL,
  `routineId` TEXT,
  `focusKind` TEXT,
  `anchorDay` INTEGER,
  `createdAt` INTEGER NOT NULL,
  `updatedAt` INTEGER NOT NULL,
  PRIMARY KEY(`id`),
  FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_schedule_slots_routineId` ON `schedule_slots` (`routineId`);
```

Semantics (signed in Phase 0, restated): a slot is a routine slot (`routineId` set), a
focus-only slot (`focusKind` set), never neither; absent slot = rest. `position` orders the
cycle. `anchorDay` 0-6 (Monday=0) is a *preference*; the effective week is derived, never
written back. Routine deletion CASCADEs its slots. Phase 3 creates the table, backup
carries it, `hasLocalData` counts it — **no code writes slots until Phase 4**. The
`exercise_muscles` primary row is the one with `weight = 1.0`; there is no `isPrimary`
column. `seed_meta` is a single row `id = 1`. `pendingCollisions` is a JSON array of
`{"builtInId": "...", "customId": "...", "nameKey": "..."}` — flag storage only; the
"needs attention" UI is Phase 7.

**S5 — Muscle credit model.** `muscleKey` values are `CanonicalMuscle.name.lowercase()`
(`"chest"`, `"back"`, `"shoulders"`, `"biceps"`, `"triceps"`, `"quadriceps"`,
`"hamstrings"`, `"glutes"`, `"calves"`, `"core"`). Weights: exactly one primary per
exercise with `weight = 1.0` (this is the band-model definition from the brief — a set
credits each muscle by its junction weight, primary 1.0 — and trivially satisfies the
signed `primary ≥ 0.5` floor); each secondary weight in `(0.0, 0.5]`; the secondaries of
one exercise sum to `≤ 1.0`. Derived secondaries (fallback and v1-upgrade paths) use
`SECONDARY_VOLUME_WEIGHT = 0.4` (MuscleLoadCalculator.kt:11), which survives as the single
derived-secondary constant. Resolution contract: a `muscleKey` resolves through
`MuscleNormalizer`'s alias index (underscores treated as spaces before lookup); a key the
index cannot place falls back to the exercise's `muscleGroup` primary; any future
sub-muscle key (e.g. `front_delt`) MUST be covered by its parent's aliases in the same
change — the invariant test makes an unmapped batch-1 key a build failure. `OTHER` is never
a resolution target for a recognizable key; `exercises.muscleGroup` survives untouched as
denormalized display text (planner, filters, and v1 backups still consume it). **Spelling is
part of the contract**: a `muscleKey` is exactly `CanonicalMuscle.name.lowercase()` — the
invariant test asserts that as an exact match, and Phase 7's tail batches must use the same
spellings (`"quadriceps"`, never `"quads"`).

**S6 — movementKey vocabulary: FAMILY keys, not movement patterns** (closed set for batch 1;
Phase 7 extends it with the tail families, never re-keys these):
`squat`, `lunge`, `leg-press`, `leg-extension`, `deadlift`, `romanian-deadlift`, `hip-thrust`,
`leg-curl`, `calf-raise`, `bench-press`, `push-up`, `chest-fly`, `overhead-press`,
`lateral-raise`, `rear-delt`, `row`, `pulldown`, `pull-up`, `curl`, `triceps-extension`,
`plank`, `leg-raise`, `crunch` (23 families).

A `movementKey` names the **lift family** — the set of exercises that are siblings of each other
(Barbell Bench Press, Incline Bench Press, Dumbbell Bench Press and Close-Grip Bench Press are all
`bench-press`) — not the biomechanical pattern. Why this and not pattern keys (`hinge`,
`press-horizontal`, …): there is ONE movementKey vocabulary in this app from day one, and it is the
one Phase 7's Library grouping and sibling-swap ride on. Shipping pattern keys here would force an
unauthorized re-keying of already-migrated user data in Phase 7 and would make Phase 7's own named
tests (e.g. "the `bench-press` family has 8 members" — 4 from batch 1, 4 from batch 2) unpassable.

**S7 — Versioned seeding.** `DefaultExercises.CATALOG_VERSION = 2`. Upsert-by-id: UPDATE
built-ins in place (name, muscleGroup, equipment, loadType, movementKey, nameKey — preserve
`notes`, `isCustom`, `imageKey`), INSERT new ids, **never DELETE**, **never re-slug** (the
37 ids from DefaultExercises.kt:44-55 are frozen; the invariant test pins them literally).
Built-in junction rows are replaced wholesale from the catalog on every seed pass. Name
collision between a built-in and a custom: **insert the built-in anyway and flag** the pair
in `seed_meta.pendingCollisions` — the seeder never resolves, renames, merges, or deletes
anything; Phase 7 builds the surface. All of seed / restore / reconciliation runs behind
ONE `kotlinx.coroutines.sync.Mutex` (S9) — the startup seed (PersonalTrainerApp.kt:33-40 is
fire-and-forget and today races restore) and `BackupRepository.restoreFromJson` serialize
through it.

**S8 — Built-in name uniqueness** is enforced by (a) the seed invariant test
`nameKeysAreUniqueAmongBuiltIns` and (b) app-layer checks: `createCustom`/`updateCustom`
refuse a name whose `nameKey` equals any existing exercise's `nameKey`, returning a sealed
result the three calling ViewModels (ExerciseLibraryViewModel.kt:148-151,
RoutineEditorViewModel.kt:308, ActiveWorkoutViewModel.kt:464) surface through their
existing `error.value` channels with one new string (WI-3) — no new UI.

**S9 — Restore/seed choreography.** Every restore (any document version) ends with the
idempotent catalog-reconciliation pass: reset `seed_meta.catalogVersion` to 0 inside the
restore transaction, then run the seed pass (re-seed missing built-ins, refresh built-in
rows and junction credits, derive junction rows for customs that have none, rewrite all
nameKeys, recompute `pendingCollisions` from scratch). `seed_meta` is never trusted across
a restore. `hasLocalData` additionally counts schedule slots.

**S10 — Backup v2.** `BackupJson.CURRENT_VERSION = 2`. `decode` accepts versions 1..2 and
ALWAYS returns a fully-populated v2-shaped document (the `version` field keeps the source
version for provenance). Null/absent `equipment`/`loadType` default to `OTHER`/`EXTERNAL`
during decode for any version (Gson leaves nulls in non-null Kotlin fields — every new
document field is declared nullable and defaulted in one normalization step, matching the
existing validator philosophy, BackupValidator.kt:26-35). For version-1 documents, decode
derives `exerciseMuscles` from each exercise's `muscleGroup` via the shared
`MuscleNormalizer.deriveCredits` (primary 1.0, secondaries 0.4). `nameKey` is NOT in the
document — recomputed on restore. v1 code already refuses v2 files (BackupJson.kt:74-76,
BackupValidator.kt:180-182) — that is by design and part of why S11 is the only rollback.

**S11 — Pre-open raw copy is the ONLY rollback path.** A raw file copy of
`personal_trainer.db` + `-wal` + `-shm`, taken in `Application.onCreate` before ANY
container/DB touch, gated on a SharedPreferences schema marker. Keep exactly one pre-v2
copy, never overwritten, never auto-deleted. Room refuses downgrades and v1 code refuses v2
JSON, so this copy (restorable by a future corrective release, or by re-signed manual
surgery) is the entire story if the migration corrupts data. It lives in app-private
storage and dies with an uninstall — the runbook therefore also has the owner take a fresh
JSON export immediately before the real-phone upgrade.

**S12 — The migration transforms no data** beyond the `nameKey` backfill. Junction rows,
catalog upgrades, and collision detection happen in Kotlin, behind the mutex, at first v2
startup — where `MuscleNormalizer` lives and where a failure is a logged no-op instead of a
bricked open. The heat calculator's muscleGroup fallback keeps the Body tab correct in the
window between migration and the first seed pass.

**S13 — Heat resolution order flips to catalog-first**: catalog junction credits → embedded
junction credits → embedded `muscleGroup` derivation → catalog `muscleGroup` derivation →
OTHER (unmapped, off-map, unchanged). Window-max normalization (normalizeHeat, 87-90) and
the magma ramp are untouched — bands are Phase 5.

## 5. Work items

### WI-1 — v2 entities, DAOs, and MIGRATION_1_2

**Build:**
- Modify `data/local/entity/ExerciseEntity.kt`: add the five S2 fields with
  `@ColumnInfo(defaultValue = ...)` exactly per S2's annotation-values note, and
  `indices = [Index("nameKey")]` on the `@Entity`. Give the new fields Kotlin default
  values mirroring S2 (`equipment: String = "OTHER"`, `loadType: String = "EXTERNAL"`,
  `movementKey: String? = null`, `imageKey: String? = null`, `nameKey: String = ""`) so
  the positional `ExerciseEntity(it.id, it.name, it.muscleGroup, it.notes, it.isCustom)`
  construction at LocalBackupRepository.kt:193 and `Exercise.toEntity()` at
  Mappers.kt:24-30 keep compiling until WI-4/WI-5 rewrite them.
- New `data/local/entity/ExerciseMuscleEntity.kt`, `SeedMetaEntity.kt`,
  `ScheduleSlotEntity.kt` generating exactly the S4 DDL:

```kotlin
@Entity(tableName = "exercise_muscles", primaryKeys = ["exerciseId", "muscleKey"],
    foreignKeys = [ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"],
        childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("muscleKey")])
data class ExerciseMuscleEntity(val exerciseId: String, val muscleKey: String, val weight: Double)

@Entity(tableName = "seed_meta")
data class SeedMetaEntity(
    @PrimaryKey val id: Int = 1,
    val catalogVersion: Int,
    @ColumnInfo(defaultValue = "'[]'") val pendingCollisions: String = "[]",
)

@Entity(tableName = "schedule_slots",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"],
        childColumns = ["routineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routineId")])
data class ScheduleSlotEntity(
    @PrimaryKey val id: String, val position: Int, val routineId: String?,
    val focusKind: String?, val anchorDay: Int?, val createdAt: Long, val updatedAt: Long,
)
```

- New `data/local/dao/CatalogDao.kt`: `observeAllCredits(): Flow<List<ExerciseMuscleEntity>>`,
  `suspend getAllCredits()`, `suspend replaceCreditsFor(exerciseId, rows)` (delete-then-insert
  in a `@Transaction`), `suspend insertCredits(rows)` (REPLACE), `suspend deleteAllCredits()`,
  `suspend getSeedMeta(): SeedMetaEntity?`, `suspend upsertSeedMeta(meta)` (REPLACE).
- New `data/local/dao/ScheduleDao.kt`: `suspend count(): Int`, `suspend getAll()`,
  `suspend replaceAll(rows)` (REPLACE), `suspend deleteAll()`. (Phase 4 adds the observe
  queries; keep this minimal.)
- Modify `data/local/TrainerDatabase.kt`: `version = 2`, register the three entities and
  two DAOs, and new `data/local/Migrations.kt` holding `val MIGRATION_1_2 = object :
  Migration(1, 2)` whose `migrate` runs, in order: the five `ALTER TABLE exercises ADD
  COLUMN` statements (defaults per S2), `UPDATE exercises SET nameKey = LOWER(TRIM(name))`,
  `CREATE INDEX index_exercises_nameKey`, then the three S4 `CREATE TABLE` + two
  `CREATE INDEX` statements, then `INSERT INTO seed_meta (id, catalogVersion,
  pendingCollisions) VALUES (1, 0, '[]')` (catalogVersion 0 forces the first seed pass to
  treat the migrated DB as un-upgraded). Register via `.addMigrations(MIGRATION_1_2)` in
  `TrainerDatabase.create`. Nothing else — no data transforms (S12).
- Build once and **commit** `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/2.json`.
  Provenance, in order of preference: (1) `./gradlew :app:assembleDebug` on a machine with the
  Android SDK — normally the owner's, via the §9 mid-phase round-trip, since the executor
  environment cannot build; (2) the `room-schemas` CI artifact ONLY if CI is demonstrably alive
  (it has never run — billing). Then diff every CREATE/INDEX statement in 2.json against your
  migration SQL — they must be equivalent per S1. **You cannot finish the migration SQL without
  this file**; see §9 for how the round-trip is sequenced.

**Tests** (Robolectric JVM lane, same source set/package as the Phase-2 smoke migration
test; file `Migration1To2Test.kt`):

> **Host-OS constraint — read before debugging a schema-validation failure.** Robolectric 4.14.1
> defaults to NATIVE SQLite on every host EXCEPT Windows, where `SQLiteModeConfigurer.defaultValue()`
> hard-falls back to LEGACY mode (SQLite 3.7.10). LEGACY's `PRAGMA table_info` cannot express
> composite primary keys, so Room's schema validation fails FALSELY for any entity with a compound
> PK — which is exactly `exercise_muscles` (`PRIMARY KEY(exerciseId, muscleKey)`, S4). Run
> `Migration1To2Test` on macOS or Linux. On a Windows host the JVM lane is not a valid migration
> lane at all and `connectedDebugAndroidTest` (the Phase-2 device lane) is the only one. A
> `validateMigrations` failure that names `exercise_muscles` primary keys on a Windows machine is
> the harness, not your migration — re-run it on a supported host before changing any SQL.

- `migratesEmptyV1Database` — MigrationTestHelper creates v1 from 1.json, runs
  MIGRATION_1_2 with `validateMigrations`, asserts seed_meta row (1, 0, '[]').
- `migratesPopulatedV1DatabaseWithHistory` — inserts into the v1 DB: 2 built-in-shaped
  exercises + 1 custom, 1 routine + routine_exercises, 2 sessions (one finished, one
  in-progress) + session_exercises + set_logs; migrates; asserts every row survives, new
  columns hold defaults, `nameKey = lower(trim(name))` for each row.
- `foreignKeyIntegrityAfterMigration` — `PRAGMA foreign_key_check` returns zero rows on the
  populated DB.
- `indexesMatchDeclaredSchema` — `PRAGMA index_list` on exercises, exercise_muscles,
  schedule_slots, filtered to rows with origin `'c'` (SQLite auto-creates
  `sqlite_autoindex_*` rows with origin `'pk'` for the TEXT and composite primary keys —
  those are expected and ignored), contains exactly the declared indexes
  (`index_exercises_nameKey`, `index_exercise_muscles_muscleKey`,
  `index_schedule_slots_routineId`) and nothing extra.

### WI-2 — Pre-open safety net

**Build:** new `data/local/PreMigrationSnapshot.kt`:

```kotlin
object PreMigrationSnapshot {
    const val PREFS_NAME = "schema_marker"
    const val KEY_LAST_OPENED_SCHEMA = "lastOpenedSchemaVersion"
    const val TARGET_SCHEMA = 2
    /** Call FIRST in Application.onCreate, before AppContainer exists. Synchronous, runs
     *  real work at most once per install lifetime. Never throws. */
    fun ensure(context: Context)
}
```

Logic: read the marker from `SharedPreferences(PREFS_NAME)`. If it already equals
`TARGET_SCHEMA`, return. If `context.getDatabasePath("personal_trainer.db")` exists and the
copy directory `File(context.filesDir, "pre-migration/v1")` does NOT exist: create it, copy
`personal_trainer.db`, `personal_trainer.db-wal`, `personal_trainer.db-shm` (each if
present) byte-for-byte, then write the marker. If the copy dir already exists, do not touch
it (retention: exactly one pre-v2 copy, kept forever — S11) — just write the marker and
return (the copy was taken by an earlier partial run). If no DB file exists (fresh
install), just write the marker. Copy failure: log via `AppLog.e`, do NOT write the marker
(so the next launch retries), and return — the app must never be blocked from opening.

Modify `PersonalTrainerApp.onCreate` (PersonalTrainerApp.kt:24-41):
`PreMigrationSnapshot.ensure(this)` becomes the first statement after `super.onCreate()`,
BEFORE `container = AppContainer(this)` — AppContainer builds the Room instance at
AppContainer.kt:23 and the rest-timer rehydrate + seed launch follow at 32-40; nothing may
precede the copy. Replace the `seedDefaultsIfEmpty()` launch body with
`container.dbMaintenance.seedCatalog()` (WI-3), same try/catch shape.

**Tests** (Robolectric): `PreMigrationSnapshotTest.copiesDbAndSidecarFilesOnce`,
`.secondRunDoesNotOverwriteExistingCopy`, `.freshInstallWritesMarkerWithoutCopy`,
`.copyFailureLeavesMarkerUnset` (use a read-only dir or nonexistent parent to force
failure).

### WI-3 — Versioned seeding, mutex, catalog v2 payload, review artifact

**Build:**
- Rewrite `domain/DefaultExercises.kt`: `CATALOG_VERSION = 2`; a `SeedExercise` data class
  `(id, name, muscleGroup, equipment: EquipmentType, loadType: LoadType, movementKey:
  String?, credits: List<MuscleCredit>)` — keep the property names `name` and
  `muscleGroup` exactly: `MuscleNormalizerTest.kt:10-14` iterates
  `DefaultExercises.catalog()` reading `.name`/`.muscleGroup` and must keep compiling;
  `fun catalog(): List<SeedExercise>` transcribing the batch-1 table below EXACTLY (do not
  re-derive, re-balance, or "improve" any value; the review artifact is where corrections
  happen). Keep the slug helper only as a private assertion aid — ids are now literal
  strings.
- New `domain/ExerciseTraits.kt` (S3 enums + `data class MuscleCredit(val muscleKey:
  String, val weight: Double)`).
- **Expose the name normalizer — required, do not skip.** `normalizeKey` is declared
  `private fun normalizeKey` at `domain/CanonicalMuscle.kt:161`, but S2's "one function, used
  everywhere" rule needs it OUTSIDE `MuscleNormalizer`: `ExerciseRepository`'s duplicate check
  (S8), `Mappers.toEntity` (WI-5), `LocalBackupRepository`'s insert path (WI-4), and
  `DbMaintenance`'s reconciliation pass (this WI) all compute a `nameKey`. Either make
  `normalizeKey` public, or add a public `fun nameKeyOf(name: String): String` on
  `MuscleNormalizer` that delegates to it — one or the other, your choice, then say which in
  the PR. **Every nameKey writer calls that ONE function.** No caller may re-implement
  `trim`/`lowercase`/whitespace-collapse locally; a second implementation is exactly how the
  `nameKey` index and the duplicate check drift apart. (The migration's SQL backfill
  `LOWER(TRIM(name))` is the one deliberate exception — it runs before any Kotlin can, and the
  first reconciliation pass rewrites every row through the real function, per S2.)
- Add `MuscleNormalizer.deriveCredits(muscleGroup: String): List<MuscleCredit>` in
  `domain/CanonicalMuscle.kt`: primary from `normalize(muscleGroup).primary` at 1.0,
  each secondary at `MuscleLoadCalculator.SECONDARY_VOLUME_WEIGHT` (0.4); primary OTHER →
  single credit `("other", 1.0)` (keeps current off-map behavior).
- New `data/repository/DbMaintenance.kt`:

```kotlin
class DbMaintenance(private val database: TrainerDatabase) {
    private val mutex = Mutex()
    suspend fun <T> withMaintenanceLock(block: suspend () -> T): T
    /** Idempotent versioned seed + reconciliation. Safe to run on every process start. */
    suspend fun seedCatalog()               // takes the lock itself
    /** Restore epilogue: caller already holds the lock via withMaintenanceLock. */
    suspend fun reconcileCatalogLocked()
}
```

  `seedCatalog` = `withMaintenanceLock { reconcileCatalogLocked() }` unless
  `seed_meta.catalogVersion >= CATALOG_VERSION` (then no-op); that version check runs
  INSIDE the lock — a check-then-act outside it could race a concurrent restore that
  resets catalogVersion to 0. `reconcileCatalogLocked`
  runs inside `database.withTransaction`, per S7/S9: upsert the 37 by id; replace built-in
  junction rows; derive junction rows for junction-less customs via `deriveCredits`;
  rewrite every `nameKey` with the Kotlin normalizer; recompute `pendingCollisions` from
  scratch (built-in nameKey shared with ≥1 custom → JSON entries per S4); write
  `catalogVersion = CATALOG_VERSION`. Never deletes an exercise, never touches ids, never
  edits customs beyond nameKey/junction derivation.
- `AppContainer.kt`: construct `val dbMaintenance = DbMaintenance(database)` and pass it
  into `BackupRepository` (WI-4). `ExerciseRepository.seedDefaultsIfEmpty` is deleted;
  count-0 seeding is gone (Q-06 closed).
- `ExerciseRepository` (S8): `createCustom`/`updateCustom` compute `nameKey`, query
  `ExerciseDao` for an existing row with that nameKey (new
  `@Query("SELECT * FROM exercises WHERE nameKey = :nameKey LIMIT 1")`), and return
  `sealed class SaveExerciseResult { data class Saved(val exercise: Exercise);
  data class DuplicateName(val existing: Exercise) }`. Update the three ViewModel call
  sites to set the NEW one-line literal "That name is already in your library" into each
  ViewModel's existing `error.value` channel on `DuplicateName` (the string does not exist
  in the codebase today; what stays unchanged is the rendering path — each screen's
  existing error surface — so no new composables).
- New `domain/CatalogReviewRenderer.kt` (pure): renders
  `docs/gameplan/artifacts/catalog-v2-review.md` from `DefaultExercises.catalog()` —
  grouped by movementKey family, one table per family with columns
  `Lift | Equipment | Load | Primary | Secondaries (weight) | v1 muscleGroup | Changed vs v1`,
  where `v1 muscleGroup` is the literal v1 string (they are unchanged — the "diff" column
  records what each row GAINED: fields + credits). Header states the invariants and asks
  the owner to correct any equipment/credit judgment call by editing the packet table and
  re-running. Commit the rendered file.

**Batch-1 catalog (normative data — transcribe verbatim).** `nameKey` = the Name lowercased
(S2 rule). Primary always weight 1.0. muscleGroup column = current v1 value, unchanged. The
`movementKey` column holds S6 **family** keys (23 distinct families across these 37 rows) —
these are the keys Phase 7 groups and sibling-swaps on; they are shipped correct here and are
never re-keyed later.

| # | id | Name | v1 muscleGroup | Equipment | LoadType | movementKey | Primary | Secondaries (key weight) |
|---|---|---|---|---|---|---|---|---|
| 1 | ex-barbell-back-squat | Barbell Back Squat | Quads | BARBELL | EXTERNAL | squat | quadriceps | glutes 0.50, hamstrings 0.25, core 0.25 |
| 2 | ex-front-squat | Front Squat | Quads | BARBELL | EXTERNAL | squat | quadriceps | glutes 0.50, core 0.25 |
| 3 | ex-goblet-squat | Goblet Squat | Quads | DUMBBELL | EXTERNAL | squat | quadriceps | glutes 0.50, core 0.25 |
| 4 | ex-bulgarian-split-squat | Bulgarian Split Squat | Quads | DUMBBELL | EXTERNAL | lunge | quadriceps | glutes 0.50, hamstrings 0.25 |
| 5 | ex-walking-lunge | Walking Lunge | Quads | DUMBBELL | EXTERNAL | lunge | quadriceps | glutes 0.50, hamstrings 0.25 |
| 6 | ex-leg-press | Leg Press | Quads | MACHINE | EXTERNAL | leg-press | quadriceps | glutes 0.50 |
| 7 | ex-leg-extension | Leg Extension | Quads | MACHINE | STACK | leg-extension | quadriceps | — |
| 8 | ex-conventional-deadlift | Conventional Deadlift | Posterior chain | BARBELL | EXTERNAL | deadlift | glutes | hamstrings 0.50, back 0.50 |
| 9 | ex-romanian-deadlift | Romanian Deadlift | Hamstrings | BARBELL | EXTERNAL | romanian-deadlift | hamstrings | glutes 0.50, back 0.25 |
| 10 | ex-trap-bar-deadlift | Trap Bar Deadlift | Posterior chain | BARBELL | EXTERNAL | deadlift | glutes | hamstrings 0.50, quadriceps 0.25, back 0.25 |
| 11 | ex-hip-thrust | Hip Thrust | Glutes | BARBELL | EXTERNAL | hip-thrust | glutes | hamstrings 0.25 |
| 12 | ex-leg-curl | Leg Curl | Hamstrings | MACHINE | STACK | leg-curl | hamstrings | — |
| 13 | ex-standing-calf-raise | Standing Calf Raise | Calves | MACHINE | STACK | calf-raise | calves | — |
| 14 | ex-barbell-bench-press | Barbell Bench Press | Chest | BARBELL | EXTERNAL | bench-press | chest | triceps 0.50, shoulders 0.25 |
| 15 | ex-incline-bench-press | Incline Bench Press | Chest | BARBELL | EXTERNAL | bench-press | chest | shoulders 0.50, triceps 0.25 |
| 16 | ex-dumbbell-bench-press | Dumbbell Bench Press | Chest | DUMBBELL | EXTERNAL | bench-press | chest | triceps 0.50, shoulders 0.25 |
| 17 | ex-push-up | Push-Up | Chest | BODYWEIGHT | BODYWEIGHT | push-up | chest | triceps 0.50, shoulders 0.25, core 0.25 |
| 18 | ex-chest-fly | Chest Fly | Chest | DUMBBELL | EXTERNAL | chest-fly | chest | shoulders 0.25 |
| 19 | ex-overhead-press | Overhead Press | Shoulders | BARBELL | EXTERNAL | overhead-press | shoulders | triceps 0.50, core 0.25 |
| 20 | ex-seated-dumbbell-press | Seated Dumbbell Press | Shoulders | DUMBBELL | EXTERNAL | overhead-press | shoulders | triceps 0.50 |
| 21 | ex-lateral-raise | Lateral Raise | Shoulders | DUMBBELL | EXTERNAL | lateral-raise | shoulders | — |
| 22 | ex-face-pull | Face Pull | Rear delts | CABLE | STACK | rear-delt | shoulders | back 0.50 |
| 23 | ex-barbell-row | Barbell Row | Back | BARBELL | EXTERNAL | row | back | biceps 0.50, shoulders 0.25 |
| 24 | ex-pendlay-row | Pendlay Row | Back | BARBELL | EXTERNAL | row | back | biceps 0.50 |
| 25 | ex-one-arm-dumbbell-row | One-Arm Dumbbell Row | Back | DUMBBELL | EXTERNAL | row | back | biceps 0.50 |
| 26 | ex-lat-pulldown | Lat Pulldown | Back | CABLE | STACK | pulldown | back | biceps 0.50 |
| 27 | ex-pull-up | Pull-Up | Back | BODYWEIGHT | BODYWEIGHT_PLUS | pull-up | back | biceps 0.50, core 0.25 |
| 28 | ex-chin-up | Chin-Up | Back | BODYWEIGHT | BODYWEIGHT_PLUS | pull-up | back | biceps 0.50, core 0.25 |
| 29 | ex-seated-cable-row | Seated Cable Row | Back | CABLE | STACK | row | back | biceps 0.50 |
| 30 | ex-barbell-curl | Barbell Curl | Biceps | BARBELL | EXTERNAL | curl | biceps | — |
| 31 | ex-dumbbell-curl | Dumbbell Curl | Biceps | DUMBBELL | EXTERNAL | curl | biceps | — |
| 32 | ex-tricep-pushdown | Tricep Pushdown | Triceps | CABLE | STACK | triceps-extension | triceps | — |
| 33 | ex-skull-crusher | Skull Crusher | Triceps | BARBELL | EXTERNAL | triceps-extension | triceps | — |
| 34 | ex-close-grip-bench-press | Close-Grip Bench Press | Triceps | BARBELL | EXTERNAL | bench-press | triceps | chest 0.50, shoulders 0.25 |
| 35 | ex-plank | Plank | Core | BODYWEIGHT | BODYWEIGHT | plank | core | — |
| 36 | ex-hanging-leg-raise | Hanging Leg Raise | Core | BODYWEIGHT | BODYWEIGHT | leg-raise | core | — |
| 37 | ex-cable-crunch | Cable Crunch | Core | CABLE | STACK | crunch | core | — |

**Tests** (pure JVM domain lane, new `DefaultExercisesTest.kt` + Robolectric for the seeder):
- `catalogHasExactly37EntriesAtVersion2`
- `everyEntryHasExactlyOnePrimaryWithWeightOne`
- `secondaryWeightsAreInHalfOpenRangeAndSumAtMostOne` (each in (0, 0.5], per-exercise sum ≤ 1.0)
- `nameKeysAreUniqueAmongBuiltIns`
- `idsMatchFrozenV1Slugs` (compare against the literal 37-id list — never re-slugged)
- `allMuscleKeysNormalizeToCanonicalNonOther` — asserts BOTH that every `muscleKey` in the
  catalog normalizes to a non-`OTHER` `CanonicalMuscle` AND, exact-match, that
  `muscleKey == CanonicalMuscle.<X>.name.lowercase()` for some `X` (S5). The exact-match half
  is what makes spelling drift a build failure rather than a silently-aliased key: `"quads"`
  normalizes fine and would slip past the weaker assertion, but is not a legal `muscleKey`.
- `movementKeysComeFromTheClosedVocabulary` — asserts every catalog `movementKey` is a member
  of S6's 23-family set, held as a literal set in the test. Phase 7 extends that literal with
  its tail families; it never re-keys a batch-1 row.
- `CatalogReviewArtifactTest.artifactMatchesCatalog` (golden-file: renderer output equals
  the committed `docs/gameplan/artifacts/catalog-v2-review.md`; test resolves the path
  relative to the `app/` working dir as `../docs/...`)
- `DbMaintenanceTest` (Robolectric): `seedIsIdempotent` (run twice, identical DB),
  `upsertUpdatesBuiltInInPlacePreservingNotes`, `customCollisionIsInsertedAndFlagged`
  (custom named "Barbell Curl" pre-exists → both rows present, pendingCollisions has the
  pair), `seederNeverDeletesRows`, `junctionDerivedForJunctionlessCustoms`,
  `seedSkipsWhenCatalogVersionCurrent`, `concurrentSeedAndRestoreSerialize` (launch both
  under the mutex; assert no interleaved partial state).

### WI-4 — Backup format v2

**Build:**
- `BackupDocument.kt`: `BackupExercise` gains `equipment: String?`, `loadType: String?`,
  `movementKey: String?`, `imageKey: String?`. New
  `data class BackupExerciseMuscle(val exerciseId: String, val muscleKey: String, val weight: Double)`
  and `data class BackupScheduleSlot(val id: String, val position: Int, val routineId:
  String?, val focusKind: String?, val anchorDay: Int?, val createdAt: Long, val
  updatedAt: Long)`; `BackupDocument` gains `exerciseMuscles: List<BackupExerciseMuscle> =
  emptyList()` and `scheduleSlots: List<BackupScheduleSlot> = emptyList()`.
- `BackupJson.kt`: `CURRENT_VERSION = 2`; `encode` sorts the two new lists (credits by
  exerciseId then muscleKey; slots by position then id); `decode` parses the new arrays via
  the existing `fromJsonList` (missing → emptyList, so v1 files just work), then runs one
  normalization step per S10: default equipment/loadType, and — when `version == 1` —
  derive `exerciseMuscles` from each exercise's muscleGroup via
  `MuscleNormalizer.deriveCredits`. Returned document is always v2-shaped.
- `BackupValidator.kt` new rules (after the existing exercise loop, reusing `invalid()`):
  equipment/loadType not in the enum storage sets → invalid ("an exercise has an unknown
  equipment/load type"); per credit: blank muscleKey, exerciseId not in exerciseIds,
  duplicate (exerciseId, muscleKey), or weight `NaN`/`∞`/`≤ 0.0`/`> 1.0` → invalid; per
  slot: blank or duplicate id, `position < 0`, `anchorDay` non-null outside 0..6,
  routineId non-null and not in routineIds, or routineId AND focusKind both null →
  invalid; timestamps below `MIN_PLAUSIBLE_EPOCH_MS` invalid. `BackupSummary` shape is
  unchanged.
- `LocalBackupRepository.kt`:
  - `createSnapshot` reads and emits the two new tables (inside the same transaction) and
    the new exercise columns.
  - `replaceWith` delete order becomes: sets, session_exercises, sessions,
    **schedule_slots**, routine_exercises, routines, **exercise_muscles**, exercises, then
    reset seed_meta to `(1, 0, '[]')`. Insert order: exercises (computing `nameKey` from
    name — it is not in the document), routines, routine_exercises, **schedule_slots**,
    sessions, session_exercises, set_logs, **exercise_muscles**.
  - `hasLocalData` (157-161) adds `database.scheduleDao().count() > 0` (S9 — a user whose
    only authored state is a pinned week must not look empty).
- `BackupRepository.kt`: constructor gains `dbMaintenance: DbMaintenance`;
  `restoreFromJson` (115-135) wraps decode → validate → `replaceWith` →
  `dbMaintenance.reconcileCatalogLocked()` inside `dbMaintenance.withMaintenanceLock { }`
  (the in-progress guard at 120-125 stays first, outside the lock). Every restore — Drive
  or SAF file, v1 or v2 document — ends with reconciliation. `AppContainer.kt` wires the
  new parameter.

**Tests:**
- `BackupJsonTest` (pure JVM): `encodeDecodeV2RoundTripsAllNewFields`,
  `decodesV1DocumentUpgradingToV2` (committed v1 fixture string: exercise with
  muscleGroup "Posterior chain" → credits back 1.0 / hamstrings 0.4 / glutes 0.4 per
  current normalizer, equipment OTHER, loadType EXTERNAL), `missingNewArraysDecodeAsEmpty`,
  `nullEquipmentDefaultsToOther`.
- `BackupValidatorTest` (pure JVM): `rejectsUnknownEquipment`, `rejectsUnknownLoadType`,
  `rejectsCreditWeightOutOfBounds` (0.0, 1.01, NaN), `rejectsDanglingCreditExerciseId`,
  `rejectsDuplicateCreditPair`, `rejectsAnchorDayOutOfRange`,
  `rejectsDanglingScheduleRoutineId`, `rejectsSlotWithNeitherRoutineNorFocus`,
  `acceptsValidV2DocumentWithSlotsAndCredits`.
- Robolectric round-trips: `BackupV2RoundTripTest.v2ExportRestoreV2IsLossless` (seed +
  custom + history + credits + slots → exportJson → restoreFromJson → table-by-table
  equality, then reconciliation changed nothing: run it again and diff),
  `BackupV2RoundTripTest.v1FileRestoreOntoV2RebuildsCatalog` (restore a v1-shaped JSON:
  built-ins re-seeded to 37 with v2 fields, custom exercises keep ids and gain derived
  credits, seed_meta.catalogVersion == 2 after, collision detection re-ran),
  `BackupV2RoundTripTest.hasLocalDataCountsScheduleSlots`.

### WI-5 — Heat switch to junction credits

**Build:**
- `domain/Models.kt`: `Exercise` gains `equipment: EquipmentType = EquipmentType.OTHER`,
  `loadType: LoadType = LoadType.EXTERNAL`, `movementKey: String? = null`,
  `imageKey: String? = null`, `muscles: List<MuscleCredit> = emptyList()` (defaults keep
  every existing constructor call compiling).
- `data/mapper/Mappers.kt`: entity↔domain maps the new columns via
  `EquipmentType.fromStorage`/`LoadType.fromStorage`; `toEntity` writes `.name` strings
  and computes `nameKey` from the name.
- `data/repository/ExerciseRepository.kt`: `observeAll()` becomes
  `combine(exerciseDao.observeAll(), catalogDao.observeAllCredits())`, grouping credits by
  exerciseId into `Exercise.muscles`; `getById`/`observeById` join the same way. The
  catalog map already flows to the calculator through `TrainingInsightsSource`'s five-flow
  combine (TrainingInsightsSource.kt:59-68) and `TrainingInsightsInput.exerciseCatalog`
  (TrainingInsights.kt:91-98) — **no flow-shape change, no sixth source; that is Phase 4**.
- `domain/MuscleLoadCalculator.kt`: replace `mappingFor` (99-108) with
  `creditsFor(set, session, catalog): List<Pair<CanonicalMuscle, Double>>` implementing S13
  order (catalog junction → embedded junction → embedded muscleGroup derivation → catalog
  muscleGroup derivation → OTHER 1.0). Key resolution per S5: replace `_` with space,
  aliasIndex + fuzzy; unresolvable key → fall back to the whole-exercise muscleGroup
  derivation for that exercise. `snapshot` records lifetime per credited muscle and window
  volume as `setVolume × weight` (primary weight 1.0 preserves today's primary numbers;
  the flat `SECONDARY_VOLUME_WEIGHT` multiplication at 50-56 is subsumed — the constant
  now lives only in `deriveCredits`). `normalizeHeat`, windows, `trainedAtMs`, BodyMap,
  and all UI are untouched.

**Tests** (extend `MuscleLoadCalculatorTest.kt`):
- `junctionCreditsDriveHeatWhenPresent`
- `catalogJunctionWinsOverEmbeddedMuscleGroup` (embedded says "Chest", catalog junction
  says back 1.0 → back gets the volume)
- `fallsBackToMuscleGroupDerivationWithoutJunction` (existing behavior pinned: primary
  full, secondaries × 0.4 for "Posterior chain")
- `unknownJunctionKeyResolvesToParentGroupNeverOther` (`front_delts` → SHOULDERS via
  aliases; a garbage key falls back to the exercise's muscleGroup primary)
- `knownHistoryHeatBeforeAfterJunctionSwitch` — a fixed two-session history containing
  Conventional Deadlift and Barbell Bench Press; asserts the exact per-muscle window
  volumes under v2 credits (deadlift: glutes 1.0×, hamstrings 0.5×, back 0.5× — where v1
  gave back 1.0×, hams/glutes 0.4×) and states the diff in the test's comment. This test
  is the executable half of the owner's heat-diff review.

### WI-6 — Rehearsal runbook + real-phone upgrade (owner-executed; commit as `docs/gameplan/artifacts/phase-3-rehearsal.md`)

Commit the runbook below verbatim as part of the PR, then the owner executes it. The
sideloaded phone build is release-signed and non-debuggable — the phone's DB file can never
be pulled; the owner's SAF JSON export restored into a v1 emulator build is the only
faithful real-data rehearsal (DEVELOPMENT.md:121-123).

**Preparation (executor):** before the phase branch diverges, tag the merge-base:
`git tag schema-v1-rehearsal <pre-phase-3 main HEAD>` and push the tag. This is the "v1
APK" build point.

**Runbook (owner, on the development machine, ~half a day):**
1. Phone: Settings → Backup → *Export to file* → save the JSON (SAF; SettingsScreen.kt:101)
   → transfer to the computer. Note the restore-summary style counts the app shows.
2. `git checkout schema-v1-rehearsal && ./gradlew assembleDebug`
3. Start an API-34 emulator. `adb install app/build/outputs/apk/debug/PersonalTrainer-1.0.0-debug.apk`
4. `adb push <backup>.json /sdcard/Download/`
5. Emulator app: Settings → *Restore from file* → pick the JSON → confirm. **Record**: the
   summary line ("N workouts, M sets, X exercises, Y routines"), the most recent session's
   name/date in History, one known lift's last-session sets/weights, one PR value on
   Progress, and the Body-tab heat picture (screenshot).
6. `git checkout claude/phase-3-schema-v2 && ./gradlew assembleDebug`
7. `adb install -r app/build/outputs/apk/debug/PersonalTrainer-1.0.0-debug.apk` (upgrade in
   place — do NOT uninstall).
8. Launch. Verify, in order: app opens with no crash; History shows the same N workouts and
   the same most-recent session; the known lift's sets/weights unchanged; the PR value
   unchanged; Library lists 37 built-ins plus your customs, zero duplicates of your customs
   unless you genuinely named one after a built-in (then it appears twice — expected,
   flagged, Phase 7 surfaces it); Body tab renders — heat DISTRIBUTION may shift (that is
   the junction switch; compare against step 5's screenshot and judge it against how you
   actually train — this is the heat-diff sign-off).
9. Settings → *Export to file* on the emulator; open the JSON in a text editor: `"version": 2`,
   `exerciseMuscles` present.
10. Settings → *Restore from file* with the ORIGINAL step-1 (v1) JSON onto this v2 install.
    Verify step 8's checks again (v1-document upgrade path on a live DB).
11. Report all observations in the PR thread.

**Real-phone upgrade (owner, after PR review + rehearsal green, ~1 hour):**
1. Phone: take a FRESH Settings → Export to file; copy it off the phone. (The pre-v2 raw
   copy lives in app-private storage and dies with an uninstall — this JSON is your
   independent copy.)
2. Build the release APK per SETUP.md (`./gradlew assembleRelease` with keystore.properties)
   from the approved phase branch/merge commit; sideload over the existing install (do not
   uninstall).
3. Launch; run runbook step 8's verification list on the phone.
4. Confirm nothing prompts, nothing is missing, and the app has been used for one full
   workout before the PR is merged-and-closed (protocol: phase closes on this sign-off).

## 6. Out of scope — do not touch

- **ALL UI** beyond routing the `DuplicateName` result into the existing error channels and
  the generated review artifact: no Library/picker changes, no collision "needs attention"
  row (Phase 7), no equipment chips, no schedule UI, no Plan tab, no imagery, no thumbnails
  (`imageKey` stays null everywhere).
- Catalog growth past the 37 (tail batches are Phase 7 seed bumps).
- `ScheduleRepository`, week derivation, planner changes, `insights.weekPlan`, any writes
  to `schedule_slots` (Phase 4 owns all of it — this phase ships the empty table, its DAO,
  and its backup/validator/hasLocalData coverage only).
- Heat bands, window changes, RPE, imbalance, coach copy (Phase 5); the per-loadType
  increment table and `ProgressionCalculator.INCREMENT_KG` (Phase 7).
- The Library muscle-filter route contract (still matches `muscleGroup`; flips in Phase 7).
- The A1 DI refactor. `RoutineExercise` equipment override (cut). `machineKind`,
  `defaultRestSeconds` (cut from v2). Deleting or migrating `muscleGroup` (it survives as
  display text).
- Any schema object not listed in S2/S4. Any data transform inside the migration beyond the
  nameKey backfill.

## 7. Acceptance gate

All of the following, in order:

```
tools/preflight.sh                      # all eight static checks + domain tests — green
./gradlew testDebugUnitTest             # JVM + Robolectric lanes — BUILD SUCCESSFUL, 0 failures
```

**No gate line here depends on a CI run.** CI on this repo has never executed (account billing
block, docs/DEVELOPMENT.md:95-105) and the owner's billing errand is non-gating, so the primary
gate is owner-machine output pasted into the PR:

```
./gradlew testDebugUnitTest assembleDebug   # owner's machine — BUILD SUCCESSFUL, 0 failures
```

and `app/schemas/.../2.json` committed, with its provenance stated in the PR: the owner-machine
build that generated it (the `room-schemas` CI artifact counts only if CI is alive and actually
ran). If CI IS alive, a green `verify` run on `claude/phase-3-schema-v2` is an ADDITIONAL check —
welcome, never required.

Named tests that must exist and pass (grep-audit the PR against this list):

- Migration: `migratesEmptyV1Database`, `migratesPopulatedV1DatabaseWithHistory`,
  `foreignKeyIntegrityAfterMigration`, `indexesMatchDeclaredSchema`
- Safety net: `copiesDbAndSidecarFilesOnce`, `secondRunDoesNotOverwriteExistingCopy`,
  `freshInstallWritesMarkerWithoutCopy`, `copyFailureLeavesMarkerUnset`
- Seed/catalog: `catalogHasExactly37EntriesAtVersion2`,
  `everyEntryHasExactlyOnePrimaryWithWeightOne`,
  `secondaryWeightsAreInHalfOpenRangeAndSumAtMostOne`, `nameKeysAreUniqueAmongBuiltIns`,
  `idsMatchFrozenV1Slugs`, `allMuscleKeysNormalizeToCanonicalNonOther`,
  `movementKeysComeFromTheClosedVocabulary`, `artifactMatchesCatalog`, `seedIsIdempotent`,
  `upsertUpdatesBuiltInInPlacePreservingNotes`, `customCollisionIsInsertedAndFlagged`,
  `seederNeverDeletesRows`, `junctionDerivedForJunctionlessCustoms`,
  `seedSkipsWhenCatalogVersionCurrent`, `concurrentSeedAndRestoreSerialize`
- Backup: `encodeDecodeV2RoundTripsAllNewFields`, `decodesV1DocumentUpgradingToV2`,
  `missingNewArraysDecodeAsEmpty`, `nullEquipmentDefaultsToOther`,
  `rejectsUnknownEquipment`, `rejectsUnknownLoadType`, `rejectsCreditWeightOutOfBounds`,
  `rejectsDanglingCreditExerciseId`, `rejectsDuplicateCreditPair`,
  `rejectsAnchorDayOutOfRange`, `rejectsDanglingScheduleRoutineId`,
  `rejectsSlotWithNeitherRoutineNorFocus`, `acceptsValidV2DocumentWithSlotsAndCredits`,
  `v2ExportRestoreV2IsLossless`, `v1FileRestoreOntoV2RebuildsCatalog`,
  `hasLocalDataCountsScheduleSlots`
- Heat: `junctionCreditsDriveHeatWhenPresent`, `catalogJunctionWinsOverEmbeddedMuscleGroup`,
  `fallsBackToMuscleGroupDerivationWithoutJunction`,
  `unknownJunctionKeyResolvesToParentGroupNeverOther`,
  `knownHistoryHeatBeforeAfterJunctionSwitch`

Owner-side gate (phase does not close without ALL of): the rehearsal runbook executed with
every step-8/step-10 check passing; the catalog review artifact signed off (corrections, if
any, applied to the WI-3 table and re-rendered); the heat-diff judged acceptable on the
rehearsal emulator; the real-phone upgrade checklist completed; and — **required, not
optional, because the owner's host is Windows** — `.\gradlew.bat connectedDebugAndroidTest`
green per the Phase-2 runbook.

**Lane precedence on this project (settled by the host, 21 Aug 2026).** The owner's machine
is Windows, where Robolectric falls back to legacy SQLite and cannot validate a schema with
a composite primary key — which `exercise_muscles` has. The JVM lane therefore **cannot
gate this phase**: a red `Migration1To2Test` there proves nothing, and a green one proves
little. The **emulator lane is the migration lane**, and the migration suite must be green
there before this phase closes. Write the migration tests so they run in both lanes (same
source, `androidTest` twin where the packet names one), and report JVM-lane results as
informational. If the owner ever adds WSL2 or unblocks CI, the JVM lane returns as the fast
pre-check — it never becomes the gate on a Windows host.

## 8. Owner device checklist

(These are the same checks embedded in WI-6; numbered standalone for the phone.)

1. Before installing anything new: in the app, Settings → Backup → **Export to file**, save
   the JSON somewhere off the phone. Confirm the file is non-trivial in size.
2. Install the Phase-3 release APK **over** the existing app (sideload; do not uninstall).
3. Open the app. It must open normally with no crash and no unusual delay beyond ~a second.
4. History tab: your workout count and most recent session (name + date) are exactly as
   before the update.
5. Open a session you remember; a known lift shows the same sets, weights, reps.
6. Progress: a PR you know (e.g. your best bench) shows the same number.
7. Library: 37 built-in exercises plus every custom you created; your customs' names and
   notes intact. If you once named a custom the same as a built-in lift, you will now see
   both — expected; a later update lets you tidy it.
8. Body tab: the silhouette renders. The heat pattern may look different from last week —
   it now weights secondary muscles per lift. Judge: does it better match how you actually
   train? Note anything that looks wrong (that feedback tunes the catalog).
9. Settings → Export to file again; the export completes without error.
10. Do one full real workout (start, log, rest timer, finish). Everything behaves as before.
11. Report steps 3-10 pass/fail in the PR. The phase closes on your sign-off.

## 9. Estimates

- **Executor:** 3–5 days. WI-1 ~1d; WI-2 ~0.5d; WI-3 ~1–1.5d (the data table is
  transcription, the seeder + tests are the work); WI-4 ~1d; WI-5 ~0.5–1d; runbook/doc
  ~0.25d. The long pole is making `2.json`, the entity annotations, and the migration SQL
  agree byte-for-byte.
- **Mid-phase checkpoint — the 2.json owner round-trip (blocking, plan for it).** The executor
  environment has no Android SDK and cannot build, and CI cannot be relied on (never executed,
  billing). So after WI-1's entities compile-by-inspection and BEFORE the migration SQL can be
  finished, the executor stops and hands the owner a round-trip request: pull the branch, run
  `./gradlew :app:assembleDebug`, and ship back (or commit directly to the branch)
  `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/2.json` plus the generated
  CREATE/INDEX statements it contains. The executor then diffs those statements against the
  hand-written `MIGRATION_1_2` SQL (S1: byte-for-byte equivalent) and finishes the phase. Budget
  the owner's turnaround (~15 minutes of their time, but a calendar gap) inside the 3–5 day
  estimate, and raise the request early rather than at the end.
- **Owner:** 1–2 days total: catalog review artifact ~2–3 h; emulator rehearsal ~half a
  day; heat-diff judgment ~1 h; real-phone upgrade + one live workout ~1 h + a gym session;
  PR review of the migration/backup diff ~2 h (review the migration and `replaceWith`
  ordering line-by-line — this is the one PR where rubber-stamping can cost history).

## 10. Hand-back

The completion report (PR description + closing comment) must contain:

1. Links: the PR and the committed `app/schemas/.../2.json` (with its identityHash quoted),
   plus the pasted owner-machine `./gradlew testDebugUnitTest assembleDebug` output that is the
   phase's primary gate. A CI run link only if CI is alive — it is an additional check, never
   the gate (§7).
2. The full named-test list from §7 with the actual run output (count passed) from
   `./gradlew testDebugUnitTest` and `tools/preflight.sh`.
3. The committed review artifact `docs/gameplan/artifacts/catalog-v2-review.md` and a note
   of every correction the owner made during review.
4. The heat-diff summary: the before/after per-muscle table from
   `knownHistoryHeatBeforeAfterJunctionSwitch` plus the owner's emulator observation
   (step 8 of the runbook) in one paragraph.
5. The rehearsal record: date, emulator API level, the step-5 baseline counts, and
   pass/fail per runbook step 8-10.
6. The real-phone record: the owner's checklist results (steps 3-11), the APK
   versionCode/versionName installed, and confirmation the fresh pre-upgrade JSON export
   is stored off-phone.
7. Rollback statement, verbatim: "The pre-v2 raw copy of personal_trainer.db (+-wal/-shm)
   at files/pre-migration/v1/ on the phone, plus the JSON export taken before upgrade, are
   the only rollback artifacts. v1 builds refuse v2 JSON; Room refuses downgrades. The raw
   copy dies with an uninstall."
8. The `2.json` round-trip record: who ran the build, on what machine, on which commit, and
   confirmation that every CREATE/INDEX statement in `2.json` was diffed against `MIGRATION_1_2`
   (S1) — not assumed equivalent.
9. Anything deferred with its landing phase (collision UI → 7; schedule writes → 4; bands
   → 5; filter contract flip → 7), so the next packet author inherits no silent gaps.
10. The next phase, named: **Phase 4 — Plan tab** (`docs/gameplan/PHASE_4_PLAN_TAB.md`), which is
    the first writer of `schedule_slots` — hand it the empty table, its DAO, and the signed slot
    semantics from S4.
