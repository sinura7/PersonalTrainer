package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivityDetailCopy
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.CompletedTrainingDetailLoad
import com.sinura.personaltrainer.domain.cardioMinutes
import com.sinura.personaltrainer.domain.strengthWork
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [missing] and [failed] are different answers to different questions. Missing is a query
 * that succeeded and found no row: the activity is not on this phone. Failed is a query that
 * threw: the activity may well be there and the read is what broke. The screen used to fold
 * both into "That activity is no longer on this phone", which for a Room fault was untrue and
 * offered no way to find out.
 */
data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val failed: Boolean = false,
    val session: ActivitySession? = null,
    val strengthSetCount: Int = 0,
    val cardioMinutes: Int = 0,
    val volumeKg: Double = 0.0,
    val durationMinutes: Int = 0,
)

class ActivityDetailViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    container: AppDependencies = application.appContainer(),
) : AppViewModel(application, container) {
    private val activityId: String =
        savedStateHandle.get<String>("activityId")
            ?: savedStateHandle.get<String>("sessionId").orEmpty()

    private val _uiState = MutableStateFlow(ActivityDetailUiState())
    val uiState: StateFlow<ActivityDetailUiState> = _uiState.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    /** Re-read after a failed load. A no-op unless the last read actually threw. */
    fun retry() {
        if (!_uiState.value.failed) return
        load()
    }

    private fun load() {
        loading?.cancel()
        _uiState.value = ActivityDetailUiState()
        loading = viewModelScope.launch {
            val result = runCatchingCancellable { container.activityRepository.get(activityId) }
            val load = CompletedTrainingDetailLoad.fromResult(result)
            if (load.failed) {
                AppLog.e(TAG, "Loading activity detail failed", result.exceptionOrNull())
                _uiState.value = ActivityDetailUiState(isLoading = false, failed = true)
                return@launch
            }
            if (load.missing) {
                _uiState.value = ActivityDetailUiState(isLoading = false, missing = true)
                return@launch
            }
            val session = checkNotNull(load.value)
            val work = session.strengthWork()
            val cardioMinutes = session.cardioMinutes()
            _uiState.value = ActivityDetailUiState(
                isLoading = false,
                session = session,
                strengthSetCount = session.strengthSetCount(),
                cardioMinutes = cardioMinutes,
                volumeKg = work.volumeKg,
                durationMinutes = ActivityDetailCopy.receiptDurationMinutes(session, cardioMinutes),
            )
        }
    }

    private companion object {
        const val TAG = "PT/ActivityDetail"
    }
}
