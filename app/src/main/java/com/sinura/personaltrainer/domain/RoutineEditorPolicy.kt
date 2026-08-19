package com.sinura.personaltrainer.domain

object RoutineEditorPolicy {
    const val NEW_ID = "new"

    fun incomingId(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it != NEW_ID }

    fun shouldDiscardStub(createdThisSession: Boolean, exerciseCount: Int): Boolean =
        createdThisSession && exerciseCount <= 0
}