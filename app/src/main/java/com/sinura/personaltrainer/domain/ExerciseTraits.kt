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

    /**
     * How the kit reads on a chip or a row tag.
     *
     * Title case rather than the enum name, because SMITH on a filter chip is a surname and
     * "Smith" is a rack. The declaration order is the chip order, and it is deliberate: it runs
     * free weight, cable, machine, then the rest, which is roughly how a gym floor is laid out.
     */
    val label: String
        get() = when (this) {
            BARBELL -> "Barbell"
            DUMBBELL -> "Dumbbell"
            CABLE -> "Cable"
            MACHINE -> "Machine"
            SMITH -> "Smith"
            KETTLEBELL -> "Kettlebell"
            BAND -> "Band"
            BODYWEIGHT -> "Bodyweight"
            OTHER -> "Other"
        }

    companion object {
        /** DB junk must never crash a mapper: anything unrecognized reads as [OTHER]. */
        fun fromStorage(raw: String?): EquipmentType =
            entries.firstOrNull { it.name == raw } ?: OTHER

        /**
         * Reads a value a different lineage of this app wrote, which stored lowercase keys
         * ("barbell") where this one stores the enum name ("BARBELL").
         *
         * Returns null rather than [OTHER] for anything it does not recognize, which is the
         * whole point: [BackupValidator] refuses a document whose equipment it cannot place,
         * and that check is worth keeping. Silently defaulting here would turn a corrupt file
         * into an accepted one. Recognized legacy spellings are canonicalized; genuine junk is
         * handed on unchanged so the validator can name it.
         */
        fun fromLegacyStorage(raw: String?): EquipmentType? {
            val key = raw?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.name == key }
        }
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

        /**
         * The [EquipmentType.fromLegacyStorage] counterpart, and the harder of the two,
         * because load type is not only spelled differently in the other lineage — one value
         * is named differently.
         *
         * Case-folding alone would leave `weighted_bodyweight` unmatched, and an unknown load
         * type falling back to [EXTERNAL] is not a cosmetic error: [SetLogRules] requires a
         * non-zero weight for EXTERNAL, so every belt-less set the owner logged on a weighted
         * dip would become un-loggable. Hence an explicit rename rather than a fold.
         */
        fun fromLegacyStorage(raw: String?): LoadType? {
            val key = raw?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.name == key }
                ?: when (key) {
                    // The other lineage's name for a dip or pull-up with a belt.
                    "WEIGHTED_BODYWEIGHT" -> BODYWEIGHT_PLUS
                    else -> null
                }
        }
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
