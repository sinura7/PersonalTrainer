package com.sinura.personaltrainer.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cached Drive folder ids can point at a trashed folder. Drive still
 * returns 200 with `trashed: true`. Treating that as present writes the
 * next backup into the trash (N11).
 */
class DriveFolderJsonTest {
    @Test
    fun liveFolderWithExplicitFalseIsUsable() {
        assertTrue(DriveFolderJson.isUsable("""{"id":"folder-1","trashed":false}"""))
    }

    @Test
    fun omittedTrashedDefaultsToPresent() {
        // Drive may omit a false boolean. An id with no trashed flag is still a live folder.
        assertTrue(DriveFolderJson.isUsable("""{"id":"folder-1"}"""))
    }

    @Test
    fun trashedFolderIsNotUsable() {
        assertFalse(DriveFolderJson.isUsable("""{"id":"folder-1","trashed":true}"""))
    }

    @Test
    fun missingIdIsNotUsable() {
        assertFalse(DriveFolderJson.isUsable("""{"trashed":false}"""))
        assertFalse(DriveFolderJson.isUsable("""{"id":"","trashed":false}"""))
    }

    @Test
    fun junkIsNotUsable() {
        assertFalse(DriveFolderJson.isUsable("not-json"))
        assertFalse(DriveFolderJson.isUsable("[]"))
        assertFalse(DriveFolderJson.isUsable("{}"))
    }
}
