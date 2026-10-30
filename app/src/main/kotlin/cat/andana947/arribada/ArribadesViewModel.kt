package cat.andana947.arribada

import androidx.lifecycle.ViewModel
import cat.andana947.data.joc.RepositoriXarxa
import cat.andana947.joc.circulacio.LiniaCirculacio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Un avís: un vehicle de la [linia] ha arribat a l'andana [andana]. Les xifres són les del servidor [estimat]. */
data class AvisArribada(
    val clau: String,
    val andana: String,
    val linia: LiniaCirculacio,
    val pujades: Int,
    val valor: Double,
    val finsMs: Long,
)

/**
 * Mentre l'app és oberta, mira cada pocs segons quins trens i busos han arribat a les andanes del jugador segons
 * l'horari, i en fa un avís amb les persones i els diners de l'arribada (GDD 4.8). No mou diners: els cobra el servidor
 * cada 4 hores.
 */
@HiltViewModel
class ArribadesViewModel @Inject constructor(
    private val xarxa: RepositoriXarxa,
) : ViewModel() {
    private val _avisos = MutableStateFlow<List<AvisArribada>>(emptyList())
    val avisos: StateFlow<List<AvisArribada>> = _avisos.asStateFlow()

    /** Vigila les arribades fins que es cancel·la (en sortir de l'app). Comença a comptar ara: no avisa del passat. */
    suspend fun vigila() {
        var desDe = ara()
        while (true) {
            xarxa.actualitza(INTERVAL_ANDANES_MS)
            val estat = xarxa.xarxa.value
            val andanes = estat?.andanes.orEmpty().associateBy { it.id }
            val fins = ara()
            val noves = estat?.arribades?.let { a -> withContext(Dispatchers.Default) { a.entre(desDe, fins) } }.orEmpty()
            desDe = fins
            val araMs = System.currentTimeMillis()
            _avisos.update { actuals ->
                val vigents = actuals.filter { it.finsMs > araMs }
                val nous = noves.mapNotNull { arr ->
                    val andana = andanes[arr.andana] ?: return@mapNotNull null
                    if (andana.pujades <= 0 && andana.valor <= 0.0) return@mapNotNull null
                    AvisArribada(
                        clau = "${arr.andana}:${arr.linia.id}:${arr.hora}",
                        andana = andana.nom,
                        linia = arr.linia,
                        pujades = andana.pujades,
                        valor = andana.valor,
                        finsMs = araMs + DURADA_AVIS_MS,
                    )
                }
                // Els més nous a dalt, i no gaires alhora.
                (nous.reversed() + vigents).distinctBy { it.clau }.take(MAXIM_AVISOS)
            }
            delay(INTERVAL_MS)
        }
    }

    private fun ara(): LocalDateTime = LocalDateTime.now(ZONA)

    companion object {
        /** Cada quan es miren les arribades. */
        const val INTERVAL_MS = 5_000L

        /** Cada quan es tornen a demanar les andanes del jugador (en pot haver conquerit de noves). */
        const val INTERVAL_ANDANES_MS = 5 * 60_000L

        const val DURADA_AVIS_MS = 6_000L
        const val MAXIM_AVISOS = 3

        /** Els horaris són en hora local de Catalunya. */
        val ZONA: ZoneId = ZoneId.of("Europe/Madrid")
    }
}
