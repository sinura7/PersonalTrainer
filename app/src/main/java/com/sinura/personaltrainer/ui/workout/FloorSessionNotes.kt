package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** A typing pause, not a keystroke, is what commits notes to the database. */
private const val NOTES_WRITE_DEBOUNCE_MS = 400L

/**
 * The workout floor's session notes ([ActiveWorkoutViewModel]): the words as typed, the one-time
 * fill from the session row, what the database already holds, and the two writes, one at a typing
 * pause and one at Back.
 *
 * A write waits until the row has been read, so the empty field a screen opens with never goes
 * over notes it has not seen, and it happens only when the words differ from what the database
 * holds. The row fills the field once, and only an empty one.
 *
 * What the notes are to the rest of the floor is the ViewModel's: every keystroke is saved with its
 * draft (the cache for this run, the saved state for when Android stops the app), the draft brings
 * them back when the screen is rebuilt, and Finish takes them to History. So this class takes the
 * session and the database write, owns no scope and launches nothing, like [FloorUndoOffers] and
 * [FloorSetSaves]: the ViewModel launches both writes in its own scope, where it always did, so its
 * `init` starts things in the same order.
 */
internal class FloorSessionNotes(
    private val sessionId: String,
    /** Writes the session row's notes. */
    private val write: suspend (String) -> Unit,
) {
    private val _text = MutableStateFlow("")

    /** The words as typed, restored, or filled once from the row. */
    val text: StateFlow<String> = _text.asStateFlow()

    /** What the database already holds, so a re-seed or a no-op edit does not re-write it. */
    private var lastPersisted: String? = null
    private var hydrated = false

    /** The words typed, or restored from the draft. Nothing is written until a pause or Back. */
    fun edit(value: String) {
        _text.value = value
    }

    /**
     * The session row was read, holding [stored]: what the database has now and, the first time
     * only, what fills an empty field. The ViewModel calls this on every row, before it saves its
     * draft, so the draft carries what the row filled in.
     */
    fun sessionRead(stored: String) {
        lastPersisted = stored
        // Hydrate once. "Field is empty" cannot tell not-yet-seeded from
        // deliberately-cleared, and re-seeding on a later emission restored
        // notes the user had just deleted mid-debounce.
        if (!hydrated) {
            hydrated = true
            if (_text.value.isEmpty() && stored.isNotEmpty()) {
                _text.value = stored
            }
        }
    }

    /**
     * Writes the words at every typing pause, for as long as the caller's scope lives.
     *
     * Notes used to launch an independent write per keystroke. Room's writes are not
     * ordered against each other, so a shorter earlier string could land after a longer
     * later one and the user's last characters would silently disappear on the next read
     * — while a paragraph of notes cost a database write per character.
     *
     * collectLatest cancels the pending delay on every keystroke, so only a typing pause
     * writes; and because it awaits the previous block's cancellation before starting the
     * next, the writes it does perform are strictly ordered. NonCancellable means a write
     * that has already begun finishes rather than being torn in half by the next keystroke.
     */
    suspend fun writeOnTypingPause() {
        _text.collectLatest { value ->
            delay(NOTES_WRITE_DEBOUNCE_MS)
            writeIfChanged(value)
        }
    }

    /** Writes the words now: leaving must not drop the words typed in the last 400 ms. */
    suspend fun writeNow() {
        writeIfChanged(_text.value)
    }

    private suspend fun writeIfChanged(value: String) {
        if (sessionId.isBlank()) return
        // Null means the session row has not been read yet, so what is on disk is unknown.
        // Writing here would push the empty initial value over real notes whenever the first
        // query took longer than the debounce — exactly the case on a cold start.
        val known = lastPersisted ?: return
        if (value == known) return
        withContext(NonCancellable) {
            runCatchingCancellable {
                write(value)
                lastPersisted = value
            }.onFailure { thrown ->
                AppLog.w(TAG, "Writing the session notes failed", thrown)
                // Notes stay in the draft cache, and the next keystroke retries.
            }
        }
    }

    private companion object {
        /** The ViewModel's, so the diagnostics read as they did before the move. */
        const val TAG = "PT/ActiveWorkoutVM"
    }
}
