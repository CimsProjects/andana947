package cat.andana947.joc.trams

import cat.andana947.joc.config.ConfiguracioJoc
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

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
    ;

    companion object {
        /** El color d'un tram al mapa per a un jugador amb [saldo] monedes. */
        fun de(preu: Int?, mida: Mida, saldo: Long, esTeu: Boolean): EstatTram = when {
            esTeu -> TEU
            mida.esCompraSencer && preu != null && saldo >= preu -> ES_POT_COMPRAR
            else -> NO_ET_LA_POTS_PERMETRE
        }
    }
}

/**
 * Les fórmules dels trams (GDD 4.0), amb els paràmetres explícits perquè les facin servir igual [Trams] (amb la
 * configuració) i l'app (amb el catàleg, `CatalegTrams`). Al joc, qui sorteja i cobra és el servidor: l'app només les
 * fa servir per mostrar xifres aproximades.
 */
object FormulesTrams {
    /** Arrodoneix al múltiple de [pas] més proper; els mitjos, cap amunt (com `scripts/andana/trams.py`). */
    fun arrodoneix(valor: Double, pas: Int): Int = floor(valor / pas + 0.5).toInt() * pas

    /** El cost d'afegir una andana a un tram que en té [andanes]: el preu de compra × creixement^andanes. */
    fun costMillora(preuCompra: Int, andanes: Int, creixement: Double, pas: Int): Int =
        max(pas, arrodoneix(preuCompra * creixement.pow(andanes), pas))

    /** Les monedes que dona de mitjana un tram amb [andanes] andanes en un minut de servei d'un feiner [estimat]. */
    fun monedesPerMinut(arribades: Int, parades: Int, andanes: Int, recompensaMitjana: Double, minutsServei: Int): Double =
        arribades.toDouble() / parades * andanes * recompensaMitjana / minutsServei
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

    /**
     * El preu de comprar un tram, que ve amb una sola andana: les monedes que dona una andana mitjana del tram (les
     * arribades repartides entre les parades) en `diesRetorn` dies. Els grans no en tenen.
     */
    fun preu(arribades: Int, parades: Int, mida: Mida): Int? {
        val mitjana = recompensaMitjana(mida) ?: return null
        val valor = FormulesTrams.arrodoneix(arribades.toDouble() / parades * mitjana * c.diesRetorn, c.arrodonimentPreu)
        return max(c.preuMinim, valor)
    }

    /** El cost d'afegir una andana a un tram que en té [andanes]. */
    fun costMillora(preuCompra: Int, andanes: Int): Int =
        FormulesTrams.costMillora(preuCompra, andanes, c.creixementMillora, c.arrodonimentPreu)

    /** Els minuts de servei d'un dia, entre els quals es reparteixen les arribades. */
    val minutsServei: Int get() = (c.horaFiServei - c.horaIniciServei) * 60

    /** Les arribades del dia que ja han passat a l'hora [hora] (0–24, amb decimals), repartides uniformement entre
     *  l'inici i la fi del servei (GDD 4.0, [estimat]). */
    fun arribadesFinsA(arribadesDia: Double, hora: Double): Double {
        val durada = (c.horaFiServei - c.horaIniciServei).toDouble()
        val fet = ((hora - c.horaIniciServei) / durada).coerceIn(0.0, 1.0)
        return arribadesDia * fet
    }
}
