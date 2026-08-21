package com.sinura.personaltrainer.domain

/**
 * What a set was worth, in the units that lift is actually measured in.
 *
 * Two numbers, not one, because there is no honest single number. A session of squats and
 * pull-ups did four thousand kilograms *and* ninety-six bodyweight reps, and flattening those
 * into one figure requires deciding what a person weighs in kilograms of training — which is
 * the invention this type exists to remove.
 *
 * Both are additive, so a session, a week or a month is the sum of its sets.
 */
data class SetWork(
    /** Kilograms that were genuinely external: the bar, the stack, the vest. */
    val volumeKg: Double,
    /** Reps of lifts whose measure is reps. Zero for loaded lifts, which are measured in kg. */
    val bodyweightReps: Int,
) {
    operator fun plus(other: SetWork): SetWork = SetWork(
        volumeKg = volumeKg + other.volumeKg,
        bodyweightReps = bodyweightReps + other.bodyweightReps,
    )

    val isEmpty: Boolean get() = volumeKg <= 0.0 && bodyweightReps <= 0

    companion object {
        val NONE = SetWork(volumeKg = 0.0, bodyweightReps = 0)

        /**
         * @param weightKg the set's stored weight, whose meaning depends on [loadClass] —
         * see [LoadClass.weightMeaning].
         */
        fun of(weightKg: Double, reps: Int, loadClass: LoadClass): SetWork {
            val safeReps = reps.coerceAtLeast(0)
            val safeWeight = weightKg.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            return when (loadClass) {
                LoadClass.LOADED -> SetWork(
                    volumeKg = safeWeight * safeReps,
                    bodyweightReps = 0,
                )
                LoadClass.BODYWEIGHT -> SetWork(
                    volumeKg = 0.0,
                    bodyweightReps = safeReps,
                )
                // The vest counts. It is external weight that genuinely moved, and it is the
                // part of a weighted pull-up that a kilogram total can honestly describe.
                LoadClass.BODYWEIGHT_ADDED -> SetWork(
                    volumeKg = safeWeight * safeReps,
                    bodyweightReps = safeReps,
                )
                // Assistance is weight removed, so it contributes no tonnage — counting it
                // would credit the lifter with the machine's help. The reps still count: an
                // assisted pull-up is a pull-up you did.
                LoadClass.BODYWEIGHT_ASSISTED -> SetWork(
                    volumeKg = 0.0,
                    bodyweightReps = safeReps,
                )
            }
        }

        fun sum(works: Iterable<SetWork>): SetWork = works.fold(NONE) { total, work -> total + work }
    }
}
