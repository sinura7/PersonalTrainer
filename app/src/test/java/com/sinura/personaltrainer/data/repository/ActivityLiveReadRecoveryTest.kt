package com.sinura.personaltrainer.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.activity.StartLiveActivity
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.util.JvmTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual Room reads with a sibling kept attached throughout failure and retry. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActivityLiveReadRecoveryTest {
    private lateinit var database: TemperDatabase
    private lateinit var reads: RecoveryLiveDao
    private lateinit var repository: ActivityRepository
    private lateinit var sharedScope: CoroutineScope

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), TemperDatabase::class.java,
        ).allowMainThreadQueries().build()
        sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        reads = RecoveryLiveDao(database.activityDao())
        repository = ActivityRepository(database = database, dao = reads, sharedScope = sharedScope)
    }

    @After
    fun tearDown() {
        sharedScope.cancel()
        database.close()
    }

    @Test
    fun initialFailureIsNotAnEmptyLiveRowAndRetryRestartsTheActualRead() = runBlocking {
        reads.failRows = true
        val seen = mutableListOf<DataHealth<ActivitySession?>>()
        val sibling = launch { repository.observeLiveHealth().collect { seen += it } }
        awaitUntil { seen.lastOrNull() is DataHealth.Unavailable }
        val before = database.activityDao().getAllGraphs()
        val attempts = reads.subscriptions.get()
        val hold = CompletableDeferred<Unit>()
        reads.holdRows = hold
        reads.failRows = false
        val requested = repository.retryLiveHealth()
        assertEquals(attempts, reads.subscriptions.get())
        val retry = async { requested.first() }
        awaitUntil { reads.subscriptions.get() > attempts }
        assertFalse("old Unavailable replay must not satisfy a retry", retry.isCompleted)
        hold.complete(Unit)
        val recovered = withTimeout(TestWaits.FLOW_MS) { retry.await() }
        assertEquals(DataHealth.Available(null), recovered)
        awaitUntil { seen.lastOrNull() is DataHealth.Available }
        assertEquals(before, database.activityDao().getAllGraphs())
        sibling.cancelAndJoin()
    }

    @Test
    fun successfulNullFollowedByFailureIsDegradedNullAndRetryNeverWrites() = runBlocking {
        val seen = mutableListOf<DataHealth<ActivitySession?>>()
        val sibling = launch { repository.observeLiveHealth().collect { seen += it } }
        awaitUntil { seen.isNotEmpty() }
        assertEquals(DataHealth.Available(null), seen.last())
        reads.failRows = true
        // Intentional fixture write invalidates the same Room read; its inventory is then
        // frozen before retry so this setup write is never mistaken for a retry side effect.
        startLive()
        awaitUntil { seen.lastOrNull() is DataHealth.Degraded }
        assertNull((seen.last() as DataHealth.Degraded).lastValue)
        val before = database.activityDao().getAllGraphs()
        reads.failRows = false
        val recovered = withTimeout(TestWaits.FLOW_MS) { repository.retryLiveHealth().first() }
        assertTrue(recovered is DataHealth.Available)
        assertEquals("Live run", recovered.presentValue()?.title)
        assertEquals(before, database.activityDao().getAllGraphs())
        sibling.cancelAndJoin()
    }

    @Test
    fun graphReadIsSharedAndFreshRetryCannotUseTheOldSuccessfulReplay() = runBlocking {
        val live = startLive()
        val firstSeen = mutableListOf<DataHealth<ActivitySession?>>()
        val secondSeen = mutableListOf<DataHealth<ActivitySession?>>()
        val first = launch { repository.observeLiveHealth().collect { firstSeen += it } }
        val second = launch { repository.observeLiveHealth().collect { secondSeen += it } }
        awaitUntil { firstSeen.isNotEmpty() && secondSeen.isNotEmpty() }
        assertEquals(1, reads.subscriptions.get())
        assertEquals(1, reads.graphReads.get())
        reads.failGraphs = true
        val row = checkNotNull(database.activityDao().getSessionRow(live.id))
        database.activityDao().updateSession(row.copy(title = "Changed fixture", revision = row.revision + 1))
        awaitUntil { firstSeen.lastOrNull() is DataHealth.Degraded && secondSeen.lastOrNull() is DataHealth.Degraded }
        assertEquals("Live run", firstSeen.last().presentValue()?.title)
        val before = database.activityDao().getAllGraphs()
        val priorGraphs = reads.graphReads.get()
        val priorSubscriptions = reads.subscriptions.get()
        val hold = CompletableDeferred<Unit>()
        reads.holdGraphs = hold
        reads.failGraphs = false
        val retry = async { repository.retryLiveHealth().first() }
        awaitUntil { reads.subscriptions.get() > priorSubscriptions && reads.graphReads.get() > priorGraphs }
        assertFalse("old successful/degraded replay must not satisfy a retry", retry.isCompleted)
        hold.complete(Unit)
        val recovered = withTimeout(TestWaits.FLOW_MS) { retry.await() }
        assertTrue(recovered is DataHealth.Available)
        assertEquals("Changed fixture", recovered.presentValue()?.title)
        awaitUntil { secondSeen.lastOrNull() is DataHealth.Available }
        assertEquals(priorGraphs + 1, reads.graphReads.get())
        assertEquals(before, database.activityDao().getAllGraphs())
        first.cancelAndJoin()
        second.cancelAndJoin()
    }

    @Test
    fun cancellingARetryCollectorDoesNotCreateAReadFailureForItsSibling() = runBlocking {
        val seen = mutableListOf<DataHealth<ActivitySession?>>()
        val sibling = launch { repository.observeLiveHealth().collect { seen += it } }
        awaitUntil { seen.isNotEmpty() }
        val before = database.activityDao().getAllGraphs()
        val hold = CompletableDeferred<Unit>()
        reads.holdRows = hold
        val subscriptions = reads.subscriptions.get()
        val retry = launch { repository.retryLiveHealth().collect { } }
        awaitUntil { reads.subscriptions.get() > subscriptions }
        retry.cancelAndJoin()
        hold.complete(Unit)
        awaitUntil { seen.size >= 2 }
        assertTrue(seen.all { it is DataHealth.Available })
        assertEquals(before, database.activityDao().getAllGraphs())
        sibling.cancelAndJoin()
    }

    private suspend fun startLive(): ActivitySession {
        var next = 0
        val now = JvmTime.resolveLocal(CivilDateTime(CivilDate(2026, 10, 8), 9, 0), "UTC")
        val result = StartLiveActivity(repository, IdPort { "live-recovery-${++next}" }, JvmTime)(
            "Live run",
            listOf(CardioBlock(
                id = "run-block", sortOrder = 0, type = CardioType.RUN, indoor = false,
                elapsedSeconds = 480, movingSeconds = 480, distanceMeters = 1500.0,
                elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = 6, routeRef = null,
            )),
            now,
        )
        return (result as ActivityWrite.Accepted).session
    }

    private suspend fun awaitUntil(predicate: () -> Boolean) = withTimeout(TestWaits.FLOW_MS) {
        while (!predicate()) delay(10)
    }
}

private class RecoveryLiveDao(private val delegate: ActivityDao) : ActivityDao by delegate {
    val subscriptions = AtomicInteger()
    val graphReads = AtomicInteger()
    @Volatile var failRows = false
    @Volatile var failGraphs = false
    @Volatile var holdRows: CompletableDeferred<Unit>? = null
    @Volatile var holdGraphs: CompletableDeferred<Unit>? = null

    override fun observeLive(): Flow<ActivitySessionEntity?> = flow {
        subscriptions.incrementAndGet()
        emitAll(delegate.observeLive().map { row ->
            holdRows?.await()
            check(!failRows) { "Room live read failed" }
            row
        })
    }

    override suspend fun getSessionGraph(id: String): ActivitySessionGraph? {
        graphReads.incrementAndGet()
        holdGraphs?.await()
        check(!failGraphs) { "Room graph read failed" }
        return delegate.getSessionGraph(id)
    }
}
