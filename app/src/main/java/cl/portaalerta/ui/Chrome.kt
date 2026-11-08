package cl.portaalerta.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Color de las barras superiores: morado intenso en ambos temas, para que la barra de estado se integre. */
@Composable
fun headerContainer(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary

@Composable
fun onHeader(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = headerContainer(),
    scrolledContainerColor = headerContainer(),
    titleContentColor = onHeader(),
    navigationIconContentColor = onHeader(),
    actionIconContentColor = onHeader(),
)

/** Encabezado grande de la pantalla principal: título, subtítulo, acciones y un bloque opcional (contadores). */
@Composable
fun HomeHeader(
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        color = headerContainer(),
        contentColor = onHeader(),
        shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
    ) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subtitle, style = MaterialTheme.typography.labelLarge, color = onHeader().copy(alpha = 0.8f))
                    Text(title, style = MaterialTheme.typography.headlineLarge)
                }
                actions()
            }
            content()
        }
    }
}

/** Contador del encabezado. Con [alert] se resalta en rojo suave (para los atrasados). */
@Composable
fun HeaderStat(value: Int, label: String, modifier: Modifier = Modifier, alert: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (alert) cs.errorContainer else onHeader().copy(alpha = 0.16f),
        contentColor = if (alert) cs.onErrorContainer else onHeader(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun HeaderStats(overdue: Int, now: Int, upcoming: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderStat(overdue, "atrasados", Modifier.weight(1f), alert = overdue > 0)
        HeaderStat(now, "para ahora", Modifier.weight(1f))
        HeaderStat(upcoming, "próximos", Modifier.weight(1f))
    }
}
