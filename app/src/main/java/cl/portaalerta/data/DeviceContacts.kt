package cl.portaalerta.data

import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(val name: String, val phone: String, val key: String)

/** Lee la agenda del teléfono (requiere permiso READ_CONTACTS) sin repetir números. */
object DeviceContacts {
    suspend fun load(ctx: Context, countryCode: String): List<DeviceContact> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, DeviceContact>()
        val phone = ContactsContract.CommonDataKinds.Phone
        ctx.contentResolver.query(
            phone.CONTENT_URI,
            arrayOf(phone.DISPLAY_NAME, phone.NUMBER),
            null,
            null,
            "${phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            val nameIdx = c.getColumnIndexOrThrow(phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndexOrThrow(phone.NUMBER)
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
