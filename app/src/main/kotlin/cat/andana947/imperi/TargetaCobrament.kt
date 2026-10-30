package cat.andana947.imperi

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cat.andana947.R
import cat.andana947.data.joc.EstatCobraments
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Quant s'espera, passada l'hora, abans de tornar a demanar l'estat (pg_cron pot trigar uns segons). */
private const val ESPERA_DESPRES_DEL_COBRAMENT_MS = 20_000L

/**
 * El pròxim cobrament: hora, import previst pel servidor i el temps que falta, amb una barra que s'omple des del darrer
 * cobrament. En arribar a zero, demana un estat nou amb [onCobrat].
 */
@Composable
fun TargetaCobrament(
    estat: EstatCobraments,
    rebutEl: Long,
    onCobrat: () -> Unit,
) {
    val rellotge = remember(estat, rebutEl) { RellotgeCobrament.de(estat, rebutEl) }
    var ara by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(rellotge) {
        while (true) {
            ara = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val restant = rellotge.restantMs(ara)
    val arribat = restant == 0L
    LaunchedEffect(rellotge, arribat) {
        if (arribat) {
            delay(ESPERA_DESPRES_DEL_COBRAMENT_MS)
            onCobrat()
        }
    }

    val progres by animateFloatAsState(rellotge.progres(ara), tween(900), label = "progrés del cobrament")
    val hora = remember(rellotge.properMs) {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(rellotge.properMs))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ombraDura()
            .background(Colors.Superficie)
            .border(2.dp, Colors.Text)
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.imperi_seccio_cobrament),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Colors.TextSecundari,
                letterSpacing = 1.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.imperi_cobrament_import, euros(estat.cobramentPrevist)),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = Archivo,
                    fontWeight = FontWeight.Black,
                    color = Colors.FortText,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.imperi_cobrament_hora, hora),
                style = MaterialTheme.typography.bodyMedium,
                color = Colors.TextSecundari,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        // Barra: vora de tinta i farciment del color fort.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Colors.Fons)
                .border(2.dp, Colors.Text),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progres)
                    .fillMaxHeight()
                    .background(Colors.Fort),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (arribat) stringResource(R.string.imperi_cobrament_arribant) else stringResource(R.string.imperi_cobrament_queden, durada(restant)),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Colors.Text,
        )
        if (estat.cobramentPrevist <= 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (estat.fraccioServei == 0.0) {
                    stringResource(R.string.imperi_cobrament_matinada)
                } else {
                    stringResource(R.string.imperi_cobrament_sense_ingressos)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Colors.TextSecundari,
            )
        }
    }
}

@Composable
private fun durada(ms: Long): String {
    val d = Durada.de(ms)
    return when {
        d.hores > 0 -> stringResource(R.string.durada_hores_minuts, d.hores, d.minuts)
        d.minuts > 0 -> stringResource(R.string.durada_minuts_segons, d.minuts, d.segons)
        else -> stringResource(R.string.durada_segons, d.segons)
    }
}

internal fun euros(valor: Double): String = String.format(Locale.ROOT, "%.2f", valor)
