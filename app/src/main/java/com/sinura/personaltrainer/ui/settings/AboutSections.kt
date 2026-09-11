package com.sinura.personaltrainer.ui.settings


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.data.local.FoundationGeneration
import com.sinura.personaltrainer.domain.PlanSetupCopy
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.InstrumentSwitch
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.components.TemperMark
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.logging.AppLog

/**
 * The way back into the guided setup.
 *
 * Reachable rather than one-shot because the answers it asks for genuinely change — people
 * move gyms, drop to three days, come back after a layoff. The caption states what it will and
 * will not do, because "rebuild my plan" is exactly the phrase someone would expect to
 * REPLACE what they have, and it does not: nothing here deletes a routine that months of
 * history point at.
 */
@Composable
internal fun PlanSetupSection(onRerun: () -> Unit) {
    SettingsGroup(
        title = "Your plan",
        caption = PlanSetupCopy.CAPTION,
    ) {
        GroupedList {
            InstrumentRow(
                title = PlanSetupCopy.ROW_TITLE,
                subtitle = PlanSetupCopy.ROW_SUBTITLE,
                onClick = onRerun,
            )
        }
    }
}

@Composable
internal fun LogRedactSection() {
    var redact by remember { mutableStateOf(AppLog.redactMessages) }
    SettingsGroup(
        title = "Log",
        caption = "On by default. Off only until this process dies. A restart redacts again.",
    ) {
        GroupedList {
            InstrumentRow(
                title = "Redact messages",
                modifier = Modifier.testTag(SettingsTags.REDACT_LOGS),
                checked = redact,
                onCheckedChange = { on ->
                    redact = on
                    AppLog.redactMessages = on
                },
                trailing = { InstrumentSwitch(checked = redact, onCheckedChange = null) },
            )
        }
    }
}

@Composable
internal fun FoundationGenerationSection() {
    SettingsGroup(
        title = "Foundation generation",
        caption = if (FoundationGeneration.FROZEN) {
            "The Temper foundation database is frozen. A second development reset is a defect."
        } else {
            "This is the one authorized development reset. Keep an export off this device. " +
                "It cannot be undone. Only weight units and rest sound, vibration, and " +
                "default duration are kept."
        },
    ) {
        GroupedList {
            InstrumentRow(
                title = "Database",
                trailing = {
                    Text(
                        FoundationGeneration.DATABASE_FILE,
                        style = InstrumentType.numeralSm,
                        color = TextPrimary,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

@Composable
internal fun DiagnosticsSection(onShare: () -> Unit, onClear: () -> Unit) {
    SettingsGroup(
        title = "Diagnostics",
        caption = "Nothing is sent automatically. A shared bundle names the app, schema, " +
            "and device, plus event IDs, exception classes, and Temper stack frames. It " +
            "never includes workout names, weights, notes, bodyweight, emails, tokens, " +
            "or backup files. The last crash is kept on this phone until you clear it.",
    ) {
        SecondaryGymButton(
            text = "Share diagnostics",
            onClick = onShare,
            modifier = Modifier.testTag(SettingsTags.SHARE_DIAGNOSTICS),
        )
        SecondaryGymButton(
            text = "Clear diagnostics",
            onClick = onClear,
            modifier = Modifier.testTag(SettingsTags.CLEAR_DIAGNOSTICS),
        )
    }
}

@Composable
internal fun AboutSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
        TemperMark(size = 64.dp)
        SettingsGroup(
            title = "About",
            caption = "Install or update the APK yourself, or let Obtainium watch GitHub Releases. " +
                "The Play Store is not required.",
        ) {
            GroupedList {
                InstrumentRow(
                    title = "Version",
                    trailing = {
                        Text(
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = InstrumentType.numeralSm,
                            color = TextPrimary,
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}
