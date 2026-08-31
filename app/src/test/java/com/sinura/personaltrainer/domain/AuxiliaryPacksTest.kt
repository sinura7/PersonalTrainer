package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliaryPacksTest {
    @Test
    fun packsReferenceExistingCatalogIds() {
        val ids = DefaultExercises.catalog().map { it.id }.toSet()
        assertEquals(
            listOf(
                "golf", "lower-body", "upper-body", "shoulder",
                "stretch", "lower-back", "hips", "holds", "core",
            ),
            AuxiliaryPacks.all.map { it.id },
        )
        assertEquals(
            listOf("golf", "lower-body", "upper-body", "shoulder"),
            AuxiliaryPacks.warmups.map { it.id },
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
        assertEquals(AuxiliaryPacks.Golf, AuxiliaryPacks.byId("golf"))
        assertEquals(AuxiliaryPacks.Core, AuxiliaryPacks.byId("core"))
        assertEquals(AuxiliaryKind.WARMUP, AuxiliaryPacks.Shoulder.kind)
        assertEquals(AuxiliaryKind.MOBILITY, AuxiliaryPacks.Hips.kind)
    }
}
