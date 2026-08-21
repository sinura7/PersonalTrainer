package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v2 rules on the last line of defence before an irreversible wipe.
 *
 * Every rule here rejects something that would otherwise write a row the schema accepts and the
 * app cannot use: a credit pointing at an exercise the file does not contain, a weight of zero
 * that silently removes a muscle from the map, a schedule slot that is neither a routine nor a
 * focus (a state the model has no meaning for — rest is the ABSENCE of a slot, not an empty one).
 */
class BackupValidatorV2Test {

    @Test
    fun rejectsUnknownEquipment() {
        assertInvalid(base().withExercise(equipment = "PLASMA"), "equipment")
    }

    @Test
    fun rejectsUnknownLoadType() {
        assertInvalid(base().withExercise(loadType = "TELEKINETIC"), "load type")
    }

    @Test
    fun rejectsCreditWeightOutOfBounds() {
        listOf(0.0, 1.01, Double.NaN, Double.POSITIVE_INFINITY, -0.5).forEach { weight ->
            assertInvalid(
                base().copy(exerciseMuscles = listOf(BackupExerciseMuscle(EX, "chest", weight))),
                "weight",
            )
        }
    }

    @Test
    fun rejectsDanglingCreditExerciseId() {
        assertInvalid(
            base().copy(exerciseMuscles = listOf(BackupExerciseMuscle("ex-ghost", "chest", 1.0))),
            "not in this file",
        )
    }

    @Test
    fun rejectsDuplicateCreditPair() {
        assertInvalid(
            base().copy(
                exerciseMuscles = listOf(
                    BackupExerciseMuscle(EX, "chest", 1.0),
                    BackupExerciseMuscle(EX, "chest", 0.5),
                ),
            ),
            "same muscle twice",
        )
    }

    @Test
    fun rejectsAnchorDayOutOfRange() {
        assertInvalid(base().copy(scheduleSlots = listOf(slot(anchorDay = 7))), "day that does not exist")
        assertInvalid(base().copy(scheduleSlots = listOf(slot(anchorDay = -1))), "day that does not exist")
    }

    @Test
    fun rejectsDanglingScheduleRoutineId() {
        assertInvalid(base().copy(scheduleSlots = listOf(slot(routineId = "r-ghost"))), "routine")
    }

    @Test
    fun rejectsSlotWithNeitherRoutineNorFocus() {
        assertInvalid(
            base().copy(scheduleSlots = listOf(slot(routineId = null, focusKind = null))),
            "neither a routine nor a focus",
        )
    }

    @Test
    fun acceptsValidV2DocumentWithSlotsAndCredits() {
        val document = base().copy(
            exerciseMuscles = listOf(
                BackupExerciseMuscle(EX, "chest", 1.0),
                BackupExerciseMuscle(EX, "triceps", 0.5),
            ),
            scheduleSlots = listOf(slot(), slot(id = "slot-2", position = 1, routineId = null, focusKind = "pull")),
        )
        val result = BackupValidator.validate(document = document, localHasData = true)
        assertTrue(result.toString(), result is BackupValidation.Valid)
    }

    private fun assertInvalid(document: BackupDocument, fragment: String) {
        val result = BackupValidator.validate(document = document, localHasData = true)
        assertTrue("expected Invalid for $fragment, got $result", result is BackupValidation.Invalid)
        val reason = (result as BackupValidation.Invalid).reason
        assertTrue("reason '$reason' does not mention '$fragment'", reason.contains(fragment))
    }

    private fun BackupDocument.withExercise(
        equipment: String = "BARBELL",
        loadType: String = "EXTERNAL",
    ): BackupDocument = copy(
        exercises = listOf(
            BackupExercise(
                id = EX, name = "Bench Press", muscleGroup = "Chest", notes = "",
                isCustom = false, equipment = equipment, loadType = loadType,
            ),
        ),
    )

    private fun slot(
        id: String = "slot-1",
        position: Int = 0,
        routineId: String? = ROUTINE,
        focusKind: String? = null,
        anchorDay: Int? = 0,
    ): BackupScheduleSlot = BackupScheduleSlot(
        id = id,
        position = position,
        routineId = routineId,
        focusKind = focusKind,
        anchorDay = anchorDay,
        createdAt = STAMP,
        updatedAt = STAMP,
    )

    private fun base(): BackupDocument = BackupDocument(
        version = 2,
        exportedAt = "2026-08-21T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = listOf(
            BackupExercise(
                id = EX, name = "Bench Press", muscleGroup = "Chest", notes = "",
                isCustom = false, equipment = "BARBELL", loadType = "EXTERNAL",
            ),
        ),
        routines = listOf(BackupRoutine(ROUTINE, "Push", "", STAMP, STAMP)),
        routineExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        setLogs = emptyList(),
    )

    private companion object {
        const val EX = "ex-bench"
        const val ROUTINE = "r1"
        const val STAMP = 1_700_000_000_000L
    }
}
