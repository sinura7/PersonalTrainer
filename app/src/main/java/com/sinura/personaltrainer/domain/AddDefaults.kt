package com.sinura.personaltrainer.domain

/**
 * Where a lift sits in its session.
 *
 * [AddDefaults] can see how a lift is loaded and how many muscles it credits, and from those
 * two facts a barbell back squat and a Bulgarian split squat are the same lift: both external,
 * both compound, both therefore 3 x 5 with two and a half minutes' rest. One of those is a
 * main lift and the other is an accessory, and no property of the exercise says which — it is
 * a property of the SESSION, and only whoever built the session knows it.
 *
 * So it is passed in. A hand-added lift is [PRIMARY], which is exactly the behaviour that
 * shipped before this existed; the generator marks its opening lifts primary and everything
 * after them accessory, which is what stops a generated leg day asking for five heavy singles-
 * range movements in a row.
 */
enum class LiftRole {
    /** The lift the session is built around. Heavier, longer rest. */
    PRIMARY,

    /** Everything after it. Lighter, more reps, shorter rest. */
    ACCESSORY,
}

/** Starting targets for a lift being added to a routine or a live session. */
data class TargetDefaults(
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val seconds: Int? = null,
    val secondsMax: Int? = null,
) {
    /** The line the add-to-routine sheet shows before anything is written. */
    fun previewLine(): String {
        val work = seconds?.let { HoldWork.formatRange(it, secondsMax) } ?: reps.toString()
        return "$sets × $work · ${RestTimer.formatClock(restSeconds)}"
    }
}

/**
 * What a lift's targets should start at, given what kind of lift it is.
 *
 * Every add path in the app used the same literal 3 × 5 with 90 seconds' rest. That is a
 * defensible number for a barbell squat and a wrong one for everything else: 3 × 5 on a cable
 * lateral raise is not a set scheme anybody runs, and 90 seconds between heavy deadlifts is not
 * enough rest. The user could always fix it, which is exactly the problem — the app was making
 * them correct a guess it had no reason to make badly.
 *
 * Four axes decide it. [LoadType] says how the resistance behaves. Compound-ness
 * says how much of you is working. [LiftRole] says where the lift sits in the
 * session. [TrainingGoal] shifts the landing reps and rest — Muscle up and shorter,
 * Strength the reverse — without adding or removing a rule, and only for a row
 * being added now. A goal changed on a Tuesday never rewrites a routine the user
 * already wrote. Nothing about the goal enters [SetMicroRecCalculator].
 *
 * These are opinions, not physiology. They are here so the app has one opinion instead of one
 * number pretending not to be an opinion.
 */
object AddDefaults {
    /** A lift is compound when at least one secondary muscle takes half a set's credit. */
    fun isCompound(exercise: Exercise): Boolean =
        exercise.muscles.drop(1).any { it.weight >= COMPOUND_SECONDARY_WEIGHT }

    fun forExercise(exercise: Exercise, role: LiftRole = LiftRole.PRIMARY, goal: TrainingGoal = TrainingGoal.GENERAL): TargetDefaults {
        if (HoldWork.isHold(exercise)) {
            return TargetDefaults(
                sets = 2,
                reps = HoldWork.HOLD_REPS_PLACEHOLDER,
                restSeconds = WorkoutPasteRest.ACCESSORY_SECONDS,
                seconds = HoldWork.DEFAULT_SECONDS,
            )
        }
        // Empty-hands dumbbell lunges / step-ups log as BODYWEIGHT_PLUS so 0 kg
        // is a complete set. Their session dose stays the EXTERNAL compound
        // row they had before that recategorization — 3 × 5 / 150 as a
        // primary, 3 × 8 / 90 as an accessory — so generated Lower days do not
        // quietly move from 8 to 9 reps.
        val loadForDose =
            if (UnloadedLoad.sizesLikeExternalCompound(exercise)) LoadType.EXTERNAL
            else exercise.loadType
        return forExercise(loadForDose, isCompound(exercise), role, goal)
    }

    /**
     * What the add-to-routine sheet says this lift will land as.
     *
     * The old sentence named no numbers. "3 × 5" as a universal line was a lie after
     * per-lift defaults shipped. This is the actual row [forExercise] will write.
     */
    fun landingCopy(exercise: Exercise, goal: TrainingGoal = TrainingGoal.GENERAL): String =
        "Lands at ${forExercise(exercise, goal = goal).previewLine()} — editable on the routine."

    fun forExercise(
        loadType: LoadType?,
        isCompound: Boolean,
        role: LiftRole = LiftRole.PRIMARY,
        goal: TrainingGoal = TrainingGoal.GENERAL,
    ): TargetDefaults {
        val primary = when (loadType) {
            LoadType.EXTERNAL ->
                if (isCompound) TargetDefaults(3, 5, 150) else TargetDefaults(3, 10, 90)
            LoadType.STACK ->
                if (isCompound) TargetDefaults(3, 10, 90) else TargetDefaults(3, 12, 60)
            // Added load is the point of these, so they sit in the low-rep range whether or not
            // a second muscle is credited: a weighted pull-up is trained like a loaded lift.
            LoadType.BODYWEIGHT_PLUS -> TargetDefaults(3, 6, 120)
            LoadType.BODYWEIGHT ->
                if (isCompound) TargetDefaults(3, 8, 90) else TargetDefaults(3, 12, 60)
            LoadType.ASSISTED -> TargetDefaults(3, 8, 90)
            // A custom the user typed in, or a row from a backup this build does not
            // understand. The isolation row is the safer wrong answer: too many reps at too
            // little rest is a bad set, while too few reps at too much rest is a wasted
            // afternoon.
            null -> FALLBACK
        }
        val byRole = if (role == LiftRole.PRIMARY) primary else accessory(primary)
        return applyGoal(byRole, goal)
    }

    /**
     * Goal moves the numbers the rules will later read. It does not add or
     * remove a rule. See ADR-025.
     */
    private fun applyGoal(defaults: TargetDefaults, goal: TrainingGoal): TargetDefaults =
        when (goal) {
            TrainingGoal.HYPERTROPHY -> defaults.copy(
                reps = (defaults.reps + GOAL_REP_STEP).coerceAtMost(GOAL_MAX_REPS),
                restSeconds = (defaults.restSeconds - GOAL_REST_SHIFT).coerceAtLeast(GOAL_MIN_REST),
            )
            TrainingGoal.STRENGTH -> defaults.copy(
                reps = (defaults.reps - GOAL_REP_STEP).coerceAtLeast(GOAL_MIN_REPS),
                restSeconds = (defaults.restSeconds + GOAL_REST_SHIFT).coerceAtMost(GOAL_MAX_REST),
            )
            TrainingGoal.ATHLETIC,
            TrainingGoal.RESILIENCE,
            TrainingGoal.GENERAL,
            -> defaults
        }

    /**
     * The same lift, done later in the session.
     *
     * Derived from the primary row rather than tabled separately, so there is still one table
     * to argue with. Reps go up by the same step the loaded rows are spaced at and rest comes
     * down, both floored — an accessory never asks for fewer reps or more rest than its primary
     * form, and nothing drops below the isolation row's minute.
     */
    private fun accessory(primary: TargetDefaults) = TargetDefaults(
        sets = primary.sets,
        reps = (primary.reps + ACCESSORY_REP_STEP).coerceAtMost(ACCESSORY_MAX_REPS),
        restSeconds = (primary.restSeconds - ACCESSORY_REST_CUT).coerceAtLeast(ACCESSORY_MIN_REST),
    )

    private const val COMPOUND_SECONDARY_WEIGHT = 0.5
    private val FALLBACK = TargetDefaults(3, 10, 90)

    private const val ACCESSORY_REP_STEP = 3
    private const val ACCESSORY_MAX_REPS = 12
    private const val ACCESSORY_REST_CUT = 60
    private const val ACCESSORY_MIN_REST = 60

    private const val GOAL_REP_STEP = 2
    private const val GOAL_MAX_REPS = 15
    private const val GOAL_MIN_REPS = 3
    private const val GOAL_REST_SHIFT = 30
    private const val GOAL_MIN_REST = 60
    private const val GOAL_MAX_REST = 210
}
