package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.Instant

/**
 * `listBackups` asked Drive for one page of 50 and reported it as the whole folder:
 * the 51st-newest backup and everything older was unreachable from Settings, and
 * "Found 50 backups." counted the page, not the folder (R13). Drive also answers with
 * short pages and with empty pages that still carry a token, so the only sign that a
 * listing is finished is a page with no `nextPageToken`.
 *
 * The client is driven through a fake [DriveHttp] that serves canned pages and records
 * every URL. No socket: the request sequence is the thing under test.
 */
class DriveRestClientTest {
    @Test
    fun threePagesAreWalkedInOrderAndEveryFileComesBack() {
        val all = backupFiles(from = 0, count = 101)
        val http = RecordingDriveHttp(
            listOf(
                { pageJson(files = all.subList(0, 50), nextPageToken = "page-2") },
                { pageJson(files = all.subList(50, 100), nextPageToken = "page-3") },
                { pageJson(files = all.subList(100, 101), nextPageToken = null) },
            ),
        )

        val listing = list(http)

        assertEquals(all, listing.files)
        assertFalse(listing.truncated)
        assertEquals(3, http.urls.size)
        assertNull(queryParam(http.urls[0], "pageToken"))
        assertEquals("page-2", queryParam(http.urls[1], "pageToken"))
        assertEquals("page-3", queryParam(http.urls[2], "pageToken"))
    }

    @Test
    fun aShortFirstPageWithATokenIsNotTheEnd() {
        // Drive tokens carry base64 padding and slashes; they must travel encoded.
        val token = "CAES/abc=="
        val http = RecordingDriveHttp(
            listOf(
                { pageJson(files = backupFiles(from = 0, count = 3), nextPageToken = token) },
                { pageJson(files = backupFiles(from = 3, count = 2), nextPageToken = null) },
            ),
        )

        val listing = list(http)

        assertEquals(backupFiles(from = 0, count = 5), listing.files)
        assertFalse(listing.truncated)
        assertEquals(2, http.urls.size)
        assertTrue(http.urls[1].contains("&pageToken=" + URLEncoder.encode(token, "UTF-8")))
        assertEquals(token, queryParam(http.urls[1], "pageToken"))
    }

    @Test
    fun anEmptyPageThatCarriesATokenIsFollowed() {
        val http = RecordingDriveHttp(
            listOf(
                { pageJson(files = backupFiles(from = 0, count = 2), nextPageToken = "t2") },
                { pageJson(files = emptyList(), nextPageToken = "t3") },
                // No `files` key at all, which Drive is also allowed to do.
                { """{"nextPageToken":"t4"}""" },
                { pageJson(files = backupFiles(from = 2, count = 1), nextPageToken = null) },
            ),
        )

        val listing = list(http)

        assertEquals(backupFiles(from = 0, count = 3), listing.files)
        assertFalse(listing.truncated)
        assertEquals(
            listOf(null, "t2", "t3", "t4"),
            http.urls.map { queryParam(it, "pageToken") },
        )
    }

    @Test
    fun thePageCapStopsAskingAndSaysTheListingIsCut() {
        // One more page than the cap, every one offering another.
        val pages = (0 until DriveRestClient.MAX_LIST_PAGES + 1).map { index ->
            { pageJson(files = backupFiles(from = index, count = 1), nextPageToken = "t${index + 1}") }
        }
        val http = RecordingDriveHttp(pages)

        val listing = list(http)

        assertTrue(listing.truncated)
        assertEquals(DriveRestClient.MAX_LIST_PAGES, http.urls.size)
        assertEquals(backupFiles(from = 0, count = DriveRestClient.MAX_LIST_PAGES), listing.files)
    }

    @Test
    fun theFileCapStopsAskingAndSaysTheListingIsCut() {
        // Eleven full pages: 550 files, every page with a token. The cap is 500.
        val pages = (0 until 11).map { index ->
            { pageJson(files = backupFiles(from = index * 50, count = 50), nextPageToken = "t${index + 1}") }
        }
        val http = RecordingDriveHttp(pages)

        val listing = list(http)

        assertTrue(listing.truncated)
        assertEquals(backupFiles(from = 0, count = DriveRestClient.MAX_LISTED_BACKUPS), listing.files)
        assertEquals(DriveRestClient.MAX_LISTED_BACKUPS / 50, http.urls.size)
    }

    @Test
    fun exactlyTheFileCapWithNoTokenLeftIsComplete() {
        val pages = (0 until 10).map { index ->
            {
                pageJson(
                    files = backupFiles(from = index * 50, count = 50),
                    nextPageToken = if (index < 9) "t${index + 1}" else null,
                )
            }
        }
        val http = RecordingDriveHttp(pages)

        val listing = list(http)

        assertFalse(listing.truncated)
        assertEquals(DriveRestClient.MAX_LISTED_BACKUPS, listing.files.size)
        assertEquals(10, http.urls.size)
    }

    @Test
    fun aPageThatOverflowsTheFileCapIsCutAtTheCap() {
        // Nine pages of 50 and then a 60-file page with no token: 510 offered, 500 kept.
        val pages = (0 until 9).map { index ->
            { pageJson(files = backupFiles(from = index * 50, count = 50), nextPageToken = "t${index + 1}") }
        } + listOf({ pageJson(files = backupFiles(from = 450, count = 60), nextPageToken = null) })
        val http = RecordingDriveHttp(pages)

        val listing = list(http)

        assertTrue(listing.truncated)
        assertEquals(backupFiles(from = 0, count = DriveRestClient.MAX_LISTED_BACKUPS), listing.files)
    }

    @Test
    fun aFailingPageThrowsAndNothingPartialComesBack() {
        val http = RecordingDriveHttp(
            listOf(
                { pageJson(files = backupFiles(from = 0, count = 50), nextPageToken = "t2") },
                { throw BackupException("Drive access was denied.") },
            ),
        )

        val thrown = runCatching { list(http) }.exceptionOrNull()

        assertTrue(thrown is BackupException)
        assertEquals("Drive access was denied.", thrown?.message)
        assertEquals(2, http.urls.size)
    }

    @Test
    fun theFirstRequestAsksForTheTokenAndSendsNone() {
        val http = RecordingDriveHttp(listOf({ pageJson(files = emptyList(), nextPageToken = null) }))

        val listing = list(http)

        assertEquals(emptyList<DriveBackupFile>(), listing.files)
        assertFalse(listing.truncated)
        val url = http.urls.single()
        assertEquals("GET", http.methods.single())
        assertEquals("nextPageToken,files(id,name,modifiedTime)", queryParam(url, "fields"))
        assertNull(queryParam(url, "pageToken"))
        assertEquals("50", queryParam(url, "pageSize"))
        assertEquals("modifiedTime desc", queryParam(url, "orderBy"))
        val query = queryParam(url, "q").orEmpty()
        assertTrue(query.contains("'folder' in parents"))
        assertTrue(query.contains("trashed = false"))
        assertTrue(query.contains(BackupJson.FILE_PREFIX))
    }

    @Test
    fun aBlankOrNullTokenEndsTheListing() {
        // Only one canned page each: a second request would trip the fake's own check.
        val blank = RecordingDriveHttp(listOf({ """{"nextPageToken":"","files":[]}""" }))
        val nul = RecordingDriveHttp(listOf({ """{"nextPageToken":null,"files":[]}""" }))

        assertFalse(list(blank).truncated)
        assertFalse(list(nul).truncated)
        assertEquals(1, blank.urls.size)
        assertEquals(1, nul.urls.size)
    }

    @Test
    fun aCancelledRefreshStopsAtThePageBoundary() {
        var requests = 0
        lateinit var refresh: Job
        // Every page offers another; only cancellation can end this listing early.
        val http = DriveHttp { _, _, _, _, _ ->
            requests += 1
            refresh.cancel()
            pageJson(files = backupFiles(from = requests - 1, count = 1), nextPageToken = "t$requests")
        }

        runBlocking {
            refresh = launch {
                DriveRestClient(http = http).listBackups(accessToken = "token", folderId = "folder")
            }
            refresh.join()
            assertTrue(refresh.isCancelled)
        }

        assertEquals(1, requests)
    }

    private fun list(http: DriveHttp): DriveBackupListing = runBlocking {
        DriveRestClient(http = http).listBackups(accessToken = "token", folderId = "folder")
    }

    private fun backupFiles(from: Int, count: Int): List<DriveBackupFile> =
        (from until from + count).map { index ->
            val stamp = index.toString().padStart(3, '0')
            DriveBackupFile(
                id = "id-$stamp",
                name = "${BackupJson.FILE_PREFIX}$stamp.json",
                modifiedAtMillis = 1_700_000_000_000L - index * 60_000L,
            )
        }

    private fun pageJson(files: List<DriveBackupFile>, nextPageToken: String?): String {
        val array = JsonArray()
        for (file in files) {
            val obj = JsonObject()
            obj.addProperty("id", file.id)
            obj.addProperty("name", file.name)
            obj.addProperty("modifiedTime", Instant.ofEpochMilli(file.modifiedAtMillis).toString())
            array.add(obj)
        }
        val page = JsonObject()
        if (nextPageToken != null) page.addProperty("nextPageToken", nextPageToken)
        page.add("files", array)
        return page.toString()
    }

    private fun queryParam(url: String, name: String): String? {
        return url.substringAfter('?', "")
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.let { URLDecoder.decode(it.substringAfter('='), "UTF-8") }
    }

    /** Answers requests in order from [pages]; a page may throw. Remembers every URL. */
    private class RecordingDriveHttp(private val pages: List<() -> String>) : DriveHttp {
        val urls = mutableListOf<String>()
        val methods = mutableListOf<String>()

        override fun call(
            accessToken: String,
            url: String,
            method: String,
            contentType: String?,
            body: String?,
        ): String {
            urls += url
            methods += method
            val index = urls.size - 1
            check(index < pages.size) { "request ${index + 1} has no canned page: $url" }
            return pages[index]()
        }
    }
}
