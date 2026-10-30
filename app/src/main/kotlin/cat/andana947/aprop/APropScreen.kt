package cat.andana947.aprop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cat.andana947.R
import cat.andana947.data.joc.NodeAProp
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.DialegResultat
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun APropScreen(
    viewModel: APropViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Colors.Fons),
    ) {
        // Capçalera
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.a_prop_titol),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = Archivo,
                    fontWeight = FontWeight.Black,
                    color = Colors.Text,
                ),
            )
            IconButton(onClick = { viewModel.refresca() }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.torna_a_provar),
                    tint = Colors.Text,
                )
            }
        }

        if (state.carregant && state.nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Colors.Fort)
            }
        } else if (state.error != null && state.nodes.isEmpty()) {
            // Abans un error del servidor es mostrava com si no hi hagués cap parada a prop.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.a_prop_error),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Colors.Text,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.TextSecundari,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                BotoSecundari(
                    text = stringResource(R.string.torna_a_provar),
                    onClick = { viewModel.refresca() },
                )
            }
        } else if (state.nodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.a_prop_sense_parades),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Colors.TextSecundari,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.nodes, key = { it.id }) { node ->
                    TargetaNode(
                        node = node,
                        onCheckinClick = { viewModel.ferCheckin(node) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Diàleg de resultat de check-in
    state.resultatCheckin?.let { resultat -> DialegResultat(resultat, onTanca = viewModel::tancaResultat) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetaNode(
    node: NodeAProp,
    onCheckinClick: () -> Unit,
) {
    val ombra = 4.dp
    val tinta = Colors.Text

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = ombra, bottom = ombra)
            .drawBehind {
                val d = ombra.toPx()
                drawRect(tinta, topLeft = Offset(d, d), size = size)
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Colors.Superficie)
                .border(2.dp, Colors.Text)
                .padding(16.dp),
        ) {
            // Fila superior: Mode, Nom, Distància
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = iconaMode(node.mode),
                        contentDescription = node.mode,
                        tint = Colors.Text,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = node.nom,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Colors.Text,
                        ),
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Colors.Superficie2)
                        .border(1.dp, Colors.Linia)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${node.distanciaMetres.toInt()} m",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Colors.Text,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Línies
            if (node.linies.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    node.linies.forEach { linia ->
                        Box(
                            modifier = Modifier
                                .background(Colors.Superficie2)
                                .border(1.dp, Colors.Linia)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = linia,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Colors.Text,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Dades d'afluència i economia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ca-ES"))
                val viatgersFmt = nf.format(node.viatgersFeiner.toInt())

                Column {
                    Text(
                        text = "≈$viatgersFmt viatgers/dia",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.TextSecundari,
                    )
                    if (node.fontAfluencia == "estimat") {
                        Text(
                            text = "(estimat)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Colors.TextSecundari,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "+${String.format(Locale.ROOT, "%.2f", node.ingresDiari)} €/dia",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Colors.Text,
                    )
                    Text(
                        text = "Preu: ${String.format(Locale.ROOT, "%.2f", node.preu)} €",
                        style = MaterialTheme.typography.bodySmall,
                        color = Colors.TextSecundari,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Estat actual i botó d'acció
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Estat de la parada per al jugador
                val granEstacio = node.categoria == "gran_estacio"
                val etiqueta = when {
                    node.presencial && node.diesRestants != null -> stringResource(R.string.a_prop_concessio_dies, node.diesRestants!!)
                    node.regal -> stringResource(R.string.a_prop_regal)
                    node.teConcessio -> stringResource(R.string.a_prop_teva)
                    granEstacio && node.accionsJugador > 0 -> stringResource(R.string.a_prop_accions_posseides, node.accionsJugador)
                    granEstacio -> stringResource(R.string.a_prop_accions_disponibles, node.accionsDisponibles)
                    else -> null
                }
                if (etiqueta != null) {
                    val meva = node.teConcessio || node.accionsJugador > 0
                    Box(
                        modifier = Modifier
                            .background(if (meva) Colors.Fort else Colors.Superficie2)
                            .border(1.dp, if (meva) Colors.Fort else Colors.Linia)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = etiqueta,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (meva) Colors.TintaFort else Colors.Text,
                        )
                    }
                } else {
                    Box(Modifier.width(1.dp))
                }

                // Botó d'acció: el check-in dona la concessió física fins al final de la temporada (×1,5).
                val preu = String.format(Locale.ROOT, "%.2f", node.preu)
                val textBoto = when {
                    node.presencial -> stringResource(R.string.a_prop_boto_vigent)
                    granEstacio && node.accionsJugador > 0 -> stringResource(R.string.a_prop_boto_visita_x15)
                    granEstacio -> stringResource(R.string.a_prop_boto_compra_accions_a_compra)
                    node.teConcessio -> stringResource(R.string.a_prop_boto_checkin_x15)
                    else -> stringResource(R.string.a_prop_boto_compra_checkin, preu)
                }
                val esActivable = !node.presencial && !(granEstacio && node.accionsJugador == 0)

                BotoPrincipal(
                    text = textBoto,
                    onClick = onCheckinClick,
                    enabled = esActivable,
                    modifier = Modifier.width(170.dp),
                )
            }
        }
    }
}

private fun iconaMode(mode: String): ImageVector = when (mode) {
    "metro" -> Icons.Default.DirectionsSubway
    "rodalies", "regional" -> Icons.Default.DirectionsTransit
    "fgc" -> Icons.Default.DirectionsSubway
    "tram" -> Icons.Default.Tram
    else -> Icons.Default.DirectionsBus
}
