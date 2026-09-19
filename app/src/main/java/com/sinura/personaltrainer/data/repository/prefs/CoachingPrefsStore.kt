package com.sinura.personaltrainer.data.repository.prefs

import androidx.datastore.preferences.core.edit
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingFocus
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import kotlinx.coroutines.flow.Flow

/** What the coach is asked to optimise for, and what the lifter actually has to lift with. */
interface CoachingPrefs {
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
    val coachPreferences: Flow<CoachPreferences>
    val trainingAge: Flow<TrainingAge>

    /**
     * Null means the key was never written — an install from before Job 3, or a restore of
     * a file that predates the field. Readers call `OnboardingAnswers.inferPlace` in that
     * case rather than pretending everyone trains in a full gym.
     */
    val trainingPlace: Flow<TrainingPlace?>
    val trainingFocus: Flow<TrainingFocus>

    /**
     * The window the body map opens on.
     *
     * Nothing persisted this before, so the map reset to a default every time the process
     * died — a preference the user re-expressed on every cold start and the app never learned.
     */
    val heatWindow: Flow<HeatWindow>

    suspend fun setTrainingGoal(goal: TrainingGoal)
    suspend fun setTrainingEmphasis(emphasis: TrainingEmphasis)
    suspend fun setAvailableEquipment(equipment: Set<String>)
    suspend fun setTrainingAge(age: TrainingAge)
    suspend fun setTrainingPlace(place: TrainingPlace)

    /**
     * One or many places, stored in the same key as the old single value.
     *
     * A single name is what every backup written before mixed places holds. Comma-separated
     * names are a mixed kit. Readers that only want one place take [TrainingPlace.widest].
     */
    suspend fun setTrainingPlaces(places: Set<TrainingPlace>)
    suspend fun setTrainingFocus(focus: TrainingFocus)
    suspend fun setHeatWindow(window: HeatWindow)
}

internal class CoachingPrefsStore(private val store: SettingsStore) : CoachingPrefs {
    override val coachPreferences: Flow<CoachPreferences> = store.pref { prefs ->
        CoachPreferences(
            goal = TrainingGoal.fromStorage(prefs[TRAINING_GOAL]),
            // Empty means gym-floor (no Hyper Pro), never "owns nothing" — see CoachPreferences.
            availableEquipment = prefs[AVAILABLE_EQUIPMENT].orEmpty(),
            emphasis = TrainingEmphasis.fromStorage(prefs[TRAINING_EMPHASIS]),
        )
    }

    override val trainingAge: Flow<TrainingAge> =
        store.pref { prefs -> TrainingAge.fromStorage(prefs[TRAINING_AGE]) }

    override val trainingPlace: Flow<TrainingPlace?> =
        store.pref { prefs -> prefs[TRAINING_PLACE]?.let { TrainingPlace.fromStorage(it) } }

    override val trainingFocus: Flow<TrainingFocus> =
        store.pref { prefs -> TrainingFocus.fromStorage(prefs[TRAINING_FOCUS]) }

    override val heatWindow: Flow<HeatWindow> =
        store.pref { prefs -> HeatWindow.fromStorage(prefs[HEAT_WINDOW]) }

    override suspend fun setTrainingGoal(goal: TrainingGoal) {
        store.data.edit { prefs -> prefs[TRAINING_GOAL] = goal.name }
    }

    override suspend fun setTrainingEmphasis(emphasis: TrainingEmphasis) {
        store.data.edit { prefs -> prefs[TRAINING_EMPHASIS] = emphasis.name }
    }

    override suspend fun setAvailableEquipment(equipment: Set<String>) {
        store.data.edit { prefs -> prefs[AVAILABLE_EQUIPMENT] = equipment }
    }

    override suspend fun setTrainingAge(age: TrainingAge) {
        store.data.edit { prefs -> prefs[TRAINING_AGE] = age.name }
    }

    override suspend fun setTrainingPlace(place: TrainingPlace) {
        setTrainingPlaces(setOf(place))
    }

    override suspend fun setTrainingPlaces(places: Set<TrainingPlace>) {
        val resolved = places.ifEmpty { setOf(TrainingPlace.FULL_GYM) }
        store.data.edit { prefs -> prefs[TRAINING_PLACE] = TrainingPlace.formatPlaces(resolved) }
    }

    override suspend fun setTrainingFocus(focus: TrainingFocus) {
        store.data.edit { prefs -> prefs[TRAINING_FOCUS] = focus.name }
    }

    override suspend fun setHeatWindow(window: HeatWindow) {
        store.data.edit { prefs -> prefs[HEAT_WINDOW] = window.name }
    }
}
