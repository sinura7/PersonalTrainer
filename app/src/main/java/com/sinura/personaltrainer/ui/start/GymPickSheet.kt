package com.sinura.personaltrainer.ui.start

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.Gym
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.GymVisit
import com.sinura.personaltrainer.ui.components.GhostButton
import com.sinura.personaltrainer.ui.components.GymPickerList
import com.sinura.personaltrainer.ui.components.GymTags
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

enum class GymPickMode { START, SELECT_ONLY, MOVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymPickSheet(
    visible: Boolean,
    mode: GymPickMode,
    title: String,
    caption: String?,
    gyms: List<Gym>,
    visits: Map<String?, GymVisit>,
    selectedId: String?,
    pendingGymId: String?,
  loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPick: (String) -> Unit,
    onCancel: () -> Unit,
    includeUnassigned: Boolean = false,
    cancelLabel: String? = null,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onCancel,
        containerColor = Surface3,
        sheetState = sheetState,
        modifier = Modifier.testTag(
            when (mode) {
                GymPickMode.MOVE -> GymTags.MOVE_SHEET
                else -> "gym-pick-sheet"
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space3),
        ) {
            Text(
                title,
                style = InstrumentType.heroTitle,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (caption != null) {
                Text(caption, style = InstrumentType.caption, color = TextTertiary)
            }
            GymPickerList(
                gyms = gyms,
                visits = visits,
                selectedId = selectedId,
                onPick = onPick,
                loading = loading,
                error = error,
                onRetry = onRetry,
                includeUnassigned = includeUnassigned,
                pendingId = pendingGymId,
                enabled = pendingGymId == null,
            )
            GhostButton(
                label = cancelLabel ?: PlanDayCopy.CANCEL,
                onClick = onCancel,
            )
        }
    }
}

/** Observe gyms and visits for pick sheets. */
@Composable
fun rememberGymPickData(
    gymsFlow: StateFlow<List<Gym>>,
    visitsFlow: StateFlow<Map<String?, GymVisit>>,
): Pair<List<Gym>, Map<String?, GymVisit>> {
    val gyms by gymsFlow.collectAsStateWithLifecycle()
    val visits by visitsFlow.collectAsStateWithLifecycle()
    return gyms to visits
}
