package com.sinura.personaltrainer.activity

import com.sinura.personaltrainer.data.repository.ActivityRepository
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.TimePort

class ConfirmActivity(
    private val repository: ActivityRepository,
    private val ids: IdPort,
    private val clock: TimePort,
) {
    suspend operator fun invoke(
        draft: ActivityDraft,
        now: CapturedCivilTime,
    ): ActivityWrite = repository.confirm(draft, now, ids, clock)
}

class StartLiveActivity(
    private val repository: ActivityRepository,
    private val ids: IdPort,
    private val clock: TimePort,
) {
    suspend operator fun invoke(
        title: String,
        blocks: List<ActivityBlock>,
        now: CapturedCivilTime,
        occurrenceId: String? = null,
    ): ActivityWrite {
        val draft = ActivityDraft(
            status = ActivityStatus.ACTIVE,
            origin = ActivityOrigin.LIVE,
            title = title,
            performedStart = now,
            occurrenceId = occurrenceId,
            blocks = blocks,
        )
        return repository.confirm(draft, now, ids, clock)
    }
}

open class DiscardActivity(
    private val repository: ActivityRepository,
) {
    open suspend operator fun invoke(sessionId: String) {
        repository.discard(sessionId)
    }
}

class FinishActivity(
    private val repository: ActivityRepository,
    private val clock: TimePort,
) {
    suspend operator fun invoke(
        sessionId: String,
        now: CapturedCivilTime,
        blocks: List<ActivityBlock>? = null,
    ): ActivityWrite = repository.completeLive(sessionId, now, clock, blocks)
}
