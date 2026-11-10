package cl.portaalerta.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es", "CL"))

    fun format(date: LocalDate): String = date.format(fmt)
    fun format(epochDay: Long): String = format(LocalDate.ofEpochDay(epochDay))
    fun daysUntil(epochDay: Long): Long = epochDay - LocalDate.now().toEpochDay()

    fun relative(days: Long): String = when {
        days > 1 -> "en $days días"
        days == 1L -> "mañana"
        days == 0L -> "hoy"
        days == -1L -> "ayer"
        else -> "hace ${-days} días"
    }

    fun dueFor(base: LocalDate, months: Int): LocalDate = base.plusMonths(months.toLong())
}

object Contact {
    /** Deja solo dígitos con código de país: "9 1234 5678" -> "56912345678". */
    fun waNumber(raw: String, countryCode: String): String {
        val digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return digits
        val local = digits.trimStart('0')
        return if (local.startsWith(countryCode) && local.length >= countryCode.length + 8) {
            local
        } else {
            countryCode + local
        }
    }

    /**
     * Deja solo el número local mientras se escribe: acepta "+56 9 8123 4567", "56981234567" o "981234567".
     * En Chile el celular local tiene 9 dígitos.
     */
    fun normalizeLocal(input: String, countryCode: String): String {
        var d = input.filter { it.isDigit() }
        val international = input.trim().startsWith("+")
        if (d.startsWith(countryCode) && (international || d.length > 9)) d = d.removePrefix(countryCode)
        return d.take(if (countryCode == "56") 9 else 15)
    }

    fun isCompleteLocal(digits: String, countryCode: String): Boolean =
        if (countryCode == "56") digits.length == 9 else digits.length >= 8

    /** +56 9 8123 4567 */
    fun pretty(digits: String, countryCode: String): String =
        if (countryCode == "56" && digits.length == 9) {
            "+56 ${digits[0]} ${digits.substring(1, 5)} ${digits.substring(5)}"
        } else {
            "+$countryCode $digits"
        }

    fun whatsappIntent(phone: String, message: String, countryCode: String): Intent {
        val number = waNumber(phone, countryCode)
        val uri = Uri.parse("https://wa.me/$number?text=${Uri.encode(message)}")
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun dialIntent(phone: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun openWhatsapp(ctx: Context, client: Client, message: String, settings: AppSettings) {
        try {
            ctx.startActivity(whatsappIntent(client.phone, message, settings.countryCode))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    fun dial(ctx: Context, phone: String) {
        try {
            ctx.startActivity(dialIntent(phone))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
        }
    }
}

object Templates {
    const val PLACEHOLDERS =
        "{nombre} {nombre_completo} {vendedor} {tienda} {compania} {detalle} {fecha}"

    fun render(template: String, client: Client, f: Followup?, s: AppSettings): String =
        template
            .replace("{nombre_completo}", client.name)
            .replace("{nombre}", client.name.trim().substringBefore(' '))
            .replace("{vendedor}", s.sellerName)
            .replace("{tienda}", s.storeName)
            .replace("{compania}", client.currentCarrier)
            .replace("{plan}", client.plan)
            .replace("{detalle}", f?.detail.orEmpty())
            .replace("{fecha}", f?.let { Dates.format(it.dueDate) }.orEmpty())
            .replace(Regex(" {2,}"), " ")
            .replace(" ,", ",")
            .replace(" .", ".")
            .trim()

    fun messageFor(f: Followup, client: Client, s: AppSettings): String =
        render(s.template(f.type), client, f, s)
}
