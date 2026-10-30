package cat.andana947.temporada

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatTemporada
import cat.andana947.data.joc.RepositoriXarxa
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TemporadaUiState(
    val carregant: Boolean = true,
    val estat: EstatTemporada? = null,
    val error: String? = null,
)

/** La temporada: rànquing, objectius, insígnies, skins i el nom públic del jugador. */
@HiltViewModel
class TemporadaViewModel @Inject constructor(
    private val joc: ServeiJoc,
    private val xarxa: RepositoriXarxa,
) : ViewModel() {
    private val _estat = MutableStateFlow(TemporadaUiState())
    val estat: StateFlow<TemporadaUiState> = _estat.asStateFlow()

    fun carrega() {
        viewModelScope.launch {
            joc.estatTemporada()
                .onSuccess { e -> _estat.update { it.copy(carregant = false, estat = e, error = null) } }
                .onFailure { err -> _estat.update { it.copy(carregant = false, error = err.message) } }
        }
    }

    fun desaNom(nom: String) {
        viewModelScope.launch {
            joc.desaNom(nom)
                .onSuccess { carrega() }
                .onFailure { err -> _estat.update { it.copy(error = err.message) } }
        }
    }

    fun posaSkin(skin: String) {
        viewModelScope.launch {
            joc.desaSkin(skin)
                .onSuccess {
                    carrega()
                    // El color de les andanes al mapa canvia.
                    xarxa.actualitza()
                }
                .onFailure { err -> _estat.update { it.copy(error = err.message) } }
        }
    }
}
