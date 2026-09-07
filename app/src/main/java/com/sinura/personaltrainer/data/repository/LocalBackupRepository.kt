package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupExerciseMuscle
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.RestoreJournal
import com.sinura.personaltrainer.data.backup.RestoreWitness
import com.sinura.personaltrainer.data.backup.SafetySnapshot
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
import com.sinura.personaltrainer.data.backup.SafetySnapshotStore
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.backup.BackupRoutine
import com.sinura.personaltrainer.data.backup.BackupRoutineExercise
import com.sinura.personaltrainer.data.backup.BackupMeasurableGoal
import com.sinura.personaltrainer.data.backup.BackupMissedWorkDecision
import com.sinura.personaltrainer.data.backup.BackupReminderDelivery
import com.sinura.personaltrainer.data.backup.BackupScheduleOccurrence
import com.sinura.personaltrainer.data.backup.BackupScheduleRule
import com.sinura.personaltrainer.data.backup.BackupScheduleSlot
import com.sinura.personaltrainer.data.backup.BackupSession
import com.sinura.personaltrainer.data.backup.BackupSessionExercise
import com.sinura.personaltrainer.data.backup.BackupActivity
import com.sinura.personaltrainer.data.backup.BackupActivityTemplate
import com.sinura.personaltrainer.data.backup.BackupSetLog
import com.sinura.personaltrainer.data.local.AppRoomDatabase
import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.GoalDao
import com.sinura.personaltrainer.data.local.dao.PlannerDao
import com.sinura.personaltrainer.data.local.entity.MeasurableGoalEntity
import com.sinura.personaltrainer.data.local.entity.MissedWorkDecisionEntity
import com.sinura.personaltrainer.data.local.entity.ReminderDeliveryEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleOccurrenceEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.local.entity.SeedMetaEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.BlockArchive
import com.sinura.personaltrainer.domain.BodyweightLog
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.SplitStyle
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Turns the database into a backup document and back again.
 *
 * @param onBeforeRestore runs immediately before the wipe, at the single choke point every
 * restore passes through, so no caller can forget to tear down state that points at rows
 * about to be deleted (the rest timer and the in-memory workout draft).
 * @param safetySnapshotDir where a verified copy of the current data is written
 * before it is overwritten. [replaceWith] does not write that copy — [commitRestore]
 * does, so a failed snapshot can abort without touching Room.
 */
class LocalBackupRepository(
    private val database: AppRoomDatabase,
    private val preferencesRepository: PreferencesRepository,
    private val activityDao: ActivityDao? = null,
    private val plannerDao: PlannerDao? = null,
    private val goalDao: GoalDao? = null,
    private val onBeforeRestore: suspend () -> Unit = {},
    safetySnapshotDir: File? = null,
    clock: () -> Long = { System.currentTimeMillis() },
) {
    private val safetySnapshots: SafetySnapshotStore? =
        safetySnapshotDir?.let { SafetySnapshotStore(it, clock) }
    /**
     * A consistent point-in-time export.
     *
     * The six table reads run inside one transaction: read outside one and a write landing
     * between them can produce a file whose child rows reference a parent that is not in it.
     * Restore enforces foreign keys, so such a file fails wholesale — and it fails at the one
     * moment it is the last copy.
     *
     * Unfinished sessions are deliberately EXCLUDED, along with their exercises and sets. A
     * backup taken mid-workout would otherwise restore as a phantom "workout in progress"
     * pointing at a session the user finished on another day, and Home would offer to resume
     * it. The cost is that the in-flight session is not backed up; it is still on the phone,
     * and it will be included by the next backup once finished.
     *
     * Preference reads stay outside the transaction on purpose — they are DataStore, not
     * Room, and suspending on unrelated I/O inside a Room transaction is a deadlock risk.
     */
    suspend fun createSnapshot(): BackupDocument {
        val snapshot = database.withTransaction {
            val allSessions = database.workoutDao().getAllSessions()
            val finishedSessions = allSessions.filter { it.finishedAt != null }
            val finishedIds = finishedSessions.mapTo(HashSet()) { it.id }
            TableSnapshot(
                exercises = database.exerciseDao().getAll(),
                routines = database.routineDao().getAllRoutines(),
                routineExercises = database.routineDao().getAllRoutineExercises(),
                sessions = finishedSessions,
                sessionExercises = database.workoutDao().getAllSessionExercises()
                    .filter { it.sessionId in finishedIds },
                sets = database.workoutDao().getAllSets()
                    .filter { it.sessionId in finishedIds },
                credits = database.catalogDao().getAllCredits(),
                scheduleSlots = database.scheduleDao().getAll(),
                activityExport = activityDao?.let { ActivityBackupIo.snapshot(it) },
                plannerExport = plannerDao?.let { dao ->
                    PlannerExport(
                        rules = dao.getRules(),
                        occurrences = dao.getOccurrencesBetween(Long.MIN_VALUE, Long.MAX_VALUE),
                        decisions = dao.getDecisions(),
                        deliveries = dao.getDeliveries(),
                    )
                },
                goals = goalDao?.getAll().orEmpty(),
            )
        }
        val exercises = snapshot.exercises
        val routines = snapshot.routines
        val routineExercises = snapshot.routineExercises
        val sessions = snapshot.sessions
        val sessionExercises = snapshot.sessionExercises
        val sets = snapshot.sets
        val unit = preferencesRepository.weightUnit.first()
        val schedule = preferencesRepository.schedulePreferences.first()
        val rest = preferencesRepository.restTimerPreferences.first()
        val coach = preferencesRepository.coachPreferences.first()
        val heatWindow = preferencesRepository.heatWindow.first()
        val bodyweightKg = preferencesRepository.bodyweightKg.first()
        val onboardingComplete = preferencesRepository.onboardingComplete.first()
        val dismissedCollisions = preferencesRepository.dismissedCollisionIds.first()
        val block = preferencesRepository.trainingBlock.first()
        val pastBlocks = preferencesRepository.pastBlocks.first()
        val bodyweightLog = preferencesRepository.bodyweightLog.first()
        return BackupDocument(
            version = BackupJson.CURRENT_VERSION,
            app = BackupJson.APP_ID,
            exportedAt = BackupJson.nowIso(),
            preferences = BackupPreferences(
                weightUnit = unit.storageKey,
                trainingDaysPerWeek = schedule.trainingDaysPerWeek,
                splitStyle = schedule.splitStyle.storageKey,
                weekStart = schedule.weekStart.name,
                restSoundEnabled = rest.soundEnabled,
                restVibrationEnabled = rest.vibrationEnabled,
                defaultRestSeconds = rest.defaultRestSeconds,
                trainingGoal = coach.goal.name,
                trainingEmphasis = coach.emphasis.name,
                availableEquipment = coach.availableEquipment.sorted(),
                heatWindow = heatWindow.name,
                bodyweightKg = bodyweightKg,
                onboardingComplete = onboardingComplete,
                // Sorted so two exports of the same state produce byte-identical documents,
                // which is what makes a backup diffable and a round-trip test meaningful.
                dismissedCollisionIds = dismissedCollisions.sorted(),
                blockStartEpochDay = block?.startEpochDay,
                blockWeeks = block?.weeks ?: TrainingBlock.DEFAULT_WEEKS,
                pastBlocks = BlockArchive.encode(pastBlocks),
                bodyweightLog = BodyweightLog.encode(bodyweightLog),
                trainingAge = preferencesRepository.trainingAge.first().name,
                preferredDays = preferencesRepository.preferredDays.first()
                    .map { it.name }
                    .sorted(),
                trainingPlace = TrainingPlace.formatPlaces(
                    preferencesRepository.storedOnboardingAnswers().resolvedPlaces(),
                ),
                lighterWeekStartEpochDay = preferencesRepository.lighterWeekStartEpochDay.first(),
                reminderOptOut = preferencesRepository.reminderPreferences.first().optOut,
                reminderQuietStartHour = preferencesRepository.reminderPreferences.first().quietStartHour,
                reminderQuietEndHour = preferencesRepository.reminderPreferences.first().quietEndHour,
                clockFormat = preferencesRepository.clockFormat.first().storageKey,
                bodyweightCheckInWeekday = preferencesRepository.bodyweightCheckInWeekday.first()?.name.orEmpty(),
            ),
            exercises = exercises.map {
                BackupExercise(
                    id = it.id,
                    name = it.name,
                    muscleGroup = it.muscleGroup,
                    notes = it.notes,
                    isCustom = it.isCustom,
                    equipment = it.equipment,
                    loadType = it.loadType,
                    movementKey = it.movementKey,
                    imageKey = it.imageKey,
                    // nameKey is deliberately absent: it is derived, and restore recomputes it.
                )
            },
            routines = routines.map {
                BackupRoutine(it.id, it.name, it.notes, it.createdAt, it.updatedAt)
            },
            routineExercises = routineExercises.map {
                BackupRoutineExercise(
                    id = it.id,
                    routineId = it.routineId,
                    exerciseId = it.exerciseId,
                    sortOrder = it.sortOrder,
                    targetSets = it.targetSets,
                    targetReps = it.targetReps,
                    targetWeightKg = it.targetWeightKg,
                    restSeconds = it.restSeconds,
                )
            },
            sessions = sessions.map {
                BackupSession(
                    id = it.id,
                    routineId = it.routineId,
                    routineName = it.routineName,
                    date = it.date,
                    notes = it.notes,
                    durationMinutes = it.durationMinutes,
                    startedAt = it.startedAt,
                    finishedAt = it.finishedAt,
                )
            },
            sessionExercises = sessionExercises.map {
                BackupSessionExercise(
                    id = it.id,
                    sessionId = it.sessionId,
                    exerciseId = it.exerciseId,
                    sortOrder = it.sortOrder,
                    targetSets = it.targetSets,
                    targetReps = it.targetReps,
                    targetWeightKg = it.targetWeightKg,
                    restSeconds = it.restSeconds,
                )
            },
            setLogs = sets.map {
                BackupSetLog(
                    id = it.id,
                    sessionId = it.sessionId,
                    exerciseId = it.exerciseId,
                    setNumber = it.setNumber,
                    weightKg = it.weightKg,
                    reps = it.reps,
                    rpe = it.rpe,
                    isWarmup = it.isWarmup,
                    completedAt = it.completedAt,
                )
            },
            exerciseMuscles = snapshot.credits.map {
                BackupExerciseMuscle(
                    exerciseId = it.exerciseId,
                    muscleKey = it.muscleKey,
                    weight = it.weight,
                )
            },
            scheduleSlots = snapshot.scheduleSlots.map {
                BackupScheduleSlot(
                    id = it.id,
                    position = it.position,
                    routineId = it.routineId,
                    focusKind = it.focusKind,
                    anchorDay = it.anchorDay,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            activities = snapshot.activityExport?.first ?: emptyList(),
            activityTemplates = snapshot.activityExport?.second ?: emptyList(),
            scheduleRules = snapshot.plannerExport?.rules.orEmpty().map { it.toBackup() },
            scheduleOccurrences = snapshot.plannerExport?.occurrences.orEmpty().map { it.toBackup() },
            missedWorkDecisions = snapshot.plannerExport?.decisions.orEmpty().map { it.toBackup() },
            reminderDeliveries = snapshot.plannerExport?.deliveries.orEmpty().map { it.toBackup() },
            measurableGoals = snapshot.goals.map { it.toBackup() },
        )
    }

    /** Authored rows on this phone, including DataStore-only bodyweight and blocks. */
    suspend fun authoredInventory(): AuthoredInventory {
        val room = database.withTransaction {
            RoomAuthored(
                sessions = database.workoutDao().getAllSessions().size,
                setLogs = database.workoutDao().getAllSets().size,
                routines = database.routineDao().getAllRoutines().size,
                customExercises = database.exerciseDao().getAll().count { it.isCustom },
                scheduleSlots = database.scheduleDao().count(),
                activities = activityDao?.sessionCount() ?: 0,
            )
        }
        val log = preferencesRepository.bodyweightLog.first()
        val currentKg = preferencesRepository.bodyweightKg.first()
        val bodyweight = when {
            log.isNotEmpty() -> log.size
            currentKg != null -> 1
            else -> 0
        }
        val currentBlock = preferencesRepository.trainingBlock.first()
        val pastBlocks = preferencesRepository.pastBlocks.first()
        return AuthoredInventory(
            sessions = room.sessions,
            setLogs = room.setLogs,
            routines = room.routines,
            customExercises = room.customExercises,
            scheduleSlots = room.scheduleSlots,
            activities = room.activities,
            bodyweightEntries = bodyweight,
            blocks = pastBlocks.size + if (currentBlock != null) 1 else 0,
        )
    }

    /** True when the phone holds anything worth protecting from an empty restore. */
    suspend fun hasLocalData(): Boolean = !authoredInventory().isEmpty

    suspend fun inProgressSessionId(): String? =
        database.workoutDao().getInProgressSession()?.id
            ?: activityDao?.getLive()?.id

    /**
     * Overwrites everything. The only irreversible operation in the app.
     *
     * Callers MUST have run [BackupValidator] on the document first: foreign keys are
     * enforced inside the transaction, so a bad file rolls back rather than half-writing, but
     * an empty or nonsense document that satisfies the schema would still wipe the phone.
     *
     * @return whether preferences were restored alongside the data.
     */
    suspend fun replaceWith(document: BackupDocument): RestoreOutcome {
        replaceRoom(document)
        return RestoreOutcome(preferencesRestored = applyPreferences(document))
    }

    /**
     * Room wipe + write only. Preferences stay outside so a journal can
     * finish them after process death.
     */
    suspend fun replaceRoom(document: BackupDocument) {
        if (document.version > BackupJson.CURRENT_VERSION) {
            throw BackupException("This backup was made with a newer app version and can’t be opened here.")
        }
        // Stop anything holding a session id that is about to stop existing.
        onBeforeRestore()
        database.withTransaction {
            // Children before parents, all the way down. schedule_slots hangs off routines and
            // exercise_muscles off exercises, so both have to go before the row they reference.
            database.workoutDao().deleteAllSets()
            database.workoutDao().deleteAllSessionExercises()
            database.workoutDao().deleteAllSessions()
            plannerDao?.deleteAllDeliveries()
            plannerDao?.deleteAllOccurrences()
            plannerDao?.deleteAllDecisions()
            plannerDao?.deleteAllRules()
            goalDao?.deleteAll()
            database.scheduleDao().deleteAll()
            database.routineDao().deleteAllRoutineExercises()
            database.routineDao().deleteAllRoutines()
            database.catalogDao().deleteAllCredits()
            database.exerciseDao().deleteAll()

            if (document.exercises.isNotEmpty()) {
                database.exerciseDao().replaceAll(
                    document.exercises.map {
                        ExerciseEntity(
                            id = it.id,
                            name = it.name,
                            muscleGroup = it.muscleGroup,
                            notes = it.notes,
                            isCustom = it.isCustom,
                            equipment = it.equipment ?: EquipmentType.OTHER.name,
                            loadType = it.loadType ?: LoadType.EXTERNAL.name,
                            movementKey = it.movementKey,
                            imageKey = it.imageKey,
                            // Recomputed, never read from the file: one function owns nameKey.
                            nameKey = MuscleNormalizer.nameKeyOf(it.name),
                        )
                    },
                )
            }
            if (document.routines.isNotEmpty()) {
                database.routineDao().replaceRoutines(
                    document.routines.map {
                        RoutineEntity(it.id, it.name, it.notes, it.createdAt, it.updatedAt)
                    },
                )
            }
            if (document.routineExercises.isNotEmpty()) {
                database.routineDao().replaceRoutineExercises(
                    document.routineExercises.map {
                        RoutineExerciseEntity(
                            id = it.id,
                            routineId = it.routineId,
                            exerciseId = it.exerciseId,
                            sortOrder = it.sortOrder,
                            targetSets = it.targetSets,
                            targetReps = it.targetReps,
                            targetWeightKg = it.targetWeightKg,
                            restSeconds = it.restSeconds,
                        )
                    },
                )
            }
            if (document.scheduleSlots.isNotEmpty()) {
                database.scheduleDao().replaceAll(
                    document.scheduleSlots.map {
                        ScheduleSlotEntity(
                            id = it.id,
                            position = it.position,
                            routineId = it.routineId,
                            focusKind = it.focusKind,
                            anchorDay = it.anchorDay,
                            createdAt = it.createdAt,
                            updatedAt = it.updatedAt,
                        )
                    },
                )
            }
            if (document.sessions.isNotEmpty()) {
                database.workoutDao().replaceSessions(
                    document.sessions.map {
                        WorkoutSessionEntity(
                            id = it.id,
                            routineId = it.routineId,
                            routineName = it.routineName,
                            date = it.date,
                            notes = it.notes,
                            durationMinutes = it.durationMinutes,
                            startedAt = it.startedAt,
                            finishedAt = it.finishedAt,
                        )
                    },
                )
            }
            if (document.sessionExercises.isNotEmpty()) {
                database.workoutDao().insertSessionExercises(
                    document.sessionExercises.map {
                        SessionExerciseEntity(
                            id = it.id,
                            sessionId = it.sessionId,
                            exerciseId = it.exerciseId,
                            sortOrder = it.sortOrder,
                            targetSets = it.targetSets,
                            targetReps = it.targetReps,
                            targetWeightKg = it.targetWeightKg,
                            restSeconds = it.restSeconds,
                        )
                    },
                )
            }
            if (document.setLogs.isNotEmpty()) {
                database.workoutDao().replaceSets(
                    document.setLogs.map {
                        SetLogEntity(
                            id = it.id,
                            sessionId = it.sessionId,
                            exerciseId = it.exerciseId,
                            setNumber = it.setNumber,
                            weightKg = it.weightKg,
                            reps = it.reps,
                            rpe = it.rpe,
                            isWarmup = it.isWarmup,
                            completedAt = it.completedAt,
                        )
                    },
                )
            }
            if (document.exerciseMuscles.isNotEmpty()) {
                database.catalogDao().insertCredits(
                    document.exerciseMuscles.map {
                        ExerciseMuscleEntity(
                            exerciseId = it.exerciseId,
                            muscleKey = it.muscleKey,
                            weight = it.weight,
                        )
                    },
                )
            }
            // seed_meta is never trusted across a restore: whatever catalog version the backup
            // was taken at says nothing about the catalog this build ships. Zero forces the
            // reconciliation pass that every restore ends with.
            database.catalogDao().upsertSeedMeta(
                SeedMetaEntity(id = 1, catalogVersion = 0, pendingCollisions = "[]"),
            )
            activityDao?.let { ActivityBackupIo.replace(it, document) }
            plannerDao?.let { dao ->
                if (document.scheduleRules.isNotEmpty()) {
                    dao.upsertRules(
                        document.scheduleRules.map { row ->
                            ScheduleRuleEntity(
                                id = row.id,
                                weekday = row.weekday,
                                hour = row.hour,
                                minute = row.minute,
                                modality = row.modality,
                                zonePolicy = row.zonePolicy,
                                fixedZoneId = row.fixedZoneId,
                                routineId = row.routineId,
                                templateId = row.templateId,
                                focusKind = row.focusKind,
                                reminderOffsetMinutes = row.reminderOffsetMinutes,
                                enabled = if (row.enabled) 1 else 0,
                                createdAtMs = row.createdAtMs,
                                updatedAtMs = row.updatedAtMs,
                            )
                        },
                    )
                }
                if (document.scheduleOccurrences.isNotEmpty()) {
                    dao.upsertOccurrences(
                        document.scheduleOccurrences.map { row ->
                            ScheduleOccurrenceEntity(
                                id = row.id,
                                ruleId = row.ruleId,
                                status = row.status,
                                instantMs = row.instantMs,
                                zoneId = row.zoneId,
                                offsetSeconds = row.offsetSeconds,
                                localEpochDay = row.localEpochDay,
                                hour = row.hour,
                                minute = row.minute,
                                completedActivityId = row.completedActivityId,
                                createdAtMs = row.createdAtMs,
                                updatedAtMs = row.updatedAtMs,
                            )
                        },
                    )
                }
                if (document.missedWorkDecisions.isNotEmpty()) {
                    document.missedWorkDecisions.forEach { row ->
                        dao.upsertDecision(
                            MissedWorkDecisionEntity(
                                weekStartEpochDay = row.weekStartEpochDay,
                                choice = row.choice,
                                decidedAtMs = row.decidedAtMs,
                            ),
                        )
                    }
                }
                if (document.reminderDeliveries.isNotEmpty()) {
                    dao.upsertDeliveries(
                        document.reminderDeliveries.map { row ->
                            ReminderDeliveryEntity(
                                id = row.id,
                                occurrenceId = row.occurrenceId,
                                scheduledAtMs = row.scheduledAtMs,
                                status = row.status,
                                createdAtMs = row.createdAtMs,
                                updatedAtMs = row.updatedAtMs,
                            )
                        },
                    )
                }
            }
            goalDao?.let { dao ->
                if (document.measurableGoals.isNotEmpty()) {
                    dao.upsertAll(document.measurableGoals.map { it.toEntity() })
                }
            }
        }
    }

    suspend fun applyPreferences(document: BackupDocument): Boolean {
        return try {
            preferencesRepository.setRestoredPreferences(
                unit = WeightUnit.fromStorage(document.preferences.weightUnit),
                schedule = SchedulePreferences(
                    trainingDaysPerWeek = document.preferences.trainingDaysPerWeek,
                    splitStyle = SplitStyle.fromStorage(document.preferences.splitStyle),
                    weekStart = SchedulePreferences.weekStartFromStorage(document.preferences.weekStart),
                ),
                rest = RestTimerPreferences(
                    soundEnabled = document.preferences.restSoundEnabled,
                    vibrationEnabled = document.preferences.restVibrationEnabled,
                    defaultRestSeconds = document.preferences.defaultRestSeconds,
                ),
                coach = CoachPreferences(
                    goal = TrainingGoal.fromStorage(document.preferences.trainingGoal),
                    availableEquipment = document.preferences.availableEquipment.toSet(),
                    emphasis = TrainingEmphasis.fromStorage(document.preferences.trainingEmphasis),
                ),
                heatWindow = HeatWindow.fromStorage(document.preferences.heatWindow),
                bodyweightKg = document.preferences.bodyweightKg,
                onboardingComplete = document.hasBeenSetUp(),
                dismissedCollisionIds = document.preferences.dismissedCollisionIds.toSet(),
                block = document.preferences.blockStartEpochDay?.let { start ->
                    TrainingBlock(startEpochDay = start, weeks = document.preferences.blockWeeks)
                },
                pastBlocks = BlockArchive.decode(document.preferences.pastBlocks),
                bodyweightLog = BodyweightLog.decode(document.preferences.bodyweightLog),
                trainingAge = TrainingAge.fromStorage(
                    document.preferences.trainingAge.takeIf { it.isNotBlank() },
                ),
                preferredDays = document.preferences.preferredDays.mapNotNull { raw ->
                    Weekday.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                }.toSet(),
                trainingPlace = document.preferences.trainingPlace.takeIf { it.isNotBlank() }
                    ?.let { TrainingPlace.fromStorage(it) }
                    ?: OnboardingAnswers.inferPlace(
                        document.preferences.availableEquipment.toSet(),
                    ),
                trainingPlaces = TrainingPlace.parsePlaces(document.preferences.trainingPlace),
                lighterWeekStartEpochDay = document.preferences.lighterWeekStartEpochDay,
                reminderOptOut = document.preferences.reminderOptOut,
                reminderQuietStartHour = document.preferences.reminderQuietStartHour,
                reminderQuietEndHour = document.preferences.reminderQuietEndHour,
                clockFormat = ClockFormat.fromStorage(document.preferences.clockFormat),
                bodyweightCheckInWeekday = Weekday.fromStorage(
                    document.preferences.bodyweightCheckInWeekday.takeIf { it.isNotBlank() },
                ),
            )
            true
        } catch (thrown: kotlinx.coroutines.CancellationException) {
            // Was swallowed here as "false", so a cancelled restore reported that settings
            // could not be applied and then went on to close the journal. Cancellation is
            // the caller's to see; the journal stays where it is and recovery finishes it.
            throw thrown
        } catch (thrown: Exception) {
            AppLog.w(TAG, "Applying restored preferences failed", thrown)
            false
        }
    }

    /** The legacy count witness; kept only to resolve a journal written before [roomWitness]. */
    suspend fun roomFingerprint(): String {
        val authored = authoredInventory()
        val firstId = database.withTransaction {
            database.workoutDao().getAllSessions().minByOrNull { it.id }?.id
        }
        return RestoreJournal.fingerprint(authored, firstId)
    }

    /**
     * What Room holds right now, as the same content digest the journal stores for the
     * incoming document. Reads through [createSnapshot] so both sides of the comparison
     * take the identical projection — finished sessions and non-live activities only.
     */
    suspend fun roomWitness(): String = RestoreWitness.of(createSnapshot())

    /**
     * Encode the current phone, write it, re-read it, and refuse unless the
     * authored counts match. Restore must call this before [replaceWith].
     *
     * @param current the phone's snapshot when the caller already took one — restore does,
     * because the same document is also its before-witness — else taken here.
     */
    suspend fun writeVerifiedSafetySnapshot(current: BackupDocument? = null): SafetySnapshotMeta {
        val store = safetySnapshots ?: throw BackupException(SafetySnapshot.MISSING_DIR)
        val expected = authoredInventory()
        val json = BackupJson.encode(current ?: createSnapshot())
        return store.writeVerified(json, expected)
    }

    fun listSafetySnapshots(): List<SafetySnapshotMeta> =
        safetySnapshots?.list() ?: emptyList()

    fun readSafetySnapshot(id: String): String {
        val store = safetySnapshots ?: throw BackupException(SafetySnapshot.MISSING_DIR)
        return store.readJson(id)
    }

    fun deleteSafetySnapshot(id: String) {
        val store = safetySnapshots ?: throw BackupException(SafetySnapshot.NOT_FOUND)
        store.delete(id)
    }

    private companion object {
        const val TAG = "PT/LocalBackup"
    }

    private data class TableSnapshot(
        val exercises: List<ExerciseEntity>,
        val routines: List<RoutineEntity>,
        val routineExercises: List<RoutineExerciseEntity>,
        val sessions: List<WorkoutSessionEntity>,
        val sessionExercises: List<SessionExerciseEntity>,
        val sets: List<SetLogEntity>,
        val credits: List<ExerciseMuscleEntity>,
        val scheduleSlots: List<ScheduleSlotEntity>,
        val activityExport: Pair<List<BackupActivity>, List<BackupActivityTemplate>>?,
        val plannerExport: PlannerExport?,
        val goals: List<MeasurableGoalEntity> = emptyList(),
    )
}

private data class PlannerExport(
    val rules: List<ScheduleRuleEntity>,
    val occurrences: List<ScheduleOccurrenceEntity>,
    val decisions: List<MissedWorkDecisionEntity>,
    val deliveries: List<ReminderDeliveryEntity>,
)

private fun ScheduleRuleEntity.toBackup() = BackupScheduleRule(
    id = id,
    weekday = weekday,
    hour = hour,
    minute = minute,
    modality = modality,
    zonePolicy = zonePolicy,
    fixedZoneId = fixedZoneId,
    routineId = routineId,
    templateId = templateId,
    focusKind = focusKind,
    reminderOffsetMinutes = reminderOffsetMinutes,
    enabled = enabled != 0,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

private fun ScheduleOccurrenceEntity.toBackup() = BackupScheduleOccurrence(
    id = id,
    ruleId = ruleId,
    status = status,
    instantMs = instantMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
    localEpochDay = localEpochDay,
    hour = hour,
    minute = minute,
    completedActivityId = completedActivityId,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

private fun MissedWorkDecisionEntity.toBackup() = BackupMissedWorkDecision(
    weekStartEpochDay = weekStartEpochDay,
    choice = choice,
    decidedAtMs = decidedAtMs,
)

private fun MeasurableGoalEntity.toBackup() = BackupMeasurableGoal(
    id = id,
    kind = kind,
    targetValue = targetValue,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    period = period,
    instantMs = instantMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
    localEpochDay = localEpochDay,
    paused = paused,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

private fun BackupMeasurableGoal.toEntity() = MeasurableGoalEntity(
    id = id,
    kind = kind,
    targetValue = targetValue,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    period = period,
    instantMs = instantMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
    localEpochDay = localEpochDay,
    paused = paused,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

private fun ReminderDeliveryEntity.toBackup() = BackupReminderDelivery(
    id = id,
    occurrenceId = occurrenceId,
    scheduledAtMs = scheduledAtMs,
    status = status,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

private data class RoomAuthored(
    val sessions: Int,
    val setLogs: Int,
    val routines: Int,
    val customExercises: Int,
    val scheduleSlots: Int,
    val activities: Int,
)

data class RestoreOutcome(
    val preferencesRestored: Boolean,
)
