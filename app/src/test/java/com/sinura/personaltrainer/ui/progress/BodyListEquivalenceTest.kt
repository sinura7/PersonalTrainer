package com.sinura.personaltrainer.ui.progress

import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** P11: the full-size list names every mapped muscle the figure can select. */
class BodyListEquivalenceTest {
    @Test
    fun everyMappedMuscleHasARowAndOtherAppearsOnlyWithWork() {
        val empty = snapshot(loads = emptyList())
        assertEquals(CanonicalMuscle.bodyMapOrder, muscleRows(empty).map { it.muscle })
        assertFalse(muscleRows(empty).any { it.muscle == CanonicalMuscle.OTHER })

        val other = load(CanonicalMuscle.OTHER, workingSets = 2)
        assertEquals(
            CanonicalMuscle.bodyMapOrder + CanonicalMuscle.OTHER,
            muscleRows(snapshot(loads = listOf(other))).map { it.muscle },
        )
    }

    @Test
    fun aRowShowsTheSameWorkTheSnapshotStoresForThatWindow() {
        val chest = load(CanonicalMuscle.CHEST, workingSets = 4)
        val snap = snapshot(loads = listOf(chest)).copy(window = HeatWindow.DAY)
        val row = muscleRows(snap).single { it.muscle == CanonicalMuscle.CHEST }
        assertEquals(HeatWindow.DAY, snap.window)
        assertEquals(snap.load(CanonicalMuscle.CHEST).workingSets, row.workingSets)
        assertEquals(4, row.workingSets)
    }

    private fun snapshot(loads: List<MuscleLoadSummary>) = BodyHeatSnapshot(
        window = HeatWindow.CURRENT_WEEK,
        windowStartMs = 0L,
        generatedAtMs = 0L,
        loads = loads,
        hasAnyWorkingSets = loads.any { it.workingSets > 0 },
        hasWindowWorkingSets = loads.any { it.workingSets > 0 },
    )

    private fun load(muscle: CanonicalMuscle, workingSets: Int) = MuscleLoadSummary(
        muscle = muscle,
        volumeKg = 0.0,
        workingSets = workingSets,
        sessionCount = 1,
        lastTrainedAtMs = null,
        daysSinceLastTrained = null,
        weeklySets = workingSets.toDouble(),
        heat = 0.2,
        exercises = emptyList(),
    )
}
