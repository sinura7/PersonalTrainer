package com.sinura.personaltrainer.testutil

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.security.MessageDigest

/** Unique persistent artifacts survive APK cleanup; a failed copy fails the test. */
object NativeArtifacts {
    val runId: String = InstrumentationRegistry.getArguments().getString("artifactRunId")
        ?: UUID.randomUUID().toString()

    fun write(name: String, bitmap: Bitmap): String {
        require(runId.matches(Regex("[A-Za-z0-9-]+")))
        require(name.matches(Regex("[A-Za-z0-9-]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val file = File(root, "$runId-$name.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        val persistent = "/sdcard/Download/${file.name}"
        fun command(command: String): String = instrumentation.uiAutomation
            .executeShellCommand(command)
            .use { pipe -> FileInputStream(pipe.fileDescriptor).use { it.readBytes().toString(Charsets.UTF_8) } }
        // UiAutomation uses Runtime.exec, not a shell: operators such as &&
        // are literal arguments. Verify the unique destination independently.
        command("cp ${file.absolutePath} $persistent")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count >= 0) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }
        val expectedHash = digest.digest().joinToString("") { "%02x".format(it) }
        val actualHash = command("sha256sum $persistent").substringBefore(' ').trim()
        check(actualHash == expectedHash) { "Artifact copy failed hash verification: $persistent" }
        return persistent
    }
}
