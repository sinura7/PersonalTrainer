package com.sinura.personaltrainer.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Temper v1 → v2: bodyweight entries and training blocks leave encoded
 * DataStore strings and become Room tables (P6.1 / FND-019).
 *
 * Additive only. Existing v1 history is untouched. The migration creates
 * empty tables; DataStore → Room import is an app-layer one-shot after
 * open, because a Room [Migration] cannot read DataStore.
 *
 * `fallbackToDestructiveMigration` remains banned. After foundation
 * freeze, this is the only legal way the schema may change.
 */
val MIGRATION_TEMPER_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `bodyweight_entries` (" +
                "`epochDay` INTEGER NOT NULL, " +
                "`kg` REAL NOT NULL, " +
                "`recordedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `training_blocks` (" +
                "`id` TEXT NOT NULL, " +
                "`startEpochDay` INTEGER NOT NULL, " +
                "`weeks` INTEGER NOT NULL, " +
                "`isCurrent` INTEGER NOT NULL, " +
                "`archivedAtMs` INTEGER, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_training_blocks_startEpochDay` " +
                "ON `training_blocks` (`startEpochDay`)",
        )
    }
}
