package com.sinura.personaltrainer.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkBackPolicyTest {
    @Test
    fun restKickerIsALiveRegionOnlyWhenFinished() {
        assertEquals("REST", TalkBackPolicy.restKicker(justFinished = false))
        assertEquals("Back to the bar", TalkBackPolicy.restKicker(justFinished = true))
        assertFalse(TalkBackPolicy.announceRestKicker(justFinished = false))
        assertTrue(TalkBackPolicy.announceRestKicker(justFinished = true))
        assertTrue(TalkBackPolicy.announceRecordBanner())
    }

    @Test
    fun typedBodyweightOpensTheSameRangeAsTheWheel() {
        assertEquals(75.0, TalkBackPolicy.parseTypedBodyweightKg("75", WeightUnit.KG)!!, 0.0001)
        assertEquals(
            BodyweightSteps.toKg(165, WeightUnit.LBS),
            TalkBackPolicy.parseTypedBodyweightKg("165", WeightUnit.LBS)!!,
            0.0001,
        )
        assertNull(TalkBackPolicy.parseTypedBodyweightKg("abc", WeightUnit.KG))
        assertNull(TalkBackPolicy.parseTypedBodyweightKg("10", WeightUnit.KG))
        assertNull(TalkBackPolicy.parseTypedBodyweightKg("400", WeightUnit.KG))
        assertEquals(TalkBackPolicy.BODYWEIGHT_TYPED_TITLE, "Bodyweight")
        assertEquals(TalkBackPolicy.BODYWEIGHT_TYPED_SPOKEN, "Type bodyweight")
    }

    @Test
    fun headingsRolesAndLiveRegionsLandOnOwnedSurfaces() {
        val gymSurfaces = readOwned("ui/components/GymSurfaces.kt")
        assertTrue(gymSurfaces.contains("heading()"))
        assertTrue(gymSurfaces.contains("Role.Button"))

        val common = readOwned("ui/components/Common.kt")
        assertTrue(common.contains("role = Role.Button"))
        assertTrue(common.contains("LiveRegionMode.Polite"))
        assertTrue(common.contains("TalkBackPolicy.announceRestKicker"))

        val gymStatus = readOwned("ui/components/GymStatus.kt")
        assertTrue(gymStatus.contains("LiveRegionMode.Polite"))
        assertTrue(gymStatus.contains("TalkBackPolicy.announceRecordBanner"))

        val weekStrip = readOwned("ui/components/WeekStrip.kt")
        assertTrue(weekStrip.contains("Role.Tab"))
        assertTrue(weekStrip.contains("selectable("))
        assertTrue(weekStrip.contains("selectableGroup()"))

        val onboarding = readOwned("ui/onboarding/OnboardingScreen.kt")
        assertTrue(onboarding.contains("Role.RadioButton"))
        assertTrue(onboarding.contains("RadioButton("))
        assertTrue(onboarding.contains("heading()"))

        val wheel = readOwned("ui/onboarding/BodyweightWheel.kt")
        assertTrue(wheel.contains("NumberEntryDialog("))
        assertTrue(wheel.contains("TalkBackPolicy.parseTypedBodyweightKg"))

        val settings = readOwned("ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("checked = preferences.soundEnabled"))
        assertTrue(settings.contains("onCheckedChange = onSound"))
        assertTrue(settings.contains("onCheckedChange = null"))

        val reminders = readOwned("ui/reminders/ReminderPrefsSection.kt")
        assertTrue(reminders.contains("checked = enabled"))
        assertTrue(reminders.contains("onCheckedChange = null"))

        val exerciseRow = readOwned("ui/components/ExercisePickerSheet.kt")
        assertTrue(exerciseRow.contains("Role.Button"))

        val home = readOwned("ui/home/HomeScreen.kt")
        assertTrue(home.contains("heading()"))

        assertFalse(AccessibilityMatrix.publicCandidateReady())
        assertTrue(AccessibilityMatrix.pages.none { it.physicalTalkBack })
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        val file = roots.map { File(it, relative) }.firstOrNull { it.isFile }
        assertNotNull(relative, file)
        return file!!.readText()
    }
}
