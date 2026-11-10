package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.data.repository.GymArchiveOutcome
import com.sinura.personaltrainer.data.repository.GymRepository
import com.sinura.personaltrainer.data.repository.GymRestoreOutcome
import com.sinura.personaltrainer.data.repository.GymSaveOutcome
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GymsUiState(
    val gyms: List<Gym> = emptyList(),
    val visits: Map<String?, GymVisit> = emptyMap(),
    val activeCount: Int = 0,
    val unassignedSessions: Int = 0,
    val error: String? = null,
)

class GymsViewModel @JvmOverloads constructor(
    application: Application,
    container: AppDependencies = application.appContainer(),
    private val gymRepository: GymRepository = container.gymRepository,
) : AppViewModel(application, container) {

    val uiState: StateFlow<GymsUiState> = combine(
        gymRepository.observeGyms(),
        gymRepository.observeVisits(),
    ) { gyms, visits ->
        GymsUiState(
            gyms = gyms,
            visits = visits,
            activeCount = gyms.count { !it.archived },
            unassignedSessions = visits[null]?.sessions ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymsUiState())

    fun reorder(activeIds: List<String>) {
        viewModelScope.launch {
            runCatchingCancellable { gymRepository.reorder(activeIds) }
        }
    }

    fun move(activeIds: List<String>, id: String, delta: Int) {
        val index = activeIds.indexOf(id)
        if (index < 0) return
        val target = index + delta
        if (target !in activeIds.indices) return
        val next = activeIds.toMutableList()
        val item = next.removeAt(index)
        next.add(target, item)
        reorder(next)
    }

    suspend fun saveNew(name: String, shortName: String, badge: String?): GymSaveOutcome =
        gymRepository.add(name, shortName, badge)

    suspend fun saveExisting(id: String, name: String, shortName: String, badge: String): GymSaveOutcome =
        gymRepository.update(id, name, shortName, badge)

    suspend fun archive(id: String): GymArchiveOutcome = gymRepository.archive(id)

    suspend fun restore(id: String): GymRestoreOutcome = gymRepository.restore(id)
}

object GymsTags {
    const val SECTION = "settings-gyms"
    const val ADD = "gyms-add"
    fun row(id: String): String = "gyms-row-$id"
    const val UNASSIGNED = "gyms-unassigned"
    const val ARCHIVED_KICKER = "gyms-archived-kicker"
    fun archivedRow(id: String): String = "gyms-archived-$id"
    const val EDIT_SHEET = "gym-edit-sheet"
    const val EDIT_SAVE = "gym-edit-save"
    const val EDIT_ARCHIVE = "gym-edit-archive"
    const val EDIT_RESTORE = "gym-edit-restore"
    fun editField(field: String): String = "gym-edit-$field"
}
