package com.sinura.personaltrainer.ui.plan

import com.sinura.personaltrainer.domain.PlanDayCopy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanDayConfirmTest {
    @Test
    fun planRemoveConfirmsWithDangerAndIdleStartIsVolt() {
        assertEquals("Remove Push?", PlanDayCopy.removeTitle("Push"))
        assertTrue(PlanDayCopy.REMOVE_BODY.contains("History"))
        val planDay = readOwned("ui/plan/PlanDayScreen.kt")
        assertTrue(planDay.contains("ConfirmActionDialog("))
        assertTrue(planDay.contains("destructive = true"))
        assertTrue(planDay.contains("color = Danger"))
        assertTrue(planDay.contains("viewModel.deleteSession"))
        val rest = readOwned("ui/workout/RestTimerScreen.kt")
        assertTrue(rest.contains("text = \"Start rest\""))
        assertTrue(rest.contains("PrimaryGymButton("))
        assertTrue(!rest.contains("label = \"Start rest\""))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
