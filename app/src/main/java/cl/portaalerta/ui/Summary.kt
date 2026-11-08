package cl.portaalerta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.portaalerta.MainViewModel
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupOutcome
import cl.portaalerta.data.FollowupStatus
import cl.portaalerta.data.FollowupType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private data class MonthStats(
    val reminders: Int,
    val contacted: Int,
    val closed: Int,
    val closedPortability: Int,
    val closedInsurance: Int,
    val perWeek: List<Int>,
    val unattended: Int,
) {
    val rate: Int get() = if (contacted == 0) 0 else closed * 100 / contacted
}

private fun computeStats(all: List<Followup>, ym: YearMonth): MonthStats {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now().toEpochDay()
    fun doneDate(f: Followup): LocalDate? = f.doneAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    fun dueInMonth(f: Followup) = YearMonth.from(LocalDate.ofEpochDay(f.dueDate)) == ym

    val contacted = all.filter { f ->
        f.status == FollowupStatus.DONE && f.outcome != null && doneDate(f)?.let { YearMonth.from(it) == ym } == true
    }
    val closed = contacted.filter { it.outcome == FollowupOutcome.CLOSED }
    val weeks = (ym.lengthOfMonth() + 6) / 7
    val perWeek = MutableList(weeks) { 0 }
    closed.forEach { f ->
        val day = doneDate(f)?.dayOfMonth ?: return@forEach
        perWeek[(day - 1) / 7] += 1
    }
    return MonthStats(
        reminders = all.count { dueInMonth(it) },
        contacted = contacted.size,
        closed = closed.size,
        closedPortability = closed.count { it.type == FollowupType.PORTABILITY },
        closedInsurance = closed.count { it.type == FollowupType.INSURANCE },
        perWeek = perWeek,
        unattended = all.count { it.status == FollowupStatus.OPEN && dueInMonth(it) && it.dueDate <= today },
    )
}

@Composable
fun SummaryTab(vm: MainViewModel, padding: PaddingValues) {
    val all by vm.allFollowups.collectAsState()
    var offset by vm::summaryOffset
    val ym = YearMonth.now().plusMonths(offset.toLong())
    val stats = remember(all, ym) { computeStats(all, ym) }
    val goal = vm.settings.monthlyGoal
    val cs = MaterialTheme.colorScheme
    val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale("es", "CL")).replaceFirstChar { it.uppercase() }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$monthName ${ym.year}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { offset -= 1 }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Mes anterior", modifier = Modifier.rotate(90f))
            }
            IconButton(onClick = { offset += 1 }, enabled = offset < 0) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Mes siguiente", modifier = Modifier.rotate(-90f))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = cs.primary, contentColor = cs.onPrimary)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("META DEL MES", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${stats.closed}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("de $goal cierres", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                }
                LinearProgressIndicator(
                    progress = { (stats.closed.toFloat() / goal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = cs.onPrimary,
                    trackColor = cs.onPrimary.copy(alpha = 0.3f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("${stats.reminders}", "recordatorios", Modifier.weight(1f))
            StatCard("${stats.contacted}", "contactados", Modifier.weight(1f))
            StatCard("${stats.rate}%", "cierre", Modifier.weight(1f))
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("CIERRES POR SEMANA", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                val max = (stats.perWeek.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(
                    Modifier.fillMaxWidth().height(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    stats.perWeek.forEachIndexed { i, n ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.fillMaxWidth()
                                    .height(if (n == 0) 4.dp else (n * 70 / max).coerceAtLeast(8).dp)
                                    .background(
                                        if (n == 0) cs.surfaceVariant else cs.primary,
                                        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                                    ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("S${i + 1}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${stats.closedPortability} portabilidades", style = MaterialTheme.typography.bodyMedium)
                    Text("${stats.closedInsurance} seguros", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (stats.unattended > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer)) {
                Text(
                    "${stats.unattended} clientes sin contactar este mes",
                    Modifier.padding(16.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(
            "Los cierres se cuentan cuando marcas “Hecho” y eliges “Cerrado”.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
