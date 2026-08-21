package com.sinura.personaltrainer.domain

/**
 * How much weight to add, in one place, for everything that adds weight.
 *
 * There were three answers to this question and they disagreed. `ProgressionCalculator` moved
 * in 2.5 kg. The weight stepper moved in 2.5 kg or 5 lbs depending on the display unit. The
 * coach quoted the calculator's constant through the unit formatter. So a lifter in pounds was
 * told to add "+5.5 lbs" — which is 2.5 kg converted, a number no gym has plates for — while
 * the stepper beside it moved in fives. Neither was wrong on its own terms; there were just
 * two systems.
 *
 * The fix is to decide the step in the unit the lifter thinks in, and convert to kilograms for
 * storage rather than the other way round. Someone using pounds adds a 2.5 lb plate to each
 * side and gets exactly "+5 lbs"; the 2.26796 kg that lands in the database is an
 * implementation detail they never see.
 *
 * [LoadType.BODYWEIGHT] returns null everywhere. There is no weight to add to a push-up, and a
 * "+2.5 kg" suggestion on one is not a small inaccuracy — it is advice that cannot be followed.
 * Callers must render rep progression instead, which is why the null is not defaulted away.
 */
object IncrementTable {
    /** One 2.5 kg plate per side, the smallest jump most racks are stocked for. */
    const val STEP_KG = 2.5

    /** One 2.5 lb plate per side. */
    const val STEP_LBS = 5.0

    /** [STEP_LBS] in kilograms, so a pound user's history stores what they actually lifted. */
    const val STEP_LBS_IN_KG = STEP_LBS / WeightConverter.LBS_PER_KG

    /** The number the lifter sees, in their own unit. Null when there is nothing to add. */
    fun displayStep(loadType: LoadType, unit: WeightUnit): Double? = when (loadType) {
        LoadType.BODYWEIGHT -> null
        LoadType.EXTERNAL, LoadType.STACK, LoadType.BODYWEIGHT_PLUS, LoadType.ASSISTED ->
            when (unit) {
                WeightUnit.KG -> STEP_KG
                WeightUnit.LBS -> STEP_LBS
            }
    }

    /** The same step in kilograms, for the arithmetic and for storage. */
    fun stepKg(loadType: LoadType, unit: WeightUnit): Double? = when (loadType) {
        LoadType.BODYWEIGHT -> null
        LoadType.EXTERNAL, LoadType.STACK, LoadType.BODYWEIGHT_PLUS, LoadType.ASSISTED ->
            when (unit) {
                WeightUnit.KG -> STEP_KG
                WeightUnit.LBS -> STEP_LBS_IN_KG
            }
    }

    /** "2.5 kg" or "5 lbs", for coach copy. Null for bodyweight, where the copy differs. */
    fun stepLabel(loadType: LoadType, unit: WeightUnit): String? =
        displayStep(loadType, unit)?.let { "${WeightConverter.formatDisplayNumber(it)} ${unit.suffix}" }

    /**
     * What a lifter with nothing to add should be told instead.
     *
     * Bodyweight progression is reps, then tempo, then a harder variation. The app can honestly
     * say the first of those.
     */
    const val REP_PROGRESSION_COPY = "Hit target reps. Add a rep next session."
}
