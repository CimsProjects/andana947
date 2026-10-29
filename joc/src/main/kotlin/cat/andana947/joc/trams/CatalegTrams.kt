package cat.andana947.joc.trams

import cat.andana947.joc.circulacio.HorarisTrens
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Un tram del joc, tal com surt a `trams.json` (`scripts/genera_trams.py`). El preu que mana és el del servidor. */
@Serializable
data class TramDelJoc(
    val id: String,
    val codi: String,
    val nom: String,
    val mode: String,
    /** Hexadecimal, sense coixinet; buit si la línia no en té. */
    val color: String,
    val mida: String,
    /** Null per als trams grans, que es compraran per fraccions. */
    val preu: Int?,
    val parades: Int,
    /** Arribades a andana d'un feiner. */
    val arribades: Int,
    /** Les línies de `linies.csv` que formen el tram. */
    val linies: List<String>,
) {
    val midaTram: Mida get() = Mida.de(mida)
}

/** El fitxer `trams.json`, que l'app porta a dins per dibuixar els trams i mostrar-ne la fitxa. */
@Serializable
data class CatalegTrams(
    val versio: Int,
    val capitalInicial: Int,
    /** Les monedes per arribada de cada mida: [mínim, màxim]. */
    val recompenses: Map<String, List<Int>>,
    val trams: List<TramDelJoc>,
) {
    private val perId: Map<String, TramDelJoc> by lazy { trams.associateBy { it.id } }

    fun tram(id: String): TramDelJoc? = perId[id]

    /** Les monedes que pot donar una arribada d'un tram d'aquesta mida, o null si és gran. */
    fun recompensa(mida: Mida): IntRange? = recompenses[mida.clau]?.let { it[0]..it[1] }

    /**
     * Els traçats de `circulacions.json` de cada tram: els de totes les seves línies, sense repetir. Un tram sense cap
     * traçat (una línia que no té horaris al mapa) no surt.
     */
    fun tracats(horaris: HorarisTrens): Map<String, List<Int>> {
        val indexLinia = horaris.linies.withIndex().associate { (i, l) -> l.id to i }
        val tracatsLinia = horaris.patrons.groupBy({ it.linia }, { it.tracat })
        return trams.associate { tram ->
            tram.id to tram.linies.flatMap { id -> indexLinia[id]?.let { tracatsLinia[it] }.orEmpty() }.distinct().sorted()
        }.filterValues { it.isNotEmpty() }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun llegeix(text: String): CatalegTrams = json.decodeFromString(serializer(), text)
    }
}
