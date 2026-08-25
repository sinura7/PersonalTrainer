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
 * WHAT VERIFIES THAT: the committed `2.json` under
 * `app/schemas/com.sinura.personaltrainer.data.local.TrainerDatabase/`, which Room's KSP
 * processor emitted from the entities. Both `1.json` and `2.json` are checked in, so this is
 * not reasoned about in the abstract — its `exercises` createSql spells out exactly the DEFAULT
 * clauses added below (`equipment ... DEFAULT 'OTHER'`, `loadType ... DEFAULT 'EXTERNAL'`,
 * `nameKey ... DEFAULT ''`), and `Migration1To2Test` replays this migration and validates the
 * result against that schema. The default-value question is thereby settled from the artifact a
 * compiler emitted, not from memory: the quoting of 'OTHER' and 'EXTERNAL' matches `2.json`
 * byte-for-byte, which is what keeps Room's `validateMigrations` from rejecting them at open.
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
