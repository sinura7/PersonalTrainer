package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.ExtraEquipment
import com.sinura.personaltrainer.domain.HomeStart
import com.sinura.personaltrainer.domain.HomeStartCopy
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.ScheduleKind
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.ui.plan.AuxiliaryPackList
import com.sinura.personaltrainer.ui.plan.CardioPickCard
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.units.DateCopy
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay

private enum class HomeStartPage { KIND, ROUTINES, CARDIO, AUX }

/**
 * Home's Start a workout sheet. Not [com.sinura.personaltrainer.ui.workout.StartOptionsSheet]
 * (that stays on Body / History / Plan for log-or-cardio). One tap here
 * starts; it does not mint a Plan row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeStartSheet(
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onStartFree: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onStartCardio: (CardioType) -> Unit,
    onStartExtra: (String) -> Unit,
    suggestedKit: ExtraEquipment = ExtraEquipment.MIXED,
    mutationEnabled: Boolean = true,
    recoveryContent: (@Composable () -> Unit)? = null,
    navigationEnabled: Boolean = true,
    canNavigate: () -> Boolean = { navigationEnabled },
    canDismiss: () -> Boolean = { navigationEnabled },
    onChoiceChangeAccepted: () -> Boolean = { true },
) {
    var picking by rememberSaveable { mutableStateOf(HomeStartPage.KIND.name) }
    // Popup content has its own registry. Keep both page and equipment with the sheet's
    // outer owner so recreation during a held Retry restores the exact draft choices.
    var kitName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = runCatching { HomeStartPage.valueOf(picking) }.getOrNull()
        ?: HomeStartPage.KIND
    val named = remember(routines) { HomeStart.namedRoutines(routines) }
    val mayNavigate by rememberUpdatedState(canNavigate)
    val mayDismiss by rememberUpdatedState(canDismiss)
    val acceptChoiceChange by rememberUpdatedState(onChoiceChangeAccepted)
    val canChange = remember { { value: SheetValue -> value != SheetValue.Hidden || mayDismiss() } }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = canChange)
    val changePage: (HomeStartPage) -> Unit = { next ->
        if (mayNavigate() && acceptChoiceChange()) picking = next.name
    }

    ModalBottomSheet(
        onDismissRequest = { if (mayDismiss()) onDismiss() },
        containerColor = Surface3,
        sheetState = sheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeStartTags.SHEET)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text(
                "Starts now · ${DateCopy.civilDate(LocalTodayEpochDay.current)}",
                modifier = Modifier.testTag(HomeStartTags.DATE),
                style = InstrumentType.caption,
                color = TextSecondary,
            )
            recoveryContent?.invoke()
            when (page) {
                HomeStartPage.KIND -> KindPage(
                    onStartFree = onStartFree,
                    onRoutines = { changePage(HomeStartPage.ROUTINES) },
                    onCardio = { changePage(HomeStartPage.CARDIO) },
                    onExtra = { changePage(HomeStartPage.AUX) },
                    mutationEnabled = mutationEnabled,
                    navigationEnabled = navigationEnabled,
                )
                HomeStartPage.ROUTINES -> RoutinePage(
                    routines = named,
                    onBack = { changePage(HomeStartPage.KIND) },
                    onStartRoutine = onStartRoutine,
                    mutationEnabled = mutationEnabled,
                    navigationEnabled = navigationEnabled,
                )
                HomeStartPage.CARDIO -> CardioPage(
                    onBack = { changePage(HomeStartPage.KIND) },
                    onStartCardio = onStartCardio,
                    mutationEnabled = mutationEnabled,
                    navigationEnabled = navigationEnabled,
                )
                HomeStartPage.AUX -> AuxiliaryPackList(
                    usedPackIds = emptySet(),
                    onPick = onStartExtra,
                    onCancel = { changePage(HomeStartPage.KIND) },
                    suggestedKit = suggestedKit,
                    modifier = Modifier.testTag(HomeStartTags.EXTRA_PAGE),
                    pickEnabled = mutationEnabled,
                    equipment = kitName?.let { ExtraEquipment.fromStorage(it) },
                    onEquipmentChanged = { if (mayNavigate() && acceptChoiceChange()) kitName = it?.name },
                    navigationEnabled = navigationEnabled,
                )
            }
        }
    }
}

@Composable
private fun KindPage(
    onStartFree: () -> Unit,
    onRoutines: () -> Unit,
    onCardio: () -> Unit,
    onExtra: () -> Unit,
    mutationEnabled: Boolean,
    navigationEnabled: Boolean,
) {
    Text(
        SessionOrderCopy.FREE_WORKOUT,
        modifier = Modifier.semantics { heading() },
        style = InstrumentType.title,
        color = TextPrimary,
    )
    GroupedList {
        InstrumentRow(
            title = HomeStartCopy.FREE,
            subtitle = HomeStartCopy.FREE_SUBTITLE,
            modifier = Modifier
                .testTag(HomeStartTags.FREE)
                .semantics { contentDescription = HomeStartCopy.FREE },
            onClick = onStartFree,
            enabled = mutationEnabled,
        )
        HairlineDivider()
        InstrumentRow(
            title = HomeStartCopy.ROUTINE,
            subtitle = HomeStartCopy.ROUTINE_SUBTITLE,
            modifier = Modifier
                .testTag(HomeStartTags.ROUTINE)
                .semantics { contentDescription = HomeStartCopy.ROUTINE },
            onClick = onRoutines,
            enabled = navigationEnabled,
        )
        HairlineDivider()
        InstrumentRow(
            title = HomeStartCopy.CARDIO,
            subtitle = HomeStartCopy.CARDIO_SUBTITLE,
            modifier = Modifier
                .testTag(HomeStartTags.CARDIO)
                .semantics { contentDescription = HomeStartCopy.CARDIO },
            onClick = onCardio,
            enabled = navigationEnabled,
        )
        HairlineDivider()
        InstrumentRow(
            title = HomeStartCopy.EXTRA,
            subtitle = HomeStartCopy.EXTRA_SUBTITLE,
            modifier = Modifier
                .testTag(HomeStartTags.EXTRA)
                .semantics { contentDescription = HomeStartCopy.EXTRA },
            onClick = onExtra,
            enabled = navigationEnabled,
        )
    }
}

@Composable
private fun RoutinePage(
    routines: List<Routine>,
    onBack: () -> Unit,
    onStartRoutine: (String) -> Unit,
    mutationEnabled: Boolean,
    navigationEnabled: Boolean,
) {
    SheetBackKicker(
        title = HomeStartCopy.PICK_ROUTINE,
        onBack = onBack,
        enabled = navigationEnabled,
    )
    if (routines.isEmpty()) {
        Text(
            HomeStartCopy.ROUTINE_EMPTY,
            style = InstrumentType.body,
            color = TextPrimary,
        )
        return
    }
    GroupedList {
        routines.forEachIndexed { index, routine ->
            if (index > 0) HairlineDivider()
            val empty = routine.exercises.isEmpty()
            InstrumentRow(
                title = routine.name,
                subtitle = if (empty) {
                    SessionOrderCopy.NEED_A_LIFT
                } else {
                    val count = routine.exercises.size
                    if (count == 1) "1 lift" else "$count lifts"
                },
                modifier = Modifier
                    .testTag(HomeStartTags.routine(routine.id))
                    .semantics { contentDescription = routine.name },
                onClick = if (empty) null else ({ onStartRoutine(routine.id) }),
                enabled = mutationEnabled,
            )
        }
    }
}

@Composable
private fun CardioPage(
    onBack: () -> Unit,
    onStartCardio: (CardioType) -> Unit,
    mutationEnabled: Boolean,
    navigationEnabled: Boolean,
) {
    SheetBackKicker(
        title = HomeStartCopy.CARDIO,
        onBack = onBack,
        enabled = navigationEnabled,
    )
    LazyRow(
        modifier = Modifier.testTag(HomeStartTags.CARDIO_PAGE),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        items(ScheduleKind.planCardioTypes, key = { it.name }) { type ->
            CardioPickCard(
                type = type,
                onClick = { onStartCardio(type) },
                enabled = mutationEnabled,
            )
        }
    }
}

@Composable
private fun SheetBackKicker(
    title: String,
    onBack: () -> Unit,
    enabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker(text = title, modifier = Modifier.weight(1f))
        TextButton(
            onClick = onBack,
            enabled = enabled,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(HomeStartTags.CANCEL),
        ) {
            Text(
                PlanDayCopy.CANCEL,
                style = InstrumentType.bodyStrong,
                color = if (enabled) TextSecondary else TextDisabled,
            )
        }
    }
}

object HomeStartTags {
    const val SHEET = "home-start-sheet"
    const val FREE = "home-start-free"
    const val ROUTINE = "home-start-routine"
    const val CARDIO = "home-start-cardio"
    const val EXTRA = "home-start-extra"
    const val CARDIO_PAGE = "home-start-cardio-page"
    const val EXTRA_PAGE = "home-start-extra-page"
    const val DATE = "home-start-date"
    const val PENDING = "home-start-pending"
    const val ERROR = "home-start-error"
    const val RETRY = "home-start-retry"
    const val CANCEL = "home-start-cancel"

    fun routine(id: String): String = "home-start-routine-$id"
}
