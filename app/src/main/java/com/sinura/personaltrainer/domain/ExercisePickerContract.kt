package com.sinura.personaltrainer.domain

/**
 * Cohesive picker modes (P9.4 / FND-035). The sheet used to take eleven
 * optional parameters that encoded three jobs. The job is the mode.
 */
enum class ExercisePickerMode {
    /** Mid-session add: one tap, one lift. */
    SINGLE_ADD,
    /** Same movement, different kit. */
    SWAP,
    /**
     * Routine / custom-week multi-add. Every tap is written where it lands, so the
     * footer button closes the sheet rather than committing anything.
     */
    MULTI_ADD,
}

/**
 * [selectedOrder] is the session as it stands — in multi-add it is what the routine or
 * the day already holds, so the numbered rows survive the sheet being closed.
 */
data class ExercisePickerState(
    val query: String,
    val results: List<Exercise>,
    val title: String,
    val mode: ExercisePickerMode,
    val suggestion: Exercise? = null,
    val suggestionReason: String? = null,
    val siblings: List<Exercise> = emptyList(),
    val selectedOrder: List<String> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
    val error: String? = null,
) {
    val multiSelect: Boolean get() = mode == ExercisePickerMode.MULTI_ADD
    val showSiblings: Boolean get() = mode == ExercisePickerMode.SWAP && siblings.isNotEmpty()
    val selectedIds: Set<String> get() = LiftCart.sanitize(selectedOrder).toSet()
    val cart: List<Exercise>
        get() = LiftCart.resolve(
            selectedOrder,
            LiftCart.mergeSources(
                catalog,
                results + siblings + listOfNotNull(suggestion),
            ),
        )
}

sealed class ExercisePickerEvent {
    data class QueryChanged(val query: String) : ExercisePickerEvent()
    data class Selected(val exercise: Exercise) : ExercisePickerEvent()
    data class Created(val name: String, val muscleGroup: String) : ExercisePickerEvent()
    /** A tap in [ExercisePickerMode.MULTI_ADD]: adds the lift, or takes it back out. */
    data class Toggled(val exercise: Exercise) : ExercisePickerEvent()
    data object Dismissed : ExercisePickerEvent()
    data object ErrorDismissed : ExercisePickerEvent()
}
