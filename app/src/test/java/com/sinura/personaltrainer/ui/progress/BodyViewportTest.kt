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
        assertTrue(bodyMap.contains("LocalWindowInfo.current.containerDpSize"))
        assertTrue(bodyMap.contains("figureHeightDp("))
        assertTrue(!bodyMap.contains("440.dp"))
        assertTrue(!bodyMap.contains("PANEL_HEIGHT"))
    }

    @Test
    fun viewSwitchSitsUnderTheFigureNotOverIt() {
        // DESIGN_AUDIT B-05. The chips floated over the panel's top-left corner and read
        // as part of the drawing; they now share a strip under it with the facts line.
        val bodyMap = readOwned("ui/progress/BodyMap.kt")
        val panelAt = bodyMap.indexOf(".height(panelHeight)")
        val chipsAt = bodyMap.indexOf("BodyTags.VIEW_FRONT else BodyTags.VIEW_BACK")
        val factsAt = bodyMap.indexOf("BodyTags.FACTS")
        assertTrue("figure panel must still be composed", panelAt >= 0)
        assertTrue("view chips must still be composed", chipsAt >= 0)
        assertTrue("facts line must be composed", factsAt >= 0)
        assertTrue("view chips must sit under the figure panel", chipsAt > panelAt)
        assertTrue("facts line shares the chips' strip", factsAt > chipsAt)
        assertTrue("nothing floats over the figure any more", !bodyMap.contains("Alignment.TopStart"))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
