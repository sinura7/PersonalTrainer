package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.data.draft.DraftForm
import com.sinura.personaltrainer.data.draft.DraftOwner
import com.sinura.personaltrainer.data.draft.wellFormedDraftText
import java.security.MessageDigest

internal fun workoutPickerOwner(sessionId: String, itemId: String?): DraftOwner {
    (listOf(sessionId) + listOfNotNull(itemId)).forEach {
        require(it.isNotBlank() && it.length <= 256 && wellFormedDraftText(it))
    }
    val tuple = "${sessionId.length}:$sessionId" + if (itemId == null) ":add" else ":swap:${itemId.length}:$itemId"
    val identity = MessageDigest.getInstance("SHA-256").digest(tuple.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return DraftOwner(DraftForm.WORKOUT, "session-picker:$identity")
}
