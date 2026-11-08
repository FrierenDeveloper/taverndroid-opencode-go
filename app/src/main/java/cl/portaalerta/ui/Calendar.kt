package cl.portaalerta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.portaalerta.AgendaItem
import cl.portaalerta.MainViewModel
import cl.portaalerta.Screen
import cl.portaalerta.data.Dates
import cl.portaalerta.data.Followup
import cl.portaalerta.data.FollowupStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class DayState { NONE, OVERDUE, NOW, UPCOMING, DONE }

private fun dayState(list: List<AgendaItem>, today: Long): DayState {
    if (list.isEmpty()) return DayState.NONE
    val open = list.filter { it.followup.status == FollowupStatus.OPEN }
    if (open.isEmpty()) return DayState.DONE
    return when {
        open.any { it.followup.dueDate < today } -> DayState.OVERDUE
        open.any { it.followup.dueDate - it.followup.leadDays <= today } -> DayState.NOW
        else -> DayState.UPCOMING
    }
}

/** Calendario mensual: cada día toma el color del recordatorio más urgente (igual que la agenda). */
@Composable
fun CalendarView(vm: MainViewModel, onDone: (Followup) -> Unit) {
    val items by vm.allItems.collectAsState()
    val cs = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }

    val ym = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val byDay = remember(items) { items.groupBy { it.followup.dueDate } }
    val first = ym.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale("es", "CL"))
        .replaceFirstChar { it.uppercase() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$monthName ${ym.year}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { monthOffset -= 1 }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Mes anterior", modifier = Modifier.rotate(90f))
            }
            IconButton(onClick = { monthOffset += 1 }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Mes siguiente", modifier = Modifier.rotate(-90f))
            }
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        repeat(6) { week ->
            Row(Modifier.padding(horizontal = 16.dp)) {
                repeat(7) { dow ->
                    val date = start.plusDays((week * 7 + dow).toLong())
                    val list = byDay[date.toEpochDay()].orEmpty()
                    DayCell(
                        day = date.dayOfMonth,
                        inMonth = YearMonth.from(date) == ym,
                        isToday = date == today,
                        isSelected = date.toEpochDay() == selected,
                        state = dayState(list, today.toEpochDay()),
                        count = list.size,
                        onClick = { selected = date.toEpochDay() },
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Atrasado", cs.errorContainer)
                Legend("Para contactar", cs.primaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Próximo", cs.surfaceVariant)
                Legend("Completado", cs.surfaceVariant.copy(alpha = 0.5f))
            }
        }

        val day = LocalDate.ofEpochDay(selected)
        val dayItems = byDay[selected].orEmpty()
        val dayName = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "CL")).replaceFirstChar { it.uppercase() }
        val monthShort = day.month.getDisplayName(TextStyle.FULL, Locale("es", "CL"))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "$dayName ${day.dayOfMonth} de $monthShort".uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = cs.primary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            if (dayItems.isEmpty()) {
                Text(
                    "Sin recordatorios este día.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            dayItems.forEach { item ->
                FollowupCard(
                    f = item.followup,
                    client = item.client,
                    settings = vm.settings,
                    onClick = { vm.push(Screen.ClientDetail(item.client.id)) },
                    onDone = { onDone(item.followup) },
                )
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(14.dp)
                .background(color, RoundedCornerShape(5.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.DayCell(
    day: Int,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    state: DayState,
    count: Int,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    val (bg, fg) = when (state) {
        DayState.OVERDUE -> cs.errorContainer to cs.onErrorContainer
        DayState.NOW -> cs.primaryContainer to cs.onPrimaryContainer
        DayState.UPCOMING -> cs.surfaceVariant to cs.onSurfaceVariant
        DayState.DONE -> cs.surfaceVariant.copy(alpha = 0.5f) to cs.onSurfaceVariant
        DayState.NONE -> Color.Transparent to (if (inMonth) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.6f))
    }
    Box(
        Modifier.weight(1f).height(52.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.padding(top = 4.dp).size(42.dp).background(bg, shape)
                .then(
                    when {
                        isSelected -> Modifier.border(2.dp, cs.primary, shape)
                        isToday -> Modifier.border(2.dp, cs.onSurface, shape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$day",
                color = fg,
                fontWeight = if (state == DayState.NONE) FontWeight.Medium else FontWeight.Bold,
                textDecoration = if (state == DayState.DONE) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (count > 1) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp).background(cs.onSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("$count", color = cs.surface, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
