package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.BlueprintRoutine
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.PlanBlueprint
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate

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
    /**
     * @param weekStart the lifter's stored first day of the week. Required, and deliberately
     * not defaulted: it used to default to Monday and the one caller never passed it, so every
     * run of setup wrote Monday over whatever the lifter had chosen. Setup is re-enterable from
     * Settings, so a Sunday-week lifter who re-ran it was silently moved to Monday — reshaping
     * their week derivation, their heat window and every weekly chart. The questionnaire has no
     * question about week start, so setup has no answer to write; this value is read from
     * preferences by the caller, used to lay the week out, and written back by nobody.
     * @param today passed in rather than read from the clock here, so the block's start date is
     * testable and so it agrees with the date the rest of the flow is working from.
     */
    suspend fun apply(
        answers: OnboardingAnswers,
        blueprint: PlanBlueprint,
        catalog: List<Exercise>,
        weekStart: Weekday,
        today: LocalDate,
    ): ApplyPlanResult {
        val clean = answers.sanitized()
        return runCatchingCancellable {
            // Preferences first. If anything below fails the lifter still gets an app that
            // knows their days, their goal and their equipment — a worse outcome than the
            // whole plan, but a much better one than a questionnaire they filled in for
            // nothing.
            val schedule = clean.schedulePreferences(weekStart)
            // Days per week and split style are answers the questionnaire actually asked for,
            // so a re-run overwriting them is the point. Week start is not asked, and is not
            // written — see the note on [weekStart] above.
            preferencesRepository.setTrainingDaysPerWeek(schedule.trainingDaysPerWeek)
            preferencesRepository.setSplitStyle(schedule.splitStyle)
            val coach = clean.coachPreferences()
            preferencesRepository.setTrainingGoal(coach.goal)
            preferencesRepository.setTrainingEmphasis(coach.emphasis)
            preferencesRepository.setAvailableEquipment(coach.availableEquipment)
            // Age, preferred days and place used to die at accept. Replay of an empty week
            // has to reconstruct the questionnaire from what is already stored — without
            // these three it can only guess NEW / spaced days / inferPlace.
            preferencesRepository.setTrainingAge(clean.trainingAge)
            preferencesRepository.setPreferredDays(clean.preferredDays)
            preferencesRepository.setTrainingPlaces(clean.resolvedPlaces())
            preferencesRepository.setTrainingFocus(clean.focus)
            // Recorded as a weigh-in, not just stored: it is the opening reading of the block
            // being started on the next line, and the block review compares against it.
            clean.bodyweightKg?.let { kg ->
                preferencesRepository.recordBodyweight(kg, today.toEpochDay())
            }
            // The block starts the moment a plan is accepted, not the moment the app was
            // installed: what is being counted is twelve weeks of *this* programme. beginBlock
            // keeps the one this replaces if it had finished — re-running setup the week after
            // a block ends should not lose the block that ended.
            preferencesRepository.beginBlock(
                next = TrainingBlock.startingIn(
                    today = com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(today.toEpochDay()),
                    weekStart = weekStart,
                ),
                todayEpochDay = today.toEpochDay(),
            )

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
     * Writes a week the lifter built by hand.
     *
     * Same stores as [apply]. Questionnaire fields are written only when [answers] is
     * non-null — the preview path that already asked them. Fork-only custom week does
     * not invent a goal.
     */
    suspend fun applyCustom(
        days: Map<Weekday, List<CustomWeekLift>>,
        weekStart: Weekday,
        today: LocalDate,
        answers: OnboardingAnswers? = null,
    ): ApplyPlanResult {
        if (!CustomWeekPolicy.canConfirm(days)) {
            return ApplyPlanResult.Failed("Add at least one lift to a day.")
        }
        return runCatchingCancellable {
            val trainingDays = CustomWeekPolicy.trainingDayCount(days)
            preferencesRepository.setTrainingDaysPerWeek(trainingDays)
            preferencesRepository.setSplitStyle(SplitStyle.CUSTOM)
            preferencesRepository.setPreferredDays(days.filter { it.value.isNotEmpty() }.keys)
            answers?.sanitized()?.let { clean ->
                val coach = clean.coachPreferences()
                preferencesRepository.setTrainingGoal(coach.goal)
                preferencesRepository.setTrainingEmphasis(coach.emphasis)
                preferencesRepository.setAvailableEquipment(coach.availableEquipment)
                preferencesRepository.setTrainingAge(clean.trainingAge)
                preferencesRepository.setTrainingPlaces(clean.resolvedPlaces())
                preferencesRepository.setTrainingFocus(clean.focus)
                clean.bodyweightKg?.let { kg ->
                    preferencesRepository.recordBodyweight(kg, today.toEpochDay())
                }
            }
            preferencesRepository.beginBlock(
                next = TrainingBlock.startingIn(
                    today = com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(today.toEpochDay()),
                    weekStart = weekStart,
                ),
                todayEpochDay = today.toEpochDay(),
            )
            var pinned = 0
            days.entries
                .filter { it.value.isNotEmpty() }
                .forEach { (day, lifts) ->
                    val created = routineRepository.create(
                        name = CustomWeekPolicy.routineName(day),
                    )
                    lifts.forEach { lift ->
                        routineRepository.addExercise(
                            routineId = created.id,
                            exercise = lift.exercise,
                            targetSets = lift.targetSets,
                            targetReps = lift.targetReps,
                            targetWeightKg = lift.targetWeightKg,
                            restSeconds = lift.restSeconds,
                        )
                    }
                    scheduleRepository.pin(
                        routineId = created.id,
                        focusKind = null,
                        anchorDay = day,
                    )
                    pinned += 1
                }
            preferencesRepository.setOnboardingComplete(true)
            ApplyPlanResult.Applied(routineCount = pinned, pinnedDays = pinned)
        }.getOrElse { thrown ->
            AppLog.e(TAG, "Applying a custom week failed", thrown)
            ApplyPlanResult.Failed("Couldn’t save that week. Try again.")
        }
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
