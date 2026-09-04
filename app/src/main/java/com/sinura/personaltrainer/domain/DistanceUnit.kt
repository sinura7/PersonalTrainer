package com.sinura.personaltrainer.domain

/**
 * Display unit for cardio distance. Stored metres do not change.
 *
 * Pounds users were asked for kilometres because the composer and live
 * cardio field hard-coded km. Distance follows the weight unit so the
 * Display chips decide both.
 */
enum class DistanceUnit(val suffix: String) {
    KM("km"),
    MI("mi"),
    ;

    companion object {
        const val METERS_PER_MILE = 1_609.344

        fun fromWeight(unit: WeightUnit): DistanceUnit = when (unit) {
            WeightUnit.KG -> KM
            WeightUnit.LBS -> MI
        }
    }
}
