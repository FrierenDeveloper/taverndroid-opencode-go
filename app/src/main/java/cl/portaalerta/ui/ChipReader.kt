package cl.portaalerta.ui

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import cl.portaalerta.data.Iccid
import cl.portaalerta.data.PhotoStore
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/**
 * Lectura en vivo: cada cuadro se prueba primero con el lector de códigos de barras (rápido y fiable)
 * y, si no hay resultado, con el reconocimiento de texto (cada ~0,6 s). El número impreso debe leerse
 * igual en dos cuadros seguidos para aceptarse; el código de barras vale con una sola lectura válida.
 */
class ChipAnalyzer(private val onRead: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val barcodes = BarcodeScanning.getClient()
    private val texts = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastTextAt = 0L
    private var lastText: String? = null
    private var textHits = 0

    @Volatile
    private var closed = false

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || closed) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        var found: String? = null
        barcodes.process(image)
            .addOnSuccessListener { list ->
                found = list.firstNotNullOfOrNull { b -> b.rawValue?.let { Iccid.fromCode(it) } }
            }
            .addOnCompleteListener {
                val hit = found
                val now = SystemClock.elapsedRealtime()
                if (hit != null) {
                    onRead(hit)
                    proxy.close()
                } else if (!closed && now - lastTextAt >= 600) {
                    lastTextAt = now
                    texts.process(image)
                        .addOnSuccessListener { t ->
                            val v = Iccid.findIn(t.text)
                            if (v == null) {
                                lastText = null
                                textHits = 0
                            } else {
                                if (v == lastText) textHits++ else {
                                    lastText = v
                                    textHits = 1
                                }
                                if (textHits >= 2) onRead(v)
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                } else {
                    proxy.close()
                }
            }
    }

    fun close() {
        closed = true
        runCatching { barcodes.close() }
        runCatching { texts.close() }
    }
}

/** Lee una foto ya hecha (por ejemplo, una de la galería): código de barras y, si no, texto impreso. */
object ChipReader {
    suspend fun readFile(file: File): String? = withContext(Dispatchers.Default) {
        val bitmap = PhotoStore.loadBitmap(file.path, 2400) ?: return@withContext null
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val fromBarcode = runCatching { scanner.process(image).awaitResult() }.getOrNull()
                ?.firstNotNullOfOrNull { b -> b.rawValue?.let { Iccid.fromCode(it) } }
            fromBarcode ?: runCatching { recognizer.process(image).awaitResult() }.getOrNull()
                ?.let { Iccid.findIn(it.text) }
        } finally {
            scanner.close()
            recognizer.close()
        }
    }
}
