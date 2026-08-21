package com.sinura.personaltrainer.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleNormalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BackupJson {
    const val CURRENT_VERSION = 2
    const val APP_ID = "personal-trainer"
    const val FOLDER_NAME = "PersonalTrainer Backups"
    const val FILE_PREFIX = "personal-trainer-backup-"
    const val MIME_TYPE = "application/json"

    private const val NOT_A_BACKUP = "This file is not a Personal Trainer backup."
    private const val DAMAGED =
        "This backup file is damaged or incomplete, so nothing was changed."

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
            // Sorted so two exports of the same database are byte-identical: a diffable backup
            // is how you find out what actually changed between them.
            exerciseMuscles = document.exerciseMuscles
                .sortedWith(compareBy({ it.exerciseId }, { it.muscleKey })),
            scheduleSlots = document.scheduleSlots
                .sortedWith(compareBy({ it.position }, { it.id })),
        )
        return gsonPretty.toJson(sorted) + "\n"
    }

    /**
     * Parses a backup file into a document, or throws [BackupException] with copy that is
     * safe to show the user.
     *
     * Every parser failure — malformed JSON, a truncated download, an HTML error page, a
     * field of the wrong type — is funnelled into one friendly message. Raw Gson text such as
     * "java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING" used to reach
     * the Settings screen verbatim.
     *
     * Decoding proves only that the shape is readable. It does NOT prove the contents are
     * sane: run [BackupValidator] before writing anything to the database.
     */
    fun decode(json: String): BackupDocument {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Exception) {
            throw BackupException(NOT_A_BACKUP)
        }
        val version = try {
            root.get("version")?.asInt
        } catch (_: Exception) {
            null
        } ?: throw BackupException(NOT_A_BACKUP)
        if (version > CURRENT_VERSION) {
            throw BackupException("This backup was made with a newer app version and can’t be opened here.")
        }
        if (version < 1) {
            throw BackupException("This backup file is missing a valid version.")
        }
        val app = try {
            root.get("app")?.asString
        } catch (_: Exception) {
            null
        }
        if (app != null && app != APP_ID) {
            throw BackupException(NOT_A_BACKUP)
        }
        return try {
            buildDocument(root, version, app)
        } catch (error: BackupException) {
            throw error
        } catch (_: Exception) {
            // Wrong field types, malformed nested arrays, Gson reflection failures.
            throw BackupException(DAMAGED)
        }
    }

    private fun buildDocument(root: JsonObject, version: Int, app: String?): BackupDocument {
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
            exerciseMuscles = gsonPretty.fromJsonList(root, "exerciseMuscles", Array<BackupExerciseMuscle>::class.java),
            scheduleSlots = gsonPretty.fromJsonList(root, "scheduleSlots", Array<BackupScheduleSlot>::class.java),
        ).normalized()
    }

    /**
     * Makes every accepted document v2-shaped, whatever version it arrived as.
     *
     * One place, run once, immediately after parsing — so nothing downstream ever has to ask
     * "which version is this?" again. Two things happen here:
     *
     * Nulls become defaults. Gson bypasses Kotlin constructors, so an absent or explicitly null
     * `equipment` reaches a non-null field as null; the fields are declared nullable to make
     * that expressible and are collapsed here.
     *
     * A v1 document gains its junction. v1 knew only a muscle-group string per exercise, so the
     * credits are derived from it exactly the way the body map used to derive its secondaries —
     * primary 1.0, each secondary at the flat 0.4. That is not new information invented during a
     * restore; it is the v1 model written down in the v2 shape, so restoring an old backup gives
     * the same heat picture the old app gave.
     */
    private fun BackupDocument.normalized(): BackupDocument {
        val exercises = exercises.map { exercise ->
            exercise.copy(
                equipment = exercise.equipment?.takeIf { it.isNotBlank() }
                    ?: EquipmentType.OTHER.name,
                loadType = exercise.loadType?.takeIf { it.isNotBlank() }
                    ?: LoadType.EXTERNAL.name,
            )
        }
        val credits = if (version == 1 || exerciseMuscles.isEmpty() && version < CURRENT_VERSION) {
            exercises.flatMap { exercise ->
                MuscleNormalizer.deriveCredits(exercise.muscleGroup).map { credit ->
                    BackupExerciseMuscle(
                        exerciseId = exercise.id,
                        muscleKey = credit.muscleKey,
                        weight = credit.weight,
                    )
                }
            }
        } else {
            exerciseMuscles
        }
        return copy(exercises = exercises, exerciseMuscles = credits)
    }

    private fun parsePreferences(root: JsonObject): BackupPreferences {
        // Preferences are cosmetic next to training data: a malformed block falls back to
        // defaults rather than failing an otherwise good restore.
        val prefs = try {
            root.getAsJsonObject("preferences")
        } catch (_: Exception) {
            null
        }
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
