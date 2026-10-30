package cat.andana947.imperi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.Esdeveniment
import cat.andana947.data.joc.EstatCobraments
import cat.andana947.data.joc.EstatImperi
import cat.andana947.data.joc.RepositoriCobrament
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImperiUiState(
    val carregant: Boolean = true,
    val imperi: EstatImperi? = null,
    /** Els esdeveniments del motor de caos d'avui (fase 6). */
    val esdeveniments: List<Esdeveniment> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ImperiViewModel @Inject constructor(
    private val serveiJoc: ServeiJoc,
    private val cobrament: RepositoriCobrament,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImperiUiState())
    val uiState: StateFlow<ImperiUiState> = _uiState.asStateFlow()

    /** El saldo i el que es cobra, al dia amb cada arribada (RepositoriCobrament). */
    val cobraments: StateFlow<EstatCobraments?> = cobrament.estat

    init {
        carregaImperi()
    }

    fun refresca() {
        carregaImperi()
    }

    private fun carregaImperi() {
        viewModelScope.launch {
            _uiState.update { it.copy(carregant = true, error = null) }
            // Primer es cobra fins ara, perquè el saldo de l'Imperi sigui el d'aquest moment.
            cobrament.cobra()
            serveiJoc.estatImperi()
                .onSuccess { imperi -> _uiState.update { it.copy(carregant = false, imperi = imperi) } }
                .onFailure { err -> _uiState.update { it.copy(carregant = false, error = err.message) } }
            // Primer els que toquen andanes del jugador.
            serveiJoc.esdevenimentsAvui().onSuccess { e ->
                _uiState.update { it.copy(esdeveniments = e.sortedBy { x -> x.andanes.isEmpty() }) }
            }
        }
    }
}
