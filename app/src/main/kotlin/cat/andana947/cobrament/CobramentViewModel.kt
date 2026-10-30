package cat.andana947.cobrament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** El que s'ha cobrat mentre el jugador no hi era. Les xifres són del servidor. */
data class CobramentPendent(
    val total: Double,
    val nombre: Int,
    val saldoAra: Double,
    /** L'hora del servidor de la consulta: fins aquí es marquen com a vistos. */
    val fins: String,
)

/**
 * En tornar a l'app, comprova si hi ha cobraments que el jugador encara no ha vist. Si n'hi ha, la pantalla de
 * benvinguda els mostra; en recollir-los, el servidor els marca com a vistos (també per als altres mòbils).
 */
@HiltViewModel
class CobramentViewModel @Inject constructor(
    private val serveiJoc: ServeiJoc,
) : ViewModel() {

    private val _pendent = MutableStateFlow<CobramentPendent?>(null)
    val pendent: StateFlow<CobramentPendent?> = _pendent.asStateFlow()

    private var comprovant = false

    fun comprova() {
        if (comprovant || _pendent.value != null) return
        comprovant = true
        viewModelScope.launch {
            serveiJoc.estatCobraments().onSuccess { e ->
                if (e.pendentNombre > 0 && e.pendentTotal > 0) {
                    _pendent.value = CobramentPendent(
                        total = e.pendentTotal,
                        nombre = e.pendentNombre,
                        saldoAra = e.saldo,
                        fins = e.ara,
                    )
                }
            }
            comprovant = false
        }
    }

    fun recull() {
        val p = _pendent.value ?: return
        _pendent.value = null
        // Si falla (sense connexió), es tornarà a mostrar la pròxima vegada: millor repetir-la que perdre-la.
        viewModelScope.launch { serveiJoc.marcaCobramentsVistos(p.fins) }
    }
}
