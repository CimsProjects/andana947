package cat.andana947.imperi

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatCobraments
import cat.andana947.data.joc.EstatImperi
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImperiUiState(
    val carregant: Boolean = true,
    val imperi: EstatImperi? = null,
    val cobraments: EstatCobraments? = null,
    /** Quan es van rebre els cobraments, en el rellotge monòton del mòbil (per al compte enrere). */
    val cobramentsRebutsEl: Long = 0,
    val error: String? = null,
)

@HiltViewModel
class ImperiViewModel @Inject constructor(
    private val serveiJoc: ServeiJoc,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImperiUiState())
    val uiState: StateFlow<ImperiUiState> = _uiState.asStateFlow()

    init {
        carregaImperi()
    }

    fun refresca() {
        carregaImperi()
    }

    private fun carregaImperi() {
        viewModelScope.launch {
            _uiState.update { it.copy(carregant = true, error = null) }
            val cobraments = async { serveiJoc.estatCobraments() }
            serveiJoc.estatImperi()
                .onSuccess { imperi ->
                    _uiState.update { it.copy(carregant = false, imperi = imperi) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(carregant = false, error = err.message) }
                }
            // Si falla, l'Imperi es mostra igualment, sense el compte enrere.
            cobraments.await().onSuccess { c ->
                _uiState.update { it.copy(cobraments = c, cobramentsRebutsEl = SystemClock.elapsedRealtime()) }
            }
        }
    }
}
