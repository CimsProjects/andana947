package cat.andana947.aprop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.NodeAProp
import cat.andana947.data.joc.ResultatCheckinRpc
import cat.andana947.data.joc.ServeiJoc
import cat.andana947.data.ubicacio.Posicio
import cat.andana947.data.ubicacio.ServeiUbicacio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class APropUiState(
    val carregant: Boolean = true,
    val nodes: List<NodeAProp> = emptyList(),
    val nodeSeleccionat: NodeAProp? = null,
    val fentCheckin: Boolean = false,
    val resultatCheckin: ResultatCheckinRpc? = null,
    val error: String? = null,
)

@HiltViewModel
class APropViewModel @Inject constructor(
    private val serveiJoc: ServeiJoc,
    private val serveiUbicacio: ServeiUbicacio,
) : ViewModel() {

    private val _uiState = MutableStateFlow(APropUiState())
    val uiState: StateFlow<APropUiState> = _uiState.asStateFlow()

    private val _darreraPosicio = MutableStateFlow<Posicio?>(null)

    init {
        viewModelScope.launch {
            serveiUbicacio.seguiment().collectLatest { posicio ->
                _darreraPosicio.value = posicio
                carregaNodes(posicio.latitud, posicio.longitud)
            }
        }
    }

    fun refresca() {
        val pos = _darreraPosicio.value
        if (pos != null) {
            carregaNodes(pos.latitud, pos.longitud)
        } else {
            // Si encara no tenim posició GPS, intentar amb coordenades centrals de Catalunya
            carregaNodes(41.3870, 2.1700)
        }
    }

    private fun carregaNodes(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(carregant = true, error = null) }
            serveiJoc.nodesAProp(lat, lon, radiMetres = 2000.0)
                .onSuccess { nodes ->
                    _uiState.update { it.copy(carregant = false, nodes = nodes) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(carregant = false, error = error.message) }
                }
        }
    }

    fun seleccionaNode(node: NodeAProp?) {
        _uiState.update { it.copy(nodeSeleccionat = node) }
    }

    fun ferCheckin(node: NodeAProp) {
        val pos = _darreraPosicio.value
        val lat = pos?.latitud ?: node.lat
        val lon = pos?.longitud ?: node.lon
        val precisio = pos?.precisio ?: 10.0
        val velocitatKmh = (pos?.velocitat ?: 0.0) * 3.6

        viewModelScope.launch {
            _uiState.update { it.copy(fentCheckin = true) }
            serveiJoc.ferCheckin(
                nodeId = node.id,
                lat = lat,
                lon = lon,
                precisio = precisio,
                velocitat = velocitatKmh,
            ).onSuccess { res ->
                _uiState.update { it.copy(fentCheckin = false, resultatCheckin = res) }
                if (res.status == "valid") {
                    carregaNodes(lat, lon)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        fentCheckin = false,
                        resultatCheckin = ResultatCheckinRpc(
                            status = "error",
                            missatge = err.message ?: "Error de xarxa.",
                        ),
                    )
                }
            }
        }
    }

    fun tancaResultat() {
        _uiState.update { it.copy(resultatCheckin = null) }
    }
}
