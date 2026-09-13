package com.sinura.personaltrainer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "PT/DebugUpdate"

/**
 * What Home and Settings read. Gym-floor wires [DisabledDebugUpdate].
 */
interface DebugUpdatePort {
    val ui: StateFlow<DebugUpdateUi>
    fun onForeground()
    fun onSettingsOpened()
    fun dismissBanner()
    fun openOffer(context: Context)
}

object DisabledDebugUpdate : DebugUpdatePort {
    override val ui: StateFlow<DebugUpdateUi> = MutableStateFlow(DebugUpdateUi())
    override fun onForeground() {}
    override fun onSettingsOpened() {}
    override fun dismissBanner() {}
    override fun openOffer(context: Context) {}
}

internal class DebugUpdateMonitor(
    private val checker: DebugUpdateChecker,
    private val cache: DebugUpdateCache,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : DebugUpdatePort {
    private val mutex = Mutex()
    private val held = MutableStateFlow(DebugUpdateUi())
    override val ui: StateFlow<DebugUpdateUi> = held.asStateFlow()

    override fun onForeground() {
        scope.launch { refresh(FOREGROUND_TTL_MS) }
    }

    override fun onSettingsOpened() {
        scope.launch { refresh(SETTINGS_TTL_MS) }
    }

    override fun dismissBanner() {
        val offer = held.value.offer ?: return
        held.value = held.value.copy(showBanner = false)
        scope.launch {
            runCatchingCancellable { cache.dismiss(offer.versionCode) }
                .onFailure { error -> AppLog.w(TAG, "Dismissing the update banner failed", error) }
        }
    }

    override fun openOffer(context: Context) {
        val url = held.value.offer?.releaseUrl ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatchingCancellable { context.startActivity(intent) }
            .onFailure { error -> AppLog.w(TAG, "Opening the GitHub drop failed", error) }
    }

    internal suspend fun refresh(minIntervalMs: Long) {
        mutex.withLock {
            val offer = withContext(ioDispatcher) {
                runCatchingCancellable { checker.check(minIntervalMs) }.getOrElse { error ->
                    AppLog.w(TAG, "Debug update refresh failed", error)
                    null
                }
            }
            val dismissed = withContext(ioDispatcher) {
                runCatchingCancellable { cache.dismissedVersionCode() }.getOrDefault(0)
            }
            held.value = DebugUpdateUi(
                offer = offer,
                showBanner = offer != null && offer.versionCode > dismissed,
            )
        }
    }
}
