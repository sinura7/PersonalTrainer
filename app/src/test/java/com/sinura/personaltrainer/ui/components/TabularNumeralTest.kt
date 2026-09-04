package com.sinura.personaltrainer.ui.components

import android.app.Application
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.ui.theme.InstrumentType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TabularNumeralTest {
    @Test
    fun onesAndZerosShareParagraphWidthOnEveryNumeralStyle() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val typeface = ResourcesCompat.getFont(context, R.font.space_grotesk_medium)
            ?: Typeface.DEFAULT
        val density = context.resources.displayMetrics.scaledDensity
        listOf(
            InstrumentType.numeralHero,
            InstrumentType.numeralXl,
            InstrumentType.numeralLg,
            InstrumentType.numeralMd,
            InstrumentType.numeralSm,
        ).forEach { style ->
            assertEquals("tnum", style.fontFeatureSettings)
            val paint = Paint().apply {
                this.typeface = typeface
                textSize = style.fontSize.value * density
                fontFeatureSettings = "tnum"
            }
            val ones = paint.measureText("1111")
            val zeros = paint.measureText("0000")
            assertEquals(
                "${style.fontSize} ones=$ones zeros=$zeros",
                ones,
                zeros,
                0.5f,
            )
        }
    }

    @Test
    fun componentGalleryNamesThePublicAtoms() {
        val gallery = readDebug("ui/preview/ComponentStateGallery.kt")
        assertTrue(gallery.contains("fun ComponentStateGallery"))
        assertTrue(gallery.contains("Numeral("))
        assertTrue(gallery.contains("CountBadge("))
        assertTrue(gallery.contains("DangerGymButton("))
        assertTrue(gallery.contains("InstrumentRow("))
        assertTrue(gallery.contains("InstrumentSwitch("))
        assertTrue(gallery.contains("PrimaryGymButton("))
        val surfaces = readMain("ui/components/GymSurfaces.kt")
        assertTrue(surfaces.contains("fun Numeral("))
        assertTrue(surfaces.contains("fun CountBadge("))
        assertTrue(surfaces.contains("toggleable("))
    }

    private fun readMain(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }

    private fun readDebug(relative: String): String {
        val roots = listOf(
            File("app/src/debug/java/com/sinura/personaltrainer"),
            File("../app/src/debug/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
