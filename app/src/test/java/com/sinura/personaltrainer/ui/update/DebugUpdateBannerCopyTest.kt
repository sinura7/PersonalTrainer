package com.sinura.personaltrainer.ui.update

import com.sinura.personaltrainer.domain.DebugUpdateCopy
import com.sinura.personaltrainer.update.DebugUpdateInstall
import com.sinura.personaltrainer.update.DebugUpdateOffer
import com.sinura.personaltrainer.update.DebugUpdateUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugUpdateBannerCopyTest {
    @Test
    fun idleUpdateFailedAndProgressCopy() {
        val idle = DebugUpdateUi(offer = OFFER)
        assertEquals(DebugUpdateCopy.BANNER_BODY, debugUpdateBody(idle))
        assertEquals(DebugUpdateCopy.ACTION, debugUpdateAction(idle))
        assertEquals("Live 51 is ready", debugUpdateSettingsSummary(idle))

        val downloading = DebugUpdateUi(
            offer = OFFER,
            install = DebugUpdateInstall.Downloading,
            downloadPercent = 40,
        )
        assertEquals("Downloading the update… 40%", debugUpdateBody(downloading))
        assertNull(debugUpdateAction(downloading))
        assertEquals("Downloading the update… 40%", debugUpdateSettingsSummary(downloading))

        val failed = DebugUpdateUi(offer = OFFER, install = DebugUpdateInstall.Failed)
        assertEquals(DebugUpdateCopy.FAILED, debugUpdateBody(failed))
        assertEquals(DebugUpdateCopy.ACTION_RETRY, debugUpdateAction(failed))

        val needs = DebugUpdateUi(offer = OFFER, install = DebugUpdateInstall.NeedsPermission)
        assertEquals(DebugUpdateCopy.NEEDS_PERMISSION, debugUpdateBody(needs))
        assertEquals(DebugUpdateCopy.ACTION_ALLOW, debugUpdateAction(needs))
    }

    companion object {
        private val OFFER = DebugUpdateOffer(
            versionCode = 51,
            tag = "debug-live-2026-09-13-2",
            releaseUrl = "https://github.com/sinura7/PersonalTrainer/releases/tag/debug-live-2026-09-13-2",
            apkUrl = "https://github.com/sinura7/PersonalTrainer/releases/download/debug-live-2026-09-13-2/PersonalTrainer-1.0.0-debug.apk",
        )
    }
}
