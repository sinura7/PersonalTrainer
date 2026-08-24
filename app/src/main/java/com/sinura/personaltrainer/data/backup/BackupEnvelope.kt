package com.sinura.personaltrainer.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned portable authenticated encrypted envelope around a backup JSON.
 *
 * Platform cryptography only: PBKDF2-HMAC-SHA256 and AES-256-GCM. Salt and
 * nonce are random per wrap. The key is derived from the password the owner
 * typed; it is never taken from Android Keystore, so the file opens on another
 * phone. Custom constructions are forbidden.
 *
 * The envelope is itself JSON so a file manager still sees a document. It
 * deliberately omits [BackupJson]'s `version` field: decoding this object as a
 * backup document must fail closed, not look like an empty v1 catalog.
 */
object BackupEnvelope {
    const val FORMAT = "temper-backup-envelope"
    const val ENVELOPE_VERSION = 1
    const val KDF = "PBKDF2WithHmacSHA256"
    const val CIPHER = "AES/GCM/NoPadding"
    const val DEFAULT_ITERATIONS = 210_000
    const val MIN_PASSWORD = 8
    const val KEY_BYTES = 32
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val TAG_BITS = 128

    const val NEED_PASSWORD = "This backup is protected. Enter the password to open it."
    const val WRONG_PASSWORD = "That password doesn't open this file."
    const val PASSWORD_TOO_SHORT = "Use at least 8 characters."
    const val PASSWORD_MISMATCH = "Those passwords don't match."
    const val UNKNOWN_METHOD = "This file uses a protection method this app doesn't know."

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val decoder = Base64.getDecoder()
    private val encoder = Base64.getEncoder().withoutPadding()

    fun looksLike(raw: String): Boolean {
        return try {
            val root = JsonParser.parseString(raw).asJsonObject
            root.get("format")?.asString == FORMAT
        } catch (_: Exception) {
            false
        }
    }

    /**
     * If [raw] is an envelope, unwrap it. Otherwise return it unchanged so
     * legacy plaintext files keep working.
     */
    fun open(raw: String, password: CharArray?): String {
        if (!looksLike(raw)) return raw
        val secret = password ?: throw BackupException(NEED_PASSWORD)
        return unwrap(raw, secret)
    }

    fun validateNewPassword(password: String, confirm: String): String? {
        if (password.length < MIN_PASSWORD) return PASSWORD_TOO_SHORT
        if (password != confirm) return PASSWORD_MISMATCH
        return null
    }

    fun wrap(
        plaintext: String,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): String {
        validateNewPassword(String(password), String(password))?.let { throw BackupException(it) }
        if (iterations < 1) throw BackupException(UNKNOWN_METHOD)
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val keyBytes = derive(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.updateAAD(aad(iterations))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val root = JsonObject()
            root.addProperty("format", FORMAT)
            root.addProperty("envelopeVersion", ENVELOPE_VERSION)
            root.addProperty("app", BackupJson.APP_ID)
            root.addProperty("kdf", KDF)
            root.addProperty("iterations", iterations)
            root.addProperty("keyBytes", KEY_BYTES)
            root.addProperty("cipher", CIPHER)
            root.addProperty("salt", encoder.encodeToString(salt))
            root.addProperty("nonce", encoder.encodeToString(nonce))
            root.addProperty("ciphertext", encoder.encodeToString(ciphertext))
            gson.toJson(root) + "\n"
        } finally {
            keyBytes.fill(0)
        }
    }

    fun unwrap(envelope: String, password: CharArray): String {
        val root = try {
            JsonParser.parseString(envelope).asJsonObject
        } catch (_: Exception) {
            throw BackupException(BackupJson.notABackupMessage())
        }
        if (root.get("format")?.asString != FORMAT) {
            throw BackupException(BackupJson.notABackupMessage())
        }
        val envelopeVersion = try {
            root.get("envelopeVersion")?.asInt
        } catch (_: Exception) {
            null
        } ?: throw BackupException(BackupJson.notABackupMessage())
        if (envelopeVersion > ENVELOPE_VERSION) {
            throw BackupException(
                "This backup was made with a newer app version and can’t be opened here.",
            )
        }
        if (envelopeVersion < 1) {
            throw BackupException(BackupJson.notABackupMessage())
        }
        val app = root.get("app")?.asString
        if (app != null && app != BackupJson.APP_ID) {
            throw BackupException(BackupJson.notABackupMessage())
        }
        val kdf = root.get("kdf")?.asString
        val cipherName = root.get("cipher")?.asString
        val keyBytesField = try {
            root.get("keyBytes")?.asInt
        } catch (_: Exception) {
            null
        }
        if (kdf != KDF || cipherName != CIPHER || keyBytesField != KEY_BYTES) {
            throw BackupException(UNKNOWN_METHOD)
        }
        val iterations = try {
            root.get("iterations")?.asInt
        } catch (_: Exception) {
            null
        } ?: throw BackupException(UNKNOWN_METHOD)
        if (iterations < 1) throw BackupException(UNKNOWN_METHOD)
        val salt = decodeB64(root, "salt")
        val nonce = decodeB64(root, "nonce")
        val ciphertext = decodeB64(root, "ciphertext")
        if (salt.size < SALT_BYTES || nonce.size != NONCE_BYTES || ciphertext.isEmpty()) {
            throw BackupException(WRONG_PASSWORD)
        }
        val keyBytes = derive(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(keyBytes, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            cipher.updateAAD(aad(iterations))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw BackupException(WRONG_PASSWORD)
        } catch (thrown: BackupException) {
            throw thrown
        } catch (_: Exception) {
            throw BackupException(WRONG_PASSWORD)
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun aad(iterations: Int): ByteArray =
        "$FORMAT|$ENVELOPE_VERSION|${BackupJson.APP_ID}|$KDF|$iterations".toByteArray(Charsets.UTF_8)

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeB64(root: JsonObject, key: String): ByteArray {
        val raw = root.get(key)?.asString ?: throw BackupException(WRONG_PASSWORD)
        return try {
            decoder.decode(raw)
        } catch (_: Exception) {
            throw BackupException(WRONG_PASSWORD)
        }
    }
}
