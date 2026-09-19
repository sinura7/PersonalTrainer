package com.sinura.personaltrainer.domain

/**
 * Second Extra question: what is actually here for this session.
 * Labels: Bodyweight (none) / Free weights / Machines / Mixed.
 *
 * Settings kit is a guess (home vs hotel). The picker still asks, and
 * the answer only filters Extra packs — it does not rewrite Settings.
 */
enum class ExtraEquipment(val label: String, val caption: String) {
    NONE("Bodyweight (none)", "Bodyweight and the floor."),
    FREE_WEIGHTS("Free weights", "Dumbbells, kettlebells, a bar."),
    MACHINES("Machines", "Stacks, cables, a hip machine."),
    MIXED("Mixed", "Free weights and machines."),
    ;

    companion object {
        const val PICK = "What equipment is here?"
        const val USUAL = "Usual kit. Change it for this session."

        /** Catalog types Extra treats as a gym stack. Floor work must not include these. */
        val GYM_STACK: Set<EquipmentType> = setOf(
            EquipmentType.MACHINE,
            EquipmentType.CABLE,
            EquipmentType.SMITH,
            EquipmentType.HYPER_PRO,
        )

        val FREE: Set<EquipmentType> = setOf(
            EquipmentType.BARBELL,
            EquipmentType.DUMBBELL,
            EquipmentType.KETTLEBELL,
        )

        val FLOOR: Set<EquipmentType> = setOf(
            EquipmentType.BODYWEIGHT,
            EquipmentType.BAND,
            EquipmentType.OTHER,
        )

        /**
         * Pre-select from Settings. Empty kit is a full gym (same as
         * [CoachPreferences]), so Mixed — not "owns nothing".
         */
        fun fromPreferences(availableEquipment: Set<String>): ExtraEquipment {
            if (availableEquipment.isEmpty()) return MIXED
            val types = availableEquipment.map { EquipmentType.fromStorage(it) }.toSet()
            val hasFree = types.any { it in FREE }
            val hasMachines = types.any { it in GYM_STACK }
            return when {
                !hasFree && !hasMachines && types.all { it in FLOOR } -> NONE
                hasFree && !hasMachines -> FREE_WEIGHTS
                hasMachines && !hasFree -> MACHINES
                else -> MIXED
            }
        }

        fun fromStorage(raw: String?): ExtraEquipment =
            entries.firstOrNull { it.name == raw } ?: MIXED
    }
}
