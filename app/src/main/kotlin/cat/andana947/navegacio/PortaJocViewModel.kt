package cat.andana947.navegacio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Porta { Carregant, Introduccio, Joc }

/** Decideix, en entrar amb sessió, si cal la introducció del Director o es va directament al joc. */
@HiltViewModel
class PortaJocViewModel @Inject constructor(joc: ServeiJoc) : ViewModel() {
    private val _porta = MutableStateFlow(Porta.Carregant)
    val porta: StateFlow<Porta> = _porta.asStateFlow()

    init {
        viewModelScope.launch {
            // Si no es pot llegir el perfil (sense connexió), no es bloqueja el joc: la introducció sortirà un altre dia.
            _porta.value = joc.llegirPerfilJugador()
                .map { if (it.introduccioFeta) Porta.Joc else Porta.Introduccio }
                .getOrDefault(Porta.Joc)
        }
    }

    fun introduccioAcabada() {
        _porta.value = Porta.Joc
    }
}
