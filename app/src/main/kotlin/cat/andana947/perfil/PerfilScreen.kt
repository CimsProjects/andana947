package cat.andana947.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cat.andana947.BuildConfig
import cat.andana947.R
import cat.andana947.data.compte.EstatCompte
import cat.andana947.data.compte.ServeiCompte
import cat.andana947.data.joc.ServeiJoc
import cat.andana947.ui.components.BotoSecundari
import cat.andana947.ui.components.Targeta
import cat.andana947.ui.theme.Colors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val compte: ServeiCompte,
    private val joc: ServeiJoc,
) : ViewModel() {
    val estat: StateFlow<EstatCompte> = compte.estat

    private val _modeDev = MutableStateFlow<Boolean?>(null)
    val modeDev: StateFlow<Boolean?> = _modeDev.asStateFlow()

    init {
        if (!BuildConfig.SENSE_SERVIDOR) {
            viewModelScope.launch {
                joc.llegirModeDev()
                    .onSuccess { _modeDev.value = it }
                    .onFailure { _modeDev.value = false }
            }
        }
    }

    fun surt() {
        viewModelScope.launch { compte.surt() }
    }
}

/** El perfil (GDD, secció 7.1). De moment, el compte i els crèdits; el rànquing arriba a la fase 7. */
@Composable
fun PerfilScreen(viewModel: PerfilViewModel = hiltViewModel()) {
    val estat by viewModel.estat.collectAsStateWithLifecycle()
    val modeDev by viewModel.modeDev.collectAsStateWithLifecycle()
    val correu = (estat as? EstatCompte.Llest)?.compte?.correu
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.perfil_titol), style = MaterialTheme.typography.displayMedium, modifier = Modifier.semantics { heading() })
        Targeta(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.perfil_compte), style = MaterialTheme.typography.labelSmall.copy(color = Colors.TextSecundari))
                Text(correu ?: stringResource(R.string.perfil_sense_correu), style = MaterialTheme.typography.titleMedium)
                if (BuildConfig.SENSE_SERVIDOR) {
                    Text(stringResource(R.string.perfil_sense_servidor), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        BotoSecundari(stringResource(R.string.compte_surt), viewModel::surt)

        // Mode desenvolupador: visible només quan hi ha servidor i s'ha carregat el valor.
        if (!BuildConfig.SENSE_SERVIDOR && modeDev != null) {
            Targeta(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.perfil_mode_dev),
                        style = MaterialTheme.typography.labelSmall.copy(color = Colors.TextSecundari),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (modeDev == true) stringResource(R.string.perfil_mode_dev_activat)
                            else stringResource(R.string.perfil_mode_dev_desactivat),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // Lectura: el client no pot activar mode_dev (la RLS ho impedeix).
                        Switch(checked = modeDev == true, onCheckedChange = null)
                    }
                    Text(
                        stringResource(R.string.perfil_mode_dev_descripcio),
                        style = MaterialTheme.typography.bodySmall.copy(color = Colors.TextSecundari),
                    )
                }
            }
        }

        Text(stringResource(R.string.perfil_credits), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.perfil_credits_mapa), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.perfil_credits_fonts), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.perfil_versio, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
    }
}
