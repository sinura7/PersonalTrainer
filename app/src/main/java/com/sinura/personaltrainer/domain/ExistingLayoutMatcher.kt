package com.sinura.personaltrainer.domain

/**
 * Turns a generated blueprint into proposals that point at routines already on the phone.
 *
 * Deliberately inert: nothing here is written. Plan fills its proposals list with the
 * result and the existing "Use this week" path is the only write. Creating a routine here
 * would be the Settings rebuild, which is the thing this exists to avoid.
 *
 * Matching is name first (setup names the focus — `Upper`, `Pull`, `Full Body A`), then a
 * unique [SessionFocusKind] among what is left. Two customs of the same kind are not guessed.
 */
object ExistingLayoutMatcher {

    fun match(
        blueprint: PlanBlueprint,
        existing: List<Routine>,
        weekStartEpochDay: Long,
    ): List<SuggestedTrainingDay> {
        val unused = existing.toMutableList()
        val claimedByKey = LinkedHashMap<String, Routine>()
        val weekStart = CivilDate.fromEpochDay(weekStartEpochDay)
        val proposals = ArrayList<SuggestedTrainingDay>(blueprint.trainingDayCount)
        for (day in blueprint.days) {
            if (day.isRest) continue
            val planned = blueprint.routineFor(day) ?: continue
            val claimed = claim(planned, unused, claimedByKey) ?: continue
                val date = dateOn(weekStart, day.dayOfWeek)
            proposals += SuggestedTrainingDay(
                epochDay = date.epochDay,
                dayOfWeek = day.dayOfWeek,
                isRest = false,
                focusKind = planned.focusKind,
                focusTitle = planned.name,
                routineId = claimed.id,
                routineName = claimed.name,
                reason = WeekTwoCopy.PROPOSAL_REASON,
                emphasisMuscles = emptyList(),
                confidence = ScheduleConfidence.HIGH,
            )
        }
        return proposals
    }

    private fun claim(
        planned: BlueprintRoutine,
        unused: MutableList<Routine>,
        claimedByKey: MutableMap<String, Routine>,
    ): Routine? {
        // A four-day upper/lower week is two routines used twice. The second Upper day
        // must get the same id, not fail because we already "used" it.
        claimedByKey[planned.key]?.let { return it }
        val byName = unused.filter { routine ->
            routine.name.trim().equals(planned.name.trim(), ignoreCase = true)
        }
        val hit = when {
            byName.isNotEmpty() -> byName.first()
            else -> unused.filter { routine ->
                WeeklySchedulePlanner.classifyRoutine(routine) == planned.focusKind
            }.singleOrNull()
        } ?: return null
        unused.removeAll { it.id == hit.id }
        claimedByKey[planned.key] = hit
        return hit
    }

    private fun dateOn(weekStart: CivilDate, day: Weekday): CivilDate {
        var cursor = weekStart
        repeat(7) {
            if (cursor.dayOfWeek == day) return cursor
            cursor = cursor.plusDays(1)
        }
        return weekStart
    }
}
