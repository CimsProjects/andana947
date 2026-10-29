package cat.andana947.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatPartida
import cat.andana947.data.joc.FontTrams
import cat.andana947.data.joc.ResultatCompra
import cat.andana947.data.joc.ServeiJoc
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Els trams del mapa i el que se n'ha de dibuixar: els traçats de cada tram (de `circulacions.json`). */
class MapaTrams(val cataleg: CatalegTrams, val tracatsDeTram: Map<String, List<Int>>, val tracats: List<Tracat>)

/**
 * Els trams i les monedes (GDD 4.0): el catàleg, la partida del jugador (saldo i trams) i les compres. El saldo i el
 * preu que manen són els del servidor; la partida es torna a demanar cada [INTERVAL_ESTAT_MS] mentre es mira el mapa,
 * que és quan el servidor cobra les arribades pendents.
 */
@HiltViewModel
class TramsViewModel @Inject constructor(
    private val trams: FontTrams,
    private val horaris: FontHoraris,
    private val joc: ServeiJoc,
) : ViewModel() {
    val mapa: StateFlow<MapaTrams?> = flow {
        val cataleg = trams.cataleg()
        val circulacions = horaris.circulacions()
        emit(withContext(Dispatchers.Default) { MapaTrams(cataleg, cataleg.tracats(circulacions.horaris), circulacions.tracats) })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _partida = MutableStateFlow<EstatPartida?>(null)
    val partida: StateFlow<EstatPartida?> = _partida.asStateFlow()

    /** Si no s'ha pogut parlar amb el servidor l'última vegada. */
    private val _senseConnexio = MutableStateFlow(false)
    val senseConnexio: StateFlow<Boolean> = _senseConnexio.asStateFlow()

    /** Les monedes guanyades amb les arribades des de l'última vegada, per avisar-ne el jugador. */
    private val _guanyat = MutableStateFlow(0L)
    val guanyat: StateFlow<Long> = _guanyat.asStateFlow()

    private val _seleccionat = MutableStateFlow<String?>(null)

    /** El tram de la fitxa, si n'hi ha cap d'obert. */
    val seleccionat: StateFlow<TramDelJoc?> = combine(_seleccionat, mapa) { id, m -> id?.let { m?.cataleg?.tram(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _comprant = MutableStateFlow(false)
    val comprant: StateFlow<Boolean> = _comprant.asStateFlow()

    /** El resultat de l'última compra, per mostrar-lo a la fitxa. */
    private val _resultat = MutableStateFlow<ResultatCompra?>(null)
    val resultat: StateFlow<ResultatCompra?> = _resultat.asStateFlow()

    /** L'estat (el color) de cada tram per al jugador. */
    val estats: StateFlow<Map<String, EstatTram>> = combine(mapa, partida) { m, p ->
        val cataleg = m?.cataleg ?: return@combine emptyMap()
        val teus = p?.trams.orEmpty().toSet()
        val saldo = p?.saldo ?: 0
        cataleg.trams.associate { it.id to EstatTram.de(it.preu, it.midaTram, saldo, it.id in teus) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Mentre es mira el mapa: demana la partida ara i cada [INTERVAL_ESTAT_MS]. */
    suspend fun segueixPartida() {
        while (true) {
            actualitza()
            delay(INTERVAL_ESTAT_MS)
        }
    }

    private suspend fun actualitza() {
        val estat = joc.estat()
        _senseConnexio.value = estat == null
        if (estat != null) {
            _partida.value = estat
            if (estat.guanyat > 0) _guanyat.value = estat.guanyat
        }
    }

    fun selecciona(tramId: String?) {
        _seleccionat.value = tramId
        _resultat.value = null
    }

    fun oblidaGuanyat() {
        _guanyat.value = 0
    }

    fun compra(tramId: String) {
        if (_comprant.value) return
        _comprant.value = true
        viewModelScope.launch {
            try {
                val resposta = joc.compra(tramId)
                val resultat = ResultatCompra.de(resposta.resultat)
                _resultat.value = resultat
                // Després de comprar, la partida tal com la té el servidor (saldo i trams).
                if (resultat != ResultatCompra.SENSE_CONNEXIO) actualitza()
            } finally {
                _comprant.value = false
            }
        }
    }

    companion object {
        const val INTERVAL_ESTAT_MS = 60_000L
    }
}
