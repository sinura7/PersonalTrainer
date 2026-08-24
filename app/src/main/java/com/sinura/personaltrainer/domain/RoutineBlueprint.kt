package com.sinura.personaltrainer.domain


/** One lift in a generated routine, already sized. */
data class BlueprintLift(
    val exerciseId: String,
    val name: String,
    val equipment: EquipmentType,
    val targets: TargetDefaults,
)

/** One generated routine — a named session shape, which may be used on more than one day. */
data class BlueprintRoutine(
    val key: String,
    val name: String,
    val focusKind: SessionFocusKind,
    val lifts: List<BlueprintLift>,
)

/** One day of the proposed week. [routineKey] is null on a rest day. */
data class BlueprintDay(
    val dayOfWeek: Weekday,
    val routineKey: String?,
) {
    val isRest: Boolean get() = routineKey == null
}

/**
 * The whole proposal: the routines to create, and the week to pin them into.
 *
 * Deliberately inert. Nothing here has been written to the database — this is what the preview
 * screen renders and what "Use this plan" then applies, so the lifter sees the actual lifts
 * before anything is created. A generator that wrote as it went would leave half a program
 * behind when someone backed out.
 */
data class PlanBlueprint(
    val splitStyle: SplitStyle,
    val routines: List<BlueprintRoutine>,
    val days: List<BlueprintDay>,
) {
    fun routineFor(day: BlueprintDay): BlueprintRoutine? =
        day.routineKey?.let { key -> routines.firstOrNull { it.key == key } }

    val trainingDayCount: Int get() = days.count { !it.isRest }
    val liftCount: Int get() = routines.sumOf { it.lifts.size }
}
