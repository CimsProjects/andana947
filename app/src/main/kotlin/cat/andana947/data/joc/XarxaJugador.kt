package cat.andana947.data.joc

import cat.andana947.data.vehicles.FontHoraris
import cat.andana947.joc.circulacio.AndanaPropia
import cat.andana947.joc.circulacio.Arribades
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Les andanes del jugador amb el que en sap l'horari: quan hi arriben els vehicles, quines línies hi paren i quins
 * trams formen. Les fan servir els avisos de cada arribada i el mapa.
 */
data class XarxaJugador(
    val andanes: List<AndanaJugador>,
    val arribades: Arribades?,
    /** La part de la flota de cada línia (mode:codi) que té llogada el jugador (fase 5): es veu del seu color. */
    val flota: Map<String, Double> = emptyMap(),
) {
    /** El color de la skin del jugador (el de les seves andanes), per als seus vehicles. */
    val color: String? get() = andanes.firstOrNull()?.color
}

/** Guarda la [XarxaJugador] per a tota l'app, perquè l'horari (gran) només s'aparelli una vegada. */
@Singleton
class RepositoriXarxa @Inject constructor(
    private val joc: ServeiJoc,
    private val horaris: FontHoraris,
) {
    private val mutex = Mutex()
    private val _xarxa = MutableStateFlow<XarxaJugador?>(null)
    val xarxa: StateFlow<XarxaJugador?> = _xarxa.asStateFlow()
    private var llegidaMs = 0L

    /** Torna a llegir les andanes del jugador si fa més de [maximMs] que es van llegir (0: sempre). */
    suspend fun actualitza(maximMs: Long = 0) = mutex.withLock {
        val ara = System.currentTimeMillis()
        if (_xarxa.value != null && ara - llegidaMs < maximMs) return@withLock
        val andanes = joc.andanesJugador().getOrNull() ?: return@withLock
        llegidaMs = ara
        val flota = joc.estatFlota().getOrNull().orEmpty().filter { it.total > 0 }.associate { it.linia to it.part }
        // Si les andanes no han canviat, no cal tornar a aparellar l'horari.
        val actual = _xarxa.value
        if (actual != null && actual.arribades != null && actual.andanes.map { it.id }.toSet() == andanes.map { it.id }.toSet()) {
            _xarxa.value = actual.copy(andanes = andanes, flota = flota)
            return@withLock
        }
        val circulacions = runCatching { horaris.circulacions() }.getOrNull()
        val arribades = circulacions?.let { c ->
            withContext(Dispatchers.Default) {
                Arribades(c, andanes.map { AndanaPropia(it.id, it.lat, it.lon, it.linies, it.mode) })
            }
        }
        _xarxa.value = XarxaJugador(andanes, arribades, flota)
    }
}
