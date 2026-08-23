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
    NEW("New to lifting", "Short sessions. The bar climbs slowly."),
    RETURNING("On and off", "You know the lifts. We'll load a full session."),
    EXPERIENCED("Training regularly", "More work per session, faster jumps."),
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
 * Asked as places rather than an equipment checklist because a checklist of nine types is a
 * form. The three real answers cover almost everyone, and they mix: gym plus a living-room
 * pair of dumbbells is a real week. The union of selected places maps to
 * [CoachPreferences.availableEquipment], which already filters the catalog everywhere.
 */
enum class TrainingPlace(val displayName: String, val blurb: String, val shortLabel: String) {
    FULL_GYM("A full gym", "Barbells, machines, cables", "Gym"),
    HOME_DUMBBELLS("Dumbbells at home", "Adjustable or a rack, plus bands", "Home"),
    BODYWEIGHT_ONLY("Bodyweight only", "No equipment at all", "Bodyweight"),
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
        fun fromStorage(raw: String?): TrainingPlace = widest(parsePlaces(raw))

        /**
         * One or many places in the stored string.
         *
         * A single name is the old shape. Comma-separated names are a mixed kit. Unknown
         * tokens are dropped; an empty result is "never written", not "owns nothing".
         */
        fun parsePlaces(raw: String?): Set<TrainingPlace> {
            if (raw.isNullOrBlank()) return emptySet()
            return raw.split(',')
                .mapNotNull { token -> entries.firstOrNull { it.name == token.trim() } }
                .toSet()
        }

        fun formatPlaces(places: Set<TrainingPlace>): String =
            places.sortedBy { it.ordinal }.joinToString(",") { it.name }

        /** Gym swallows the rest: it already contains every equipment type. */
        fun widest(places: Set<TrainingPlace>): TrainingPlace = when {
            FULL_GYM in places -> FULL_GYM
            HOME_DUMBBELLS in places -> HOME_DUMBBELLS
            BODYWEIGHT_ONLY in places -> BODYWEIGHT_ONLY
            else -> FULL_GYM
        }

        fun label(places: Set<TrainingPlace>): String {
            val resolved = places.ifEmpty { setOf(FULL_GYM) }
            return resolved.sortedBy { it.ordinal }.joinToString(" + ") { it.shortLabel }
        }

        fun equipmentOf(places: Set<TrainingPlace>): Set<EquipmentType> {
            val resolved = places.ifEmpty { setOf(FULL_GYM) }
            if (FULL_GYM in resolved) return EquipmentType.entries.toSet()
            return resolved.flatMap { it.equipment }.toSet()
        }
    }
}

/**
 * Whole-number stops on the bodyweight wheel, in the unit the lifter is looking at.
 *
 * The old chips were ten-kilo jumps labelled as bare numbers, which is how an lbs lifter
 * could tap "80" and be stored at eighty kilos. The wheel speaks one unit at a time and
 * converts at the edge.
 */
object BodyweightSteps {
    const val DEFAULT_KG = 75.0

    fun displayValues(unit: WeightUnit): List<Int> {
        val min = WeightConverter.toDisplayValue(OnboardingAnswers.MIN_BODYWEIGHT_KG, unit).toInt()
        val max = WeightConverter.toDisplayValue(OnboardingAnswers.MAX_BODYWEIGHT_KG, unit).toInt()
        return (min..max).toList()
    }

    fun defaultDisplay(unit: WeightUnit): Int =
        WeightConverter.toDisplayValue(DEFAULT_KG, unit).toInt()

    fun displayOf(kg: Double, unit: WeightUnit): Int =
        WeightConverter.toDisplayValue(kg, unit).toInt()

    fun toKg(display: Int, unit: WeightUnit): Double =
        WeightConverter.toKg(display.toDouble(), unit)
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
    /**
     * The single-place field older call sites still write.
     *
     * [resolvedPlaces] is what the generator and the coach actually read: if [places] is
     * non-empty it wins, otherwise this stands alone. Sanitising writes both so they cannot
     * disagree after a round-trip.
     */
    val place: TrainingPlace = TrainingPlace.FULL_GYM,
    val places: Set<TrainingPlace> = emptySet(),
    val goal: TrainingGoal = TrainingGoal.GENERAL,
    val emphasis: TrainingEmphasis = TrainingEmphasis.BALANCED,
    /** Null when skipped. Replaces the flat stand-in in bodyweight-set volume. */
    val bodyweightKg: Double? = null,
) {
    fun resolvedPlaces(): Set<TrainingPlace> =
        (if (places.isNotEmpty()) places else setOf(place)).ifEmpty { setOf(TrainingPlace.FULL_GYM) }

    fun equipment(): Set<EquipmentType> = TrainingPlace.equipmentOf(resolvedPlaces())

    fun withToggledPlace(target: TrainingPlace): OnboardingAnswers {
        val current = resolvedPlaces()
        val next = if (target in current) {
            (current - target).ifEmpty { current }
        } else {
            current + target
        }
        return copy(places = next, place = TrainingPlace.widest(next))
    }

    fun sanitized(): OnboardingAnswers {
        val resolved = resolvedPlaces()
        return copy(
            daysPerWeek = daysPerWeek.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS),
            places = resolved,
            place = TrainingPlace.widest(resolved),
            bodyweightKg = bodyweightKg?.takeIf { it.isFinite() && it in MIN_BODYWEIGHT_KG..MAX_BODYWEIGHT_KG },
        )
    }

    fun coachPreferences(): CoachPreferences {
        val resolved = resolvedPlaces()
        return CoachPreferences(
            goal = goal,
            // A full gym filters nothing, and storing all nine types would be a list that has to be
            // updated every time the enum grows. Empty already means "no filtering".
            availableEquipment = if (TrainingPlace.FULL_GYM in resolved) {
                emptySet()
            } else {
                TrainingPlace.equipmentOf(resolved).map { it.name }.toSet()
            },
            emphasis = emphasis,
        )
    }

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
            places: Set<TrainingPlace> = emptySet(),
        ): OnboardingAnswers = OnboardingAnswers(
            trainingAge = trainingAge,
            daysPerWeek = daysPerWeek,
            preferredDays = preferredDays,
            place = place,
            places = places.ifEmpty { setOf(place) },
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
