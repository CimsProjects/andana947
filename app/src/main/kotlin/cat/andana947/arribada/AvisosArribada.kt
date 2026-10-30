package cat.andana947.arribada

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cat.andana947.R
import cat.andana947.imperi.euros
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.contrast

/** Els avisos de les arribades, a dalt de la pantalla: entren des de dalt i se'n van sols. */
@Composable
fun AvisosArribada(avisos: List<AvisArribada>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (avis in avisos) {
            key(avis.clau) {
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    Avis(avis)
                }
            }
        }
    }
}

@Composable
private fun Avis(avis: AvisArribada) {
    Row(
        Modifier
            .fillMaxWidth()
            .ombraDura(3.dp)
            .background(Colors.Fons)
            .border(2.dp, Colors.Text)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XipLinia(avis.linia.codi, avis.linia.color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                avis.andana,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.avis_pujades, avis.pujades, avis.pujades),
                style = MaterialTheme.typography.bodySmall,
                color = Colors.TextSecundari,
            )
        }
        if (avis.valor >= 0.01) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(Colors.Fort).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.imperi_cobrament_import, euros(avis.valor)),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                    color = Colors.TintaFort,
                )
            }
        }
    }
}

@Composable
private fun XipLinia(codi: String, color: String) {
    val fons = runCatching { Color(("FF" + color.removePrefix("#")).toLong(16)) }.getOrDefault(Colors.Text)
    val text = if (contrast(fons, Color.White) >= contrast(fons, Color.Black)) Color.White else Color.Black
    Box(Modifier.background(fons).border(1.5.dp, Colors.Text).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(codi, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black), color = text, maxLines = 1)
    }
}
