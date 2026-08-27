package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.domain.GetStartedCopy
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Surface3
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

/**
 * First-visit invitation on Home. Not a launch gate.
 *
 * Three paths, one Volt: generate a schedule from the questionnaire, build
 * the week by hand, or start a workout with nothing pinned. Dismissing
 * leaves Home; the empty card still offers the same three acts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GetStartedSheet(
    onGenerate: () -> Unit,
    onBuild: () -> Unit,
    onWorkout: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface3) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.gutter)
                .padding(bottom = Metrics.space7)
                .testTag(HomeTags.GET_STARTED),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space2)) {
                Text(GetStartedCopy.TITLE, style = InstrumentType.title, color = TextPrimary)
                Text(GetStartedCopy.BODY, style = InstrumentType.body, color = TextSecondary)
            }
            PrimaryGymButton(
                text = GetStartedCopy.GENERATE,
                onClick = onGenerate,
                modifier = Modifier
                    .testTag(HomeTags.GENERATE)
                    .semantics { contentDescription = GetStartedCopy.GENERATE },
            )
            SecondaryGymButton(
                text = GetStartedCopy.BUILD,
                onClick = onBuild,
                modifier = Modifier
                    .testTag(HomeTags.BUILD_WEEK)
                    .semantics { contentDescription = GetStartedCopy.BUILD },
            )
            SecondaryGymButton(
                text = GetStartedCopy.WORKOUT,
                onClick = onWorkout,
                modifier = Modifier
                    .testTag(HomeTags.STARTER_WORKOUT)
                    .semantics { contentDescription = GetStartedCopy.WORKOUT },
            )
        }
    }
}
