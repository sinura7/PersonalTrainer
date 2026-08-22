package com.sinura.personaltrainer.domain

import java.time.DayOfWeek

/**
 * How long the lifter has been at this.
 *
 * The one thing about a person that should change their first program, and the only such
 * question worth asking. It sets how much work a session carries and how fast the app expects
 * load to climb — not which lifts they get, because a beginner and a veteran both squat.
 */
enum class TrainingAge(val displayName: String, val blurb: String) {
    NEW("New to lifting", "Or coming back after a long break"),
    RETURNING("On and off", "You know the lifts, you're not consistent yet"),
    EXPERIENCED("Training regularly", "You've been lifting steadily"),
    ;

    /** Lifts per session. More experience carries more work before quality falls off. */
    val liftsPerSession: Int
        get() = when (this) {
            NEW -> 4
            RETURNING -> 5
            EXPERIENCED -> 6
        }

    companion object {
        fun fromStorage(raw: String?): TrainingAge = entries.firstOrNull { it.name == raw } ?: NEW
    }
}

/**
 * What the lifter can actually get their hands on.
 *
 * Asked instead of an equipment checklist because a checklist of nine types is a form, and the
 * three real answers cover almost everyone. It maps to [CoachPreferences.availableEquipment],
 * which already filters the catalog everywhere — so answering it once quietly improves the
 * coach, the picker and the library at the same time.
 */
enum class TrainingPlace(val displayName: String, val blurb: String) {
    FULL_GYM("A full gym", "Barbells, machines, cables"),
    HOME_DUMBBELLS("Dumbbells at home", "Adjustable or a rack, plus bands"),
    BODYWEIGHT_ONLY("Bodyweight only", "No equipment at all"),
    ;

    val equipment: Set<EquipmentType>
        get() = when (this) {
            // Empty would ALSO mean "no filtering" to CoachPreferences, but stating the full
            // set keeps this enum readable on its own terms.
            FULL_GYM -> EquipmentType.entries.toSet()
            HOME_DUMBBELLS -> setOf(
                EquipmentType.DUMBBELL,
                EquipmentType.KETTLEBELL,
                EquipmentType.BAND,
                EquipmentType.BODYWEIGHT,
                EquipmentType.OTHER,
            )
            BODYWEIGHT_ONLY -> setOf(EquipmentType.BODYWEIGHT, EquipmentType.OTHER)
        }

    companion object {
        fun fromStorage(raw: String?): TrainingPlace = entries.firstOrNull { it.name == raw } ?: FULL_GYM
    }
}

/**
 * Everything the guided setup asks, and nothing it does not.
 *
 * Seven fields. Each one changes the plan that comes out the other side; a question whose answer
 * changes nothing is a screen the user pays for and gets nothing back. Height and body type
 * were both proposed and both cut for exactly that reason — nothing in a strength app consumes
 * a height, and somatotype does not predict how anyone responds to training.
 *
 * Note what is NOT here: the split. Whether someone runs push/pull/legs or upper/lower is the
 * app's job to work out from the three answers that determine it, and asking a new lifter to
 * choose between two things they have no basis to compare is the exact opposite of guided.
 */
data class OnboardingAnswers(
    val trainingAge: TrainingAge = TrainingAge.NEW,
    val daysPerWeek: Int = SchedulePreferences.DEFAULT_DAYS,
    /** Empty means "no preference" — the planner spaces them out instead. */
    val preferredDays: Set<DayOfWeek> = emptySet(),
    val place: TrainingPlace = TrainingPlace.FULL_GYM,
    val goal: TrainingGoal = TrainingGoal.GENERAL,
    val emphasis: TrainingEmphasis = TrainingEmphasis.BALANCED,
    /** Null when skipped. Replaces the flat stand-in in bodyweight-set volume. */
    val bodyweightKg: Double? = null,
) {
    fun sanitized(): OnboardingAnswers = copy(
        daysPerWeek = daysPerWeek.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS),
        bodyweightKg = bodyweightKg?.takeIf { it.isFinite() && it in MIN_BODYWEIGHT_KG..MAX_BODYWEIGHT_KG },
    )

    fun coachPreferences(): CoachPreferences = CoachPreferences(
        goal = goal,
        // A full gym filters nothing, and storing all nine types would be a list that has to be
        // updated every time the enum grows. Empty already means "no filtering".
        availableEquipment = if (place == TrainingPlace.FULL_GYM) {
            emptySet()
        } else {
            place.equipment.map { it.name }.toSet()
        },
        emphasis = emphasis,
    )

    fun schedulePreferences(weekStart: DayOfWeek = DayOfWeek.MONDAY): SchedulePreferences =
        SchedulePreferences(
            trainingDaysPerWeek = daysPerWeek,
            splitStyle = SplitDerivation.forAnswers(this),
            weekStart = weekStart,
        )

    companion object {
        /** Wide enough for any adult, narrow enough to catch a slipped decimal point. */
        const val MIN_BODYWEIGHT_KG = 30.0
        const val MAX_BODYWEIGHT_KG = 300.0

        /**
         * Rebuild the answers from what is already on the phone.
         *
         * Replay of an empty week must not re-ask the questionnaire. Days, goal, emphasis and
         * bodyweight already live in preferences; age, preferred days and place join them in
         * Job 3. Callers that only have equipment (an older install, a v1 backup) pass
         * [inferPlace] for [place].
         */
        fun fromStored(
            trainingAge: TrainingAge,
            daysPerWeek: Int,
            preferredDays: Set<DayOfWeek>,
            place: TrainingPlace,
            goal: TrainingGoal,
            emphasis: TrainingEmphasis,
            bodyweightKg: Double?,
        ): OnboardingAnswers = OnboardingAnswers(
            trainingAge = trainingAge,
            daysPerWeek = daysPerWeek,
            preferredDays = preferredDays,
            place = place,
            goal = goal,
            emphasis = emphasis,
            bodyweightKg = bodyweightKg,
        ).sanitized()

        /**
         * Best-effort place from the kit filter. Empty means "no filtering" in
         * [CoachPreferences], which is how a full gym is stored — not "owns nothing".
         */
        fun inferPlace(availableEquipment: Set<String>): TrainingPlace {
            if (availableEquipment.isEmpty()) return TrainingPlace.FULL_GYM
            val types = availableEquipment.mapNotNull { raw ->
                EquipmentType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            }.toSet()
            if (types.isEmpty()) return TrainingPlace.FULL_GYM
            val bodyweight = TrainingPlace.BODYWEIGHT_ONLY.equipment
            val home = TrainingPlace.HOME_DUMBBELLS.equipment
            return when {
                types.all { it in bodyweight } -> TrainingPlace.BODYWEIGHT_ONLY
                types.all { it in home } -> TrainingPlace.HOME_DUMBBELLS
                else -> TrainingPlace.FULL_GYM
            }
        }
    }
}
