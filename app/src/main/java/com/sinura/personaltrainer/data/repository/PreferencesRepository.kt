package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sinura.personaltrainer.data.local.FoundationGeneration
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.dao.TrainingBlockDao
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.local.entity.TrainingBlockEntity
import com.sinura.personaltrainer.data.repository.prefs.AVAILABLE_EQUIPMENT
import com.sinura.personaltrainer.data.repository.prefs.BLOCK_START
import com.sinura.personaltrainer.data.repository.prefs.BLOCK_WEEKS
import com.sinura.personaltrainer.data.repository.prefs.BODYWEIGHT_CHECK_IN_WEEKDAY
import com.sinura.personaltrainer.data.repository.prefs.BODYWEIGHT_KG
import com.sinura.personaltrainer.data.repository.prefs.BODYWEIGHT_LOG
import com.sinura.personaltrainer.data.repository.prefs.BackupPrefs
import com.sinura.personaltrainer.data.repository.prefs.BackupPrefsStore
import com.sinura.personaltrainer.data.repository.prefs.CLOCK_FORMAT
import com.sinura.personaltrainer.data.repository.prefs.CoachingPrefs
import com.sinura.personaltrainer.data.repository.prefs.CoachingPrefsStore
import com.sinura.personaltrainer.data.repository.prefs.DISMISSED_COLLISIONS
import com.sinura.personaltrainer.data.repository.prefs.DisplayPrefs
import com.sinura.personaltrainer.data.repository.prefs.DisplayPrefsStore
import com.sinura.personaltrainer.data.repository.prefs.FOUNDATION_GENERATION
import com.sinura.personaltrainer.data.repository.prefs.HEAT_WINDOW
import com.sinura.personaltrainer.data.repository.prefs.LIGHTER_WEEK_START
import com.sinura.personaltrainer.data.repository.prefs.ONBOARDING_COMPLETE
import com.sinura.personaltrainer.data.repository.prefs.PAST_BLOCKS
import com.sinura.personaltrainer.data.repository.prefs.PREFERRED_DAYS
import com.sinura.personaltrainer.data.repository.prefs.PlanningPrefs
import com.sinura.personaltrainer.data.repository.prefs.PlanningPrefsStore
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_OPT_OUT
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_QUIET_END
import com.sinura.personaltrainer.data.repository.prefs.REMINDER_QUIET_START
import com.sinura.personaltrainer.data.repository.prefs.REST_DEFAULT
import com.sinura.personaltrainer.data.repository.prefs.REST_LAST_PRESET
import com.sinura.personaltrainer.data.repository.prefs.REST_SOUND
import com.sinura.personaltrainer.data.repository.prefs.REST_VIBRATE
import com.sinura.personaltrainer.data.repository.prefs.ReminderPrefs
import com.sinura.personaltrainer.data.repository.prefs.ReminderPrefsStore
import com.sinura.personaltrainer.data.repository.prefs.RestPrefs
import com.sinura.personaltrainer.data.repository.prefs.RestPrefsStore
import com.sinura.personaltrainer.data.repository.prefs.preferredDaysFrom
import com.sinura.personaltrainer.data.repository.prefs.SPLIT_STYLE
import com.sinura.personaltrainer.data.repository.prefs.SettingsStore
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_AGE
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_DAYS
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_EMPHASIS
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_FOCUS
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_GOAL
import com.sinura.personaltrainer.data.repository.prefs.TRAINING_PLACE
import com.sinura.personaltrainer.data.repository.prefs.WEEK_START
import com.sinura.personaltrainer.data.repository.prefs.WEIGHT_UNIT
import com.sinura.personaltrainer.domain.BlockArchive
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.BodyweightLog
import com.sinura.personaltrainer.domain.ClockFormat
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.OnboardingAnswers
import com.sinura.personaltrainer.domain.ReminderPreferences
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

/**
 * The `user_settings` facade.
 *
 * This class was 1,070 lines over forty-six keys spanning ten unrelated feature areas: the
 * weight unit, the coach's goal, the training split, rest sound, reminder quiet hours,
 * bodyweight, training blocks, Drive and automatic backup, library collisions, onboarding, and
 * the restore cutover. It is where every preference went because it was the only place a
 * preference could go — the keys were a private companion, so nothing else could read one.
 *
 * The five self-contained areas now live in `data/repository/prefs/` behind interfaces, and are
 * mixed back in here by delegation. Delegation rather than hand-written forwarding: every
 * signature is declared once, in the interface, and the compiler proves the facade is complete.
 * Call sites are unchanged — `preferencesRepository.weightUnit` still resolves — and callers
 * that only want one area can now depend on [DisplayPrefs] or [BackupPrefs] instead of all
 * forty-six keys, which is what `BackupRepository` has been reaching through this class for.
 *
 * What stays here is what does not belong to one area: the Room-backed bodyweight and
 * training-block mirrors, which need DAOs, and the restore cutover, which writes across
 * every group at once.
 *
 * It is still one DataStore and one file on disk. That part is not incidental — the keys share
 * `user_settings`, so splitting them across stores would be a data migration, and the one
 * signed migration this app is allowed already happened (ADR-010).
 */
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
    private val store: SettingsStore = SettingsStore(dataStore),
) : DisplayPrefs by DisplayPrefsStore(store = store),
    CoachingPrefs by CoachingPrefsStore(store = store),
    PlanningPrefs by PlanningPrefsStore(store = store),
    RestPrefs by RestPrefsStore(store = store),
    BackupPrefs by BackupPrefsStore(store = store) {
    private val dataStore = dataStore
    private val reminders: ReminderPrefs = ReminderPrefsStore(store = store)

    val reminderPreferences: Flow<ReminderPreferences> get() = reminders.reminderPreferences
    val pendingOccurrenceId: Flow<String?> get() = reminders.pendingOccurrenceId
    suspend fun setReminderOptOut(optOut: Boolean) = reminders.setReminderOptOut(optOut)
    suspend fun setReminderQuietHours(startHour: Int, endHour: Int) =
        reminders.setReminderQuietHours(startHour, endHour)
    suspend fun setPendingOccurrenceId(id: String?) = reminders.setPendingOccurrenceId(id)

    private val safePreferences: Flow<Preferences> = store.safePreferences

    private fun <T> pref(read: (Preferences) -> T): Flow<T> = store.pref(read)

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
