package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadTypeCopyTest {
    @Test
    fun labelsAreGymFloorWordsNotEnumNames() {
        assertEquals("Plates", LoadType.EXTERNAL.label)
        assertEquals("Stack", LoadType.STACK.label)
        assertEquals("Bodyweight", LoadType.BODYWEIGHT.label)
        assertEquals("Added", LoadType.BODYWEIGHT_PLUS.label)
        assertEquals("Assisted", LoadType.ASSISTED.label)
    }

    @Test
    fun aMachineNamesPlatesVersusStack() {
        assertEquals(
            "Machine · Plates",
            LoadTypeCopy.rowTag(EquipmentType.MACHINE, LoadType.EXTERNAL),
        )
        assertEquals(
            "Machine · Stack",
            LoadTypeCopy.rowTag(EquipmentType.MACHINE, LoadType.STACK),
        )
        assertEquals("Assisted", LoadTypeCopy.rowTag(EquipmentType.MACHINE, LoadType.ASSISTED))
        assertEquals(
            "Cable · Stack",
            LoadTypeCopy.rowTag(EquipmentType.CABLE, LoadType.STACK),
        )
    }

    @Test
    fun aBarbellDoesNotRepeatPlates() {
        assertEquals("Barbell", LoadTypeCopy.rowTag(EquipmentType.BARBELL, LoadType.EXTERNAL))
        assertEquals("Dumbbell", LoadTypeCopy.rowTag(EquipmentType.DUMBBELL, LoadType.EXTERNAL))
        assertEquals(
            "Bodyweight",
            LoadTypeCopy.rowTag(EquipmentType.BODYWEIGHT, LoadType.BODYWEIGHT),
        )
        assertEquals(
            "Bodyweight · Added",
            LoadTypeCopy.rowTag(EquipmentType.BODYWEIGHT, LoadType.BODYWEIGHT_PLUS),
        )
    }

    @Test
    fun libraryTagKeepsCustomOnTheOwnersLifts() {
        val plates = Exercise(
            id = "ex-custom-1",
            name = "My squat",
            muscleGroup = "Quads",
            notes = "",
            isCustom = true,
        )
        assertEquals("Custom", LoadTypeCopy.libraryTag(plates))
        assertEquals(
            "Custom · Bodyweight",
            LoadTypeCopy.libraryTag(plates.copy(loadType = LoadType.BODYWEIGHT)),
        )
        assertEquals(
            "Machine · Stack",
            LoadTypeCopy.libraryTag(
                plates.copy(
                    isCustom = false,
                    equipment = EquipmentType.MACHINE,
                    loadType = LoadType.STACK,
                ),
            ),
        )
    }

    @Test
    fun aBodyweightLiftHasNoWeightWell() {
        assertNull(LoadTypeCopy.editorWeightLabel(LoadType.BODYWEIGHT))
        assertFalse(LoadTypeCopy.showsWeightWell(LoadType.BODYWEIGHT))
        assertEquals("Stack", LoadTypeCopy.editorWeightLabel(LoadType.STACK))
        assertEquals("Added", LoadTypeCopy.editorWeightLabel(LoadType.BODYWEIGHT_PLUS))
        assertEquals("Assist", LoadTypeCopy.editorWeightLabel(LoadType.ASSISTED))
        assertEquals(
            LoadTypeCopy.TARGET_WEIGHT,
            LoadTypeCopy.editorWeightLabel(LoadType.EXTERNAL),
        )
        assertTrue(LoadTypeCopy.showsWeightWell(LoadType.STACK))
    }

    @Test
    fun createCaptionNamesTheLoad() {
        assertEquals(
            "Adds a custom Chest lift · Bodyweight",
            LoadTypeCopy.createCaption("Chest", LoadType.BODYWEIGHT),
        )
    }

    @Test
    fun kitForKeepsACableStackAndDropsBodyweightKitOnPlates() {
        assertEquals(EquipmentType.CABLE, LoadType.STACK.kitFor(EquipmentType.CABLE))
        assertEquals(EquipmentType.MACHINE, LoadType.STACK.kitFor(EquipmentType.BARBELL))
        assertEquals(EquipmentType.BODYWEIGHT, LoadType.BODYWEIGHT.kitFor(EquipmentType.OTHER))
        assertEquals(EquipmentType.OTHER, LoadType.EXTERNAL.kitFor(EquipmentType.BODYWEIGHT))
        assertEquals(EquipmentType.BARBELL, LoadType.EXTERNAL.kitFor(EquipmentType.BARBELL))
        assertEquals(EquipmentType.MACHINE, LoadType.ASSISTED.kitFor(EquipmentType.OTHER))
        assertEquals(EquipmentType.OTHER, LoadType.EXTERNAL.defaultEquipment)
        assertEquals(EquipmentType.MACHINE, LoadType.STACK.defaultEquipment)
        assertEquals(EquipmentType.BODYWEIGHT, LoadType.BODYWEIGHT.defaultEquipment)
    }
}
