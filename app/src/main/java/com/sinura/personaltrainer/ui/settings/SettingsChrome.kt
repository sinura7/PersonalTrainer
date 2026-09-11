package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.theme.Danger
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextTertiary

@Composable
internal fun SettingsHeader() {
    Text(
        "Settings",
        modifier = Modifier
            .fillMaxWidth()
            .background(Pit)
            .padding(horizontal = Metrics.gutter, vertical = Metrics.space3),
        style = InstrumentType.display,
        color = TextPrimary,
        maxLines = 1,
    )
}

/**
 * One preference group: a label, its controls, and one line of explanation.
 *
 * Every section here used to be a naked column of paragraphs on the window colour, separated
 * from the next by the same gap that separated two lines of its own prose — so the boundary
 * between "weight units" and "weekly schedule" existed only for someone reading the words.
 * The kicker labels the group, the container draws it, and the explanations collapse to a
 * single caption underneath: the settings that used to be described are now shown.
 */
@Composable
internal fun SettingsGroup(
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Metrics.kickerGap),
    ) {
        Kicker(title)
        content()
        Text(caption, style = InstrumentType.caption, color = TextTertiary)
    }
}

/** The trailing verb of a row that destroys or replaces data. */
@Composable
internal fun DangerAction(label: String, enabled: Boolean) {
    Text(
        label,
        style = InstrumentType.bodyStrong,
        color = if (enabled) Danger else TextDisabled,
        maxLines = 1,
    )
}
