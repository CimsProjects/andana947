package cat.andana947.joc.progressio

import cat.andana947.joc.config.ConfiguracioJoc
import kotlin.math.floor
import kotlin.math.pow

/** Prestigi i nivells (GDD 5.1). El prestigi no es gasta. */
class Prestigi(config: ConfiguracioJoc) {
    private val p = config.prestigi
    private val c = config.concessions

    /** Punts de prestigi que calen per arribar al nivell `n`: `base × (n − 1)^exponent`. */
    fun puntsPerNivell(n: Int): Int {
        require(n >= 1) { "El primer nivell és l'1" }
        return floor(p.baseNivell * (n - 1).toDouble().pow(p.exponentNivell)).toInt()
    }

    fun nivell(punts: Int): Int {
        var n = 1
        while (puntsPerNivell(n + 1) <= punts) n++
        return n
    }

    /** Concessions actives que permet la llicència d'operador (GDD 4.2). */
    fun maximConcessions(nivell: Int): Int = c.maximBase + c.maximPerNivell * nivell
}
