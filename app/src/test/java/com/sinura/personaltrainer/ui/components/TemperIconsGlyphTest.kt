package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.ui.navigation.shippingTabs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tabs and Settings index rows draw Allen's glyphs, tinted — not the old
 * plate-language marks and not Material icons.
 */
class TemperIconsGlyphTest {
    @Test
    fun tabsAndSettingsRowsAreAllenGlyphsNotPlatesOrMaterial() {
        val icons = readOwned("ui/components/TemperIcons.kt")
        assertTrue(icons.contains("TemperGlyphPaths.HOME"))
        assertTrue(icons.contains("TemperGlyphPaths.BODY"))
        assertTrue(icons.contains("TemperGlyphPaths.PLAN"))
        assertTrue(icons.contains("TemperGlyphPaths.HISTORY"))
        assertTrue(icons.contains("TemperGlyphPaths.SETTINGS"))
        assertTrue(icons.contains("TemperGlyphPaths.DISPLAY"))
        assertTrue(icons.contains("TemperGlyphPaths.REMINDERS"))
        assertTrue(icons.contains("TemperGlyphPaths.GENERATOR"))
        assertTrue(icons.contains("TemperGlyphPaths.REST"))
        assertTrue(icons.contains("TemperGlyphPaths.BODYWEIGHT"))
        assertTrue(icons.contains("TemperGlyphPaths.BACKUP"))
        assertTrue(icons.contains("TemperGlyphPaths.YOUR_PLAN"))
        assertTrue(icons.contains("TemperGlyphPaths.DIAGNOSTICS"))
        assertTrue(icons.contains("TemperGlyphPaths.ABOUT"))
        assertTrue(icons.contains("pathFillType = PathFillType.EvenOdd"))
        assertFalse(icons.contains("torsoMark"))
        assertFalse(icons.contains("bodyMark"))
        assertFalse(icons.contains("stackMark"))
        assertFalse(icons.contains("settingsMark"))
        assertFalse(icons.contains("displayMark"))
        assertFalse(icons.contains("import androidx.compose.material.icons"))

        val nav = readOwned("ui/navigation/AppNav.kt")
        assertTrue(nav.contains("TemperIcons.Home"))
        assertTrue(nav.contains("TemperIcons.Body"))
        assertTrue(nav.contains("TemperIcons.Plan"))
        assertTrue(nav.contains("TemperIcons.History"))
        assertTrue(nav.contains("TemperIcons.Settings"))
        val tabDraw = nav.substringAfter("private fun NavTab(").substringBefore("private const val")
        assertTrue(tabDraw.contains("tab.icon"))
        assertTrue(tabDraw.contains("tint = content"))

        val home = readOwned("ui/settings/SettingsHome.kt")
        assertTrue(home.contains("TemperIcons.YourPlan"))
        assertFalse(home.contains("TemperIcons.Plan"))
        val rowMark = home.substringAfter("private fun settingsRowMark")
        assertTrue(rowMark.contains("tint = TextSecondary"))
    }

    @Test
    fun glyphPathsAreTintableSilhouettesNotOpaqueSquares() {
        val paths = listOf(
            TemperGlyphPaths.HOME,
            TemperGlyphPaths.BODY,
            TemperGlyphPaths.PLAN,
            TemperGlyphPaths.HISTORY,
            TemperGlyphPaths.SETTINGS,
            TemperGlyphPaths.DISPLAY,
            TemperGlyphPaths.REMINDERS,
            TemperGlyphPaths.GENERATOR,
            TemperGlyphPaths.REST,
            TemperGlyphPaths.BODYWEIGHT,
            TemperGlyphPaths.BACKUP,
            TemperGlyphPaths.YOUR_PLAN,
            TemperGlyphPaths.DIAGNOSTICS,
            TemperGlyphPaths.ABOUT,
        )
        assertEquals(14, paths.toSet().size)
        paths.forEach { d ->
            assertTrue(d.startsWith("M"))
            assertTrue(d.contains("Z"))
            assertTrue(d.length > 200)
            assertFalse(d.contains("h24"))
            assertFalse(d.contains("H24"))
        }
        assertNotEquals(TemperGlyphPaths.PLAN, TemperGlyphPaths.YOUR_PLAN)
    }

    @Test
    fun builtVectorsKeepAllenNamesAndSplitPlanFromYourPlan() {
        assertEquals("Home", TemperIcons.Home.name)
        assertEquals("Body", TemperIcons.Body.name)
        assertEquals("Plan", TemperIcons.Plan.name)
        assertEquals("History", TemperIcons.History.name)
        assertEquals("Settings", TemperIcons.Settings.name)
        assertEquals("YourPlan", TemperIcons.YourPlan.name)
        assertEquals("Display", TemperIcons.Display.name)
        assertEquals(
            listOf("Home", "Body", "Plan", "History", "Settings"),
            shippingTabs.map { it.icon.name },
        )
        assertNotEquals(TemperIcons.Plan.name, TemperIcons.YourPlan.name)
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
