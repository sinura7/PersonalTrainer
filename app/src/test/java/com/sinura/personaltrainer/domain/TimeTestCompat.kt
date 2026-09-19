package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.toCivilDate
import com.sinura.personaltrainer.util.toCivilYearMonth
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Keep existing test fixtures compiling against the P5.1 seams.
 * Production call sites use [TimePort] and [CivilDate] directly.
 */
fun HeatWindow.startMs(
    nowMs: Long,
    zone: ZoneId,
    weekStart: Weekday = Weekday.MONDAY,
): Long = startMs(nowMs, JvmTime, weekStart, zone.id)

fun MuscleLoadCalculator.snapshot(
    sessions: List<WorkoutSession>,
    window: HeatWindow,
    nowMs: Long,
    zone: ZoneId,
    exerciseCatalog: Map<String, Exercise> = emptyMap(),
    weekStart: Weekday = Weekday.MONDAY,
): BodyHeatSnapshot = snapshot(
    sessions, window, nowMs, JvmTime, zone.id, exerciseCatalog, weekStart,
)

fun MuscleLoadCalculator.coachBasis(
    sessions: List<WorkoutSession>,
    nowMs: Long,
    zone: ZoneId,
    exerciseCatalog: Map<String, Exercise> = emptyMap(),
    lastTrainedByMuscle: Map<CanonicalMuscle, Long> = emptyMap(),
): CoachBasis =
    coachBasis(sessions, nowMs, JvmTime, zone.id, exerciseCatalog, lastTrainedByMuscle)

fun MuscleLoadCalculator.daysSince(
    lastTrainedAtMs: Long?,
    nowMs: Long,
    zone: ZoneId,
): Int? = daysSince(lastTrainedAtMs, nowMs, JvmTime, zone.id)

fun DayLabel.relative(thenMs: Long, nowMs: Long, zone: ZoneId): String? =
    relative(thenMs, nowMs, JvmTime, zone.id)

fun todayEpochDay(nowMs: Long, zone: ZoneId): Long =
    todayEpochDay(nowMs, JvmTime, zone.id)

/**
 * Civil today from the real clock in the default zone.
 *
 * The domain function carried this as `nowMs = JvmTime.nowMillis(), time = JvmTime` defaults,
 * which is what tied `domain/` to `util/`. Tests that mean "whatever day it is now" keep
 * saying so; the reach for the platform clock is declared here, in the test source set,
 * where it belongs.
 */
fun todayEpochDay(): Long = todayEpochDay(JvmTime.nowMillis(), JvmTime)

fun WorkoutSession.toSummary(): SessionSummary = toSummary(JvmTime)

fun WorkoutSession.toHistoryEntry(): HistoryEntry = toHistoryEntry(JvmTime)

fun BodyHeatSnapshot.rememberLifetimeWork(
    summaries: List<SessionSummary>,
    nowMs: Long,
    zoneId: String,
): BodyHeatSnapshot = rememberLifetimeWork(summaries, nowMs, JvmTime, zoneId)

fun TrainingCalendarBuilder.build(
    month: CivilYearMonth,
    sessions: List<WorkoutSession>,
    activities: List<ActivitySession>,
): TrainingMonth = build(month, sessions, activities, JvmTime)

fun WorkoutSession.performedEpochDay(zone: ZoneId): Long =
    performedEpochDay(JvmTime, zone.id)

fun TrainingBlock.Companion.startingIn(
    today: LocalDate,
    weekStart: Weekday,
    weeks: Int = TrainingBlock.DEFAULT_WEEKS,
): TrainingBlock = startingIn(today.toCivilDate(), weekStart, weeks)

fun LighterWeek.weekStartEpochDay(today: LocalDate, weekStart: Weekday): Long =
    weekStartEpochDay(today.toCivilDate(), weekStart)

fun TrainingCalendarBuilder.build(
    month: YearMonth,
    sessions: List<WorkoutSession>,
    zone: ZoneId,
    weekStart: Weekday = Weekday.MONDAY,
): TrainingMonth = build(
    month.toCivilYearMonth(),
    sessions,
    emptyList(),
    JvmTime,
    weekStart,
    zone.id,
)

fun ExerciseHistoryBuilder.build(
    exerciseId: String,
    sessions: List<WorkoutSession>,
    loadClass: LoadClass = sessions.firstOrNull { session ->
        session.exercises.any { it.exercise.id == exerciseId }
    }?.loadClassOf(exerciseId) ?: LoadClass.LOADED,
    zone: ZoneId,
    weekStart: Weekday = Weekday.MONDAY,
): ExerciseHistory = build(exerciseId, sessions, loadClass, JvmTime, zone.id, weekStart)

fun ExerciseHistoryBuilder.fromEntries(
    exerciseId: String,
    entries: List<ExerciseSetEntry>,
    loadClass: LoadClass,
    zone: ZoneId,
    weekStart: Weekday = Weekday.MONDAY,
): ExerciseHistory = fromEntries(exerciseId, entries, loadClass, JvmTime, zone.id, weekStart)

fun BlockReviewBuilder.build(
    block: TrainingBlock,
    sessions: List<WorkoutSession>,
    unit: WeightUnit,
    zone: ZoneId,
    bodyweightLog: List<BodyweightEntry> = emptyList(),
): BlockReview = build(
    block,
    sessions.mapNotNull { it.toCompletedTraining(JvmTime, zone.id) },
    unit,
    bodyweightLog,
)

fun BlockReviewBuilder.overRange(
    startEpochDay: Long,
    endExclusiveEpochDay: Long,
    sessions: List<WorkoutSession>,
    unit: WeightUnit,
    zone: ZoneId,
): HorizonProgress = overRange(
    startEpochDay,
    endExclusiveEpochDay,
    sessions.mapNotNull { it.toCompletedTraining(JvmTime, zone.id) },
    unit,
)

fun WeekDerivation.derive(
    slots: List<ScheduleSlot>,
    history: List<WorkoutSession>,
    preferences: SchedulePreferences,
    nowMs: Long,
    zone: ZoneId,
): DerivedWeek = derive(slots, history, preferences, nowMs, JvmTime, zone.id)

fun WeeklySchedulePlanner.plan(
    preferences: SchedulePreferences,
    snapshot: BodyHeatSnapshot,
    recommendations: List<TrainingRecommendation>,
    routines: List<Routine>,
    recentSessions: List<WorkoutSession>,
    nowMs: Long,
    zone: ZoneId,
    pinnedSlots: List<ScheduleSlot> = emptyList(),
    emphasis: TrainingEmphasis = TrainingEmphasis.BALANCED,
): WeeklySchedulePlan = WeeklySchedulePlanner.plan(
    preferences,
    snapshot,
    recommendations,
    routines,
    recentSessions,
    nowMs,
    JvmTime,
    zone.id,
    pinnedSlots,
    emphasis,
)

fun DeloadSignal.detect(
    history: List<WorkoutSession>,
    nowMs: Long,
    zone: ZoneId,
): DeloadFinding? = detect(history, nowMs, JvmTime, zone.id)

fun TrainingInsightsInput(
    history: List<WorkoutSession>,
    summaries: List<SessionSummary> = emptyList(),
    routines: List<Routine>,
    exerciseCatalog: Map<String, Exercise>,
    lastLoggedAtByExerciseId: Map<String, Long> = emptyMap(),
    hints: List<ProgressionHint>?,
    preferences: SchedulePreferences,
    slots: List<ScheduleSlot> = emptyList(),
    coachPrefs: CoachPreferences = CoachPreferences.DEFAULT,
    unit: WeightUnit,
    window: HeatWindow,
    nowMs: Long,
    zone: ZoneId,
    includeWeekPlan: Boolean = true,
): TrainingInsightsInput {
    val input = TrainingInsightsInput(
        history = history,
        summaries = summaries,
        routines = routines,
        exerciseCatalog = exerciseCatalog,
        lastLoggedAtByExerciseId = lastLoggedAtByExerciseId,
        hints = hints,
        preferences = preferences,
        slots = slots,
        coachPrefs = coachPrefs,
        unit = unit,
        window = window,
        nowMs = nowMs,
        time = JvmTime,
        includeWeekPlan = includeWeekPlan,
    )
    return input.copy(zoneId = zone.id)
}

fun CoachInputs(
    basis: CoachBasis,
    history: List<WorkoutSession>,
    routines: List<Routine>,
    hints: List<ProgressionHint>,
    exerciseCatalog: Map<String, Exercise>,
    preferences: CoachPreferences = CoachPreferences.DEFAULT,
    unit: WeightUnit = WeightUnit.KG,
    nowMs: Long,
    zone: ZoneId,
): CoachInputs {
    val inputs = CoachInputs(
        basis = basis,
        history = history,
        routines = routines,
        hints = hints,
        exerciseCatalog = exerciseCatalog,
        preferences = preferences,
        unit = unit,
        nowMs = nowMs,
        time = JvmTime,
    )
    return inputs.copy(zoneId = zone.id)
}
