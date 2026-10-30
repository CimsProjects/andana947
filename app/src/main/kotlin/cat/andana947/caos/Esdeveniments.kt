package cat.andana947.caos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.andana947.R
import cat.andana947.data.joc.Esdeveniment
import cat.andana947.ui.theme.Colors
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Un esdeveniment del motor de caos (GDD 6): què passa, on, quant canvia l'ingrés i quantes andanes teves toca. */
@Composable
fun TargetaEsdeveniment(e: Esdeveniment, mostraAndanes: Boolean = true) {
    val baixa = e.multiplicador < 1.0
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (e.andanes.isNotEmpty()) Colors.FortSuau else Colors.Superficie)
            .border(1.5.dp, if (baixa) Colors.Error else Colors.Text)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (baixa) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = if (baixa) Colors.Error else Colors.FortText,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                nomEsdeveniment(e),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Colors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                efecte(e.multiplicador),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = if (baixa) Colors.Error else Colors.FortText,
            )
        }
        ambit(e)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari) }
        Text(fins(e), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
        if (mostraAndanes && e.andanes.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.caos_andanes_teves, e.andanes.size, e.andanes.size),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = Colors.Text,
            )
        }
    }
}

@Composable
private fun nomEsdeveniment(e: Esdeveniment): String = when (e.tipus) {
    "robatori_coure" -> stringResource(R.string.caos_robatori_coure)
    "animals_via" -> stringResource(R.string.caos_animals_via)
    "incidencia_costa" -> stringResource(R.string.caos_incidencia_costa)
    "avaria_infraestructura" -> stringResource(R.string.caos_avaria)
    "temporal" -> stringResource(R.string.caos_temporal)
    "manifestacio" -> stringResource(R.string.caos_manifestacio)
    else -> e.nom ?: stringResource(R.string.caos_esdeveniment)
}

/** On passa: les línies, la zona, el lloc o els municipis. */
@Composable
private fun ambit(e: Esdeveniment): String? = when {
    e.linies.isNotEmpty() -> stringResource(R.string.caos_linies, e.linies.distinct().joinToString(", "))
    e.aPropDe != null -> stringResource(R.string.caos_a_prop_de, e.aPropDe)
    e.tipus == "temporal" && e.nom != null -> e.nom
    e.municipis.isNotEmpty() -> e.municipis.joinToString(", ")
    else -> null
}

/** «−50 % de viatgers» o «+40 % de viatgers». */
@Composable
private fun efecte(multiplicador: Double): String {
    val percent = ((multiplicador - 1) * 100).roundToInt()
    return stringResource(R.string.caos_efecte, (if (percent >= 0) "+" else "−") + kotlin.math.abs(percent))
}

@Composable
private fun fins(e: Esdeveniment): String {
    val fi = runCatching { OffsetDateTime.parse(e.fi).atZoneSameInstant(ZoneId.of("Europe/Madrid")) }.getOrNull()
        ?: return ""
    val avui = OffsetDateTime.now().atZoneSameInstant(ZoneId.of("Europe/Madrid")).toLocalDate()
    // Els que s'acaben a la mitjanit d'avui duren tot el dia.
    return if (fi.toLocalDate() == avui.plusDays(1) && fi.toLocalTime().toSecondOfDay() == 0) {
        stringResource(R.string.caos_tot_el_dia)
    } else {
        stringResource(R.string.caos_fins, fi.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")))
    }
}
