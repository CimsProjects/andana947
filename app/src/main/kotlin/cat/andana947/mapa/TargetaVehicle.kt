package cat.andana947.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.andana947.R
import cat.andana947.joc.circulacio.FitxaVehicle
import cat.andana947.joc.circulacio.ParadaHora
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.contrast
import java.util.Locale

/** La fitxa del vehicle tocat: la línia, d'on ve, la pròxima parada i cap on va, amb les hores si se saben. */
@Composable
fun TargetaVehicle(fitxa: FitxaVehicle, onTanca: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .ombraDura()
            .background(Colors.Fons)
            .border(2.dp, Colors.Text)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            fitxa.linia?.let { XipLinia(it.codi, it.color) }
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (fitxa.real) R.string.vehicle_posicio_real else R.string.vehicle_posicio_estimada),
                style = MaterialTheme.typography.labelMedium,
                color = Colors.TextSecundari,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onTanca) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.vehicle_tanca), tint = Colors.Text)
            }
        }
        Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fitxa.origen?.let { Fila(stringResource(R.string.vehicle_ve_de), it) }
            when {
                fitxa.aturatA != null -> Fila(stringResource(R.string.vehicle_ara_a), fitxa.aturatA!!, destacada = true)
                fitxa.propera != null -> Fila(stringResource(R.string.vehicle_propera), fitxa.propera!!, destacada = true)
            }
            fitxa.desti?.let { Fila(stringResource(R.string.vehicle_va_a), it) }
            if (fitxa.origen == null && fitxa.desti == null) {
                Text(stringResource(R.string.vehicle_sense_horari), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
            }
        }
    }
}

@Composable
private fun Fila(etiqueta: String, parada: ParadaHora, destacada: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = Colors.TextSecundari)
            Text(
                parada.nom,
                style = if (destacada) {
                    MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black)
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                },
                color = Colors.Text,
            )
        }
        parada.segons?.let {
            Text(
                hora(it),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = if (destacada) Colors.FortText else Colors.Text,
            )
        }
    }
}

/** L'hora del dia; les del GTFS poden passar de les 24:00 (expedicions que acaben després de mitjanit). */
private fun hora(segons: Int): String {
    val s = ((segons % 86_400) + 86_400) % 86_400
    return String.format(Locale.ROOT, "%02d:%02d", s / 3600, s % 3600 / 60)
}

/** El codi de la línia sobre el seu color, amb el text en blanc o negre segons el contrast. */
@Composable
private fun XipLinia(codi: String, color: String) {
    val fons = runCatching { Color(("FF" + color.removePrefix("#")).toLong(16)) }.getOrDefault(Colors.Text)
    val text = if (contrast(fons, Color.White) >= contrast(fons, Color.Black)) Color.White else Color.Black
    Box(Modifier.background(fons).border(1.5.dp, Colors.Text).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(codi, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black), color = text)
    }
}
