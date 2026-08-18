package com.sinura.personaltrainer.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BackupJson {
    const val CURRENT_VERSION = 1
    const val APP_ID = "personal-trainer"
    const val FOLDER_NAME = "PersonalTrainer Backups"
    const val FILE_PREFIX = "personal-trainer-backup-"

    private val gsonPretty: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    private val fileStamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm").withZone(ZoneId.systemDefault())

    private val isoStamp: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT

    fun nowIso(clock: Instant = Instant.now()): String = isoStamp.format(clock)

    fun fileName(clock: Instant = Instant.now()): String =
        "$FILE_PREFIX${fileStamp.format(clock)}.json"

    fun encode(document: BackupDocument): String {
        val sorted = document.copy(
            exercises = document.exercises.sortedBy { it.id },
            routines = document.routines.sortedBy { it.id },
            routineExercises = document.routineExercises.sortedBy { it.id },
            sessions = document.sessions.sortedBy { it.id },
            sessionExercises = document.sessionExercises.sortedBy { it.id },
            setLogs = document.setLogs.sortedBy { it.id },
        )
        return gsonPretty.toJson(sorted) + "\n"
    }

    fun decode(json: String): BackupDocument {
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("version")?.asInt
            ?: throw BackupException("This file is not a Personal Trainer backup.")
        if (version > CURRENT_VERSION) {
            throw BackupException("This backup was made with a newer app version and can’t be opened here.")
        }
        if (version < 1) {
            throw BackupException("This backup file is missing a valid version.")
        }
        val app = root.get("app")?.asString
        if (app != null && app != APP_ID) {
            throw BackupException("This file is not a Personal Trainer backup.")
        }
        return BackupDocument(
            version = version,
            app = app ?: APP_ID,
            exportedAt = root.get("exportedAt")?.asString ?: nowIso(),
            preferences = parsePreferences(root),
            exercises = gsonPretty.fromJsonList(root, "exercises", Array<BackupExercise>::class.java),
            routines = gsonPretty.fromJsonList(root, "routines", Array<BackupRoutine>::class.java),
            routineExercises = gsonPretty.fromJsonList(root, "routineExercises", Array<BackupRoutineExercise>::class.java),
            sessions = gsonPretty.fromJsonList(root, "sessions", Array<BackupSession>::class.java),
            sessionExercises = gsonPretty.fromJsonList(root, "sessionExercises", Array<BackupSessionExercise>::class.java),
            setLogs = gsonPretty.fromJsonList(root, "setLogs", Array<BackupSetLog>::class.java),
        )
    }

    private fun parsePreferences(root: JsonObject): BackupPreferences {
        val prefs = root.getAsJsonObject("preferences")
        val unit = prefs?.get("weightUnit")?.asString ?: "kg"
        return BackupPreferences(
            weightUnit = unit,
            trainingDaysPerWeek = prefs?.get("trainingDaysPerWeek")?.asInt ?: 4,
            splitStyle = prefs?.get("splitStyle")?.asString ?: "auto",
            weekStart = prefs?.get("weekStart")?.asString ?: "MONDAY",
            restSoundEnabled = prefs?.get("restSoundEnabled")?.asBoolean ?: true,
            restVibrationEnabled = prefs?.get("restVibrationEnabled")?.asBoolean ?: true,
            defaultRestSeconds = prefs?.get("defaultRestSeconds")?.asInt ?: 90,
        )
    }

    private fun <T> Gson.fromJsonList(root: JsonObject, key: String, type: Class<Array<T>>): List<T> {
        val element = root.get(key) ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return fromJson(element, type).toList()
    }
}
