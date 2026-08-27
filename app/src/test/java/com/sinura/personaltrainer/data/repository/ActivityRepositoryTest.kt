package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.activity.DiscardActivity
import com.sinura.personaltrainer.activity.StartLiveActivity
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityRepositoryTest {
    private lateinit var database: TemperDatabase
    private lateinit var repository: ActivityRepository
    private val now = JvmTime.resolveLocal(
        CivilDateTime(CivilDate(2026, 8, 21), hour = 9, minute = 0),
        "Asia/Tokyo",
    )
    private val morning = JvmTime.resolveLocal(
        CivilDateTime(CivilDate(2026, 8, 20), hour = 7, minute = 0),
        "Asia/Tokyo",
    )
    private val evening = JvmTime.resolveLocal(
        CivilDateTime(CivilDate(2026, 8, 20), hour = 19, minute = 0),
        "Asia/Tokyo",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ActivityRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun oneLiveIsATransactionalInvariant() = runBlocking {
        val start = StartLiveActivity(repository, ids(), JvmTime)
        val first = start("Live run", listOf(run()), now)
        assertTrue(first is ActivityWrite.Accepted)
        val second = start("Second", listOf(squat()), now)
        assertTrue(second is ActivityWrite.Rejected)
        assertEquals("One live activity at a time.", (second as ActivityWrite.Rejected).reason)
        assertEquals(1, repository.all().size)
    }

    @Test
    fun uniqueLiveTokenRejectsASecondActiveRow() = runBlocking {
        val accepted = repository.confirm(
            draft(ActivityOrigin.LIVE, ActivityStatus.ACTIVE, now, listOf(run())),
            now,
            ids(),
            JvmTime,
        )
        assertTrue(accepted is ActivityWrite.Accepted)
        val live = (accepted as ActivityWrite.Accepted).session
        val clone = live.copy(id = "other", title = "Clone")
        var failed = false
        try {
            ActivityBackupIo.insertSession(database.activityDao(), clone)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(1, database.activityDao().sessionCount())
    }

    @Test
    fun twoCompletedActivitiesShareALocalDate() = runBlocking {
        val ids = ids()
        val cardio = repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, morning, listOf(run()), "Morning"),
            now,
            ids,
            JvmTime,
        )
        val lift = repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, evening, listOf(squat()), "Evening"),
            now,
            ids,
            JvmTime,
        )
        assertTrue(cardio is ActivityWrite.Accepted)
        assertTrue(lift is ActivityWrite.Accepted)
        val day = repository.completedOn(morning.localEpochDay)
        assertEquals(2, day.size)
    }

    @Test
    fun completedOnReadsOnlyThatLocalDate() = runBlocking {
        val ids = ids()
        repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, morning, listOf(run()), "Morning"),
            now,
            ids,
            JvmTime,
        )
        val yesterday = JvmTime.resolveLocal(
            CivilDateTime(CivilDate(2026, 8, 19), hour = 12, minute = 0),
            "Asia/Tokyo",
        )
        repository.confirm(
            draft(
                ActivityOrigin.BACKDATED,
                ActivityStatus.COMPLETED,
                yesterday,
                listOf(run().copy(id = "blk-old")),
                "Yesterday",
            ),
            now,
            ids,
            JvmTime,
        )
        assertEquals(1, repository.completedOn(morning.localEpochDay).size)
        assertEquals(1, repository.completedOn(yesterday.localEpochDay).size)
        assertEquals("Yesterday", repository.completedOn(yesterday.localEpochDay).single().title)
    }

    @Test
    fun completedSummariesCarryVolumeWithoutLoadingOtherDaysAsGraphs() = runBlocking {
        repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, evening, listOf(squat()), "Evening"),
            now,
            ids(),
            JvmTime,
        )
        val summaries = repository.observeCompletedSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(500.0, summaries.single().volumeKg, 0.0001)
        assertEquals(1, summaries.single().workingSets)
        assertEquals(evening.localEpochDay, summaries.single().localEpochDay)
    }

    @Test
    fun discardRemovesOnlyTheLiveRow() = runBlocking {
        val live = repository.confirm(
            draft(ActivityOrigin.LIVE, ActivityStatus.ACTIVE, now, listOf(run())),
            now,
            ids(),
            JvmTime,
        ) as ActivityWrite.Accepted
        DiscardActivity(repository)(live.session.id)
        assertNull(repository.getLive())
        assertEquals(0, repository.all().size)
    }

    @Test
    fun futureDateIsRejectedAndWritesNothing() = runBlocking {
        val tomorrow = JvmTime.resolveLocal(
            CivilDateTime(CivilDate(2026, 8, 22), hour = 7, minute = 0),
            "Asia/Tokyo",
        )
        val write = repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, tomorrow, listOf(run())),
            now,
            ids(),
            JvmTime,
        )
        assertTrue(write is ActivityWrite.Rejected)
        assertEquals(0, repository.all().size)
    }

    private fun draft(
        origin: ActivityOrigin,
        status: ActivityStatus,
        start: CapturedCivilTime,
        blocks: List<com.sinura.personaltrainer.domain.ActivityBlock>,
        title: String = "Session",
    ) = ActivityDraft(
        status = status,
        origin = origin,
        title = title,
        performedStart = start,
        blocks = blocks,
    )

    private fun run() = CardioBlock(
        id = "blk-run",
        sortOrder = 0,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = 480,
        movingSeconds = 480,
        distanceMeters = 1_500.0,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = 6,
        routeRef = null,
    )

    private fun squat() = StrengthBlock(
        id = "blk-squat",
        sortOrder = 0,
        exerciseId = "ex-squat",
        exerciseName = "Squat",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.BARBELL,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet(
                id = "set-1",
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAtMs = morning.instantMillis,
            ),
        ),
    )

    private fun ids(): IdPort {
        var n = 0
        return IdPort { "id-${++n}" }
    }
}
