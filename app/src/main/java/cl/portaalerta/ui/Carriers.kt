package cl.portaalerta.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Carrier(val key: String, val name: String, val color: Color, val textColor: Color = Color.White)

/**
 * Compañías móviles de Chile. Las 4 con red propia (Entel, Movistar, Claro, WOM) más las virtuales más comunes.
 * Cualquier otra se escribe con "Otra".
 *
 * LOGOS: cada insignia usa los colores de la marca como base. Para poner el logo oficial, copia la imagen
 * como app/src/main/res/drawable-nodpi/logo_<clave>.png (por ejemplo logo_entel.png) y la app la usa sola.
 */
object Carriers {
    const val OTHER = "Otra"

    val all = listOf(
        Carrier("entel", "Entel", Color(0xFFF26522)),
        Carrier("movistar", "Movistar", Color(0xFF0B8AD9)),
        Carrier("claro", "Claro", Color(0xFFD62A1F)),
        Carrier("wom", "WOM", Color(0xFF5B2A86)),
        Carrier("virgin", "Virgin", Color(0xFFC8102E)),
        Carrier("mundo", "Mundo", Color(0xFF00897B)),
        Carrier("vtr", "VTR", Color(0xFFFFC72C), textColor = Color(0xFF1E1530)),
        Carrier("simple", "Simple", Color(0xFF3949AB)),
    )

    fun find(name: String): Carrier? = all.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

/** Insignia de la compañía: logo oficial si existe en los recursos; si no, color de marca con su nombre. */
@Composable
fun CarrierBadge(name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val carrier = Carriers.find(name)
    val shape = RoundedCornerShape(size * 0.28f)
    val logoRes = remember(name) {
        carrier?.let { ctx.resources.getIdentifier("logo_${it.key}", "drawable", ctx.packageName) } ?: 0
    }
    if (logoRes != 0) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(shape),
        )
        return
    }
    val bg = carrier?.color ?: MaterialTheme.colorScheme.surfaceVariant
    val fg = carrier?.textColor ?: MaterialTheme.colorScheme.onSurfaceVariant
    val label = when {
        carrier != null -> carrier.name.lowercase()
        name.isBlank() || name == Carriers.OTHER -> "+"
        else -> name.trim().take(2).uppercase()
    }
    val factor = if (label.length > 6) 0.17f else if (label.length > 4) 0.22f else 0.28f
    Box(modifier.size(size).background(bg, shape), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * factor).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Cuadrícula de compañías para elegir con un toque. */
@Composable
fun CarrierGrid(selected: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val items = Carriers.all.map { it.name } + Carriers.OTHER
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { name ->
                    val isSelected = selected.equals(name, ignoreCase = true)
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onPick(name) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CarrierBadge(name, 64.dp)
                            Text(
                                name,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
