package com.sinura.personaltrainer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.BuildConfig
import com.sinura.personaltrainer.domain.PlanSetupCopy
import com.sinura.personaltrainer.domain.SettingsHomeCopy
import com.sinura.personaltrainer.ui.components.GroupedList
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.theme.Metrics

@Composable
internal fun SettingsHome(
    displaySummary: String,
    remindersSummary: String,
    generatorSummary: String,
    restSummary: String,
    bodyweightSummary: String,
    onOpen: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
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
        item(key = "training") {
            GroupedList {
                IndexRow(
                    title = SettingsHomeCopy.DISPLAY,
                    subtitle = displaySummary,
                    tag = SettingsTags.ROW_DISPLAY,
                    onClick = { onOpen(SettingsPage.DISPLAY) },
                )
                HairlineDivider()
                IndexRow(
                    title = SettingsHomeCopy.REMINDERS,
                    subtitle = remindersSummary,
                    tag = SettingsTags.ROW_REMINDERS,
                    onClick = { onOpen(SettingsPage.REMINDERS) },
                )
                HairlineDivider()
                IndexRow(
                    title = SettingsHomeCopy.GENERATOR,
                    subtitle = generatorSummary,
                    tag = SettingsTags.ROW_GENERATOR,
                    onClick = { onOpen(SettingsPage.GENERATOR) },
                )
                HairlineDivider()
                IndexRow(
                    title = SettingsHomeCopy.REST,
                    subtitle = restSummary,
                    tag = SettingsTags.ROW_REST,
                    onClick = { onOpen(SettingsPage.REST) },
                )
                HairlineDivider()
                IndexRow(
                    title = SettingsHomeCopy.BODYWEIGHT,
                    subtitle = bodyweightSummary,
                    tag = SettingsTags.ROW_BODYWEIGHT,
                    onClick = { onOpen(SettingsPage.BODYWEIGHT) },
                )
            }
        }
        item(key = "keep") {
            GroupedList {
                IndexRow(
                    title = SettingsHomeCopy.BACKUP,
                    subtitle = SettingsHomeCopy.BACKUP_SUMMARY,
                    tag = SettingsTags.ROW_BACKUP,
                    onClick = { onOpen(SettingsPage.BACKUP) },
                )
                HairlineDivider()
                IndexRow(
                    title = SettingsHomeCopy.PLAN,
                    subtitle = PlanSetupCopy.ROW_TITLE,
                    tag = SettingsTags.ROW_PLAN,
                    onClick = { onOpen(SettingsPage.PLAN) },
                )
            }
        }
        if (BuildConfig.DEBUG) {
            item(key = "debug") {
                GroupedList {
                    IndexRow(
                        title = SettingsHomeCopy.LOG,
                        subtitle = SettingsHomeCopy.LOG_SUMMARY,
                        tag = SettingsTags.ROW_LOG,
                        onClick = { onOpen(SettingsPage.LOG) },
                    )
                    HairlineDivider()
                    IndexRow(
                        title = SettingsHomeCopy.FOUNDATION,
                        subtitle = SettingsHomeCopy.FOUNDATION_SUMMARY,
                        tag = SettingsTags.ROW_FOUNDATION,
                        onClick = { onOpen(SettingsPage.FOUNDATION) },
                    )
                }
            }
        }
        item(key = "about") {
            GroupedList {
                IndexRow(
                    title = SettingsHomeCopy.DIAGNOSTICS,
                    subtitle = SettingsHomeCopy.DIAGNOSTICS_SUMMARY,
                    tag = SettingsTags.ROW_DIAGNOSTICS,
                    onClick = { onOpen(SettingsPage.DIAGNOSTICS) },
                )
                HairlineDivider()
                IndexRow(
                    title = SettingsHomeCopy.ABOUT,
                    subtitle = SettingsHomeCopy.ABOUT_SUMMARY,
                    tag = SettingsTags.ROW_ABOUT,
                    onClick = { onOpen(SettingsPage.ABOUT) },
                )
            }
        }
    }
}

@Composable
private fun IndexRow(
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    InstrumentRow(
        title = title,
        subtitle = subtitle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        onClick = onClick,
    )
}
