package cat.andana947.joc.trams

import cat.andana947.joc.config.ConfiguracioJoc
import kotlin.math.floor
import kotlin.math.max

/** La mida d'un tram (GDD 4.0), segons les arribades a andana d'un feiner. */
enum class Mida(val clau: String) {
    /** El terç de trams amb menys arribades: els inicials. */
    PETIT("petit"),
    MITJA("mitja"),

    /** El terç amb més arribades: els finals, que no es compren sencers. */
    GRAN("gran"),
    ;

    val esCompraSencer: Boolean get() = this != GRAN

    companion object {
        fun de(clau: String): Mida = entries.first { it.clau == clau }
    }
}

/** Què pot fer el jugador amb un tram, que és el color amb què es dibuixa al mapa (GDD 4.0). */
enum class EstatTram {
    /** Gris: no té prou monedes, o és gran. */
    NO_ET_LA_POTS_PERMETRE,

    /** Verd. */
    ES_POT_COMPRAR,

    /** Blau. */
    TEU,
}

/**
 * Les regles dels trams (GDD 4.0). Les mateixes que `scripts/andana/trams.py`, que classifica els trams reals;
 * `TramsTest` comprova que `dades/generades/trams.csv` hi quadra. Al joc, qui sorteja i cobra és el servidor.
 */
class Trams(config: ConfiguracioJoc) {
    private val c = config.trams

    /** Les monedes que pot donar una arribada a andana, o null per als trams grans. */
    fun recompensa(mida: Mida): IntRange? = when (mida) {
        Mida.PETIT -> c.recompensaPetitMinima..c.recompensaPetitMaxima
        Mida.MITJA -> c.recompensaMitjaMinima..c.recompensaMitjaMaxima
        Mida.GRAN -> null
    }

    /** Les monedes que dona de mitjana una arribada. */
    fun recompensaMitjana(mida: Mida): Double? = recompensa(mida)?.let { (it.first + it.last) / 2.0 }

    /** Les arribades on comencen els trams mitjans i els grans: els terços de tots els trams. */
    fun talls(arribades: List<Int>): Pair<Int, Int> {
        val a = arribades.sorted()
        return a[a.size / 3] to a[2 * a.size / 3]
    }

    fun mida(arribades: Int, talls: Pair<Int, Int>): Mida = when {
        arribades < talls.first -> Mida.PETIT
        arribades < talls.second -> Mida.MITJA
        else -> Mida.GRAN
    }

    /** El preu d'un tram: les monedes que dona en `diesRetorn` dies. Els grans no en tenen. */
    fun preu(arribades: Int, mida: Mida): Int? {
        val mitjana = recompensaMitjana(mida) ?: return null
        val pas = c.arrodonimentPreu
        val valor = floor(arribades * mitjana * c.diesRetorn / pas + 0.5).toInt() * pas
        return max(c.preuMinim, valor)
    }

    /** El color del tram al mapa. */
    fun estat(preu: Int?, mida: Mida, saldo: Long, esTeu: Boolean): EstatTram = when {
        esTeu -> EstatTram.TEU
        mida.esCompraSencer && preu != null && saldo >= preu -> EstatTram.ES_POT_COMPRAR
        else -> EstatTram.NO_ET_LA_POTS_PERMETRE
    }

    /** Les arribades del dia que ja han passat a l'hora [hora] (0–24, amb decimals), repartides uniformement entre
     *  l'inici i la fi del servei (GDD 4.0, [estimat]). */
    fun arribadesFinsA(arribadesDia: Double, hora: Double): Double {
        val durada = (c.horaFiServei - c.horaIniciServei).toDouble()
        val fet = ((hora - c.horaIniciServei) / durada).coerceIn(0.0, 1.0)
        return arribadesDia * fet
    }
}
