package com.sinura.personaltrainer.testutil

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.sinura.personaltrainer.ui.home.HomeTags
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags

/**
 * The way back into a live workout on the current page: Today shows its own Resume workout
 * primary and hides the live bar there (blueprint 4.1); every other page shows the bar.
 */
fun ComposeTestRule.liveEntryTag(): String? = listOf(LiveSessionBarTestTags.ROOT, HomeTags.TODAY_RESUME)
    .firstOrNull { tag -> onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.awaitLiveEntry(timeoutMillis: Long = 30_000): String {
    waitUntil(timeoutMillis) { liveEntryTag() != null }
    return checkNotNull(liveEntryTag())
}
