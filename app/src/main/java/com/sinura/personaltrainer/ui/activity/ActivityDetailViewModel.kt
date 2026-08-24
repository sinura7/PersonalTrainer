package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.ActivitySession
import com.sinura.personaltrainer.domain.cardioMinutes
import com.sinura.personaltrainer.domain.strengthWork
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val session: ActivitySession? = null,
    val strengthSetCount: Int = 0,
    val cardioMinutes: Int = 0,
    val volumeKg: Double = 0.0,
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

    init {
        viewModelScope.launch {
            runCatchingCancellable {
                val session = container.activityRepository.get(activityId)
                if (session == null) {
                    _uiState.value = ActivityDetailUiState(isLoading = false, missing = true)
                    return@runCatchingCancellable
                }
                val work = session.strengthWork()
                _uiState.value = ActivityDetailUiState(
                    isLoading = false,
                    session = session,
                    strengthSetCount = session.strengthSetCount(),
                    cardioMinutes = session.cardioMinutes(),
                    volumeKg = work.volumeKg,
                )
            }.onFailure { thrown ->
                AppLog.w(TAG, "Loading activity detail failed", thrown)
                _uiState.value = ActivityDetailUiState(isLoading = false, missing = true)
            }
        }
    }

    private companion object {
        const val TAG = "PT/ActivityDetail"
    }
}
