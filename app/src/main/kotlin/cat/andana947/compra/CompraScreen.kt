package cat.andana947.compra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.data.joc.Resultat
import cat.andana947.joc.trams.Mida
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.Carregant
import cat.andana947.ui.components.Targeta
import cat.andana947.ui.components.monedes
import cat.andana947.ui.components.ritme
import cat.andana947.ui.theme.Colors

/**
 * La pàgina de compra (GDD 4.0): els trams petits i mitjans que el jugador encara no té, amb el preu (que dona la
 * primera andana) i el que en guanyaria. Al principi només hi ha monedes per a un tram petit.
 */
@Composable
fun CompraScreen(viewModel: CompraViewModel = hiltViewModel()) {
    val llista by viewModel.llista.collectAsStateWithLifecycle()
    val partida by viewModel.partida.collectAsStateWithLifecycle()
    val senseConnexio by viewModel.senseConnexio.collectAsStateWithLifecycle()
    val cerca by viewModel.cerca.collectAsStateWithLifecycle()
    val filtre by viewModel.filtre.collectAsStateWithLifecycle()
    val ocupat by viewModel.ocupat.collectAsStateWithLifecycle()
    val resultat by viewModel.resultat.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.actualitza() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.compra_titol), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
            Text(
                partida?.let { stringResource(R.string.compra_saldo, monedes(it.saldo)) }
                    ?: stringResource(if (senseConnexio) R.string.mapa_sense_connexio else R.string.mapa_carregant_partida),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.compra_explicacio), style = MaterialTheme.typography.bodyMedium, color = Colors.TextSecundari)
            OutlinedTextField(
                value = cerca,
                onValueChange = viewModel::cerca,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.compra_cerca)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Colors.FortText,
                    unfocusedBorderColor = Colors.Linia,
                    focusedContainerColor = Colors.Superficie,
                    unfocusedContainerColor = Colors.Superficie,
                    cursorColor = Colors.FortText,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Xip(stringResource(R.string.compra_filtre_tots), filtre == FiltreCompra.TOTS) { viewModel.filtra(FiltreCompra.TOTS) }
                Xip(stringResource(R.string.compra_filtre_assequibles), filtre == FiltreCompra.ASSEQUIBLES) {
                    viewModel.filtra(FiltreCompra.ASSEQUIBLES)
                }
            }
        }
        val trams = llista
        when {
            trams == null -> Carregant(Modifier.fillMaxSize())
            trams.isEmpty() -> Text(
                stringResource(R.string.compra_cap),
                style = MaterialTheme.typography.bodyLarge,
                color = Colors.TextSecundari,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(trams, key = { it.tram.id }) { t ->
                    FilaTram(
                        venda = t,
                        comprant = ocupat == t.tram.id,
                        resultat = resultat?.takeIf { it.first == t.tram.id }?.second,
                        onCompra = { viewModel.compra(t.tram.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaTram(venda: TramEnVenda, comprant: Boolean, resultat: Resultat?, onCompra: () -> Unit) {
    val tram = venda.tram
    Targeta(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(14.dp).background(colorLinia(tram.color)).border(1.dp, Colors.Text))
                Text(
                    if (tram.codi == tram.nom) tram.nom else stringResource(R.string.tram_codi_i_nom, tram.codi, tram.nom),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(if (tram.midaTram == Mida.PETIT) R.string.tram_petit else R.string.tram_mitja) + " · " +
                    pluralStringResource(R.plurals.tram_parades, tram.parades, tram.parades),
                style = MaterialTheme.typography.bodyMedium,
                color = Colors.TextSecundari,
            )
            Text(stringResource(R.string.compra_guany, ritme(venda.monedesPerMinut)), style = MaterialTheme.typography.bodyMedium)
            BotoPrincipal(
                stringResource(R.string.compra_boto, monedes(venda.preu.toLong())),
                onCompra,
                enabled = venda.esPotPermetre,
                carregant = comprant,
            )
            missatge(resultat)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Colors.FortText) }
        }
    }
}

/** Un botó de filtre: ple quan està triat. */
@Composable
private fun Xip(text: String, triat: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (triat) Colors.TintaFort else Colors.Text,
        modifier = Modifier
            .background(if (triat) Colors.Fort else Colors.Fons)
            .border(2.dp, Colors.Text)
            .selectable(selected = triat, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun missatge(resultat: Resultat?): String? = when (resultat) {
    null -> null
    Resultat.COMPRAT -> stringResource(R.string.compra_feta)
    Resultat.SENSE_MONEDES -> stringResource(R.string.tram_sense_monedes)
    Resultat.SENSE_CONNEXIO -> stringResource(R.string.tram_sense_connexio)
    else -> stringResource(R.string.tram_no_es_pot)
}

/** El color de la línia, o la tinta si no en té. */
private fun colorLinia(hex: String): Color =
    hex.toLongOrNull(16)?.takeIf { hex.length == 6 }?.let { Color(0xFF000000 or it) } ?: Colors.Text
