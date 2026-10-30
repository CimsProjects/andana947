package cat.andana947.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatPartida
import cat.andana947.data.joc.FontTrams
import cat.andana947.data.joc.Partida
import cat.andana947.data.joc.Resultat
import cat.andana947.data.vehicles.FontHoraris
import cat.andana947.joc.circulacio.Tracat
import cat.andana947.joc.trams.CatalegTrams
import cat.andana947.joc.trams.EstatTram
import cat.andana947.joc.trams.TramDelJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Els trams del mapa i el que se n'ha de dibuixar: els traçats de cada tram (de `circulacions.json`). */
class MapaTrams(val cataleg: CatalegTrams, val tracatsDeTram: Map<String, List<Int>>, val tracats: List<Tracat>)

/**
 * Els trams al mapa (GDD 4.0): el catàleg, el color de cada tram segons la partida del jugador (saldo i trams) i la
 * fitxa del tram que toca, amb la compra. La partida és la compartida ([Partida]); es torna a demanar cada
 * [INTERVAL_ESTAT_MS] mentre es mira el mapa, que és quan el servidor cobra les arribades pendents.
 */
@HiltViewModel
class TramsViewModel @Inject constructor(
    private val trams: FontTrams,
    private val horaris: FontHoraris,
    private val joc: Partida,
) : ViewModel() {
    val mapa: StateFlow<MapaTrams?> = flow {
        val cataleg = trams.cataleg()
        val circulacions = horaris.circulacions()
        emit(withContext(Dispatchers.Default) { MapaTrams(cataleg, cataleg.tracats(circulacions.horaris), circulacions.tracats) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val partida: StateFlow<EstatPartida?> = joc.estat
    val senseConnexio: StateFlow<Boolean> = joc.senseConnexio
    val guanyat: StateFlow<Long> = joc.guanyat

    private val _seleccionat = MutableStateFlow<String?>(null)

    /** El tram de la fitxa, si n'hi ha cap d'obert. */
    val seleccionat: StateFlow<TramDelJoc?> = combine(_seleccionat, mapa) { id, m -> id?.let { m?.cataleg?.tram(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val comprant: StateFlow<Boolean> = joc.ocupat.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** El resultat de l'última compra, per mostrar-lo a la fitxa. */
    private val _resultat = MutableStateFlow<Resultat?>(null)
    val resultat: StateFlow<Resultat?> = _resultat.asStateFlow()

    /** L'estat (el color) de cada tram per al jugador. */
    val estats: StateFlow<Map<String, EstatTram>> = combine(mapa, partida) { m, p ->
        val cataleg = m?.cataleg ?: return@combine emptyMap()
        val teus = p?.trams.orEmpty().map { it.id }.toSet()
        val saldo = p?.saldo ?: 0
        cataleg.trams.associate { it.id to EstatTram.de(it.preu, it.midaTram, saldo, it.id in teus) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Mentre es mira el mapa: demana la partida ara i cada [INTERVAL_ESTAT_MS]. */
    suspend fun segueixPartida() {
        while (true) {
            joc.actualitza()
            delay(INTERVAL_ESTAT_MS)
        }
    }

    fun selecciona(tramId: String?) {
        _seleccionat.value = tramId
        _resultat.value = null
    }

    fun oblidaGuanyat() = joc.oblidaGuanyat()

    fun compra(tramId: String) {
        viewModelScope.launch { _resultat.value = joc.compra(tramId) }
    }

    companion object {
        const val INTERVAL_ESTAT_MS = 60_000L
    }
}
