package cat.andana947.compra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.AndanaCerca
import cat.andana947.data.joc.RepositoriXarxa
import cat.andana947.data.joc.ResultatCheckinRpc
import cat.andana947.data.joc.ServeiJoc
import cat.andana947.data.ubicacio.Posicio
import cat.andana947.data.ubicacio.ServeiUbicacio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class CompraUiState(
    val text: String = "",
    val carregant: Boolean = false,
    val andanes: List<AndanaCerca> = emptyList(),
    /** Sense text: les andanes a prop d'on és el jugador (si se sap). */
    val aProp: Boolean = true,
    val comprant: String? = null,
    val resultat: ResultatCheckinRpc? = null,
    val error: String? = null,
)

/** Comprar andanes i accions des de qualsevol lloc (docs/abast.md). El servidor posa el preu i ho valida tot. */
@HiltViewModel
class CompraViewModel @Inject constructor(
    private val joc: ServeiJoc,
    private val ubicacio: ServeiUbicacio,
    private val xarxa: RepositoriXarxa,
) : ViewModel() {
    private val _estat = MutableStateFlow(CompraUiState())
    val estat: StateFlow<CompraUiState> = _estat.asStateFlow()

    private var posicio: Posicio? = null
    private var cerca: Job? = null

    init {
        viewModelScope.launch {
            if (ubicacio.tePermis()) {
                posicio = withTimeoutOrNull(ESPERA_POSICIO_MS) { ubicacio.seguiment().firstOrNull() }
            }
            busca(immediata = true)
        }
    }

    fun escriu(text: String) {
        _estat.update { it.copy(text = text) }
        busca(immediata = false)
    }

    private fun busca(immediata: Boolean) {
        cerca?.cancel()
        cerca = viewModelScope.launch {
            if (!immediata) delay(ESPERA_ESCRIPTURA_MS)
            val text = _estat.value.text.trim()
            _estat.update { it.copy(carregant = true, error = null, aProp = text.isEmpty()) }
            joc.cercaAndanes(text, posicio?.latitud, posicio?.longitud)
                .onSuccess { llista -> _estat.update { it.copy(carregant = false, andanes = llista) } }
                .onFailure { err -> _estat.update { it.copy(carregant = false, error = err.message) } }
        }
    }

    fun compra(andana: AndanaCerca) {
        if (_estat.value.comprant != null) return
        _estat.update { it.copy(comprant = andana.id) }
        viewModelScope.launch {
            val resultat = if (andana.granEstacio) joc.compraAccions(andana.id, ACCIONS_PER_COMPRA) else joc.compraAndana(andana.id)
            resultat
                .onSuccess { r -> _estat.update { it.copy(comprant = null, resultat = r) } }
                .onFailure { err -> _estat.update { it.copy(comprant = null, error = err.message) } }
            // Les andanes noves surten al mapa i als avisos, i els preus de la llista pugen.
            xarxa.actualitza()
            busca(immediata = true)
        }
    }

    fun tancaResultat() = _estat.update { it.copy(resultat = null) }

    companion object {
        const val ACCIONS_PER_COMPRA = 5
        private const val ESPERA_ESCRIPTURA_MS = 350L
        private const val ESPERA_POSICIO_MS = 4_000L
    }
}
