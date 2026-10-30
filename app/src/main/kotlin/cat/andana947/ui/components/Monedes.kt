package cat.andana947.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cat.andana947.R
import java.text.NumberFormat
import java.util.Locale

/** «1 catalí», «500 catalins». */
@Composable
fun monedes(quantitat: Long): String =
    pluralStringResource(R.plurals.catalins, quantitat.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), quantitat)

/**
 * El ritme de guany d'un tram, en la unitat que es llegeix millor (GDD 4.0): «≈ 1,2 catalins/minut» si n'hi ha prou
 * per minut, i si no «≈ 3,4 catalins/hora». És una mitjana d'un feiner: qui sorteja i cobra és el servidor.
 */
@Composable
fun ritme(monedesPerMinut: Double): String {
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("ca")).apply { maximumFractionDigits = 1 }
    return if (monedesPerMinut >= 1.0) {
        stringResource(R.string.ritme_minut, format.format(monedesPerMinut))
    } else {
        stringResource(R.string.ritme_hora, format.format(monedesPerMinut * 60))
    }
}
