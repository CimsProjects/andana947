package cat.andana947.joc.circulacio

import cat.andana947.joc.geo.distanciaMetres
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Un punt sobre el mapa, amb el rumb (graus des del nord, en sentit horari) quan és un punt d'un traçat. */
data class Punt(val lat: Double, val lon: Double, val rumb: Double = 0.0)

/** El que queda d'un punt projectat sobre un traçat: a quants metres del traçat cau i a quina distància en queda. */
data class Projeccio(val distancia: Double, val separacio: Double)

/**
 * El traçat d'una via (una polilínia), amb les distàncies acumulades calculades com al script que el genera
 * (haversinus), perquè les distàncies de les parades dels patrons hi caiguin al lloc.
 */
class Tracat(val punts: List<Punt>) {
    init {
        require(punts.size >= 2) { "Un traçat necessita almenys dos punts" }
    }

    private val acumulades: DoubleArray = DoubleArray(punts.size).also { d ->
        for (i in 1 until punts.size) d[i] = d[i - 1] + distanciaMetres(punts[i - 1].lat, punts[i - 1].lon, punts[i].lat, punts[i].lon)
    }

    val llargada: Double get() = acumulades.last()

    /** L'índex del tram (entre el punt i i el i + 1) que conté la distància [d]. */
    private fun tram(d: Double): Int {
        var baix = 0
        var alt = punts.size - 2
        while (baix < alt) {
            val mig = (baix + alt + 1) / 2
            if (acumulades[mig] <= d) baix = mig else alt = mig - 1
        }
        return baix
    }

    /** El punt a [d] metres de l'inici del traçat, amb el rumb del tram on cau. Fora del traçat, l'extrem. */
    fun posicio(d: Double): Punt {
        val i = tram(d.coerceIn(0.0, llargada))
        val a = punts[i]
        val b = punts[i + 1]
        val llarg = acumulades[i + 1] - acumulades[i]
        val t = if (llarg <= 0.0) 0.0 else ((d - acumulades[i]) / llarg).coerceIn(0.0, 1.0)
        return Punt(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon), rumb(a, b))
    }

    /** Els punts del traçat entre [desde] i [fins] metres, amb els dos extrems: el tros de via que ocupa un tren. */
    fun tros(desde: Double, fins: Double): List<Punt> {
        val a = desde.coerceIn(0.0, llargada)
        val b = fins.coerceIn(a, llargada)
        val resultat = mutableListOf(posicio(a))
        for (i in tram(a) + 1..tram(b)) {
            if (acumulades[i] > a && acumulades[i] < b) resultat += punts[i].copy(rumb = rumb(punts[i - 1], punts[i + 1]))
        }
        resultat += posicio(b)
        return resultat
    }

    /** On cau el punt ([lat], [lon]) sobre el traçat. */
    fun projecta(lat: Double, lon: Double): Projeccio {
        val escala = cos(Math.toRadians(lat)) * METRES_PER_GRAU
        var millor = Projeccio(0.0, Double.MAX_VALUE)
        for (i in 0 until punts.size - 1) {
            val a = punts[i]
            val b = punts[i + 1]
            val ax = (a.lon - lon) * escala
            val ay = (a.lat - lat) * METRES_PER_GRAU
            val dx = (b.lon - a.lon) * escala
            val dy = (b.lat - a.lat) * METRES_PER_GRAU
            val llarg2 = dx * dx + dy * dy
            val t = if (llarg2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / llarg2).coerceIn(0.0, 1.0)
            val separacio = hypot(ax + t * dx, ay + t * dy)
            if (separacio < millor.separacio) millor = Projeccio(acumulades[i] + t * (acumulades[i + 1] - acumulades[i]), separacio)
        }
        return millor
    }

    companion object {
        /** Metres d'un grau de latitud (sobre l'esfera del joc). */
        const val METRES_PER_GRAU = cat.andana947.joc.geo.RADI_TERRA_METRES * PI / 180

        /** Rumb de [a] cap a [b], en graus des del nord (aproximació plana, prou bona per a trams curts). */
        fun rumb(a: Punt, b: Punt): Double {
            val dx = (b.lon - a.lon) * cos(Math.toRadians((a.lat + b.lat) / 2))
            val dy = b.lat - a.lat
            if (dx == 0.0 && dy == 0.0) return 0.0
            return (Math.toDegrees(atan2(dx, dy)) + 360) % 360
        }

        /** El punt a [metres] de [p] en la direcció [rumb] (graus) i desplaçat [lateral] metres cap a la dreta. */
        fun desplaca(p: Punt, rumb: Double, metres: Double, lateral: Double = 0.0): Punt {
            val r = Math.toRadians(rumb)
            val est = sin(r) * metres + cos(r) * lateral
            val nord = cos(r) * metres - sin(r) * lateral
            return Punt(
                p.lat + nord / METRES_PER_GRAU,
                p.lon + est / (METRES_PER_GRAU * cos(Math.toRadians(p.lat))),
                p.rumb,
            )
        }
    }
}
