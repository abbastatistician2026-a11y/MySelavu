package com.myselavu.core

import com.myselavu.core.backup.BackupCodec
import com.myselavu.core.backup.HistoryRetention
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val json = """{"app":"MySelavu","expense":[{"id":1,"note":"Tea ☕ உணவு","amountPaise":1500}]}"""

    @Test fun plainRoundTripIsCompressed() {
        val big = json.repeat(2000)
        val bytes = BackupCodec.encode(big, null)
        assertTrue(BackupCodec.isBackup(bytes))
        assertFalse(BackupCodec.isEncrypted(bytes))
        assertTrue("gzip should shrink repetitive data", bytes.size < big.length / 10)
        assertEquals(big, BackupCodec.decode(bytes, null))
    }

    @Test fun encryptedRoundTrip() {
        val bytes = BackupCodec.encode(json, "my secret 123".toCharArray())
        assertTrue(BackupCodec.isEncrypted(bytes))
        // The text must not be readable inside the file
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("MySelavu\""))
        assertEquals(json, BackupCodec.decode(bytes, "my secret 123".toCharArray()))
    }

    @Test fun sameDataEncryptsDifferentlyEachTime() {
        val a = BackupCodec.encode(json, "pw".toCharArray())
        val b = BackupCodec.encode(json, "pw".toCharArray())
        assertNotEquals(a.toList(), b.toList())
    }

    @Test(expected = BackupCodec.WrongPasswordException::class)
    fun wrongPasswordFails() {
        BackupCodec.decode(BackupCodec.encode(json, "right".toCharArray()), "wrong".toCharArray())
    }

    @Test(expected = BackupCodec.PasswordNeededException::class)
    fun missingPasswordFails() {
        BackupCodec.decode(BackupCodec.encode(json, "right".toCharArray()), null)
    }

    @Test(expected = BackupCodec.WrongPasswordException::class)
    fun tamperedFileFails() {
        val bytes = BackupCodec.encode(json, "pw".toCharArray())
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        BackupCodec.decode(bytes, "pw".toCharArray())
    }

    @Test(expected = BackupCodec.NotABackupException::class)
    fun randomFileIsRejected() {
        BackupCodec.decode("hello world".toByteArray(), null)
    }

    @Test fun retentionCutoff() {
        assertEquals(LocalDate.of(2021, 11, 1), HistoryRetention.cutoff(LocalDate.of(2026, 11, 18), 5))
        assertEquals(LocalDate.of(2024, 2, 1), HistoryRetention.cutoff(LocalDate.of(2028, 2, 29), 4))
        // 10 expenses a day for 10 years ≈ 5.8 MB
        val tenYears = HistoryRetention.estimatedBytes(10.0, 10)
        assertTrue(tenYears in 5_000_000L..7_000_000L)
    }
}
