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
    }

    @Test
    fun fingerprintChangesWhenSessionsChange() {
        val empty = RestoreJournal.fingerprint(AuthoredInventory.EMPTY, firstSessionId = null)
        val authored = RestoreJournal.fingerprint(authoredSample())
        assertTrue(empty != authored)
        assertTrue(authored.contains("s1"))
    }
}
