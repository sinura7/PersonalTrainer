package com.sinura.personaltrainer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row, holding what the catalog seeder needs to know across launches.
 *
 * Seeding used to fire only when the exercise table was empty, which is a rule that works
 * exactly once: every catalog improvement after the first launch was unreachable on any phone
 * that had already opened the app. A stored version turns seeding into an upsert that can run
 * on every start and still be a no-op when there is nothing new to say.
 *
 * [pendingCollisions] is a JSON array of `{"builtInId","customId","nameKey"}`. The seeder only
 * ever records collisions here — it never renames, merges or deletes anything, because the
 * exercise a user named is theirs and history points at its id.
 */
@Entity(tableName = "seed_meta")
data class SeedMetaEntity(
    @PrimaryKey val id: Int = 1,
    val catalogVersion: Int,
    @ColumnInfo(defaultValue = "'[]'") val pendingCollisions: String = "[]",
)
