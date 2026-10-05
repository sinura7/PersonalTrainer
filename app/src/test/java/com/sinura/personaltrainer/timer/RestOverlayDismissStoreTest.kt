package com.sinura.personaltrainer.timer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestOverlayDismissStoreTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun reset() {
        RestOverlayDismissStore.clear(context)
    }

    @Test
    fun dismissIsScopedToTimerId() {
        RestOverlayDismissStore.dismissForRest(context, "rest-a")
        assertTrue(RestOverlayDismissStore.isDismissedForRest(context, "rest-a"))
        assertFalse(RestOverlayDismissStore.isDismissedForRest(context, "rest-b"))
    }

    @Test
    fun clearRemovesDismiss() {
        RestOverlayDismissStore.dismissForRest(context, "rest-a")
        RestOverlayDismissStore.clear(context)
        assertFalse(RestOverlayDismissStore.isDismissedForRest(context, "rest-a"))
    }
}
