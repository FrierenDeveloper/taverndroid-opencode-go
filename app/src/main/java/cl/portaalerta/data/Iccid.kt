package cl.portaalerta.data

/**
 * Validación y búsqueda del ICCID (número de la SIM): 19 o 20 dígitos, empieza con 89
 * y el último dígito es de control (Luhn). Funciona tanto para el código de barras como para el
 * número impreso leído por OCR.
 */
object Iccid {
    fun isValid(s: String): Boolean =
        s.length in 19..20 && s.all { it.isDigit() } && s.startsWith("89") && luhn(s)

    fun luhn(s: String): Boolean {
        var sum = 0
        var double = false
        for (i in s.length - 1 downTo 0) {
            var d = s[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** El contenido de un código de barras: solo dígitos, o un ICCID válido escondido dentro del texto. */
    fun fromCode(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return if (isValid(digits)) digits else findIn(raw)
    }

    /** Busca un ICCID válido en el texto que entrega el OCR (con espacios, saltos de línea y letras confundidas). */
    fun findIn(text: String): String? {
        val lines = text.lines().map { fixOcr(it) }
        val candidates = lines.map { l -> l.filter { it.isDigit() } } + listOf(lines.joinToString("").filter { it.isDigit() })
        for (digits in candidates) search(digits)?.let { return it }
        return null
    }

    private fun search(digits: String): String? {
        var i = digits.indexOf("89")
        while (i >= 0) {
            for (len in intArrayOf(20, 19)) {
                if (i + len <= digits.length) {
                    val c = digits.substring(i, i + len)
                    if (isValid(c)) return c
                }
            }
            i = digits.indexOf("89", i + 1)
        }
        return null
    }

    /** En líneas casi numéricas, el OCR suele confundir O/0, I/1, S/5 y B/8. */
    private fun fixOcr(line: String): String =
        if (line.count { it.isDigit() } < 10) {
            line
        } else {
            line.map {
                when (it) {
                    'O', 'o' -> '0'
                    'I', 'l', '|' -> '1'
                    'S' -> '5'
                    'B' -> '8'
                    else -> it
                }
            }.joinToString("")
        }

    /** 8956 5260 1815 9083 015 */
    fun pretty(s: String): String = s.chunked(4).joinToString(" ")
}
