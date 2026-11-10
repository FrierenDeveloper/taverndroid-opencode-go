package cl.portaalerta.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Claves de la app. Hay una llave maestra dentro del Android Keystore (no se puede extraer del teléfono)
 * que protege dos secretos aleatorios: la clave de la base de datos y la clave de las fotos.
 * Los secretos se guardan cifrados con esa llave maestra; sin este teléfono y esta app no se pueden leer.
 */
object SecureKeys {
    private const val ALIAS = "portaalerta_master"
    private const val PREFS = "secure"
    private const val IV_LEN = 12

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, blob.copyOfRange(0, IV_LEN)))
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    /**
     * Devuelve el secreto [name] de 32 bytes. Si no existe se crea. Si existe pero no se puede descifrar
     * (por ejemplo, el teléfono perdió la llave maestra), se crea uno nuevo y se llama a [onRegenerated]:
     * lo cifrado con el secreto anterior ya no es recuperable.
     */
    @Synchronized
    fun secret(ctx: Context, name: String, onRegenerated: () -> Unit = {}): ByteArray {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(name, null)
        if (stored != null) {
            try {
                return unwrap(Base64.decode(stored, Base64.NO_WRAP))
            } catch (e: Exception) {
                onRegenerated()
            }
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(name, Base64.encodeToString(wrap(fresh), Base64.NO_WRAP)).apply()
        return fresh
    }
}
