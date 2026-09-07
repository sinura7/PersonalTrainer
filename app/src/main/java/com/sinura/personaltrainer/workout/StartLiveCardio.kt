package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.activity.StartLiveActivity
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioCopy
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.timer.CardioElapsed
import com.sinura.personaltrainer.timer.CardioTimerPersistence
import com.sinura.personaltrainer.timer.PersistedCardioTimer
import com.sinura.personaltrainer.util.IdFactory

sealed interface StartCardioOutcome {
    data class Open(val sessionId: String) : StartCardioOutcome
    data class Rejected(val reason: String) : StartCardioOutcome
}

/**
 * The live-cardio start: one empty block, persist the elapsed-clock baseline.
 *
 * Home, the start sheet, and Plan each used to copy this. The copies drifted —
 * a scheduled ride started as a run from the sheet — so the ritual lives here.
 */
class StartLiveCardio(
    private val startLiveActivity: StartLiveActivity,
    private val cardioTimerPersistence: CardioTimerPersistence,
    private val ids: IdPort = IdFactory.Uuid,
) {
    suspend operator fun invoke(
        type: CardioType,
        now: CapturedCivilTime,
        occurrenceId: String? = null,
        title: String = CardioCopy.name(type),
    ): StartCardioOutcome {
        val block = CardioBlock(
            id = ids.newId(),
            sortOrder = 0,
            type = type,
            indoor = false,
            elapsedSeconds = 0L,
            movingSeconds = 0L,
            distanceMeters = null,
            elevationMeters = null,
            heartRateBpm = null,
            energyKj = null,
            rpe = null,
            routeRef = null,
        )
        return when (val write = startLiveActivity(title, listOf(block), now, occurrenceId)) {
            is ActivityWrite.Accepted -> {
                val nowElapsed = android.os.SystemClock.elapsedRealtime()
                val nowWall = System.currentTimeMillis()
                val saved = cardioTimerPersistence.save(
                    PersistedCardioTimer(
                        sessionId = write.session.id,
                        startedAtElapsedRealtime = nowElapsed,
                        startedAtWallClockMillis = nowWall,
                        bootMarker = CardioElapsed.bootMarker(nowWall, nowElapsed),
                    ),
                )
                if (!saved) {
                    // The live screen re-saves on open; until then its clock
                    // counts from the session's own start.
                    AppLog.w(TAG, "Cardio timer row did not commit at start")
                }
                StartCardioOutcome.Open(write.session.id)
            }
            is ActivityWrite.Rejected -> StartCardioOutcome.Rejected(write.reason)
        }
    }

    private companion object {
        const val TAG = "PT/StartLiveCardio"
    }
}
