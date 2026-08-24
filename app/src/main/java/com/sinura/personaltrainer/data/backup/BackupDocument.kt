package com.sinura.personaltrainer.data.backup

data class BackupDocument(
    val version: Int = BackupJson.CURRENT_VERSION,
    val app: String = BackupJson.APP_ID,
    val exportedAt: String,
    val preferences: BackupPreferences,
    val exercises: List<BackupExercise>,
    val routines: List<BackupRoutine>,
    val routineExercises: List<BackupRoutineExercise>,
    val sessions: List<BackupSession>,
    val sessionExercises: List<BackupSessionExercise>,
    val setLogs: List<BackupSetLog>,
    /**
     * v2 additions. Defaulted to empty so a v1 file — which has neither array — decodes into a
     * complete document rather than failing, and so every existing construction site keeps
     * compiling. `decode` fills [exerciseMuscles] for a v1 document by deriving it.
     */
    val exerciseMuscles: List<BackupExerciseMuscle> = emptyList(),
    val scheduleSlots: List<BackupScheduleSlot> = emptyList(),
    /**
     * Foundation-generation activities (P5.5). Defaulted so a v1/v2 file
     * decodes instead of failing. Live (ACTIVE) activities are excluded
     * from export, same rule as unfinished workout sessions.
     */
    val activities: List<BackupActivity> = emptyList(),
    val activityTemplates: List<BackupActivityTemplate> = emptyList(),
    /**
     * v4 planner tables (P7.1). Defaulted so a v1–v3 file decodes instead
     * of failing. Live reminder deliveries restore as PENDING only when
     * still in the future; the importer leaves status as stored.
     */
    val scheduleRules: List<BackupScheduleRule> = emptyList(),
    val scheduleOccurrences: List<BackupScheduleOccurrence> = emptyList(),
    val missedWorkDecisions: List<BackupMissedWorkDecision> = emptyList(),
    val reminderDeliveries: List<BackupReminderDelivery> = emptyList(),
    /** v5 measurable goals (P8.2). Defaulted so v1–v4 files still decode. */
    val measurableGoals: List<BackupMeasurableGoal> = emptyList(),
) {
    /**
     * Whether the owner of this backup has plainly already been through the guided setup.
     *
     * [BackupPreferences.onboardingComplete] is the direct answer, but it only exists in
     * documents written after the guided setup shipped. An older file decodes that field to
     * `false`, and restoring it verbatim would drop a lifter with a year of history into a
     * questionnaire — the exact failure the field was added to prevent, arriving through the
     * other door. So a document that carries any routine or any session counts as set up
     * regardless of the flag: nobody accumulates either without having made the choice.
     *
     * The remaining `false` case is the honest one — an empty document from a phone that
     * genuinely never finished setup — and that one should still go to setup.
     */
    fun hasBeenSetUp(): Boolean =
        preferences.onboardingComplete ||
            routines.isNotEmpty() ||
            sessions.isNotEmpty() ||
            activities.isNotEmpty()
}

/**
 * The settings a restore should bring with it.
 *
 * Not every preference belongs here, but every one that is a statement about the LIFTER rather
 * than about this handset does. The five fields below the rest timer were added after the guided
 * setup shipped and a restore was found to drop them: a new phone came up not knowing the
 * owner's goal, their equipment, their bodyweight — and, worst of the five, not knowing they had
 * already been through setup, so it asked them the six questions again on a phone that already
 * had their whole training history on it.
 *
 * Nothing here is version-gated. [BackupJson.parsePreferences] reads every field individually
 * with an explicit default, so a v1 or v2 document missing all five decodes cleanly and simply
 * keeps the defaults.
 */
data class BackupPreferences(
    val weightUnit: String,
    val trainingDaysPerWeek: Int = 4,
    val splitStyle: String = "auto",
    val weekStart: String = "MONDAY",
    val restSoundEnabled: Boolean = true,
    val restVibrationEnabled: Boolean = true,
    val defaultRestSeconds: Int = 90,
    val trainingGoal: String = "GENERAL",
    val trainingEmphasis: String = "BALANCED",
    val availableEquipment: List<String> = emptyList(),
    val heatWindow: String = "CURRENT_WEEK",
    /** Null is a real value: it means "never told us", not "weighs nothing". */
    val bodyweightKg: Double? = null,
    /**
     * Restoring onto a new phone must not re-run the guided setup. The owner has a full history
     * in front of them; being asked how many days a week they can train is the app failing to
     * notice that.
     */
    val onboardingComplete: Boolean = false,
    /**
     * Name collisions already waved through. Phase 7 recorded these as device-local because the
     * backup did not carry preferences it could hang them on; it does, so they travel. Being
     * asked again about a decision already made is the same failure as the setup one above.
     */
    val dismissedCollisionIds: List<String> = emptyList(),
    /**
     * The training block, when there is one. Null means "not in a block", which is a real
     * answer — someone who built their routines by hand never started one.
     *
     * It travels for the same reason the setup flag does: a block is a horizon and a review
     * date, and a restore that dropped it would put a lifter back at week one of nothing with
     * eleven weeks of the work already behind them.
     */
    val blockStartEpochDay: Long? = null,
    val blockWeeks: Int = 12,
    /**
     * Finished blocks, as "start:weeks" pairs — see BlockArchive.
     *
     * A string rather than a list of objects because that is what it is stored as, and because
     * a backup field that needs its own nested shape is a backup field that needs its own
     * version gate. Unreadable content decodes to no blocks, never to a failed restore.
     */
    val pastBlocks: String = "",
    /** Weigh-ins as "epochDay:kg" pairs — see BodyweightLog. Unreadable content decodes to none. */
    val bodyweightLog: String = "",
    /**
     * The three setup answers that used to die at accept. Empty string / empty list means
     * "this file predates them" — restore infers, it does not fail.
     */
    val trainingAge: String = "",
    val preferredDays: List<String> = emptyList(),
    val trainingPlace: String = "",
    /** Null means no lighter week is marked. A past value is inert to readers. */
    val lighterWeekStartEpochDay: Long? = null,
    val reminderOptOut: Boolean = false,
    val reminderQuietStartHour: Int = 22,
    val reminderQuietEndHour: Int = 7,
)

/**
 * The four v2 fields are nullable even though the columns behind them are not.
 *
 * Gson does not run Kotlin constructors, so a JSON null lands in a non-null Kotlin field as
 * null anyway and blows up at the first read — the type says one thing and the object holds
 * another. Declaring them nullable makes that state expressible, and `decode` normalizes every
 * one of them in a single step before anything else sees the document.
 *
 * `nameKey` is deliberately NOT in the document: it is derived from the name, and a stored copy
 * is one more thing that can arrive stale. Restore recomputes it.
 */
data class BackupExercise(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val notes: String,
    val isCustom: Boolean,
    val equipment: String? = null,
    val loadType: String? = null,
    val movementKey: String? = null,
    val imageKey: String? = null,
)

data class BackupExerciseMuscle(
    val exerciseId: String,
    val muscleKey: String,
    val weight: Double,
)

data class BackupScheduleSlot(
    val id: String,
    val position: Int,
    val routineId: String?,
    val focusKind: String?,
    val anchorDay: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupRoutine(
    val id: String,
    val name: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BackupRoutineExercise(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class BackupSession(
    val id: String,
    val routineId: String?,
    val routineName: String?,
    val date: Long,
    val notes: String,
    val durationMinutes: Int,
    val startedAt: Long,
    val finishedAt: Long?,
)

data class BackupSessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
)

data class BackupSetLog(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val completedAt: Long,
)

data class BackupCapturedTime(
    val instantMs: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val localEpochDay: Long,
)

data class BackupActivityMuscle(
    val muscleKey: String,
    val weight: Double,
)

data class BackupStrengthSet(
    val id: String,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Int?,
    val isWarmup: Boolean,
    val completedAtMs: Long,
)

data class BackupCardioInterval(
    val id: String,
    val sortOrder: Int,
    val elapsedSeconds: Long,
    val distanceMeters: Double?,
    val rpe: Int?,
)

data class BackupActivityBlock(
    val id: String,
    val sortOrder: Int,
    val kind: String,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val loadType: String? = null,
    val equipment: String? = null,
    val muscles: List<BackupActivityMuscle> = emptyList(),
    val sets: List<BackupStrengthSet> = emptyList(),
    val cardioType: String? = null,
    val indoor: Boolean? = null,
    val elapsedSeconds: Long? = null,
    val movingSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val elevationMeters: Double? = null,
    val heartRateBpm: Int? = null,
    val energyKj: Double? = null,
    val rpe: Int? = null,
    val routeRef: String? = null,
    val intervals: List<BackupCardioInterval> = emptyList(),
)

data class BackupActivity(
    val id: String,
    val status: String,
    val origin: String,
    val source: String,
    val title: String,
    val notes: String,
    val performedStart: BackupCapturedTime,
    val performedEnd: BackupCapturedTime? = null,
    val templateId: String? = null,
    val occurrenceId: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val revision: Long,
    val blocks: List<BackupActivityBlock> = emptyList(),
)

data class BackupActivityTemplate(
    val id: String,
    val title: String,
    val notes: String,
    val blocks: List<BackupActivityBlock> = emptyList(),
)

data class BackupScheduleRule(
    val id: String,
    val weekday: Int,
    val hour: Int,
    val minute: Int,
    val modality: String,
    val zonePolicy: String = "FOLLOW_DEVICE",
    val fixedZoneId: String? = null,
    val routineId: String? = null,
    val templateId: String? = null,
    val focusKind: String? = null,
    val reminderOffsetMinutes: Int = 0,
    val enabled: Boolean = true,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class BackupScheduleOccurrence(
    val id: String,
    val ruleId: String,
    val status: String,
    val instantMs: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val localEpochDay: Long,
    val hour: Int,
    val minute: Int,
    val completedActivityId: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class BackupMissedWorkDecision(
    val weekStartEpochDay: Long,
    val choice: String,
    val decidedAtMs: Long,
)

data class BackupReminderDelivery(
    val id: String,
    val occurrenceId: String,
    val scheduledAtMs: Long,
    val status: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class BackupMeasurableGoal(
    val id: String,
    val kind: String,
    val targetValue: Double,
    val exerciseId: String? = null,
    val exerciseName: String? = null,
    val period: String,
    val instantMs: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val localEpochDay: Long,
    val paused: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedAtMillis: Long,
)

class BackupException(message: String) : Exception(message)
