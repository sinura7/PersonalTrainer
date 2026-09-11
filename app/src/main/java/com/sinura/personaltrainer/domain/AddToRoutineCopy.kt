package com.sinura.personaltrainer.domain

/**
 * How the add-to-routine sheet names the landing row and the destination.
 *
 * L-05: the sheet used to be a title, a sentence, and a list of routine
 * names. [AddDefaults] already sizes the row; this is the preview of that
 * row and of the floor it is going onto, so a tap is a place, not a
 * text button.
 */
data class AddToRoutineDestination(
    val id: String,
    val name: String,
    val lifts: List<Exercise>,
    val alreadyHolds: Boolean = false,
)

object AddToRoutineCopy {
    const val STILL_LIMIT = RoutineCardCopy.STILL_LIMIT
    const val EDITABLE = "Editable on the routine."
    const val ALREADY = "Already in this routine"
    const val CREATE = "Create routine"
    const val EMPTY_LIBRARY = "No routines yet. Create one first, then add this lift."
    const val EMPTY_DETAIL =
        "You have no routines yet. Build one in Plan and this lift can go straight into it."
    const val NO_LIFTS = "No lifts yet"

    fun title(exerciseName: String): String = "Add $exerciseName"

    fun landing(exercise: Exercise): TargetDefaults = AddDefaults.forExercise(exercise)

    fun workValue(defaults: TargetDefaults): String =
        SessionOrderCopy.workValue(defaults.sets, defaults.reps)

    fun restClock(defaults: TargetDefaults): String = RestTimer.formatClock(defaults.restSeconds)

    fun stills(lifts: List<Exercise>, limit: Int = STILL_LIMIT): List<Exercise> =
        lifts.take(limit.coerceAtLeast(0))

    fun mix(lifts: List<Exercise>): String? = RoutineCardCopy.mix(lifts.map { it.equipment })

    fun liftCount(count: Int): String = if (count == 1) "1 lift" else "$count lifts"

    fun destinationSubtitle(destination: AddToRoutineDestination): String =
        if (destination.alreadyHolds) ALREADY else liftCount(destination.lifts.size)

    fun destinations(routines: List<Routine>, exerciseId: String): List<AddToRoutineDestination> =
        routines.map { destination(it, exerciseId) }

    fun destination(routine: Routine, exerciseId: String): AddToRoutineDestination {
        val lifts = routine.exercises.map { it.exercise }
        return AddToRoutineDestination(
            id = routine.id,
            name = routine.name,
            lifts = lifts,
            alreadyHolds = lifts.any { it.id == exerciseId },
        )
    }

    fun destination(routine: Routine, alreadyHolds: Boolean): AddToRoutineDestination =
        AddToRoutineDestination(
            id = routine.id,
            name = routine.name,
            lifts = routine.exercises.map { it.exercise },
            alreadyHolds = alreadyHolds,
        )

    fun spokenLanding(exercise: Exercise): String {
        val defaults = landing(exercise)
        return "${exercise.name}, ${workValue(defaults)}, rest ${restClock(defaults)}"
    }

    fun spokenDestination(destination: AddToRoutineDestination): String {
        val mix = mix(destination.lifts)
        return listOfNotNull(
            destination.name,
            destinationSubtitle(destination),
            mix,
        ).joinToString(", ")
    }
}
