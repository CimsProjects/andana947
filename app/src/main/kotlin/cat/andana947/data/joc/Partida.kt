package cat.andana947.data.joc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La partida del jugador, compartida per totes les pantalles (el mapa, la compra i l'imperi): el que ha dit el
 * servidor l'última vegada. Les compres i les millores es fan d'una en una, i després es torna a demanar l'estat.
 */
@Singleton
class Partida @Inject constructor(private val joc: ServeiJoc) {
    private val _estat = MutableStateFlow<EstatPartida?>(null)
    val estat: StateFlow<EstatPartida?> = _estat.asStateFlow()

    /** Si no s'ha pogut parlar amb el servidor l'última vegada. */
    private val _senseConnexio = MutableStateFlow(false)
    val senseConnexio: StateFlow<Boolean> = _senseConnexio.asStateFlow()

    /** Les monedes guanyades amb les arribades des de l'última vegada, per avisar-ne el jugador. */
    private val _guanyat = MutableStateFlow(0L)
    val guanyat: StateFlow<Long> = _guanyat.asStateFlow()

    /** El tram que s'està comprant o millorant, si n'hi ha cap. */
    private val _ocupat = MutableStateFlow<String?>(null)
    val ocupat: StateFlow<String?> = _ocupat.asStateFlow()

    private val mutex = Mutex()

    suspend fun actualitza() {
        val estat = joc.estat()
        _senseConnexio.value = estat == null
        if (estat != null) {
            _estat.value = estat
            if (estat.guanyat > 0) _guanyat.value = estat.guanyat
        }
    }

    fun oblidaGuanyat() {
        _guanyat.value = 0
    }

    suspend fun compra(tramId: String): Resultat = accio(tramId) { joc.compra(it) }

    suspend fun millora(tramId: String): Resultat = accio(tramId) { joc.millora(it) }

    private suspend fun accio(tramId: String, crida: suspend (String) -> Resposta): Resultat = mutex.withLock {
        _ocupat.value = tramId
        try {
            val resultat = Resultat.de(crida(tramId).resultat)
            // Després, la partida tal com la té el servidor (saldo i trams).
            if (resultat != Resultat.SENSE_CONNEXIO) actualitza()
            resultat
        } finally {
            _ocupat.value = null
        }
    }
}
