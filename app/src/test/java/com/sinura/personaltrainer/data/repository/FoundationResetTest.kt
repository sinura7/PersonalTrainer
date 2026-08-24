package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.FoundationGeneration
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.WeightUnit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoundationResetTest {
    private lateinit var context: Context
    private lateinit var preferences: PreferencesRepository
    private lateinit var prefsScope: CoroutineScope
    private val deleted = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(
            scope = prefsScope,
            produceFile = {
                File(context.cacheDir, "datastore/reset-${System.nanoTime()}.preferences_pb")
            },
        )
        preferences = PreferencesRepository(context, store)
    }

    @After
    fun tearDown() {
        prefsScope.cancel()
    }

    @Test
    fun refusesWithoutExportOrIrreversibleAck() = runBlocking {
        val reset = reset(frozen = false)
        val noExport = reset.run(ResetAcknowledgements(false, true))
        assertTrue(noExport is ResetResult.Refused)
        val noAck = reset.run(ResetAcknowledgements(true, false))
        assertTrue(noAck is ResetResult.Refused)
    }

    @Test
    fun frozenInstallWithoutLegacyFileRefusesASecondWipe() = runBlocking {
        val reset = reset(
            frozen = true,
            temperExists = true,
            legacyExists = false,
        )
        val result = reset.run(ResetAcknowledgements(true, true))
        assertEquals("Foundation generation is frozen.", (result as ResetResult.Refused).reason)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun resetPreservesUnitsAndRestAndDeletesLegacy() = runBlocking {
        preferences.setWeightUnit(WeightUnit.KG)
        preferences.setRestSoundEnabled(false)
        preferences.setRestVibrationEnabled(false)
        preferences.setDefaultRestSeconds(45)
        preferences.setTrainingGoal(TrainingGoal.HYPERTROPHY)
        preferences.setHeatWindow(HeatWindow.LAST_30_DAYS)
        preferences.setOnboardingComplete(true)

        val reset = reset(frozen = false, temperExists = false, legacyExists = true)
        val result = reset.run(ResetAcknowledgements(hasExternalExport = true, understandsIrreversible = true))
        assertTrue(result is ResetResult.Accepted)
        assertEquals(listOf(FoundationGeneration.LEGACY_DATABASE_FILE), deleted)
        assertEquals(WeightUnit.KG, preferences.weightUnit.first())
        assertFalse(preferences.restTimerPreferences.first().soundEnabled)
        assertFalse(preferences.restTimerPreferences.first().vibrationEnabled)
        assertEquals(45, preferences.restTimerPreferences.first().defaultRestSeconds)
        assertEquals(TrainingGoal.GENERAL, preferences.coachPreferences.first().goal)
        assertFalse(preferences.onboardingComplete.first())
        assertEquals(FoundationGeneration.NAME, preferences.foundationGeneration.first())
    }

    private fun reset(
        frozen: Boolean,
        temperExists: Boolean = false,
        legacyExists: Boolean = true,
    ): FoundationReset {
        return FoundationReset(
            context = context,
            preferences = preferences,
            frozen = frozen,
            openTemper = {
                Room.inMemoryDatabaseBuilder(it, TemperDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            },
            deleteDatabase = { _, name ->
                deleted += name
                true
            },
            legacyExists = { legacyExists },
            temperExists = { temperExists },
        )
    }

    @Test
    fun trainerDatabaseCreateIsNotTheProductionOpen() {
        val container = java.io.File("src/main/java/com/sinura/personaltrainer/AppContainer.kt")
            .takeIf { it.isFile }
            ?: java.io.File("app/src/main/java/com/sinura/personaltrainer/AppContainer.kt")
        val body = container.readText()
        assertTrue(body.contains("TemperDatabase.create"))
        assertFalse(body.contains("TrainerDatabase.create"))
        assertTrue(FoundationGeneration.FROZEN)
        assertFalse(TrainerDatabase::class.java.name.contains("missing"))
    }
}
