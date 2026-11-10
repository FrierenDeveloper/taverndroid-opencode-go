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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import cl.portaalerta.Screen
import cl.portaalerta.data.Client
import cl.portaalerta.data.Contact
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
 * Alta de cliente en 3 pasos pensada para el mostrador:
 * 1) nombre y teléfono juntos, 2) compañía con un toque, 3) fecha en que puede portarse.
 * Al guardar queda creado el cliente y programada la alarma.
 */
@Composable
fun NewClientScreen(vm: MainViewModel) {
    val settings = vm.settings
    val cc = settings.countryCode
    val clients by vm.clients.collectAsState()
    val months = settings.months(FollowupType.PORTABILITY)

    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var digits by rememberSaveable { mutableStateOf("") }
    var carrier by rememberSaveable { mutableStateOf("") }
    var otherMode by rememberSaveable { mutableStateOf(false) }
    var fromContract by rememberSaveable { mutableStateOf(false) }
    var pickedEpoch by rememberSaveable { mutableStateOf(-1L) }
    var lead by rememberSaveable { mutableIntStateOf(settings.leadDays(FollowupType.PORTABILITY)) }
    var showPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    BackHandler(enabled = step > 0) { step -= 1 }

    val today = LocalDate.now()
    val picked: LocalDate? = if (pickedEpoch >= 0) LocalDate.ofEpochDay(pickedEpoch) else null
    val due: LocalDate? = picked?.let { if (fromContract) it.plusMonths(months.toLong()) else it }
    val base: LocalDate? = picked?.let { if (fromContract) it else it.minusMonths(months.toLong()) }

    val phoneOk = Contact.isCompleteLocal(digits, cc)
    val duplicate: Client? = if (phoneOk) {
        val key = Contact.waNumber(digits, cc)
        clients.firstOrNull { Contact.waNumber(it.phone, cc) == key }
    } else {
        null
    }

    fun save(withDate: Boolean, thenScanChip: Boolean) {
        if (saving) return
        saving = true
        val client = Client(name = name.trim(), phone = Contact.pretty(digits, cc), currentCarrier = carrier.trim())
        vm.saveClient(client) { clientId ->
            fun finish() {
                vm.replaceTop(Screen.ClientDetail(clientId))
                if (thenScanChip) vm.push(Screen.Scanner(clientId))
            }
            if (withDate && due != null && base != null) {
                val f = Followup(
                    clientId = clientId,
                    type = FollowupType.PORTABILITY,
                    baseDate = base.toEpochDay(),
                    months = months,
                    dueDate = due.toEpochDay(),
                    leadDays = lead,
                )
                vm.saveFollowup(f) { finish() }
            } else {
                finish()
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = appBarColors(),
                    title = { Text("Nuevo cliente") },
                    navigationIcon = {
                        IconButton(onClick = { if (step > 0) step -= 1 else vm.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    },
                )
                LinearProgressIndicator(
                    progress = { (step + 1) / 3f },
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
                        onClick = { step = 1 },
                        enabled = name.isNotBlank() && phoneOk,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Siguiente") }
                    1 -> if (otherMode) {
                        Button(
                            onClick = { step = 2 },
                            enabled = carrier.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Siguiente") }
                    } else {
                        TextButton(onClick = { carrier = ""; step = 2 }, modifier = Modifier.fillMaxWidth()) {
                            Text("No sé la compañía, saltar")
                        }
                    }
                    else -> {
                        Button(
                            onClick = { save(withDate = true, thenScanChip = false) },
                            enabled = due != null && !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Guardar y programar alarma") }
                        OutlinedButton(
                            onClick = { save(withDate = true, thenScanChip = true) },
                            enabled = due != null && !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Guardar y leer chip") }
                        TextButton(
                            onClick = { save(withDate = false, thenScanChip = false) },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Guardar sin fecha por ahora") }
                    }
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
                    StepTitle("1 de 3", "¿Quién es el cliente?")
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = digits,
                        onValueChange = { digits = Contact.normalizeLocal(it, cc) },
                        label = { Text("Teléfono / WhatsApp") },
                        prefix = { Text("+$cc ") },
                        placeholder = { Text("9 1234 5678") },
                        supportingText = {
                            Text(
                                when {
                                    digits.isEmpty() -> "Solo el número, sin el +$cc"
                                    !phoneOk -> "Faltan dígitos (${digits.length} de 9)"
                                    digits.first() != '9' && cc == "56" -> "No parece celular: WhatsApp no lo abrirá"
                                    else -> "Número completo ✓"
                                },
                            )
                        },
                        isError = digits.isNotEmpty() && phoneOk && cc == "56" && digits.first() != '9',
                        visualTransformation = if (cc == "56") ChilePhoneFormat() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    duplicate?.let { d ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Este número ya está guardado", style = MaterialTheme.typography.titleSmall)
                                Text("${d.name} · ${d.phone}")
                                TextButton(onClick = { vm.replaceTop(Screen.ClientDetail(d.id)) }) { Text("Abrir su ficha") }
                            }
                        }
                    }
                }

                1 -> {
                    StepTitle("2 de 3", "¿De qué compañía viene?")
                    CarrierGrid(
                        selected = if (otherMode) Carriers.OTHER else carrier,
                        onPick = { picked ->
                            if (picked == Carriers.OTHER) {
                                otherMode = true
                                carrier = ""
                            } else {
                                otherMode = false
                                carrier = picked
                                step = 2
                            }
                        },
                    )
                    if (otherMode) {
                        OutlinedTextField(
                            value = carrier,
                            onValueChange = { carrier = it },
                            label = { Text("Nombre de la compañía") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                else -> {
                    StepTitle("3 de 3", if (fromContract) "¿Cuándo contrató con su compañía?" else "¿Cuándo puede portarse?")
                    val options: List<Pair<String, LocalDate>> = if (fromContract) {
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
                                onClick = { pickedEpoch = date.toEpochDay() },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (picked != null && options.none { it.second == picked }) {
                                "Fecha elegida: ${Dates.format(picked)}"
                            } else {
                                "Elegir otra fecha en el calendario"
                            },
                        )
                    }
                    TextButton(onClick = { fromContract = !fromContract; pickedEpoch = -1L }) {
                        Text(if (fromContract) "Mejor, tengo la fecha exacta en que puede portarse" else "No sé la fecha exacta: calcular desde que contrató (+$months meses)")
                    }

                    if (due != null) {
                        Card {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CarrierBadge(carrier.ifBlank { "?" }, 48.dp)
                                    Column {
                                        Text(name.trim(), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            Contact.pretty(digits, cc) + if (carrier.isNotBlank()) " · $carrier" else "",
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
                                        Text("Puede portarse el ${Dates.format(due)}", fontWeight = FontWeight.Bold)
                                        val days = due.toEpochDay() - today.toEpochDay()
                                        Text(
                                            if (days < 0) {
                                                "Ya puede portarse: aparecerá en “Para contactar ahora”."
                                            } else if (lead > 0 && days > lead) {
                                                "Te aviso el ${Dates.format(due.minusDays(lead.toLong()))} y el ${Dates.format(due)}."
                                            } else {
                                                "Te aviso el ${Dates.format(due)}."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                Text("Avisarme antes:", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(1, 3, 7, 14).forEach { d ->
                                        FilterChip(selected = lead == d, onClick = { lead = d }, label = { Text("$d d") })
                                    }
                                }
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
                        pickedEpoch = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun StepTitle(step: String, title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(step.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}
