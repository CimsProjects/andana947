package cat.andana947.compra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatPartida
import cat.andana947.data.joc.FontTrams
import cat.andana947.data.joc.Partida
import cat.andana947.data.joc.Resultat
import cat.andana947.joc.trams.CatalegTrams
import cat.andana947.joc.trams.Mida
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
import java.text.Normalizer
import javax.inject.Inject

/** Un tram que es pot comprar, amb el que dona amb la primera andana. */
data class TramEnVenda(val tram: TramDelJoc, val preu: Int, val monedesPerMinut: Double, val esPotPermetre: Boolean)

/** Quins trams es mostren a la llista de compra. */
enum class FiltreCompra {
    /** Els que es pot permetre ara. */
    ASSEQUIBLES,
    TOTS,
}

/**
 * La pàgina de compra (GDD 4.0): tots els trams petits i mitjans que el jugador encara no té, del més barat al més car,
 * amb el preu de la primera andana i el que en guanyaria. Els grans no hi surten: es compraran per fraccions.
 */
@HiltViewModel
class CompraViewModel @Inject constructor(
    trams: FontTrams,
    private val joc: Partida,
) : ViewModel() {
    private val cataleg: StateFlow<CatalegTrams?> =
        flow { emit(trams.cataleg()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val partida: StateFlow<EstatPartida?> = joc.estat
    val senseConnexio: StateFlow<Boolean> = joc.senseConnexio
    val ocupat: StateFlow<String?> = joc.ocupat

    private val _cerca = MutableStateFlow("")
    val cerca: StateFlow<String> = _cerca.asStateFlow()

    private val _filtre = MutableStateFlow(FiltreCompra.TOTS)
    val filtre: StateFlow<FiltreCompra> = _filtre.asStateFlow()

    /** L'últim resultat, amb el tram, per mostrar-lo a la seva fila. */
    private val _resultat = MutableStateFlow<Pair<String, Resultat>?>(null)
    val resultat: StateFlow<Pair<String, Resultat>?> = _resultat.asStateFlow()

    val llista: StateFlow<List<TramEnVenda>?> = combine(cataleg, partida, cerca, filtre) { c, p, text, f ->
        c?.let { enVenda(it, p, text, f) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun cerca(text: String) {
        _cerca.value = text
    }

    fun filtra(filtre: FiltreCompra) {
        _filtre.value = filtre
    }

    fun actualitza() {
        viewModelScope.launch { joc.actualitza() }
    }

    fun compra(tramId: String) {
        viewModelScope.launch { _resultat.value = tramId to joc.compra(tramId) }
    }

    companion object {
        /** La llista, sense els trams grans ni els que ja té el jugador, i filtrada pel text i per [filtre]. */
        fun enVenda(cataleg: CatalegTrams, partida: EstatPartida?, cerca: String, filtre: FiltreCompra): List<TramEnVenda> {
            val teus = partida?.trams.orEmpty().map { it.id }.toSet()
            val saldo = partida?.saldo ?: 0
            val text = cerca.trim().senseAccents()
            return cataleg.trams
                .asSequence()
                .filter { it.midaTram != Mida.GRAN && it.id !in teus }
                .filter { text.isEmpty() || text in it.nom.senseAccents() || text in it.codi.senseAccents() }
                .mapNotNull { tram ->
                    val preu = tram.preu ?: return@mapNotNull null
                    TramEnVenda(tram, preu, cataleg.monedesPerMinut(tram, 1), saldo >= preu)
                }
                .filter { filtre == FiltreCompra.TOTS || it.esPotPermetre }
                .sortedWith(compareBy({ it.preu }, { it.tram.nom }))
                .toList()
        }

        /** Per cercar sense fer cas de majúscules ni accents: «Lleida» troba «lleida», «Gràcia» troba «gracia». */
        private fun String.senseAccents(): String =
            Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }
}
