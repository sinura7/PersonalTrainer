package com.sinura.personaltrainer.update

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
    fun install()

    /** Android's answer to the install session the banner started. */
    fun onInstallAnswer(answer: DebugInstallAnswer) {}
}

object DisabledDebugUpdate : DebugUpdatePort {
    override val ui: StateFlow<DebugUpdateUi> = MutableStateFlow(DebugUpdateUi())
    override fun onForeground() {}
    override fun onSettingsOpened() {}
    override fun dismissBanner() {}
    override fun install() {}
}

internal class DebugUpdateMonitor(
    private val checker: DebugUpdateChecker,
    private val cache: DebugUpdateCache,
    private val fetcher: DebugApkFetcher,
    private val installer: DebugApkInstaller,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) : DebugUpdatePort {
    private val mutex = Mutex()
    private val installMutex = Mutex()
    private val held = MutableStateFlow(DebugUpdateUi())
    override val ui: StateFlow<DebugUpdateUi> = held.asStateFlow()

    override fun onForeground() {
        scope.launch {
            refresh(FOREGROUND_TTL_MS)
            retryInstallIfPermissionGranted()
        }
    }

    override fun onSettingsOpened() {
        scope.launch {
            refresh(SETTINGS_TTL_MS)
            retryInstallIfPermissionGranted()
        }
    }

    override fun dismissBanner() {
        val offer = held.value.offer ?: return
        held.value = held.value.copy(showBanner = false)
        scope.launch {
            runCatchingCancellable { cache.dismiss(offer.versionCode) }
                .onFailure { error -> AppLog.w(TAG, "Dismissing the update banner failed", error) }
        }
    }

    override fun install() {
        scope.launch { runInstall() }
    }

    /**
     * Android's sheet is the app's to open ([DebugInstallAnswer.Confirm]); a cancelled sheet
     * leaves the update to tap again. A refusal says the update did not finish, and an install
     * that went through leaves no download behind.
     */
    override fun onInstallAnswer(answer: DebugInstallAnswer) {
        when (answer) {
            is DebugInstallAnswer.Confirm, DebugInstallAnswer.Cancelled -> Unit
            is DebugInstallAnswer.Failed -> {
                AppLog.w(TAG, "Android did not install the update (status ${answer.status}): ${answer.message}")
                publishInstall(DebugUpdateInstall.Failed)
            }
            DebugInstallAnswer.Installed -> scope.launch { clearStaging() }
        }
    }

    private suspend fun clearStaging() {
        withContext(ioDispatcher) {
            runCatchingCancellable { installer.clearStaging() }
                .onFailure { error -> AppLog.w(TAG, "Deleting the downloaded builds failed", error) }
        }
    }

    /** JVM tests call this so they do not race the fire-and-forget tap. */
    internal suspend fun installNow() = runInstall()

    internal suspend fun retryInstallIfPermissionGranted() {
        if (held.value.install != DebugUpdateInstall.NeedsPermission) return
        if (!installer.canInstall()) return
        runInstall()
    }

    private suspend fun runInstall() {
        if (!installMutex.tryLock()) return
        try {
            val offer = held.value.offer ?: return
            if (!installer.canInstall()) {
                publishInstall(DebugUpdateInstall.NeedsPermission)
                installer.openInstallPermissionSettings()
                return
            }
            publishInstall(DebugUpdateInstall.Downloading, percent = null)
            val dest = withContext(ioDispatcher) {
                runCatchingCancellable {
                    val file = installer.stagingFile(offer.versionCode)
                    fetcher.fetch(offer.apkUrl, file) { read, total ->
                        publishInstall(
                            DebugUpdateInstall.Downloading,
                            percent = DebugApkDownload.percent(read, total),
                        )
                    }
                    file
                }
            }.getOrElse { error ->
                AppLog.w(TAG, "Downloading the debug APK failed", error)
                publishInstall(DebugUpdateInstall.Failed)
                return
            }
            publishInstall(DebugUpdateInstall.Installing)
            runCatchingCancellable { installer.install(dest) }
                .onFailure { error ->
                    AppLog.w(TAG, "Handing the debug APK to the installer failed", error)
                    publishInstall(DebugUpdateInstall.Failed)
                    return
                }
            publishInstall(DebugUpdateInstall.Idle)
        } finally {
            installMutex.unlock()
        }
    }

    private fun publishInstall(install: DebugUpdateInstall, percent: Int? = null) {
        val current = held.value
        held.value = current.copy(
            install = install,
            downloadPercent = if (install == DebugUpdateInstall.Downloading) percent else null,
        )
    }

    internal suspend fun refresh(minIntervalMs: Long) {
        if (held.value.install.blocksRefresh) return
        mutex.withLock {
            if (held.value.install.blocksRefresh) return
            val offer = withContext(ioDispatcher) {
                runCatchingCancellable { checker.check(minIntervalMs) }.getOrElse { error ->
                    AppLog.w(TAG, "Debug update refresh failed", error)
                    null
                }
            }
            val dismissed = withContext(ioDispatcher) {
                runCatchingCancellable { cache.dismissedVersionCode() }.getOrDefault(0)
            }
            // No newer build: whatever was downloaded is installed, or will not be (audit RM-6).
            if (offer == null) clearStaging()
            val previous = held.value
            held.value = DebugUpdateUi(
                offer = offer,
                showBanner = offer != null && offer.versionCode > dismissed,
                install = if (offer == null) DebugUpdateInstall.Idle else previous.install,
                downloadPercent = null,
            )
        }
    }
}

private val DebugUpdateInstall.blocksRefresh: Boolean
    get() = this == DebugUpdateInstall.Downloading || this == DebugUpdateInstall.Installing
