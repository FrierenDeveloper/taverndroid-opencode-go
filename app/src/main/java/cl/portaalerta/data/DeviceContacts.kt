package cl.portaalerta.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(val name: String, val phone: String, val key: String)

/** Lee la agenda del teléfono (requiere permiso READ_CONTACTS) sin repetir números. */
object DeviceContacts {
    suspend fun load(ctx: Context, countryCode: String): List<DeviceContact> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, DeviceContact>()
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            null,
            null,
            "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            val nameIdx = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndexOrThrow(Phone.NUMBER)
            while (c.moveToNext()) {
                val number = c.getString(numIdx).orEmpty().trim()
                val key = Contact.waNumber(number, countryCode)
                if (key.isNotEmpty() && key !in out) {
                    out[key] = DeviceContact(c.getString(nameIdx).orEmpty().ifBlank { number }, number, key)
                }
            }
        }
        out.values.toList()
    }

    /** En Chile los celulares son 569XXXXXXXX; los fijos no sirven para WhatsApp. */
    fun isMobile(key: String, countryCode: String): Boolean =
        countryCode != "56" || (key.startsWith("569") && key.length == 11)
}
