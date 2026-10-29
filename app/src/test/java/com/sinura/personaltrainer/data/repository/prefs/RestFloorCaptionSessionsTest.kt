package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RestFloorCaptionSessionsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun captionBudgetCountsOncePerSessionNotPerRest() = runBlocking {
        val store = SettingsStore(PreferenceDataStoreFactory.create { temp.newFile("prefs.preferences_pb") })
        val restPrefs = RestPrefsStore(store)
        repeat(3) {
            restPrefs.recordRestFloorCaptionForSession("session-a")
        }
        repeat(3) {
            restPrefs.recordRestFloorCaptionForSession("session-b")
        }
        assertEquals(2, restPrefs.restFloorCaptionSessions.first())
    }

    @Test
    fun captionBudgetStopsAfterThreeDistinctSessions() = runBlocking {
        val store = SettingsStore(PreferenceDataStoreFactory.create { temp.newFile("prefs2.preferences_pb") })
        val restPrefs = RestPrefsStore(store)
        restPrefs.recordRestFloorCaptionForSession("s1")
        restPrefs.recordRestFloorCaptionForSession("s2")
        restPrefs.recordRestFloorCaptionForSession("s3")
        assertEquals(3, restPrefs.restFloorCaptionSessions.first())
        restPrefs.recordRestFloorCaptionForSession("s4")
        assertEquals(4, restPrefs.restFloorCaptionSessions.first())
    }
}
