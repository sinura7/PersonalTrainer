package com.sinura.personaltrainer.data.backup

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestoreJournalStoreTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "restore-journal-${System.nanoTime()}")
            .also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun stageMarkClear() {
        val store = RestoreJournalStore(root)
        val incoming = BackupJson.encode(authoredSample())
        store.stage(
            RestoreJournalRecord(
                phase = RestoreJournal.STAGED,
                sourceName = "phone.json",
                snapshotId = "pre-restore-1.json",
                beforeFingerprint = "before",
                afterFingerprint = RestoreJournal.fingerprint(authoredSample()),
            ),
            incoming,
        )
        assertTrue(store.isOpen())
        assertEquals(RestoreJournal.STAGED, store.read()?.phase)
        assertEquals(incoming, store.readIncoming())

        store.mark(RestoreJournal.WIPING)
        assertEquals(RestoreJournal.WIPING, store.read()?.phase)
        store.mark(RestoreJournal.ROOM)
        assertEquals(RestoreJournal.ROOM, store.read()?.phase)

        store.clear()
        assertFalse(store.isOpen())
        assertNull(store.read())
        // Both files, not just the one isOpen looks at. The incoming copy is the owner's
        // entire training history in the clear.
        assertFalse(File(root, RestoreJournal.INCOMING_FILE).exists())
        assertFalse(File(root, RestoreJournal.STATE_FILE).exists())
    }

    @Test
    fun anOrphanedIncomingFileIsSweptOnTheNextRead() {
        // No state file means no restore to finish, so nothing will ever come back for this —
        // it is a decrypted copy of everything the owner has ever logged, sitting in app
        // storage, invisible to isOpen and to every recovery pass. A crash between clear()'s
        // two deletes used to be able to leave one, which is also why those two deletes are
        // now in the other order.
        val orphan = File(root, RestoreJournal.INCOMING_FILE)
        orphan.writeText("""{"version":2,"sessions":[{"id":"s1"}]}""")
        assertTrue(orphan.isFile)

        val store = RestoreJournalStore(root)
        assertNull(store.read())

        assertFalse("a decrypted backup must not outlive its journal", orphan.exists())
    }

    @Test
    fun aStagedJournalKeepsItsIncomingFile() {
        // The sweep must only take orphans. Staging writes the incoming copy first and the
        // state file second, and a read between the two would be a read of a journal that is
        // not open yet — so the guard is "no state file", not "no state file yet".
        val store = RestoreJournalStore(root)
        store.stage(
            RestoreJournalRecord(
                phase = RestoreJournal.STAGED,
                sourceName = "phone.json",
                snapshotId = "pre-restore-1.json",
                beforeFingerprint = "before",
                afterFingerprint = RestoreJournal.fingerprint(authoredSample()),
            ),
            BackupJson.encode(authoredSample()),
        )
        assertEquals(RestoreJournal.STAGED, store.read()?.phase)
        assertTrue(File(root, RestoreJournal.INCOMING_FILE).isFile)
    }

    // -------------------------------------------------------------------------------------
    // What the owner is told when a restore throws (A4).
    // -------------------------------------------------------------------------------------

    @Test
    fun aRestoreThatFailedBeforeTheWipeSaysNothingWasChanged() {
        // The symptom: a restore that died staging told the owner Temper "is finishing it from
        // the copy already on this phone" — a recovery that is not going to happen, about data
        // that was never touched. The journal writes throw exactly that message, so the rule
        // has to refuse to repeat it rather than trusting whatever came up from underneath.
        assertEquals(
            RestoreJournal.NOTHING_CHANGED,
            RestoreJournal.commitFailureMessage(RestoreJournal.STAGED, RestoreJournal.INTERRUPTED),
        )
        assertEquals(
            RestoreJournal.NOTHING_CHANGED,
            RestoreJournal.commitFailureMessage(phase = null, reported = RestoreJournal.INTERRUPTED),
        )
        assertEquals(
            RestoreJournal.NOTHING_CHANGED,
            RestoreJournal.commitFailureMessage(RestoreJournal.STAGED, reported = null),
        )
        assertEquals(
            RestoreJournal.NOTHING_CHANGED,
            RestoreJournal.commitFailureMessage(RestoreJournal.STAGED, reported = "  "),
        )
    }

    @Test
    fun aFailureWithSomethingUsefulToSayKeepsSayingIt() {
        // Refusing INTERRUPTED must not flatten every other reason into one sentence: a
        // password or validation failure carries the only wording that helps.
        assertEquals(
            "That password doesn't open this file.",
            RestoreJournal.commitFailureMessage(
                phase = null,
                reported = "That password doesn't open this file.",
            ),
        )
    }

    @Test
    fun onlyThePhasesPastTheWipeMaySayDataWasReplaced() {
        listOf(RestoreJournal.WIPING, RestoreJournal.ROOM, RestoreJournal.PREFS).forEach { phase ->
            assertEquals(
                phase,
                RestoreJournal.RECOVERED_MIXED,
                RestoreJournal.commitFailureMessage(phase, reported = "anything at all"),
            )
        }
    }

    @Test
    fun fingerprintChangesWhenSessionsChange() {
        val empty = RestoreJournal.fingerprint(AuthoredInventory.EMPTY, firstSessionId = null)
        val authored = RestoreJournal.fingerprint(authoredSample())
        assertTrue(empty != authored)
        assertTrue(authored.contains("s1"))
    }
}
