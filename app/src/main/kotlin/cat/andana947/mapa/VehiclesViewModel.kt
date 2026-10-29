package cat.andana947.mapa

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.vehicles.FontHoraris
import cat.andana947.data.vehicles.ServeiVehiclesReals
import cat.andana947.joc.circulacio.Flota
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Els trens del mapa (docs/vehicles.md): els horaris per a tots i, mentre es mira el mapa, les posicions reals de
 * Renfe i FGC cada [INTERVAL_REALS_MS]. La [Flota] es comparteix amb el bucle que dibuixa el mapa: s'hi accedeix
 * sempre amb `synchronized(flota)`.
 */
@HiltViewModel
class VehiclesViewModel @Inject constructor(
    private val horaris: FontHoraris,
    private val reals: ServeiVehiclesReals,
) : ViewModel() {
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

    companion object {
        /** Cada quan es demanen les posicions reals. Les fonts s'actualitzen cada mig minut, més o menys. */
        const val INTERVAL_REALS_MS = 20_000L

        /** Un tren real va d'una posició a la següent en el temps que triga a arribar-ne una de nova. */
        const val DURADA_ANIMACIO_MS = INTERVAL_REALS_MS
    }
}
