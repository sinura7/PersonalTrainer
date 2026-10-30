package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
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
    fun legacySessionCountIsReadBeforeMigratingToIds() = runBlocking {
        val store = SettingsStore(PreferenceDataStoreFactory.create { temp.newFile("prefs3.preferences_pb") })
        store.data.edit { prefs ->
            prefs[REST_FLOOR_CAPTION_SESSIONS] = 2
            prefs[REST_FLOOR_CAPTION_LAST_SESSION] = "legacy-a"
        }
        val restPrefs = RestPrefsStore(store)
        restPrefs.recordRestFloorCaptionForSession("legacy-b")
        // Two sessions were captioned before the migration; legacy-b is the third.
        assertEquals(3, restPrefs.restFloorCaptionSessions.first())
        restPrefs.recordRestFloorCaptionForSession("legacy-c")
        assertEquals(4, restPrefs.restFloorCaptionSessions.first())
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
        // Session 4 is over the budget of 3, so the caption hides from here on.
        assertEquals(4, restPrefs.restFloorCaptionSessions.first())
        restPrefs.recordRestFloorCaptionForSession("s5")
        assertEquals(4, restPrefs.restFloorCaptionSessions.first())
        restPrefs.recordRestFloorCaptionForSession("s2")
        assertEquals(4, restPrefs.restFloorCaptionSessions.first())
    }

    @Test
    fun legacyCountAtTheBudgetMigratesOverTheBudget() = runBlocking {
        val store = SettingsStore(PreferenceDataStoreFactory.create { temp.newFile("prefs4.preferences_pb") })
        store.data.edit { prefs ->
            prefs[REST_FLOOR_CAPTION_SESSIONS] = 3
            prefs[REST_FLOOR_CAPTION_LAST_SESSION] = "legacy-a"
        }
        val restPrefs = RestPrefsStore(store)
        restPrefs.recordRestFloorCaptionForSession("next")
        assertEquals(4, restPrefs.restFloorCaptionSessions.first())
    }
}
