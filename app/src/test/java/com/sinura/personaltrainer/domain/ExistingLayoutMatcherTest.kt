package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replay matches routines that already exist. It must never invent an id, and it must not
 * guess when two customs share a kind.
 */
class ExistingLayoutMatcherTest {

    private val weekStart = LocalDate.of(2026, 8, 17) // Monday

    @Test
    fun nameMatchClaimsTheExistingIdsOnThePickedDays() {
        val upper = existing("r-upper", "Upper")
        val lower = existing("r-lower", "Lower Body", "Quads")
        val blueprint = PlanBlueprint(
            splitStyle = SplitStyle.UPPER_LOWER,
            routines = listOf(
                planned("upper", "Upper", SessionFocusKind.UPPER),
                planned("lower", "Lower Body", SessionFocusKind.LOWER),
            ),
            days = listOf(
                BlueprintDay(Weekday.MONDAY, null),
                BlueprintDay(Weekday.TUESDAY, "upper"),
                BlueprintDay(Weekday.WEDNESDAY, null),
                BlueprintDay(Weekday.THURSDAY, "lower"),
                BlueprintDay(Weekday.FRIDAY, null),
                BlueprintDay(Weekday.SATURDAY, null),
                BlueprintDay(Weekday.SUNDAY, "upper"),
            ),
        )

        val proposals = ExistingLayoutMatcher.match(
            blueprint, listOf(upper, lower), weekStart.toEpochDay(),
        )

        assertEquals(3, proposals.size)
        assertEquals(setOf(Weekday.TUESDAY, Weekday.THURSDAY, Weekday.SUNDAY),
            proposals.map { it.dayOfWeek }.toSet())
        assertEquals("r-upper", proposals.single { it.dayOfWeek == Weekday.TUESDAY }.routineId)
        assertEquals("r-lower", proposals.single { it.dayOfWeek == Weekday.THURSDAY }.routineId)
        assertEquals("r-upper", proposals.single { it.dayOfWeek == Weekday.SUNDAY }.routineId)
        assertTrue(proposals.all { it.slotId == null })
        assertTrue(proposals.all { it.reason == WeekTwoCopy.PROPOSAL_REASON })
        assertEquals(
            weekStart.plusDays(1).toEpochDay(),
            proposals.single { it.dayOfWeek == Weekday.TUESDAY }.epochDay,
        )
    }

    @Test
    fun uniqueFocusKindMatchesWhenTheNameWasChanged() {
        val renamed = existing("r-push", "My Push")
        val blueprint = PlanBlueprint(
            splitStyle = SplitStyle.PUSH_PULL_LEGS,
            routines = listOf(planned("push", "Push", SessionFocusKind.PUSH)),
            days = listOf(BlueprintDay(Weekday.MONDAY, "push")),
        )

        val proposals = ExistingLayoutMatcher.match(
            blueprint, listOf(renamed), weekStart.toEpochDay(),
        )

        assertEquals(1, proposals.size)
        assertEquals("r-push", proposals.single().routineId)
    }

    @Test
    fun twoCustomsOfTheSameKindAreNotGuessed() {
        val first = existing("r-a", "Alpha", "Chest", "Shoulders")
        val second = existing("r-b", "Bravo", "Chest", "Shoulders")
        val blueprint = PlanBlueprint(
            splitStyle = SplitStyle.PUSH_PULL_LEGS,
            routines = listOf(planned("push", "Push", SessionFocusKind.PUSH)),
            days = listOf(BlueprintDay(Weekday.MONDAY, "push")),
        )

        assertTrue(
            ExistingLayoutMatcher.match(
                blueprint, listOf(first, second), weekStart.toEpochDay(),
            ).isEmpty(),
        )
    }

    @Test
    fun nothingMatchingIsAnEmptyListNotAFabricatedId() {
        val custom = existing("r-mine", "My Own Thing", "Quads")
        val blueprint = PlanBlueprint(
            splitStyle = SplitStyle.UPPER_LOWER,
            routines = listOf(planned("upper", "Upper", SessionFocusKind.UPPER)),
            days = listOf(BlueprintDay(Weekday.MONDAY, "upper")),
        )

        assertTrue(
            ExistingLayoutMatcher.match(
                blueprint, listOf(custom), weekStart.toEpochDay(),
            ).isEmpty(),
        )
    }

    @Test
    fun restDaysNeverBecomeProposals() {
        val upper = existing("r-upper", "Upper")
        val blueprint = PlanBlueprint(
            splitStyle = SplitStyle.UPPER_LOWER,
            routines = listOf(planned("upper", "Upper", SessionFocusKind.UPPER)),
            days = listOf(
                BlueprintDay(Weekday.MONDAY, null),
                BlueprintDay(Weekday.TUESDAY, "upper"),
            ),
        )

        val proposals = ExistingLayoutMatcher.match(
            blueprint, listOf(upper), weekStart.toEpochDay(),
        )
        assertEquals(listOf(Weekday.TUESDAY), proposals.map { it.dayOfWeek })
    }

    @Test
    fun claimedIdsAreOnlyIdsThatAlreadyExisted() {
        val upper = existing("r-upper", "Upper")
        val blueprint = PlanBlueprint(
            splitStyle = SplitStyle.UPPER_LOWER,
            routines = listOf(planned("upper", "Upper", SessionFocusKind.UPPER)),
            days = listOf(BlueprintDay(Weekday.MONDAY, "upper")),
        )
        val ids = ExistingLayoutMatcher.match(
            blueprint, listOf(upper), weekStart.toEpochDay(),
        ).map { it.routineId }
        assertEquals(listOf("r-upper"), ids)
    }

    private fun planned(key: String, name: String, kind: SessionFocusKind) =
        BlueprintRoutine(key = key, name = name, focusKind = kind, lifts = emptyList())

    private fun existing(id: String, name: String, vararg muscles: String): Routine {
        val groups = muscles.toList().ifEmpty { listOf("Chest") }
        return Routine(
            id = id,
            name = name,
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = groups.mapIndexed { index, muscle ->
                RoutineExercise(
                    id = "re-$id-$index",
                    routineId = id,
                    exercise = Exercise(
                        id = "ex-$id-$index",
                        name = "Lift $index",
                        muscleGroup = muscle,
                        notes = "",
                        isCustom = false,
                    ),
                    sortOrder = index,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = null,
                    restSeconds = 90,
                )
            },
        )
    }
}
