package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.ScheduleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraPickerArtworkTest {
    @Test
    fun everyCardioTypeHasAnImageResource() {
        CardioType.entries.forEach { type ->
            assertTrue("$type has no still", cardioPickerArtwork(type) != 0)
        }
    }

    @Test
    fun planCardioTypesEachHaveTheirOwnStill() {
        val ids = ScheduleKind.planCardioTypes.map { cardioPickerArtwork(it) }
        assertEquals(ScheduleKind.planCardioTypes.size, ids.toSet().size)
        assertEquals(R.drawable.cardio_walk, cardioPickerArtwork(CardioType.WALK))
        assertEquals(R.drawable.cardio_run, cardioPickerArtwork(CardioType.RUN))
        assertEquals(R.drawable.cardio_ride, cardioPickerArtwork(CardioType.RIDE))
        assertEquals(R.drawable.cardio_row, cardioPickerArtwork(CardioType.ROW))
        assertEquals(R.drawable.cardio_swim, cardioPickerArtwork(CardioType.SWIM))
        assertEquals(R.drawable.cardio_hike, cardioPickerArtwork(CardioType.HIKE))
        ids.forEach { id ->
            assertNotEquals("plan cardio reused the unlit figure", R.drawable.temper_front_unlit, id)
        }
    }

    @Test
    fun extraWarmUpAndMobilityItemsExposeACatalogStill() {
        AuxiliaryPacks.all.forEach { pack ->
            val art = extraPackArtwork(pack.id)
            assertEquals(pack.title, keyedArtwork(pack.imageKey), art)
            assertNotEquals(pack.title, 0, art)
            assertNotEquals(
                "${pack.id} fell back to the unlit figure",
                R.drawable.temper_front_unlit,
                art,
            )
        }
        assertEquals(
            AuxiliaryPacks.all.size,
            AuxiliaryPacks.all.map { extraPackArtwork(it.id) }.toSet().size,
        )
    }
}
