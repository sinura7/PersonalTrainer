package com.sinura.personaltrainer.data.draft

import android.content.Context
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android noBackup filesystem and atomic replacement; no training records or phone data. */
@RunWith(AndroidJUnit4::class)
class DurableDraftPlatformTest {
    @Test fun reopeningReplacementClearAndGenerationUsePrivateStorageOffMain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "com.sinura.personaltrainer.debug")
        check(Build.HARDWARE.lowercase().let { "ranchu" in it || "goldfish" in it })
        val name = "draft-platform-fixture-${UUID.randomUUID()}"
        val root = File(context.noBackupFilesDir, name)
        val owner = DraftOwner(DraftForm.CUSTOM_EXERCISE, "fixture-new")
        val codec = object : DraftCodec<String, String> {
            override val format = "platform-fixture"
            override val version = 1
            override fun encodeDraft(value: String): JsonElement = JsonPrimitive(value)
            override fun decodeDraft(value: JsonElement): String = decode(value)
            override fun encodeCommand(value: String): JsonElement = JsonPrimitive(value)
            override fun decodeCommand(value: JsonElement): String = decode(value)
            private fun decode(value: JsonElement): String {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                return value.asString
            }
        }
        val io = object : DraftFileIO {
            override fun read(file: File): ByteArray? {
                assertFalse(Looper.myLooper() == Looper.getMainLooper())
                return AtomicDraftFiles.read(file)
            }
            override fun replace(file: File, bytes: ByteArray) {
                assertFalse(Looper.myLooper() == Looper.getMainLooper())
                AtomicDraftFiles.replace(file, bytes)
            }
        }
        try {
            withContext(Dispatchers.Main) {
                val store = DurableDraftStore(directory = root, files = io)
                val form = store.bind(owner, codec)
                val empty = (form.read() as DraftRead.Available).checkpoint
                val first = (form.checkpoint(empty.version, "62,5 lb · raw authored fields") as DraftChange.Written).checkpoint
                val pending = (form.beginSave(first.version, "62,5 lb · raw authored fields", "command", "stable-id") as DraftChange.Written).checkpoint
                val reopened = DurableDraftStore(root).bind(owner, codec)
                val restored = (reopened.read() as DraftRead.Available).checkpoint
                assertEquals(pending, restored)
                val accepted = (form.inspect(restored.version, "command", DraftRecoveryOutcome.EXACT_SAVED) as DraftChange.Written).checkpoint
                val cleared = (form.clear(accepted.version) as DraftChange.Written).checkpoint
                assertNull(cleared.draft)
                assertEquals(DraftChange.Rejected(DraftRejection.STALE_VERSION), form.checkpoint(first.version, "queued old input"))
                val next = store.advanceGeneration(cleared.currentGeneration) as DraftGenerationChange.Changed
                val stale = (reopened.read() as DraftRead.Available).checkpoint
                assertTrue(stale.needsReconciliation)
                val reconciled = (form.reconcileOwner(stale.version, next.generation) as DraftChange.Written).checkpoint
                assertFalse(reconciled.needsReconciliation)
            }
            assertTrue(root.canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile)
            assertTrue(root.listFiles()!!.none { "fixture-new" in it.name || it.name.endsWith(".tmp") })
        } finally {
            check(root.canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile && root.name == name)
            check(root.deleteRecursively())
        }
    }
}
