package cl.portaalerta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.portaalerta.MainViewModel
import cl.portaalerta.Screen
import cl.portaalerta.data.AppSettings
import cl.portaalerta.data.Client
import cl.portaalerta.data.Contact
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.FollowupType
import cl.portaalerta.data.Templates
import java.time.LocalDate

@Composable
fun AppRoot(vm: MainViewModel) {
    val screen = vm.stack.last()
    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        key(screen) {
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                is Screen.ClientDetail -> ClientDetailScreen(vm, screen.id)
                is Screen.ClientEdit -> ClientEditScreen(vm, screen.id)
                is Screen.FollowupEdit -> FollowupEditScreen(vm, screen)
            }
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, suffix: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (value > range.first) onChange(value - 1) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) { Text("−") }
        Text("$value $suffix", Modifier.padding(horizontal = 12.dp))
        OutlinedButton(
            onClick = { if (value < range.last) onChange(value + 1) },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) { Text("+") }
    }
}

/**
 * Tarjeta de un recordatorio con acceso directo a WhatsApp (mensaje personalizado) y llamada.
 * Colores: rojo = atrasado, morado = dentro de la ventana de aviso, gris = aún falta.
 */
@Composable
fun FollowupCard(
    f: Followup,
    client: Client,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    showClientName: Boolean = true,
    onClick: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val days = Dates.daysUntil(f.dueDate)
    val done = f.status == FollowupStatus.DONE
    val container = when {
        done -> cs.surfaceVariant
        days < 0 -> cs.errorContainer
        days <= f.leadDays -> cs.primaryContainer
        else -> cs.surfaceVariant.copy(alpha = 0.5f)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showClientName) client.name else f.type.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (showClientName) {
                    Text(f.type.label, style = MaterialTheme.typography.labelMedium, color = cs.primary)
                }
            }
            if (f.detail.isNotBlank()) Text(f.detail, style = MaterialTheme.typography.bodyMedium)
            val state = if (done) "Completado" else Dates.relative(days).replaceFirstChar { it.uppercase() }
            Text("${Dates.format(f.dueDate)} · $state", style = MaterialTheme.typography.bodyMedium)
            if (!done && days > f.leadDays && f.leadDays > 0) {
                Text(
                    "Te aviso desde el ${Dates.format(f.dueDate - f.leadDays)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (f.type == FollowupType.INSURANCE && !done) {
                val total = (f.dueDate - f.baseDate).coerceAtLeast(1L)
                val elapsed = (LocalDate.now().toEpochDay() - f.baseDate).coerceIn(0L, total)
                LinearProgressIndicator(
                    progress = { elapsed.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!done) {
                    Button(onClick = {
                        Contact.openWhatsapp(ctx, client, Templates.messageFor(f, client, settings), settings)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                    OutlinedIconButton(onClick = { Contact.dial(ctx, client.phone) }) {
                        Icon(Icons.Default.Call, contentDescription = "Llamar")
                    }
                    if (onDone != null) {
                        TextButton(onClick = onDone) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hecho")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                extraActions()
            }
        }
    }
}
