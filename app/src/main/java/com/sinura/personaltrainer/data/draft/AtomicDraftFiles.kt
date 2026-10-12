package com.sinura.personaltrainer.data.draft

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption

internal const val MAX_DRAFT_BYTES = 8 * 1024 * 1024

internal interface DraftFileIO {
    fun read(file: File): ByteArray?
    fun replace(file: File, bytes: ByteArray)
}

/** API26+; failed atomic replacement preserves the previous checkpoint. No overwrite fallback. */
internal object AtomicDraftFiles : DraftFileIO {
    override fun read(file: File): ByteArray? = try {
        Files.newInputStream(file.toPath()).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_DRAFT_BYTES) throw DraftTooLarge()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } catch (_: NoSuchFileException) {
        null
    }

    override fun replace(file: File, bytes: ByteArray) {
        if (bytes.size > MAX_DRAFT_BYTES) throw DraftTooLarge()
        val parent = file.parentFile ?: throw IOException()
        Files.createDirectories(parent.toPath())
        val temporary = File(parent, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // A killed process may leave this private orphan; the next replacement overwrites it.
            Files.deleteIfExists(temporary.toPath())
        }
    }
}

internal class DraftTooLarge : IOException()
