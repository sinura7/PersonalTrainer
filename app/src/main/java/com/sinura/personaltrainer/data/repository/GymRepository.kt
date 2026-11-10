package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.GymSeed
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.GymDao
import com.sinura.personaltrainer.data.local.dao.GymVisitRow
import com.sinura.personaltrainer.data.local.entity.GymEntity
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymBadge
import com.sinura.personaltrainer.domain.GymCopy
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

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

    suspend fun add(name: String, shortName: String, badge: String?): GymSaveOutcome {
        val trimmedName = name.trim()
        val trimmedShort = shortName.trim()
        val badgeValue = (badge?.trim()?.uppercase().takeUnless { it.isNullOrEmpty() }
            ?: GymBadge.defaultForShortName(trimmedShort)).uppercase()
        if (!validFields(trimmedName, trimmedShort, badgeValue)) return GymSaveOutcome.Invalid
        if (gymDao.activeWithNameIgnoringCase(trimmedName) != null) return GymSaveOutcome.NameTaken
        if (gymDao.activeWithBadge(badgeValue) != null) return GymSaveOutcome.BadgeTaken
        val now = nowMs()
        val active = gymDao.getAll().filter { it.archivedAtMs == null }
        val nextOrder = (active.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val entity = GymEntity(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            shortName = trimmedShort,
            badge = badgeValue,
            sortOrder = nextOrder,
            archivedAtMs = null,
            createdAtMs = now,
            updatedAtMs = now,
        )
        gymDao.upsert(entity)
        return GymSaveOutcome.Saved(entity.toDomain())
    }

    suspend fun update(id: String, name: String, shortName: String, badge: String): GymSaveOutcome {
        val existing = gymDao.get(id) ?: return GymSaveOutcome.Invalid
        val trimmedName = name.trim()
        val trimmedShort = shortName.trim()
        val badgeValue = badge.trim().uppercase()
        if (!validFields(trimmedName, trimmedShort, badgeValue)) return GymSaveOutcome.Invalid
        if (gymDao.activeWithNameIgnoringCase(trimmedName, excludeId = id) != null) {
            return GymSaveOutcome.NameTaken
        }
        if (gymDao.activeWithBadge(badgeValue, excludeId = id) != null) {
            return GymSaveOutcome.BadgeTaken
        }
        val now = nowMs()
        gymDao.upsert(
            existing.copy(
                name = trimmedName,
                shortName = trimmedShort,
                badge = badgeValue,
                updatedAtMs = now,
            ),
        )
        return GymSaveOutcome.Saved(gymDao.get(id)!!.toDomain())
    }

    suspend fun reorder(idsInOrder: List<String>) {
        database.withTransaction {
            val now = nowMs()
            idsInOrder.forEachIndexed { index, gymId ->
                val row = gymDao.get(gymId) ?: return@forEachIndexed
                if (row.archivedAtMs != null) return@forEachIndexed
                gymDao.upsert(row.copy(sortOrder = index, updatedAtMs = now))
            }
        }
    }

    suspend fun archive(id: String): GymArchiveOutcome {
        val gym = gymDao.get(id) ?: return GymArchiveOutcome.LastActiveGym
        if (gym.archivedAtMs != null) return GymArchiveOutcome.Archived
        if (gymDao.countActive() <= 1) return GymArchiveOutcome.LastActiveGym
        if (gymDao.liveSessionUsesGym(id)) return GymArchiveOutcome.InUseByLiveWorkout
        val now = nowMs()
        gymDao.upsert(gym.copy(archivedAtMs = now, updatedAtMs = now))
        return GymArchiveOutcome.Archived
    }

    suspend fun restore(id: String): GymRestoreOutcome {
        val gym = gymDao.get(id) ?: return GymRestoreOutcome.Restored
        if (gym.archivedAtMs == null) return GymRestoreOutcome.Restored
        val nameClash = gymDao.activeWithNameIgnoringCase(gym.name.trim(), excludeId = id)
        if (nameClash != null) return GymRestoreOutcome.NameTaken(nameClash.toDomain())
        val badgeClash = gymDao.activeWithBadge(gym.badge.trim().uppercase(), excludeId = id)
        if (badgeClash != null) return GymRestoreOutcome.BadgeTaken(badgeClash.toDomain())
        val now = nowMs()
        gymDao.upsert(gym.copy(archivedAtMs = null, updatedAtMs = now))
        return GymRestoreOutcome.Restored
    }

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

    private fun validFields(name: String, shortName: String, badge: String): Boolean =
        name.length in 1..60 &&
            shortName.length in 1..12 &&
            GymBadge.isValid(badge)

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
