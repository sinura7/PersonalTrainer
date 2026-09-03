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
    /** Routine / custom-week multi-add with a confirm. */
    MULTI_ADD,
}

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
    data class Toggled(val exercise: Exercise) : ExercisePickerEvent()
    data object Confirmed : ExercisePickerEvent()
    data object Dismissed : ExercisePickerEvent()
    data object ErrorDismissed : ExercisePickerEvent()
}
