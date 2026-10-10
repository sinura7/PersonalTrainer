package com.sinura.personaltrainer.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.PlanDayCopy
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import java.util.Locale

/** Keep the complete question and a separate, reachable Cancel on every picker page. */
@Composable
internal fun PickerHeader(title: String, onCancel: () -> Unit, enabled: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val words = title.uppercase(Locale.ENGLISH)
        val titleWidth = measurer.measure(words, style = InstrumentType.kicker, softWrap = false).size.width
        val cancelWidth = maxOf(
            with(density) { Metrics.touchMin.roundToPx() },
            measurer.measure(PlanDayCopy.CANCEL, style = InstrumentType.bodyStrong, softWrap = false).size.width +
                with(density) { (Metrics.space2 * 2).roundToPx() },
        )
        val gap = with(density) { Metrics.space2.roundToPx() }
        val stacked = titleWidth + cancelWidth + gap > constraints.maxWidth
        val question: @Composable (Modifier) -> Unit = { modifier ->
            Text(
                words,
                modifier = modifier.testTag(PickerHeaderTags.TITLE).semantics { heading() },
                style = InstrumentType.kicker,
                color = TextSecondary,
            )
        }
        val cancel: @Composable () -> Unit = {
            TextButton(
                onClick = onCancel,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = Metrics.space2, vertical = 0.dp),
                modifier = Modifier
                    .width(with(density) { cancelWidth.toDp() })
                    .heightIn(min = Metrics.touchMin)
                    .testTag(PickerHeaderTags.CANCEL),
            ) {
                Text(PlanDayCopy.CANCEL, style = InstrumentType.bodyStrong, color = TextSecondary)
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Metrics.space1)) {
                question(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { cancel() }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Metrics.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                question(Modifier.weight(1f))
                cancel()
            }
        }
    }
}

object PickerHeaderTags {
    const val TITLE = "picker-question"
    const val CANCEL = "picker-cancel"
}
