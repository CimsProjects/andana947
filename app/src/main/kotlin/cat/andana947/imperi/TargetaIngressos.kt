package cat.andana947.imperi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.andana947.R
import cat.andana947.data.joc.EstatCobraments
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import java.util.Locale

/**
 * El que es cobra per cada arribada (GDD 4.8): el que s'ha cobrat avui, que puja amb cada tren o bus que arriba a les
 * andanes del jugador, i el ritme d'ara (per hora).
 */
@Composable
fun TargetaIngressos(estat: EstatCobraments) {
    // El número puja suaument cada cop que arriba un tren.
    val avui by animateFloatAsState(estat.avui.toFloat(), tween(700), label = "cobrat avui")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ombraDura()
            .background(Colors.Superficie)
            .border(2.dp, Colors.Text)
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.imperi_seccio_cobrat_avui),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Colors.TextSecundari,
                letterSpacing = 1.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.imperi_cobrament_import, euros(avui.toDouble())),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = Archivo,
                fontWeight = FontWeight.Black,
                color = Colors.FortText,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                estat.ingresHora > 0 -> stringResource(R.string.imperi_ritme, euros(estat.ingresHora)) + " · " +
                    pluralStringResource(R.plurals.imperi_arribades_hora, estat.arribadesHora, estat.arribadesHora)
                !estat.enServei -> stringResource(R.string.imperi_cobrament_matinada)
                else -> stringResource(R.string.imperi_cobrament_sense_ingressos)
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Colors.Text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.imperi_com_es_cobra),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TextSecundari,
        )
    }
}

internal fun euros(valor: Double): String = String.format(Locale.ROOT, "%.2f", valor)
