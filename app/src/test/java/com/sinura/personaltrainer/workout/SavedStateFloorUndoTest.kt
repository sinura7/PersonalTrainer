package com.sinura.personaltrainer.workout

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import com.sinura.personaltrainer.domain.UndoKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The floor's undo offers as a process death hands them back: what [SavedStateFloorUndo] writes
 * into the saved state, under which keys, and what it reads out again.
 *
 * The keys are the ones the shipped build wrote, spelled out rather than borrowed from the class,
 * because a phone updated mid-workout reads back what the old build saved. A renamed key would
 * lose the lifter's offers on the first process death after the update, and nothing else would
 * notice.
 */
class SavedStateFloorUndoTest {
    @Test
    fun aDeletedSetAndARemovedLiftComeBackFieldForField() {
        val handle = SavedStateHandle()
        val written = listOf(
            setEntry(SET_NO_RPE),
            liftEntry(LIFT_HOLD),
            setEntry(SET_WITH_RPE),
            liftEntry(LIFT_LOADED),
        )
        SavedStateFloorUndo(handle).write(entries = written, dwellMs = TALKBACK_DWELL_MS)

        val revived = SavedStateFloorUndo(handle)
        val read = revived.read()
        assertEquals("every token comes back field for field, in order", written.map { it.token }, read.map { it.token })
        assertEquals("every offer keeps its words", written.map { it.offer.message }, read.map { it.offer.message })
        assertEquals("every offer keeps its kind", written.map { it.offer.kind }, read.map { it.offer.kind })
        assertEquals("the dwell promised with the offers comes back with them", TALKBACK_DWELL_MS, revived.readDwellMs())
    }

    @Test
    fun theSavedKeysAreTheOnesTheShippedBuildWrote() {
        val handle = SavedStateHandle()
        SavedStateFloorUndo(handle).write(entries = listOf(setEntry(SET_NO_RPE), liftEntry(LIFT_HOLD)), dwellMs = TALKBACK_DWELL_MS)

        val expected = setOf(
            "floorUndo.count",
            "floorUndo.dwellMs",
            "floorUndo.item.0.type",
            "floorUndo.item.0.message",
            "floorUndo.item.0.setId",
            "floorUndo.item.0.sessionId",
            "floorUndo.item.0.exerciseId",
            "floorUndo.item.0.setNumber",
            "floorUndo.item.0.weightKg",
            "floorUndo.item.0.reps",
            "floorUndo.item.0.isWarmup",
            "floorUndo.item.0.completedAt",
            "floorUndo.item.0.durationSeconds",
            "floorUndo.item.1.type",
            "floorUndo.item.1.message",
            "floorUndo.item.1.itemId",
            "floorUndo.item.1.sessionId",
            "floorUndo.item.1.exerciseId",
            "floorUndo.item.1.sortOrder",
            "floorUndo.item.1.targetSets",
            "floorUndo.item.1.targetReps",
            "floorUndo.item.1.restSeconds",
            "floorUndo.item.1.targetSeconds",
            "floorUndo.item.1.targetSecondsMax",
            "floorUndo.item.1.name",
        )
        assertEquals(
            "exactly the shipped keys, with no key for the set's missing RPE or the lift's missing target weight",
            expected,
            handle.keys(),
        )
        assertTrue("the count is saved as an Int", handle.get<Any>("floorUndo.count") is Int)
        assertEquals("the count names both offers", 2, handle.get<Int>("floorUndo.count"))
        assertTrue("the dwell is saved as a Long", handle.get<Any>("floorUndo.dwellMs") is Long)
        assertEquals("a deleted set is typed \"set\"", "set", handle.get<String>("floorUndo.item.0.type"))
        assertEquals("a removed lift is typed \"lift\"", "lift", handle.get<String>("floorUndo.item.1.type"))
    }

    @Test
    fun aShorterQueueLeavesNothingReadablePastItsEnd() {
        val handle = SavedStateHandle()
        val undo = SavedStateFloorUndo(handle)
        val first = setEntry(SET_WITH_RPE)
        undo.write(entries = listOf(first, liftEntry(LIFT_LOADED), setEntry(SET_NO_RPE)), dwellMs = TALKBACK_DWELL_MS)
        undo.write(entries = listOf(first), dwellMs = TALKBACK_DWELL_MS)

        val stale = handle.keys().filter { it.startsWith("floorUndo.item.1.") || it.startsWith("floorUndo.item.2.") }
        assertEquals("nothing of the second and third offers is left behind", emptyList<String>(), stale)
        assertEquals("the one offer left is the one read back", listOf(first.token), undo.read().map { it.token })

        undo.write(entries = emptyList(), dwellMs = TALKBACK_DWELL_MS)
        assertEquals("an empty queue saves a count of 0", 0, handle.get<Int>("floorUndo.count"))
        assertEquals("an empty queue keeps the dwell it was written with", TALKBACK_DWELL_MS, undo.readDwellMs())
        assertEquals(
            "an empty queue leaves no offer's field behind",
            emptyList<String>(),
            handle.keys().filter { it.startsWith("floorUndo.item.") },
        )
        assertTrue("an empty queue reads back empty", undo.read().isEmpty())
    }

    @Test
    fun aRestoredOfferKeepsItsWordsAndIsKeyedAsRestored() {
        val handle = SavedStateHandle()
        val set = UndoEntry(
            token = FloorUndo.DeletedSet(SET_NO_RPE),
            offer = UndoOffer(key = "undo-7-set-set-a", message = "Set deleted · 225 lb × 5", kind = UndoKind.DELETED_SET),
        )
        val lift = UndoEntry(
            token = FloorUndo.RemovedLift(LIFT_HOLD),
            offer = UndoOffer(key = "undo-8-lift-item-plank", message = "Lift removed · Plank", kind = UndoKind.REMOVED_LIFT),
        )
        SavedStateFloorUndo(handle).write(entries = listOf(set, lift), dwellMs = TALKBACK_DWELL_MS)

        val read = SavedStateFloorUndo(handle).read()
        assertEquals(
            "a restored offer is keyed as restored, by its place and its type",
            listOf("undo-restored-0-set", "undo-restored-1-lift"),
            read.map { it.offer.key },
        )
        assertEquals(
            "a restored offer keeps the words it was saved with, pounds and all",
            listOf("Set deleted · 225 lb × 5", "Lift removed · Plank"),
            read.map { it.offer.message },
        )
    }

    @Test
    fun aFreshHandleOffersNothingAndPromisesNoDwell() {
        val fresh = SavedStateFloorUndo(SavedStateHandle())
        assertTrue("a handle nothing was saved to offers nothing", fresh.read().isEmpty())
        assertNull("a handle nothing was saved to promises no dwell", fresh.readDwellMs())

        val handle = SavedStateHandle()
        SavedStateFloorUndo(handle).write(entries = listOf(setEntry(SET_NO_RPE), liftEntry(LIFT_HOLD)), dwellMs = TALKBACK_DWELL_MS)
        handle.remove<String>("floorUndo.item.0.type")
        assertEquals(
            "an offer saved without its type is skipped and the rest still come back",
            listOf<FloorUndo>(FloorUndo.RemovedLift(LIFT_HOLD)),
            SavedStateFloorUndo(handle).read().map { it.token },
        )
    }

    private fun setEntry(deleted: WorkoutRepository.DeletedSet) = UndoEntry(
        token = FloorUndo.DeletedSet(deleted),
        offer = UndoOffer(
            key = "undo-0-set-${deleted.setId}",
            message = "Set deleted · ${deleted.weightKg} kg × ${deleted.reps}",
            kind = UndoKind.DELETED_SET,
        ),
    )

    private fun liftEntry(removed: WorkoutRepository.RemovedLift) = UndoEntry(
        token = FloorUndo.RemovedLift(removed),
        offer = UndoOffer(
            key = "undo-1-lift-${removed.item.id}",
            message = "Lift removed · ${removed.name}",
            kind = UndoKind.REMOVED_LIFT,
        ),
    )

    private companion object {
        const val TALKBACK_DWELL_MS = 20_000L

        /** A timed working set with no RPE: `rpe` is left out, `durationSeconds` is saved. */
        val SET_NO_RPE = WorkoutRepository.DeletedSet(
            setId = "set-a",
            sessionId = "session-1",
            exerciseId = "squat",
            setNumber = 2,
            weightKg = 102.5,
            reps = 5,
            rpe = null,
            isWarmup = false,
            completedAt = 1_700_000_123_000L,
            durationSeconds = 45,
        )

        /** A warm-up at RPE 8 with no duration: the opposite way round. */
        val SET_WITH_RPE = WorkoutRepository.DeletedSet(
            setId = "set-b",
            sessionId = "session-1",
            exerciseId = "squat",
            setNumber = 1,
            weightKg = 60.0,
            reps = 8,
            rpe = 8,
            isWarmup = true,
            completedAt = 1_700_000_001_000L,
            durationSeconds = null,
        )

        /** A hold with no target weight: `targetWeightKg` is left out, both hold lengths are saved. */
        val LIFT_HOLD = WorkoutRepository.RemovedLift(
            item = SessionExerciseEntity(
                id = "item-plank",
                sessionId = "session-1",
                exerciseId = "plank",
                sortOrder = 3,
                targetSets = 4,
                targetReps = 1,
                targetWeightKg = null,
                restSeconds = 75,
                targetSeconds = 30,
                targetSecondsMax = 45,
            ),
            name = "Plank",
        )

        /** A loaded lift with a target weight and no hold lengths. */
        val LIFT_LOADED = WorkoutRepository.RemovedLift(
            item = SessionExerciseEntity(
                id = "item-row",
                sessionId = "session-1",
                exerciseId = "row",
                sortOrder = 1,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = 60.0,
                restSeconds = 90,
                targetSeconds = null,
                targetSecondsMax = null,
            ),
            name = "Row",
        )
    }
}
