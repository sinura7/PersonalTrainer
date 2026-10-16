package com.sinura.personaltrainer.domain

/**
 * Display unit for cardio distance. Stored distance stays kilometres.
 *
 * When nothing is stored, miles follow pounds and kilometres follow kilograms.
 * A stored choice stays when the weight unit changes.
 */
enum class DistanceUnit(val suffix: String, val storageKey: String, val displayName: String) {
    KM("km", "km", "Kilometres"),
    MI("mi", "mi", "Miles"),
    ;

    companion object {
        const val METERS_PER_MILE = 1_609.344

        const val CHOICE_CAPTION =
            "Until you choose, miles follow pounds and kilometres follow kilograms. " +
                "A choice stays when the weight unit changes. " +
                "Stored weight stays kilograms. Stored distance stays kilometres."

        fun fromWeight(unit: WeightUnit): DistanceUnit = when (unit) {
            WeightUnit.KG -> KM
            WeightUnit.LBS -> MI
        }

        /** Null when the backup or this phone has no distance choice. */
        fun fromStorage(raw: String?): DistanceUnit? =
            entries.firstOrNull { it.storageKey.equals(raw, ignoreCase = true) }

        fun resolve(stored: DistanceUnit?, weight: WeightUnit): DistanceUnit =
            stored ?: fromWeight(weight)
    }
}
