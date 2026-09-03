package com.sinura.personaltrainer.ui.progress

import com.sinura.personaltrainer.ui.theme.Metrics
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyViewportTest {
    @Test
    fun firstMuscleRowIsInsideTheFirstViewportAt360x640() {
        assertEquals(300, BodyViewport.figureHeightDp(640))
        assertEquals(300, BodyViewport.figureHeightDp(500))
        assertEquals(440, BodyViewport.figureHeightDp(1_000))
        assertEquals(360, (800 * BodyViewport.FIGURE_FRACTION).toInt())
        assertTrue(
            "first muscle row must land in 360×640 above the tab bar",
            BodyViewport.firstMuscleRowFits(
                BodyViewport.SHORT_WIDTH_DP,
                BodyViewport.SHORT_HEIGHT_DP,
            ),
        )
        val bottom = BodyViewport.firstMuscleRowTopDp(640) + Metrics.rowMin.value.toInt()
        assertTrue(
            "row bottom $bottom must be ≤ ${640 - BodyViewport.TAB_BAR_DP}",
            bottom <= BodyViewport.SHORT_HEIGHT_DP - BodyViewport.TAB_BAR_DP,
        )
    }

    @Test
    fun legendSitsAboveTheFigure() {
        val bodyMap = readOwned("ui/progress/BodyMap.kt")
        val legendAt = bodyMap.indexOf("HeatLegend()")
        val panelAt = bodyMap.indexOf(".height(panelHeight)")
        assertTrue("HeatLegend() must still be composed", legendAt >= 0)
        assertTrue("figure panel must still be composed", panelAt >= 0)
        assertTrue("legend must sit above the figure panel", legendAt < panelAt)
        assertTrue(bodyMap.contains("LocalConfiguration.current.screenHeightDp"))
        assertTrue(bodyMap.contains("figureHeightDp("))
        assertTrue(!bodyMap.contains("440.dp"))
        assertTrue(!bodyMap.contains("PANEL_HEIGHT"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
