@file:OptIn(ExperimentalMaterial3Api::class)

package cl.portaalerta.ui

import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.portaalerta.AgendaItem
import cl.portaalerta.MainViewModel
import cl.portaalerta.Scheduler
import cl.portaalerta.Screen
import cl.portaalerta.data.AppSettings
import cl.portaalerta.data.Contact
import cl.portaalerta.data.DefaultTemplates
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupType
import cl.portaalerta.data.Templates
import kotlinx.coroutines.launch

private data class TabDef(val label: String, val icon: ImageVector)

@Composable
fun HomeScreen(vm: MainViewModel) {
    var tab by vm::homeTab
    val tabs = listOf(
        TabDef("Agenda", Icons.Default.DateRange),
        TabDef("Clientes", Icons.Default.Person),
        TabDef("Seguros", Icons.Default.Notifications),
        TabDef("Resumen", Icons.Default.Info),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tabs[tab].label) },
                actions = {
                    IconButton(onClick = { vm.push(Screen.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                0, 1 -> ExtendedFloatingActionButton(
                    onClick = { vm.push(Screen.ClientEdit(null)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo cliente") },
                )
                2 -> ExtendedFloatingActionButton(
                    onClick = { vm.push(Screen.FollowupEdit(null, null, FollowupType.INSURANCE)) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo seguro") },
                )
                else -> {}
            }
        },
    ) { padding ->
        when (tab) {
            0 -> AgendaTab(vm, padding)
            1 -> ClientsTab(vm, padding)
            2 -> InsuranceTab(vm, padding)
            else -> SummaryTab(vm, padding)
        }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<AgendaItem>,
    content: @Composable (AgendaItem) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "header_$title") {
        Text(
            "$title (${list.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    items(list, key = { it.followup.id }) { content(it) }
}

@Composable
private fun AgendaTab(vm: MainViewModel, padding: PaddingValues) {
    var mode by vm::agendaMode
    var doneTarget by remember { mutableStateOf<Followup?>(null) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Lista") })
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Calendario") })
        }
        if (mode == 0) {
            AgendaList(vm) { doneTarget = it }
        } else {
            CalendarView(vm) { doneTarget = it }
        }
    }
    OutcomeDialog(
        target = doneTarget,
        onPick = { outcome ->
            doneTarget?.let { vm.markDone(it, outcome) }
            doneTarget = null
        },
        onDismiss = { doneTarget = null },
    )
}

@Composable
private fun AgendaList(vm: MainViewModel, onDone: (Followup) -> Unit) {
    val all by vm.agenda.collectAsState()
    val settings = vm.settings
    val overdue = all.filter { Dates.daysUntil(it.followup.dueDate) < 0 }
    val now = all.filter {
        val d = Dates.daysUntil(it.followup.dueDate)
        d >= 0 && d <= it.followup.leadDays
    }
    val upcoming = all.filter { Dates.daysUntil(it.followup.dueDate) > it.followup.leadDays }

    if (all.isEmpty()) {
        EmptyState("No tienes recordatorios pendientes.\nCrea un cliente y agrega una portabilidad o un seguro.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val card: @Composable (AgendaItem) -> Unit = { item ->
            FollowupCard(
                f = item.followup,
                client = item.client,
                settings = settings,
                onClick = { vm.push(Screen.ClientDetail(item.client.id)) },
                onDone = { onDone(item.followup) },
            )
        }
        section("Atrasados", overdue, card)
        section("Para contactar ahora", now, card)
        section("Próximos", upcoming, card)
    }
}

@Composable
private fun ClientsTab(vm: MainViewModel, padding: PaddingValues) {
    val clients by vm.clients.collectAsState()
    val ctx = LocalContext.current
    var query by vm::clientQuery
    val filtered = remember(clients, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            clients
        } else {
            clients.filter {
                it.name.lowercase().contains(q) || it.phone.contains(q) || it.currentCarrier.lowercase().contains(q)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar por nombre, teléfono o compañía") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        TextButton(
            onClick = { vm.push(Screen.Import) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("Importar desde los contactos del teléfono") }
        if (filtered.isEmpty()) {
            EmptyState(if (clients.isEmpty()) "Aún no tienes clientes guardados." else "Sin resultados.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(filtered, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = {
                            Text(listOf(c.phone, c.currentCarrier).filter { it.isNotBlank() }.joinToString(" · "))
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                val hi = Templates.render("Hola {nombre} 👋", c, null, vm.settings)
                                Contact.openWhatsapp(ctx, c, hi, vm.settings)
                            }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "WhatsApp") }
                        },
                        modifier = Modifier.clickable { vm.push(Screen.ClientDetail(c.id)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InsuranceTab(vm: MainViewModel, padding: PaddingValues) {
    val all by vm.agenda.collectAsState()
    val list = all.filter { it.followup.type == FollowupType.INSURANCE }
    var doneTarget by remember { mutableStateOf<Followup?>(null) }
    OutcomeDialog(
        target = doneTarget,
        onPick = { outcome ->
            doneTarget?.let { vm.markDone(it, outcome) }
            doneTarget = null
        },
        onDismiss = { doneTarget = null },
    )
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(
            "Cada seguro tiene su cuenta regresiva. Cuando se cumple el plazo (por defecto 1 mes) " +
                "te llega la alarma con acceso directo a WhatsApp.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (list.isEmpty()) {
            EmptyState("No hay seguros en cuenta regresiva.\nToca “Nuevo seguro” para agregar uno.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.followup.id }) { item ->
                    FollowupCard(
                        f = item.followup,
                        client = item.client,
                        settings = vm.settings,
                        onClick = { vm.push(Screen.ClientDetail(item.client.id)) },
                        onDone = { doneTarget = item.followup },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding -> SettingsTab(vm, padding) }
}

@Composable
private fun SettingsTab(vm: MainViewModel, padding: PaddingValues) {
    val ctx = LocalContext.current
    val s: AppSettings = vm.settings
    val scope = rememberCoroutineScope()

    var seller by remember { mutableStateOf(s.sellerName) }
    var store by remember { mutableStateOf(s.storeName) }
    var cc by remember { mutableStateOf(s.countryCode) }
    var hour by remember { mutableIntStateOf(s.notifyHour) }
    var portMonths by remember { mutableIntStateOf(s.months(FollowupType.PORTABILITY)) }
    var portLead by remember { mutableIntStateOf(s.leadDays(FollowupType.PORTABILITY)) }
    var insMonths by remember { mutableIntStateOf(s.months(FollowupType.INSURANCE)) }
    var insLead by remember { mutableIntStateOf(s.leadDays(FollowupType.INSURANCE)) }
    var tplPort by remember { mutableStateOf(s.template(FollowupType.PORTABILITY)) }
    var tplIns by remember { mutableStateOf(s.template(FollowupType.INSURANCE)) }
    var goal by remember { mutableIntStateOf(s.monthlyGoal) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tus datos (se usan en los mensajes)", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(seller, { seller = it }, label = { Text("Tu nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(store, { store = it }, label = { Text("Nombre de la tienda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            cc, { cc = it.filter { c -> c.isDigit() } },
            label = { Text("Código de país para WhatsApp (Chile = 56)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        Text("Alarmas", style = MaterialTheme.typography.titleSmall)
        Stepper("Hora de la alarma", hour, 0..23, "h") { hour = it }
        Stepper("Portabilidad: plazo", portMonths, 1..24, "meses") { portMonths = it }
        Stepper("Portabilidad: avisar antes", portLead, 0..30, "días") { portLead = it }
        Stepper("Seguro: plazo", insMonths, 1..24, "meses") { insMonths = it }
        Stepper("Seguro: avisar antes", insLead, 0..30, "días") { insLead = it }
        Stepper("Meta mensual de cierres", goal, 1..200, "cierres") { goal = it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !Scheduler.canScheduleExact(ctx)) {
            Text(
                "Para que las alarmas suenen a la hora exacta, permite “Alarmas y recordatorios” para esta app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Permitir alarmas exactas") }
        }
        Text(
            "Tip: en Xiaomi, Samsung, Huawei y similares desactiva el ahorro de batería para esta app " +
                "si las alarmas no suenan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        Text("Mensajes de WhatsApp por defecto", style = MaterialTheme.typography.titleSmall)
        Text(
            "Variables: ${Templates.PLACEHOLDERS}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            tplPort, { tplPort = it }, label = { Text("Portabilidad a WOM") },
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { tplPort = DefaultTemplates.PORTABILITY }) { Text("Restaurar mensaje original") }
        OutlinedTextField(
            tplIns, { tplIns = it }, label = { Text("Seguro del equipo") },
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { tplIns = DefaultTemplates.INSURANCE }) { Text("Restaurar mensaje original") }

        Button(
            onClick = {
                s.sellerName = seller.trim()
                s.storeName = store.trim()
                s.countryCode = cc
                s.notifyHour = hour
                s.monthlyGoal = goal
                s.setMonths(FollowupType.PORTABILITY, portMonths)
                s.setLeadDays(FollowupType.PORTABILITY, portLead)
                s.setMonths(FollowupType.INSURANCE, insMonths)
                s.setLeadDays(FollowupType.INSURANCE, insLead)
                s.setTemplate(FollowupType.PORTABILITY, tplPort)
                s.setTemplate(FollowupType.INSURANCE, tplIns)
                vm.rescheduleAll()
                saved = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saved) "Guardado ✓" else "Guardar ajustes") }

        HorizontalDivider()
        Text("Respaldo", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = {
                scope.launch {
                    val csv = vm.exportCsv()
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "Respaldo PortaAlerta")
                        .putExtra(Intent.EXTRA_TEXT, csv)
                    ctx.startActivity(Intent.createChooser(send, "Compartir respaldo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Text("  Exportar clientes y recordatorios (CSV)")
        }
        Spacer(Modifier.height(80.dp))
    }
}
