@file:OptIn(ExperimentalMaterial3Api::class)

package cl.portaalerta.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cl.portaalerta.MainViewModel
import cl.portaalerta.data.Contact
import cl.portaalerta.data.Dates
import cl.portaalerta.data.DeviceContact
import cl.portaalerta.data.DeviceContacts
import cl.portaalerta.data.FollowupType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ImportScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val settings = vm.settings
    val clients by vm.clients.collectAsState()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var contacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            contacts = DeviceContacts.load(ctx, settings.countryCode)
            loaded = true
        } else {
            launcher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val existing = remember(clients) { clients.map { Contact.waNumber(it.phone, settings.countryCode) }.toSet() }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var createReminder by remember { mutableStateOf(true) }
    var base by remember { mutableStateOf(LocalDate.now()) }
    var showPicker by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }

    val visible = remember(contacts, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) contacts else contacts.filter { it.name.lowercase().contains(q) || it.phone.contains(q) }
    }
    val months = settings.months(FollowupType.PORTABILITY)
    val lead = settings.leadDays(FollowupType.PORTABILITY)
    val due = Dates.dueFor(base, months)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = appBarColors(),
                title = { Text("Importar contactos") },
                navigationIcon = {
                    IconButton(onClick = { vm.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Crear recordatorio de portabilidad", Modifier.weight(1f))
                        Switch(checked = createReminder, onCheckedChange = { createReminder = it })
                    }
                    if (createReminder) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fecha base para todos", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { showPicker = true }) { Text(Dates.format(base)) }
                        }
                        Text(
                            "Vence el ${Dates.format(due)} ($months meses) · aviso $lead días antes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        enabled = selected.isNotEmpty() && !importing,
                        onClick = {
                            importing = true
                            val chosen = contacts.filter { it.key in selected }.map { it.name to it.phone }
                            vm.importContacts(chosen, createReminder, base) { n ->
                                Toast.makeText(ctx, "Se importaron $n contactos", Toast.LENGTH_SHORT).show()
                                vm.pop()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (selected.isEmpty()) "Elige contactos" else "Importar ${selected.size} contactos") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!hasPermission) {
                EmptyState("Necesito permiso para leer tus contactos.\nSi no aparece el aviso, actívalo en Ajustes del teléfono > Apps > PortaAlerta.")
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar en tu agenda") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (loaded && visible.isEmpty()) {
                EmptyState("No hay contactos para mostrar.")
            } else {
                LazyColumn {
                    items(visible, key = { it.key }) { c ->
                        val exists = c.key in existing
                        val mobile = DeviceContacts.isMobile(c.key, settings.countryCode)
                        val checked = c.key in selected
                        ListItem(
                            leadingContent = {
                                Checkbox(
                                    checked = checked,
                                    enabled = !exists,
                                    onCheckedChange = { selected = if (it) selected + c.key else selected - c.key },
                                )
                            },
                            headlineContent = { Text(c.name) },
                            supportingContent = { Text(c.phone) },
                            trailingContent = {
                                when {
                                    exists -> Text("Ya existe", style = MaterialTheme.typography.labelMedium)
                                    !mobile -> Text("Fijo", style = MaterialTheme.typography.labelMedium)
                                }
                            },
                            modifier = Modifier.clickable(enabled = !exists) {
                                selected = if (checked) selected - c.key else selected + c.key
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = base.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        base = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}
