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

/**
 * Temper v2 → v3: schedule rules, dated occurrences, one missed-work
 * decision, and reminder delivery records (P7.1). Additive empty tables.
 * Slot → rule import is app-layer after open. Recurrence rules are not
 * rewritten here.
 */
val MIGRATION_TEMPER_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_rules` (" +
                "`id` TEXT NOT NULL, " +
                "`weekday` INTEGER NOT NULL, " +
                "`hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, " +
                "`modality` TEXT NOT NULL, " +
                "`zonePolicy` TEXT NOT NULL, " +
                "`fixedZoneId` TEXT, " +
                "`routineId` TEXT, " +
                "`templateId` TEXT, " +
                "`focusKind` TEXT, " +
                "`reminderOffsetMinutes` INTEGER NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL, " +
                "`updatedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_rules_weekday` " +
                "ON `schedule_rules` (`weekday`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_rules_enabled` " +
                "ON `schedule_rules` (`enabled`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_occurrences` (" +
                "`id` TEXT NOT NULL, " +
                "`ruleId` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`instantMs` INTEGER NOT NULL, " +
                "`zoneId` TEXT NOT NULL, " +
                "`offsetSeconds` INTEGER NOT NULL, " +
                "`localEpochDay` INTEGER NOT NULL, " +
                "`hour` INTEGER NOT NULL, " +
                "`minute` INTEGER NOT NULL, " +
                "`completedActivityId` TEXT, " +
                "`createdAtMs` INTEGER NOT NULL, " +
                "`updatedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`ruleId`) REFERENCES `schedule_rules`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_occurrences_ruleId` " +
                "ON `schedule_occurrences` (`ruleId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_occurrences_localEpochDay` " +
                "ON `schedule_occurrences` (`localEpochDay`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_schedule_occurrences_ruleId_localEpochDay` " +
                "ON `schedule_occurrences` (`ruleId`, `localEpochDay`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `missed_work_decisions` (" +
                "`weekStartEpochDay` INTEGER NOT NULL, " +
                "`choice` TEXT NOT NULL, " +
                "`decidedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`weekStartEpochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminder_deliveries` (" +
                "`id` TEXT NOT NULL, " +
                "`occurrenceId` TEXT NOT NULL, " +
                "`scheduledAtMs` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL, " +
                "`updatedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`occurrenceId`) REFERENCES `schedule_occurrences`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reminder_deliveries_occurrenceId` " +
                "ON `reminder_deliveries` (`occurrenceId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reminder_deliveries_status` " +
                "ON `reminder_deliveries` (`status`)",
        )
    }
}
