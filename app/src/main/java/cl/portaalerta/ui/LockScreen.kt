package cl.portaalerta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pantalla de bloqueo: pide el PIN, patrón o huella del teléfono apenas aparece. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(headerContainer()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PortaAlerta", style = MaterialTheme.typography.headlineLarge, color = onHeader())
        Text(
            "Bloqueada para proteger a tus clientes",
            style = MaterialTheme.typography.bodyLarge,
            color = onHeader().copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )
        Button(
            onClick = onUnlock,
            colors = ButtonDefaults.buttonColors(containerColor = onHeader(), contentColor = headerContainer()),
        ) { Text("Desbloquear") }
    }
}
