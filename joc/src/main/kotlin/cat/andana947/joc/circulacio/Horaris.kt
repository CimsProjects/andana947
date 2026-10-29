package cat.andana947.joc.circulacio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate

/** El tipus de dia dels horaris. Els festius entre setmana, de moment, fan l'horari de feiner. */
enum class TipusDia(val clau: String) {
    FEINER("feiner"),
    DISSABTE("dissabte"),
    FESTIU("festiu");

    companion object {
        fun de(data: LocalDate): TipusDia = when (data.dayOfWeek) {
            DayOfWeek.SATURDAY -> DISSABTE
            DayOfWeek.SUNDAY -> FESTIU
            else -> FEINER
        }
    }
}

@Serializable
data class LiniaCirculacio(
    val id: String,
    val codi: String,
    val mode: String,
    val operador: String,
    /** Hexadecimal, sense coixinet. */
    val color: String,
)

/**
 * El recorregut d'unes quantes expedicions: el traçat, a quants metres hi ha cada parada i quants segons després de
 * sortir de la primera hi arriba i en surt. Sense [sortides], el tren no s'atura (arribada i sortida iguals).
 */
@Serializable
data class Patro(
    val linia: Int,
    val tracat: Int,
    val distancies: List<Int>,
    val arribades: List<Int>,
    val sortides: List<Int>? = null,
) {
    fun sortida(i: Int): Int = (sortides ?: arribades)[i]

    val durada: Int get() = arribades.last()
}

/** El fitxer `circulacions.json` que genera `scripts/genera_circulacions.py` (docs/vehicles.md). */
@Serializable
data class HorarisTrens(
    val versio: Int,
    val generat: String,
    val linies: List<LiniaCirculacio>,
    val tracats: List<String>,
    val patrons: List<Patro>,
    /** Per a cada tipus de dia, parelles (patró, segons de sortida des de la mitjanit) en una sola llista. */
    val expedicions: Map<String, List<Int>>,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun llegeix(text: String): HorarisTrens = json.decodeFromString(serializer(), text)
    }
}
