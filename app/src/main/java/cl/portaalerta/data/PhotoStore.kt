package cl.portaalerta.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.max

/**
 * Fotos de chips. Todas se guardan CIFRADAS (AES-256-GCM) en la carpeta privada de la app:
 * no pasan por la galería (Google Fotos nunca las ve), no van a ningún respaldo y, aunque alguien copie
 * los archivos, sin la clave del Keystore de este teléfono no se pueden abrir.
 * La única copia sin cifrar es la que escribe la cámara en la caché privada durante un instante,
 * y se borra apenas se cifra.
 */
object PhotoStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "chips").apply { mkdirs() }
    private fun tmpDir(ctx: Context) = File(dir(ctx), "tmp").apply { mkdirs() }
    private fun key(ctx: Context): ByteArray = SecureKeys.secret(ctx, "photo")

    /** Donde la cámara escribe la foto cruda (caché privada), antes de cifrarla. */
    fun newRawFile(ctx: Context): File = File(ctx.cacheDir, "raw_${System.nanoTime()}.jpg")

    private fun newTempFile(ctx: Context): File = File(tmpDir(ctx), "tmp_${System.nanoTime()}.enc")

    /** Cifra la foto cruda de la cámara y borra la original. */
    fun sealRaw(ctx: Context, raw: File): File? = try {
        val out = newTempFile(ctx)
        out.writeBytes(PhotoCrypto.encrypt(key(ctx), raw.readBytes()))
        out
    } catch (e: Exception) {
        null
    } finally {
        raw.delete()
    }

    /** Copia cifrada de una imagen elegida de la galería. La original no se toca ni se guarda sin cifrar. */
    fun copyToTemp(ctx: Context, uri: Uri): File? {
        return try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val out = newTempFile(ctx)
            out.writeBytes(PhotoCrypto.encrypt(key(ctx), bytes))
            out
        } catch (e: Exception) {
            null
        }
    }

    /** Guarda directamente una foto (JPEG) ya en su lugar definitivo, cifrada. Se usa al restaurar un respaldo. */
    fun saveFinal(ctx: Context, jpeg: ByteArray): String? = try {
        val dest = File(dir(ctx), "chip_${System.nanoTime()}.enc")
        dest.writeBytes(PhotoCrypto.encrypt(key(ctx), jpeg))
        dest.path
    } catch (e: Exception) {
        null
    }

    /** Mueve una foto temporal a su lugar definitivo y devuelve la ruta nueva. */
    fun promote(ctx: Context, tmp: File): String {
        val dest = File(dir(ctx), "chip_${System.currentTimeMillis()}.enc")
        return if (tmp.renameTo(dest)) dest.path else tmp.path
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    /** Borra temporales y fotos crudas abandonados (más de una hora). */
    fun cleanTmp(ctx: Context) {
        val limit = System.currentTimeMillis() - 60 * 60 * 1000
        tmpDir(ctx).listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
        ctx.cacheDir.listFiles { f -> f.name.startsWith("raw_") && f.lastModified() < limit }?.forEach { it.delete() }
    }

    /** Descifra y carga la imagen reducida, ya girada según su orientación EXIF. */
    fun loadBitmap(ctx: Context, path: String, maxPx: Int): Bitmap? {
        val bytes = try {
            PhotoCrypto.decrypt(key(ctx), File(path).readBytes())
        } catch (e: Exception) {
            return null
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val degrees = when (
            runCatching {
                ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
