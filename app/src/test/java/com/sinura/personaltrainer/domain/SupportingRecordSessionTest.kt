package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportingRecordSessionTest {
    private val set = ExerciseSetRecord("set", "session", 60.0, 8, 123L)
    private val record = PersonalRecord(PersonalRecordKind.WEIGHT, "set", "session", 60.0, 8, 60.0, 123L)
    private val source = ExerciseSessionSummary(sessionId = "session", sessionName = "Recorded mixed day", performedAtMs = 100L,
        topSet = set, workingSets = 1, volumeKg = 480.0,
        estimatedOneRepMaxKg = 76.0, sets = listOf(set), kind = HistoryKind.ACTIVITY, localEpochDay = 12L)

    @Test fun exactSupportingSetRetainsItsStoreAndCapturedDay() {
        assertEquals(source, supportingRecordSession(record, listOf(source)))
    }

    @Test fun missingOrChangedSupportingSetCannotPointAtAnotherSession() {
        assertNull(supportingRecordSession(record, emptyList()))
        assertNull(supportingRecordSession(record, listOf(source.copy(sets = listOf(set.copy(weightKg = 65.0))))))
        assertNull(supportingRecordSession(record, listOf(source.copy(sessionId = "another"))))
    }

    @Test fun collidingIdentitiesAcrossTheTwoStoresAreExplicitlyUnresolved() {
        assertNull(supportingRecordSession(record, listOf(source, source.copy(kind = HistoryKind.WORKOUT))))
    }
}
