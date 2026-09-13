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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
) {
    var picking by rememberSaveable { mutableStateOf(HomeStartPage.KIND.name) }
    val page = runCatching { HomeStartPage.valueOf(picking) }.getOrNull()
        ?: HomeStartPage.KIND
    val named = remember(routines) { HomeStart.namedRoutines(routines) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface3) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeStartTags.SHEET)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            when (page) {
                HomeStartPage.KIND -> KindPage(
                    onStartFree = onStartFree,
                    onRoutines = { picking = HomeStartPage.ROUTINES.name },
                    onCardio = { picking = HomeStartPage.CARDIO.name },
                    onExtra = { picking = HomeStartPage.AUX.name },
                )
                HomeStartPage.ROUTINES -> RoutinePage(
                    routines = named,
                    onBack = { picking = HomeStartPage.KIND.name },
                    onStartRoutine = onStartRoutine,
                )
                HomeStartPage.CARDIO -> CardioPage(
                    onBack = { picking = HomeStartPage.KIND.name },
                    onStartCardio = onStartCardio,
                )
                HomeStartPage.AUX -> AuxiliaryPackList(
                    usedPackIds = emptySet(),
                    onPick = onStartExtra,
                    onCancel = { picking = HomeStartPage.KIND.name },
                    modifier = Modifier.testTag(HomeStartTags.EXTRA_PAGE),
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
        )
        HairlineDivider()
        InstrumentRow(
            title = HomeStartCopy.ROUTINE,
            subtitle = HomeStartCopy.ROUTINE_SUBTITLE,
            modifier = Modifier
                .testTag(HomeStartTags.ROUTINE)
                .semantics { contentDescription = HomeStartCopy.ROUTINE },
            onClick = onRoutines,
        )
        HairlineDivider()
        InstrumentRow(
            title = HomeStartCopy.CARDIO,
            subtitle = HomeStartCopy.CARDIO_SUBTITLE,
            modifier = Modifier
                .testTag(HomeStartTags.CARDIO)
                .semantics { contentDescription = HomeStartCopy.CARDIO },
            onClick = onCardio,
        )
        HairlineDivider()
        InstrumentRow(
            title = HomeStartCopy.EXTRA,
            subtitle = HomeStartCopy.EXTRA_SUBTITLE,
            modifier = Modifier
                .testTag(HomeStartTags.EXTRA)
                .semantics { contentDescription = HomeStartCopy.EXTRA },
            onClick = onExtra,
        )
    }
}

@Composable
private fun RoutinePage(
    routines: List<Routine>,
    onBack: () -> Unit,
    onStartRoutine: (String) -> Unit,
) {
    SheetBackKicker(
        title = HomeStartCopy.PICK_ROUTINE,
        onBack = onBack,
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
            )
        }
    }
}

@Composable
private fun CardioPage(
    onBack: () -> Unit,
    onStartCardio: (CardioType) -> Unit,
) {
    SheetBackKicker(
        title = HomeStartCopy.CARDIO,
        onBack = onBack,
    )
    LazyRow(
        modifier = Modifier.testTag(HomeStartTags.CARDIO_PAGE),
        horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
    ) {
        items(ScheduleKind.planCardioTypes, key = { it.name }) { type ->
            CardioPickCard(
                type = type,
                onClick = { onStartCardio(type) },
            )
        }
    }
}

@Composable
private fun SheetBackKicker(
    title: String,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Kicker(text = title, modifier = Modifier.weight(1f))
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = Metrics.touchMin),
        ) {
            Text(
                PlanDayCopy.CANCEL,
                style = InstrumentType.bodyStrong,
                color = TextSecondary,
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

    fun routine(id: String): String = "home-start-routine-$id"
}
