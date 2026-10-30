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
    /** Concessió física (o visita, a les grans estacions) fins al final de la temporada: ×1,5. */
    val presencial: Boolean = false,
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
    /** Els dies que queden de concessió física (×1,5); null si no en té. */
    val diesRestants: Int? = null,
    val regal: Boolean = false,
    @SerialName("presencial_fins")
    val presencialFins: String? = null,
    val multiplicador: Double = 1.0,
    @SerialName("manteniment_diari")
    val mantenimentDiari: Double = 0.0,
    @SerialName("nivell_maestria")
    val nivellMaestria: Int = 1,
    /** Quantes millores té l'andana. */
    val millores: Int = 0,
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
    /** L'ingrés net d'un dia feiner: arribades (×1,5 amb concessió física) menys el manteniment. */
    @SerialName("ingres_estimat_dia")
    val ingresEstimatDia: Double = 0.0,
    @SerialName("manteniment_dia")
    val mantenimentDia: Double = 0.0,
    @SerialName("temporada_fi")
    val temporadaFi: String? = null,
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
    /** Les fites que s'han aconseguit amb aquesta acció. */
    val fites: List<String> = emptyList(),
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
    /** La part del dia de servei que paga el pròxim cobrament; 0 de matinada, quan no passa cap tren. */
    @SerialName("fraccio_servei")
    val fraccioServei: Double? = null,
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

/** El resum de la pantalla Perfil (funció `resum_perfil`). */
@Serializable
data class ResumPerfil(
    val saldo: Double,
    val prestigi: Int,
    val nivell: Int,
    @SerialName("prestigi_inici_nivell")
    val prestigiIniciNivell: Int,
    @SerialName("prestigi_seguent_nivell")
    val prestigiSeguentNivell: Int,
    @SerialName("membre_des_de")
    val membreDesDe: String,
    val concessions: Int = 0,
    val regals: Int = 0,
    @SerialName("maxim_concessions")
    val maximConcessions: Int = 0,
    val accions: Int = 0,
    @SerialName("estacions_amb_accions")
    val estacionsAmbAccions: Int = 0,
    val municipis: Int = 0,
    val comarques: Int = 0,
    val checkins: Int = 0,
    @SerialName("nodes_visitats")
    val nodesVisitats: Int = 0,
    @SerialName("total_cobrat")
    val totalCobrat: Double = 0.0,
) {
    /** De 0 a 1: quant falta per al nivell següent. */
    val progresNivell: Float
        get() {
            val tram = prestigiSeguentNivell - prestigiIniciNivell
            if (tram <= 0) return 1f
            return ((prestigi - prestigiIniciNivell).toFloat() / tram).coerceIn(0f, 1f)
        }
}

/**
 * Una andana del jugador amb les xifres de cada arribada (funció `andanes_jugador`): les persones que hi pugen i els
 * diners que dona [estimat]. Les calcula el servidor; l'app només les mostra quan, segons l'horari, hi arriba un vehicle.
 */
@Serializable
data class AndanaJugador(
    val id: String,
    val nom: String,
    val mode: String,
    val lat: Double,
    val lon: Double,
    val linies: List<String> = emptyList(),
    val regal: Boolean = false,
    val pujades: Int = 0,
    val valor: Double = 0.0,
    val presencial: Boolean = false,
    /** El color de la skin de les andanes del jugador. */
    val color: String = "#FF3D7F",
)

/** Una andana que es pot comprar (funció `cerca_andanes`). El preu ja és el d'aquest jugador. */
@Serializable
data class AndanaCerca(
    val id: String,
    val nom: String,
    val mode: String,
    val operador: String,
    val linies: List<String> = emptyList(),
    val municipi: String? = null,
    @SerialName("viatgers_feiner")
    val viatgersFeiner: Double,
    @SerialName("font_afluencia")
    val fontAfluencia: String,
    @SerialName("gran_estacio")
    val granEstacio: Boolean = false,
    val preu: Double,
    @SerialName("ingres_diari")
    val ingresDiari: Double = 0.0,
    val teva: Boolean = false,
    @SerialName("distancia_metres")
    val distanciaMetres: Double? = null,
)

/** La temporada del jugador (funció `estat_temporada`): rànquing, objectius, insígnies i skins. */
@Serializable
data class EstatTemporada(
    val temporada: Temporada,
    val nom: String,
    val jo: PosicioRanking? = null,
    val jugadors: Int = 0,
    val ranking: List<FilaRanking> = emptyList(),
    val fites: List<Fita> = emptyList(),
    val premis: List<PremiTemporada> = emptyList(),
    val skins: List<SkinAndana> = emptyList(),
)

@Serializable
data class Temporada(val numero: Int, val inici: String, val fi: String)

@Serializable
data class PosicioRanking(val punts: Int, val posicio: Int)

@Serializable
data class FilaRanking(
    val posicio: Int,
    val nom: String,
    val punts: Int,
    val avatar: cat.andana947.avatar.Avatar? = null,
    val jo: Boolean = false,
)

@Serializable
data class Fita(
    val id: String,
    val tipus: String,
    val objectiu: Int,
    val punts: Int,
    val skin: String? = null,
    val progres: Int = 0,
    @SerialName("aconseguida_el")
    val aconseguidaEl: String? = null,
) {
    val aconseguida: Boolean get() = aconseguidaEl != null
}

@Serializable
data class PremiTemporada(val temporada: Int, val posicio: Int, val punts: Int, val skin: String? = null)

@Serializable
data class SkinAndana(val id: String, val color: String, val teva: Boolean = false, val posada: Boolean = false)

/** Nivell i experiència d'una maestria (andana o línia). [xpSeguent] és null al nivell màxim. */
@Serializable
data class Maestria(
    val xp: Double = 0.0,
    val nivell: Int = 1,
    @SerialName("xp_nivell")
    val xpNivell: Double = 0.0,
    @SerialName("xp_seguent")
    val xpSeguent: Double? = null,
) {
    /** El progrés cap al nivell següent, de 0 a 1 (1 al nivell màxim). */
    val progres: Float
        get() = xpSeguent?.let { seg -> ((xp - xpNivell) / (seg - xpNivell)).toFloat().coerceIn(0f, 1f) } ?: 1f
}

@Serializable
data class MilloraAndana(
    val id: String,
    val bonus: Double,
    val cost: Double,
    @SerialName("nivell_minim")
    val nivellMinim: Int,
    val comprada: Boolean = false,
    val desbloquejada: Boolean = false,
)

@Serializable
data class LiniaAndana(
    val id: String,
    val codi: String,
    val mode: String,
    val color: String? = null,
    @SerialName("arribades_dia")
    val arribadesDia: Int = 0,
    val maestria: Maestria = Maestria(),
)

/** La fitxa d'una andana pròpia: maestria, línies i millores. Els diners són del servidor. */
@Serializable
data class EstatAndana(
    val id: String,
    val nom: String,
    val mode: String,
    val operador: String = "",
    val municipi: String? = null,
    val regal: Boolean = false,
    @SerialName("presencial_fins")
    val presencialFins: String? = null,
    val multiplicador: Double = 1.0,
    @SerialName("factor_millores")
    val factorMillores: Double = 1.0,
    @SerialName("arribades_dia")
    val arribadesDia: Int = 0,
    @SerialName("ingres_diari")
    val ingresDiari: Double = 0.0,
    @SerialName("manteniment_diari")
    val mantenimentDiari: Double = 0.0,
    val maestria: Maestria = Maestria(),
    val linies: List<LiniaAndana> = emptyList(),
    val millores: List<MilloraAndana> = emptyList(),
)

@Serializable
data class LiniaMaestria(
    val id: String,
    val codi: String,
    val nom: String = "",
    val mode: String,
    val color: String? = null,
    val maestria: Maestria = Maestria(),
)

@Serializable
data class AndanaMaestria(
    val id: String,
    val nom: String,
    val mode: String,
    val maestria: Maestria = Maestria(),
)

/** La xarxa més gran del jugador: andanes pròpies unides per trams o transbords a peu. */
@Serializable
data class XarxaUnida(
    val andanes: Int = 0,
    val municipis: Int = 0,
    val comarques: Int = 0,
    val provincies: Int = 0,
    val linies: Int = 0,
)

@Serializable
data class EstatMaestries(
    val linies: List<LiniaMaestria> = emptyList(),
    val andanes: List<AndanaMaestria> = emptyList(),
    @SerialName("xarxa_mes_gran")
    val xarxaMesGran: XarxaUnida = XarxaUnida(),
)
