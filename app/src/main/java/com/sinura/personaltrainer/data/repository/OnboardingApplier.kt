package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.BlueprintRoutine
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.PlanBlueprint
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.time.DayOfWeek

private const val TAG = "PT/Onboarding"

/** What "Use this plan" produced, or why it did not. */
sealed interface ApplyPlanResult {
    data class Applied(val routineCount: Int, val pinnedDays: Int) : ApplyPlanResult

    data class Failed(val message: String) : ApplyPlanResult
}

/**
 * Turns an accepted blueprint into a real program.
 *
 * Kept out of the view model because it is the one moment in the app that writes across four
 * stores at once — preferences, routines, their exercises, and the schedule — and the ordering
 * between them matters. Kept out of the repositories because it belongs to none of them.
 *
 * **It does not clear anything.** Setup can be re-run from Settings, and a lifter who does that
 * has weeks of history pointing at routines they still use. Adding a second Upper is a mess
 * they can see and fix in ten seconds; deleting the one their last month of sessions points at
 * is not recoverable from inside the app.
 */
class OnboardingApplier(
    private val routineRepository: RoutineRepository,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    suspend fun apply(
        answers: OnboardingAnswers,
        blueprint: PlanBlueprint,
        catalog: List<Exercise>,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): ApplyPlanResult {
        val clean = answers.sanitized()
        return runCatchingCancellable {
            // Preferences first. If anything below fails the lifter still gets an app that
            // knows their days, their goal and their equipment — a worse outcome than the
            // whole plan, but a much better one than a questionnaire they filled in for
            // nothing.
            val schedule = clean.schedulePreferences(weekStart)
            preferencesRepository.setTrainingDaysPerWeek(schedule.trainingDaysPerWeek)
            preferencesRepository.setSplitStyle(schedule.splitStyle)
            preferencesRepository.setWeekStart(schedule.weekStart)
            val coach = clean.coachPreferences()
            preferencesRepository.setTrainingGoal(coach.goal)
            preferencesRepository.setAvailableEquipment(coach.availableEquipment)
            preferencesRepository.setBodyweightKg(clean.bodyweightKg)

            val byId = catalog.associateBy { it.id }
            val createdIds = LinkedHashMap<String, String>()
            blueprint.routines.forEach { routine ->
                createdIds[routine.key] = createRoutine(routine, byId)
            }

            // Then the week. Anchored to the weekday rather than to a date, because a slot is
            // "Tuesday is an upper day" and not "the 14th was" — that is what lets the week
            // regenerate itself next Monday without anyone touching it.
            var pinned = 0
            blueprint.days.forEach { day ->
                val routineId = day.routineKey?.let(createdIds::get) ?: return@forEach
                scheduleRepository.pin(
                    routineId = routineId,
                    focusKind = null,
                    anchorDay = day.dayOfWeek,
                )
                pinned += 1
            }

            preferencesRepository.setOnboardingComplete(true)
            ApplyPlanResult.Applied(routineCount = createdIds.size, pinnedDays = pinned)
        }.getOrElse { thrown ->
            AppLog.e(TAG, "Applying the generated plan failed", thrown)
            ApplyPlanResult.Failed("Couldn’t build that plan. Your answers are saved — try again.")
        }
    }

    private suspend fun createRoutine(
        routine: BlueprintRoutine,
        byId: Map<String, Exercise>,
    ): String {
        val created = routineRepository.create(name = routine.name)
        routine.lifts.forEach { lift ->
            // A lift the catalog no longer has is skipped rather than fatal. The blueprint was
            // built from this same catalog moments ago, so it should not happen — but a routine
            // missing one accessory is a far better outcome than a setup that dies at the last
            // step and leaves half a program behind.
            val exercise = byId[lift.exerciseId] ?: return@forEach
            routineRepository.addExercise(
                routineId = created.id,
                exercise = exercise,
                targetSets = lift.targets.sets,
                targetReps = lift.targets.reps,
                targetWeightKg = null,
                restSeconds = lift.targets.restSeconds,
            )
        }
        return created.id
    }

    /**
     * Whether there is already a program here.
     *
     * Setup is reachable again from Settings, and the screen warns before adding a second copy
     * of everything. Asked of the schedule and the routines together because either alone is a
     * half-answer: pinned days with no routines is what an interrupted setup leaves behind.
     */
    suspend fun hasExistingProgram(): Boolean =
        scheduleRepository.slots().isNotEmpty() || routineRepository.count() > 0
}
