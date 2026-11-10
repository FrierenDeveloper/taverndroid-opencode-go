package cl.portaalerta

import cl.portaalerta.data.PhotoCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class CryptoTest {
    private fun key() = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Test
    fun roundTrip() {
        val k = key()
        val photo = ByteArray(200_000).also { SecureRandom().nextBytes(it) }
        assertArrayEquals(photo, PhotoCrypto.decrypt(k, PhotoCrypto.encrypt(k, photo)))
    }

    @Test
    fun ciphertextDoesNotContainPlaintext() {
        val k = key()
        val plain = "8956526018159083015-cliente-Valentina".repeat(50).toByteArray()
        val enc = PhotoCrypto.encrypt(k, plain)
        assertFalse(String(enc, Charsets.ISO_8859_1).contains("Valentina"))
        assertTrue(enc.size > plain.size)
    }

    @Test
    fun sameInputGivesDifferentOutput() {
        val k = key()
        val plain = ByteArray(64)
        assertFalse(PhotoCrypto.encrypt(k, plain).contentEquals(PhotoCrypto.encrypt(k, plain)))
    }

    @Test
    fun wrongKeyFails() {
        val enc = PhotoCrypto.encrypt(key(), ByteArray(100))
        assertTrue(runCatching { PhotoCrypto.decrypt(key(), enc) }.isFailure)
    }

    @Test
    fun tamperedFileFails() {
        val k = key()
        val enc = PhotoCrypto.encrypt(k, ByteArray(100) { it.toByte() })
        enc[enc.size / 2] = (enc[enc.size / 2].toInt() xor 1).toByte()
        assertTrue(runCatching { PhotoCrypto.decrypt(k, enc) }.isFailure)
    }
}
