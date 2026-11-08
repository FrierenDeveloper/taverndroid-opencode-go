@file:OptIn(ExperimentalMaterial3Api::class)

package cl.portaalerta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.portaalerta.MainViewModel
import cl.portaalerta.Screen
import cl.portaalerta.data.Client
import cl.portaalerta.data.Contact
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.FollowupType
import cl.portaalerta.data.Templates

@Composable
fun ClientEditScreen(vm: MainViewModel, id: Long?) {
    var initial by remember { mutableStateOf<Client?>(null) }
    var ready by remember { mutableStateOf(id == null) }
    LaunchedEffect(id) {
        if (id != null) {
            initial = vm.getClient(id)
            ready = true
        }
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        ClientForm(vm, initial)
    }
}

@Composable
private fun ClientForm(vm: MainViewModel, initial: Client?) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var carrier by remember { mutableStateOf(initial?.currentCarrier.orEmpty()) }
    var plan by remember { mutableStateOf(initial?.plan.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    val valid = name.isNotBlank() && phone.count { it.isDigit() } >= 8

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(if (initial == null) "Nuevo cliente" else "Editar cliente") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                phone, { phone = it }, label = { Text("Teléfono / WhatsApp *") },
                placeholder = { Text("9 1234 5678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                carrier, { carrier = it }, label = { Text("Compañía actual") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                plan, { plan = it }, label = { Text("Plan actual (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Notas") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = valid,
                onClick = {
                    val c = (initial ?: Client(name = "", phone = "")).copy(
                        name = name.trim(), phone = phone.trim(), currentCarrier = carrier.trim(),
                        plan = plan.trim(), notes = notes.trim(),
                    )
                    vm.saveClient(c) { savedId ->
                        if (initial == null) vm.replaceTop(Screen.ClientDetail(savedId)) else vm.pop()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }
}

@Composable
fun ClientDetailScreen(vm: MainViewModel, id: Long) {
    val ctx = LocalContext.current
    val client by remember(id) { vm.clientFlow(id) }.collectAsState(initial = null)
    val followups by remember(id) { vm.followupsFor(id) }.collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    var followupToDelete by remember { mutableStateOf<Followup?>(null) }
    var doneTarget by remember { mutableStateOf<Followup?>(null) }

    val c = client
    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text(c?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.push(Screen.ClientEdit(id)) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                    }
                },
            )
        },
    ) { padding ->
        if (c == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(c.phone, style = MaterialTheme.typography.titleMedium)
                        if (c.currentCarrier.isNotBlank()) Text("Compañía: ${c.currentCarrier}")
                        if (c.plan.isNotBlank()) Text("Plan: ${c.plan}")
                        if (c.notes.isNotBlank()) Text(c.notes, style = MaterialTheme.typography.bodySmall)
                        Row(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = {
                                    val hi = Templates.render("Hola {nombre} 👋", c, null, vm.settings)
                                    Contact.openWhatsapp(ctx, c, hi, vm.settings)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("WhatsApp")
                            }
                            OutlinedButton(onClick = { Contact.dial(ctx, c.phone) }) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Llamar")
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.push(Screen.FollowupEdit(null, id, FollowupType.PORTABILITY)) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Portabilidad")
                    }
                    OutlinedButton(onClick = { vm.push(Screen.FollowupEdit(null, id, FollowupType.INSURANCE)) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Seguro")
                    }
                }
            }
            if (followups.isEmpty()) {
                item {
                    Text(
                        "Sin recordatorios. Agrega una portabilidad (alarma a los 4 meses) o un seguro (1 mes).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(followups, key = { it.id }) { f ->
                FollowupCard(
                    f = f,
                    client = c,
                    settings = vm.settings,
                    showClientName = false,
                    onDone = { doneTarget = f },
                    extraActions = {
                        if (f.status == FollowupStatus.DONE) {
                            TextButton(onClick = { vm.reopen(f) }) { Text("Reabrir") }
                        }
                        IconButton(onClick = { vm.push(Screen.FollowupEdit(f.id, id, f.type)) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { followupToDelete = f }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    },
                )
            }
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
    if (confirmDelete && c != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar cliente") },
            text = { Text("Se eliminarán ${c.name} y todos sus recordatorios.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteClient(c)
                    vm.pop()
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
    followupToDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { followupToDelete = null },
            title = { Text("Eliminar recordatorio") },
            text = { Text("¿Eliminar “${f.type.label}”?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFollowup(f)
                    followupToDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { followupToDelete = null }) { Text("Cancelar") } },
        )
    }
}
