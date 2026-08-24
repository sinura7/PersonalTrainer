package com.sinura.personaltrainer.data.backup

import com.google.gson.JsonParser

/** Pure JSON read of Drive About `user.emailAddress`. No Android, no Play Services. */
object DriveAboutJson {
    fun parseAccountEmail(body: String): String? {
        return try {
            JsonParser.parseString(body)
                .asJsonObject
                .getAsJsonObject("user")
                ?.get("emailAddress")
                ?.asString
                ?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
