package com.sinura.personaltrainer.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sinura.personaltrainer.data.repository.prefs.PENDING_OCCURRENCE_ID
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow

/**
 * The settings store with one refusal: while [refuse] is set, a write that changes the link
 * between a live session and its planned day throws, as a full disk would. Every other read
 * and write is the real store's, so the finish, discard or start around it runs for real.
 */
class RefusingPlanLinkStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    @Volatile var refuse = false
    val refusals = AtomicInteger()

    override val data: Flow<Preferences> = delegate.data

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        delegate.updateData { current ->
            val next = transform(current)
            if (refuse && next[PENDING_OCCURRENCE_ID] != current[PENDING_OCCURRENCE_ID]) {
                refusals.incrementAndGet()
                throw IOException("boom: the settings file refused the planned-session link")
            }
            next
        }
}
