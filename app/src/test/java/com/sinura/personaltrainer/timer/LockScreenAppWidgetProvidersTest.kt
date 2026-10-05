package com.sinura.personaltrainer.timer

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LockScreenAppWidgetProvidersTest {
    @Test
    fun manifestDeclaresLockWidgetProvidersAndServiceBox() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val pm = context.packageManager
        val wide = pm.getReceiverInfo(
            android.content.ComponentName(context, LockScreenRestWideWidget::class.java),
            PackageManager.GET_META_DATA,
        )
        val small = pm.getReceiverInfo(
            android.content.ComponentName(context, LockScreenRestSmallWidget::class.java),
            PackageManager.GET_META_DATA,
        )
        val serviceBox = pm.getReceiverInfo(
            android.content.ComponentName(context, RestLockScreenServiceBoxReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        org.junit.Assert.assertTrue(wide.metaData.containsKey("android.appwidget.provider"))
        org.junit.Assert.assertTrue(small.metaData.containsKey("android.appwidget.provider"))
        org.junit.Assert.assertTrue(wide.metaData.containsKey("samsung.appwidget.monotone.info"))
        assertNotNull(serviceBox)
    }
}
