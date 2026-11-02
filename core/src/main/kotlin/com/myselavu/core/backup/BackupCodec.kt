package com.myselavu.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Packs a backup (JSON text) into a small file:
 *  - always compressed (gzip), so 10 years of data stays a few MB;
 *  - with a password: encrypted with AES-256-GCM, key from PBKDF2-HMAC-SHA256 (random salt).
 *    Without the password nobody can read it, not even if it sits in Google Drive.
 *
 * File layout:
 *   plain:     "MYSELAVU-GZ1\n" + gzip(json)
 *   encrypted: "MYSELAVU-ENC1\n" + iterations(4 bytes) + salt(16) + iv(12) + AES-GCM(gzip(json))
 */
object BackupCodec {
    private val MAGIC_PLAIN = "MYSELAVU-GZ1\n".toByteArray(Charsets.US_ASCII)
    private val MAGIC_ENC = "MYSELAVU-ENC1\n".toByteArray(Charsets.US_ASCII)
    const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    class NotABackupException : Exception("This file is not a MySelavu backup")
    class PasswordNeededException : Exception("This backup is locked with a password")
    class WrongPasswordException : Exception("Wrong password, or the file is damaged")

    fun isEncrypted(bytes: ByteArray): Boolean = bytes.startsWith(MAGIC_ENC)
    fun isBackup(bytes: ByteArray): Boolean = bytes.startsWith(MAGIC_ENC) || bytes.startsWith(MAGIC_PLAIN)

    fun encode(json: String, password: CharArray?, random: SecureRandom = SecureRandom()): ByteArray {
        val compressed = gzip(json.toByteArray(Charsets.UTF_8))
        if (password == null || password.isEmpty()) return MAGIC_PLAIN + compressed
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC_ENC)
        val sealed = cipher.doFinal(compressed)
        return MAGIC_ENC + intBytes(ITERATIONS) + salt + iv + sealed
    }

    fun decode(bytes: ByteArray, password: CharArray?): String {
        return when {
            bytes.startsWith(MAGIC_PLAIN) -> gunzip(bytes.copyOfRange(MAGIC_PLAIN.size, bytes.size))
            bytes.startsWith(MAGIC_ENC) -> {
                if (password == null || password.isEmpty()) throw PasswordNeededException()
                var p = MAGIC_ENC.size
                if (bytes.size < p + 4 + SALT_BYTES + IV_BYTES + TAG_BITS / 8) throw WrongPasswordException()
                val iterations = readInt(bytes, p); p += 4
                if (iterations !in 10_000..10_000_000) throw NotABackupException()
                val salt = bytes.copyOfRange(p, p + SALT_BYTES); p += SALT_BYTES
                val iv = bytes.copyOfRange(p, p + IV_BYTES); p += IV_BYTES
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(MAGIC_ENC)
                val plain = try {
                    cipher.doFinal(bytes, p, bytes.size - p)
                } catch (e: AEADBadTagException) {
                    throw WrongPasswordException()
                }
                gunzip(plain)
            }
            else -> throw NotABackupException()
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): String = try {
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }.toString(Charsets.UTF_8)
    } catch (e: java.io.IOException) {
        throw NotABackupException()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun readInt(b: ByteArray, p: Int): Int =
        ((b[p].toInt() and 0xFF) shl 24) or ((b[p + 1].toInt() and 0xFF) shl 16) or
            ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
}

/** How much history to keep, and where to cut. */
object HistoryRetention {
    /** Options offered in Settings. 0 = keep forever. */
    val OPTIONS_YEARS = listOf(0, 3, 5, 10)

    /** History before this date may be cleared when keeping [years] years. */
    fun cutoff(today: java.time.LocalDate, years: Int): java.time.LocalDate {
        require(years >= 1)
        return today.minusYears(years.toLong()).withDayOfMonth(1)
    }

    /** Rough size of the data: about 160 bytes per expense including indexes. */
    fun estimatedBytes(expensesPerDay: Double, years: Int): Long = (expensesPerDay * 365.25 * years * 160).toLong()
}
