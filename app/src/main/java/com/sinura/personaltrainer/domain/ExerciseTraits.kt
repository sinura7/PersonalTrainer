package com.sinura.personaltrainer.domain

/**
 * What you hold, and what the weight is.
 *
 * These two are separate on purpose. Equipment is what the lift is performed WITH — the thing
 * you pick from a rack — and it is what the library groups and filters on. Load type is how the
 * resistance behaves, and it is what a progression suggestion has to respect: a machine's stack
 * moves in the increments the maker chose, a barbell moves in whatever plates exist, and a
 * pull-up does not move at all until you hang something off yourself. A single "equipment"
 * field cannot answer both questions, which is how "+2.5 kg" ends up being suggested for a
 * bodyweight plank.
 */
enum class EquipmentType {
    BARBELL,
    DUMBBELL,
    CABLE,
    MACHINE,
    SMITH,
    KETTLEBELL,
    BAND,
    BODYWEIGHT,
    OTHER,
    ;

    companion object {
        /** DB junk must never crash a mapper: anything unrecognized reads as [OTHER]. */
        fun fromStorage(raw: String?): EquipmentType =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class LoadType {
    /** Free weight you add to: barbells, dumbbells, plate-loaded machines. */
    EXTERNAL,

    /** A selectorized stack, moving in the pin increments the machine was built with. */
    STACK,

    /** Your own bodyweight, and nothing else. */
    BODYWEIGHT,

    /** Bodyweight you can add load to — weighted pull-ups, dips with a belt. */
    BODYWEIGHT_PLUS,

    /** Bodyweight with assistance subtracted; heavier assistance means an easier set. */
    ASSISTED,
    ;

    companion object {
        fun fromStorage(raw: String?): LoadType =
            entries.firstOrNull { it.name == raw } ?: EXTERNAL
    }
}

/**
 * One muscle's share of a lift.
 *
 * [muscleKey] is exactly `CanonicalMuscle.name.lowercase()` — the spelling is part of the
 * contract, not a formatting preference, because it is what the junction table stores and what
 * the invariant test asserts by exact match. "quads" normalizes fine through the alias index
 * and would slip past a weaker check; it is still not a legal key.
 */
data class MuscleCredit(val muscleKey: String, val weight: Double)
