package com.sinura.personaltrainer.domain

/**
 * What the lifter is training for.
 *
 * The coach's rules do not change with the goal — a neglected muscle is neglected whatever you
 * are chasing — but their *order* does. Someone training for strength wants to hear about the
 * lift that is ready to go up before they hear about balance; someone training for size wants
 * the reverse. The goal is a ranking modifier for that reason, not a different rule set: a
 * rule that only fires for one goal is a rule that is wrong for the others.
 */
enum class TrainingGoal(val displayName: String, val blurb: String) {
    STRENGTH("Strength", "Progression and load first"),
    HYPERTROPHY("Muscle", "Volume and balance first"),
    GENERAL("General", "No emphasis"),
    ;

    companion object {
        fun fromStorage(raw: String?): TrainingGoal =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

/**
 * @param availableEquipment storage names of [EquipmentType]. **Empty means no filtering** —
 * not "no equipment". A first-run default of "you own nothing" would silently stop the coach
 * naming any lift at all, and the user would have no way to know why.
 */
data class CoachPreferences(
    val goal: TrainingGoal = TrainingGoal.GENERAL,
    val availableEquipment: Set<String> = emptySet(),
) {
    fun allows(equipment: EquipmentType): Boolean =
        availableEquipment.isEmpty() || equipment.name in availableEquipment

    companion object {
        val DEFAULT = CoachPreferences()
    }
}
