package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.activity.DiscardActivity
import com.sinura.personaltrainer.activity.StartLiveActivity
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityEditCopy
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        assertEquals(listOf("ex-squat"), summaries.single().stills.map { it.id })
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

    // ---------------------------------------------------------------------------------------
    // Finishing settles the planned day, so its reminders go with it (A3).
    // ---------------------------------------------------------------------------------------

    @Test
    fun completingAnActivityHandsItsPlannedDayOverToBeCleared() = runBlocking {
        // The third of A3's three gaps: this repository wrote DONE and told nobody, so the
        // reminder stayed scheduled and any notification already posted kept offering Skip and
        // Move for a session that was over.
        val cleared = mutableListOf<String>()
        val repo = ActivityRepository(database, onOccurrenceCompleted = { cleared += it })

        val write = repo.confirm(
            draft(
                ActivityOrigin.BACKDATED,
                ActivityStatus.COMPLETED,
                morning,
                listOf(run()),
            ).copy(occurrenceId = "occ-1"),
            now,
            ids(),
            JvmTime,
        )

        assertTrue(write is ActivityWrite.Accepted)
        assertEquals(listOf("occ-1"), cleared)
    }

    @Test
    fun finishingALiveActivityClearsItsPlannedDayToo() = runBlocking {
        val cleared = mutableListOf<String>()
        val repo = ActivityRepository(database, onOccurrenceCompleted = { cleared += it })
        val started = repo.confirm(
            draft(
                ActivityOrigin.LIVE,
                ActivityStatus.ACTIVE,
                morning,
                listOf(run()),
            ).copy(occurrenceId = "occ-1"),
            now,
            ids(),
            JvmTime,
        )
        assertTrue(started is ActivityWrite.Accepted)
        assertTrue("starting settles nothing", cleared.isEmpty())

        repo.completeLive((started as ActivityWrite.Accepted).session.id, now, JvmTime)

        assertEquals(listOf("occ-1"), cleared)
    }

    @Test
    fun aFailingReminderCleanupDoesNotUnsaveTheActivity() = runBlocking {
        // The commit is durable before the cleanup runs. A throw from WorkManager used to
        // escape as a save failure, and the composer's retry wrote the day twice.
        val repo = ActivityRepository(
            database = database,
            onOccurrenceCompleted = { error("WorkManager is unavailable") },
        )

        val write = repo.confirm(
            draft(
                ActivityOrigin.BACKDATED,
                ActivityStatus.COMPLETED,
                morning,
                listOf(run()),
            ).copy(occurrenceId = "occ-1"),
            now,
            ids(),
            JvmTime,
        )

        assertTrue(write.toString(), write is ActivityWrite.Accepted)
        assertEquals(1, database.activityDao().sessionCount())
        assertEquals("occ-1", repo.all().single().occurrenceId)
    }

    @Test
    fun aFailingCleanupAfterALiveFinishStillFinishes() = runBlocking {
        val repo = ActivityRepository(
            database = database,
            onOccurrenceCompleted = { error("WorkManager is unavailable") },
        )
        val started = repo.confirm(
            draft(
                ActivityOrigin.LIVE,
                ActivityStatus.ACTIVE,
                morning,
                listOf(run()),
            ).copy(occurrenceId = "occ-1"),
            now,
            ids(),
            JvmTime,
        )
        val liveId = (started as ActivityWrite.Accepted).session.id

        val finished = repo.completeLive(liveId, now, JvmTime)

        assertTrue(finished.toString(), finished is ActivityWrite.Accepted)
        assertNull(repo.getLive())
        assertEquals(ActivityStatus.COMPLETED, repo.get(liveId)?.status)
    }

    @Test
    fun cleanupCancellationStillPropagatesAfterTheCommit() = runBlocking {
        // Cancellation of the caller is not a cleanup failure and must not be swallowed
        // into an Accepted; the row is committed either way.
        val repo = ActivityRepository(
            database = database,
            onOccurrenceCompleted = { throw CancellationException("caller went away") },
        )
        val draft = draft(
            ActivityOrigin.BACKDATED,
            ActivityStatus.COMPLETED,
            morning,
            listOf(run()),
        ).copy(occurrenceId = "occ-1")

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.confirm(draft, now, ids(), JvmTime) }
        }
        assertEquals(1, database.activityDao().sessionCount())
    }

    @Test
    fun completedStrengthSetsFeedRecordsWithTheBlocksOwnSnapshot() = runBlocking {
        repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, evening, listOf(squat()), "Evening"),
            now,
            ids(),
            JvmTime,
        )
        // A live row's sets are not records yet.
        val liveSquat = squat().let { block ->
            block.copy(id = "blk-squat-live", sets = block.sets.map { it.copy(id = "set-live") })
        }
        repository.confirm(
            draft(ActivityOrigin.LIVE, ActivityStatus.ACTIVE, now, listOf(liveSquat)),
            now,
            ids(),
            JvmTime,
        )

        val health = repository.observeRecordSetsHealth().first()
        val sets = (health as DataHealth.Available).value
        val only = sets.single()
        assertEquals("ex-squat", only.exerciseId)
        assertEquals("Squat", only.exerciseName)
        assertEquals(LoadClass.LOADED, only.loadClass)
        assertEquals(100.0, only.set.weightKg, 0.0)
        assertEquals(morning.instantMillis, only.set.completedAt)
    }

    @Test
    fun anActivityWithNoPlannedDayClearsNothing() = runBlocking {
        val cleared = mutableListOf<String>()
        val repo = ActivityRepository(database, onOccurrenceCompleted = { cleared += it })

        repo.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, morning, listOf(run())),
            now,
            ids(),
            JvmTime,
        )

        assertTrue(cleared.isEmpty())
    }

    @Test
    fun notesOnACompletedActivityBumpRevisionAndKeepBlockIds() = runBlocking {
        val write = repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, evening, listOf(squat()), "Evening"),
            now,
            ids(),
            JvmTime,
        ) as ActivityWrite.Accepted
        val original = write.session
        val blockId = original.strengthBlocks.single().id
        val setId = original.strengthBlocks.single().sets.single().id

        val updated = repository.updateCompletedNotes(original.id, "  felt strong  ", now.instantMillis)
        assertTrue(updated is ActivityWrite.Accepted)
        val session = (updated as ActivityWrite.Accepted).session
        assertEquals("felt strong", session.notes)
        assertEquals(original.revision + 1, session.revision)
        assertEquals(blockId, session.strengthBlocks.single().id)
        assertEquals(setId, session.strengthBlocks.single().sets.single().id)
        assertEquals(100.0, session.strengthBlocks.single().sets.single().weightKg, 0.0)

        val reread = checkNotNull(repository.get(original.id))
        assertEquals("felt strong", reread.notes)
        assertEquals(session.revision, reread.revision)
        assertEquals(blockId, reread.strengthBlocks.single().id)
    }

    @Test
    fun deleteCompletedRemovesTheRowAndLiveWritesAreRefused() = runBlocking {
        val completed = repository.confirm(
            draft(ActivityOrigin.BACKDATED, ActivityStatus.COMPLETED, evening, listOf(squat()), "Evening"),
            now,
            ids(),
            JvmTime,
        ) as ActivityWrite.Accepted
        val deleted = repository.deleteCompleted(completed.session.id)
        assertTrue(deleted is ActivityWrite.Accepted)
        assertNull(repository.get(completed.session.id))

        val live = StartLiveActivity(repository, ids(), JvmTime)("Live run", listOf(run().copy(id = "blk-live")), now)
        val liveId = (live as ActivityWrite.Accepted).session.id
        val liveNotes = repository.updateCompletedNotes(liveId, "nope", now.instantMillis)
        assertEquals(ActivityEditCopy.NOTES_LIVE_REFUSED, (liveNotes as ActivityWrite.Rejected).reason)
        val liveDelete = repository.deleteCompleted(liveId)
        assertEquals(ActivityEditCopy.DELETE_LIVE_REFUSED, (liveDelete as ActivityWrite.Rejected).reason)
        assertEquals(liveId, repository.getLive()?.id)
    }

    @Test
    fun setRepairAndRepeatAreRefusedOnActivities() = runBlocking {
        assertEquals(
            ActivityEditCopy.SET_REPAIR_REFUSED,
            (repository.updateStrengthSet("set-1", 200.0, 5, null, false) as ActivityWrite.Rejected).reason,
        )
        assertEquals(
            ActivityEditCopy.REPEAT_REFUSED,
            (repository.repeatCompleted("any") as ActivityWrite.Rejected).reason,
        )
        assertEquals(
            ActivityEditCopy.GONE,
            (repository.deleteCompleted("missing") as ActivityWrite.Rejected).reason,
        )
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
