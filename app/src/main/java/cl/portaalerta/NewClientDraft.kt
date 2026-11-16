package cl.portaalerta

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.portaalerta.data.PhotoStore

/**
 * Estado del alta de cliente. Vive en el ViewModel para que no se pierda al pasar por la pantalla
 * del lector de chips (la pantalla del asistente sale de la composición mientras se escanea).
 */
class NewClientDraft(
    step: Int = 0,
    name: String = "",
    digits: String = "",
    carrier: String = "",
    otherMode: Boolean = false,
    fromContract: Boolean = false,
    pickedEpoch: Long = -1L,
    leadDays: Int = 7,
    chipIccid: String = "",
    chipPath: String? = null,
) {
    var step by mutableIntStateOf(step)
    var name by mutableStateOf(name)
    var digits by mutableStateOf(digits)
    var carrier by mutableStateOf(carrier)
    var otherMode by mutableStateOf(otherMode)

    /** false = la fecha elegida es el día en que puede portarse; true = es la fecha de contrato (+ plazo). */
    var fromContract by mutableStateOf(fromContract)
    var pickedEpoch by mutableLongStateOf(pickedEpoch)
    var leadDays by mutableIntStateOf(leadDays)
    var chipIccid by mutableStateOf(chipIccid)

    /** Foto del chip, ya cifrada y temporal hasta que se guarda el cliente. */
    var chipPath by mutableStateOf(chipPath)

    val hasData: Boolean get() = name.isNotBlank() || digits.isNotEmpty()

    fun setChip(iccid: String, path: String?) {
        val old = chipPath
        if (old != null && old != path) PhotoStore.delete(old)
        chipIccid = iccid
        chipPath = path
    }

    fun discardChip() {
        PhotoStore.delete(chipPath)
        chipIccid = ""
        chipPath = null
    }
}
