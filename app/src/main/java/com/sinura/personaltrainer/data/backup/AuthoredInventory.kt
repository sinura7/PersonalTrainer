package com.sinura.personaltrainer.data.backup

import com.sinura.personaltrainer.domain.BlockArchive
import com.sinura.personaltrainer.domain.BodyweightLog

/**
 * Counts that belong to the owner, not the seeded catalog.
 *
 * Built-in exercises travel in every normal export. They are not "has data"
 * and they must not let a catalog-only file wipe history.
 */
data class AuthoredInventory(
    val sessions: Int,
    val setLogs: Int,
    val routines: Int,
    val customExercises: Int,
    val scheduleSlots: Int,
    val bodyweightEntries: Int,
    val blocks: Int,
) {
    val isEmpty: Boolean
        get() = sessions == 0 &&
            setLogs == 0 &&
            routines == 0 &&
            customExercises == 0 &&
            scheduleSlots == 0 &&
            bodyweightEntries == 0 &&
            blocks == 0

    fun describe(): String =
        "$sessions session${plural(sessions)}, $setLogs set${plural(setLogs)}, " +
            "$routines routine${plural(routines)}, $customExercises custom exercise${plural(customExercises)}, " +
            "$scheduleSlots scheduled day${plural(scheduleSlots)}, " +
            "$bodyweightEntries weigh-in${plural(bodyweightEntries)}, " +
            "$blocks block${plural(blocks)}"

    companion object {
        val EMPTY = AuthoredInventory(0, 0, 0, 0, 0, 0, 0)

        /** Structural-test stand-in: the phone has authored data; counts are unused. */
        val PRESENT = AuthoredInventory(
            sessions = 1,
            setLogs = 0,
            routines = 0,
            customExercises = 0,
            scheduleSlots = 0,
            bodyweightEntries = 0,
            blocks = 0,
        )

        fun fromDocument(document: BackupDocument): AuthoredInventory {
            val log = BodyweightLog.decode(document.preferences.bodyweightLog)
            val bodyweight = when {
                log.isNotEmpty() -> log.size
                document.preferences.bodyweightKg != null -> 1
                else -> 0
            }
            val past = BlockArchive.decode(document.preferences.pastBlocks)
            val current = if (document.preferences.blockStartEpochDay != null) 1 else 0
            return AuthoredInventory(
                sessions = document.sessions.size,
                setLogs = document.setLogs.size,
                routines = document.routines.size,
                customExercises = document.exercises.count { it.isCustom },
                scheduleSlots = document.scheduleSlots.size,
                bodyweightEntries = bodyweight,
                blocks = current + past.size,
            )
        }

        fun confirmBody(
            sourceName: String,
            incoming: AuthoredInventory,
            local: AuthoredInventory,
        ): String =
            "Restoring $sourceName replaces everything on this phone.\n\n" +
                "This file: ${incoming.describe()}.\n" +
                "This phone: ${local.describe()}.\n\n" +
                "This cannot be undone from Settings."

        const val EMPTY_INCOMING_REFUSED =
            "This file has no sessions, sets, routines, custom exercises, " +
                "schedule, weigh-ins, or blocks. Restoring it would erase the " +
                "training data on this phone, so it was refused."
    }
}

private fun plural(n: Int): String = if (n == 1) "" else "s"
