package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary

/** Opens the existing explanation and Apply action without covering the entry. */
@Composable
internal fun TempoCoachShortcut(enabled: Boolean, description: String, onOpen: () -> Unit) {
    Row(Modifier.testTag(WorkoutTestTags.TEMPO_COACH_CARD)) {
        TextButton(onClick = onOpen, enabled = enabled,
            modifier = Modifier.heightIn(min = Metrics.touchMin).testTag(WorkoutTestTags.MICRO_REC_WHY)
                .semantics { contentDescription = "Tempo suggestion. $description" }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                Image(painterResource(R.drawable.tempo_coach_avatar), contentDescription = null,
                    modifier = Modifier.size(Metrics.icon))
                Text("Tempo", modifier = Modifier.clearAndSetSemantics { }, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
    }
}
