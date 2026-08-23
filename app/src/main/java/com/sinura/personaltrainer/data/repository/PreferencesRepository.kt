package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sinura.personaltrainer.domain.BlockArchive
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.BodyweightLog
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.HeatWindow
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

class PreferencesRepository(
    context: Context,
    /**
     * Tests pass a private store. The [preferencesDataStore] delegate is a process
     * singleton — a unique `filesDir` alone still shares `user_settings` and hangs
     * `edit()` / `first()` once another Robolectric test has opened it.
     */
    dataStore: DataStore<Preferences> = context.applicationContext.userSettingsDataStore,
) {
    private val dataStore = dataStore

    /**
     * The preferences stream with the one guard every reader needs: a corrupted or unreadable
     * settings file degrades to defaults instead of throwing into a collector. Read through
     * this rather than [dataStore].data directly.
     */
    private val safePreferences: Flow<Preferences> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw error
            }
        }

    val weightUnit: Flow<WeightUnit> = safePreferences
        .map { prefs -> WeightUnit.fromStorage(prefs[WEIGHT_UNIT]) }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { prefs ->
            prefs[WEIGHT_UNIT] = unit.storageKey
        }
    }

    /**
     * What the coach emphasises, and what the user actually has to lift with.
     *
     * These used to be deliberately outside the backup document, on the reasoning that they
     * describe the gym you walk into rather than your training history. The guided setup
     * settled the argument the other way: it is the setup questionnaire that writes both of
     * them, so leaving them behind meant a restore either re-asked six questions the owner had
     * already answered, or — once the "already answered" flag travelled — silently kept the
     * app's defaults with no path back to the questions. They travel.
     */
    val coachPreferences: Flow<CoachPreferences> = safePreferences
        .map { prefs ->
            CoachPreferences(
                goal = TrainingGoal.fromStorage(prefs[TRAINING_GOAL]),
                // Empty means "no filtering", never "owns nothing" — see CoachPreferences.
                availableEquipment = prefs[AVAILABLE_EQUIPMENT].orEmpty(),
                emphasis = TrainingEmphasis.fromStorage(prefs[TRAINING_EMPHASIS]),
            )
        }

    suspend fun setTrainingGoal(goal: TrainingGoal) {
        dataStore.edit { prefs -> prefs[TRAINING_GOAL] = goal.name }
    }

    suspend fun setTrainingEmphasis(emphasis: TrainingEmphasis) {
        dataStore.edit { prefs -> prefs[TRAINING_EMPHASIS] = emphasis.name }
    }

    suspend fun setAvailableEquipment(equipment: Set<String>) {
        dataStore.edit { prefs -> prefs[AVAILABLE_EQUIPMENT] = equipment }
    }

    val trainingAge: Flow<TrainingAge> = safePreferences
        .map { prefs -> TrainingAge.fromStorage(prefs[TRAINING_AGE]) }

    suspend fun setTrainingAge(age: TrainingAge) {
        dataStore.edit { prefs -> prefs[TRAINING_AGE] = age.name }
    }

    val preferredDays: Flow<Set<DayOfWeek>> = safePreferences
        .map { prefs -> preferredDaysFrom(prefs[PREFERRED_DAYS]) }

    suspend fun setPreferredDays(days: Set<DayOfWeek>) {
        dataStore.edit { prefs -> prefs[PREFERRED_DAYS] = days.map { it.name }.toSet() }
    }

    /**
     * Null means the key was never written — an install from before Job 3, or a restore of
     * a file that predates the field. Readers call [OnboardingAnswers.inferPlace] in that
     * case rather than pretending everyone trains in a full gym.
     */
    val trainingPlace: Flow<TrainingPlace?> = safePreferences
        .map { prefs -> prefs[TRAINING_PLACE]?.let { TrainingPlace.fromStorage(it) } }

    suspend fun setTrainingPlace(place: TrainingPlace) {
        setTrainingPlaces(setOf(place))
    }

    /**
     * One or many places, stored in the same key as the old single value.
     *
     * A single name is what every backup written before mixed places holds. Comma-separated
     * names are a mixed kit. Readers that only want one place take [TrainingPlace.widest].
     */
    suspend fun setTrainingPlaces(places: Set<TrainingPlace>) {
        val resolved = places.ifEmpty { setOf(TrainingPlace.FULL_GYM) }
        dataStore.edit { prefs -> prefs[TRAINING_PLACE] = TrainingPlace.formatPlaces(resolved) }
    }

    /**
     * The week-start epoch day marked lighter, or null when none is.
     *
     * A past value is inert: readers compare it to this week's start. Clearing is writing
     * null, not deleting a row that no longer matches.
     */
    val lighterWeekStartEpochDay: Flow<Long?> = safePreferences
        .map { prefs -> prefs[LIGHTER_WEEK_START] }

    suspend fun setLighterWeekStartEpochDay(epochDay: Long?) {
        dataStore.edit { prefs ->
            if (epochDay == null) {
                prefs.remove(LIGHTER_WEEK_START)
            } else {
                prefs[LIGHTER_WEEK_START] = epochDay
            }
        }
    }

    /**
     * The questionnaire, reconstructed from what is already stored.
     *
     * One snapshot so a replay tap cannot mix a just-changed goal with yesterday's days.
     */
    suspend fun storedOnboardingAnswers(): OnboardingAnswers {
        val prefs = safePreferences.first()
        val coach = CoachPreferences(
            goal = TrainingGoal.fromStorage(prefs[TRAINING_GOAL]),
            availableEquipment = prefs[AVAILABLE_EQUIPMENT].orEmpty(),
            emphasis = TrainingEmphasis.fromStorage(prefs[TRAINING_EMPHASIS]),
        )
        val placeRaw = prefs[TRAINING_PLACE]
        val parsedPlaces = TrainingPlace.parsePlaces(placeRaw)
        val place = parsedPlaces.let { found ->
            if (found.isEmpty()) {
                placeRaw?.let { TrainingPlace.fromStorage(it) }
                    ?: OnboardingAnswers.inferPlace(coach.availableEquipment)
            } else {
                TrainingPlace.widest(found)
            }
        }
        val bodyweight = prefs[BODYWEIGHT_KG]?.takeIf {
            it.isFinite() && it in OnboardingAnswers.MIN_BODYWEIGHT_KG..OnboardingAnswers.MAX_BODYWEIGHT_KG
        }
        return OnboardingAnswers.fromStored(
            trainingAge = TrainingAge.fromStorage(prefs[TRAINING_AGE]),
            daysPerWeek = prefs[TRAINING_DAYS] ?: SchedulePreferences.DEFAULT_DAYS,
            preferredDays = preferredDaysFrom(prefs[PREFERRED_DAYS]),
            place = place,
            places = parsedPlaces.ifEmpty { setOf(place) },
            goal = coach.goal,
            emphasis = coach.emphasis,
            bodyweightKg = bodyweight,
        )
    }

    /**
     * The window the body map opens on.
     *
     * Nothing persisted this before, so the map reset to a default every time the process
     * died — a preference the user re-expressed on every cold start and the app never learned.
     */
    val heatWindow: Flow<HeatWindow> = safePreferences
        .map { prefs -> HeatWindow.fromStorage(prefs[HEAT_WINDOW]) }

    suspend fun setHeatWindow(window: HeatWindow) {
        dataStore.edit { prefs -> prefs[HEAT_WINDOW] = window.name }
    }

    val schedulePreferences: Flow<SchedulePreferences> = safePreferences
        .map { prefs ->
            SchedulePreferences(
                trainingDaysPerWeek = prefs[TRAINING_DAYS] ?: SchedulePreferences.DEFAULT_DAYS,
                splitStyle = SplitStyle.fromStorage(prefs[SPLIT_STYLE]),
                weekStart = SchedulePreferences.weekStartFromStorage(prefs[WEEK_START]),
            ).sanitized()
        }

    suspend fun setTrainingDaysPerWeek(days: Int) {
        dataStore.edit { prefs ->
            prefs[TRAINING_DAYS] = days.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)
        }
    }

    suspend fun setSplitStyle(style: SplitStyle) {
        dataStore.edit { prefs ->
            prefs[SPLIT_STYLE] = style.storageKey
        }
    }

    suspend fun setWeekStart(day: DayOfWeek) {
        dataStore.edit { prefs ->
            prefs[WEEK_START] = day.name
        }
    }

    val restTimerPreferences: Flow<RestTimerPreferences> = safePreferences
        .map { prefs ->
            RestTimerPreferences(
                soundEnabled = prefs[REST_SOUND] ?: true,
                vibrationEnabled = prefs[REST_VIBRATE] ?: true,
                defaultRestSeconds = prefs[REST_DEFAULT] ?: RestTimerPreferences.DEFAULT_SECONDS,
                lastPresetSeconds = prefs[REST_LAST_PRESET],
            ).sanitized()
        }

    suspend fun setRestSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REST_SOUND] = enabled }
    }

    suspend fun setRestVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[REST_VIBRATE] = enabled }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[REST_DEFAULT] = seconds.coerceIn(RestTimerPreferences.MIN_SECONDS, RestTimerPreferences.MAX_SECONDS)
        }
    }

    suspend fun setLastRestPresetSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[REST_LAST_PRESET] = seconds.coerceIn(RestTimerPreferences.MIN_SECONDS, RestTimerPreferences.MAX_SECONDS)
        }
    }

    suspend fun setRestTimerPreferences(value: RestTimerPreferences) {
        val clean = value.sanitized()
        dataStore.edit { prefs ->
            prefs[REST_SOUND] = clean.soundEnabled
            prefs[REST_VIBRATE] = clean.vibrationEnabled
            prefs[REST_DEFAULT] = clean.defaultRestSeconds
            if (clean.lastPresetSeconds == null) {
                prefs.remove(REST_LAST_PRESET)
            } else {
                prefs[REST_LAST_PRESET] = clean.lastPresetSeconds
            }
        }
    }

    suspend fun setSchedulePreferences(value: SchedulePreferences) {
        val clean = value.sanitized()
        dataStore.edit { prefs ->
            prefs[TRAINING_DAYS] = clean.trainingDaysPerWeek
            prefs[SPLIT_STYLE] = clean.splitStyle.storageKey
            prefs[WEEK_START] = clean.weekStart.name
        }
    }

    val driveAccountEmail: Flow<String?> = safePreferences
        .map { prefs -> prefs[DRIVE_ACCOUNT] }

    val lastBackupAt: Flow<Long?> = safePreferences
        .map { prefs -> prefs[LAST_BACKUP_AT] }

    val lastBackupName: Flow<String?> = safePreferences
        .map { prefs -> prefs[LAST_BACKUP_NAME] }

    suspend fun setDriveAccountEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(DRIVE_ACCOUNT)
            } else {
                prefs[DRIVE_ACCOUNT] = email
            }
        }
    }

    suspend fun driveFolderId(): String? = safePreferences.first()[DRIVE_FOLDER_ID]

    suspend fun setDriveFolderId(folderId: String?) {
        dataStore.edit { prefs ->
            if (folderId.isNullOrBlank()) {
                prefs.remove(DRIVE_FOLDER_ID)
            } else {
                prefs[DRIVE_FOLDER_ID] = folderId
            }
        }
    }

    /**
     * One atomic write for everything a restore carries, so a crash mid-way cannot leave the
     * restored data paired with half the old preferences.
     *
     * Every parameter is required and every one is written unconditionally. That is the point:
     * a restore replaces state, and an optional parameter here would quietly mean "keep what
     * this handset happened to have", which is how a restored phone ends up with the previous
     * owner's rest timer. Callers decoding an older document pass the documented defaults
     * rather than omitting the argument.
     *
     * @param bodyweightKg null is a real value — "never told us". Out-of-range numbers are
     * dropped to null rather than clamped, because a stored 900 is corruption, not a claim.
     * @param onboardingComplete false here really does send the next launch to the guided
     * setup, so the caller — not this function — is responsible for not passing false to
     * someone whose history says otherwise. See the call site in LocalBackupRepository.
     * @param dismissedCollisionIds replaces rather than merges. A restore replaces the whole
     * library, so decisions about the old library have nothing left to refer to.
     */
    suspend fun setRestoredPreferences(
        unit: WeightUnit,
        schedule: SchedulePreferences,
        rest: RestTimerPreferences,
        coach: CoachPreferences,
        heatWindow: HeatWindow,
        bodyweightKg: Double?,
        bodyweightLog: List<BodyweightEntry>,
        onboardingComplete: Boolean,
        dismissedCollisionIds: Set<String>,
        block: TrainingBlock?,
        pastBlocks: List<TrainingBlock>,
        trainingAge: TrainingAge,
        preferredDays: Set<DayOfWeek>,
        trainingPlace: TrainingPlace,
        lighterWeekStartEpochDay: Long?,
        trainingPlaces: Set<TrainingPlace> = emptySet(),
    ) {
        val cleanSchedule = schedule.sanitized()
        val cleanRest = rest.sanitized()
        val cleanBodyweight = bodyweightKg?.takeIf {
            it.isFinite() && it in OnboardingAnswers.MIN_BODYWEIGHT_KG..OnboardingAnswers.MAX_BODYWEIGHT_KG
        }
        dataStore.edit { prefs ->
            prefs[WEIGHT_UNIT] = unit.storageKey
            prefs[TRAINING_DAYS] = cleanSchedule.trainingDaysPerWeek
            prefs[SPLIT_STYLE] = cleanSchedule.splitStyle.storageKey
            prefs[WEEK_START] = cleanSchedule.weekStart.name
            prefs[REST_SOUND] = cleanRest.soundEnabled
            prefs[REST_VIBRATE] = cleanRest.vibrationEnabled
            prefs[REST_DEFAULT] = cleanRest.defaultRestSeconds
            // Cleared, not carried. RestTimer.secondsToStart prefers the last preset over the
            // default, so leaving this handset's value in place lets a stale tap outrank the
            // rest time that was just restored. It is deliberately not in the backup document
            // either: one phone's transient preset has no business landing on another.
            prefs.remove(REST_LAST_PRESET)
            prefs[TRAINING_GOAL] = coach.goal.name
            prefs[TRAINING_EMPHASIS] = coach.emphasis.name
            prefs[AVAILABLE_EQUIPMENT] = coach.availableEquipment
            prefs[HEAT_WINDOW] = heatWindow.name
            if (cleanBodyweight == null) {
                prefs.remove(BODYWEIGHT_KG)
            } else {
                prefs[BODYWEIGHT_KG] = cleanBodyweight
            }
            if (bodyweightLog.isEmpty()) {
                prefs.remove(BODYWEIGHT_LOG)
            } else {
                prefs[BODYWEIGHT_LOG] = BodyweightLog.encode(bodyweightLog)
            }
            prefs[ONBOARDING_COMPLETE] = onboardingComplete
            prefs[DISMISSED_COLLISIONS] = dismissedCollisionIds
            if (block == null) {
                prefs.remove(BLOCK_START)
                prefs.remove(BLOCK_WEEKS)
            } else {
                prefs[BLOCK_START] = block.startEpochDay
                prefs[BLOCK_WEEKS] = block.weeks
            }
            if (pastBlocks.isEmpty()) {
                prefs.remove(PAST_BLOCKS)
            } else {
                prefs[PAST_BLOCKS] = BlockArchive.encode(pastBlocks)
            }
            prefs[TRAINING_AGE] = trainingAge.name
            prefs[PREFERRED_DAYS] = preferredDays.map { it.name }.toSet()
            prefs[TRAINING_PLACE] = TrainingPlace.formatPlaces(
                trainingPlaces.ifEmpty { setOf(trainingPlace) },
            )
            if (lighterWeekStartEpochDay == null) {
                prefs.remove(LIGHTER_WEEK_START)
            } else {
                prefs[LIGHTER_WEEK_START] = lighterWeekStartEpochDay
            }
        }
    }

    /**
     * Name collisions the owner has said they are fine with.
     *
     * Device-local by design. A collision is between a lift the owner made and one the app
     * ships, and "keep both" is a statement about their own library on their own phone — not a
     * property of either lift. Storing it as a preference also means it survives a restore,
     * which has one consequence worth stating: a collision re-detected after a restore stays
     * hidden if it was dismissed before. That is the right default (the owner already answered
     * the question) and it is recorded here so it is not a surprise.
     */
    val dismissedCollisionIds: Flow<Set<String>> = safePreferences
        .map { prefs -> prefs[DISMISSED_COLLISIONS].orEmpty() }

    suspend fun dismissCollision(exerciseId: String) {
        dataStore.edit { prefs ->
            prefs[DISMISSED_COLLISIONS] = prefs[DISMISSED_COLLISIONS].orEmpty() + exerciseId
        }
    }

    /**
     * Whether the guided setup has been seen.
     *
     * A flag rather than "do they have routines", because those are different questions. A
     * lifter who deliberately deleted every routine has still been through setup and must not
     * be dropped back into a questionnaire; someone who skipped setup and built one routine by
     * hand has still made their choice. Inferring it from data would get both wrong.
     */
    val onboardingComplete: Flow<Boolean> = safePreferences
        .map { prefs -> prefs[ONBOARDING_COMPLETE] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETE] = complete }
    }

    /**
     * What the lifter weighs, when they have told us.
     *
     * Null is a real answer — the setup lets it be skipped — and it means "use the flat
     * stand-in", which is what [com.sinura.personaltrainer.domain.MuscleLoadCalculator]
     * already did for every bodyweight set before this existed.
     */
    val bodyweightKg: Flow<Double?> = safePreferences
        .map { prefs -> prefs[BODYWEIGHT_KG]?.takeIf { it > 0.0 } }

    /**
     * Every weigh-in, oldest first.
     *
     * Kept beside [bodyweightKg] rather than instead of it: that is the current value, this is
     * how it got there, and [recordBodyweight] writes both in one edit so they cannot disagree.
     */
    val bodyweightLog: Flow<List<BodyweightEntry>> = safePreferences
        .map { prefs -> BodyweightLog.decode(prefs[BODYWEIGHT_LOG]) }

    /**
     * Record what the lifter weighs today, keeping the history.
     *
     * The only writer anything should use. [setBodyweightKg] survives for clearing the value,
     * which is a different act — saying "I would rather not say" is not a weigh-in.
     */
    suspend fun recordBodyweight(kg: Double, epochDay: Long) {
        val clean = kg.takeIf {
            it.isFinite() &&
                it >= OnboardingAnswers.MIN_BODYWEIGHT_KG &&
                it <= OnboardingAnswers.MAX_BODYWEIGHT_KG
        } ?: return
        dataStore.edit { prefs ->
            prefs[BODYWEIGHT_KG] = clean
            prefs[BODYWEIGHT_LOG] = BodyweightLog.encode(
                BodyweightLog.record(
                    BodyweightLog.decode(prefs[BODYWEIGHT_LOG]),
                    BodyweightEntry(epochDay = epochDay, kg = clean),
                ),
            )
        }
    }

    suspend fun setBodyweightKg(kg: Double?) {
        dataStore.edit { prefs ->
            if (kg == null || !kg.isFinite() || kg <= 0.0) {
                prefs.remove(BODYWEIGHT_KG)
            } else {
                prefs[BODYWEIGHT_KG] = kg
            }
        }
    }

    /**
     * The block the lifter is in, or null when they are not in one.
     *
     * Null is a real state and stays one. Someone who built their routines by hand never
     * started a block, and putting them in an implied one — dated from whenever the app first
     * saw them — would invent a milestone they never set.
     */
    val trainingBlock: Flow<TrainingBlock?> = safePreferences
        .map { prefs ->
            val start = prefs[BLOCK_START] ?: return@map null
            TrainingBlock(
                startEpochDay = start,
                weeks = prefs[BLOCK_WEEKS] ?: TrainingBlock.DEFAULT_WEEKS,
            )
        }

    /**
     * The blocks already finished, oldest first. Boundaries only — see [BlockArchive].
     */
    val pastBlocks: Flow<List<TrainingBlock>> = safePreferences
        .map { prefs -> BlockArchive.decode(prefs[PAST_BLOCKS]) }

    /**
     * Make [next] the current block, keeping the one it replaces if it was finished.
     *
     * One method rather than an archive call beside every set, because the rule about *which*
     * blocks are worth keeping belongs in one place: a block you finished is a result, and a
     * block you abandoned half way through by re-running setup is not. Both callers — the
     * "start the next twelve" button and the guided setup — go through here.
     */
    suspend fun beginBlock(next: TrainingBlock, todayEpochDay: Long) {
        dataStore.edit { prefs ->
            val current = prefs[BLOCK_START]?.let { start ->
                TrainingBlock(start, prefs[BLOCK_WEEKS] ?: TrainingBlock.DEFAULT_WEEKS)
            }
            if (current != null && current.isCompleteOn(todayEpochDay)) {
                prefs[PAST_BLOCKS] = BlockArchive.encode(
                    BlockArchive.archive(BlockArchive.decode(prefs[PAST_BLOCKS]), current),
                )
            }
            prefs[BLOCK_START] = next.startEpochDay
            prefs[BLOCK_WEEKS] = next.weeks
        }
    }

    /**
     * Set or clear the current block, leaving the archive alone.
     *
     * The plain setter. Anything that *replaces* one block with another goes through
     * [beginBlock], which is where the rule about keeping a finished block lives.
     */
    suspend fun setTrainingBlock(block: TrainingBlock?) {
        dataStore.edit { prefs ->
            if (block == null) {
                prefs.remove(BLOCK_START)
                prefs.remove(BLOCK_WEEKS)
            } else {
                prefs[BLOCK_START] = block.startEpochDay
                prefs[BLOCK_WEEKS] = block.weeks
            }
        }
    }

    val lastRestoreAt: Flow<Long?> = safePreferences.map { prefs -> prefs[LAST_RESTORE_AT] }

    val lastRestoreName: Flow<String?> = safePreferences.map { prefs -> prefs[LAST_RESTORE_NAME] }

    /**
     * Tracked separately from [setLastBackup]. Restoring used to overwrite the last-backup
     * stamp, so Settings claimed a backup existed as of the restored file's date — the one
     * signal whose whole job is to nag the user into backing up.
     */
    suspend fun setLastRestore(fileName: String, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_RESTORE_NAME] = fileName
            prefs[LAST_RESTORE_AT] = atMillis
        }
    }

    suspend fun setLastBackup(fileName: String, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_BACKUP_NAME] = fileName
            prefs[LAST_BACKUP_AT] = atMillis
        }
    }

    suspend fun clearDriveSession() {
        dataStore.edit { prefs ->
            prefs.remove(DRIVE_ACCOUNT)
            prefs.remove(DRIVE_FOLDER_ID)
        }
    }

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val TRAINING_GOAL = stringPreferencesKey("training_goal")
        val TRAINING_EMPHASIS = stringPreferencesKey("training_emphasis")
        val AVAILABLE_EQUIPMENT = stringSetPreferencesKey("available_equipment")
        val DISMISSED_COLLISIONS = stringSetPreferencesKey("library_collision_dismissed_ids")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val BODYWEIGHT_KG = doublePreferencesKey("bodyweight_kg")
        val BODYWEIGHT_LOG = stringPreferencesKey("bodyweight_log")
        val BLOCK_START = longPreferencesKey("block_start_epoch_day")
        val BLOCK_WEEKS = intPreferencesKey("block_weeks")
        val PAST_BLOCKS = stringPreferencesKey("past_blocks")
        val HEAT_WINDOW = stringPreferencesKey("heat_window")
        val TRAINING_DAYS = intPreferencesKey("training_days_per_week")
        val SPLIT_STYLE = stringPreferencesKey("split_style")
        val WEEK_START = stringPreferencesKey("week_start")
        val REST_SOUND = booleanPreferencesKey("rest_sound")
        val REST_VIBRATE = booleanPreferencesKey("rest_vibrate")
        val REST_DEFAULT = intPreferencesKey("rest_default_seconds")
        val REST_LAST_PRESET = intPreferencesKey("rest_last_preset_seconds")
        val DRIVE_ACCOUNT = stringPreferencesKey("drive_account_email")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_NAME = stringPreferencesKey("last_backup_name")
        val LAST_RESTORE_AT = longPreferencesKey("last_restore_at")
        val LAST_RESTORE_NAME = stringPreferencesKey("last_restore_name")
        val TRAINING_AGE = stringPreferencesKey("training_age")
        val PREFERRED_DAYS = stringSetPreferencesKey("preferred_days")
        val TRAINING_PLACE = stringPreferencesKey("training_place")
        val LIGHTER_WEEK_START = longPreferencesKey("lighter_week_start_epoch_day")

        fun preferredDaysFrom(raw: Set<String>?): Set<DayOfWeek> =
            raw.orEmpty().mapNotNull { name ->
                DayOfWeek.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }.toSet()
    }
}
