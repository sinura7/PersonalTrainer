package com.sinura.personaltrainer.domain

/**
 * Home's filled Volt opens one start sheet: free, a named Plan routine,
 * cardio, or Extra. It does not add a Plan row. Plan still adds.
 */
object HomeStartCopy {
    const val FREE = "Start a free workout"
    const val FREE_SUBTITLE = "Empty session. Add lifts as you go."
    const val ROUTINE = "Select a routine"
    const val ROUTINE_SUBTITLE = "A routine you already named on Plan."
    const val ROUTINE_EMPTY = "Create a routine on Plan, then start it here."
    const val CARDIO = "Cardio"
    const val CARDIO_SUBTITLE = "Walk, run, ride, row, swim, or hike."
    const val EXTRA = "Extra"
    const val EXTRA_SUBTITLE = "A warm-up or stretch. Asks what you have first."
    const val EMPTY_BODY = "Start a workout, cardio, or a warm-up / stretch block."
    const val PICK_ROUTINE = "Routine"
}

object HomeStart {
    /**
     * Routines the owner named on Plan. Extra packs have their own sheet
     * page; the week generator is not a start choice here.
     */
    fun namedRoutines(routines: List<Routine>): List<Routine> {
        val auxTitles = AuxiliaryPacks.all.map { pack -> pack.title.lowercase() }.toSet()
        return routines.filter { routine -> routine.name.lowercase() !in auxTitles }
    }
}
