package cat.andana947.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.ui.components.BotoIcona
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.Obertura
import cat.andana947.ui.components.Targeta
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.rememberObreConfiguracio
import cat.andana947.ui.rememberPermisUbicacio
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.LocalReduirAnimacions

/**
 * El mapa: la pantalla principal (GDD, secció 7.1). De moment, els carrers, on ets i els trens que circulen
 * (docs/vehicles.md); les parades arriben a la fase 4.
 */
@Composable
fun MapaScreen(viewModel: MapaViewModel = hiltViewModel(), vehicles: VehiclesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val flota by vehicles.flota.collectAsStateWithLifecycle()
    val permis by viewModel.permis.collectAsStateWithLifecycle()
    val posicio by viewModel.posicio.collectAsStateWithLifecycle()
    val cameraInicial by viewModel.cameraInicial.collectAsStateWithLifecycle()
    val centra by viewModel.centra.collectAsStateWithLifecycle()
    val fosc = isSystemInDarkTheme()
    val plantilla = remember { context.assets.open(EstilMapa.FITXER).bufferedReader().use { it.readText() } }
    val estil = remember(fosc) { EstilMapa.genera(plantilla, fosc) }

    val demanaPermis = rememberPermisUbicacio(viewModel::resultatPermis)
    val obreConfiguracio = rememberObreConfiguracio()
    // El permís es demana en obrir el mapa, que és quan cal, i no abans.
    LaunchedEffect(Unit) { if (permis == EstatPermis.PENDENT) demanaPermis() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.revisaPermis() }

    val camera = cameraInicial
    if (camera == null) {
        Obertura(Modifier.fillMaxSize())
        return
    }
    Box(Modifier.fillMaxSize()) {
        VistaMapa(
            estil = estil,
            cameraInicial = camera,
            posicio = posicio,
            centra = centra,
            animacions = !LocalReduirAnimacions.current,
            flota = flota,
            fosc = fosc,
            onCameraQuieta = viewModel::desaCamera,
            modifier = Modifier.fillMaxSize(),
        )

        if (permis == EstatPermis.CONCEDIT && posicio == null) {
            Etiqueta(
                stringResource(R.string.mapa_buscant),
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (posicio != null) {
                BotoIcona(Icons.Filled.MyLocation, stringResource(R.string.mapa_centra), viewModel::centraEnMi)
            }
            if (permis == EstatPermis.DENEGAT) {
                Targeta(Modifier.ombraDura().fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.mapa_sense_permis_titol), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.mapa_sense_permis_text), style = MaterialTheme.typography.bodyMedium)
                        BotoPrincipal(stringResource(R.string.mapa_activa_ubicacio), demanaPermis)
                        BotoSecundari(stringResource(R.string.mapa_obre_configuracio), obreConfiguracio)
                    }
                }
            }
        }
    }
}

/** Una etiqueta petita sobre el mapa, amb vora de tinta. */
@Composable
private fun Etiqueta(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier
            .ombraDura(3.dp)
            .background(Colors.Fons)
            .border(2.dp, Colors.Text)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
