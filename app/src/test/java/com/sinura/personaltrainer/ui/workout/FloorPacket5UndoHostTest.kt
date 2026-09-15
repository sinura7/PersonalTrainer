package com.sinura.personaltrainer.ui.workout

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Packet 5: one undo host (~6s) for cheap destructives. Finish, discard,
 * and leave a live workout still ask first.
 */
class FloorPacket5UndoHostTest {
    @Test
    fun cheapDestructivesUseTheNamedUndoHost() {
        assertTrue(com.sinura.personaltrainer.domain.FloorCompactChrome.cheapDestructivesAreUndoable())
        val host = readOwned("ui/components/GymStatus.kt")
        assertTrue(host.contains("fun GymUndoHost"))
        assertTrue(host.contains("UndoHostCopy.ACTION"))
        assertTrue(host.contains("Motion.STATUS_DWELL_MS"))
        val copy = readOwned("domain/UndoHostCopy.kt")
        assertTrue(copy.contains("object UndoHostCopy"))
        assertTrue(copy.contains("fun setDeleted"))
        assertTrue(copy.contains("fun liftRemoved"))
        assertTrue(copy.contains("fun daySkipped"))
        val floor = readOwned("ui/workout/ActiveWorkoutScreen.kt")
        assertFalse(floor.contains("confirmRemoveLift"))
        assertTrue(floor.contains("GymUndoHost("))
        assertTrue(floor.contains("onRemove = viewModel::removeSelectedLift"))
        assertTrue(floor.contains("EndWorkoutDialog("))
        assertTrue(floor.contains("confirmDiscard"))
        val detail = readOwned("ui/history/SessionDetailScreen.kt")
        assertTrue(detail.contains("GymUndoHost("))
        val home = readOwned("ui/home/HomeScreen.kt")
        assertTrue(home.contains("GymUndoHost("))
        assertTrue(home.contains("onSkipOccurrence = viewModel::skipOccurrence"))
        val liveBar = readOwned("ui/navigation/LiveSessionBar.kt")
        assertTrue(liveBar.contains("ConfirmActionDialog("))
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
