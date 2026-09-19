package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuxiliaryPacksTest {
    private val catalogEquipment: Map<String, EquipmentType> =
        DefaultExercises.catalog().associate { it.id to it.equipment }

    @Test
    fun packsReferenceExistingCatalogIds() {
        val ids = DefaultExercises.catalog().map { it.id }.toSet()
        assertEquals(
            listOf(
                "golf", "lower-body", "upper-body", "shoulder",
                "golf-cooldown", "stretch", "lower-back", "hips", "holds", "core",
            ),
            AuxiliaryPacks.forEquipment(ExtraEquipment.MIXED).map { it.id },
        )
        assertEquals(
            listOf("golf", "lower-body", "upper-body", "shoulder"),
            AuxiliaryPacks.forEquipment(ExtraEquipment.MIXED)
                .filter { it.kind == AuxiliaryKind.WARMUP }
                .map { it.id },
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
    fun everyPackExposesACatalogStillFromItsOwnLifts() {
        val catalogKeys = DefaultExercises.catalog().map { it.imageKey }.toSet()
        AuxiliaryPacks.all.forEach { pack ->
            assertTrue("${pack.id} imageKey blank", pack.imageKey.isNotBlank())
            assertTrue("${pack.id} imageKey ${pack.imageKey} missing", pack.imageKey in catalogKeys)
            assertTrue(
                "${pack.id} still is not one of its lifts",
                pack.lifts.any { it.exerciseId.replace('-', '_') == pack.imageKey },
            )
        }
        assertEquals(
            AuxiliaryPacks.all.size,
            AuxiliaryPacks.all.map { it.imageKey }.toSet().size,
        )
    }

    @Test
    fun theGolfCoolDownIsAMobilityPackThatFollowsTheRound() {
        val pack = AuxiliaryPacks.byId("golf-cooldown")
        assertEquals(AuxiliaryPacks.GolfCooldown, pack)
        assertEquals(AuxiliaryKind.MOBILITY, pack!!.kind)
        assertEquals("golf-cooldown", AuxiliaryPacks.mobility.first().id)
        assertEquals(
            listOf(
                "ex-couch-stretch",
                "ex-good-morning",
                "ex-hyper-pro-elephant-walk",
                "ex-hyper-pro-calf-stretch",
            ),
            pack.lifts.map { it.exerciseId },
        )
        // A cool-down that repeats the warm-up is the warm-up again.
        assertTrue(pack.lifts.map { it.exerciseId }.intersect(
            AuxiliaryPacks.Golf.lifts.map { it.exerciseId }.toSet(),
        ).size <= 1)
    }

    @Test
    fun noneEquipmentHidesMachineOnlyExtraPacks() {
        val none = AuxiliaryPacks.forEquipment(ExtraEquipment.NONE)
        val noneIds = none.map { it.id }.toSet()
        assertFalse("None listed machine Core", "core" in noneIds)
        assertFalse("None listed Hyper Pro golf", "golf" in noneIds)
        assertTrue("None hid floor Core", "core-none" in noneIds)
        assertTrue("None hid floor Holds", "holds-none" in noneIds)
        none.forEach { pack ->
            pack.lifts.forEach { lift ->
                val equipment = catalogEquipment.getValue(lift.exerciseId)
                assertFalse(
                    "${pack.id}:${lift.exerciseId} is ${equipment.name}",
                    equipment in ExtraEquipment.GYM_STACK,
                )
            }
        }
        assertFalse(
            "None still offers a gym-stack pack",
            none.any { AuxiliaryPacks.usesGymStack(it, catalogEquipment) },
        )
    }

    @Test
    fun machinesIncludesMachinePacks() {
        val machines = AuxiliaryPacks.forEquipment(ExtraEquipment.MACHINES)
        val machineIds = machines.map { it.id }.toSet()
        assertTrue("Machines hid Core", "core-machines" in machineIds)
        assertTrue("Machines hid golf", "golf-machines" in machineIds)
        assertTrue("Machines hid machine lower-body", "lower-body-machines" in machineIds)
        assertFalse("Machines listed floor Core", "core-none" in machineIds)
        assertTrue(
            "Machines has at least one gym-stack pack",
            machines.any { AuxiliaryPacks.usesGymStack(it, catalogEquipment) },
        )
        assertTrue(
            "Core still has a machine or cable crunch",
            AuxiliaryPacks.Core.lifts.any { lift ->
                catalogEquipment[lift.exerciseId] in ExtraEquipment.GYM_STACK
            },
        )
    }

    @Test
    fun noneAndMachinesDoNotListTheSamePacks() {
        val nonePacks = AuxiliaryPacks.forEquipment(ExtraEquipment.NONE)
        val machinePacks = AuxiliaryPacks.forEquipment(ExtraEquipment.MACHINES)
        val none = nonePacks.map { it.id }.toSet()
        val machines = machinePacks.map { it.id }.toSet()
        assertNotEquals(none, machines)
        assertTrue(
            "None and Machines must not share a pack",
            (none intersect machines).isEmpty(),
        )
        val noneLifts = nonePacks.flatMap { pack -> pack.lifts.map { it.exerciseId } }.toSet()
        val machineLifts = machinePacks.flatMap { pack -> pack.lifts.map { it.exerciseId } }.toSet()
        assertNotEquals(
            "None and Machines must vary the lifts, not just the ids",
            noneLifts,
            machineLifts,
        )
    }

    @Test
    fun freeWeightsGetDumbbellAndBarbellRamps() {
        val free = AuxiliaryPacks.forEquipment(ExtraEquipment.FREE_WEIGHTS)
        assertTrue("goblet squat ramp", free.any { it.id == "lower-body-free" })
        val freeEquipment = free.flatMap { pack ->
            pack.lifts.map { catalogEquipment.getValue(it.exerciseId) }
        }.toSet()
        assertTrue(EquipmentType.DUMBBELL in freeEquipment || EquipmentType.BARBELL in freeEquipment)
        assertTrue(
            "Free weights offered a gym-stack pack",
            free.none { AuxiliaryPacks.usesGymStack(it, catalogEquipment) },
        )
    }

    @Test
    fun settingsKitIsASuggestionNotALock() {
        assertEquals(
            ExtraEquipment.NONE,
            ExtraEquipment.fromPreferences(
                setOf(EquipmentType.BODYWEIGHT.name, EquipmentType.OTHER.name),
            ),
        )
        assertEquals(
            ExtraEquipment.FREE_WEIGHTS,
            ExtraEquipment.fromPreferences(
                setOf(EquipmentType.DUMBBELL.name, EquipmentType.BARBELL.name),
            ),
        )
        assertEquals(
            ExtraEquipment.MACHINES,
            ExtraEquipment.fromPreferences(
                setOf(EquipmentType.MACHINE.name, EquipmentType.CABLE.name),
            ),
        )
        assertEquals(ExtraEquipment.MIXED, ExtraEquipment.fromPreferences(emptySet()))
        assertEquals(
            ExtraEquipment.MIXED,
            ExtraEquipment.fromPreferences(
                setOf(EquipmentType.DUMBBELL.name, EquipmentType.MACHINE.name),
            ),
        )
        assertEquals("Bodyweight (none)", ExtraEquipment.NONE.label)
        assertTrue(ExtraEquipment.entries.none { it.label == "None" })
    }

    @Test
    fun fortyCombosCoverTenTypesAndFourKits() {
        assertEquals(40, AuxiliaryPacks.all.size)
        ExtraEquipment.entries.forEach { kit ->
            val packs = AuxiliaryPacks.forEquipment(kit)
            assertEquals(kit.label, 10, packs.size)
            assertEquals(kit.label, 10, packs.map { it.family }.toSet().size)
            packs.forEach { pack ->
                assertTrue("${pack.id} too short", pack.lifts.size in 3..4)
            }
        }
        listOf(
            "golf", "lower-body", "upper-body", "shoulder",
            "golf-cooldown", "stretch", "lower-back", "hips", "holds", "core",
        ).forEach { family ->
            assertEquals(family, 4, AuxiliaryPacks.all.count { it.family == family })
        }
    }

    @Test
    fun mixedCombosMixFreeWeightsAndMachines() {
        AuxiliaryPacks.forEquipment(ExtraEquipment.MIXED).forEach { pack ->
            val types = pack.lifts.map { catalogEquipment.getValue(it.exerciseId) }.toSet()
            assertTrue(
                "${pack.id} has no free weight: $types",
                types.any { it in ExtraEquipment.FREE },
            )
            assertTrue(
                "${pack.id} has no machine: $types",
                types.any { it in ExtraEquipment.GYM_STACK },
            )
        }
    }
}
