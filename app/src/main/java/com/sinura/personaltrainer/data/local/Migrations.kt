package com.sinura.personaltrainer.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: the one reviewed migration this app gets for the v2 feature wave.
 *
 * Everything here is additive, and deliberately so. It runs exactly once on a phone holding
 * training history that cannot be re-created, and there is no destructive fallback to catch a
 * mistake — `TrainerDatabase.create` has never called `fallbackToDestructiveMigration` and
 * never will. So: five new columns, three new tables, three new indexes, one seed_meta row.
 *
 * The ONLY data it touches is the `nameKey` backfill, and even that is provisional. SQL's
 * `LOWER(TRIM(name))` is ASCII-safe for this catalog but is not the Kotlin normalizer, so the
 * first reconciliation pass after the migration rewrites every `nameKey` through the real
 * function. Junction credits, catalog upgrades and collision detection all happen in Kotlin
 * behind the maintenance mutex, where `MuscleNormalizer` lives and where a failure is a logged
 * no-op instead of a database that will not open.
 *
 * `catalogVersion = 0` in the inserted row is load-bearing: it tells the first v2 startup that
 * this database has never been through a catalog pass, so the seeder upgrades the 37 built-ins
 * and builds their junction rows.
 *
 * The DEFAULT clauses below must match the `@ColumnInfo(defaultValue = ...)` annotations on the
 * entities byte-for-byte, or Room's `validateMigrations` fails on the default-value diff.
 *
 * A NOTE ON WHAT VERIFIES THAT, because the sentence here used to overstate it: it said the
 * committed `2.json` was the arbiter and that its createSql had been copied here rather than
 * reasoned about. There is no committed `2.json`. `app/schemas/` holds `1.json` only, so every
 * clause below — the quoting of 'OTHER' and 'EXTERNAL' especially — was in fact reasoned about,
 * and has never been checked against anything a compiler emitted.
 *
 * Generating it is the first thing to do on a machine with the SDK: `./gradlew
 * :app:kspDebugKotlin` writes `2.json`, and diffing its `exercises` createSql against these
 * ALTER TABLE statements settles the default-value question from the artifact instead of from
 * memory. Until then this file is an argument, not a verified migration — and a fresh install
 * will not tell you either way, because it builds the table from Room's own createSql and never
 * runs a line of this.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `equipment` TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `loadType` TEXT NOT NULL DEFAULT 'EXTERNAL'")
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `movementKey` TEXT")
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `imageKey` TEXT")
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `nameKey` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `exercises` SET `nameKey` = LOWER(TRIM(`name`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_nameKey` ON `exercises` (`nameKey`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_muscles` (" +
                "`exerciseId` TEXT NOT NULL, " +
                "`muscleKey` TEXT NOT NULL, " +
                "`weight` REAL NOT NULL, " +
                "PRIMARY KEY(`exerciseId`, `muscleKey`), " +
                "FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercise_muscles_muscleKey` " +
                "ON `exercise_muscles` (`muscleKey`)",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `seed_meta` (" +
                "`id` INTEGER NOT NULL, " +
                "`catalogVersion` INTEGER NOT NULL, " +
                "`pendingCollisions` TEXT NOT NULL DEFAULT '[]', " +
                "PRIMARY KEY(`id`))",
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_slots` (" +
                "`id` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "`routineId` TEXT, " +
                "`focusKind` TEXT, " +
                "`anchorDay` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`routineId`) REFERENCES `routines`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_slots_routineId` " +
                "ON `schedule_slots` (`routineId`)",
        )

        db.execSQL(
            "INSERT OR REPLACE INTO `seed_meta` (`id`, `catalogVersion`, `pendingCollisions`) " +
                "VALUES (1, 0, '[]')",
        )
    }
}
