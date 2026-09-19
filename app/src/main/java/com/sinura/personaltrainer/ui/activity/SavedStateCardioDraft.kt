package com.sinura.personaltrainer.ui.activity

import androidx.lifecycle.SavedStateHandle
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.DraftStore

data class CardioInputDraft(
    val type: CardioType,
    val indoor: Boolean,
    val distanceKm: String,
)

/**
 * Live-cardio type, indoor and distance, mirrored into saved state so a process
 * death mid-run does not reset the owner's later choices to the Room row they
 * started with. Cleared on an accepted finish or a discard.
 */
class SavedStateCardioDraft(private val handle: SavedStateHandle) : DraftStore<CardioInputDraft> {
    override fun read(): CardioInputDraft? {
        if (!handle.contains(KEY_TYPE) &&
            !handle.contains(KEY_INDOOR) &&
            !handle.contains(KEY_DISTANCE)
        ) {
            return null
        }
        val type = handle.get<String>(KEY_TYPE)
            ?.let { raw -> CardioType.entries.firstOrNull { it.name == raw } }
            ?: CardioType.RUN
        return CardioInputDraft(
            type = type,
            indoor = handle.get<Boolean>(KEY_INDOOR) ?: false,
            distanceKm = handle.get<String>(KEY_DISTANCE).orEmpty(),
        )
    }

    override fun write(value: CardioInputDraft) {
        handle[KEY_TYPE] = value.type.name
        handle[KEY_INDOOR] = value.indoor
        handle[KEY_DISTANCE] = value.distanceKm
    }

    override fun clear() {
        handle.remove<String>(KEY_TYPE)
        handle.remove<Boolean>(KEY_INDOOR)
        handle.remove<String>(KEY_DISTANCE)
    }

    fun containsType(): Boolean = handle.contains(KEY_TYPE)

    fun containsIndoor(): Boolean = handle.contains(KEY_INDOOR)

    companion object {
        const val KEY_TYPE = "liveCardio.type"
        const val KEY_INDOOR = "liveCardio.indoor"
        const val KEY_DISTANCE = "liveCardio.distance"
    }
}
