package com.sinura.personaltrainer.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
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

    internal fun notABackupMessage(): String = NOT_A_BACKUP

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
            // Rebuilt field by field through `text()` rather than `copy()`. Gson constructs
            // these classes through Unsafe, so a JSON object missing "name" leaves null in a
            // field Kotlin declares non-null — and `copy()`, like any public function, checks
            // its parameters and throws. That threw inside decode(), which reports one generic
            // "this file is damaged" for everything, costing the validator's specific "an
            // exercise is missing its name or id". The file is refused either way; the point
            // is that the owner is told which field was wrong.
            BackupExercise(
                id = text(exercise.id),
                name = text(exercise.name),
                muscleGroup = text(exercise.muscleGroup),
                notes = text(exercise.notes),
                isCustom = exercise.isCustom,
                // Canonicalized, not merely defaulted. A document written by the other lineage
                // of this app spells these lowercase ("barbell", "weighted_bodyweight") and the
                // validator matches on the enum name, so without this pass every backup that
                // lineage ever wrote is refused outright — the whole restore, not the field.
                // An unrecognized value is deliberately passed through unchanged so the
                // validator still refuses it and still names it.
                equipment = exercise.equipment?.takeIf { it.isNotBlank() }
                    ?.let { raw -> EquipmentType.fromLegacyStorage(raw)?.name ?: raw }
                    ?: EquipmentType.OTHER.name,
                loadType = exercise.loadType?.takeIf { it.isNotBlank() }
                    ?.let { raw -> LoadType.fromLegacyStorage(raw)?.name ?: raw }
                    ?: LoadType.EXTERNAL.name,
                movementKey = exercise.movementKey,
                imageKey = exercise.imageKey,
            )
        }
        // Parenthesized deliberately. Written without them, `&&` binds tighter than `||` and
        // this read as `version == 1 || (exerciseMuscles.isEmpty() && version < 2)` — and since
        // decode() has already refused anything below 1, `version < 2` IS `version == 1`. The
        // second test could never contribute a case the first had not already taken, so a v2
        // document carrying no credits fell through to the else branch with none derived.
        // The restore epilogue happens to repair that (reconcileCatalogLocked derives credits
        // for anything lacking them), so this was a trap rather than live data loss — but the
        // expression did not mean what it said, and the next caller of normalized() would not
        // have had an epilogue to save it.
        val credits = if (version == 1 || exerciseMuscles.isEmpty()) {
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

    /**
     * Reads a String that Kotlin says cannot be null but Gson may have made null anyway.
     *
     * The parameter is declared nullable on purpose: that is what stops the compiler emitting
     * the parameter null-check that is the whole problem, and it is why this must stay a
     * function rather than becoming an `?: ""` at each call site — on a value the compiler
     * believes is already non-null, that elvis is free to be optimised away.
     */
    private fun text(value: String?): String = value ?: ""

    private fun parsePreferences(root: JsonObject): BackupPreferences {
        // Preferences are cosmetic next to training data: a malformed block falls back to
        // defaults rather than failing an otherwise good restore.
        val prefs = try {
            root.getAsJsonObject("preferences")
        } catch (_: Exception) {
            null
        }
        // Read field by field through the tolerant accessors below rather than `?.asInt`:
        // Gson throws on a type it cannot coerce, so a single junk scalar used to take the
        // whole decode — and with it the restore — down with it. One bad field now costs that
        // field alone.
        return BackupPreferences(
            weightUnit = prefs.string("weightUnit", "lbs"),
            trainingDaysPerWeek = prefs.int("trainingDaysPerWeek", 4),
            splitStyle = prefs.string("splitStyle", "auto"),
            weekStart = prefs.string("weekStart", "MONDAY"),
            restSoundEnabled = prefs.bool("restSoundEnabled", true),
            restVibrationEnabled = prefs.bool("restVibrationEnabled", true),
            defaultRestSeconds = prefs.int("defaultRestSeconds", 90),
            trainingGoal = prefs.string("trainingGoal", "GENERAL"),
            trainingEmphasis = prefs.string("trainingEmphasis", "BALANCED"),
            availableEquipment = prefs.stringList("availableEquipment"),
            heatWindow = prefs.string("heatWindow", "CURRENT_WEEK"),
            // Absent and null both mean "not told", which is a different thing from zero.
            bodyweightKg = prefs.positiveDoubleOrNull("bodyweightKg"),
            onboardingComplete = prefs.bool("onboardingComplete", false),
            dismissedCollisionIds = prefs.stringList("dismissedCollisionIds"),
            blockStartEpochDay = prefs.longOrNull("blockStartEpochDay"),
            blockWeeks = prefs.int("blockWeeks", 12),
            pastBlocks = prefs.string("pastBlocks", ""),
            bodyweightLog = prefs.string("bodyweightLog", ""),
            trainingAge = prefs.string("trainingAge", ""),
            preferredDays = prefs.stringList("preferredDays"),
            trainingPlace = prefs.string("trainingPlace", ""),
            lighterWeekStartEpochDay = prefs.longOrNull("lighterWeekStartEpochDay"),
        )
    }

    /**
     * The preference readers.
     *
     * All five share one rule: a missing, null, wrong-typed or uncoercible value yields the
     * fallback and never throws. Preferences are cosmetic next to training data, and no
     * malformed setting should cost someone their sessions.
     */
    private fun JsonObject?.primitive(key: String): JsonElement? =
        this?.get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }

    private fun JsonObject?.string(key: String, fallback: String): String =
        try {
            primitive(key)?.asString ?: fallback
        } catch (_: Exception) {
            fallback
        }

    private fun JsonObject?.int(key: String, fallback: Int): Int =
        try {
            primitive(key)?.asInt ?: fallback
        } catch (_: Exception) {
            fallback
        }

    private fun JsonObject?.bool(key: String, fallback: Boolean): Boolean =
        try {
            primitive(key)?.asBoolean ?: fallback
        } catch (_: Exception) {
            fallback
        }

    private fun JsonObject?.longOrNull(key: String): Long? =
        try {
            primitive(key)?.asLong
        } catch (_: Exception) {
            null
        }

    private fun JsonObject?.positiveDoubleOrNull(key: String): Double? =
        try {
            primitive(key)?.asDouble?.takeIf { it.isFinite() && it > 0.0 }
        } catch (_: Exception) {
            null
        }

    private fun JsonObject?.stringList(key: String): List<String> {
        val element = this?.get(key) ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { entry ->
            try {
                entry.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun <T> Gson.fromJsonList(root: JsonObject, key: String, type: Class<Array<T>>): List<T> {
        val element = root.get(key) ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return fromJson(element, type).toList()
    }
}
