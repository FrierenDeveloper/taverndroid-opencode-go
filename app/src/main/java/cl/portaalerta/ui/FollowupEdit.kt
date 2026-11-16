@file:OptIn(ExperimentalMaterial3Api::class)

package cl.portaalerta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.portaalerta.MainViewModel
import cl.portaalerta.Screen
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.FollowupType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun FollowupEditScreen(vm: MainViewModel, screen: Screen.FollowupEdit) {
    var initial by remember { mutableStateOf<Followup?>(null) }
    var ready by remember { mutableStateOf(screen.followupId == null) }
    LaunchedEffect(screen.followupId) {
        if (screen.followupId != null) {
            initial = vm.getFollowup(screen.followupId)
            ready = true
        }
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        FollowupForm(vm, initial, screen.clientId, screen.type)
    }
}

@Composable
private fun FollowupForm(vm: MainViewModel, initial: Followup?, clientId: Long?, startType: FollowupType) {
    val settings = vm.settings
    val clients by vm.clients.collectAsState()

    var selectedClient by remember { mutableStateOf(initial?.clientId ?: clientId) }
    var type by remember { mutableStateOf(initial?.type ?: startType) }
    // Portabilidad: la fecha elegida es el DÍA EN QUE PUEDE PORTARSE (no se le suman meses).
    // Solo si no se sabe, se puede calcular desde la fecha de contrato (+ plazo).
    // Seguro: la fecha elegida es la de compra y el aviso llega al cumplirse el plazo.
    var fromStart by remember { mutableStateOf(false) }
    var picked by remember {
        mutableStateOf(
            initial?.let { LocalDate.ofEpochDay(if (it.type == FollowupType.PORTABILITY) it.dueDate else it.baseDate) }
                ?: LocalDate.now(),
        )
    }
    var months by remember { mutableIntStateOf(initial?.months ?: settings.months(startType)) }
    var lead by remember { mutableIntStateOf(initial?.leadDays ?: settings.leadDays(startType)) }
    var detail by remember { mutableStateOf(initial?.detail.orEmpty()) }
    var showPicker by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val exactMode = type == FollowupType.PORTABILITY && !fromStart
    val due = if (exactMode) picked else Dates.dueFor(picked, months)
    val base = if (exactMode) picked.minusMonths(months.toLong()) else picked
    val alertDay = due.minusDays(lead.toLong())
    val clientName = clients.firstOrNull { it.id == selectedClient }?.name

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(if (initial == null) "Nuevo recordatorio" else "Editar recordatorio") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (clientId == null && initial == null) {
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(clientName ?: "Elegir cliente *")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (clients.isEmpty()) {
                            DropdownMenuItem(text = { Text("Primero crea un cliente") }, onClick = { menuOpen = false })
                        }
                        clients.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                onClick = {
                                    selectedClient = c.id
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
            } else if (clientName != null) {
                Text(clientName, style = MaterialTheme.typography.titleMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FollowupType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            if (t != type) picked = LocalDate.now()
                            type = t
                            fromStart = false
                            if (initial == null) {
                                months = settings.months(t)
                                lead = settings.leadDays(t)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }

            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Text(
                    when {
                        exactMode -> "  Puede portarse el: ${Dates.format(picked)}"
                        type == FollowupType.PORTABILITY -> "  Contrató el: ${Dates.format(picked)}"
                        else -> "  Fecha de compra / inicio: ${Dates.format(picked)}"
                    },
                )
            }
            if (type == FollowupType.PORTABILITY) {
                TextButton(onClick = { fromStart = !fromStart }) {
                    Text(
                        if (fromStart) "Mejor, tengo la fecha exacta en que puede portarse"
                        else "No sé la fecha exacta: calcular desde que contrató (+$months meses)",
                    )
                }
            }
            if (!exactMode) Stepper("Plazo", months, 1..36, if (months == 1) "mes" else "meses") { months = it }
            Stepper("Avisar con anticipación", lead, 0..60, if (lead == 1) "día" else "días") { lead = it }

            OutlinedTextField(
                detail, { detail = it },
                label = { Text(if (type == FollowupType.INSURANCE) "Equipo asegurado (ej. iPhone 15)" else "Observación (plan que le interesa, etc.)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (type == FollowupType.PORTABILITY) "Puede portarse el ${Dates.format(due)}" else "Vence: ${Dates.format(due)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (lead > 0) "Alarma de aviso: ${Dates.format(alertDay)} a las ${"%02d:00".format(java.util.Locale.ROOT, settings.notifyHour)}"
                        else "Alarma el día del vencimiento",
                    )
                    if (lead > 0) Text("Y otra el día ${Dates.format(due)}")
                }
            }

            Button(
                enabled = selectedClient != null,
                onClick = {
                    val cid = selectedClient
                    if (cid != null) {
                        val blank = Followup(
                            clientId = cid, type = type, baseDate = 0, months = 0, dueDate = 0, leadDays = 0,
                        )
                        // copy() conserva estado, resultado y fecha de cierre del recordatorio editado.
                        val f = (initial ?: blank).copy(
                            clientId = cid,
                            type = type,
                            baseDate = base.toEpochDay(),
                            months = months,
                            dueDate = due.toEpochDay(),
                            leadDays = lead,
                            detail = detail.trim(),
                        )
                        vm.saveFollowup(f) { vm.pop() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar y programar alarma") }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = picked.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}
