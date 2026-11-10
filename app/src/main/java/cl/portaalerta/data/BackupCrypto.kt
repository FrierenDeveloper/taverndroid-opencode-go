package cl.portaalerta.data

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Contraseña incorrecta o archivo dañado")
class InvalidBackupException(message: String) : Exception(message)

/**
 * Respaldo cifrado con una contraseña elegida por la persona.
 *
 * Archivo: "PABK" | versión (1) | iteraciones (4) | sal (16) | IV (12) | AES-256-GCM(datos).
 * La clave sale de la contraseña con PBKDF2-HMAC-SHA256 (600.000 iteraciones) y una sal aleatoria.
 * El encabezado va autenticado: cualquier cambio en el archivo hace fallar la apertura.
 * Sin la contraseña no hay forma de recuperar el contenido.
 */
object BackupCrypto {
    private val MAGIC = "PABK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val HEADER_LEN = 4 + 1 + 4 + SALT_LEN + IV_LEN
    const val DEFAULT_ITERATIONS = 600_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 5_000_000

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(password: CharArray, data: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_LEN)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).array()
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            return header + cipher.doFinal(data)
        } finally {
            key.fill(0)
        }
    }

    fun open(password: CharArray, file: ByteArray): ByteArray {
        if (file.size < HEADER_LEN + 16 || !file.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw InvalidBackupException("Este archivo no es un respaldo de PortaAlerta")
        }
        val buf = ByteBuffer.wrap(file, 0, HEADER_LEN)
        buf.position(4)
        if (buf.get() != VERSION) throw InvalidBackupException("Versión de respaldo no compatible")
        val iterations = buf.getInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw InvalidBackupException("Respaldo dañado")
        val salt = ByteArray(SALT_LEN).also { buf.get(it) }
        val iv = ByteArray(IV_LEN).also { buf.get(it) }
        val header = file.copyOfRange(0, HEADER_LEN)
        val key = deriveKey(password, salt, iterations)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(header)
            return cipher.doFinal(file, HEADER_LEN, file.size - HEADER_LEN)
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        } catch (e: GeneralSecurityException) {
            throw WrongPasswordException()
        } finally {
            key.fill(0)
        }
    }
}
