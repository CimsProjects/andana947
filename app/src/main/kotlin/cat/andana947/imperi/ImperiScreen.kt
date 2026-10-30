package cat.andana947.imperi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cat.andana947.R
import cat.andana947.data.joc.AccioEstacio
import cat.andana947.data.joc.ConcessioActiva
import cat.andana947.data.joc.ConcessioCaducada
import cat.andana947.data.joc.Moviment
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import java.util.Locale

@Composable
fun ImperiScreen(
    viewModel: ImperiViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Recarrega l'imperi cada cop que l'usuari torna a aquesta pestanya.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresca()
        }
    }

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
                text = stringResource(R.string.imperi_titol),
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

        if (state.carregant && state.imperi == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Colors.Fort)
            }
        } else {
            val imperi = state.imperi
            if (imperi != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Targeta Resum Saldo i Nivell
                    item {
                        TargetaResum(
                            saldo = imperi.saldo,
                            prestigi = imperi.prestigi,
                            nivell = imperi.nivell,
                            maximConcessions = imperi.maximConcessions,
                            concessionsActives = imperi.concessionsActives.size,
                            ingresPrevist = imperi.ingresEstimatDia,
                        )
                    }

                    // Pròxim cobrament (compte enrere)
                    state.cobraments?.let { cobraments ->
                        item(key = "cobrament") {
                            TargetaCobrament(
                                estat = cobraments,
                                rebutEl = state.cobramentsRebutsEl,
                                onCobrat = { viewModel.refresca() },
                            )
                        }
                    }

                    // Concessions Actives
                    item {
                        Text(
                            text = stringResource(R.string.imperi_seccio_concessions),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Colors.TextSecundari,
                                letterSpacing = 1.sp,
                            ),
                        )
                    }

                    if (imperi.concessionsActives.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.imperi_sense_concessions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Colors.TextSecundari,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(imperi.concessionsActives, key = { it.nodeId }) { conc ->
                            ItemConcessio(conc = conc)
                        }
                    }

                    // Concessions Caducades
                    if (imperi.concessionsCaducades.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.imperi_seccio_caducades),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Colors.Error,
                                    letterSpacing = 1.sp,
                                ),
                            )
                        }
                        items(imperi.concessionsCaducades, key = { "caducada_${it.nodeId}" }) { cad ->
                            ItemConcessioCaducada(cad = cad)
                        }
                    }

                    // Accions
                    item {
                        Text(
                            text = stringResource(R.string.imperi_seccio_accions),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Colors.TextSecundari,
                                letterSpacing = 1.sp,
                            ),
                        )
                    }

                    if (imperi.accions.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.imperi_sense_accions),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Colors.TextSecundari,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(imperi.accions, key = { "accio_${it.nodeId}" }) { acc ->
                            ItemAccio(acc = acc)
                        }
                    }

                    // Darrers Moviments
                    item {
                        Text(
                            text = stringResource(R.string.imperi_seccio_moviments),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Colors.TextSecundari,
                                letterSpacing = 1.sp,
                            ),
                        )
                    }

                    if (imperi.moviments.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.imperi_sense_moviments),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Colors.TextSecundari,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        items(imperi.moviments, key = { it.id }) { mov ->
                            ItemMoviment(mov = mov)
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetaResum(
    saldo: Double,
    prestigi: Int,
    nivell: Int,
    maximConcessions: Int,
    concessionsActives: Int,
    ingresPrevist: Double,
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
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.imperi_saldo),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Colors.TextSecundari,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.a_prop_preu, euros(saldo)),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = Archivo,
                            fontWeight = FontWeight.Black,
                            color = Colors.Fort,
                        ),
                    )
                }

                Box(
                    modifier = Modifier
                        .background(Colors.Fort)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.imperi_nivell, nivell),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Colors.TintaFort,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.imperi_ingres_previst, euros(ingresPrevist)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Colors.Text,
                )
                Text(
                    text = stringResource(R.string.imperi_llicencia, concessionsActives, maximConcessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Colors.TextSecundari,
                )
            }
        }
    }
}

@Composable
private fun ItemConcessio(conc: ConcessioActiva) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Superficie)
            .border(1.5.dp, Colors.Text)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = iconaMode(conc.mode),
                    contentDescription = conc.mode,
                    tint = Colors.Text,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = conc.nom,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Colors.Text,
                    )
                    Text(
                        text = stringResource(R.string.imperi_dies_restants, conc.diesRestants),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (conc.diesRestants < 7) Colors.Error else Colors.TextSecundari,
                    )
                }
            }
            Text(
                text = stringResource(R.string.imperi_per_dia, euros(conc.ingresDiari)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Colors.Fort,
            )
        }
    }
}

@Composable
private fun ItemConcessioCaducada(cad: ConcessioCaducada) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Superficie2)
            .border(1.5.dp, Colors.Error)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = cad.nom,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Colors.Text,
                )
                Text(
                    text = stringResource(R.string.imperi_caducada_reactiva),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.Error,
                )
            }
        }
    }
}

@Composable
private fun ItemAccio(acc: AccioEstacio) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Superficie)
            .border(1.5.dp, Colors.Text)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = acc.nom,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Colors.Text,
                )
                Text(
                    text = stringResource(R.string.a_prop_accions_posseides, acc.quantitat),
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.TextSecundari,
                )
            }
            Text(
                text = stringResource(R.string.imperi_per_dia, euros(acc.dividendDiari)),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Colors.Text,
            )
        }
    }
}

@Composable
private fun ItemMoviment(mov: Moviment) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Colors.Superficie)
            .border(1.dp, Colors.Linia)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mov.concepte,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Colors.Text,
                )
            }
            val esPositiu = mov.import >= 0
            Text(
                text = "${if (esPositiu) "+" else ""}${String.format(Locale.ROOT, "%.2f", mov.import)} €",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (esPositiu) Colors.Fort else Colors.Text,
                ),
            )
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
