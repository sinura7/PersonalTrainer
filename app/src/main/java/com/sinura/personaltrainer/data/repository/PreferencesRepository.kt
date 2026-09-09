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
import com.sinura.personaltrainer.data.local.FoundationGeneration
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.dao.TrainingBlockDao
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.local.entity.TrainingBlockEntity
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.BlockArchive
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.BodyweightLog
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.ReminderPreferences
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings",
)

/**
 * The automatic-backup decision inputs, read in one snapshot.
 *
 * Lives here rather than in `domain/` because it is a preferences shape, and because the
 * pure rule that consumes it ([com.sinura.personaltrainer.domain.AutoBackupPolicy]) takes
 * plain parameters — which keeps that rule testable in the no-Android lane.
 */
data class AutoBackupSettings(
    val enabled: Boolean,
    /** Base64 IV + ciphertext. Opened by the sealer; never a passphrase in the clear. */
    val sealedPassphrase: String?,
    val lastBackedUpSessionId: String?,
)

class PreferencesRepository(
    context: Context,
    /**
     * Tests pass a private store. The [preferencesDataStore] delegate is a process
     * singleton — a unique `filesDir` alone still shares `user_settings` and hangs
     * `edit()` / `first()` once another Robolectric test has opened it.
     */
    dataStore: DataStore<Preferences> = context.applicationContext.userSettingsDataStore,
    private val bodyweightDao: BodyweightDao? = null,
    private val trainingBlockDao: TrainingBlockDao? = null,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
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

    /**
     * A mapped preference that stays quiet when the stored value did not
     * change. DataStore re-emits the whole [Preferences] object on any write;
     * without this, every reader of an unrelated key reruns.
     */
    private fun <T> pref(read: (Preferences) -> T): Flow<T> =
        safePreferences.map(read).distinctUntilChanged()

    val weightUnit: Flow<WeightUnit> = pref { prefs -> WeightUnit.fromStorage(prefs[WEIGHT_UNIT]) }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { prefs ->
            prefs[WEIGHT_UNIT] = unit.storageKey
        }
    }

    val clockFormat: Flow<ClockFormat> = pref { prefs -> ClockFormat.fromStorage(prefs[CLOCK_FORMAT]) }

    suspend fun setClockFormat(format: ClockFormat) {
        dataStore.edit { prefs -> prefs[CLOCK_FORMAT] = format.storageKey }
    }

    /**
     * Null is Auto: first training day of the week.
     */
    val bodyweightCheckInWeekday: Flow<Weekday?> =
        pref { prefs -> Weekday.fromStorage(prefs[BODYWEIGHT_CHECK_IN_WEEKDAY]) }

    suspend fun setBodyweightCheckInWeekday(day: Weekday?) {
        dataStore.edit { prefs ->
            if (day == null) {
                prefs.remove(BODYWEIGHT_CHECK_IN_WEEKDAY)
            } else {
                prefs[BODYWEIGHT_CHECK_IN_WEEKDAY] = day.name
            }
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
    val coachPreferences: Flow<CoachPreferences> = pref { prefs ->
        CoachPreferences(
            goal = TrainingGoal.fromStorage(prefs[TRAINING_GOAL]),
            // Empty means gym-floor (no Hyper Pro), never "owns nothing" — see CoachPreferences.
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

    val trainingAge: Flow<TrainingAge> = pref { prefs -> TrainingAge.fromStorage(prefs[TRAINING_AGE]) }

    suspend fun setTrainingAge(age: TrainingAge) {
        dataStore.edit { prefs -> prefs[TRAINING_AGE] = age.name }
    }

    val preferredDays: Flow<Set<Weekday>> = pref { prefs -> preferredDaysFrom(prefs[PREFERRED_DAYS]) }

    suspend fun setPreferredDays(days: Set<Weekday>) {
        dataStore.edit { prefs -> prefs[PREFERRED_DAYS] = days.map { it.name }.toSet() }
    }

    /**
     * Null means the key was never written — an install from before Job 3, or a restore of
     * a file that predates the field. Readers call [OnboardingAnswers.inferPlace] in that
     * case rather than pretending everyone trains in a full gym.
     */
    val trainingPlace: Flow<TrainingPlace?> =
        pref { prefs -> prefs[TRAINING_PLACE]?.let { TrainingPlace.fromStorage(it) } }

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
    val lighterWeekStartEpochDay: Flow<Long?> = pref { prefs -> prefs[LIGHTER_WEEK_START] }

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
            focus = TrainingFocus.fromStorage(prefs[TRAINING_FOCUS]),
            availableEquipment = prefs[AVAILABLE_EQUIPMENT].orEmpty(),
        )
    }

    val trainingFocus: Flow<TrainingFocus> = pref { prefs -> TrainingFocus.fromStorage(prefs[TRAINING_FOCUS]) }

    suspend fun setTrainingFocus(focus: TrainingFocus) {
        dataStore.edit { prefs -> prefs[TRAINING_FOCUS] = focus.name }
    }

    /**
     * The window the body map opens on.
     *
     * Nothing persisted this before, so the map reset to a default every time the process
     * died — a preference the user re-expressed on every cold start and the app never learned.
     */
    val heatWindow: Flow<HeatWindow> = pref { prefs -> HeatWindow.fromStorage(prefs[HEAT_WINDOW]) }

    suspend fun setHeatWindow(window: HeatWindow) {
        dataStore.edit { prefs -> prefs[HEAT_WINDOW] = window.name }
    }

    val schedulePreferences: Flow<SchedulePreferences> = pref { prefs ->
        SchedulePreferences(
            trainingDaysPerWeek = prefs[TRAINING_DAYS] ?: SchedulePreferences.DEFAULT_DAYS,
            splitStyle = SplitStyle.fromStorage(prefs[SPLIT_STYLE]),
            weekStart = SchedulePreferences.weekStartFromStorage(prefs[WEEK_START]),
        ).sanitized()
    }

    suspend fun setTrainingDaysPerWeek(days: Int) {
        dataStore.edit { prefs ->
            val clean = days.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)
            prefs[TRAINING_DAYS] = clean
            val preferred = preferredDaysFrom(prefs[PREFERRED_DAYS])
            if (preferred.size > clean) {
                val weekStart = SchedulePreferences.weekStartFromStorage(prefs[WEEK_START])
                val ordered = (0 until 7).map { weekStart.plus(it.toLong()) }
                prefs[PREFERRED_DAYS] = ordered.filter { it in preferred }.take(clean).map { it.name }.toSet()
            }
        }
    }

    suspend fun setSplitStyle(style: SplitStyle) {
        dataStore.edit { prefs ->
            prefs[SPLIT_STYLE] = style.storageKey
        }
    }

    suspend fun setWeekStart(day: Weekday) {
        dataStore.edit { prefs ->
            prefs[WEEK_START] = day.name
        }
    }

    val restTimerPreferences: Flow<RestTimerPreferences> = pref { prefs ->
        RestTimerPreferences(
            soundEnabled = prefs[REST_SOUND] ?: true,
            vibrationEnabled = prefs[REST_VIBRATE] ?: true,
            defaultRestSeconds = prefs[REST_DEFAULT] ?: RestTimerPreferences.DEFAULT_SECONDS,
            lastPresetSeconds = prefs[REST_LAST_PRESET],
        ).sanitized()
    }

    /**
     * Exact-alarm special-access is requested only after rest is used or
     * configured. Onboarding must never write this. Restore leaves it alone.
     */
    val restAlarmEligible: Flow<Boolean> = pref { prefs -> prefs[REST_ALARM_ELIGIBLE] ?: false }

    suspend fun markRestAlarmEligible() {
        dataStore.edit { prefs -> prefs[REST_ALARM_ELIGIBLE] = true }
    }

    val reminderPreferences: Flow<ReminderPreferences> = pref { prefs ->
        ReminderPreferences(
            optOut = prefs[REMINDER_OPT_OUT] ?: false,
            quietStartHour = prefs[REMINDER_QUIET_START]
                ?: ReminderPreferences.DEFAULT_QUIET_START_HOUR,
            quietEndHour = prefs[REMINDER_QUIET_END]
                ?: ReminderPreferences.DEFAULT_QUIET_END_HOUR,
        ).sanitized()
    }

    suspend fun setReminderOptOut(optOut: Boolean) {
        dataStore.edit { prefs -> prefs[REMINDER_OPT_OUT] = optOut }
    }

    suspend fun setReminderQuietHours(startHour: Int, endHour: Int) {
        dataStore.edit { prefs ->
            prefs[REMINDER_QUIET_START] = startHour.coerceIn(0, 23)
            prefs[REMINDER_QUIET_END] = endHour.coerceIn(0, 23)
        }
    }

    /**
     * Strength (and mixed) occurrence started through the live logger.
     * Device-local: not part of backup. Cleared on finish or discard.
     */
    val pendingOccurrenceId: Flow<String?> =
        pref { prefs -> prefs[PENDING_OCCURRENCE_ID]?.takeIf { it.isNotBlank() } }

    suspend fun setPendingOccurrenceId(id: String?) {
        dataStore.edit { prefs ->
            if (id.isNullOrBlank()) {
                prefs.remove(PENDING_OCCURRENCE_ID)
            } else {
                prefs[PENDING_OCCURRENCE_ID] = id
            }
        }
    }

    suspend fun setRestSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[REST_SOUND] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    suspend fun setRestVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[REST_VIBRATE] = enabled
            prefs[REST_ALARM_ELIGIBLE] = true
        }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[REST_DEFAULT] = seconds.coerceIn(RestTimerPreferences.MIN_SECONDS, RestTimerPreferences.MAX_SECONDS)
            prefs[REST_ALARM_ELIGIBLE] = true
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

    val driveAccountEmail: Flow<String?> = pref { prefs -> prefs[DRIVE_ACCOUNT] }

    val lastBackupAt: Flow<Long?> = pref { prefs -> prefs[LAST_BACKUP_AT] }

    val lastBackupName: Flow<String?> = pref { prefs -> prefs[LAST_BACKUP_NAME] }

    /**
     * The last backup that was read back out of Drive, decrypted and found to carry the history
     * that was written. Device-local and deliberately absent from the backup document: a
     * verification is a statement about THIS phone's copy, and restoring one onto a new phone
     * would import a reassurance that had never been earned there.
     */
    val lastVerifiedBackupAt: Flow<Long?> = pref { prefs -> prefs[LAST_VERIFIED_BACKUP_AT] }

    val lastVerifiedBackupName: Flow<String?> =
        pref { prefs -> prefs[LAST_VERIFIED_BACKUP_NAME] }

    suspend fun setDriveAccountEmail(email: String?) {
        dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(DRIVE_ACCOUNT)
            } else {
                prefs[DRIVE_ACCOUNT] = email
            }
        }
    }

    /**
     * Automatic backup after a finished workout. Device-local, all four keys: none of them
     * appears in [com.sinura.personaltrainer.data.backup.BackupPreferences], which is a
     * hand-listed set rather than a sweep, so they are excluded by construction. That is
     * deliberate — AUTO_BACKUP_SECRET is ciphertext under a non-exportable Keystore key, so
     * restoring it onto another phone, or onto this one after a reinstall, would write a
     * blob nothing can open over a passphrase the owner had just entered.
     */
    val autoBackupEnabled: Flow<Boolean> = pref { prefs -> prefs[AUTO_BACKUP_ENABLED] ?: false }

    /** Set when an unattended copy found the Drive grant lapsed. Settings surfaces it. */
    val autoBackupNeedsSignIn: Flow<Boolean> =
        pref { prefs -> prefs[AUTO_BACKUP_NEEDS_SIGN_IN] ?: false }

    /**
     * One snapshot, read once. Separate `.first()` calls each re-collect and can observe
     * different write generations, so a toggle flipped as a workout ends could be seen as
     * enabled with no secret — the same reason [storedOnboardingAnswers] takes one snapshot.
     */
    suspend fun autoBackupSettings(): AutoBackupSettings {
        val prefs = safePreferences.first()
        return AutoBackupSettings(
            enabled = prefs[AUTO_BACKUP_ENABLED] ?: false,
            sealedPassphrase = prefs[AUTO_BACKUP_SECRET]?.takeIf { it.isNotBlank() },
            lastBackedUpSessionId = prefs[AUTO_BACKUP_LAST_SESSION]?.takeIf { it.isNotBlank() },
        )
    }

    /** Arming is one write so a crash cannot leave the toggle on with no secret behind it. */
    suspend fun armAutoBackup(sealedPassphrase: String) {
        dataStore.edit { prefs ->
            prefs[AUTO_BACKUP_ENABLED] = true
            prefs[AUTO_BACKUP_SECRET] = sealedPassphrase
            prefs.remove(AUTO_BACKUP_NEEDS_SIGN_IN)
        }
    }

    /** Turning it off forgets the passphrase too: an unopenable secret helps nobody. */
    suspend fun disarmAutoBackup() {
        dataStore.edit { prefs ->
            prefs.remove(AUTO_BACKUP_ENABLED)
            prefs.remove(AUTO_BACKUP_SECRET)
            prefs.remove(AUTO_BACKUP_NEEDS_SIGN_IN)
            prefs.remove(AUTO_BACKUP_LAST_SESSION)
        }
    }

    suspend fun setAutoBackupNeedsSignIn(needsSignIn: Boolean) {
        dataStore.edit { prefs ->
            if (needsSignIn) {
                prefs[AUTO_BACKUP_NEEDS_SIGN_IN] = true
            } else {
                prefs.remove(AUTO_BACKUP_NEEDS_SIGN_IN)
            }
        }
    }

    /** Written only after an upload returns, so a failed copy is retried rather than skipped. */
    suspend fun setAutoBackupLastSession(sessionId: String) {
        dataStore.edit { prefs -> prefs[AUTO_BACKUP_LAST_SESSION] = sessionId }
    }

    /** One-shot, for the account-change guard, which must read before it writes. */
    suspend fun driveAccountEmailOnce(): String? =
        safePreferences.first()[DRIVE_ACCOUNT]?.takeIf { it.isNotBlank() }

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
     * @param onboardingComplete false here is a first-visit Home (get-started sheet),
     * not a launch that replaces the shell with the questionnaire. The caller — not
     * this function — is responsible for not passing false to someone whose history
     * says otherwise. See the call site in LocalBackupRepository.
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
        preferredDays: Set<Weekday>,
        trainingPlace: TrainingPlace,
        lighterWeekStartEpochDay: Long?,
        trainingPlaces: Set<TrainingPlace> = emptySet(),
        reminderOptOut: Boolean = false,
        reminderQuietStartHour: Int = ReminderPreferences.DEFAULT_QUIET_START_HOUR,
        reminderQuietEndHour: Int = ReminderPreferences.DEFAULT_QUIET_END_HOUR,
        clockFormat: ClockFormat = ClockFormat.TWELVE,
        bodyweightCheckInWeekday: Weekday? = null,
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
            prefs[REMINDER_OPT_OUT] = reminderOptOut
            prefs[REMINDER_QUIET_START] = reminderQuietStartHour.coerceIn(0, 23)
            prefs[REMINDER_QUIET_END] = reminderQuietEndHour.coerceIn(0, 23)
            prefs[CLOCK_FORMAT] = clockFormat.storageKey
            if (bodyweightCheckInWeekday == null) {
                prefs.remove(BODYWEIGHT_CHECK_IN_WEEKDAY)
            } else {
                prefs[BODYWEIGHT_CHECK_IN_WEEKDAY] = bodyweightCheckInWeekday.name
            }
        }
        replaceRoomHistory(bodyweightLog, block, pastBlocks)
    }

    /**
     * One-shot DataStore → Room copy after the Temper v1→v2 migration.
     *
     * Room migrations cannot read DataStore. Empty tables plus leftover
     * encoded strings mean this install still holds history in preferences.
     * After import, Room is the source of truth and the encoded strings
     * stay only as a backup-compatible echo until the next write.
     */
    suspend fun importEncodedHistoryIfNeeded() {
        val bodyDao = bodyweightDao
        val blockDao = trainingBlockDao
        if (bodyDao != null && bodyDao.count() == 0) {
            val prefs = safePreferences.first()
            val entries = BodyweightLog.decode(prefs[BODYWEIGHT_LOG])
            if (entries.isNotEmpty()) {
                bodyDao.upsertAll(entries.map { it.toEntity(nowMs()) })
            }
        }
        if (blockDao != null && blockDao.count() == 0) {
            val prefs = safePreferences.first()
            val past = BlockArchive.decode(prefs[PAST_BLOCKS])
            val current = prefs[BLOCK_START]?.let { start ->
                TrainingBlock(start, prefs[BLOCK_WEEKS] ?: TrainingBlock.DEFAULT_WEEKS)
            }
            val rows = buildList {
                past.forEach { add(it.toEntity(isCurrent = false, archivedAtMs = nowMs())) }
                current?.let { add(it.toEntity(isCurrent = true, archivedAtMs = null)) }
            }
            if (rows.isNotEmpty()) blockDao.upsertAll(rows)
        }
    }

    private suspend fun replaceRoomHistory(
        log: List<BodyweightEntry>,
        current: TrainingBlock?,
        past: List<TrainingBlock>,
    ) {
        bodyweightDao?.let { dao ->
            dao.deleteAll()
            if (log.isNotEmpty()) {
                dao.upsertAll(log.map { it.toEntity(nowMs()) })
            }
        }
        trainingBlockDao?.let { dao ->
            dao.deleteAll()
            val rows = buildList {
                past.forEach { add(it.toEntity(isCurrent = false, archivedAtMs = nowMs())) }
                current?.let { add(it.toEntity(isCurrent = true, archivedAtMs = null)) }
            }
            if (rows.isNotEmpty()) dao.upsertAll(rows)
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
    val dismissedCollisionIds: Flow<Set<String>> =
        pref { prefs -> prefs[DISMISSED_COLLISIONS].orEmpty() }

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
    /**
     * Setup-finished flag as [DataHealth]. An unreadable store is Unavailable,
     * never `false` — that would masquerade as a first install.
     */
    val onboardingCompleteHealth: Flow<DataHealth<Boolean>> = dataStore.data
        .observeHealth("settings")
        .map { health ->
            when (health) {
                is DataHealth.Available ->
                    DataHealth.Available(health.value[ONBOARDING_COMPLETE] ?: false)
                is DataHealth.Degraded ->
                    DataHealth.Degraded(
                        lastValue = health.lastValue[ONBOARDING_COMPLETE] ?: false,
                        what = health.what,
                    )
                is DataHealth.Unavailable -> health
            }
        }
        .distinctUntilChanged()

    val onboardingComplete: Flow<Boolean> = onboardingCompleteHealth.presentValues()

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
    val bodyweightKg: Flow<Double?> = pref { prefs -> prefs[BODYWEIGHT_KG]?.takeIf { it > 0.0 } }

    /**
     * Every weigh-in, oldest first.
     *
     * Kept beside [bodyweightKg] rather than instead of it: that is the current value, this is
     * how it got there, and [recordBodyweight] writes both in one edit so they cannot disagree.
     */
    val bodyweightLog: Flow<List<BodyweightEntry>> =
        bodyweightDao?.observeAll()
            ?.map { rows -> rows.map { it.toDomain() } }
            ?.distinctUntilChanged()
            ?: pref { prefs -> BodyweightLog.decode(prefs[BODYWEIGHT_LOG]) }

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
        bodyweightDao?.let { dao ->
            val now = com.sinura.personaltrainer.util.JvmTime.captureNow()
            dao.upsert(
                BodyweightEntryEntity(
                    epochDay = epochDay,
                    kg = clean,
                    recordedAtMs = now.instantMillis,
                    zoneId = now.zoneId,
                    offsetSeconds = now.offsetSeconds,
                ),
            )
            val all = dao.getAll()
            if (all.size > BodyweightLog.MAX_ENTRIES) {
                all.dropLast(BodyweightLog.MAX_ENTRIES).forEach { extra ->
                    dao.deleteDay(extra.epochDay)
                }
            }
        }
        dataStore.edit { prefs ->
            prefs[BODYWEIGHT_KG] = clean
            if (bodyweightDao == null) {
                prefs[BODYWEIGHT_LOG] = BodyweightLog.encode(
                    BodyweightLog.record(
                        BodyweightLog.decode(prefs[BODYWEIGHT_LOG]),
                        BodyweightEntry(epochDay = epochDay, kg = clean),
                    ),
                )
            }
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
    val trainingBlock: Flow<TrainingBlock?> =
        trainingBlockDao?.observeCurrent()?.map { it?.toDomain() }?.distinctUntilChanged()
            ?: pref { prefs ->
                val start = prefs[BLOCK_START] ?: return@pref null
                TrainingBlock(
                    startEpochDay = start,
                    weeks = prefs[BLOCK_WEEKS] ?: TrainingBlock.DEFAULT_WEEKS,
                )
            }

    /**
     * The blocks already finished, oldest first. Boundaries only — see [BlockArchive].
     */
    val pastBlocks: Flow<List<TrainingBlock>> =
        trainingBlockDao?.observePast()
            ?.map { rows -> rows.map { it.toDomain() } }
            ?.distinctUntilChanged()
            ?: pref { prefs -> BlockArchive.decode(prefs[PAST_BLOCKS]) }

    /**
     * Make [next] the current block, keeping the one it replaces if it was finished.
     *
     * One method rather than an archive call beside every set, because the rule about *which*
     * blocks are worth keeping belongs in one place: a block you finished is a result, and a
     * block you abandoned half way through by re-running setup is not. Both callers — the
     * "start the next twelve" button and the guided setup — go through here.
     */
    suspend fun beginBlock(next: TrainingBlock, todayEpochDay: Long) {
        trainingBlockDao?.let { dao ->
            val existing = dao.getCurrent()
            if (existing != null) {
                val current = existing.toDomain()
                if (current.isCompleteOn(todayEpochDay)) {
                    dao.upsert(existing.copy(isCurrent = false, archivedAtMs = nowMs()))
                } else {
                    dao.deleteById(existing.id)
                }
            }
            dao.upsert(next.toEntity(isCurrent = true, archivedAtMs = null))
        }
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
        trainingBlockDao?.let { dao ->
            dao.getCurrent()?.let { dao.deleteById(it.id) }
            if (block != null) {
                dao.upsert(block.toEntity(isCurrent = true, archivedAtMs = null))
            }
        }
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

    val lastRestoreAt: Flow<Long?> = pref { prefs -> prefs[LAST_RESTORE_AT] }

    val lastRestoreName: Flow<String?> = pref { prefs -> prefs[LAST_RESTORE_NAME] }

    /**
     * A durable note from restore recovery that Settings shows until it is dismissed: an
     * interrupted restore that finished without its settings, or one whose outcome could
     * not be verified. Written by BackupRepository only; a screen may clear it.
     */
    val restoreRecoveryNote: Flow<String?> = pref { prefs -> prefs[RESTORE_RECOVERY_NOTE] }

    suspend fun setRestoreRecoveryNote(note: String?) {
        dataStore.edit { prefs ->
            if (note == null) prefs.remove(RESTORE_RECOVERY_NOTE) else prefs[RESTORE_RECOVERY_NOTE] = note
        }
    }

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

    /** Written only after the file has come back out of Drive intact. */
    suspend fun setLastVerifiedBackup(fileName: String, atMillis: Long) {
        dataStore.edit { prefs ->
            prefs[LAST_VERIFIED_BACKUP_NAME] = fileName
            prefs[LAST_VERIFIED_BACKUP_AT] = atMillis
        }
    }

    /**
     * Signing out of Drive also disarms automatic backup — without a grant it cannot run, and
     * leaving the toggle on would promise a copy that never happens. The sealed passphrase
     * goes with it: keeping a secret for a feature that is off is a liability, not a
     * convenience, and re-arming re-asks for it.
     */
    suspend fun clearDriveSession() {
        dataStore.edit { prefs ->
            prefs.remove(DRIVE_ACCOUNT)
            prefs.remove(DRIVE_FOLDER_ID)
            prefs.remove(AUTO_BACKUP_ENABLED)
            prefs.remove(AUTO_BACKUP_SECRET)
            prefs.remove(AUTO_BACKUP_NEEDS_SIGN_IN)
            prefs.remove(AUTO_BACKUP_LAST_SESSION)
        }
    }

    val foundationGeneration: Flow<String?> = pref { prefs -> prefs[FOUNDATION_GENERATION] }

    /**
     * ADR-010 cutover: keep only weight unit and rest sound / vibration /
     * default duration. Everything else, including encoded bodyweight and
     * block strings, is dropped.
     */
    suspend fun resetForFoundationCutover() {
        val unit = weightUnit.first()
        val rest = restTimerPreferences.first()
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[WEIGHT_UNIT] = unit.storageKey
            prefs[REST_SOUND] = rest.soundEnabled
            prefs[REST_VIBRATE] = rest.vibrationEnabled
            prefs[REST_DEFAULT] = rest.defaultRestSeconds
            prefs[FOUNDATION_GENERATION] = FoundationGeneration.NAME
        }
        bodyweightDao?.deleteAll()
        trainingBlockDao?.deleteAll()
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
        val TRAINING_FOCUS = stringPreferencesKey("training_focus")
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
        val REST_ALARM_ELIGIBLE = booleanPreferencesKey("rest_alarm_eligible")
        val DRIVE_ACCOUNT = stringPreferencesKey("drive_account_email")
        val DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_SECRET = stringPreferencesKey("auto_backup_secret")
        val AUTO_BACKUP_LAST_SESSION = stringPreferencesKey("auto_backup_last_session_id")
        val AUTO_BACKUP_NEEDS_SIGN_IN = booleanPreferencesKey("auto_backup_needs_sign_in")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_NAME = stringPreferencesKey("last_backup_name")
        val LAST_VERIFIED_BACKUP_AT = longPreferencesKey("last_verified_backup_at")
        val LAST_VERIFIED_BACKUP_NAME = stringPreferencesKey("last_verified_backup_name")
        val LAST_RESTORE_AT = longPreferencesKey("last_restore_at")
        val LAST_RESTORE_NAME = stringPreferencesKey("last_restore_name")
        val RESTORE_RECOVERY_NOTE = stringPreferencesKey("restore_recovery_note")
        val TRAINING_AGE = stringPreferencesKey("training_age")
        val PREFERRED_DAYS = stringSetPreferencesKey("preferred_days")
        val TRAINING_PLACE = stringPreferencesKey("training_place")
        val LIGHTER_WEEK_START = longPreferencesKey("lighter_week_start_epoch_day")
        val FOUNDATION_GENERATION = stringPreferencesKey("foundation_generation")
        val REMINDER_OPT_OUT = booleanPreferencesKey("reminder_opt_out")
        val REMINDER_QUIET_START = intPreferencesKey("reminder_quiet_start_hour")
        val REMINDER_QUIET_END = intPreferencesKey("reminder_quiet_end_hour")
        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val BODYWEIGHT_CHECK_IN_WEEKDAY = stringPreferencesKey("bodyweight_check_in_weekday")
        val PENDING_OCCURRENCE_ID = stringPreferencesKey("pending_occurrence_id")

        fun preferredDaysFrom(raw: Set<String>?): Set<Weekday> =
            raw.orEmpty().mapNotNull { name ->
                Weekday.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            }.toSet()
    }
}

private fun BodyweightEntry.toEntity(recordedAtMs: Long) = BodyweightEntryEntity(
    epochDay = epochDay,
    kg = kg,
    recordedAtMs = if (this.recordedAtMs > 0L) this.recordedAtMs else recordedAtMs,
    zoneId = zoneId.ifBlank { "UTC" },
    offsetSeconds = offsetSeconds,
)

private fun BodyweightEntryEntity.toDomain() = BodyweightEntry(
    epochDay = epochDay,
    kg = kg,
    recordedAtMs = recordedAtMs,
    zoneId = zoneId,
    offsetSeconds = offsetSeconds,
)

private fun TrainingBlock.toEntity(isCurrent: Boolean, archivedAtMs: Long?) = TrainingBlockEntity(
    id = "block-$startEpochDay",
    startEpochDay = startEpochDay,
    weeks = weeks,
    isCurrent = isCurrent,
    archivedAtMs = archivedAtMs,
)

private fun TrainingBlockEntity.toDomain() = TrainingBlock(
    startEpochDay = startEpochDay,
    weeks = weeks,
)
