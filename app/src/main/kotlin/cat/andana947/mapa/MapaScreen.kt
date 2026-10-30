package cat.andana947.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cat.andana947.R
import cat.andana947.data.joc.Resultat
import cat.andana947.joc.circulacio.TipusVehicle
import cat.andana947.joc.trams.EstatTram
import cat.andana947.joc.trams.Mida
import cat.andana947.joc.trams.TramDelJoc
import cat.andana947.ui.components.BotoIcona
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.Obertura
import cat.andana947.ui.components.Targeta
import cat.andana947.ui.components.monedes
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.LocalReduirAnimacions
import kotlinx.coroutines.delay

/**
 * El mapa: la pantalla principal (GDD, secció 7.1). Els trams del joc (GDD 4.0), en gris, verd o blau, el saldo de
 * monedes i els trens i busos que circulen (docs/vehicles.md), que es poden amagar. Tocant un tram se n'obre la fitxa,
 * amb el botó de comprar. De moment el joc no fa servir la ubicació per a res (decidit el 29/10/2026).
 */
@Composable
fun MapaScreen(
    viewModel: MapaViewModel = hiltViewModel(),
    vehicles: VehiclesViewModel = hiltViewModel(),
    trams: TramsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val flota by vehicles.flota.collectAsStateWithLifecycle()
    val visibles by vehicles.visibles.collectAsStateWithLifecycle()
    val cameraInicial by viewModel.cameraInicial.collectAsStateWithLifecycle()
    val mapaTrams by trams.mapa.collectAsStateWithLifecycle()
    val estats by trams.estats.collectAsStateWithLifecycle()
    val partida by trams.partida.collectAsStateWithLifecycle()
    val senseConnexio by trams.senseConnexio.collectAsStateWithLifecycle()
    val guanyat by trams.guanyat.collectAsStateWithLifecycle()
    val seleccionat by trams.seleccionat.collectAsStateWithLifecycle()
    val comprant by trams.comprant.collectAsStateWithLifecycle()
    val resultat by trams.resultat.collectAsStateWithLifecycle()
    val fosc = isSystemInDarkTheme()
    val plantilla = remember { context.assets.open(EstilMapa.FITXER).bufferedReader().use { it.readText() } }
    val estil = remember(fosc) { EstilMapa.genera(plantilla, fosc) }

    // La partida (saldo i trams) es demana mentre es mira el mapa: és quan el servidor cobra les arribades.
    val cicle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cicle) { cicle.repeatOnLifecycle(Lifecycle.State.STARTED) { trams.segueixPartida() } }
    LaunchedEffect(guanyat) {
        if (guanyat > 0) {
            delay(DURADA_AVIS_GUANYAT_MS)
            trams.oblidaGuanyat()
        }
    }

    val camera = cameraInicial
    if (camera == null) {
        Obertura(Modifier.fillMaxSize())
        return
    }
    Box(Modifier.fillMaxSize()) {
        VistaMapa(
            estil = estil,
            cameraInicial = camera,
            posicio = null,
            centra = 0,
            animacions = !LocalReduirAnimacions.current,
            flota = flota,
            visibles = visibles,
            fosc = fosc,
            trams = mapaTrams,
            estats = estats,
            onCameraQuieta = viewModel::desaCamera,
            onTramTocat = trams::selecciona,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val saldo = partida?.saldo
            Etiqueta(
                when {
                    saldo != null -> monedes(saldo)
                    senseConnexio -> stringResource(R.string.mapa_sense_connexio)
                    else -> stringResource(R.string.mapa_carregant_partida)
                },
                fort = saldo != null,
            )
            if (guanyat > 0) Etiqueta(stringResource(R.string.mapa_guanyat, monedes(guanyat)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Interruptor(stringResource(R.string.mapa_mostra_trens), TipusVehicle.TREN in visibles) { vehicles.commuta(TipusVehicle.TREN) }
                Interruptor(stringResource(R.string.mapa_mostra_busos), TipusVehicle.BUS in visibles) { vehicles.commuta(TipusVehicle.BUS) }
            }
        }

        val tram = seleccionat
        val cataleg = mapaTrams?.cataleg
        if (tram != null && cataleg != null) {
            FitxaTram(
                tram = tram,
                recompensa = cataleg.recompensa(tram.midaTram),
                estat = estats[tram.id] ?: EstatTram.NO_ET_LA_POTS_PERMETRE,
                saldo = partida?.saldo,
                comprant = comprant,
                resultat = resultat,
                onCompra = { trams.compra(tram.id) },
                onTanca = { trams.selecciona(null) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            )
        }
    }
}

/** La fitxa d'un tram: què és, què dona i, si es pot, el botó de comprar-lo. */
@Composable
private fun FitxaTram(
    tram: TramDelJoc,
    recompensa: IntRange?,
    estat: EstatTram,
    saldo: Long?,
    comprant: Boolean,
    resultat: Resultat?,
    onCompra: () -> Unit,
    onTanca: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Targeta(modifier.ombraDura()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (tram.codi == tram.nom) tram.nom else stringResource(R.string.tram_codi_i_nom, tram.codi, tram.nom),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                BotoIcona(Icons.Filled.Close, stringResource(R.string.tram_tanca), onTanca)
            }
            Text(
                stringResource(
                    when (tram.midaTram) {
                        Mida.PETIT -> R.string.tram_petit
                        Mida.MITJA -> R.string.tram_mitja
                        Mida.GRAN -> R.string.tram_gran
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                pluralStringResource(R.plurals.tram_parades, tram.parades, tram.parades) + " · " +
                    pluralStringResource(R.plurals.tram_arribades, tram.arribades, tram.arribades),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (recompensa != null) {
                Text(
                    stringResource(R.string.tram_recompensa, recompensa.first, recompensa.last),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val preu = tram.preu
            when {
                estat == EstatTram.TEU -> Text(stringResource(R.string.tram_es_teu), style = MaterialTheme.typography.labelLarge)
                tram.midaTram == Mida.GRAN || preu == null ->
                    Text(stringResource(R.string.tram_gran_fraccions), style = MaterialTheme.typography.bodyMedium)
                else -> {
                    BotoPrincipal(
                        stringResource(R.string.tram_compra, monedes(preu.toLong())),
                        onCompra,
                        enabled = estat == EstatTram.ES_POT_COMPRAR,
                        carregant = comprant,
                    )
                    if (estat != EstatTram.ES_POT_COMPRAR && saldo != null) {
                        Text(
                            stringResource(R.string.tram_et_falten, monedes(preu - saldo)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Colors.TextSecundari,
                        )
                    }
                }
            }
            val missatge = when (resultat) {
                Resultat.COMPRAT -> R.string.tram_comprat
                Resultat.SENSE_MONEDES -> R.string.tram_sense_monedes
                Resultat.SENSE_CONNEXIO -> R.string.tram_sense_connexio
                Resultat.JA_ES_TEU, Resultat.GRAN, Resultat.NO_EXISTEIX, Resultat.NO_ES_TEU, Resultat.MAXIM,
                Resultat.MILLORAT,
                -> R.string.tram_no_es_pot
                null -> null
            }
            if (missatge != null) Text(stringResource(missatge), style = MaterialTheme.typography.labelLarge, color = Colors.FortText)
        }
    }
}

/** Quant de temps es veu l'avís de les monedes guanyades. */
private const val DURADA_AVIS_GUANYAT_MS = 6_000L

/** Un botó petit que mostra o amaga una capa del mapa: ple quan està encès, buit quan està apagat. */
@Composable
private fun Interruptor(text: String, ences: Boolean, onCanvi: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (ences) Colors.TintaFort else Colors.Text,
        modifier = Modifier
            .ombraDura(3.dp)
            .background(if (ences) Colors.Fort else Colors.Fons)
            .border(2.dp, Colors.Text)
            .toggleable(value = ences, role = Role.Switch, onValueChange = { onCanvi() })
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Una etiqueta petita sobre el mapa, amb vora de tinta; [fort], amb el color fort (el saldo). */
@Composable
private fun Etiqueta(text: String, modifier: Modifier = Modifier, fort: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (fort) Colors.TintaFort else Colors.Text,
        modifier = modifier
            .ombraDura(3.dp)
            .background(if (fort) Colors.Fort else Colors.Fons)
            .border(2.dp, Colors.Text)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
