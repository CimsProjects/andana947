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
    /** El Director parla. [cara] és l'expressió. */
    data class Dialeg(@StringRes val text: Int, val cara: String = "cara_director") : PasIntroduccio

    /** El jugador es fa l'avatar. */
    data object FesAvatar : PasIntroduccio
}

/** La seqüència de la introducció. Les andanes de regal s'hi afegiran a la fase següent. */
val passosIntroduccio: List<PasIntroduccio> = listOf(
    PasIntroduccio.Dialeg(R.string.director_intro_1),
    PasIntroduccio.Dialeg(R.string.director_intro_2),
    PasIntroduccio.Dialeg(R.string.director_intro_3, "cara_director_ullet"),
    PasIntroduccio.FesAvatar,
    PasIntroduccio.Dialeg(R.string.director_intro_4),
    PasIntroduccio.Dialeg(R.string.director_intro_5),
    PasIntroduccio.Dialeg(R.string.director_intro_6),
    PasIntroduccio.Dialeg(R.string.director_intro_7),
    PasIntroduccio.Dialeg(R.string.director_intro_8, "cara_director_ullet"),
)

data class IntroduccioUiState(
    val pas: Int = 0,
    val avatar: Avatar = CatalegAvatar.inicial,
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
    }

    fun canviaAvatar(avatar: Avatar) = _estat.update { it.copy(avatar = avatar, error = null) }

    fun endavant() {
        val e = _estat.value
        if (e.desant) return
        when (passosIntroduccio[e.pas]) {
            is PasIntroduccio.FesAvatar -> desaAvatarIContinua()
            is PasIntroduccio.Dialeg -> if (e.pas == passosIntroduccio.lastIndex) acaba() else seguent()
        }
    }

    private fun seguent() = _estat.update { it.copy(pas = it.pas + 1, error = null) }

    private fun desaAvatarIContinua() {
        _estat.update { it.copy(desant = true, error = null) }
        viewModelScope.launch {
            joc.desaAvatar(_estat.value.avatar)
                .onSuccess { a -> _estat.update { it.copy(desant = false, avatar = a, pas = it.pas + 1) } }
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
