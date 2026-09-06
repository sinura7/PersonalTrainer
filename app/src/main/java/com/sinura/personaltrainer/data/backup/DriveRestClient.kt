package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonParser
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class DriveRestClient(
    private val http: DriveHttp = HttpUrlConnectionDriveHttp(),
) {
    suspend fun ensureBackupFolder(accessToken: String, knownFolderId: String?): String {
        if (!knownFolderId.isNullOrBlank() && folderExists(accessToken, knownFolderId)) {
            return knownFolderId
        }
        val existing = findFolder(accessToken)
        if (existing != null) return existing
        return createFolder(accessToken)
    }

    /**
     * Every backup in the folder, newest first, following Drive's page tokens.
     *
     * One `pageSize=50` request used to be the whole answer, so the 51st-newest backup
     * and everything older could not be restored from Settings, and "Found 50 backups."
     * counted the page (R13). Drive returns short pages, and empty pages that still carry
     * a token, so a page is only the last one when it has no `nextPageToken`.
     *
     * Bounded on both axes — [MAX_LISTED_BACKUPS] files, [MAX_LIST_PAGES] requests — so a
     * folder someone has filled cannot turn a refresh into an unbounded walk. Hitting a
     * bound while a token remains is reported as [DriveBackupListing.truncated] rather
     * than passed off as the whole folder. A page failure propagates the transport's
     * [BackupException]; nothing partial is returned.
     *
     * Suspends only to observe cancellation: a refresh the user has left must not keep
     * paging on the IO thread.
     */
    suspend fun listBackups(accessToken: String, folderId: String): DriveBackupListing {
        val query = "'$folderId' in parents and trashed = false and name contains '${BackupJson.FILE_PREFIX}'"
        val files = ArrayList<DriveBackupFile>()
        var pageToken: String? = null
        var pages = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = parseListPage(request(accessToken, listPageUrl(query, pageToken), "GET"))
            pages += 1
            val room = MAX_LISTED_BACKUPS - files.size
            files.addAll(page.files.take(room))
            val moreInDrive = page.files.size > room || page.nextPageToken != null
            if (!moreInDrive) {
                return DriveBackupListing(files = files, truncated = false)
            }
            if (files.size >= MAX_LISTED_BACKUPS || pages >= MAX_LIST_PAGES) {
                return DriveBackupListing(files = files, truncated = true)
            }
            pageToken = page.nextPageToken
        }
    }

    fun uploadBackup(accessToken: String, folderId: String, fileName: String, json: String): DriveBackupFile {
        val metadata = """
            {"name":"$fileName","mimeType":"application/json","parents":["$folderId"],"appProperties":{"app":"${BackupJson.APP_ID}","kind":"backup"}}
        """.trimIndent()
        val boundary = "ptbackup${System.currentTimeMillis()}"
        val payload = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(json)
            if (!json.endsWith("\n")) append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
        val body = request(
            accessToken = accessToken,
            url = "$DRIVE_UPLOAD?uploadType=multipart&fields=id,name,modifiedTime",
            method = "POST",
            contentType = "multipart/related; boundary=$boundary",
            body = payload,
        )
        val obj = JsonParser.parseString(body).asJsonObject
        return DriveBackupFile(
            id = obj.get("id").asString,
            name = obj.get("name").asString,
            modifiedAtMillis = parseTime(obj.get("modifiedTime")?.asString),
        )
    }

    fun downloadBackup(accessToken: String, fileId: String): String {
        return request(accessToken, "$DRIVE_FILES/$fileId?alt=media", "GET")
    }

    /**
     * Drive About is in the `drive.file` surface. AuthorizationClient
     * does not return an account email; this is the supported read.
     */
    fun fetchAccountEmail(accessToken: String): String? {
        val body = request(accessToken, "$DRIVE_ABOUT?fields=user(emailAddress)", "GET")
        return DriveAboutJson.parseAccountEmail(body)
    }

    private fun folderExists(accessToken: String, folderId: String): Boolean {
        return try {
            DriveFolderJson.isUsable(
                request(accessToken, "$DRIVE_FILES/$folderId?fields=id,trashed", "GET"),
            )
        } catch (_: BackupException) {
            false
        }
    }

    private fun findFolder(accessToken: String): String? {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '${BackupJson.FOLDER_NAME}' and trashed = false"
        val url = "$DRIVE_FILES?pageSize=1&fields=files(id)&q=${urlEncode(query)}&spaces=drive"
        val body = request(accessToken, url, "GET")
        val files = JsonParser.parseString(body).asJsonObject.getAsJsonArray("files")
        if (files == null || files.size() == 0) return null
        return files[0].asJsonObject.get("id").asString
    }

    private fun createFolder(accessToken: String): String {
        val body = request(
            accessToken = accessToken,
            url = "$DRIVE_FILES?fields=id",
            method = "POST",
            contentType = "application/json; charset=UTF-8",
            body = """{"name":"${BackupJson.FOLDER_NAME}","mimeType":"application/vnd.google-apps.folder"}""",
        )
        return JsonParser.parseString(body).asJsonObject.get("id").asString
    }

    private fun listPageUrl(query: String, pageToken: String?): String = buildString {
        append("$DRIVE_FILES?pageSize=$LIST_PAGE_SIZE")
        append("&orderBy=modifiedTime+desc")
        append("&fields=nextPageToken,files(id,name,modifiedTime)")
        append("&q=").append(urlEncode(query))
        append("&spaces=drive")
        if (pageToken != null) {
            append("&pageToken=").append(urlEncode(pageToken))
        }
    }

    private class ListPage(
        val files: List<DriveBackupFile>,
        val nextPageToken: String?,
    )

    private fun parseListPage(body: String): ListPage {
        val obj = JsonParser.parseString(body).asJsonObject
        val files = obj.getAsJsonArray("files")?.map { element ->
            val file = element.asJsonObject
            DriveBackupFile(
                id = file.get("id").asString,
                name = file.get("name").asString,
                modifiedAtMillis = parseTime(file.get("modifiedTime")?.asString),
            )
        }.orEmpty()
        // A blank token is no token: asking Drive for page "" would loop to the cap.
        val token = obj.get("nextPageToken")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.ifBlank { null }
        return ListPage(files = files, nextPageToken = token)
    }

    private fun request(
        accessToken: String,
        url: String,
        method: String,
        contentType: String? = null,
        body: String? = null,
    ): String = http.call(
        accessToken = accessToken,
        url = url,
        method = method,
        contentType = contentType,
        body = body,
    )

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        /**
         * Ceilings on one listing. Past either, Drive is asked nothing more and the
         * answer says it is cut. 500 backups is years of daily exports; 20 requests
         * is what a folder of short pages costs before the refresh is judged stuck.
         */
        const val MAX_LISTED_BACKUPS = 500
        const val MAX_LIST_PAGES = 20
        private const val LIST_PAGE_SIZE = 50
        private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        private const val DRIVE_ABOUT = "https://www.googleapis.com/drive/v3/about"
    }
}
