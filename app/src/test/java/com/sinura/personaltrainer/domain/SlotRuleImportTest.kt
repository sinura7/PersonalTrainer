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

    @Test
    fun upsertUpdatesRoutineAndFocusOnAnExistingRule() {
        val slot = ScheduleSlot(
            id = "slot-1",
            position = 0,
            routineId = "r-pull",
            focusKind = null,
            anchorDay = Weekday.TUESDAY,
            createdAt = 1L,
            updatedAt = 2L,
        )
        val existing = SlotRuleImport.ruleFromSlot(
            slot.copy(routineId = "r-push", focusKind = SessionFocusKind.PUSH, anchorDay = Weekday.MONDAY),
            1L,
        )!!
        val updated = SlotRuleImport.upsertFromSlot(existing, slot, 9L)!!
        assertEquals("rule-slot-1", updated.id)
        assertEquals("r-pull", updated.routineId)
        assertEquals(Weekday.TUESDAY, updated.weekday)
        assertEquals(null, updated.focusKind)
        assertEquals(18, updated.hour)
        assertEquals(9L, updated.updatedAtMs)
    }

    @Test
    fun upsertIsNullWhenSlotFieldsAlreadyMatch() {
        val slot = ScheduleSlot(
            id = "slot-1",
            position = 0,
            routineId = "r-push",
            focusKind = null,
            anchorDay = Weekday.MONDAY,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val existing = SlotRuleImport.ruleFromSlot(slot, 1L)!!
        assertNull(SlotRuleImport.upsertFromSlot(existing, slot, 9L))
    }

    @Test
    fun userTimedRuleIdsAreNotImportedSlotRules() {
        assertTrue(SlotRuleImport.isUserTimedRule("rule-strength-1-abc"))
        assertTrue(SlotRuleImport.isUserTimedRule("rule-cardio-1-abc"))
        assertTrue(SlotRuleImport.isUserTimedRule("rule-mixed-1-abc"))
        assertTrue(SlotRuleImport.isImportedSlotRule("rule-slot-1"))
        assertTrue(!SlotRuleImport.isUserTimedRule("rule-slot-1"))
        assertTrue(!SlotRuleImport.isImportedSlotRule("rule-strength-1-abc"))
    }

    @Test
    fun nextLaterHourSitsAfterTheEveningPin() {
        assertEquals(20, SlotRuleImport.nextLaterHour(emptyList()))
        assertEquals(20, SlotRuleImport.nextLaterHour(listOf(18)))
        assertEquals(20, SlotRuleImport.nextLaterHour(listOf(7, 18)))
        assertEquals(22, SlotRuleImport.nextLaterHour(listOf(7, 18, 20)))
        assertEquals(23, SlotRuleImport.nextLaterHour(listOf(18, 20, 22)))
    }
}
