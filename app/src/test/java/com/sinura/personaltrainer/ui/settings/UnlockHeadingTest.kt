package com.sinura.personaltrainer.ui.settings

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.backup.BackupEnvelope
import com.sinura.personaltrainer.data.backup.BackupStageCopy
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UnlockHeadingTest {
    @Test
    fun aWrongPasswordClosesTheUnlockDialogSoTheHeadingCanShow() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val deps = FakeAppDependencies(context = app)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val backup = BackupCoordinator(container = deps, scope = scope, application = app)
            val file = File(app.cacheDir, "locked-backup.json")
            file.writeText(
                BackupEnvelope.wrap(
                    plaintext = "{}",
                    password = "correct-password".toCharArray(),
                    iterations = 1,
                ),
            )
            backup.requestFileRestore(Uri.fromFile(file))
            withTimeout(timeMillis = 20_000) { backup.uiState.first { it.pendingUnlock } }
            backup.unlockPending("wrong-password")
            val shown = withTimeout(timeMillis = 20_000) {
                backup.uiState.first { !it.pendingUnlock && it.error != null }
            }
            assertFalse(shown.pendingUnlock)
            assertEquals(BackupEnvelope.WRONG_PASSWORD, shown.error)
            assertEquals(BackupStageCopy.WRONG_PASSWORD, BackupStageCopy.titleFor(checkNotNull(shown.error)))
        } finally {
            scope.cancel()
            deps.close()
        }
    }
}
