package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlanRemoteRowsTest {
    @Test
    fun routineRoundTripUsesSnakeCaseOnWire() {
        val entity = RoutineEntity(
            id = "r1",
            name = "Legs",
            notes = "notes",
            createdAt = 10L,
            updatedAt = 20L,
        )
        val json = encodeSync(entity.toRemote(userId = "user-abc"))
        assertTrue(json.contains("\"user_id\":\"user-abc\""))
        assertTrue(json.contains("\"created_at_ms\":10"))
        assertTrue(json.contains("\"updated_at_ms\":20"))
        val back = decodeSync<RemoteRoutineRow>(json).toEntity()
        assertEquals(entity, back)
    }

    @Test
    fun routineExerciseMapsTargetColumns() {
        val item = RoutineExerciseEntity(
            id = "re1",
            routineId = "r1",
            exerciseId = "ex1",
            sortOrder = 2,
            targetSets = 3,
            targetReps = 8,
            targetWeightKg = 60.0,
            restSeconds = 90,
            targetSeconds = null,
            targetSecondsMax = null,
        )
        val json = encodeSync(item.toRemote(userId = "user-abc", updatedAtMs = 50L))
        assertTrue(json.contains("\"routine_id\":\"r1\""))
        assertTrue(json.contains("\"target_sets\":3"))
        assertTrue(json.contains("\"sort_order\":2"))
        val back = decodeSync<RemoteRoutineExerciseRow>(json).toEntity()
        assertEquals(item, back)
    }

    @Test
    fun activityTemplateRoundTrip() {
        val entity = ActivityTemplateEntity(
            id = "tpl-1",
            title = "Morning",
            notes = "",
            updatedAtMs = 77L,
        )
        val json = encodeSync(entity.toRemote(userId = "user-abc"))
        assertTrue(json.contains("\"updated_at_ms\":77"))
        val back = decodeSync<RemoteActivityTemplateRow>(json).toEntity()
        assertEquals(entity, back)
    }
}
