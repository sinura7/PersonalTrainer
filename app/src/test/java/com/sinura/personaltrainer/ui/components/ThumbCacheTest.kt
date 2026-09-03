package com.sinura.personaltrainer.ui.components

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Thumbs decode on IO with an explicit inSampleSize. A full-size
 * painterResource on the main thread is the hitch this cache exists to stop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ThumbCacheTest {
    @After
    fun tearDown() {
        ThumbCache.clear()
    }

    @Test
    fun loadRecordsTheRequestedSampleAndReusesTheBitmap() = runBlocking {
        var sample = -1
        var decodes = 0
        ThumbCache.io = Dispatchers.Unconfined
        ThumbCache.decodeResource = { _, _, options ->
            sample = options.inSampleSize
            decodes++
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        }
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        val first = ThumbCache.load(resources, id = 42, sample = ThumbCache.MARK_SAMPLE)
        val second = ThumbCache.load(resources, id = 42, sample = ThumbCache.MARK_SAMPLE)
        assertEquals(ThumbCache.MARK_SAMPLE, sample)
        assertEquals(1, decodes)
        assertSame(first, second)
    }

    @Test
    fun thumbSampleIsOneForThe256Pack() {
        assertEquals(1, ThumbCache.THUMB_SAMPLE)
    }
}
