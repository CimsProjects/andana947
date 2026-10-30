package cat.andana947.joc.circulacio

import java.time.LocalDateTime

/** Una parada de la fitxa, amb l'hora (segons des de la mitjanit local) si se sap. */
data class ParadaHora(val nom: String, val segons: Int?)

/**
 * El que es veu en tocar un vehicle: la línia, d'on ve, cap on va i la pròxima parada. Als vehicles amb posició
 * [real] no hi ha horari: només els noms. [aturatA] és la parada on és aturat ara, si n'és a una.
 */
data class FitxaVehicle(
    val linia: LiniaCirculacio?,
    val real: Boolean,
    val origen: ParadaHora?,
    val desti: ParadaHora?,
    val propera: ParadaHora?,
    val aturatA: ParadaHora?,
)

/** Les fitxes dels vehicles, a partir dels horaris (docs/vehicles.md). */
object Fitxes {
    /**
     * La fitxa d'un vehicle estimat segons l'horari, amb l'identificador que li dona [Circulacions.enCirculacio]
     * (`d0:12`: prefix, tipus de dia i ordre de l'expedició). Null si l'identificador no és d'un vehicle estimat.
     */
    fun estimat(circulacions: Circulacions, id: String, ara: LocalDateTime): FitxaVehicle? {
        val (expedicio, segonsAra) = circulacions.expedicio(id, ara) ?: return null
        val (patro, inici) = expedicio
        val t = segonsAra - inici
        val noms = circulacions.horaris.parades
        fun nom(i: Int): String? = patro.parades?.getOrNull(i)?.takeIf { it >= 0 }?.let { noms.getOrNull(it) }
        val reals = patro.arribades.indices.filter { nom(it) != null }

        val origen = patro.origen?.let { (n, s) -> noms.getOrNull(n)?.let { ParadaHora(it, inici + s) } }
            ?: reals.firstOrNull()?.let { ParadaHora(nom(it)!!, inici + patro.sortida(it)) }
        val desti = patro.desti?.let { (n, s) -> noms.getOrNull(n)?.let { ParadaHora(it, inici + s) } }
            ?: reals.lastOrNull()?.let { ParadaHora(nom(it)!!, inici + patro.arribades[it]) }
        val aturat = reals.firstOrNull { patro.arribades[it] <= t && t <= patro.sortida(it) }
        val propera = reals.firstOrNull { patro.arribades[it] > t }
        return FitxaVehicle(
            linia = circulacions.horaris.linies.getOrNull(patro.linia),
            real = false,
            origen = origen,
            desti = desti,
            propera = propera?.let { ParadaHora(nom(it)!!, inici + patro.arribades[it]) },
            aturatA = aturat?.let { ParadaHora(nom(it)!!, null) },
        )
    }

    /**
     * La fitxa d'un vehicle amb posició real: sense horari, però com que se sap per quin [tracat] va i a quina
     * [distancia], els noms d'origen, destí i pròxima parada surten d'un recorregut de la mateixa línia pel mateix traçat.
     */
    fun real(circulacions: Circulacions, linia: Int?, tracat: Int?, distancia: Double?): FitxaVehicle {
        val l = linia?.let { circulacions.horaris.linies.getOrNull(it) }
        val noms = circulacions.horaris.parades
        val patro = if (tracat == null) null else circulacions.horaris.patrons.firstOrNull {
            it.tracat == tracat && (linia == null || it.linia == linia) && it.parades != null
        }
        if (patro == null || distancia == null) return FitxaVehicle(l, true, null, null, null, null)
        fun nom(i: Int): String? = patro.parades?.getOrNull(i)?.takeIf { it >= 0 }?.let { noms.getOrNull(it) }
        val reals = patro.distancies.indices.filter { nom(it) != null }
        val origen = patro.origen?.let { noms.getOrNull(it[0]) } ?: reals.firstOrNull()?.let { nom(it) }
        val desti = patro.desti?.let { noms.getOrNull(it[0]) } ?: reals.lastOrNull()?.let { nom(it) }
        val propera = reals.firstOrNull { patro.distancies[it] > distancia + MARGE_PARADA_METRES }
        return FitxaVehicle(
            linia = l,
            real = true,
            origen = origen?.let { ParadaHora(it, null) },
            desti = desti?.let { ParadaHora(it, null) },
            propera = propera?.let { ParadaHora(nom(it)!!, null) },
            aturatA = null,
        )
    }

    /** A menys d'aquests metres d'una parada, el tren ja hi és: la pròxima és la següent. */
    const val MARGE_PARADA_METRES = 50.0
}
