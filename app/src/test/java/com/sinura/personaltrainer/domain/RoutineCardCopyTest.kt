package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineCardCopyTest {
    @Test
    fun mixIsNullWhenThereAreNoLifts() {
        assertNull(RoutineCardCopy.mix(emptyList()))
    }

    @Test
    fun aMachineDayNamesTheKitOnce() {
        assertEquals(
            "Machine",
            RoutineCardCopy.mix(
                listOf(EquipmentType.MACHINE, EquipmentType.MACHINE, EquipmentType.MACHINE),
            ),
        )
    }

    @Test
    fun mixKeepsFirstSeenOrderAndDropsLaterDuplicates() {
        assertEquals(
            "Machine · Barbell · Cable",
            RoutineCardCopy.mix(
                listOf(
                    EquipmentType.MACHINE,
                    EquipmentType.BARBELL,
                    EquipmentType.MACHINE,
                    EquipmentType.CABLE,
                    EquipmentType.BARBELL,
                ),
            ),
        )
    }

    @Test
    fun smithStaysSmithAndIsNotFoldedIntoMachine() {
        assertEquals(
            "Smith · Machine",
            RoutineCardCopy.mix(listOf(EquipmentType.SMITH, EquipmentType.MACHINE)),
        )
    }

    @Test
    fun theStartCardPicturesThreeLifts() {
        assertEquals(3, RoutineCardCopy.STILL_LIMIT)
    }
}
