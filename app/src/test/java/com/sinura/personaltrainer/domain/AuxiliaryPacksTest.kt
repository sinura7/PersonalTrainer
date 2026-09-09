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
                "golf-cooldown", "stretch", "lower-back", "hips", "holds", "core",
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
        assertEquals(AuxiliaryPacks.all.size, AuxiliaryPacks.all.map { it.id }.toSet().size)
    }

    @Test
    fun theGolfCoolDownIsAMobilityPackThatFollowsTheRound() {
        val pack = AuxiliaryPacks.byId("golf-cooldown")
        assertEquals(AuxiliaryPacks.GolfCooldown, pack)
        assertEquals(AuxiliaryKind.MOBILITY, pack!!.kind)
        assertEquals("golf-cooldown", AuxiliaryPacks.mobility.first().id)
        assertEquals(
            listOf(
                "ex-hyper-pro-couch-stretch",
                "ex-hyper-pro-incline-pigeon",
                "ex-hyper-pro-calf-stretch",
                "ex-hyper-pro-elephant-walk",
                "ex-dead-bug",
            ),
            pack.lifts.map { it.exerciseId },
        )
        // A cool-down that repeats the warm-up is the warm-up again.
        assertTrue(pack.lifts.map { it.exerciseId }.intersect(
            AuxiliaryPacks.Golf.lifts.map { it.exerciseId }.toSet(),
        ).size <= 1)
    }
}
