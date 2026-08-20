package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleNormalizerTest {
    @Test
    fun seededExercisesMapToCanonicalMuscles() {
        DefaultExercises.catalog().forEach { exercise ->
            val mapping = MuscleNormalizer.normalize(exercise.muscleGroup)
            assertTrue(
                "${exercise.name} (${exercise.muscleGroup}) should not fall through to Other",
                mapping.primary != CanonicalMuscle.OTHER,
            )
        }
    }

    @Test
    fun catalogLabelsMapToExpectedPrimaries() {
        assertEquals(CanonicalMuscle.QUADRICEPS, MuscleNormalizer.primaryOf("Quads"))
        assertEquals(CanonicalMuscle.HAMSTRINGS, MuscleNormalizer.primaryOf("Hamstrings"))
        assertEquals(CanonicalMuscle.GLUTES, MuscleNormalizer.primaryOf("Glutes"))
        assertEquals(CanonicalMuscle.CALVES, MuscleNormalizer.primaryOf("Calves"))
        assertEquals(CanonicalMuscle.CHEST, MuscleNormalizer.primaryOf("Chest"))
        assertEquals(CanonicalMuscle.BACK, MuscleNormalizer.primaryOf("Back"))
        assertEquals(CanonicalMuscle.SHOULDERS, MuscleNormalizer.primaryOf("Shoulders"))
        assertEquals(CanonicalMuscle.BICEPS, MuscleNormalizer.primaryOf("Biceps"))
        assertEquals(CanonicalMuscle.TRICEPS, MuscleNormalizer.primaryOf("Triceps"))
        assertEquals(CanonicalMuscle.CORE, MuscleNormalizer.primaryOf("Core"))
        assertEquals(CanonicalMuscle.SHOULDERS, MuscleNormalizer.primaryOf("Rear delts"))
        assertEquals(CanonicalMuscle.BACK, MuscleNormalizer.primaryOf("Posterior chain"))
        assertEquals(CanonicalMuscle.OTHER, MuscleNormalizer.primaryOf("Other"))
    }

    @Test
    fun posteriorChainAddsSecondaryLegDrive() {
        val mapping = MuscleNormalizer.normalize("Posterior chain")
        assertEquals(CanonicalMuscle.BACK, mapping.primary)
        assertEquals(listOf(CanonicalMuscle.HAMSTRINGS, CanonicalMuscle.GLUTES), mapping.secondaries)
    }

    @Test
    fun aliasesAndCaseFold() {
        assertEquals(CanonicalMuscle.CHEST, MuscleNormalizer.primaryOf(" pecs "))
        assertEquals(CanonicalMuscle.BACK, MuscleNormalizer.primaryOf("LATS"))
        assertEquals(CanonicalMuscle.CORE, MuscleNormalizer.primaryOf("Abs"))
        assertEquals(CanonicalMuscle.QUADRICEPS, MuscleNormalizer.primaryOf("quad"))
    }

    @Test
    fun blankAndUnknownFallBackToOther() {
        assertEquals(CanonicalMuscle.OTHER, MuscleNormalizer.primaryOf(""))
        assertEquals(CanonicalMuscle.OTHER, MuscleNormalizer.primaryOf("   "))
        assertEquals(CanonicalMuscle.OTHER, MuscleNormalizer.primaryOf(null))
        assertEquals(CanonicalMuscle.OTHER, MuscleNormalizer.primaryOf("Neck"))
        assertEquals(CanonicalMuscle.OTHER, MuscleNormalizer.primaryOf("Full Body"))
    }

    @Test
    fun filterMatchesCanonicalEquivalents() {
        assertTrue(MuscleNormalizer.matchesFilter("Quads", "Quadriceps"))
        assertTrue(MuscleNormalizer.matchesFilter("Rear delts", "Shoulders"))
        assertTrue(MuscleNormalizer.matchesFilter("Chest", "pecs"))
        assertTrue(MuscleNormalizer.matchesFilter("Biceps", null))
    }
}
