package cat.andana947.introduccio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cat.andana947.R
import cat.andana947.data.joc.AndanaRegal
import cat.andana947.data.joc.OpcioRegal
import cat.andana947.imperi.euros
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.contrast
import java.text.NumberFormat
import java.util.Locale

/** Triar les andanes de regal: una pestanya per província i, a cada una, les opcions del Director. */
@Composable
fun TriaRegal(
    seleccio: SeleccioRegal,
    avisMaxim: Boolean,
    desant: Boolean,
    error: String?,
    onCommuta: (String) -> Unit,
    onConfirma: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val provincies = seleccio.ofertes.provincies
    var triada by rememberSaveable { mutableIntStateOf(0) }
    val provincia = provincies[triada.coerceIn(provincies.indices)]
    val maxim = seleccio.ofertes.maximPerProvincia

    Column(modifier.padding(horizontal = 20.dp)) {
        Text(
            stringResource(R.string.regal_titol),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.regal_explicacio, maxim),
            style = MaterialTheme.typography.bodyMedium,
            color = Colors.TextSecundari,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            provincies.forEachIndexed { i, p ->
                val n = seleccio.andanes(p.provincia)
                Pestanya(stringResource(R.string.regal_pestanya, p.nom, n, maxim), i == triada, n > 0) { triada = i }
            }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(provincia.opcions, key = { it.id }) { opcio ->
                TargetaOpcio(
                    opcio = opcio,
                    triada = opcio.id in seleccio.triades,
                    disponible = seleccio.espot(opcio.id),
                    onClick = { onCommuta(opcio.id) },
                )
            }
        }

        val avis = when {
            error != null -> stringResource(R.string.introduccio_error, error)
            avisMaxim -> stringResource(R.string.regal_maxim, maxim)
            else -> null
        }
        avis?.let {
            Text(it, color = Colors.Error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        BotoPrincipal(
            text = if (seleccio.total == 0) {
                stringResource(R.string.regal_tria_alguna)
            } else {
                pluralStringResource(R.plurals.regal_confirma, seleccio.total, seleccio.total)
            },
            onClick = onConfirma,
            enabled = seleccio.total > 0,
            carregant = desant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

@Composable
private fun Pestanya(text: String, triada: Boolean, ambTria: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (triada) Colors.FortSuau else Colors.Fons)
            .border(if (triada) 2.dp else 1.5.dp, if (triada) Colors.Text else Colors.Linia)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = triada }
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (triada || ambTria) FontWeight.Bold else FontWeight.Medium,
            ),
            color = Colors.Text,
        )
    }
}

@Composable
private fun TargetaOpcio(opcio: OpcioRegal, triada: Boolean, disponible: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (disponible) 1f else 0.45f)
            .background(if (triada) Colors.FortSuau else Colors.Superficie)
            .border(if (triada) 3.dp else 1.5.dp, if (triada) Colors.Fort else Colors.Text)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = triada }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (opcio.esTram) {
                XipLinia(opcio.linia, opcio.color)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.regal_tram),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Colors.TextSecundari,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box(
                Modifier
                    .size(26.dp)
                    .background(if (triada) Colors.Fort else Colors.Fons)
                    .border(2.dp, Colors.Text),
                contentAlignment = Alignment.Center,
            ) {
                if (triada) Icon(Icons.Default.Check, contentDescription = null, tint = Colors.TintaFort, modifier = Modifier.size(18.dp))
            }
        }
        opcio.nodes.forEachIndexed { i, andana ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            FilaAndana(andana)
        }
    }
}

@Composable
private fun FilaAndana(andana: AndanaRegal) {
    val viatgers = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ca")).format(andana.viatgersFeiner)
    Column {
        Text(
            andana.nom,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
        )
        Text(
            stringResource(R.string.regal_lloc, nomMode(andana.mode), andana.municipi),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TextSecundari,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.a_prop_viatgers_dia, viatgers) +
                    if (andana.fontAfluencia == "estimat") " · " + stringResource(R.string.a_prop_estimat) else "",
                style = MaterialTheme.typography.bodySmall,
                color = Colors.TextSecundari,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.a_prop_ingres_diari, euros(andana.ingresDiari)),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Colors.FortText,
            )
        }
    }
}

/** El nom de la línia sobre el seu color (dada del GTFS), amb el text en blanc o negre segons el contrast. */
@Composable
private fun XipLinia(linia: String?, color: String?) {
    val fons = color?.takeIf { it.length == 6 }?.let { runCatching { Color(("FF$it").toLong(16)) }.getOrNull() } ?: Colors.Text
    val text = if (contrast(fons, Color.White) >= contrast(fons, Color.Black)) Color.White else Color.Black
    Box(Modifier.background(fons).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(
            linia ?: "",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
            color = text,
        )
    }
}

@Composable
private fun nomMode(mode: String): String = stringResource(
    when (mode) {
        "metro" -> R.string.mode_metro
        "rodalies" -> R.string.mode_rodalies
        "regional" -> R.string.mode_regional
        "fgc" -> R.string.mode_fgc
        "tram" -> R.string.mode_tram
        else -> R.string.mode_bus
    },
)
