package cl.portaalerta.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

/**
 * Fotos de chips: solo en la carpeta PRIVADA de la app (filesDir). No pasan por la galería del teléfono,
 * así que Google Fotos nunca las ve, y están excluidas del respaldo automático de Android (res/xml).
 */
object PhotoStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "chips").apply { mkdirs() }
    private fun tmpDir(ctx: Context) = File(dir(ctx), "tmp").apply { mkdirs() }

    fun newTempFile(ctx: Context): File = File(tmpDir(ctx), "tmp_${System.nanoTime()}.jpg")

    /** Copia una imagen elegida de la galería a la carpeta privada. La original no se toca. */
    fun copyToTemp(ctx: Context, uri: Uri): File? {
        return try {
            val out = newTempFile(ctx)
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> out.outputStream().use { dst -> src.copyTo(dst) } }
            out
        } catch (e: Exception) {
            null
        }
    }

    /** Mueve una foto temporal a su lugar definitivo y devuelve la ruta nueva. */
    fun promote(ctx: Context, tmp: File): String {
        val dest = File(dir(ctx), "chip_${System.currentTimeMillis()}.jpg")
        return if (tmp.renameTo(dest)) dest.path else tmp.path
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    /** Borra temporales abandonados (más de una hora). */
    fun cleanTmp(ctx: Context) {
        val limit = System.currentTimeMillis() - 60 * 60 * 1000
        tmpDir(ctx).listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    /** Carga la imagen reducida y ya girada según su orientación EXIF. */
    fun loadBitmap(path: String, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = when (
            runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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
