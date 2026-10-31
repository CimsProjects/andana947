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

enum class Porta { Carregant, Introduccio, Regal, Joc }

/**
 * Decideix, en entrar amb sessió, si cal la introducció del Director, només el regal (comptes que van fer la
 * introducció abans que hi hagués andanes de regal) o es va directament al joc.
 */
@HiltViewModel
class PortaJocViewModel @Inject constructor(joc: ServeiJoc) : ViewModel() {
    private val _porta = MutableStateFlow(Porta.Carregant)
    val porta: StateFlow<Porta> = _porta.asStateFlow()

    init {
        viewModelScope.launch {
            // Els comptes antics no tenien perfil: es crea abans de llegir-lo.
            joc.asseguraPerfil()
            // Si no es pot llegir el perfil (sense connexió), no es bloqueja el joc: la introducció sortirà un altre dia.
            val feta = joc.llegirPerfilJugador().map { it.introduccioFeta }.getOrDefault(true)
            _porta.value = when {
                !feta -> Porta.Introduccio
                // Introducció feta però regal per triar: el Director l'ofereix ara.
                joc.ofertesRegal().getOrNull()?.provincies?.any { it.opcions.isNotEmpty() } == true -> Porta.Regal
                else -> Porta.Joc
            }
        }
    }

    fun introduccioAcabada() {
        _porta.value = Porta.Joc
    }
}
