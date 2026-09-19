package com.sinura.personaltrainer.ui.progress

import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySpokenTest {
    @Test
    fun muscleRowMergesIdentityRecencyAndWork() {
        val spoken = muscleRowSpoken(CHEST, HeatWindow.CURRENT_WEEK, WeightUnit.KG)
        assertEquals(
            "Chest, This week: low load, 8 credited sets, 3200 kg, 2 days ago",
            spoken,
        )
        assertEquals("Week: low · 2 days ago", muscleRowSubtitle(CHEST, HeatWindow.CURRENT_WEEK))
    }

    @Test
    fun doorwayRowNamesTheTapNotZeroWork() {
        val spoken = muscleRowSpoken(
            UNTRAINED,
            HeatWindow.DAY,
            WeightUnit.KG,
            doorway = true,
        )
        assertEquals(
            "Chest, Today: no work, Not trained yet. Tap to see the lifts that train it.",
            spoken,
        )
    }

    @Test
    fun mapSpokenNamesTheRowsAsTheTarget() {
        assertTrue(BodyTags.MAP_SPOKEN.contains("muscle list"))
        assertEquals("body-muscle-CHEST", BodyTags.muscle(CanonicalMuscle.CHEST))
    }

    companion object {
        val CHEST = MuscleLoadSummary(
            muscle = CanonicalMuscle.CHEST,
            volumeKg = 3_200.0,
            workingSets = 8,
            sessionCount = 2,
            lastTrainedAtMs = 1_700_000_000_000L,
            daysSinceLastTrained = 2,
            weeklySets = 8.0,
            heat = 0.5,
            exercises = emptyList(),
        )
        val UNTRAINED = MuscleLoadSummary(
            muscle = CanonicalMuscle.CHEST,
            volumeKg = 0.0,
            workingSets = 0,
            sessionCount = 0,
            lastTrainedAtMs = null,
            daysSinceLastTrained = null,
            weeklySets = 0.0,
            heat = 0.0,
            exercises = emptyList(),
        )
    }
}
