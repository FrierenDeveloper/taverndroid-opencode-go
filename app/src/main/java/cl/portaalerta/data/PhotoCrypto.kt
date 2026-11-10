package cl.portaalerta.data

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado AES-256-GCM de las fotos. Formato del archivo: [IV de 12 bytes][texto cifrado + etiqueta de 16 bytes].
 * GCM además detecta si el archivo fue alterado: descifrar un archivo modificado falla.
 */
object PhotoCrypto {
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(key: ByteArray, blob: ByteArray): ByteArray {
        require(blob.size > IV_LEN + TAG_BITS / 8) { "Archivo cifrado inválido" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_LEN)),
        )
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }
}
