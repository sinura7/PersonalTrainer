package com.sinura.personaltrainer.data.backup

import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType

/** Counts shown to the user before they agree to overwrite everything. */
data class BackupSummary(
    val exercises: Int,
    val routines: Int,
    val sessions: Int,
    val setLogs: Int,
) {
    val isEmpty: Boolean get() = exercises == 0 && routines == 0 && sessions == 0 && setLogs == 0

    fun describe(): String =
        "$sessions workout${plural(sessions)}, $setLogs set${plural(setLogs)}, " +
            "$exercises exercise${plural(exercises)}, $routines routine${plural(routines)}"

    private fun plural(n: Int) = if (n == 1) "" else "s"
}

sealed interface BackupValidation {
    data class Valid(val summary: BackupSummary) : BackupValidation

    /** [reason] is user-facing copy; it is the only thing the UI ever shows. */
    data class Invalid(val reason: String) : BackupValidation
}

/**
 * Decides whether a decoded backup is safe to write over the phone's training history.
 *
 * This is the last line of defence before an irreversible wipe, and it exists because
 * decoding alone proves almost nothing: Gson builds these data classes through Unsafe, so a
 * missing JSON field lands as `null` inside a non-null Kotlin `String` and a missing number
 * silently becomes `0`. A file can therefore decode "successfully" and still be junk. Every
 * string here is read through nullable helpers on purpose — the declared types are lying.
 *
 * Pure and dependency-free so it can be exhaustively unit-tested on the JVM.
 */
object BackupValidator {
    /** Sanity bound: rules out 0/garbage timestamps without guessing the user's clock. */
    private const val MIN_PLAUSIBLE_EPOCH_MS = 946_684_800_000L // 2000-01-01T00:00:00Z

    private val EQUIPMENT_STORAGE: Set<String> = EquipmentType.entries.map { it.name }.toSet()
    private val LOAD_TYPE_STORAGE: Set<String> = LoadType.entries.map { it.name }.toSet()

    private const val GENERIC_CORRUPT =
        "This backup file is damaged or incomplete, so nothing was changed."

    /**
     * @param localAuthored authored rows currently on the phone. A document with no
     * authored data — including a catalog-only file of built-in exercises — would
     * erase that state, so it is refused unless [allowEmptyDestructiveRestore] is
     * set by a test that is deliberately wiping.
     */
    fun validate(
        document: BackupDocument,
        localAuthored: AuthoredInventory,
        allowEmptyDestructiveRestore: Boolean = false,
    ): BackupValidation {
        identityProblem(document)?.let { return BackupValidation.Invalid(it) }

        val exerciseIds = HashSet<String>(document.exercises.size)
        document.exercises.forEach { exercise ->
            if (isBlank(exercise.id) || isBlank(exercise.name)) {
                return invalid("an exercise is missing its name or id")
            }
            if (!exerciseIds.add(exercise.id)) {
                return invalid("two exercises share the id \"${exercise.id}\"")
            }
            // Normalization in decode() should have filled both, so a null here means the
            // document did not come through decode() — refuse rather than silently default,
            // because silently defaulting is how a corrupt file passes as a good one.
            if (exercise.equipment !in EQUIPMENT_STORAGE) {
                return invalid("an exercise has an unknown equipment type")
            }
            if (exercise.loadType !in LOAD_TYPE_STORAGE) {
                return invalid("an exercise has an unknown load type")
            }
        }

        val routineIds = HashSet<String>(document.routines.size)
        document.routines.forEach { routine ->
            if (isBlank(routine.id) || isBlank(routine.name)) {
                return invalid("a routine is missing its name or id")
            }
            if (!routineIds.add(routine.id)) {
                return invalid("two routines share the id \"${routine.id}\"")
            }
        }

        val sessionIds = HashSet<String>(document.sessions.size)
        document.sessions.forEach { session ->
            if (isBlank(session.id)) return invalid("a workout is missing its id")
            if (!sessionIds.add(session.id)) {
                return invalid("two workouts share the id \"${session.id}\"")
            }
            if (session.startedAt < MIN_PLAUSIBLE_EPOCH_MS) {
                return invalid("a workout has an impossible start time")
            }
            val finishedAt = session.finishedAt
            if (finishedAt != null && finishedAt < session.startedAt) {
                return invalid("a workout finishes before it starts")
            }
            if (session.durationMinutes < 0) return invalid("a workout has a negative duration")
            // routineId is nullable by design (the routine may have been deleted since), but a
            // non-null one must resolve or history would restore pointing at nothing.
            val routineId = session.routineId
            if (routineId != null && routineId !in routineIds) {
                return invalid("a workout references a routine that is not in this file")
            }
        }

        val ids = HashSet<String>()
        document.routineExercises.forEach { line ->
            if (isBlank(line.id)) return invalid("a routine entry is missing its id")
            if (!ids.add("re:${line.id}")) {
                return invalid("two routine entries share the id \"${line.id}\"")
            }
            if (line.routineId !in routineIds) {
                return invalid("a routine entry belongs to a routine that is not in this file")
            }
            if (line.exerciseId !in exerciseIds) {
                return invalid("a routine entry uses an exercise that is not in this file")
            }
            if (line.targetSets < 0 || line.targetReps < 0 || line.restSeconds < 0) {
                return invalid("a routine entry has negative targets")
            }
            if (!isPlausibleWeight(line.targetWeightKg)) {
                return invalid("a routine entry has an impossible target weight")
            }
        }

        document.sessionExercises.forEach { line ->
            if (isBlank(line.id)) return invalid("a workout entry is missing its id")
            if (!ids.add("se:${line.id}")) {
                return invalid("two workout entries share the id \"${line.id}\"")
            }
            if (line.sessionId !in sessionIds) {
                return invalid("a workout entry belongs to a workout that is not in this file")
            }
            if (line.exerciseId !in exerciseIds) {
                return invalid("a workout entry uses an exercise that is not in this file")
            }
            if (line.targetSets < 0 || line.targetReps < 0 || line.restSeconds < 0) {
                return invalid("a workout entry has negative targets")
            }
            if (!isPlausibleWeight(line.targetWeightKg)) {
                return invalid("a workout entry has an impossible target weight")
            }
        }

        document.setLogs.forEach { set ->
            if (isBlank(set.id)) return invalid("a logged set is missing its id")
            if (!ids.add("sl:${set.id}")) {
                return invalid("two logged sets share the id \"${set.id}\"")
            }
            if (set.sessionId !in sessionIds) {
                return invalid("a logged set belongs to a workout that is not in this file")
            }
            if (set.exerciseId !in exerciseIds) {
                return invalid("a logged set uses an exercise that is not in this file")
            }
            if (set.setNumber < 1) return invalid("a logged set has an invalid set number")
            if (set.reps < 0) return invalid("a logged set has negative reps")
            if (!isPlausibleWeight(set.weightKg) || set.weightKg < 0.0) {
                return invalid("a logged set has an impossible weight")
            }
            val rpe = set.rpe
            if (rpe != null && rpe !in 1..10) return invalid("a logged set has an RPE outside 1–10")
            if (set.completedAt < MIN_PLAUSIBLE_EPOCH_MS) {
                return invalid("a logged set has an impossible timestamp")
            }
        }

        val creditPairs = HashSet<String>(document.exerciseMuscles.size)
        document.exerciseMuscles.forEach { credit ->
            if (isBlank(credit.muscleKey)) {
                return invalid("a muscle credit is missing its muscle")
            }
            if (credit.exerciseId !in exerciseIds) {
                return invalid("a muscle credit belongs to an exercise that is not in this file")
            }
            if (!creditPairs.add("${credit.exerciseId}|${credit.muscleKey}")) {
                return invalid("an exercise credits the same muscle twice")
            }
            val weight = credit.weight
            if (weight.isNaN() || weight.isInfinite() || weight <= 0.0 || weight > 1.0) {
                return invalid("a muscle credit has a weight outside 0–1")
            }
        }

        val slotIds = HashSet<String>(document.scheduleSlots.size)
        document.scheduleSlots.forEach { slot ->
            if (isBlank(slot.id)) return invalid("a schedule slot is missing its id")
            if (!slotIds.add(slot.id)) {
                return invalid("two schedule slots share the id \"${slot.id}\"")
            }
            if (slot.position < 0) return invalid("a schedule slot has a negative position")
            val anchorDay = slot.anchorDay
            if (anchorDay != null && anchorDay !in 0..6) {
                return invalid("a schedule slot anchors to a day that does not exist")
            }
            val routineId = slot.routineId
            if (routineId != null && routineId !in routineIds) {
                return invalid("a schedule slot points at a routine that is not in this file")
            }
            // A slot that is neither a routine nor a focus is not "rest" — rest is the absence
            // of a slot. A row like this can only be corruption.
            if (routineId == null && isBlank(slot.focusKind)) {
                return invalid("a schedule slot has neither a routine nor a focus")
            }
            if (slot.createdAt < MIN_PLAUSIBLE_EPOCH_MS || slot.updatedAt < MIN_PLAUSIBLE_EPOCH_MS) {
                return invalid("a schedule slot has an impossible timestamp")
            }
        }

        val activityIds = HashSet<String>(document.activities.size)
        document.activities.forEach { activity ->
            if (isBlank(activity.id)) return invalid("an activity is missing its id")
            if (!activityIds.add(activity.id)) {
                return invalid("two activities share the id \"${activity.id}\"")
            }
            if (activity.status !in setOf("ACTIVE", "COMPLETED")) {
                return invalid("an activity has an unknown status")
            }
            if (activity.origin !in setOf("LIVE", "BACKDATED", "IMPORTED")) {
                return invalid("an activity has an unknown origin")
            }
            if (isBlank(activity.performedStart.zoneId)) {
                return invalid("an activity is missing its time zone")
            }
            if (activity.blocks.isEmpty()) {
                return invalid("an activity has no blocks")
            }
            activity.blocks.forEach { block ->
                if (isBlank(block.id)) return invalid("an activity block is missing its id")
                if (block.kind == "STRENGTH") {
                    if (isBlank(block.exerciseName) && isBlank(block.exerciseId)) {
                        return invalid("a strength block is missing its exercise")
                    }
                    block.sets.forEach { set ->
                        if (set.setNumber < 1) return invalid("a strength set has an invalid set number")
                        if (set.reps < 0) return invalid("a strength set has negative reps")
                    }
                } else if (block.kind == "CARDIO") {
                    if ((block.elapsedSeconds ?: 0L) < 0L) {
                        return invalid("a cardio block has a negative duration")
                    }
                    if (block.sets.isNotEmpty()) {
                        return invalid("a cardio block carries strength sets")
                    }
                } else {
                    return invalid("an activity block has an unknown kind")
                }
            }
        }

        val summary = BackupSummary(
            exercises = document.exercises.size,
            routines = document.routines.size,
            sessions = document.sessions.size,
            setLogs = document.setLogs.size,
        )
        val incoming = AuthoredInventory.fromDocument(document)

        if (incoming.isEmpty && !localAuthored.isEmpty && !allowEmptyDestructiveRestore) {
            return BackupValidation.Invalid(AuthoredInventory.EMPTY_INCOMING_REFUSED)
        }

        return BackupValidation.Valid(summary)
    }

    /** Compatibility for callers that only know whether the phone has authored data. */
    fun validate(
        document: BackupDocument,
        localHasData: Boolean,
        allowEmptyDestructiveRestore: Boolean = false,
    ): BackupValidation = validate(
        document = document,
        localAuthored = if (localHasData) AuthoredInventory.PRESENT else AuthoredInventory.EMPTY,
        allowEmptyDestructiveRestore = allowEmptyDestructiveRestore,
    )

    private fun identityProblem(document: BackupDocument): String? {
        if (document.version < 1) return "This backup file is missing a valid version."
        if (document.version > BackupJson.CURRENT_VERSION) {
            return "This backup was made with a newer app version and can’t be opened here."
        }
        val app = document.app
        if (!isBlank(app) && app != BackupJson.APP_ID) {
            return "This file is not a Personal Trainer backup."
        }
        return null
    }

    private fun invalid(detail: String) = BackupValidation.Invalid("$GENERIC_CORRUPT ($detail)")

    /** Gson can leave `null` in a non-null String, so every read goes through here. */
    private fun isBlank(value: String?): Boolean = value.isNullOrBlank()

    private fun isPlausibleWeight(value: Double?): Boolean {
        if (value == null) return true
        return !value.isNaN() && !value.isInfinite() && value >= 0.0 && value < 10_000.0
    }
}
