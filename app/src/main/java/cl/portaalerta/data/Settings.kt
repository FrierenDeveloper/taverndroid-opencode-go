package cl.portaalerta.data

import android.content.Context

object DefaultTemplates {
    const val PORTABILITY =
        "Hola {nombre} 👋, soy {vendedor} de {tienda}. Ya cumpliste el plazo y puedes portarte a WOM " +
            "conservando tu mismo número 📲. ¿Te muestro las opciones de planes que tenemos hoy?"

    const val INSURANCE =
        "Hola {nombre} 👋, te escribe {vendedor} de {tienda}. Se está cumpliendo el plazo para el seguro " +
            "de tu equipo {detalle} 🛡️. ¿Lo revisamos hoy?"

    fun forType(type: FollowupType) = when (type) {
        FollowupType.PORTABILITY -> PORTABILITY
        FollowupType.INSURANCE -> INSURANCE
    }
}

/** Preferencias simples de la app (SharedPreferences). */
class AppSettings(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun str(key: String, def: String) = p.getString(key, def) ?: def

    var sellerName: String
        get() = str("seller", "")
        set(v) = p.edit().putString("seller", v).apply()

    var storeName: String
        get() = str("store", "")
        set(v) = p.edit().putString("store", v).apply()

    /** Código de país sin "+" para armar los links de WhatsApp (Chile = 56). */
    var countryCode: String
        get() = str("cc", "56")
        set(v) = p.edit().putString("cc", v.filter { it.isDigit() }.ifEmpty { "56" }).apply()

    /** Pide el PIN, patrón o huella del teléfono al abrir la app y tras 1 minuto fuera de ella. */
    var lockEnabled: Boolean
        get() = p.getBoolean("lock", true)
        set(v) = p.edit().putBoolean("lock", v).apply()

    /** Hora del día (0-23) a la que suenan las alarmas. */
    var notifyHour: Int
        get() = p.getInt("hour", 9)
        set(v) = p.edit().putInt("hour", v.coerceIn(0, 23)).apply()

    /** Meta de cierres (portabilidades + seguros) por mes, para el resumen. */
    var monthlyGoal: Int
        get() = p.getInt("goal", 15)
        set(v) = p.edit().putInt("goal", v.coerceIn(1, 500)).apply()

    fun leadDays(type: FollowupType): Int = p.getInt("lead_${type.name}", type.defaultLeadDays)
    fun setLeadDays(type: FollowupType, days: Int) =
        p.edit().putInt("lead_${type.name}", days.coerceIn(0, 60)).apply()

    fun months(type: FollowupType): Int = p.getInt("months_${type.name}", type.defaultMonths)
    fun setMonths(type: FollowupType, months: Int) =
        p.edit().putInt("months_${type.name}", months.coerceIn(1, 36)).apply()

    fun template(type: FollowupType): String = str("tpl_${type.name}", DefaultTemplates.forType(type))
    fun setTemplate(type: FollowupType, text: String) =
        p.edit().putString("tpl_${type.name}", text).apply()
}
