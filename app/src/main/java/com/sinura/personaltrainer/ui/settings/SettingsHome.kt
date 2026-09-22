package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.PlanSetupCopy
import com.sinura.personaltrainer.domain.SettingsHomeCopy
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary

@Composable
internal fun SettingsHome(
    displaySummary: String,
    remindersSummary: String,
    generatorSummary: String,
    restSummary: String,
    bodyweightSummary: String,
    savePostureSummary: String,
    accountSummary: String,
    onOpen: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    updateSummary: String? = null,
    onOpenUpdate: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(SettingsTags.HOME),
        contentPadding = PaddingValues(
            start = Metrics.gutter,
            end = Metrics.gutter,
            top = Metrics.space2,
            bottom = Metrics.space8,
        ),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        if (updateSummary != null) {
            item(key = "update") {
                GroupedList {
                    SettingsIndexRow(
                        title = SettingsHomeCopy.UPDATE,
                        subtitle = updateSummary,
                        icon = TemperIcons.Check,
                        tag = SettingsTags.ROW_UPDATE,
                        onClick = onOpenUpdate,
                    )
                }
            }
        }
        item(key = "training") {
            GroupedList {
                SettingsIndexRow(
                    title = SettingsHomeCopy.DISPLAY,
                    subtitle = displaySummary,
                    icon = TemperIcons.Display,
                    tag = SettingsTags.ROW_DISPLAY,
                    onClick = { onOpen(SettingsPage.DISPLAY) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.REMINDERS,
                    subtitle = remindersSummary,
                    icon = TemperIcons.Reminders,
                    tag = SettingsTags.ROW_REMINDERS,
                    onClick = { onOpen(SettingsPage.REMINDERS) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.GENERATOR,
                    subtitle = generatorSummary,
                    icon = TemperIcons.Generator,
                    tag = SettingsTags.ROW_GENERATOR,
                    onClick = { onOpen(SettingsPage.GENERATOR) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.REST,
                    subtitle = restSummary,
                    icon = TemperIcons.Rest,
                    tag = SettingsTags.ROW_REST,
                    onClick = { onOpen(SettingsPage.REST) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.BODYWEIGHT,
                    subtitle = bodyweightSummary,
                    icon = TemperIcons.Bodyweight,
                    tag = SettingsTags.ROW_BODYWEIGHT,
                    onClick = { onOpen(SettingsPage.BODYWEIGHT) },
                )
            }
        }
        item(key = "keep") {
            GroupedList {
                SettingsIndexRow(
                    title = SettingsHomeCopy.SAVE_POSTURE,
                    subtitle = savePostureSummary,
                    icon = TemperIcons.Backup,
                    tag = SettingsTags.ROW_SAVE_POSTURE,
                    onClick = { onOpen(SettingsPage.SAVE_POSTURE) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.ACCOUNT,
                    subtitle = accountSummary,
                    icon = TemperIcons.Bodyweight,
                    tag = SettingsTags.ROW_ACCOUNT,
                    onClick = { onOpen(SettingsPage.ACCOUNT) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.BACKUP,
                    subtitle = SettingsHomeCopy.BACKUP_SUMMARY,
                    icon = TemperIcons.Backup,
                    tag = SettingsTags.ROW_BACKUP,
                    onClick = { onOpen(SettingsPage.BACKUP) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.PLAN,
                    subtitle = PlanSetupCopy.ROW_TITLE,
                    icon = TemperIcons.YourPlan,
                    tag = SettingsTags.ROW_PLAN,
                    onClick = { onOpen(SettingsPage.PLAN) },
                )
            }
        }
        item(key = "about") {
            GroupedList {
                SettingsIndexRow(
                    title = SettingsHomeCopy.DIAGNOSTICS,
                    subtitle = SettingsHomeCopy.DIAGNOSTICS_SUMMARY,
                    icon = TemperIcons.Diagnostics,
                    tag = SettingsTags.ROW_DIAGNOSTICS,
                    onClick = { onOpen(SettingsPage.DIAGNOSTICS) },
                )
                IndexHairline()
                SettingsIndexRow(
                    title = SettingsHomeCopy.ABOUT,
                    subtitle = SettingsHomeCopy.ABOUT_SUMMARY,
                    icon = TemperIcons.About,
                    tag = SettingsTags.ROW_ABOUT,
                    onClick = { onOpen(SettingsPage.ABOUT) },
                )
            }
        }
    }
}

@Composable
internal fun SettingsIndexRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    InstrumentRow(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        leading = settingsRowMark(icon),
        trailing = settingsRowChevron(),
        onClick = onClick,
    )
}

@Composable
private fun IndexHairline() {
    HairlineDivider(startIndent = Metrics.rowIconHairline)
}

private fun settingsRowMark(icon: ImageVector): @Composable () -> Unit = {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = TextSecondary,
        modifier = Modifier.size(Metrics.icon),
    )
}

private fun settingsRowChevron(): @Composable RowScope.() -> Unit = {
    Icon(
        imageVector = TemperIcons.Chevron,
        contentDescription = null,
        tint = TextTertiary,
        modifier = Modifier.size(Metrics.chevron),
    )
}
