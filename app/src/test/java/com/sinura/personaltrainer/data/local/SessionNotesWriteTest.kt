package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.WorkoutRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A notes write touches the notes and nothing else.
 *
 * Both writers of `workout_sessions` used to rewrite the whole row from an entity they had
 * read moments earlier, which makes every other column a hostage to whatever wrote in between.
 * Notes and Finish are the pair that actually meet on the phone: type a note, close the sheet,
 * and tap Finish inside the notes field's 400 ms debounce. The notes write is holding a row it
 * read while the session was still running, so writing it back puts `finishedAt = null` over
 * the finish that landed in between. The session un-finishes while the summary is already
 * saying it is done.
 *
 * The test writes that interleaving out by hand — the stale snapshot IS the read the debounce
 * had already done — because the defect is not "notes are wrong", it is "notes carried five
 * other columns with them".
 */
@RunWith(RobolectricTestRunner::class)
class SessionNotesWriteTest {
    private lateinit var database: TrainerDatabase

    private val session = WorkoutSessionEntity(
        id = "session-1",
        routineId = null,
        routineName = "Push Day",
        date = 1_756_000_000_000L,
        notes = "",
        durationMinutes = 0,
        startedAt = 1_756_000_000_000L,
        finishedAt = null,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** The interleaving itself: a stale read, a finish, then the notes write landing last. */
    @Test
    fun aNotesWriteHoldingAStaleRowDoesNotUndoAFinishThatLandedFirst() = runBlocking {
        val dao = database.workoutDao()
        dao.upsertSession(session)

        // What the debounced notes write read: the session, still running.
        val asReadByTheNotesWrite = checkNotNull(dao.getSessionRow(session.id))
        assertNull(asReadByTheNotesWrite.finishedAt)

        // Finish lands in between.
        dao.updateSession(
            asReadByTheNotesWrite.copy(durationMinutes = 42, finishedAt = 1_756_000_600_000L),
        )

        // The notes write lands last, still holding the row it read.
        dao.updateSessionNotes(id = session.id, notes = "felt strong")

        val stored = checkNotNull(dao.getSessionRow(session.id))
        assertEquals("felt strong", stored.notes)
        assertNotNull("the finish must survive a later notes write", stored.finishedAt)
        assertEquals(1_756_000_600_000L, stored.finishedAt)
        assertEquals(42, stored.durationMinutes)
    }

    /**
     * The negative control, kept as a test so the claim above is not taken on trust: the same
     * interleaving through the whole-row writer really does undo the finish. This is what the
     * repository did until [WorkoutDao.updateSessionNotes] existed.
     */
    @Test
    fun theWholeRowWriterIsWhatUndidTheFinish() = runBlocking {
        val dao = database.workoutDao()
        dao.upsertSession(session)
        val asReadByTheNotesWrite = checkNotNull(dao.getSessionRow(session.id))
        dao.updateSession(
            asReadByTheNotesWrite.copy(durationMinutes = 42, finishedAt = 1_756_000_600_000L),
        )

        dao.updateSession(asReadByTheNotesWrite.copy(notes = "felt strong"))

        val stored = checkNotNull(dao.getSessionRow(session.id))
        assertEquals("felt strong", stored.notes)
        assertNull("this is the defect the one-column write removes", stored.finishedAt)
        assertEquals(0, stored.durationMinutes)
    }

    /** A row that is gone matches nothing, which is what the repository's early return did. */
    @Test
    fun aNotesWriteForASessionThatIsGoneIsANoOp() = runBlocking {
        val dao = database.workoutDao()
        dao.updateSessionNotes(id = "no-such-session", notes = "felt strong")
        assertNull(dao.getSessionRow("no-such-session"))
    }

    /**
     * The repository must reach for the one-column write, not the whole-row one.
     *
     * The DAO tests above prove the two writers behave differently; this proves the repository
     * picks the safe one. Without it a later edit could quietly restore the read-modify-write
     * and every assertion above would still pass, because none of them goes through
     * [WorkoutRepository].
     */
    @Test
    fun theRepositoryNeverWritesTheWholeRowToChangeNotes() = runBlocking {
        val spy = WholeRowWatchingDao(database.workoutDao())
        val repository = WorkoutRepository(database = database, workoutDao = spy)
        spy.upsertSession(session)

        repository.updateSessionNotes(session.id, "  felt strong  ")

        assertEquals(0, spy.wholeRowWrites)
        val stored = checkNotNull(database.workoutDao().getSessionRow(session.id))
        assertEquals("felt strong", stored.notes)
    }

    /** Counts whole-row session writes and passes everything else straight through. */
    private class WholeRowWatchingDao(
        private val delegate: WorkoutDao,
    ) : WorkoutDao by delegate {
        var wholeRowWrites = 0

        override suspend fun updateSession(session: WorkoutSessionEntity) {
            wholeRowWrites += 1
            delegate.updateSession(session)
        }
    }
}
