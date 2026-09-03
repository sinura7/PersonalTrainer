package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.testutil.FrozenTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DayBlocksTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        val frozenMs = ZonedDateTime.of(2026, 9, 3, 21, 0, 0, 0, ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            time = FrozenTime(frozenMs, "UTC"),
        )
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun addCardioAtNinePmDoesNotPersistHourSevenOrEighteen() = runBlocking {
        val thursday = CivilDate(2026, 9, 3).epochDay
        DayBlocks.addCardio(
            planner = deps.plannerRepository,
            preferences = deps.preferencesRepository,
            epochDay = thursday,
            type = CardioType.WALK,
            once = false,
            todayEpochDay = thursday,
            nowMinutes = 21 * 60,
        )
        val cardio = deps.plannerRepository.rules().single {
            it.modality == ScheduleModality.CARDIO
        }
        assertTrue(cardio.hour !in setOf(7, 18))
        assertEquals(22, cardio.hour)
    }
}
