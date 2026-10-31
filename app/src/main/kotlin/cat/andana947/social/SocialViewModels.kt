package cat.andana947.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cat.andana947.data.joc.EstatCooperatives
import cat.andana947.data.joc.EstatJunta
import cat.andana947.data.joc.JuntaMeva
import cat.andana947.data.joc.OfertaAccions
import cat.andana947.data.joc.ResultatCheckinRpc
import cat.andana947.data.joc.ServeiJoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SocialUiState(
    val carregant: Boolean = true,
    val mercat: List<OfertaAccions> = emptyList(),
    val juntes: List<JuntaMeva> = emptyList(),
    val cooperatives: EstatCooperatives? = null,
    val ocupat: Boolean = false,
    val resultat: ResultatCheckinRpc? = null,
    val error: String? = null,
)

/** La part social (fase 7): el mercat d'accions, les juntes del jugador i les cooperatives. Tot ho valida el servidor. */
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val joc: ServeiJoc,
) : ViewModel() {
    private val _estat = MutableStateFlow(SocialUiState())
    val estat: StateFlow<SocialUiState> = _estat.asStateFlow()

    fun carrega() {
        viewModelScope.launch {
            val mercat = joc.mercatAccions()
            val juntes = joc.juntesMeves()
            val coops = joc.estatCooperatives()
            _estat.update {
                it.copy(
                    carregant = false,
                    mercat = mercat.getOrDefault(it.mercat),
                    juntes = juntes.getOrDefault(it.juntes),
                    cooperatives = coops.getOrNull() ?: it.cooperatives,
                    error = (mercat.exceptionOrNull() ?: juntes.exceptionOrNull() ?: coops.exceptionOrNull())?.message,
                )
            }
        }
    }

    fun compra(oferta: OfertaAccions, quantitat: Int) = accio { joc.compraOferta(oferta.id, quantitat) }

    fun retira(oferta: OfertaAccions) = accio { joc.cancelaOferta(oferta.id) }

    fun creaCooperativa(nom: String, comarca: String) = accio { joc.creaCooperativa(nom, comarca) }

    fun uneix(cooperativa: Long) = accio { joc.uneixCooperativa(cooperativa) }

    fun surt() = accio { joc.surtCooperativa() }

    fun tancaResultat() = _estat.update { it.copy(resultat = null) }

    private fun accio(crida: suspend () -> Result<ResultatCheckinRpc>) {
        if (_estat.value.ocupat) return
        _estat.update { it.copy(ocupat = true) }
        viewModelScope.launch {
            crida()
                .onSuccess { r -> _estat.update { it.copy(ocupat = false, resultat = r) } }
                .onFailure { err -> _estat.update { it.copy(ocupat = false, error = err.message) } }
            carrega()
        }
    }
}

data class JuntaUiState(
    val carregant: Boolean = true,
    val junta: EstatJunta? = null,
    /** Les ofertes del mercat d'aquesta estació. */
    val ofertes: List<OfertaAccions> = emptyList(),
    val ocupat: Boolean = false,
    val resultat: ResultatCheckinRpc? = null,
    val error: String? = null,
)

/** La junta d'una gran estació: accionistes, propostes de millora, vots i posar accions a la venda. */
@HiltViewModel
class JuntaViewModel @Inject constructor(
    private val joc: ServeiJoc,
) : ViewModel() {
    private val _estat = MutableStateFlow(JuntaUiState())
    val estat: StateFlow<JuntaUiState> = _estat.asStateFlow()
    private var node: String? = null

    fun carrega(id: String) {
        node = id
        viewModelScope.launch {
            joc.estatJunta(id)
                .onSuccess { j -> _estat.update { it.copy(carregant = false, junta = j, error = null) } }
                .onFailure { err -> _estat.update { it.copy(carregant = false, error = err.message) } }
            joc.mercatAccions(id).onSuccess { o -> _estat.update { it.copy(ofertes = o) } }
        }
    }

    fun proposa(millora: String) = accio { joc.proposaMilloraEstacio(it, millora) }

    fun vota(proposta: Long, aFavor: Boolean) = accio { joc.votaProposta(proposta, aFavor) }

    fun posaVenda(quantitat: Int, preu: Double) = accio { joc.posaVenda(it, quantitat, preu) }

    fun compra(oferta: OfertaAccions) = accio { joc.compraOferta(oferta.id, 1) }

    fun retira(oferta: OfertaAccions) = accio { joc.cancelaOferta(oferta.id) }

    fun tancaResultat() = _estat.update { it.copy(resultat = null) }

    private fun accio(crida: suspend (String) -> Result<ResultatCheckinRpc>) {
        val id = node ?: return
        if (_estat.value.ocupat) return
        _estat.update { it.copy(ocupat = true) }
        viewModelScope.launch {
            crida(id)
                .onSuccess { r -> _estat.update { it.copy(ocupat = false, resultat = r) } }
                .onFailure { err -> _estat.update { it.copy(ocupat = false, error = err.message) } }
            carrega(id)
        }
    }
}
