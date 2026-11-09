package com.sinura.personaltrainer.data.local

import androidx.sqlite.db.SupportSQLiteDatabase

/** The five owner gyms; fixed ids for migration, backup, and tests. */
object GymSeed {
    data class SeedRow(
        val id: String,
        val name: String,
        val shortName: String,
        val badge: String,
        val sortOrder: Int,
    )

    val ROWS: List<SeedRow> = listOf(
        SeedRow("gym-home", "Home workout", "Home", "HM", 0),
        SeedRow("gym-af-contrelle", "Anytime Fitness Cotrelle", "Cotrelle", "CT", 1),
        SeedRow("gym-af-major-mackenzie", "Anytime Fitness Major Mackenzie", "Major Mac", "MM", 2),
        SeedRow("gym-af-weston", "Anytime Fitness Weston", "Weston", "WS", 3),
        SeedRow("gym-work", "Work gym", "Work", "WK", 4),
    )

    fun insertSql(nowMs: Long): List<String> = ROWS.map { row ->
        """
        INSERT OR IGNORE INTO `gyms`
            (`id`, `name`, `shortName`, `badge`, `sortOrder`, `archivedAtMs`, `createdAtMs`, `updatedAtMs`)
        VALUES
            ('${row.id}', '${row.name}', '${row.shortName}', '${row.badge}', ${row.sortOrder}, NULL, $nowMs, $nowMs)
        """.trimIndent()
    }

    fun insertAll(db: SupportSQLiteDatabase, nowMs: Long) {
        insertSql(nowMs).forEach { db.execSQL(it) }
    }
}
