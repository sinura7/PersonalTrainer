package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotRuleImportTest {
    @Test
    fun importedSlotBecomesEveningStrength() {
        val slot = ScheduleSlot(
            id = "slot-1",
            position = 0,
            routineId = "r-push",
            focusKind = null,
            anchorDay = Weekday.MONDAY,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val rule = SlotRuleImport.ruleFromSlot(slot, 9L)!!
        assertEquals("rule-slot-1", rule.id)
        assertEquals(Weekday.MONDAY, rule.weekday)
        assertEquals(18, rule.hour)
        assertEquals(ScheduleModality.STRENGTH, rule.modality)
        assertEquals("r-push", rule.routineId)
        assertEquals(ZonePolicy.FOLLOW_DEVICE, rule.zonePolicy)
    }

    @Test
    fun unanchoredSlotIsNotImported() {
        val slot = ScheduleSlot(
            id = "slot-2",
            position = 1,
            routineId = null,
            focusKind = SessionFocusKind.PULL,
            anchorDay = null,
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertNull(SlotRuleImport.ruleFromSlot(slot, 1L))
        assertTrue(SlotRuleImport.rulesFromSlots(listOf(slot), 1L).isEmpty())
    }
}
