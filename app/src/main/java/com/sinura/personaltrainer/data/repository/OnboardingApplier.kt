package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.AppRoomDatabase
import com.sinura.personaltrainer.domain.BlueprintRoutine
import com.sinura.personaltrainer.domain.CustomWeekLift
import com.sinura.personaltrainer.domain.CustomWeekPolicy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.FrozenLift
import com.sinura.personaltrainer.domain.FrozenRoutine
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.PlanAcceptReceipt
import com.sinura.personaltrainer.domain.PlanBlueprint
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first

private const val TAG = "PT/Onboarding"

enum class AcceptCrashPoint { NONE, AFTER_BLOCK, AFTER_PINS }

private class AcceptCrash : RuntimeException()

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
 * **A finished program is never deleted.** Setup can be re-run from Settings, and a
 * lifter who does that has weeks of history pointing at routines they still use.
 * Adding a second Upper is a mess they can see and fix in ten seconds; deleting
 * the one their last month of sessions points at is not recoverable from inside
 * the app.
 *
 * **A failed attempt is.** Routine creates and pins run in one Room transaction.
 * If that write — or the onboarding-complete flag after it — fails, the routines
 * this attempt just created are dropped so "try again" cannot mint a second copy.
 */
class OnboardingApplier(
    private val database: AppRoomDatabase,
    private val routineRepository: RoutineRepository,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val failAfterRoutines: Boolean = false,
) {
    /** Test hook. A crash leaves the rows already committed and does not roll them back. */
    var crashPoint: AcceptCrashPoint = AcceptCrashPoint.NONE

    /** Test hook. Thrown before any catch so the screen has to clear its own busy flag. */
    var throwBeforeCatch: Boolean = false
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
     * @param keepCurrentBlock Settings' "Generate a week": add a week's routines inside the
     * current block, finished or not (the next twelve start from Your plan). Setup starts a new
     * block and records its opening weigh-in; this door is not setup, and doing either there
     * threw away an unfinished block and logged the last stored bodyweight, however old, as
     * today's weigh-in (audit UI-3). It never records a weigh-in, and starts a block only when
     * there is none.
     */
    suspend fun apply(
        answers: OnboardingAnswers,
        blueprint: PlanBlueprint,
        catalog: List<Exercise>,
        weekStart: Weekday,
        today: LocalDate,
        keepCurrentBlock: Boolean = false,
        acceptanceId: String? = null,
    ): ApplyPlanResult {
        if (throwBeforeCatch) error("accept threw before it could catch")
        val clean = answers.sanitized()
        // Generate a week is a different door. It may add routines inside the current block
        // even after setup has already accepted a plan.
        val visitId = acceptanceId?.takeUnless { keepCurrentBlock }
        val createdIds = LinkedHashMap<String, String>()
        return runCatchingCancellable {
            val receipt = visitId?.let { freezeReceipt(it, clean, blueprint, weekStart, today) }
            if (visitId != null && preferencesRepository.acceptedPlanId() == visitId || receipt?.complete == true) {
                preferencesRepository.setOnboardingComplete(true)
                return@runCatchingCancellable ApplyPlanResult.Applied(
                    routineCount = receipt?.pins?.map { it.second }?.distinct()?.size
                        ?: blueprint.routines.size,
                    pinnedDays = receipt?.pins?.size
                        ?: blueprint.days.count { it.routineKey != null },
                )
            }
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
            val epochDay = receipt?.epochDay ?: today.toEpochDay()
            val block = receipt?.let {
                TrainingBlock(startEpochDay = it.blockStartEpochDay, weeks = TrainingBlock.DEFAULT_WEEKS)
            } ?: TrainingBlock.startingIn(
                today = com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(epochDay),
                weekStart = weekStart,
            )
            if (!keepCurrentBlock) {
                // Recorded as a weigh-in, not just stored: it is the opening reading of the block
                // being started below, and the block review compares against it.
                // The day is the one frozen on the receipt, so a retry on a later day
                // replaces that same day instead of logging a second weigh-in.
                val kg = receipt?.bodyweightKg ?: clean.bodyweightKg
                kg?.let { preferencesRepository.recordBodyweight(it, epochDay) }
            }
            if (!keepCurrentBlock || preferencesRepository.trainingBlock.first() == null) {
                // The block starts the moment a plan is accepted, not the moment the app was
                // installed: what is being counted is twelve weeks of *this* programme. beginBlock
                // keeps the one this replaces if it had finished — re-running setup the week after
                // a block ends should not lose the block that ended. A retry whose block start
                // already matches the receipt does not delete that block and start another.
                startBlockOnce(block, epochDay)
            }
            if (crashPoint == AcceptCrashPoint.AFTER_BLOCK) throw AcceptCrash()

            val byId = catalog.associateBy { it.id }
            val pinned = if (receipt != null) {
                writeFrozenProgram(receipt, blueprint, byId, createdIds)
            } else {
                writeGeneratedProgram(blueprint, byId, createdIds, visitId)
            }
            if (crashPoint == AcceptCrashPoint.AFTER_PINS) throw AcceptCrash()
            preferencesRepository.setOnboardingComplete(true)
            if (visitId != null) preferencesRepository.setAcceptedPlanId(visitId)
            if (receipt != null) {
                preferencesRepository.savePlanAcceptReceipt(receipt.copy(complete = true))
            }
            ApplyPlanResult.Applied(routineCount = createdIds.size, pinnedDays = pinned)
        }.getOrElse { thrown ->
            if (thrown is AcceptCrash) {
                return@getOrElse ApplyPlanResult.Failed("Couldn’t build that plan. Your answers are saved — try again.")
            }
            AppLog.e(TAG, "Applying the generated plan failed", thrown)
            dropAttempt(createdIds.values)
            ApplyPlanResult.Failed("Couldn’t build that plan. Your answers are saved — try again.")
        }
    }

    /**
     * Records the visit before the block and the days. A receipt already stored for
     * this visit is left as it was, including its day and its pins.
     */
    private suspend fun freezeReceipt(
        visitId: String,
        answers: OnboardingAnswers,
        blueprint: PlanBlueprint,
        weekStart: Weekday,
        today: LocalDate,
    ): PlanAcceptReceipt {
        preferencesRepository.planAcceptReceipt()?.takeIf { it.id == visitId }?.let { return it }
        val epochDay = today.toEpochDay()
        val block = TrainingBlock.startingIn(
            today = com.sinura.personaltrainer.domain.CivilDate.fromEpochDay(epochDay),
            weekStart = weekStart,
        )
        val receipt = PlanAcceptReceipt(
            id = visitId,
            epochDay = epochDay,
            bodyweightKg = answers.bodyweightKg,
            blockStartEpochDay = block.startEpochDay,
            pins = blueprint.days.mapNotNull { day -> day.routineKey?.let { day.dayOfWeek to it } },
            routines = blueprint.routines.map { routine ->
                FrozenRoutine(
                    key = routine.key,
                    name = routine.name,
                    lifts = routine.lifts.map { lift ->
                        FrozenLift(
                            exerciseId = lift.exerciseId,
                            sets = lift.targets.sets,
                            reps = lift.targets.reps,
                            restSeconds = lift.targets.restSeconds,
                        )
                    },
                )
            },
            complete = false,
        )
        preferencesRepository.savePlanAcceptReceipt(receipt)
        return receipt
    }

    private suspend fun startBlockOnce(desired: TrainingBlock, epochDay: Long) {
        val current = preferencesRepository.trainingBlock.first()
        if (current != null &&
            current.startEpochDay == desired.startEpochDay &&
            current.weeks == desired.weeks
        ) {
            return
        }
        preferencesRepository.beginBlock(next = desired, todayEpochDay = epochDay)
    }

    private suspend fun createRoutine(
        routine: BlueprintRoutine,
        byId: Map<String, Exercise>,
        id: String? = null,
    ): String {
        if (id != null) routineRepository.getById(id)?.let { return it.id }
        val created = routineRepository.create(name = routine.name, id = id ?: UUID.randomUUID().toString())
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

    /** Same visit and same routine key always name the same stored routine. */
    private fun stableRoutineId(acceptanceId: String, routineKey: String): String =
        UUID.nameUUIDFromBytes("$acceptanceId:$routineKey".toByteArray(Charsets.UTF_8)).toString()

    private fun stablePinId(acceptanceId: String, routineKey: String, day: Weekday): String =
        UUID.nameUUIDFromBytes("$acceptanceId:pin:$day:$routineKey".toByteArray(Charsets.UTF_8)).toString()

    /** Pins only the days frozen on the receipt, and reuses a slot that already has that id. */
    private suspend fun writeFrozenProgram(
        receipt: PlanAcceptReceipt,
        blueprint: PlanBlueprint,
        byId: Map<String, Exercise>,
        createdIds: MutableMap<String, String>,
    ): Int = database.withTransaction {
        createdIds.clear()
        val live = blueprint.routines.associateBy { it.key }
        val frozen = receipt.routines.associateBy { it.key }
        var pinned = 0
        receipt.pins.forEach { (day, key) ->
            val routine = live[key] ?: frozen[key]?.toBlueprint() ?: return@forEach
            val routineId = createRoutine(routine, byId, stableRoutineId(receipt.id, key))
            createdIds[key] = routineId
            scheduleRepository.pin(
                routineId = routineId,
                focusKind = null,
                anchorDay = day,
                id = stablePinId(receipt.id, key, day),
            )
            pinned += 1
        }
        if (failAfterRoutines) error("test: fail after routine creates")
        pinned
    }

    /**
     * Then the week. Anchored to the weekday rather than to a date, because a slot is
     * "Tuesday is an upper day" and not "the 14th was" — that is what lets the week
     * regenerate itself next Monday without anyone touching it.
     */
    private suspend fun writeGeneratedProgram(
        blueprint: PlanBlueprint,
        byId: Map<String, Exercise>,
        createdIds: MutableMap<String, String>,
        acceptanceId: String? = null,
    ): Int = database.withTransaction {
        createdIds.clear()
        blueprint.routines.forEach { routine ->
            val id = acceptanceId?.let { stableRoutineId(it, routine.key) }
            createdIds[routine.key] = createRoutine(routine, byId, id)
        }
        if (failAfterRoutines) error("test: fail after routine creates")
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
        pinned
    }

    private suspend fun writeCustomProgram(
        days: Map<Weekday, List<CustomWeekLift>>,
        createdIds: MutableList<String>,
    ): Int = database.withTransaction {
        createdIds.clear()
        var pinned = 0
        days.entries
            .filter { it.value.isNotEmpty() }
            .forEach { (day, lifts) ->
                val created = routineRepository.create(
                    name = CustomWeekPolicy.routineName(day),
                )
                createdIds += created.id
                if (failAfterRoutines) error("test: fail after routine creates")
                lifts.forEach { lift ->
                    routineRepository.addExercise(
                        routineId = created.id,
                        exercise = lift.exercise,
                        targetSets = lift.targetSets,
                        targetReps = lift.targetReps,
                        targetWeightKg = lift.targetWeightKg,
                        restSeconds = lift.restSeconds,
                        targetSeconds = lift.targetSeconds,
                        targetSecondsMax = lift.targetSecondsMax,
                    )
                }
                scheduleRepository.pin(
                    routineId = created.id,
                    focusKind = null,
                    anchorDay = day,
                )
                pinned += 1
            }
        pinned
    }

    private suspend fun dropAttempt(routineIds: Collection<String>) {
        routineIds.forEach { id ->
            runCatchingCancellable { routineRepository.delete(id) }
        }
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
        val createdIds = mutableListOf<String>()
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
            val pinned = writeCustomProgram(days, createdIds)
            preferencesRepository.setOnboardingComplete(true)
            ApplyPlanResult.Applied(routineCount = pinned, pinnedDays = pinned)
        }.getOrElse { thrown ->
            AppLog.e(TAG, "Applying a custom week failed", thrown)
            dropAttempt(createdIds)
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
