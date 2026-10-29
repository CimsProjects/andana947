package cat.andana947.data.joc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Node a prop del jugador, obtingut de la funció RPC `nodes_a_prop_jugador` (GDD 7.1). */
@Serializable
data class NodeAProp(
    val id: String,
    val nom: String,
    val mode: String,
    val operador: String,
    val lat: Double,
    val lon: Double,
    val linies: List<String> = emptyList(),
    @SerialName("viatgers_feiner")
    val viatgersFeiner: Double,
    @SerialName("font_afluencia")
    val fontAfluencia: String,
    @SerialName("distancia_metres")
    val distanciaMetres: Double,
    val categoria: String, // "parada" o "gran_estacio"
    val preu: Double,
    @SerialName("ingres_diari")
    val ingresDiari: Double,
    @SerialName("te_concessio")
    val teConcessio: Boolean = false,
    @SerialName("concessio_caducada")
    val concessioCaducada: Boolean = false,
    @SerialName("dies_restants")
    val diesRestants: Int? = null,
    @SerialName("accions_jugador")
    val accionsJugador: Int = 0,
    @SerialName("accions_disponibles")
    val accionsDisponibles: Int = 0,
)

@Serializable
data class ConcessioActiva(
    @SerialName("node_id")
    val nodeId: String,
    val nom: String,
    val mode: String,
    val operador: String,
    @SerialName("viatgers_feiner")
    val viatgersFeiner: Double,
    @SerialName("ingres_diari")
    val ingresDiari: Double,
    @SerialName("caduca_el")
    val caducaEl: String,
    @SerialName("dies_restants")
    val diesRestants: Int = 0,
)

@Serializable
data class ConcessioCaducada(
    @SerialName("node_id")
    val nodeId: String,
    val nom: String,
    val mode: String,
    val operador: String,
    @SerialName("viatgers_feiner")
    val viatgersFeiner: Double,
    @SerialName("caduca_el")
    val caducaEl: String,
)

@Serializable
data class AccioEstacio(
    @SerialName("node_id")
    val nodeId: String,
    val nom: String,
    val mode: String,
    val operador: String,
    val quantitat: Int,
    @SerialName("dividend_diari")
    val dividendDiari: Double,
    @SerialName("darrera_visita_el")
    val darreraVisitaEl: String,
    @SerialName("paga_dividend")
    val pagaDividend: Boolean = true,
)

@Serializable
data class Moviment(
    val id: Long,
    val concepte: String,
    val import: Double,
    @SerialName("node_id")
    val nodeId: String? = null,
    @SerialName("creat_el")
    val creatEl: String,
)

@Serializable
data class EstatImperi(
    val saldo: Double,
    val prestigi: Int,
    val nivell: Int,
    @SerialName("maxim_concessions")
    val maximConcessions: Int,
    @SerialName("concessions_actives")
    val concessionsActives: List<ConcessioActiva> = emptyList(),
    @SerialName("concessions_caducades")
    val concessionsCaducades: List<ConcessioCaducada> = emptyList(),
    val accions: List<AccioEstacio> = emptyList(),
    val moviments: List<Moviment> = emptyList(),
    @SerialName("ingres_estimat_dia")
    val ingresEstimatDia: Double = 0.0,
)

@Serializable
data class ResultatCheckinRpc(
    val status: String,
    val missatge: String,
    @SerialName("tipus_accio")
    val tipusAccio: String? = null,
    val saldo: Double? = null,
    val prestigi: Int? = null,
    val nivell: Int? = null,
    val cost: Double? = null,
    @SerialName("dies_restants")
    val diesRestants: Int? = null,
    @SerialName("accions_comprades")
    val accionsComprades: Int? = null,
    @SerialName("accions_totals")
    val accionsTotals: Int? = null,
    @SerialName("metres_falten")
    val metresFalten: Int? = null,
)
