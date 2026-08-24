package com.sinura.personaltrainer.data.backup

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SafetySnapshotStoreTest {
    private lateinit var root: File
    private var now = 1_755_000_000_000L

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "safety-snap-${System.nanoTime()}")
            .also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    private fun store(dir: File = File(root, "snaps").also { it.mkdirs() }): SafetySnapshotStore =
        SafetySnapshotStore(dir) { now }

    @Test
    fun writeListReadDelete() {
        val store = store()
        val document = authoredSample()
        val expected = AuthoredInventory.fromDocument(document)
        val json = BackupJson.encode(document)

        val meta = store.writeVerified(json, expected)
        assertTrue(SafetySnapshot.isSafeId(meta.id))
        assertFalse(meta.id.contains("/") || meta.id.contains("\\"))
        assertEquals(now, meta.createdAtMillis)
        assertEquals(expected, meta.authored)
        assertEquals(SafetySnapshotMeta.TITLE, meta.title)
        assertTrue(meta.subtitle("today").contains("1 session"))
        assertTrue(meta.subtitle("today").contains("today"))

        val listed = store.list()
        assertEquals(1, listed.size)
        assertEquals(meta, listed.single())
        assertEquals(json, store.readJson(meta.id))

        store.delete(meta.id)
        assertTrue(store.list().isEmpty())
        try {
            store.readJson(meta.id)
            fail("deleted id should be gone")
        } catch (thrown: BackupException) {
            assertEquals(SafetySnapshot.NOT_FOUND, thrown.message)
        }
    }

    @Test
    fun pruneKeepsNewestThree() {
        val store = store()
        val document = authoredSample()
        val expected = AuthoredInventory.fromDocument(document)
        val json = BackupJson.encode(document)
        val ids = mutableListOf<String>()
        repeat(4) {
            ids += store.writeVerified(json, expected).id
            now += 1_000
        }
        val remaining = store.list().map { it.id }
        assertEquals(3, remaining.size)
        assertFalse(remaining.contains(ids.first()))
        assertTrue(remaining.containsAll(ids.drop(1)))
    }

    @Test
    fun rejectsPathTraversalIds() {
        val store = store()
        val sneaky = listOf(
            "../pre-restore-1.json",
            "..\\pre-restore-1.json",
            "pre-restore-1.json/../../secret",
            "/tmp/pre-restore-1.json",
            "pre-restore-1.json.tmp",
        )
        sneaky.forEach { id ->
            try {
                store.readJson(id)
                fail("should reject $id")
            } catch (thrown: BackupException) {
                assertEquals(SafetySnapshot.BAD_ID, thrown.message)
            }
            try {
                store.delete(id)
                fail("should reject delete $id")
            } catch (thrown: BackupException) {
                assertEquals(SafetySnapshot.BAD_ID, thrown.message)
            }
        }
    }

    @Test
    fun failedVerifyDoesNotLeaveABadFile() {
        val dir = File(root, "clean").also { it.mkdirs() }
        val store = store(dir)
        try {
            store.writeVerified(BackupJson.encode(catalogOnly()), AuthoredInventory.PRESENT)
            fail("verify should fail")
        } catch (thrown: BackupException) {
            assertEquals(SafetySnapshot.VERIFY_FAILED, thrown.message)
        }
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun missingDirAbortsWrite() {
        val blocker = File(root, "not-a-dir").apply { writeText("nope") }
        try {
            store(blocker).writeVerified(
                BackupJson.encode(catalogOnly()),
                AuthoredInventory.EMPTY,
            )
            fail("file-as-dir should abort")
        } catch (thrown: BackupException) {
            assertEquals(SafetySnapshot.MISSING_DIR, thrown.message)
        }
    }
}
