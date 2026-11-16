@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cl.portaalerta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cl.portaalerta.MainViewModel
import cl.portaalerta.data.Iccid
import cl.portaalerta.data.ChipScan
import cl.portaalerta.NewClientDraft
import cl.portaalerta.Screen
import cl.portaalerta.data.Client
import cl.portaalerta.data.Contact
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupType
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Fecha de portabilidad elegida en el paso 3. */
data class FollowupDraft(val base: Long, val due: Long, val months: Int, val leadDays: Int)

/** Chip leído en el paso 4 (la foto, si existe, ya está cifrada). */
data class ChipDraft(val iccid: String, val tempPath: String?)

/** Lo que el alta de cliente necesita del resto de la app. */
interface NewClientHost {
    val countryCode: String
    val months: Int
    val draft: NewClientDraft
    val clients: StateFlow<List<Client>>
    fun save(client: Client, followup: FollowupDraft?, chip: ChipDraft?)
    fun scanChip()
    fun openClient(id: Long)

    /** Sale del asistente descartando lo escrito. */
    fun exit()
}

class VmNewClientHost(private val vm: MainViewModel) : NewClientHost {
    override val countryCode get() = vm.settings.countryCode
    override val months get() = vm.settings.months(FollowupType.PORTABILITY)
    override val draft get() = vm.newClientDraft
    override val clients get() = vm.clients

    override fun save(client: Client, followup: FollowupDraft?, chip: ChipDraft?) {
        vm.saveClient(client) { clientId ->
            fun finish() {
                chip?.let { vm.saveScan(ChipScan(iccid = it.iccid, clientId = clientId), it.tempPath) { } }
                vm.finishNewClient(clientId)
            }
            if (followup != null) {
                val f = Followup(
                    clientId = clientId,
                    type = FollowupType.PORTABILITY,
                    baseDate = followup.base,
                    months = followup.months,
                    dueDate = followup.due,
                    leadDays = followup.leadDays,
                )
                vm.saveFollowup(f) { finish() }
            } else {
                finish()
            }
        }
    }

    override fun scanChip() = vm.push(Screen.Scanner(clientId = null, forDraft = true))
    override fun openClient(id: Long) = vm.replaceTop(Screen.ClientDetail(id))
    override fun exit() = vm.discardNewClient()
}

/** Muestra 981234567 como "9 8123 4567" sin cambiar lo que se guarda. */
private class ChilePhoneFormat : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val out = StringBuilder()
        d.forEachIndexed { i, c ->
            if (i == 1 || i == 5) out.append(' ')
            out.append(c)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                when {
                    offset <= 1 -> offset
                    offset <= 5 -> offset + 1
                    else -> offset + 2
                }.coerceAtMost(out.length)

            override fun transformedToOriginal(offset: Int): Int =
                when {
                    offset <= 1 -> offset
                    offset <= 6 -> offset - 1
                    else -> offset - 2
                }.coerceIn(0, d.length)
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/**
 * Alta de cliente en 4 pasos pensada para el mostrador:
 * 1) nombre y teléfono, 2) compañía con un toque, 3) fecha en que puede portarse, 4) chip (opcional) y guardar.
 *
 * LA FECHA DEL PASO 3 es el día en que el cliente PUEDE PORTARSE: no se le suman meses.
 * Solo si no se sabe, se puede elegir "calcular desde que contrató" y entonces se suma el plazo (4 meses).
 */
@Composable
fun NewClientScreen(host: NewClientHost) {
    val cc = host.countryCode
    val clients by host.clients.collectAsState()
    val months = host.months
    val d = host.draft

    var showPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    val step = d.step
    val totalSteps = 4
    fun goBack() {
        if (d.step > 0) d.step -= 1 else if (d.hasData || d.chipIccid.isNotEmpty()) confirmExit = true else host.exit()
    }
    BackHandler { goBack() }

    val today = LocalDate.now()
    val picked: LocalDate? = if (d.pickedEpoch >= 0) LocalDate.ofEpochDay(d.pickedEpoch) else null
    val due: LocalDate? = picked?.let { if (d.fromContract) it.plusMonths(months.toLong()) else it }
    val base: LocalDate? = picked?.let { if (d.fromContract) it else it.minusMonths(months.toLong()) }

    val phoneOk = Contact.isCompleteLocal(d.digits, cc)
    val duplicate: Client? = if (phoneOk) {
        val key = Contact.waNumber(d.digits, cc)
        clients.firstOrNull { Contact.waNumber(it.phone, cc) == key }
    } else {
        null
    }

    fun save() {
        if (saving) return
        saving = true
        val client = Client(name = d.name.trim(), phone = Contact.pretty(d.digits, cc), currentCarrier = d.carrier.trim())
        val followup = if (due != null && base != null) {
            FollowupDraft(base = base.toEpochDay(), due = due.toEpochDay(), months = months, leadDays = d.leadDays)
        } else {
            null
        }
        val chip = if (d.chipIccid.isNotBlank()) ChipDraft(d.chipIccid, d.chipPath) else null
        host.save(client, followup, chip)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = appBarColors(),
                    title = { Text("Nuevo cliente") },
                    navigationIcon = {
                        IconButton(onClick = { goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (step + 1) / totalSteps.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        },
        bottomBar = {
            Column(
                Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (step) {
                    0 -> Button(
                        onClick = { d.step = 1 },
                        enabled = d.name.isNotBlank() && phoneOk,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Siguiente") }
                    1 -> if (d.otherMode) {
                        Button(
                            onClick = { d.step = 2 },
                            enabled = d.carrier.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Siguiente") }
                    } else {
                        TextButton(onClick = { d.carrier = ""; d.step = 2 }, modifier = Modifier.fillMaxWidth()) {
                            Text("No sé la compañía, saltar")
                        }
                    }
                    2 -> {
                        Button(
                            onClick = { d.step = 3 },
                            enabled = due != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Siguiente") }
                        TextButton(
                            onClick = { d.pickedEpoch = -1L; d.step = 3 },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sin fecha por ahora") }
                    }
                    else -> Button(
                        onClick = { save() },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (due != null) "Guardar y programar alarma" else "Guardar cliente") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> {
                    StepTitle("1 de $totalSteps", "¿Quién es el cliente?")
                    OutlinedTextField(
                        value = d.name,
                        onValueChange = { d.name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = d.digits,
                        onValueChange = { d.digits = Contact.normalizeLocal(it, cc) },
                        label = { Text("Teléfono / WhatsApp") },
                        prefix = { Text("+$cc ") },
                        placeholder = { Text("9 1234 5678") },
                        supportingText = {
                            Text(
                                when {
                                    d.digits.isEmpty() -> "Solo el número, sin el +$cc"
                                    !phoneOk -> "Faltan dígitos (${d.digits.length} de 9)"
                                    d.digits.first() != '9' && cc == "56" -> "No parece celular: WhatsApp no lo abrirá"
                                    else -> "Número completo ✓"
                                },
                            )
                        },
                        isError = d.digits.isNotEmpty() && phoneOk && cc == "56" && d.digits.first() != '9',
                        visualTransformation = if (cc == "56") ChilePhoneFormat() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    duplicate?.let { dup ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Este número ya está guardado", style = MaterialTheme.typography.titleSmall)
                                Text("${dup.name} · ${dup.phone}")
                                TextButton(onClick = { host.openClient(dup.id) }) { Text("Abrir su ficha") }
                            }
                        }
                    }
                }

                1 -> {
                    StepTitle("2 de $totalSteps", "¿De qué compañía viene?")
                    CarrierGrid(
                        selected = if (d.otherMode) Carriers.OTHER else d.carrier,
                        onPick = { choice ->
                            if (choice == Carriers.OTHER) {
                                d.otherMode = true
                                d.carrier = ""
                            } else {
                                d.otherMode = false
                                d.carrier = choice
                                d.step = 2
                            }
                        },
                    )
                    if (d.otherMode) {
                        OutlinedTextField(
                            value = d.carrier,
                            onValueChange = { d.carrier = it },
                            label = { Text("Nombre de la compañía") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                2 -> {
                    StepTitle("3 de $totalSteps", if (d.fromContract) "¿Cuándo contrató?" else "¿Cuándo puede portarse?")
                    Text(
                        if (d.fromContract) {
                            "Escoge el día en que firmó su contrato. Le sumamos $months meses para saber cuándo puede portarse."
                        } else {
                            "Escoge el día en que queda libre para cambiarse de compañía."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val options: List<Pair<String, LocalDate>> = if (d.fromContract) {
                        listOf(
                            "Hoy" to today,
                            "Hace 1 mes" to today.minusMonths(1),
                            "Hace 2 meses" to today.minusMonths(2),
                            "Hace 3 meses" to today.minusMonths(3),
                            "Hace 4 meses" to today.minusMonths(4),
                        )
                    } else {
                        listOf(
                            "Hoy" to today,
                            "En 1 semana" to today.plusWeeks(1),
                            "En 2 semanas" to today.plusWeeks(2),
                            "En 1 mes" to today.plusMonths(1),
                            "En 2 meses" to today.plusMonths(2),
                            "En 3 meses" to today.plusMonths(3),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (label, date) ->
                            FilterChip(
                                selected = picked == date,
                                onClick = { d.pickedEpoch = date.toEpochDay() },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (picked != null && options.none { it.second == picked }) {
                                "Fecha elegida: ${Dates.format(picked)}"
                            } else {
                                "Elegir la fecha en el calendario"
                            },
                        )
                    }
                    TextButton(onClick = { d.fromContract = !d.fromContract; d.pickedEpoch = -1L }) {
                        Text(
                            if (d.fromContract) {
                                "Mejor, tengo la fecha exacta en que puede portarse"
                            } else {
                                "No sé la fecha exacta: calcular desde que contrató (+$months meses)"
                            },
                        )
                    }
                    if (due != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Puede portarse el ${Dates.format(due)}", fontWeight = FontWeight.Bold)
                                Text(alertLine(due, d.leadDays, today), style = MaterialTheme.typography.bodyMedium)
                                Text("Avisarme antes:", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 3, 7, 14).forEach { n ->
                                        FilterChip(selected = d.leadDays == n, onClick = { d.leadDays = n }, label = { Text("$n d") })
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    StepTitle("4 de $totalSteps", "¿Le entregas el chip ahora?")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CarrierBadge(d.carrier.ifBlank { "?" }, 48.dp)
                                Column {
                                    Text(d.name.trim(), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        Contact.pretty(d.digits, cc) + if (d.carrier.isNotBlank()) " · ${d.carrier}" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    if (due != null) {
                                        Text("Puede portarse el ${Dates.format(due)}", fontWeight = FontWeight.Bold)
                                        Text(alertLine(due, d.leadDays, today), style = MaterialTheme.typography.bodyMedium)
                                    } else {
                                        Text("Sin fecha por ahora", fontWeight = FontWeight.Bold)
                                        Text("No se programará ninguna alarma.", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Chip (opcional)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Escanéalo con la cámara y queda guardado con el cliente. Si no tienes el chip a mano, déjalo para después.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (d.chipPath != null) {
                                PhotoThumb(d.chipPath, 96.dp)
                            }
                            Button(onClick = { host.scanChip() }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (d.chipIccid.isEmpty()) "Leer chip con la cámara" else "Leer otro chip")
                            }
                            OutlinedTextField(
                                value = d.chipIccid,
                                onValueChange = { d.chipIccid = it.filter { c -> c.isDigit() }.take(20) },
                                label = { Text("Número del chip (ICCID)") },
                                supportingText = {
                                    Text(
                                        when {
                                            d.chipIccid.isEmpty() -> "También puedes escribirlo"
                                            Iccid.isValid(d.chipIccid) -> "Número válido ✓  ${Iccid.pretty(d.chipIccid)}"
                                            else -> "No pasa la validación: revisa los dígitos"
                                        },
                                    )
                                },
                                isError = d.chipIccid.isNotEmpty() && !Iccid.isValid(d.chipIccid),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (d.chipIccid.isNotEmpty() || d.chipPath != null) {
                                TextButton(onClick = { d.discardChip() }) { Text("Quitar chip") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (picked ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        d.pickedEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Descartar este cliente?") },
            text = { Text("Lo que escribiste no se ha guardado.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; host.exit() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir editando") } },
        )
    }
}

private fun alertLine(due: LocalDate, lead: Int, today: LocalDate): String {
    val days = due.toEpochDay() - today.toEpochDay()
    return when {
        days < 0 -> "Ya puede portarse: aparecerá en “Para contactar ahora”."
        lead > 0 && days > lead -> "Te aviso el ${Dates.format(due.minusDays(lead.toLong()))} y el ${Dates.format(due)}."
        else -> "Te aviso el ${Dates.format(due)}."
    }
}

@Composable
private fun StepTitle(step: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(step.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
