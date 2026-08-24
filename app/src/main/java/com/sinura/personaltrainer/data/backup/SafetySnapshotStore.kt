package com.sinura.personaltrainer.data.backup

import java.io.File
import java.io.FileOutputStream

/**
 * App-private safety copies. Write is atomic (tmp → fsync → rename → re-read).
 *
 * Failure deletes the partial file and throws. Callers abort restore.
 * [list] / [readJson] / [delete] accept a filename id only — never a path.
 */
class SafetySnapshotStore(
    private val dir: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun writeVerified(json: String, expected: AuthoredInventory): SafetySnapshotMeta {
        ensureWritableDir()
        val createdAt = uniqueCreatedAt()
        val id = SafetySnapshot.fileName(createdAt)
        val target = resolve(id)
        val tmp = File(dir, "$id.tmp")
        try {
            FileOutputStream(tmp).use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                throw BackupException(SafetySnapshot.WRITE_FAILED)
            }
            val onDisk = target.readText(Charsets.UTF_8)
            SafetySnapshot.verify(onDisk, expected)
            prune()
            return SafetySnapshotMeta(
                id = id,
                createdAtMillis = createdAt,
                authored = expected,
            )
        } catch (thrown: BackupException) {
            tmp.delete()
            target.delete()
            throw thrown
        } catch (_: Exception) {
            tmp.delete()
            target.delete()
            throw BackupException(SafetySnapshot.WRITE_FAILED)
        }
    }

    fun list(): List<SafetySnapshotMeta> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.mapNotNull { file -> readMetaOrNull(file) }
            ?.sortedByDescending { it.createdAtMillis }
            .orEmpty()
    }

    fun readJson(id: String): String {
        val file = resolve(id)
        if (!file.isFile) throw BackupException(SafetySnapshot.NOT_FOUND)
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            throw BackupException(SafetySnapshot.NOT_FOUND)
        }
    }

    fun delete(id: String) {
        val file = resolve(id)
        if (!file.isFile) throw BackupException(SafetySnapshot.NOT_FOUND)
        if (!file.delete()) throw BackupException(SafetySnapshot.DELETE_FAILED)
    }

    private fun readMetaOrNull(file: File): SafetySnapshotMeta? {
        if (!file.isFile || !SafetySnapshot.isSafeId(file.name)) return null
        val createdAt = SafetySnapshot.createdAtFromId(file.name) ?: return null
        val authored = try {
            AuthoredInventory.fromDocument(BackupJson.decode(file.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            return null
        }
        return SafetySnapshotMeta(
            id = file.name,
            createdAtMillis = createdAt,
            authored = authored,
        )
    }

    private fun uniqueCreatedAt(): Long {
        var createdAt = clock()
        var guard = 0
        while (File(dir, SafetySnapshot.fileName(createdAt)).exists()) {
            createdAt += 1
            guard += 1
            if (guard > 1_000) throw BackupException(SafetySnapshot.WRITE_FAILED)
        }
        return createdAt
    }

    private fun ensureWritableDir() {
        if (!dir.exists() && !dir.mkdirs()) {
            throw BackupException(SafetySnapshot.MISSING_DIR)
        }
        if (!dir.isDirectory || !dir.canWrite()) {
            throw BackupException(SafetySnapshot.MISSING_DIR)
        }
    }

    private fun resolve(id: String): File {
        if (!SafetySnapshot.isSafeId(id)) throw BackupException(SafetySnapshot.BAD_ID)
        val file = File(dir, id)
        if (file.name != id) throw BackupException(SafetySnapshot.BAD_ID)
        val parent = dir.canonicalFile
        val resolved = file.canonicalFile
        val prefix = parent.path + File.separator
        if (resolved != File(parent, id).canonicalFile || !resolved.path.startsWith(prefix)) {
            throw BackupException(SafetySnapshot.BAD_ID)
        }
        return file
    }

    private fun prune() {
        dir.listFiles()
            ?.filter { it.isFile && SafetySnapshot.isSafeId(it.name) }
            ?.sortedByDescending { SafetySnapshot.createdAtFromId(it.name) ?: 0L }
            ?.drop(SafetySnapshot.KEEP)
            ?.forEach { it.delete() }
    }
}
