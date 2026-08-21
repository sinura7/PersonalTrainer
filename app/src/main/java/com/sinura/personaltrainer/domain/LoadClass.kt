package com.sinura.personaltrainer.domain

/**
 * How a lift is measured — which is not the same question as what equipment it uses.
 *
 * A bench press is measured in kilograms because kilograms are what changes when you get
 * stronger at it. A push-up is measured in reps, for exactly the same reason. The app used to
 * measure everything in kilograms and, for lifts that have no kilograms, invent some: every
 * bodyweight set was scored at a flat 40 kg stand-in, so a set of ten push-ups and a set of ten
 * pull-ups were worth the same 400 kg, and both were worth as much as a modest barbell row.
 * That number was not wrong so much as meaningless — it answered a question nobody asked.
 *
 * So bodyweight lifts are their own class. Reps are the measure. Tonnage is reported for the
 * load that was actually external — a 20 kg vest on a pull-up is 20 kg that genuinely moved —
 * and never for the body doing the moving, because the app does not need to price a body to
 * count what it did.
 *
 * This changes no coaching. The muscle map and the coach have always banded on weighted working
 * *sets* ([HeatBand.fromWeeklySets]), never on tonnage, so a bodyweight session lit up the map
 * correctly before this existed and lights it up identically now. What changes is the number
 * the app shows a person, which is the only place the stand-in was ever visible.
 */
enum class LoadClass {
    /** A bar, a stack, a dumbbell. Kilograms are the measure. */
    LOADED,

    /** Your own body, and nothing else. Reps are the measure. */
    BODYWEIGHT,

    /** Your body plus what you strapped on — a vest, a dip belt. Reps, qualified by the load. */
    BODYWEIGHT_ADDED,

    /** Your body minus a machine's help. Reps, qualified by how much help. */
    BODYWEIGHT_ASSISTED,
    ;

    /** True when a rep count, not a kilogram total, is what this lift's progress is made of. */
    val repsAreTheMeasure: Boolean get() = this != LOADED

    /**
     * What the weight field on a set means for this class.
     *
     * The same column, `SetLog.weightKg`, carries three different things depending on the lift,
     * and every surface that shows or asks for it has to say which. A pull-up logged as "20 kg"
     * did not lift twenty kilograms; it lifted a person and twenty kilograms.
     */
    val weightMeaning: WeightMeaning
        get() = when (this) {
            LOADED -> WeightMeaning.LIFTED
            BODYWEIGHT -> WeightMeaning.NONE
            BODYWEIGHT_ADDED -> WeightMeaning.ADDED
            BODYWEIGHT_ASSISTED -> WeightMeaning.ASSISTANCE
        }

    companion object {
        /**
         * A null [LoadType] means a lift this build cannot classify — a custom the owner typed
         * in, or a row from a newer backup. It is treated as loaded, which is the safer wrong
         * answer: a loaded lift with no weight logged reports zero tonnage and looks empty,
         * whereas a barbell lift silently reclassified as bodyweight would drop its kilograms
         * out of every total the owner has.
         */
        fun of(loadType: LoadType?): LoadClass = when (loadType) {
            LoadType.EXTERNAL, LoadType.STACK, null -> LOADED
            LoadType.BODYWEIGHT -> BODYWEIGHT
            LoadType.BODYWEIGHT_PLUS -> BODYWEIGHT_ADDED
            LoadType.ASSISTED -> BODYWEIGHT_ASSISTED
        }
    }
}

/** What a set's stored `weightKg` is a measurement of. */
enum class WeightMeaning {
    /** The weight on the bar. */
    LIFTED,

    /** Nothing — this lift has no weight to record, and asking for one invites a wrong answer. */
    NONE,

    /** Weight added to the lifter: a vest, a dip belt, a dumbbell between the feet. */
    ADDED,

    /** Weight the machine took off the lifter. More assistance is an easier set, not a harder one. */
    ASSISTANCE,
    ;

    /** The field label where the number is typed. */
    val fieldLabel: String
        get() = when (this) {
            LIFTED -> "Weight"
            NONE -> "Weight"
            ADDED -> "Added"
            ASSISTANCE -> "Assist"
        }
}
