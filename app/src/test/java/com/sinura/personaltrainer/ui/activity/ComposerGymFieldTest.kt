package com.sinura.personaltrainer.ui.activity

import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityRules
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.IdFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerGymFieldTest {
    private val now = JvmTime.captureNow()
    private val block = StrengthBlock(
        id = "b1",
        sortOrder = 0,
        exerciseId = "pec-deck",
        exerciseName = "Pec Deck",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.MACHINE,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet("s1", 1, 50.0, 10, null, false, JvmTime.nowMillis()),
        ),
    )

    @Test
    fun strengthDraftRejectedWithoutGymPick() {
        val draft = ActivityDraft(
            origin = ActivityOrigin.BACKDATED,
            title = "Past",
            performedStart = now,
            blocks = listOf<ActivityBlock>(block),
            gymResolved = false,
        )
        val write = ActivityRules.confirm(draft, emptyList(), now, IdFactory.Uuid, JvmTime)
        assertTrue(write is ActivityWrite.Rejected)
        assertEquals("Pick a gym.", (write as ActivityWrite.Rejected).reason)
    }

    @Test
    fun unassignedGymAcceptedWhenResolved() {
        val draft = ActivityDraft(
            origin = ActivityOrigin.BACKDATED,
            title = "Past",
            performedStart = now,
            blocks = listOf<ActivityBlock>(block),
            gymId = null,
            gymResolved = true,
        )
        val write = ActivityRules.confirm(draft, emptyList(), now, IdFactory.Uuid, JvmTime)
        assertTrue(write is ActivityWrite.Accepted)
        assertEquals(null, (write as ActivityWrite.Accepted).session.gymId)
    }
}
