package cat.andana947.flota

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cat.andana947.R
import cat.andana947.data.joc.LiniaFlota
import cat.andana947.data.joc.RepositoriXarxa
import cat.andana947.data.joc.ResultatCheckinRpc
import cat.andana947.data.joc.ServeiJoc
import cat.andana947.imperi.euros
import cat.andana947.ui.components.BotoPrincipal
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.DialegResultat
import cat.andana947.ui.theme.Archivo
import cat.andana947.ui.theme.Colors
import cat.andana947.ui.theme.contrast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

data class FlotaUiState(
    val carregant: Boolean = true,
    val linies: List<LiniaFlota> = emptyList(),
    /** La línia on s'està llogant o tornant un vehicle. */
    val ocupada: String? = null,
    val resultat: ResultatCheckinRpc? = null,
    val error: String? = null,
)

/** La flota del jugador (fase 5, GDD 4.7): vehicles llogats per línia. Tot ho calcula el servidor. */
@HiltViewModel
class FlotaViewModel @Inject constructor(
    private val joc: ServeiJoc,
    private val xarxa: RepositoriXarxa,
) : ViewModel() {
    private val _estat = MutableStateFlow(FlotaUiState())
    val estat: StateFlow<FlotaUiState> = _estat.asStateFlow()

    fun carrega() {
        viewModelScope.launch {
            joc.estatFlota()
                .onSuccess { l -> _estat.update { it.copy(carregant = false, linies = l, error = null) } }
                .onFailure { err -> _estat.update { it.copy(carregant = false, error = err.message) } }
        }
    }

    fun lloga(linia: String) = accio(linia) { joc.llogaVehicles(linia, 1) }

    fun torna(linia: String) = accio(linia) { joc.retornaVehicles(linia, 1) }

    fun tancaResultat() = _estat.update { it.copy(resultat = null) }

    private fun accio(linia: String, crida: suspend () -> Result<ResultatCheckinRpc>) {
        if (_estat.value.ocupada != null) return
        _estat.update { it.copy(ocupada = linia) }
        viewModelScope.launch {
            crida()
                .onSuccess { r ->
                    _estat.update { it.copy(ocupada = null, resultat = r) }
                    carrega()
                    // Els vehicles del mapa canvien de color.
                    xarxa.actualitza()
                }
                .onFailure { err -> _estat.update { it.copy(ocupada = null, error = err.message) } }
        }
    }
}

/** La flota: les línies on el jugador té andanes, amb els vehicles que hi pot llogar i el que dona cada un. */
@Composable
fun FlotaScreen(onTorna: () -> Unit, viewModel: FlotaViewModel = hiltViewModel()) {
    val ui by viewModel.estat.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.carrega() }

    Column(Modifier.fillMaxSize().background(Colors.Fons).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onTorna) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.avatar_torna), tint = Colors.Text)
            }
            Text(
                stringResource(R.string.flota_titol),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.Text,
            )
        }
        when {
            ui.carregant -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Colors.Fort) }
            else -> LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(stringResource(R.string.flota_explicacio), style = MaterialTheme.typography.bodyMedium, color = Colors.TextSecundari)
                    ui.error?.let { Text(stringResource(R.string.compra_error, it), color = Colors.Error) }
                }
                if (ui.linies.isEmpty()) {
                    item { Text(stringResource(R.string.flota_buida), color = Colors.TextSecundari) }
                }
                items(ui.linies, key = { it.linia }) { l ->
                    TargetaLinia(l, ocupada = ui.ocupada == l.linia, onLloga = { viewModel.lloga(l.linia) }, onTorna = { viewModel.torna(l.linia) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    ui.resultat?.let { DialegResultat(it, onTanca = viewModel::tancaResultat) }
}

@Composable
private fun TargetaLinia(l: LiniaFlota, ocupada: Boolean, onLloga: () -> Unit, onTorna: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Colors.Superficie).border(1.5.dp, Colors.Text).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XipLinia(l.codi, l.color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.flota_andanes, l.andanes, l.nodes),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Colors.Text,
                )
                Text(stringResource(R.string.flota_punta, l.vehiclesPunta), style = MaterialTheme.typography.bodySmall, color = Colors.TextSecundari)
            }
            Text(
                stringResource(R.string.flota_tens, l.vehicles, l.maxim),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = Archivo, fontWeight = FontWeight.Black),
                color = Colors.FortText,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(l.model, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Colors.Text)
        Text(
            stringResource(R.string.flota_preu_real, milers(l.preuModel)),
            style = MaterialTheme.typography.bodySmall,
            color = Colors.TextSecundari,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.flota_economia, euros(l.ingresDia), euros(l.costDia), signe(l.netDia)),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (l.netDia < 0) Colors.Error else Colors.Text,
        )
        if (l.netDia < 0) {
            Text(stringResource(R.string.flota_perd), style = MaterialTheme.typography.bodySmall, color = Colors.Error)
        }
        if (l.factorCaos != 1.0) {
            Text(
                stringResource(if (l.factorCaos < 1.0) R.string.flota_caos_baixa else R.string.flota_caos_puja),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = if (l.factorCaos < 1.0) Colors.Error else Colors.FortText,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotoPrincipal(
                stringResource(R.string.flota_lloga, euros(l.fiancaVehicle)),
                onClick = onLloga,
                modifier = Modifier.weight(1f),
                enabled = l.vehicles < l.maxim,
                carregant = ocupada,
            )
            if (l.vehicles > 0) {
                BotoSecundari(stringResource(R.string.flota_torna), onTorna, Modifier.weight(0.6f), enabled = !ocupada)
            }
        }
    }
}

@Composable
private fun XipLinia(codi: String, color: String?) {
    val fons = runCatching { Color(("FF" + color.orEmpty().removePrefix("#")).toLong(16)) }.getOrDefault(Colors.Text)
    val text = if (contrast(fons, Color.White) >= contrast(fons, Color.Black)) Color.White else Color.Black
    Box(Modifier.background(fons).border(1.5.dp, Colors.Text).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(codi, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 18.sp), color = text, maxLines = 1)
    }
}

private fun signe(valor: Double): String = (if (valor >= 0) "+" else "−") + euros(kotlin.math.abs(valor))

/** Un preu real sencer amb punts de milers: 604.898. */
private fun milers(valor: Double): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ca")).format(valor)
