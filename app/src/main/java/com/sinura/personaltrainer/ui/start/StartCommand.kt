package com.sinura.personaltrainer.ui.start

import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.ui.home.HomeViewModel

/** Every strength start the gate admits, with an optional gym until the user picks one. */
sealed interface StartCommand {
    val gymId: String?

    val originalOccurrenceId: String?
        get() = when (this) {
            is Occurrence -> occurrenceId
            is Discard -> requested.originalOccurrenceId
            else -> null
        }

    fun requiresGym(): Boolean = when (this) {
        is Cardio -> false
        else -> true
    }

    fun withGym(id: String): StartCommand = when (this) {
        is Free -> copy(gymId = id)
        is Routine -> copy(gymId = id)
        is Suggested -> copy(gymId = id)
        is Occurrence -> copy(gymId = id)
        is Aux -> copy(gymId = id)
        is Discard -> copy(requested = requested.withGym(id))
        is Cardio -> this
    }

    data class Free(override val gymId: String? = null) : StartCommand
    data class Routine(val routineId: String, override val gymId: String? = null) : StartCommand
    data class Cardio(val type: CardioType) : StartCommand {
        override val gymId: String? get() = null
    }
    data class Aux(val packId: String, override val gymId: String? = null) : StartCommand
    data class Suggested(val day: SuggestedTrainingDay, override val gymId: String? = null) : StartCommand
    data class Occurrence(
        val occurrenceId: String,
        val deliveryId: String? = null,
        override val gymId: String? = null,
    ) : StartCommand
    data class Discard(
        val blocked: HomeViewModel.BlockedStart,
        val requested: StartCommand,
        val discarded: Boolean = false,
    ) : StartCommand {
        override val gymId: String? get() = requested.gymId
    }
}
