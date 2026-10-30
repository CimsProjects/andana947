package cat.andana947.maestria

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatAndana
import cat.andana947.data.joc.EstatMaestries
import cat.andana947.data.joc.RepositoriXarxa
import cat.andana947.data.joc.ResultatCheckinRpc
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AndanaUiState(
    val carregant: Boolean = true,
    val andana: EstatAndana? = null,
    /** La millora que s'està comprant. */
    val comprant: String? = null,
    val resultat: ResultatCheckinRpc? = null,
    val error: String? = null,
)

/** La fitxa d'una andana pròpia: maestria, línies i millores. */
@HiltViewModel
class AndanaViewModel @Inject constructor(
    private val joc: ServeiJoc,
    private val xarxa: RepositoriXarxa,
) : ViewModel() {
    private val _estat = MutableStateFlow(AndanaUiState())
    val estat: StateFlow<AndanaUiState> = _estat.asStateFlow()

    fun carrega(id: String) {
        viewModelScope.launch {
            joc.estatAndana(id)
                .onSuccess { a -> _estat.update { it.copy(carregant = false, andana = a, error = null) } }
                .onFailure { err -> _estat.update { it.copy(carregant = false, error = err.message) } }
        }
    }

    fun compra(millora: String) {
        val id = _estat.value.andana?.id ?: return
        if (_estat.value.comprant != null) return
        _estat.update { it.copy(comprant = millora) }
        viewModelScope.launch {
            joc.compraMillora(id, millora)
                .onSuccess { r ->
                    _estat.update { it.copy(comprant = null, resultat = r) }
                    if (r.status == "valid") {
                        carrega(id)
                        // El valor de cada arribada (avisos del mapa) ha canviat.
                        xarxa.actualitza()
                    }
                }
                .onFailure { err -> _estat.update { it.copy(comprant = null, error = err.message) } }
        }
    }

    fun tancaResultat() = _estat.update { it.copy(resultat = null) }
}

data class MaestriesUiState(
    val carregant: Boolean = true,
    val estat: EstatMaestries? = null,
    val error: String? = null,
)

/** Les maestries de les línies i les andanes del jugador i la seva xarxa més gran. */
@HiltViewModel
class MaestriesViewModel @Inject constructor(
    private val joc: ServeiJoc,
) : ViewModel() {
    private val _estat = MutableStateFlow(MaestriesUiState())
    val estat: StateFlow<MaestriesUiState> = _estat.asStateFlow()

    fun carrega() {
        viewModelScope.launch {
            joc.estatMaestries()
                .onSuccess { e -> _estat.update { it.copy(carregant = false, estat = e, error = null) } }
                .onFailure { err -> _estat.update { it.copy(carregant = false, error = err.message) } }
        }
    }
}
