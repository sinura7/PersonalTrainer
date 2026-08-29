package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class DriveRestClient {
    suspend fun ensureBackupFolder(accessToken: String, knownFolderId: String?): String {
        if (!knownFolderId.isNullOrBlank() && folderExists(accessToken, knownFolderId)) {
            return knownFolderId
        }
        val existing = findFolder(accessToken)
        if (existing != null) return existing
        return createFolder(accessToken)
    }

    fun listBackups(accessToken: String, folderId: String): List<DriveBackupFile> {
        val query = "'$folderId' in parents and trashed = false and name contains '${BackupJson.FILE_PREFIX}'"
        val url = buildString {
            append("$DRIVE_FILES?pageSize=50")
            append("&orderBy=modifiedTime+desc")
            append("&fields=files(id,name,modifiedTime)")
            append("&q=").append(urlEncode(query))
            append("&spaces=drive")
        }
        val body = request(accessToken, url, "GET")
        val files = JsonParser.parseString(body).asJsonObject.getAsJsonArray("files") ?: return emptyList()
        return files.map { element ->
            val obj = element.asJsonObject
            DriveBackupFile(
                id = obj.get("id").asString,
                name = obj.get("name").asString,
                modifiedAtMillis = parseTime(obj.get("modifiedTime")?.asString),
            )
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

    private fun request(
        accessToken: String,
        url: String,
        method: String,
        contentType: String? = null,
        body: String? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (contentType != null) {
                setRequestProperty("Content-Type", contentType)
            }
            if (body != null) {
                doOutput = true
                outputStream.use { stream ->
                    stream.write(body.toByteArray(Charsets.UTF_8))
                }
            }
        }
        return try {
            val code = connection.responseCode
            // Bounded: the Drive folder is writable by anything holding the account,
            // and an unbounded readText of a planted multi-hundred-MB file OOM-kills
            // the app. No genuine backup or API reply approaches the budget.
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { stream ->
                    val bytes = stream.readNBytes(BackupScaleBudget.IMPORT_BYTES_MAX + 1)
                    if (bytes.size > BackupScaleBudget.IMPORT_BYTES_MAX) {
                        throw BackupException(BackupScaleBudget.TOO_BIG_TO_IMPORT)
                    }
                    bytes.toString(Charsets.UTF_8)
                }
                .orEmpty()
            if (code == 401) {
                throw BackupException("Google sign-in expired. Sign in again.")
            }
            if (code == 403) {
                throw BackupException("Drive access was denied.")
            }
            if (code !in 200..299) {
                throw BackupException(driveErrorMessage(code, text))
            }
            text
        } catch (error: BackupException) {
            throw error
        } catch (error: IOException) {
            throw BackupException("Connect to the internet to use Google Drive.")
        } finally {
            connection.disconnect()
        }
    }

    private fun driveErrorMessage(code: Int, body: String): String {
        val message = try {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        } catch (_: Exception) {
            null
        }
        return message?.ifBlank { null } ?: "Google Drive request failed ($code)."
    }

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
        private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        private const val DRIVE_ABOUT = "https://www.googleapis.com/drive/v3/about"
    }
}
