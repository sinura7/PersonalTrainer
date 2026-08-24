package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupExerciseMuscle
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.SafetySnapshot
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
import com.sinura.personaltrainer.data.backup.SafetySnapshotStore
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.backup.BackupRoutine
import com.sinura.personaltrainer.data.backup.BackupRoutineExercise
import com.sinura.personaltrainer.data.backup.BackupScheduleSlot
import com.sinura.personaltrainer.data.backup.BackupSession
import com.sinura.personaltrainer.data.backup.BackupSessionExercise
import com.sinura.personaltrainer.data.backup.BackupSetLog
import com.sinura.personaltrainer.data.local.TrainerDatabase
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
import java.time.DayOfWeek
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
    private val database: TrainerDatabase,
    private val preferencesRepository: PreferencesRepository,
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
            bodyweightEntries = bodyweight,
            blocks = pastBlocks.size + if (currentBlock != null) 1 else 0,
        )
    }

    /** True when the phone holds anything worth protecting from an empty restore. */
    suspend fun hasLocalData(): Boolean = !authoredInventory().isEmpty

    suspend fun inProgressSessionId(): String? =
        database.workoutDao().getInProgressSession()?.id

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
        }
        // The data is safe at this point. Preferences are a single atomic write, and a
        // failure here is reported as a warning rather than failing the whole restore —
        // losing a kg/lbs setting is not worth discarding a recovered training history.
        val preferencesRestored = try {
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
                    DayOfWeek.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                }.toSet(),
                trainingPlace = document.preferences.trainingPlace.takeIf { it.isNotBlank() }
                    ?.let { TrainingPlace.fromStorage(it) }
                    ?: OnboardingAnswers.inferPlace(
                        document.preferences.availableEquipment.toSet(),
                    ),
                trainingPlaces = TrainingPlace.parsePlaces(document.preferences.trainingPlace),
                lighterWeekStartEpochDay = document.preferences.lighterWeekStartEpochDay,
            )
            true
        } catch (_: Exception) {
            false
        }
        return RestoreOutcome(preferencesRestored = preferencesRestored)
    }

    /**
     * Encode the current phone, write it, re-read it, and refuse unless the
     * authored counts match. Restore must call this before [replaceWith].
     */
    suspend fun writeVerifiedSafetySnapshot(): SafetySnapshotMeta {
        val store = safetySnapshots ?: throw BackupException(SafetySnapshot.MISSING_DIR)
        val expected = authoredInventory()
        val json = BackupJson.encode(createSnapshot())
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

    private data class TableSnapshot(
        val exercises: List<ExerciseEntity>,
        val routines: List<RoutineEntity>,
        val routineExercises: List<RoutineExerciseEntity>,
        val sessions: List<WorkoutSessionEntity>,
        val sessionExercises: List<SessionExerciseEntity>,
        val sets: List<SetLogEntity>,
        val credits: List<ExerciseMuscleEntity>,
        val scheduleSlots: List<ScheduleSlotEntity>,
    )
}

private data class RoomAuthored(
    val sessions: Int,
    val setLogs: Int,
    val routines: Int,
    val customExercises: Int,
    val scheduleSlots: Int,
)

data class RestoreOutcome(
    val preferencesRestored: Boolean,
)
