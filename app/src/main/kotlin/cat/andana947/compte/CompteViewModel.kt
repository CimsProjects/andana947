package cat.andana947.compte

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.compte.CONTRASENYA_MINIM
import cat.andana947.data.compte.ErrorCompte
import cat.andana947.data.compte.ExcepcioCompte
import cat.andana947.data.compte.ResultatRegistre
import cat.andana947.data.compte.ServeiCompte
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ModeCompte {
    /** Entrar amb correu i contrasenya. */
    ENTRA,

    /** Crear un compte amb correu i contrasenya. */
    REGISTRA,
}

data class CompteEstat(
    val mode: ModeCompte = ModeCompte.ENTRA,
    val correu: String = "",
    val contrasenya: String = "",
    /** En crear el compte, la contrasenya s'ha d'escriure dues vegades. */
    val repeticio: String = "",
    val treballant: Boolean = false,
    val error: ErrorCompte? = null,
    /** El registre ha anat bé, però cal confirmar el correu abans d'entrar. */
    val calConfirmar: Boolean = false,
) {
    val correuValid: Boolean get() = esCorreu(correu)

    val potEnviar: Boolean
        get() = !treballant && correuValid && when (mode) {
            ModeCompte.ENTRA -> contrasenya.isNotEmpty()
            ModeCompte.REGISTRA -> contrasenya.length >= CONTRASENYA_MINIM && repeticio == contrasenya
        }

    /** Ja s'ha escrit la repetició i no coincideix amb la contrasenya. */
    val contrasenyesDiferents: Boolean get() = repeticio.isNotEmpty() && repeticio != contrasenya

    companion object {
        private val patroCorreu = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun esCorreu(text: String): Boolean = patroCorreu.matches(text.trim())
    }
}

@HiltViewModel
class CompteViewModel @Inject constructor(private val compte: ServeiCompte) : ViewModel() {
    private val _estat = MutableStateFlow(CompteEstat())
    val estat: StateFlow<CompteEstat> = _estat.asStateFlow()

    fun canviaMode(mode: ModeCompte) = _estat.update { it.copy(mode = mode, contrasenya = "", repeticio = "", error = null) }

    fun canviaCorreu(text: String) = _estat.update { it.copy(correu = text, error = null) }

    fun canviaContrasenya(text: String) = _estat.update { it.copy(contrasenya = text, error = null) }

    fun canviaRepeticio(text: String) = _estat.update { it.copy(repeticio = text, error = null) }

    fun tancaAvis() = _estat.update { it.copy(calConfirmar = false) }

    /** Fa l'acció del mode actual: entrar o crear el compte. */
    fun envia() {
        val e = _estat.value
        if (!e.potEnviar) return
        executa {
            when (e.mode) {
                ModeCompte.ENTRA -> {
                    compte.entra(e.correu, e.contrasenya)
                    false
                }
                ModeCompte.REGISTRA -> compte.registra(e.correu, e.contrasenya) == ResultatRegistre.CAL_CONFIRMAR_CORREU
            }
        }
    }

    fun reintenta() = executa {
        compte.reintenta()
        false
    }

    fun surt() = executa {
        compte.surt()
        false
    }

    /** @param accio retorna si cal avisar que es confirmi el correu. */
    private fun executa(accio: suspend () -> Boolean) {
        _estat.update { it.copy(treballant = true, error = null) }
        viewModelScope.launch {
            try {
                val confirmar = accio()
                _estat.update { it.copy(treballant = false, calConfirmar = confirmar) }
            } catch (e: ExcepcioCompte) {
                _estat.update { it.copy(treballant = false, error = e.error) }
            }
        }
    }
}
