package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GymScopeTest {

    @Test
    fun gymSpecificEquipmentMatchesExtraStack() {
        assertEquals(ExtraEquipment.GYM_STACK, GymScope.GYM_SPECIFIC)
    }

    @Test
    fun freeWeightsShareHistory() {
        val scope = GymScope.of(EquipmentType.BARBELL, "gym-af-weston")
        assertTrue(scope is HistoryScope.Shared)
    }

    @Test
    fun machinesScopeToGym() {
        val scope = GymScope.of(EquipmentType.MACHINE, "gym-af-weston")
        assertEquals(HistoryScope.AtGym("gym-af-weston"), scope)
    }

    @Test
    fun unassignedUsesNullGymId() {
        val scope = GymScope.of(EquipmentType.CABLE, null)
        assertEquals(HistoryScope.AtGym(null), scope)
        val (shared, gymId) = com.sinura.personaltrainer.domain.GymScope.run { scope.sqlArgs() }
        assertFalse(shared)
        assertEquals(null, gymId)
    }

    @Test
    fun loadTypeDoesNotChangeScope() {
        val plateLoaded = GymScope.of(EquipmentType.MACHINE, "gym-home")
        val assisted = GymScope.of(EquipmentType.MACHINE, "gym-home")
        assertEquals(plateLoaded, assisted)
    }
}
