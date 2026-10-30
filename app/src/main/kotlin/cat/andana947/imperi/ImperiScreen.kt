package cat.andana947.imperi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.data.joc.Resultat
import cat.andana947.joc.trams.Mida
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.Carregant
import cat.andana947.ui.components.PantallaBuida
import cat.andana947.ui.components.Targeta
import cat.andana947.ui.components.monedes
import cat.andana947.ui.components.ritme
import cat.andana947.ui.theme.Colors

/**
 * «El meu imperi» (GDD 4.0): el saldo, el que guanya el jugador en total i els seus trams. Cada tram es millora
 * afegint-hi una andana, fins a totes les parades que té.
 */
@Composable
fun ImperiScreen(viewModel: ImperiViewModel = hiltViewModel()) {
    val trams by viewModel.trams.collectAsStateWithLifecycle()
    val partida by viewModel.partida.collectAsStateWithLifecycle()
    val senseConnexio by viewModel.senseConnexio.collectAsStateWithLifecycle()
    val ocupat by viewModel.ocupat.collectAsStateWithLifecycle()
    val resultat by viewModel.resultat.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.actualitza() }

    val propis = trams
    when {
        propis == null && senseConnexio -> PantallaBuida(
            titol = stringResource(R.string.imperi_titol),
            text = stringResource(R.string.tram_sense_connexio),
            pista = stringResource(R.string.imperi_pista),
        )
        propis == null -> Carregant(Modifier.fillMaxSize())
        propis.isEmpty() -> PantallaBuida(
            titol = stringResource(R.string.imperi_titol),
            text = stringResource(R.string.imperi_buit),
            pista = stringResource(R.string.imperi_pista),
        )
        else -> LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.imperi_titol), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
                    partida?.let { Text(stringResource(R.string.compra_saldo, monedes(it.saldo)), style = MaterialTheme.typography.titleMedium) }
                    Text(
                        stringResource(R.string.imperi_guany_total, ritme(propis.sumOf { it.monedesPerMinut })),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(propis, key = { it.tram.id }) { t ->
                TargetaTram(
                    propi = t,
                    millorant = ocupat == t.tram.id,
                    resultat = resultat?.takeIf { it.first == t.tram.id }?.second,
                    onMillora = { viewModel.millora(t.tram.id) },
                )
            }
        }
    }
}

@Composable
private fun TargetaTram(propi: TramPropi, millorant: Boolean, resultat: Resultat?, onMillora: () -> Unit) {
    val tram = propi.tram
    Targeta(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (tram.codi == tram.nom) tram.nom else stringResource(R.string.tram_codi_i_nom, tram.codi, tram.nom),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(if (tram.midaTram == Mida.PETIT) R.string.tram_petit else R.string.tram_mitja),
                style = MaterialTheme.typography.bodyMedium,
                color = Colors.TextSecundari,
            )
            Text(stringResource(R.string.imperi_andanes, propi.andanes, tram.parades), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { propi.andanes.toFloat() / tram.parades },
                modifier = Modifier.fillMaxWidth(),
                color = Colors.Fort,
                trackColor = Colors.Superficie2,
                strokeCap = StrokeCap.Butt,
                drawStopIndicator = {},
            )
            Text(stringResource(R.string.compra_guany, ritme(propi.monedesPerMinut)), style = MaterialTheme.typography.bodyMedium)
            val cost = propi.costMillora
            val millorat = propi.monedesPerMinutMillorat
            if (cost == null || millorat == null) {
                Text(stringResource(R.string.imperi_complet), style = MaterialTheme.typography.labelLarge)
            } else {
                BotoPrincipal(
                    stringResource(R.string.imperi_millora, monedes(cost.toLong())),
                    onMillora,
                    enabled = propi.esPotPermetre,
                    carregant = millorant,
                )
                Text(
                    stringResource(R.string.imperi_millora_guany, ritme(millorat)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.TextSecundari,
                )
            }
            val missatge = when (resultat) {
                null -> null
                Resultat.MILLORAT -> R.string.imperi_millorat
                Resultat.SENSE_MONEDES -> R.string.tram_sense_monedes
                Resultat.SENSE_CONNEXIO -> R.string.tram_sense_connexio
                Resultat.MAXIM -> R.string.imperi_complet
                else -> R.string.tram_no_es_pot
            }
            missatge?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = Colors.FortText) }
        }
    }
}
