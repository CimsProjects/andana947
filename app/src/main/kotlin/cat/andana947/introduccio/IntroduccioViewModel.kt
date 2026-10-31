package cat.andana947.introduccio

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.R
import cat.andana947.avatar.Avatar
import cat.andana947.avatar.CatalegAvatar
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Un pas de la introducció (docs/lore.md). */
sealed interface PasIntroduccio {
    /**
     * El Director parla. [cara] és l'expressió. Els diàlegs [delRegal] només surten si el Director té andanes per
     * oferir (si el jugador ja les ha triades, o no hi ha servidor, se salten).
     */
    data class Dialeg(@StringRes val text: Int, val cara: String = "cara_director", val delRegal: Boolean = false) :
        PasIntroduccio

    /** El jugador es fa l'avatar. */
    data object FesAvatar : PasIntroduccio

    /** El jugador tria les andanes de regal. */
    data object TriaRegal : PasIntroduccio
}

/** La seqüència de la introducció. */
val passosIntroduccio: List<PasIntroduccio> = listOf(
    PasIntroduccio.Dialeg(R.string.director_intro_1),
    PasIntroduccio.Dialeg(R.string.director_intro_2),
    PasIntroduccio.Dialeg(R.string.director_intro_3, "cara_director_ullet"),
    PasIntroduccio.FesAvatar,
    PasIntroduccio.Dialeg(R.string.director_intro_4),
    PasIntroduccio.Dialeg(R.string.director_intro_5),
    PasIntroduccio.Dialeg(R.string.director_intro_6),
    PasIntroduccio.Dialeg(R.string.director_regal_1, "cara_director_ullet", delRegal = true),
    PasIntroduccio.TriaRegal,
    PasIntroduccio.Dialeg(R.string.director_regal_2, delRegal = true),
    PasIntroduccio.Dialeg(R.string.director_intro_7),
    PasIntroduccio.Dialeg(R.string.director_intro_8, "cara_director_ullet"),
)

private fun PasIntroduccio.esDelRegal(): Boolean =
    this is PasIntroduccio.TriaRegal || (this is PasIntroduccio.Dialeg && delRegal)

data class IntroduccioUiState(
    val pas: Int = 0,
    val avatar: Avatar = CatalegAvatar.inicial,
    /** null mentre es carrega, si ja s'ha triat o si no hi ha servidor. */
    val regal: SeleccioRegal? = null,
    /** El jugador ha intentat passar del màxim d'una província. */
    val avisMaxim: Boolean = false,
    val andanesRebudes: List<String> = emptyList(),
    val desant: Boolean = false,
    val error: String? = null,
    val acabada: Boolean = false,
)

@HiltViewModel
class IntroduccioViewModel @Inject constructor(
    private val joc: ServeiJoc,
) : ViewModel() {
    private val _estat = MutableStateFlow(IntroduccioUiState())
    val estat: StateFlow<IntroduccioUiState> = _estat.asStateFlow()

    init {
        // Si el jugador ja tenia avatar (per exemple, va tancar l'app a mitja introducció), es parteix d'aquell.
        viewModelScope.launch {
            joc.llegirPerfilJugador().onSuccess { p ->
                p.avatar?.let { a -> _estat.update { if (it.avatar == CatalegAvatar.inicial) it.copy(avatar = a) else it } }
            }
        }
        viewModelScope.launch {
            joc.ofertesRegal().onSuccess { o ->
                if (o != null && o.provincies.any { it.opcions.isNotEmpty() }) {
                    _estat.update { it.copy(regal = SeleccioRegal(o)) }
                }
            }
        }
    }

    /** Va directament al regal del Director (la resta de la introducció ja es va fer). */
    fun comencaPelRegal() = _estat.update { e ->
        val primer = passosIntroduccio.indexOfFirst { it.esDelRegal() }
        if (primer >= 0 && e.pas < primer) e.copy(pas = primer) else e
    }

    fun canviaAvatar(avatar: Avatar) = _estat.update { it.copy(avatar = avatar, error = null) }

    fun commutaRegal(opcio: String) = _estat.update { e ->
        val s = e.regal ?: return@update e
        val nova = s.commuta(opcio)
        if (nova == null) e.copy(avisMaxim = true) else e.copy(regal = nova, avisMaxim = false, error = null)
    }

    fun endavant() {
        val e = _estat.value
        if (e.desant) return
        when (passosIntroduccio[e.pas]) {
            is PasIntroduccio.FesAvatar -> desaAvatarIContinua()
            is PasIntroduccio.TriaRegal -> triaRegalIContinua()
            is PasIntroduccio.Dialeg -> if (e.pas == passosIntroduccio.lastIndex) acaba() else seguent()
        }
    }

    /** El pas següent; sense regal per oferir, se salten els passos del regal. */
    private fun seguentDe(pas: Int): Int {
        var p = pas + 1
        while (p < passosIntroduccio.lastIndex && _estat.value.regal == null && passosIntroduccio[p].esDelRegal()) p++
        return p
    }

    private fun seguent() = _estat.update { it.copy(pas = seguentDe(it.pas), error = null) }

    private fun desaAvatarIContinua() {
        _estat.update { it.copy(desant = true, error = null) }
        viewModelScope.launch {
            joc.desaAvatar(_estat.value.avatar)
                .onSuccess { a -> _estat.update { it.copy(desant = false, avatar = a, pas = seguentDe(it.pas)) } }
                .onFailure { err -> _estat.update { it.copy(desant = false, error = err.message ?: "") } }
        }
    }

    private fun triaRegalIContinua() {
        val s = _estat.value.regal ?: return seguent()
        if (s.triades.isEmpty()) return
        _estat.update { it.copy(desant = true, error = null) }
        viewModelScope.launch {
            joc.triaRegal(s.triades.toList())
                .onSuccess { noms ->
                    _estat.update { it.copy(desant = false, andanesRebudes = noms, pas = seguentDe(it.pas)) }
                }
                .onFailure { err -> _estat.update { it.copy(desant = false, error = err.message ?: "") } }
        }
    }

    private fun acaba() {
        _estat.update { it.copy(desant = true, error = null) }
        viewModelScope.launch {
            joc.completaIntroduccio()
                .onSuccess { _estat.update { it.copy(desant = false, acabada = true) } }
                .onFailure { err -> _estat.update { it.copy(desant = false, error = err.message ?: "") } }
        }
    }
}
