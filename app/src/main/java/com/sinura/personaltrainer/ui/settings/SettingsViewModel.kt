package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.sinura.personaltrainer.AppViewModel
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AppViewModel(application) {
    val weightUnit: StateFlow<WeightUnit> = container.preferencesRepository.weightUnit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WeightUnit.KG,
        )

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch {
            container.preferencesRepository.setWeightUnit(unit)
        }
    }
}
