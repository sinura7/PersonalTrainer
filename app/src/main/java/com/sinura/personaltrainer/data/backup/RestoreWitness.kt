package com.sinura.personaltrainer.data.backup

import com.google.gson.stream.JsonWriter
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import java.io.StringWriter
import java.security.MessageDigest

/**
 * Proof of which database a Room replacement left behind.
 *
 * Recovery from a [RestoreJournal.WIPING] journal has one question to answer: did the
 * replacement transaction commit before the process died, or did it roll back? The old
 * answer compared five row counts and the lowest session id, which two backups with the
 * same sessions and sets but different weights, notes, activities or preferences share.
 * Recovery could then read the untouched original as "already restored" and apply the
 * incoming preferences over it.
 *
 * This is a SHA-256 over a canonical projection of exactly the tables
 * `LocalBackupRepository.replaceRoom` writes. Both sides go through the same function:
 * the before-witness is [of] the phone's own snapshot at stage time, the after-witness is
 * [of] the incoming document, and recovery compares [of] the phone's snapshot now against
 * both. Equality means the same content in every replaced table; a mismatch on both sides
 * is reported as unresolved rather than guessed.
 *
 * What makes `of(document) == of(snapshot after replaceRoom(document))` hold:
 * - every list is sorted by a stable key at every level, so insertion order and query
 *   order are irrelevant;
 * - the defaults the activity mappers apply on the way in (`EXTERNAL`, `OTHER`, `false`,
 *   `0`) are applied to the document side here, so a document field that arrives null
 *   matches the non-null column it is stored into;
 * - unfinished sessions and live activities are left out on both sides: a backup never
 *   carries them, and a workout started after the crash must not make recovery
 *   undecidable;
 * - `exportedAt`, `version`, `app` and the preferences block are not part of it — they
 *   are not Room tables.
 *
 * Scalars are written as strings and nulls as JSON null. Hand-written with [JsonWriter]
 * rather than reflected through Gson so the byte layout does not depend on field
 * declaration order in this build, which is what lets a journal written by one build be
 * read by the next.
 */
object RestoreWitness {
    /** Bumped whenever the projection changes shape; a mismatched prefix is "not decidable". */
    const val VERSION = "w1"

    fun of(document: BackupDocument): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalJson(document).toByteArray(Charsets.UTF_8))
        return buildString(VERSION.length + 1 + digest.size * 2) {
            append(VERSION).append(':')
            digest.forEach { byte -> append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF]) }
        }
    }

    /** True when [witness] was written by this projection version and can be compared. */
    fun isCurrent(witness: String?): Boolean = witness != null && witness.startsWith("$VERSION:")

    /** The exact text that is hashed. Exposed so a test can pin the canonical form. */
    internal fun canonicalJson(document: BackupDocument): String {
        val out = StringWriter()
        JsonWriter(out).use { json ->
            json.beginObject()
            json.name("witness").value(VERSION)
            writeExercises(json, document)
            writeCredits(json, document)
            writeRoutines(json, document)
            writeRoutineExercises(json, document)
            writeSessions(json, document)
            writeScheduleSlots(json, document)
            writeActivities(json, document)
            writeTemplates(json, document)
            writePlanner(json, document)
            writeGoals(json, document)
            json.endObject()
        }
        return out.toString()
    }

    private fun writeExercises(json: JsonWriter, document: BackupDocument) {
        json.name("exercises").beginArray()
        document.exercises.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("name", row.name)
            json.str("muscleGroup", row.muscleGroup)
            json.str("notes", row.notes)
            json.bool("isCustom", row.isCustom)
            // replaceRoom writes `equipment ?: OTHER` / `loadType ?: EXTERNAL`; mirror it.
            json.str("equipment", row.equipment ?: EquipmentType.OTHER.name)
            json.str("loadType", row.loadType ?: LoadType.EXTERNAL.name)
            json.str("movementKey", row.movementKey)
            json.str("imageKey", row.imageKey)
            json.endObject()
        }
        json.endArray()
    }

    private fun writeCredits(json: JsonWriter, document: BackupDocument) {
        json.name("exerciseMuscles").beginArray()
        document.exerciseMuscles
            .sortedWith(compareBy({ it.exerciseId }, { it.muscleKey }))
            .forEach { row ->
                json.beginObject()
                json.str("exerciseId", row.exerciseId)
                json.str("muscleKey", row.muscleKey)
                json.num("weight", row.weight)
                json.endObject()
            }
        json.endArray()
    }

    private fun writeRoutines(json: JsonWriter, document: BackupDocument) {
        json.name("routines").beginArray()
        document.routines.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("name", row.name)
            json.str("notes", row.notes)
            json.num("createdAt", row.createdAt)
            json.num("updatedAt", row.updatedAt)
            json.endObject()
        }
        json.endArray()
    }

    private fun writeRoutineExercises(json: JsonWriter, document: BackupDocument) {
        json.name("routineExercises").beginArray()
        document.routineExercises.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("routineId", row.routineId)
            json.str("exerciseId", row.exerciseId)
            json.num("sortOrder", row.sortOrder)
            json.num("targetSets", row.targetSets)
            json.num("targetReps", row.targetReps)
            json.num("targetWeightKg", row.targetWeightKg)
            json.num("restSeconds", row.restSeconds)
            json.endObject()
        }
        json.endArray()
    }

    private fun writeSessions(json: JsonWriter, document: BackupDocument) {
        // Finished sessions only, with their children — the same cut createSnapshot makes.
        val finished = document.sessions.filter { it.finishedAt != null }
        val finishedIds = finished.mapTo(HashSet()) { it.id }
        json.name("sessions").beginArray()
        finished.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("routineId", row.routineId)
            json.str("routineName", row.routineName)
            json.num("date", row.date)
            json.str("notes", row.notes)
            json.num("durationMinutes", row.durationMinutes)
            json.num("startedAt", row.startedAt)
            json.num("finishedAt", row.finishedAt)
            json.endObject()
        }
        json.endArray()
        json.name("sessionExercises").beginArray()
        document.sessionExercises
            .filter { it.sessionId in finishedIds }
            .sortedBy { it.id }
            .forEach { row ->
                json.beginObject()
                json.str("id", row.id)
                json.str("sessionId", row.sessionId)
                json.str("exerciseId", row.exerciseId)
                json.num("sortOrder", row.sortOrder)
                json.num("targetSets", row.targetSets)
                json.num("targetReps", row.targetReps)
                json.num("targetWeightKg", row.targetWeightKg)
                json.num("restSeconds", row.restSeconds)
                json.endObject()
            }
        json.endArray()
        json.name("setLogs").beginArray()
        document.setLogs
            .filter { it.sessionId in finishedIds }
            .sortedBy { it.id }
            .forEach { row ->
                json.beginObject()
                json.str("id", row.id)
                json.str("sessionId", row.sessionId)
                json.str("exerciseId", row.exerciseId)
                json.num("setNumber", row.setNumber)
                json.num("weightKg", row.weightKg)
                json.num("reps", row.reps)
                json.num("rpe", row.rpe)
                json.bool("isWarmup", row.isWarmup)
                json.num("completedAt", row.completedAt)
                json.endObject()
            }
        json.endArray()
    }

    private fun writeScheduleSlots(json: JsonWriter, document: BackupDocument) {
        json.name("scheduleSlots").beginArray()
        document.scheduleSlots.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.num("position", row.position)
            json.str("routineId", row.routineId)
            json.str("focusKind", row.focusKind)
            json.num("anchorDay", row.anchorDay)
            json.num("createdAt", row.createdAt)
            json.num("updatedAt", row.updatedAt)
            json.endObject()
        }
        json.endArray()
    }

    private fun writeActivities(json: JsonWriter, document: BackupDocument) {
        // ActivityBackupIo.replace skips ACTIVE rows and snapshot() never reads them.
        json.name("activities").beginArray()
        document.activities
            .filter { it.status != "ACTIVE" }
            .sortedBy { it.id }
            .forEach { row ->
                json.beginObject()
                json.str("id", row.id)
                json.str("status", row.status)
                json.str("origin", row.origin)
                json.str("source", row.source)
                json.str("title", row.title)
                json.str("notes", row.notes)
                json.name("performedStart")
                writeTime(json, row.performedStart)
                json.name("performedEnd")
                writeTime(json, row.performedEnd)
                json.str("templateId", row.templateId)
                json.str("occurrenceId", row.occurrenceId)
                json.num("createdAtMs", row.createdAtMs)
                json.num("updatedAtMs", row.updatedAtMs)
                json.num("revision", row.revision)
                writeBlocks(json, row.blocks)
                json.endObject()
            }
        json.endArray()
    }

    private fun writeTemplates(json: JsonWriter, document: BackupDocument) {
        json.name("activityTemplates").beginArray()
        document.activityTemplates.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("title", row.title)
            json.str("notes", row.notes)
            writeBlocks(json, row.blocks)
            json.endObject()
        }
        json.endArray()
    }

    private fun writeTime(json: JsonWriter, time: BackupCapturedTime?) {
        if (time == null) {
            json.nullValue()
            return
        }
        json.beginObject()
        json.num("instantMs", time.instantMs)
        json.str("zoneId", time.zoneId)
        json.num("offsetSeconds", time.offsetSeconds)
        json.num("localEpochDay", time.localEpochDay)
        json.endObject()
    }

    /**
     * Mirrors `BackupActivityBlock.toDomain()` followed by `ActivityBlock.toBackup()`: the
     * shape a block has after it has been through the entity and back. Nested lists are
     * sorted the way the graph mappers sort them, with the id as a tie-break.
     */
    private fun writeBlocks(json: JsonWriter, blocks: List<BackupActivityBlock>) {
        json.name("blocks").beginArray()
        blocks.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { block ->
            json.beginObject()
            json.str("id", block.id)
            json.num("sortOrder", block.sortOrder)
            if (block.kind == "STRENGTH") {
                json.str("kind", "STRENGTH")
                json.str("exerciseId", block.exerciseId.orEmpty())
                json.str("exerciseName", block.exerciseName.orEmpty())
                json.str("loadType", block.loadType ?: LoadType.EXTERNAL.name)
                json.str("equipment", block.equipment ?: EquipmentType.OTHER.name)
                json.name("muscles").beginArray()
                block.muscles.sortedBy { it.muscleKey }.forEach { muscle ->
                    json.beginObject()
                    json.str("muscleKey", muscle.muscleKey)
                    json.num("weight", muscle.weight)
                    json.endObject()
                }
                json.endArray()
                json.name("sets").beginArray()
                block.sets.sortedWith(compareBy({ it.setNumber }, { it.id })).forEach { set ->
                    json.beginObject()
                    json.str("id", set.id)
                    json.num("setNumber", set.setNumber)
                    json.num("weightKg", set.weightKg)
                    json.num("reps", set.reps)
                    json.num("rpe", set.rpe)
                    json.bool("isWarmup", set.isWarmup)
                    json.num("completedAtMs", set.completedAtMs)
                    json.endObject()
                }
                json.endArray()
            } else {
                json.str("kind", "CARDIO")
                json.str("cardioType", block.cardioType ?: "OTHER")
                json.bool("indoor", block.indoor ?: false)
                json.num("elapsedSeconds", block.elapsedSeconds ?: 0L)
                json.num("movingSeconds", block.movingSeconds)
                json.num("distanceMeters", block.distanceMeters)
                json.num("elevationMeters", block.elevationMeters)
                json.num("heartRateBpm", block.heartRateBpm)
                json.num("energyKj", block.energyKj)
                json.num("rpe", block.rpe)
                json.str("routeRef", block.routeRef)
                json.name("intervals").beginArray()
                block.intervals.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { interval ->
                    json.beginObject()
                    json.str("id", interval.id)
                    json.num("sortOrder", interval.sortOrder)
                    json.num("elapsedSeconds", interval.elapsedSeconds)
                    json.num("distanceMeters", interval.distanceMeters)
                    json.num("rpe", interval.rpe)
                    json.endObject()
                }
                json.endArray()
            }
            json.endObject()
        }
        json.endArray()
    }

    private fun writePlanner(json: JsonWriter, document: BackupDocument) {
        json.name("scheduleRules").beginArray()
        document.scheduleRules.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.num("weekday", row.weekday)
            json.num("hour", row.hour)
            json.num("minute", row.minute)
            json.str("modality", row.modality)
            json.str("zonePolicy", row.zonePolicy)
            json.str("fixedZoneId", row.fixedZoneId)
            json.str("routineId", row.routineId)
            json.str("templateId", row.templateId)
            json.str("focusKind", row.focusKind)
            json.num("reminderOffsetMinutes", row.reminderOffsetMinutes)
            json.bool("enabled", row.enabled)
            json.num("createdAtMs", row.createdAtMs)
            json.num("updatedAtMs", row.updatedAtMs)
            json.endObject()
        }
        json.endArray()
        json.name("scheduleOccurrences").beginArray()
        document.scheduleOccurrences.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("ruleId", row.ruleId)
            json.str("status", row.status)
            json.num("instantMs", row.instantMs)
            json.str("zoneId", row.zoneId)
            json.num("offsetSeconds", row.offsetSeconds)
            json.num("localEpochDay", row.localEpochDay)
            json.num("hour", row.hour)
            json.num("minute", row.minute)
            json.str("completedActivityId", row.completedActivityId)
            json.num("createdAtMs", row.createdAtMs)
            json.num("updatedAtMs", row.updatedAtMs)
            json.endObject()
        }
        json.endArray()
        json.name("missedWorkDecisions").beginArray()
        document.missedWorkDecisions.sortedBy { it.weekStartEpochDay }.forEach { row ->
            json.beginObject()
            json.num("weekStartEpochDay", row.weekStartEpochDay)
            json.str("choice", row.choice)
            json.num("decidedAtMs", row.decidedAtMs)
            json.endObject()
        }
        json.endArray()
        json.name("reminderDeliveries").beginArray()
        document.reminderDeliveries.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("occurrenceId", row.occurrenceId)
            json.num("scheduledAtMs", row.scheduledAtMs)
            json.str("status", row.status)
            json.num("createdAtMs", row.createdAtMs)
            json.num("updatedAtMs", row.updatedAtMs)
            json.endObject()
        }
        json.endArray()
    }

    private fun writeGoals(json: JsonWriter, document: BackupDocument) {
        json.name("measurableGoals").beginArray()
        document.measurableGoals.sortedBy { it.id }.forEach { row ->
            json.beginObject()
            json.str("id", row.id)
            json.str("kind", row.kind)
            json.num("targetValue", row.targetValue)
            json.str("exerciseId", row.exerciseId)
            json.str("exerciseName", row.exerciseName)
            json.str("period", row.period)
            json.num("instantMs", row.instantMs)
            json.str("zoneId", row.zoneId)
            json.num("offsetSeconds", row.offsetSeconds)
            json.num("localEpochDay", row.localEpochDay)
            json.bool("paused", row.paused)
            json.num("createdAtMs", row.createdAtMs)
            json.num("updatedAtMs", row.updatedAtMs)
            json.endObject()
        }
        json.endArray()
    }

    private fun JsonWriter.str(key: String, value: String?): JsonWriter =
        if (value == null) name(key).nullValue() else name(key).value(value)

    /** Numbers travel as their decimal string so NaN and infinity never trip strict JSON. */
    private fun JsonWriter.num(key: String, value: Number?): JsonWriter =
        if (value == null) name(key).nullValue() else name(key).value(value.toString())

    private fun JsonWriter.bool(key: String, value: Boolean): JsonWriter =
        name(key).value(if (value) "true" else "false")

    private const val HEX = "0123456789abcdef"
}
