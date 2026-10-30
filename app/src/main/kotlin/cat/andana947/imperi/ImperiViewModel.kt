package cat.andana947.imperi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatImperi
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
            serveiJoc.estatImperi()
                .onSuccess { imperi ->
                    _uiState.update { it.copy(carregant = false, imperi = imperi) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(carregant = false, error = err.message) }
                }
        }
    }
}
