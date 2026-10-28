package cat.andana947.joc.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mode de transport d'un node (GDD, secció 3). */
@Serializable
enum class Mode(
    /** Estació ferroviària (radi de check-in llarg i base de la intermodalitat) o parada de carrer. */
    val esEstacio: Boolean,
) {
    @SerialName("metro")
    METRO(true),

    @SerialName("rodalies")
    RODALIES(true),

    @SerialName("regional")
    REGIONAL(true),

    @SerialName("fgc")
    FGC(true),

    @SerialName("tram")
    TRAM(false),

    @SerialName("bus")
    BUS(false),
}

/** D'on surt l'afluència d'un node. */
@Serializable
enum class FontAfluencia {
    /** Dada publicada per l'operador o per l'ATM. */
    @SerialName("real")
    REAL,

    /** Model d'estimació (GDD 2.2 i 2.3). L'app ho diu a la fitxa: «Afluència estimada». */
    @SerialName("estimat")
    ESTIMAT,
}

/** Com es posseeix un node (GDD 3.1). */
enum class Categoria {
    /** Concessió individual. */
    PARADA,

    /** Accionariat compartit: 100 accions. */
    GRAN_ESTACIO,
}

/** Una parada o estació real on es pot fer check-in. */
@Serializable
data class Node(
    val id: String,
    val nom: String,
    val mode: Mode,
    val lat: Double,
    val lon: Double,
    /** Viatgers d'un dia feiner. */
    val viatgersFeiner: Double,
    val font: FontAfluencia,
    /** Codi INE de 5 xifres. */
    val municipi: String? = null,
    /** Codi de comarca de l'ICGC. */
    val comarca: String? = null,
    /** Habitants per km² del municipi. */
    val densitatMunicipi: Double? = null,
    val linies: List<String> = emptyList(),
)
