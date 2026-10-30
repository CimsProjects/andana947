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
    /** El preu de compra, que dona la primera andana. Null per als trams grans, que es compraran per fraccions. */
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
    val creixementMillora: Double,
    val arrodonimentPreu: Int,
    /** Els minuts de servei d'un dia, entre els quals es reparteixen les arribades. */
    val minutsServei: Int,
    val trams: List<TramDelJoc>,
) {
    private val perId: Map<String, TramDelJoc> by lazy { trams.associateBy { it.id } }

    fun tram(id: String): TramDelJoc? = perId[id]

    /** Les monedes que pot donar una arribada d'un tram d'aquesta mida, o null si és gran. */
    fun recompensa(mida: Mida): IntRange? = recompenses[mida.clau]?.let { it[0]..it[1] }

    /** El cost aproximat d'afegir una andana a un tram que en té [andanes] (el que mana és el del servidor). Null si el
     *  tram no es pot millorar (és gran, o ja té totes les parades). */
    fun costMillora(tram: TramDelJoc, andanes: Int): Int? {
        val preu = tram.preu ?: return null
        if (andanes >= tram.parades) return null
        return FormulesTrams.costMillora(preu, andanes, creixementMillora, arrodonimentPreu)
    }

    /** Les monedes que dona de mitjana un tram amb [andanes] andanes en un minut de servei d'un feiner [estimat]. */
    fun monedesPerMinut(tram: TramDelJoc, andanes: Int): Double {
        val recompensa = recompensa(tram.midaTram) ?: return 0.0
        return FormulesTrams.monedesPerMinut(tram.arribades, tram.parades, andanes, (recompensa.first + recompensa.last) / 2.0, minutsServei)
    }

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
