package com.sinura.personaltrainer.data.sync

import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Minimal PostgREST client for Temper Account sync (no service role). */
class SupabaseRestClient(
    private val projectUrl: String,
    private val anonKey: String,
) {
    fun post(table: String, jsonBody: String, accessToken: String) {
        val url = URL("${projectUrl.trimEnd('/')}/rest/v1/$table")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
        }
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(jsonBody) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            throw IllegalStateException("Supabase upsert failed ($code): $error")
        }
    }

    fun getUpdatedSince(table: String, updatedColumn: String, sinceMs: Long, accessToken: String): String {
        val query =
            "$updatedColumn=gt.$sinceMs&order=$updatedColumn.asc&limit=500"
        val url = URL("${projectUrl.trimEnd('/')}/rest/v1/$table?$query")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        val code = connection.responseCode
        val body = if (code in 200..299) {
            connection.inputStream.bufferedReader().use(BufferedReader::readText)
        } else {
            val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            throw IllegalStateException("Supabase pull failed ($code): $error")
        }
        return body
    }

    /** Deletes every row visible to the signed-in user via PostgREST + RLS. */
    fun deleteAllRows(table: String, accessToken: String) {
        val filter = "user_id=not.is.null"
        val url = URL("${projectUrl.trimEnd('/')}/rest/v1/$table?$filter")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Prefer", "return=minimal")
        }
        val code = connection.responseCode
        if (code !in 200..299 && code != 204) {
            val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            throw IllegalStateException("Supabase delete failed ($code): $error")
        }
    }

    /** GoTrue self-service account deletion (trusted-server lane; not E2EE). */
    fun deleteAuthUser(accessToken: String) {
        val url = URL("${projectUrl.trimEnd('/')}/auth/v1/user")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val code = connection.responseCode
        if (code !in 200..299 && code != 204) {
            val error = connection.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            throw IllegalStateException("Supabase auth delete failed ($code): $error")
        }
    }
}
