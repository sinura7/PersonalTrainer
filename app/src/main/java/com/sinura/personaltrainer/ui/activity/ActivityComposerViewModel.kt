package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilDateTime
import com.sinura.personaltrainer.domain.DstGapPolicy
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.IdFactory
import com.sinura.personaltrainer.util.JvmTime
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ComposerMode { STRENGTH, CARDIO, MIXED }

data class ComposerStrengthLine(
    val exercise: Exercise,
    val weightKg: Double,
    val reps: Int,
)

data class ComposerCardioLine(
    val type: CardioType,
    val minutes: Int,
    val distanceKm: Double?,
    val indoor: Boolean,
)

data class ActivityComposerUiState(
    val mode: ComposerMode = ComposerMode.STRENGTH,
    val title: String = "",
    val epochDay: Long = 0L,
    val strength: List<ComposerStrengthLine> = emptyList(),
    val cardio: List<ComposerCardioLine> = emptyList(),
    val catalog: List<Exercise> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false,
)

class ActivityComposerViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
    private val ids: IdFactory = IdFactory.Uuid,
    private val clock: com.sinura.personaltrainer.domain.TimePort = JvmTime,
) : AppViewModel(application, container) {
    private val mode = MutableStateFlow(parseMode(savedStateHandle.get<String>("mode")))
    private val title = MutableStateFlow("")
    private val epochDay = MutableStateFlow(clock.captureNow().localEpochDay)
    private val strength = MutableStateFlow<List<ComposerStrengthLine>>(emptyList())
    private val cardio = MutableStateFlow<List<ComposerCardioLine>>(emptyList())
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())
    private val error = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)

    val uiState: StateFlow<ActivityComposerUiState> = combine(
        combine(mode, title, epochDay, catalog) { currentMode, name, day, lifts ->
            Quad(currentMode, name, day, lifts)
        },
        combine(strength, cardio, error, saving) { sets, cardioLines, err, busy ->
            Flags(sets, cardioLines, err, busy)
        },
    ) { quad, flags ->
        ActivityComposerUiState(
            mode = quad.mode,
            title = quad.title,
            epochDay = quad.epochDay,
            strength = flags.strength,
            cardio = flags.cardio,
            catalog = quad.catalog,
            error = flags.error,
            saving = flags.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityComposerUiState())

    private val _savedId = MutableStateFlow<String?>(null)
    val savedId: StateFlow<String?> = _savedId.asStateFlow()

    init {
        viewModelScope.launch {
            container.exerciseRepository.observeAll().collect { catalog.value = it }
        }
    }

    fun setTitle(value: String) {
        title.value = value
        error.value = null
    }

    fun setEpochDay(value: Long) {
        val today = clock.captureNow().localEpochDay
        epochDay.value = value.coerceAtMost(today)
        error.value = null
    }

    fun shiftDay(delta: Long) {
        setEpochDay(epochDay.value + delta)
    }

    fun addStrength(exercise: Exercise, weightKg: Double, reps: Int) {
        if (reps <= 0) {
            error.value = "Reps must be at least 1."
            return
        }
        strength.value = strength.value + ComposerStrengthLine(exercise, weightKg, reps)
        error.value = null
    }

    fun removeStrength(index: Int) {
        strength.value = strength.value.filterIndexed { i, _ -> i != index }
    }

    fun addCardio(type: CardioType, minutes: Int, distanceKm: Double?, indoor: Boolean) {
        if (minutes <= 0) {
            error.value = "Duration must be at least one minute."
            return
        }
        cardio.value = cardio.value + ComposerCardioLine(type, minutes, distanceKm, indoor)
        error.value = null
    }

    fun removeCardio(index: Int) {
        cardio.value = cardio.value.filterIndexed { i, _ -> i != index }
    }

    fun save() {
        if (saving.value) return
        saving.value = true
        viewModelScope.launch {
            val write = runCatchingCancellable { confirmDraft() }
            saving.value = false
            write.onSuccess { result ->
                when (result) {
                    is ActivityWrite.Accepted -> {
                        error.value = null
                        _savedId.value = result.session.id
                    }
                    is ActivityWrite.Rejected -> error.value = result.reason
                }
            }.onFailure { thrown ->
                AppLog.w(TAG, "Saving an activity failed", thrown)
                error.value = "Could not save that session. Try again."
            }
        }
    }

    fun onSavedHandled() {
        _savedId.value = null
    }

    internal suspend fun confirmDraft(): ActivityWrite {
        val now = clock.captureNow()
        val performed = clock.resolveLocal(
            CivilDateTime(CivilDate.fromEpochDay(epochDay.value), hour = 12, minute = 0),
            now.zoneId,
            gap = DstGapPolicy.SHIFT_FORWARD,
        )
        val blocks = buildBlocks()
        val draft = ActivityDraft(
            origin = ActivityOrigin.BACKDATED,
            title = resolvedTitle(),
            performedStart = performed,
            performedEnd = performed,
            blocks = blocks,
        )
        return container.confirmActivity(draft, now)
    }

    private fun resolvedTitle(): String {
        val typed = title.value.trim()
        if (typed.isNotEmpty()) return typed
        return when (mode.value) {
            ComposerMode.CARDIO -> cardio.value.firstOrNull()?.type?.name?.lowercase()
                ?.replaceFirstChar { it.uppercase() } ?: "Cardio"
            ComposerMode.MIXED -> "Mixed session"
            ComposerMode.STRENGTH -> "Workout"
        }
    }

    private fun buildBlocks(): List<ActivityBlock> {
        val includeStrength = mode.value != ComposerMode.CARDIO
        val includeCardio = mode.value != ComposerMode.STRENGTH
        val blocks = mutableListOf<ActivityBlock>()
        var order = 0
        if (includeStrength) {
            strength.value.forEach { line ->
                blocks += StrengthBlock(
                    id = ids.newId(),
                    sortOrder = order++,
                    exerciseId = line.exercise.id,
                    exerciseName = line.exercise.name,
                    loadType = line.exercise.loadType,
                    equipment = line.exercise.equipment,
                    muscles = line.exercise.muscles,
                    sets = listOf(
                        StrengthSet(
                            id = ids.newId(),
                            setNumber = 1,
                            weightKg = line.weightKg,
                            reps = line.reps,
                            rpe = null,
                            isWarmup = false,
                            completedAtMs = clock.nowMillis(),
                        ),
                    ),
                )
            }
        }
        if (includeCardio) {
            cardio.value.forEach { line ->
                blocks += CardioBlock(
                    id = ids.newId(),
                    sortOrder = order++,
                    type = line.type,
                    indoor = line.indoor,
                    elapsedSeconds = line.minutes.toLong() * 60L,
                    movingSeconds = line.minutes.toLong() * 60L,
                    distanceMeters = line.distanceKm?.takeIf { it > 0.0 }?.times(1_000.0),
                    elevationMeters = null,
                    heartRateBpm = null,
                    energyKj = null,
                    rpe = null,
                    routeRef = null,
                )
            }
        }
        return blocks
    }

    private fun parseMode(raw: String?): ComposerMode = when (raw?.lowercase()) {
        "cardio" -> ComposerMode.CARDIO
        "mixed" -> ComposerMode.MIXED
        else -> ComposerMode.STRENGTH
    }

    private data class Quad(
        val mode: ComposerMode,
        val title: String,
        val epochDay: Long,
        val catalog: List<Exercise>,
    )

    private data class Flags(
        val strength: List<ComposerStrengthLine>,
        val cardio: List<ComposerCardioLine>,
        val error: String?,
        val saving: Boolean,
    )

    private companion object {
        const val TAG = "PT/ActivityComposer"
    }
}
