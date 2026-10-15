package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyweightSyncRowTest {
    @Test fun aDeleteMovesUpdatedAtAndAnUpsertClearsTheTombstone() {
        val entry = BodyweightEntryEntity(epochDay = 20_000L, kg = 80.0, recordedAtMs = 100L)
        val deleted = entry.toRemote("user", deletedAtMs = 500L)
        assertEquals(500L, deleted.updatedAtMs)
        assertEquals(500L, deleted.deletedAtMs)
        val live = encodeSyncKeepingNulls(entry.toRemote("user"))
        assertTrue(live.contains("\"deleted_at_ms\":null"))
        assertTrue(live.contains("\"updated_at_ms\":100"))
    }
}
