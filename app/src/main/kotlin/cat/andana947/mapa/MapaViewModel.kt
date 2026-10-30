package cat.andana947.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.preferencies.Camera
import cat.andana947.data.preferencies.PreferenciesMapa
import cat.andana947.data.ubicacio.Posicio
import cat.andana947.data.ubicacio.ServeiUbicacio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EstatPermis {
    /** Encara no s'ha demanat (o s'està demanant). */
    PENDENT,
    CONCEDIT,
    DENEGAT,
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapaViewModel @Inject constructor(
    private val ubicacio: ServeiUbicacio,
    private val preferencies: PreferenciesMapa,
) : ViewModel() {
    private val _permis = MutableStateFlow(if (ubicacio.tePermis()) EstatPermis.CONCEDIT else EstatPermis.PENDENT)
    val permis: StateFlow<EstatPermis> = _permis.asStateFlow()

    /** On s'obre el mapa: on es va deixar (si és a Catalunya) o, la primera vegada, Catalunya. Null mentre es llegeix. */
    private val _cameraInicial = MutableStateFlow<Camera?>(null)
    val cameraInicial: StateFlow<Camera?> = _cameraInicial.asStateFlow()

    /**
     * Cada vegada que canvia, el mapa va a la posició del jugador: amb la primera posició que arriba i quan el jugador
     * toca el botó de centrar.
     */
    private val _centra = MutableStateFlow(0)
    val centra: StateFlow<Int> = _centra.asStateFlow()

    private var jaCentrat = false

    /** La posició del jugador, mentre es mira el mapa i si hi ha permís. */
    val posicio: StateFlow<Posicio?> = _permis
        .flatMapLatest { if (it == EstatPermis.CONCEDIT) ubicacio.seguiment() else emptyFlow() }
        .onEach {
            if (!jaCentrat) {
                jaCentrat = true
                _centra.update { n -> n + 1 }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { _cameraInicial.value = preferencies.darreraCamera()?.let(LimitsCatalunya::limita) ?: Camera.CATALUNYA }
    }

    fun resultatPermis(concedit: Boolean) {
        _permis.value = if (concedit) EstatPermis.CONCEDIT else EstatPermis.DENEGAT
    }

    /** En tornar a l'app: potser el jugador ha donat el permís des de la configuració del mòbil. */
    fun revisaPermis() {
        if (_permis.value == EstatPermis.DENEGAT && ubicacio.tePermis()) _permis.value = EstatPermis.CONCEDIT
    }

    fun centraEnMi() {
        if (posicio.value != null) _centra.update { it + 1 }
    }

    fun desaCamera(camera: Camera) {
        viewModelScope.launch { preferencies.desaCamera(camera) }
    }
}
