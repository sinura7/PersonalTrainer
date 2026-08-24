package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.ScheduleDao
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.domain.ScheduleSlot
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import java.time.DayOfWeek
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns the week the user decided on.
 *
 * Every write here is explicit and user-initiated: pin, unpin, swap, accept. Nothing in this
 * class runs on a timer, on a recompute, or as a side effect of reading — which is the whole
 * point. The old week was regenerated on every emission, so there was no state to own and
 * nothing to write; this class exists so that "my week" is a thing that persists.
 *
 * Slots live in Room rather than in preferences because they have a foreign key: deleting a
 * routine has to take its pins with it, and DataStore cannot cascade.
 */
class ScheduleRepository(private val scheduleDao: ScheduleDao) {

    fun observeSlots(): Flow<List<ScheduleSlot>> = scheduleDao.observeAll()
        .map { rows -> rows.mapNotNull { it.toDomain() } }
        .observeHealth("schedule slots").presentValues()

    suspend fun slots(): List<ScheduleSlot> = scheduleDao.getAll().mapNotNull { it.toDomain() }

    /**
     * Appends a slot to the end of the cycle.
     *
     * Exactly one of [routineId]/[focusKind] must be set. A slot that is neither is a state the
     * model has no meaning for — rest is the absence of a slot, not an empty one — so it is
     * refused here rather than written and puzzled over later.
     */
    suspend fun pin(
        routineId: String?,
        focusKind: SessionFocusKind?,
        anchorDay: DayOfWeek?,
    ): ScheduleSlot {
        require((routineId == null) != (focusKind == null)) {
            "A slot is a routine or a focus, never both and never neither."
        }
        val now = System.currentTimeMillis()
        val slot = ScheduleSlot(
            id = UUID.randomUUID().toString(),
            position = (scheduleDao.maxPosition() ?: -1) + 1,
            routineId = routineId,
            focusKind = focusKind,
            anchorDay = anchorDay,
            createdAt = now,
            updatedAt = now,
        )
        scheduleDao.upsert(slot.toEntity())
        return slot
    }

    /** Immediate and undoable by re-pinning; a confirm on this would cost more than it saves. */
    suspend fun unpin(slotId: String) {
        scheduleDao.deleteById(slotId)
    }

    suspend fun swapRoutine(slotId: String, routineId: String) {
        val current = scheduleDao.getById(slotId) ?: return
        scheduleDao.update(
            current.copy(
                routineId = routineId,
                // A swapped slot is a routine slot now; leaving the old focus behind would make
                // the row ambiguous the next time anything read it.
                focusKind = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Turns a previewed week into pins, in one transaction.
     *
     * Each accepted fill is anchored to the day it was proposed for — that is what the user
     * looked at and agreed to — and appended in day order so the cycle reads left to right.
     * Once written they are ordinary slots: nothing distinguishes an accepted suggestion from
     * something typed by hand, and the only way any slot leaves is an explicit unpin.
     */
    suspend fun acceptFills(fills: List<SuggestedTrainingDay>) {
        val usable = fills.filterNot { it.isRest }.sortedBy { it.epochDay }
        if (usable.isEmpty()) return
        val now = System.currentTimeMillis()
        var position = (scheduleDao.maxPosition() ?: -1) + 1
        val rows = usable.map { fill ->
            ScheduleSlotEntity(
                id = UUID.randomUUID().toString(),
                position = position++,
                routineId = fill.routineId,
                focusKind = if (fill.routineId == null) fill.focusKind.name else null,
                anchorDay = fill.dayOfWeek.ordinal,
                createdAt = now,
                updatedAt = now,
            )
        }
        scheduleDao.insertAll(rows)
    }
}

private fun ScheduleSlot.toEntity(): ScheduleSlotEntity = ScheduleSlotEntity(
    id = id,
    position = position,
    routineId = routineId,
    focusKind = focusKind?.name,
    anchorDay = anchorDay?.ordinal,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
