package cl.portaalerta

import cl.portaalerta.data.BackupBundle
import cl.portaalerta.data.BackupCodec
import cl.portaalerta.data.BackupCrypto
import cl.portaalerta.data.ChipScan
import cl.portaalerta.data.Client
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupOutcome
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.FollowupType
import cl.portaalerta.data.InvalidBackupException
import cl.portaalerta.data.WrongPasswordException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val pw = "una frase larga y segura 2026".toCharArray()
    private val fast = 1_000 // iteraciones bajas solo para que las pruebas sean rápidas

    @Test
    fun sealAndOpenRoundTrip() {
        val data = ByteArray(50_000) { (it % 251).toByte() }
        assertArrayEquals(data, BackupCrypto.open(pw, BackupCrypto.seal(pw, data, fast)))
    }

    @Test
    fun defaultIterationsRoundTrip() {
        val data = "hola".toByteArray()
        assertArrayEquals(data, BackupCrypto.open(pw, BackupCrypto.seal(pw, data)))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val file = BackupCrypto.seal(pw, "secreto".toByteArray(), fast)
        val e = runCatching { BackupCrypto.open("otra contraseña".toCharArray(), file) }.exceptionOrNull()
        assertTrue(e is WrongPasswordException)
    }

    @Test
    fun anyChangeToTheFileIsDetected() {
        val file = BackupCrypto.seal(pw, ByteArray(500) { it.toByte() }, fast)
        for (pos in listOf(10, 20, 40, file.size - 1)) {
            val copy = file.copyOf()
            copy[pos] = (copy[pos].toInt() xor 1).toByte()
            assertTrue("posición $pos", runCatching { BackupCrypto.open(pw, copy) }.isFailure)
        }
    }

    @Test
    fun notABackupFile() {
        val e = runCatching { BackupCrypto.open(pw, ByteArray(200) { 7 }) }.exceptionOrNull()
        assertTrue(e is InvalidBackupException)
    }

    @Test
    fun fileDoesNotRevealContent() {
        val file = BackupCrypto.seal(pw, "Valentina Cruz +56 9 8123 4567".repeat(20).toByteArray(), fast)
        assertFalse(String(file, Charsets.ISO_8859_1).contains("Valentina"))
    }

    @Test
    fun codecRoundTrip() {
        val bundle = BackupBundle(
            clients = listOf(Client(id = 3, name = "Valentina Cruz", phone = "+56 9 8123 4567", currentCarrier = "Claro", notes = "tarde", createdAt = 1L)),
            followups = listOf(
                Followup(id = 9, clientId = 3, type = FollowupType.PORTABILITY, baseDate = 20000, months = 4, dueDate = 20120, leadDays = 7, detail = "x"),
                Followup(
                    id = 10, clientId = 3, type = FollowupType.INSURANCE, baseDate = 1, months = 1, dueDate = 31, leadDays = 3,
                    status = FollowupStatus.DONE, outcome = FollowupOutcome.REJECTED, doneAt = 123456L,
                ),
            ),
            scans = listOf(
                ChipScan(id = 5, iccid = "8956526018159083015", clientId = 3, note = "n", createdAt = 99L),
                ChipScan(id = 6, iccid = "89566613186091390997", createdAt = 100L),
            ),
            photos = mapOf(5L to byteArrayOf(1, 2, 3, 4)),
        )
        val back = BackupCodec.decode(BackupCodec.encode(bundle))
        assertEquals(bundle.clients, back.clients)
        assertEquals(bundle.followups, back.followups)
        assertEquals(bundle.scans, back.scans)
        assertNull(back.followups[0].outcome)
        assertNull(back.scans[1].clientId)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), back.photos[5L])
        assertFalse(back.photos.containsKey(6L))
    }

    @Test
    fun fullChainFromBundleToEncryptedFileAndBack() {
        val bundle = BackupBundle(listOf(Client(id = 1, name = "Ana", phone = "+56 9 1111 2222", createdAt = 5L)), emptyList(), emptyList(), emptyMap())
        val file = BackupCrypto.seal(pw, BackupCodec.encode(bundle), fast)
        assertEquals(bundle.clients, BackupCodec.decode(BackupCrypto.open(pw, file)).clients)
    }
}
