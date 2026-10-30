package cat.andana947.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.RepositoriXarxa
import cat.andana947.data.joc.XarxaJugador
import cat.andana947.data.preferencies.PreferenciesVehicles
import cat.andana947.data.vehicles.FontHoraris
import cat.andana947.data.vehicles.ServeiVehiclesReals
import cat.andana947.joc.circulacio.FitxaVehicle
import cat.andana947.joc.circulacio.Flota
import cat.andana947.joc.circulacio.TipusVehicle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Els trens del mapa (docs/vehicles.md): els horaris per a tots i, mentre es mira el mapa, les posicions reals de
 * Renfe i FGC cada [INTERVAL_REALS_MS]. La [Flota] es comparteix amb el bucle que dibuixa el mapa: s'hi accedeix
 * sempre amb `synchronized(flota)`. El jugador pot amagar els trens o els busos ([visibles]), i l'app ho recorda.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val horaris: FontHoraris,
    private val reals: ServeiVehiclesReals,
    private val preferencies: PreferenciesVehicles,
    private val repositoriXarxa: RepositoriXarxa,
) : ViewModel() {
    /** Les andanes i els trams del jugador, per pintar-los i per saber quins vehicles són de les seves línies. */
    val xarxa: StateFlow<XarxaJugador?> = repositoriXarxa.xarxa

    /** Torna a llegir les andanes del jugador (en tornar al mapa, per si n'ha conquerit de noves). */
    fun actualitzaXarxa() {
        viewModelScope.launch { repositoriXarxa.actualitza(INTERVAL_XARXA_MS) }
    }

    private var flotaCreada: Flota? = null

    val flota: StateFlow<Flota?> = flow {
        val flota = flotaCreada ?: Flota(horaris.circulacions(), DURADA_ANIMACIO_MS).also { flotaCreada = it }
        emit(flota)
        while (true) {
            val resposta = reals.consulta()
            val ara = System.currentTimeMillis()
            synchronized(flota) {
                if (resposta == null) flota.senseReals(ara) else flota.actualitzaReals(resposta.vehicles, resposta.fontsOk, ara)
            }
            delay(INTERVAL_REALS_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val visibles: StateFlow<Set<TipusVehicle>> =
        preferencies.visibles().stateIn(viewModelScope, SharingStarted.Eagerly, TipusVehicle.TOTS)

    private val _triat = MutableStateFlow<String?>(null)

    /** El vehicle tocat, amb la fitxa recalculada cada segon; null quan no n'hi ha cap o ja no circula. */
    val fitxa: StateFlow<FitxaVehicle?> = _triat.flatMapLatest { id ->
        flow {
            if (id == null) {
                emit(null)
                return@flow
            }
            while (true) {
                val f = flota.value
                val fitxa = f?.let { synchronized(it) { it.fitxa(id, LocalDateTime.now(ZONA), System.currentTimeMillis()) } }
                emit(fitxa)
                if (f != null && fitxa == null) {
                    _triat.value = null
                    break
                }
                delay(INTERVAL_FITXA_MS)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tria el vehicle tocat al mapa (null el desfà). */
    fun tria(id: String?) {
        _triat.value = id
    }

    /** Mostra o amaga un tipus de vehicle. */
    fun commuta(tipus: TipusVehicle) {
        val nous = visibles.value.let { if (tipus in it) it - tipus else it + tipus }
        viewModelScope.launch { preferencies.desaVisibles(nous) }
    }

    companion object {
        /** Cada quan es demanen les posicions reals. Les fonts s'actualitzen cada mig minut, més o menys. */
        const val INTERVAL_REALS_MS = 20_000L

        /** Un tren real va d'una posició a la següent en el temps que triga a arribar-ne una de nova. */
        const val DURADA_ANIMACIO_MS = INTERVAL_REALS_MS

        /** En tornar al mapa, les andanes es tornen a llegir si fa més d'aquest temps. */
        const val INTERVAL_XARXA_MS = 30_000L

        /** Cada quan es recalcula la fitxa del vehicle tocat (la pròxima parada canvia). */
        const val INTERVAL_FITXA_MS = 1_000L

        /** Els horaris són en hora local de Catalunya. */
        val ZONA: ZoneId = ZoneId.of("Europe/Madrid")
    }
}
