package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliaryPacksTest {
    @Test
    fun fourPacksReferenceExistingCatalogIds() {
        val ids = DefaultExercises.catalog().map { it.id }.toSet()
        assertEquals(
            listOf("stretch", "lower-back", "hips", "holds"),
            AuxiliaryPacks.all.map { it.id },
        )
        AuxiliaryPacks.all.forEach { pack ->
            assertTrue(pack.title, pack.lifts.isNotEmpty())
            pack.lifts.forEach { lift ->
                assertTrue("${pack.id}:${lift.exerciseId}", lift.exerciseId in ids)
                assertTrue(lift.sets >= 1)
                assertTrue(lift.reps >= 1)
            }
        }
        assertEquals(AuxiliaryPacks.Stretch, AuxiliaryPacks.byId("stretch"))
    }
}
