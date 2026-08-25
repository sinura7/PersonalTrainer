package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonParser

/**
 * Pure JSON read of a Drive file metadata body for folder reuse.
 *
 * `folderExists` already requested `fields=id,trashed`, then treated any 200
 * as present. A cached id that now points at a trashed folder still answers
 * 200 with `trashed: true`, so backup wrote into the trash (N11).
 */
object DriveFolderJson {
    fun isUsable(body: String): Boolean {
        return try {
            val obj = JsonParser.parseString(body).asJsonObject
            val id = obj.get("id")?.asString
            if (id.isNullOrBlank()) return false
            val trashed = obj.get("trashed")
            if (trashed == null || trashed.isJsonNull) return true
            !trashed.asBoolean
        } catch (_: Exception) {
            false
        }
    }
}
