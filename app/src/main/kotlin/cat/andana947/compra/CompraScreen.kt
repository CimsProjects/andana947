package cat.andana947.compra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cat.andana947.R
import cat.andana947.data.joc.AndanaCerca
import cat.andana947.imperi.euros
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.DialegResultat
import cat.andana947.ui.components.ombraDura
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import java.text.NumberFormat
import java.util.Locale

/** La pestanya Compra: buscar andanes (o veure les de prop) i comprar-les des de qualsevol lloc. */
@Composable
fun CompraScreen(viewModel: CompraViewModel = hiltViewModel()) {
    val estat by viewModel.estat.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(Colors.Fons)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.compra_titol),
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
        )
        Text(
            stringResource(R.string.compra_explicacio),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TextSecundari,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 10.dp),
        )
        OutlinedTextField(
            value = estat.text,
            onValueChange = viewModel::escriu,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.compra_cerca)) },
            shape = MaterialTheme.shapes.extraSmall,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Colors.Text, unfocusedBorderColor = Colors.Text),
        )
        Text(
            stringResource(if (estat.aProp) R.string.compra_a_prop else R.string.compra_resultats),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Colors.TextSecundari,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp, start = 4.dp),
        )
        when {
            estat.carregant && estat.andanes.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator(color = Colors.Fort)
            }
            estat.error != null && estat.andanes.isEmpty() -> Text(
                stringResource(R.string.compra_error, estat.error.orEmpty()),
                color = Colors.Error,
                style = MaterialTheme.typography.bodyMedium,
            )
            estat.andanes.isEmpty() -> Text(
                stringResource(if (estat.aProp) R.string.compra_sense_a_prop else R.string.compra_sense_resultats),
                color = Colors.TextSecundari,
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(estat.andanes, key = { it.id }) { andana ->
                    TargetaAndana(andana, comprant = estat.comprant == andana.id) { viewModel.compra(andana) }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    estat.resultat?.let { DialegResultat(it, onTanca = viewModel::tancaResultat) }
}

@Composable
private fun TargetaAndana(andana: AndanaCerca, comprant: Boolean, onCompra: () -> Unit) {
    val viatgers = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ca")).format(andana.viatgersFeiner)
    Column(
        Modifier
            .fillMaxWidth()
            .ombraDura(3.dp)
            .background(Colors.Superficie)
            .border(1.5.dp, Colors.Text)
            .padding(14.dp),
    ) {
        Text(
            andana.nom,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
            color = Colors.Text,
        )
        Text(
            listOfNotNull(andana.operador, andana.municipi, andana.linies.take(4).joinToString(" · ").ifEmpty { null })
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TextSecundari,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.a_prop_viatgers_dia, viatgers) +
                        if (andana.fontAfluencia == "estimat") " · " + stringResource(R.string.a_prop_estimat) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Colors.TextSecundari,
                )
                if (!andana.granEstacio) {
                    Text(
                        stringResource(R.string.a_prop_ingres_diari, euros(andana.ingresDiari)),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Colors.FortText,
                    )
                }
                andana.distanciaMetres?.let {
                    Text(stringResource(R.string.compra_distancia, it.toInt()), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
                }
            }
            Spacer(Modifier.width(10.dp))
            BotoPrincipal(
                text = when {
                    andana.teva && !andana.granEstacio -> stringResource(R.string.compra_teva)
                    andana.granEstacio -> stringResource(R.string.compra_accions, CompraViewModel.ACCIONS_PER_COMPRA, euros(andana.preu))
                    else -> stringResource(R.string.compra_boto, euros(andana.preu))
                },
                onClick = onCompra,
                enabled = !(andana.teva && !andana.granEstacio),
                carregant = comprant,
                modifier = Modifier.width(170.dp),
            )
        }
    }
}
