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
    /** Regal del Director: no caduca i no compta per a la llicència. */
    val regal: Boolean = false,
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
    /** null a les de regal, que no caduquen. */
    val diesRestants: Int? = null,
    val regal: Boolean = false,
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
    /** Les concessions que compten per a la llicència (les de regal, no). */
    @SerialName("concessions_llicencia")
    val concessionsLlicencia: Int? = null,
    @SerialName("concessions_actives")
    val concessionsActives: List<ConcessioActiva> = emptyList(),
    @SerialName("concessions_caducades")
    val concessionsCaducades: List<ConcessioCaducada> = emptyList(),
    val accions: List<AccioEstacio> = emptyList(),
    val moviments: List<Moviment> = emptyList(),
    @SerialName("ingres_estimat_dia")
    val ingresEstimatDia: Double = 0.0,
    @SerialName("mode_dev")
    val modeDev: Boolean = false,
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

/**
 * Estat dels cobraments de 4 h (migració 20261030120000_cobraments.sql). Tot ho calcula el servidor; [ara] és la seva
 * hora, perquè el compte enrere no depengui del rellotge del mòbil.
 */
@Serializable
data class EstatCobraments(
    val ara: String,
    @SerialName("darrer_cobrament_el")
    val darrerCobramentEl: String,
    @SerialName("proper_cobrament_el")
    val properCobramentEl: String,
    @SerialName("periode_segons")
    val periodeSegons: Long,
    @SerialName("cobrament_previst")
    val cobramentPrevist: Double,
    val saldo: Double,
    @SerialName("pendent_total")
    val pendentTotal: Double = 0.0,
    @SerialName("pendent_nombre")
    val pendentNombre: Int = 0,
)

/** El que el jugador ha fet fora de l'economia: l'avatar i si ja ha vist la introducció del director. */
@Serializable
data class PerfilJugador(
    val avatar: cat.andana947.avatar.Avatar? = null,
    @SerialName("introduccio_feta")
    val introduccioFeta: Boolean = false,
)

/** Les andanes que el Director ofereix de regal (funció `ofertes_regal`). */
@Serializable
data class OfertesRegal(
    @SerialName("maxim_andanes_per_provincia")
    val maximPerProvincia: Int,
    val provincies: List<ProvinciaRegal> = emptyList(),
)

@Serializable
data class ProvinciaRegal(
    val provincia: String,
    val nom: String,
    val opcions: List<OpcioRegal> = emptyList(),
)

/** Una opció: una andana sola o un tram de dues andanes seguides d'una línia ([linia], amb el seu [color]). */
@Serializable
data class OpcioRegal(
    val id: String,
    val linia: String? = null,
    val color: String? = null,
    val nodes: List<AndanaRegal> = emptyList(),
) {
    val esTram: Boolean get() = nodes.size > 1
}

@Serializable
data class AndanaRegal(
    val id: String,
    val nom: String,
    val mode: String,
    val operador: String,
    val municipi: String,
    val linies: List<String> = emptyList(),
    @SerialName("viatgers_feiner")
    val viatgersFeiner: Double,
    @SerialName("font_afluencia")
    val fontAfluencia: String,
    @SerialName("ingres_diari")
    val ingresDiari: Double,
)
