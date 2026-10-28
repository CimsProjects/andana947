package cat.andana947.joc.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Radi mitjà de la Terra (IUGG), en metres. */
const val RADI_TERRA_METRES = 6_371_008.8

/**
 * Distància sobre l'esfera entre dos punts, en metres (fórmula de l'haversinus). A les distàncies del joc
 * (desenes o centenars de metres) l'error respecte de l'el·lipsoide és inferior al 0,5 %, molt per sota de
 * la precisió del GPS. El servidor fa servir `ST_Distance` sobre `geography`.
 */
fun distanciaMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val f1 = Math.toRadians(lat1)
    val f2 = Math.toRadians(lat2)
    val df = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = sin(df / 2).pow(2) + cos(f1) * cos(f2) * sin(dl / 2).pow(2)
    return 2 * RADI_TERRA_METRES * asin(sqrt(a.coerceIn(0.0, 1.0)))
}
