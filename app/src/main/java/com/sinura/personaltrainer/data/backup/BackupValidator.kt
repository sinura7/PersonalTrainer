package com.sinura.personaltrainer.data.backup

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

    private const val GENERIC_CORRUPT =
        "This backup file is damaged or incomplete, so nothing was changed."

    /**
     * @param localHasData whether the phone currently holds training data. A document that is
     * structurally valid but empty is the most dangerous file there is — it would silently
     * erase everything — so it is refused unless [allowEmptyDestructiveRestore] is set by an
     * explicit user confirmation.
     */
    fun validate(
        document: BackupDocument,
        localHasData: Boolean,
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

        val summary = BackupSummary(
            exercises = document.exercises.size,
            routines = document.routines.size,
            sessions = document.sessions.size,
            setLogs = document.setLogs.size,
        )

        if (summary.isEmpty && localHasData && !allowEmptyDestructiveRestore) {
            return BackupValidation.Invalid(
                "This backup is empty. Restoring it would erase everything on this phone, " +
                    "so it was refused. Pick a different file.",
            )
        }

        return BackupValidation.Valid(summary)
    }

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
