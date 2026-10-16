package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.sync.RemoteDisplayPrefsRow
import com.sinura.personaltrainer.data.sync.SyncAccountPrefs
import com.sinura.personaltrainer.domain.DistanceUnit
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DistancePreferenceTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun fourCombinationsStayIndependentOfTheStoredNumbers() = runBlocking {
        val prefs = deps.preferencesRepository
        prefs.recordBodyweight(80.0, 20_000)
        assertNull(prefs.storedDistanceUnit.first())
        assertEquals(DistanceUnit.KM, DistanceUnit.resolve(stored = null, weight = WeightUnit.KG))
        assertEquals(DistanceUnit.MI, DistanceUnit.resolve(stored = null, weight = WeightUnit.LBS))
        assertEquals(DistanceUnit.KM, DistanceUnit.resolve(stored = DistanceUnit.KM, weight = WeightUnit.LBS))
        assertEquals(DistanceUnit.MI, DistanceUnit.resolve(stored = DistanceUnit.MI, weight = WeightUnit.KG))

        prefs.setWeightUnit(WeightUnit.LBS)
        prefs.setDistanceUnit(DistanceUnit.KM)
        assertEquals(DistanceUnit.KM, prefs.storedDistanceUnit.first())
        assertEquals(WeightUnit.LBS, prefs.weightUnit.first())
        assertEquals(80.0, prefs.bodyweightKg.first()!!, 0.001)

        SyncAccountPrefs.applyDisplayRemote(
            store = prefs.accountSyncSettingsStore,
            remote = RemoteDisplayPrefsRow(
                userId = "local",
                weightUnit = WeightUnit.KG.storageKey,
                clockFormat = "12h",
                bodyweightCheckInWeekday = null,
                updatedAtMs = 1L,
            ),
        )
        assertEquals(DistanceUnit.KM, prefs.storedDistanceUnit.first())
        assertEquals(WeightUnit.KG, prefs.weightUnit.first())
        assertEquals(80.0, prefs.bodyweightKg.first()!!, 0.001)
    }

    @Test
    fun anOldFileWithoutADistanceChoiceFollowsWeight() = runBlocking {
        val prefs = deps.preferencesRepository
        prefs.setWeightUnit(WeightUnit.LBS)
        assertNull(DistanceUnit.fromStorage(raw = null))
        assertNull(prefs.storedDistanceUnit.first())
        assertEquals(
            DistanceUnit.MI,
            DistanceUnit.resolve(prefs.storedDistanceUnit.first(), prefs.weightUnit.first()),
        )
    }
}
