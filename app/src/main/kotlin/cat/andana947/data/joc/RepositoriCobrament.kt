package cat.andana947.data.joc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El saldo i el que es cobra, per a totes les pantalles. Es cobra per cada arribada (GDD 4.8): quan un tren o bus
 * arriba a una andana del jugador, l'avís en suma el valor aquí de seguida, perquè el jugador ho vegi; cada minut el
 * servidor cobra de debò i en torna les xifres bones, que substitueixen les previstes. El client mai no envia diners.
 */
@Singleton
class RepositoriCobrament @Inject constructor(
    private val joc: ServeiJoc,
) {
    private val _estat = MutableStateFlow<EstatCobraments?>(null)
    val estat: StateFlow<EstatCobraments?> = _estat.asStateFlow()

    /** Les xifres del servidor. */
    fun actualitza(estat: EstatCobraments) {
        _estat.value = estat
    }

    /** Un tren o bus acaba d'arribar a una andana del jugador: es veu de seguida (el servidor ho confirmarà). */
    fun sumaArribada(valor: Double) {
        if (valor <= 0.0) return
        _estat.update { e -> e?.copy(saldo = e.saldo + valor, avui = e.avui + valor) }
    }

    /** Demana al servidor que cobri les arribades fins ara. */
    suspend fun cobra() {
        joc.cobraArribades().onSuccess { actualitza(it) }
    }
}
