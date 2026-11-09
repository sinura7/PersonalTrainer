package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.GymSeed
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.GymDao
import com.sinura.personaltrainer.data.local.dao.GymVisitRow
import com.sinura.personaltrainer.data.local.entity.GymEntity
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymCopy
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface GymSaveOutcome {
    data class Saved(val gym: Gym) : GymSaveOutcome
    data object NameTaken : GymSaveOutcome
    data object BadgeTaken : GymSaveOutcome
    data object Invalid : GymSaveOutcome
}

sealed interface GymArchiveOutcome {
    data object Archived : GymArchiveOutcome
    data object LastActiveGym : GymArchiveOutcome
    data object InUseByLiveWorkout : GymArchiveOutcome
}

sealed interface GymRestoreOutcome {
    data object Restored : GymRestoreOutcome
    data class NameTaken(val gym: Gym) : GymRestoreOutcome
    data class BadgeTaken(val gym: Gym) : GymRestoreOutcome
}

class GymRepository(
    private val database: TemperDatabase,
    private val gymDao: GymDao,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun observeGyms(): Flow<List<Gym>> =
        gymDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveGyms(): Flow<List<Gym>> =
        observeGyms().map { gyms -> gyms.filterNot { it.archived } }

    fun observeVisits(): Flow<Map<String?, GymVisit>> =
        gymDao.observeVisits().map { rows -> rows.associate { it.gymId to it.toVisit() } }

    suspend fun repairDangling() {
        val ids = gymDao.danglingGymIds()
        if (ids.isEmpty()) return
        val now = nowMs()
        ids.forEach { id ->
            AppLog.w(TAG, "Recreating archived gym for dangling id $id")
            gymDao.upsert(
                GymEntity(
                    id = id,
                    name = GymCopy.RESTORED_GYM_NAME,
                    shortName = "Restored",
                    badge = "RX",
                    sortOrder = Int.MAX_VALUE,
                    archivedAtMs = now,
                    createdAtMs = now,
                    updatedAtMs = now,
                ),
            )
        }
    }

    suspend fun reseedAfterErase() {
        database.withTransaction {
            gymDao.deleteAll()
            GymSeed.insertAll(database.openHelper.writableDatabase, nowMs())
        }
    }

    private fun GymEntity.toDomain(): Gym = Gym(
        id = id,
        name = name,
        shortName = shortName,
        badge = badge,
        sortOrder = sortOrder,
        archived = archivedAtMs != null,
    )

    private fun GymVisitRow.toVisit(): GymVisit = GymVisit(sessions = sessions, lastAtMs = lastAt)

    private companion object {
        const val TAG = "PT/GymRepository"
    }
}
