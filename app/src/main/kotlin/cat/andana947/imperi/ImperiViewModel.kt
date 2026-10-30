package cat.andana947.imperi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatPartida
import cat.andana947.data.joc.FontTrams
import cat.andana947.data.joc.Partida
import cat.andana947.data.joc.Resultat
import cat.andana947.joc.trams.CatalegTrams
import cat.andana947.joc.trams.TramDelJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Un tram del jugador a «El meu imperi»: quantes andanes en té, el que guanya ara i, si encara li queden parades, el
 * cost d'afegir-ne una i el que guanyaria amb ella.
 */
data class TramPropi(
    val tram: TramDelJoc,
    val andanes: Int,
    val monedesPerMinut: Double,
    /** Null si ja té totes les parades. */
    val costMillora: Int?,
    val monedesPerMinutMillorat: Double?,
    val esPotPermetre: Boolean,
)

/** «El meu imperi» (GDD 4.0): el saldo, el que guanya el jugador i els seus trams, amb el botó de millorar-los. */
@HiltViewModel
class ImperiViewModel @Inject constructor(
    trams: FontTrams,
    private val joc: Partida,
) : ViewModel() {
    private val cataleg: StateFlow<CatalegTrams?> =
        flow { emit(trams.cataleg()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val partida: StateFlow<EstatPartida?> = joc.estat
    val senseConnexio: StateFlow<Boolean> = joc.senseConnexio
    val ocupat: StateFlow<String?> = joc.ocupat

    /** L'últim resultat, amb el tram, per mostrar-lo a la seva targeta. */
    private val _resultat = MutableStateFlow<Pair<String, Resultat>?>(null)
    val resultat: StateFlow<Pair<String, Resultat>?> = _resultat.asStateFlow()

    val trams: StateFlow<List<TramPropi>?> = combine(cataleg, partida) { c, p ->
        if (c == null || p == null) null else propis(c, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun actualitza() {
        viewModelScope.launch { joc.actualitza() }
    }

    fun millora(tramId: String) {
        viewModelScope.launch { _resultat.value = tramId to joc.millora(tramId) }
    }

    companion object {
        /** Els trams del jugador, en l'ordre en què els ha comprat. */
        fun propis(cataleg: CatalegTrams, partida: EstatPartida): List<TramPropi> = partida.trams.mapNotNull { propi ->
            val tram = cataleg.tram(propi.id) ?: return@mapNotNull null
            val cost = cataleg.costMillora(tram, propi.andanes)
            TramPropi(
                tram = tram,
                andanes = propi.andanes,
                monedesPerMinut = cataleg.monedesPerMinut(tram, propi.andanes),
                costMillora = cost,
                monedesPerMinutMillorat = cost?.let { cataleg.monedesPerMinut(tram, propi.andanes + 1) },
                esPotPermetre = cost != null && partida.saldo >= cost,
            )
        }
    }
}
